package com.example.roomsmod.entity.a;

import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSqueeze;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A-200：プレイヤーの約 {@value #DEPTH_ROOMS} 部屋下を一緒に落ちながら、真上へ弾を撃つ。
 * <ul>
 *   <li>狙い：撃つ前に、その時のプレイヤーの位置（x, z）に狙いを固定し、そこへ赤く光る縦の線を出す（{@link #aimTicks()} tick、音つき。
 *       撃つ {@value #CLICK} tick 前にもう一度音と火花）。狙いは固定なので、線から横へ {@value #HIT_R} ブロック以上離れれば当たらない。</li>
 *   <li>弾は毎 tick {@value #SPEED}（ハード {@value #SPEED_HARD}）ブロック上がる。体を通る間に横の距離が {@value #HIT_R} 未満なら
 *       ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 発 1 人 1 回。横穴（体の中心）・ロッカーの中は当たらない。</li>
 *   <li>全部で {@value #VOLLEYS} 発（ハード {@value #VOLLEYS_HARD}）、弾が通り過ぎてから {@value #REST} tick（ハード {@value #REST_HARD}）休む。
 *       プレイヤーが隠れている間は狙わない（出てきてから予告し直す）。</li>
 *   <li>間に合うか：止まった状態からスニークで {@value #HIT_R}+0.05 ブロック動くのに反応 10 tick 込みで 34 tick。
 *       予告（狙いの固定）から当たるまで = aimTicks + 弾が {@value #DEPTH_ROOMS} 部屋上がる時間（最短 ≈ 24/1.35 ≈ 17 tick）で、
 *       これが通常 1.5 倍・ハード 1.35 倍以上になるよう aimTicks を決める（通常 36+19、ハード 30+17）。縦穴の内側は 4.4 ブロック幅なので、
 *       どの位置からでも 0.85 ブロック離れる向きが必ずある。</li>
 *   <li>A-50 / A-90 / A-130 / A-180 / A-250 / A-300 とは同時に出ない（横へ動く先がふさがる・狭まる、隠れる/寄る先とぶつかる）。A-450（重力）とは一緒に出てよい。</li>
 * </ul>
 */
public class A200Entity extends AbyssEnemy {
    public static final double DEPTH_ROOMS = 2.0, START_ROOMS = 2.8, MOVE = 0.4, SPEED = 1.0, SPEED_HARD = 1.1, HIT_R = 0.8, SHOT_PAST = 6;
    public static final float DAMAGE = 4f, DAMAGE_HARD = 6f;
    public static final int AIM = 36, AIM_HARD = 30, CLICK = 10, REST = 24, REST_HARD = 18, VOLLEYS = 5, VOLLEYS_HARD = 7, GRACE = 30, WAIT_MAX = 20 * 30;
    private static final DustParticleOptions AIM_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 0.9f);

    private enum State { WAIT, AIM, FLY, DONE }
    private State state = State.WAIT;
    private int stateTicks = 0, fired = 0, waited = 0;
    private double aimX, aimZ, shotY;
    private final Set<UUID> hitThisShot = new HashSet<>();

    public A200Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-200"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A250Entity.class, A300Entity.class};
    }

    /** 予告の長さ：止まった状態からスニークで線から HIT_R+0.05 離れる時間 × 余裕 − 弾が届く最短の時間。 */
    int aimTicks() {
        double speed = hardSpawn ? SPEED_HARD : SPEED;
        int travel = (int) Math.floor((DEPTH_ROOMS * AbyssRooms.L - 1.8) / (speed + AbyssRooms.MAX_FALL));
        int need = (int) Math.ceil(AbyssSqueeze.sneakTicks(HIT_R + 0.05) * (hardSpawn ? AbyssSqueeze.MARGIN_HARD : AbyssSqueeze.MARGIN));
        return Math.max(hardSpawn ? AIM_HARD : AIM, need - travel);
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level)) { RoomsDebug.log("A-200 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t) && !anyAlive(level, A450Entity.class)) return null;
        targetId = t.getUUID();
        Vec3 p = pv(rs, t);
        setVirtualPos(new Vec3(cx(rs.abyss), p.y - START_ROOMS * AbyssRooms.L, cz(rs.abyss)));
        RoomsDebug.log("A-200 prepare target={} volleys={}", t.getGameProfile().getName(), hardSpawn ? VOLLEYS_HARD : VOLLEYS);
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        stateTicks++;
        if (life == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
            if (t != null) RoomsDebug.announce(java.util.List.of(t), "A-200 が下から狙っているわ", "A-200 telegraph aim={} ticks", aimTicks());
        }
        if (t == null || state == State.DONE) { // 撃ち終わった・いなくなった：沈んで消える
            setVirtualPos(v.add(0, -0.4, 0));
            if (stateTicks > 40) leave(t == null ? "target gone" : "all shots fired");
            return;
        }
        Vec3 p = pv(rs, t);
        // プレイヤーの DEPTH_ROOMS 部屋下を保つ（真ん中あたりで少し揺れる）
        double ny = v.y + Math.max(-MOVE, Math.min(MOVE, p.y - DEPTH_ROOMS * AbyssRooms.L - v.y));
        setVirtualPos(clampInner(rs.abyss, new Vec3(cx(rs.abyss) + Math.sin(life * 0.06) * 0.8, ny, cz(rs.abyss) + Math.cos(life * 0.045) * 0.8), 0.3));
        if (life < GRACE) return;
        switch (state) {
            case WAIT -> {
                if (stateTicks <= 0) return; // 休み
                if (sheltered(rs, t, 0.15)) { if (++waited > WAIT_MAX) setState(State.DONE); return; }
                Vec3 a = clampInner(rs.abyss, p, 0.3);
                aimX = a.x; aimZ = a.z;
                setState(State.AIM);
                level.playSound(null, t.getX(), t.getY() - 6, t.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
                RoomsDebug.log("A-200 aim {}/{} at ({}, {}) for {} ticks", fired + 1, maxVolleys(), f(aimX - cx(rs.abyss)), f(aimZ - cz(rs.abyss)), aimTicks());
            }
            case AIM -> {
                int left = aimTicks() - stateTicks;
                aimLine(level, rs, t, left <= CLICK);
                if (left == CLICK) level.playSound(null, t.getX(), t.getY() - 6, t.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.HOSTILE, 2.5f, 0.8f);
                if (left <= 0) {
                    shotY = Math.min(virtualPos().y, p.y - DEPTH_ROOMS * AbyssRooms.L) + 1.0;
                    hitThisShot.clear();
                    fired++;
                    setState(State.FLY);
                    level.playSound(null, t.getX(), t.getY() - 8, t.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 3.0f, 0.5f);
                }
            }
            case FLY -> tickShot(level, rs, t);
            case DONE -> {}
        }
    }

    private int maxVolleys() { return hardSpawn ? VOLLEYS_HARD : VOLLEYS; }

    /** 弾を上へ進めて当たりを見る。通り過ぎたら休んで次の狙い（全部撃ったら DONE）。 */
    private void tickShot(ServerLevel level, RoomsState rs, Player t) {
        double prev = shotY;
        shotY += hardSpawn ? SPEED_HARD : SPEED;
        Vec3 p = pv(rs, t);
        Vec3 shot = new Vec3(aimX, shotY, aimZ);
        if (Math.abs(shotY - p.y) < 30) for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q)) {
            Vec3 ph = nearPhysical(rs, t, shot);
            level.sendParticles(sp, ParticleTypes.SOUL_FIRE_FLAME, true, ph.x, ph.y, ph.z, 4, 0.12, 0.3, 0.12, 0.01);
            level.sendParticles(sp, ParticleTypes.SMOKE, true, ph.x, ph.y - 0.6, ph.z, 2, 0.1, 0.3, 0.1, 0.0);
        }
        for (Player q : level.players()) {
            if (!inShaft(rs, q) || hitThisShot.contains(q.getUUID())) continue;
            Vec3 qv = pv(rs, q);
            if (shotY < qv.y - 0.3 || prev > qv.y + 2.1) continue; // まだ下・もう上
            if (Math.hypot(qv.x - aimX, qv.z - aimZ) >= HIT_R) continue;
            if (sheltered(rs, q, 0.15)) continue;
            hitThisShot.add(q.getUUID());
            RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD));
            RoomsDebug.log("A-200 hit {}", q.getGameProfile().getName());
        }
        if (shotY > p.y + SHOT_PAST || stateTicks > 100) {
            if (fired >= maxVolleys()) setState(State.DONE);
            else { setState(State.WAIT); stateTicks = -(hardSpawn ? REST_HARD : REST); }
        }
    }

    /** 狙いの線：固定した (x, z) の、プレイヤーの 10 ブロック下から 2 ブロック上まで（縦穴の中のプレイヤーにだけ）。 */
    private void aimLine(ServerLevel level, RoomsState rs, Player t, boolean soon) {
        double y0 = t.getY() - 10, y1 = t.getY() + 2.5;
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q)) {
            for (int i = 0; i < 8; i++) {
                double y = y0 + random.nextDouble() * (y1 - y0);
                level.sendParticles(sp, AIM_DUST, true, aimX, y, aimZ, 1, 0, 0, 0, 0);
            }
            if (soon) {
                ParticleOptions o = ParticleTypes.ELECTRIC_SPARK;
                level.sendParticles(sp, o, true, aimX, t.getY() - 3 + random.nextDouble() * 4, aimZ, 2, 0.05, 0.2, 0.05, 0.0);
            }
        }
    }

    @Override
    protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 1.4 + random.nextDouble() * 0.4, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.08, 0);
    }

    @Override protected String debugState() { return "state=" + state + " fired=" + fired + "/" + maxVolleys() + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("State", state.name()); t.putInt("StateTicks", stateTicks); t.putInt("Fired", fired); t.putInt("Waited", waited);
        t.putDouble("AimX", aimX); t.putDouble("AimZ", aimZ); t.putDouble("ShotY", shotY);
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.WAIT; }
        if (state == State.AIM || state == State.FLY) state = State.WAIT; // 読み込み直後は予告し直す
        stateTicks = 0; fired = t.getInt("Fired"); waited = t.getInt("Waited");
        aimX = t.getDouble("AimX"); aimZ = t.getDouble("AimZ"); shotY = t.getDouble("ShotY");
    }
}
