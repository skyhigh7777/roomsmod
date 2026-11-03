package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssFx;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
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

/**
 * A-525「暗転」：数秒おきに縦穴が一瞬まっ暗になる。姿はない。見えるのは落ちてくる光る粒だけ。
 * <ul>
 *   <li>予告（音）：暗転の {@value #CUE} tick（ハード {@value #CUE_HARD}）前に低い鐘の音。暗転の瞬間にビーコンの消える音。</li>
 *   <li>暗転：{@link #darkTicks()} tick。クライアントは光る粒以外を真っ黒にする（AbyssFx / client/AbyssFxClient）。
 *       縦穴の内側いっぱいに白い光の粒（END_ROD）がゆっくり落ちてくる（向き・壁の位置の手がかり）。</li>
 *   <li>危険：暗転して {@value #MARK_DELAY} tick 後、その時のプレイヤーの横の位置を中心に半径 {@value #HAZ_R} の輪を、橙の光る粒（WAX_ON）で示す（狙いは固定）。
 *       さらに {@value #STRIKE} tick（ハード {@value #STRIKE_HARD}）後に輪の中を打つ：横の距離が {@value #HAZ_R} 未満ならダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）。
 *       横穴（体の中心）・ロッカーの中は当たらない。打つ {@value #CLICK} tick 前にもう一度カチッと音。</li>
 *   <li>間に合うか：縦穴の内側は 4.4 ブロック幅なので、どこからでも {@value #HAZ_R} ブロック以上離れる向きが必ずある。空中を歩いて約 0.2 ブロック/tick
 *       → 反応 10 tick 込みで約 17 tick（通常 40・ハード 32 tick の猶予。スニークだと約 33 tick なのでハードでは歩くこと）。</li>
 *   <li>全部で {@value #CYCLES} 回（ハード {@value #CYCLES_HARD}）、暗転のあと {@value #REST} tick は明るい。終わる・消えるときは必ず暗転を解く（クライアントも長さを数えて自分で戻る）。</li>
 *   <li>A-1000 以外の A の敵とは同時に出ない（見て避ける敵・隠れる敵と、暗さがぶつかる。画面を隠す演出も重ねない）。</li>
 * </ul>
 */
public class A525Entity extends AbyssEnemy {
    public static final int CUE = 30, CUE_HARD = 24, MARK_DELAY = 6, STRIKE = 40, STRIKE_HARD = 32, CLICK = 8, AFTER = 14, REST = 80;
    public static final int CYCLES = 3, CYCLES_HARD = 4;
    public static final double HAZ_R = 1.4;
    public static final float DAMAGE = 6f, DAMAGE_HARD = 9f;

    private enum State { CUE, DARK, REST, GONE }
    private State state = State.CUE;
    private int stateTicks = 0, cycle = 0;
    private double hx = Double.NaN, hz = Double.NaN;
    private boolean struck = false;

    public A525Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-525"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class,
                A300Entity.class, A350Entity.class, A450Entity.class, A600Entity.class, A666Entity.class, A750Entity.class, A800Entity.class,
                A1111Entity.class};
    }

    private int strike() { return hardSpawn ? STRIKE_HARD : STRIKE; }
    private int darkTicks() { return MARK_DELAY + strike() + AFTER; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A525Entity.class)) { RoomsDebug.log("A-525 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t) || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-525 prepare target={} cycles={} dark={} strike={}", t.getGameProfile().getName(), hardSpawn ? CYCLES_HARD : CYCLES, darkTicks(), strike());
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (state == State.GONE || t == null) {
            if (state != State.GONE) { lightUp(level, "target gone"); setState(State.GONE); }
            if (stateTicks > 5) leave("done");
            return;
        }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        if (life == 1) RoomsDebug.announce(java.util.List.of(t), "A-525：鐘が鳴ったら暗くなる。光の輪から離れる", "A-525 telegraph target={}", t.getGameProfile().getName());
        switch (state) {
            case CUE -> {
                if (stateTicks == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.5f, 0.5f);
                if (stateTicks == 2) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0f, 0.5f);
                if (stateTicks >= (hardSpawn ? CUE_HARD : CUE)) {
                    setState(State.DARK);
                    struck = false; hx = Double.NaN;
                    if (t instanceof ServerPlayer sp) AbyssFx.dark(sp, darkTicks());
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.5f, 0.5f);
                    RoomsDebug.log("A-525 dark cycle={} ticks={}", cycle + 1, darkTicks());
                }
            }
            case DARK -> {
                motes(level, rs, t);
                if (stateTicks == MARK_DELAY) { hx = t.getX(); hz = t.getZ(); RoomsDebug.log("A-525 mark at ({}, {})", f(hx), f(hz)); }
                if (!Double.isNaN(hx) && !struck) {
                    ring(level, rs, t);
                    if (stateTicks == MARK_DELAY + strike() - CLICK) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.HOSTILE, 2.0f, 0.6f);
                    if (stateTicks >= MARK_DELAY + strike()) strike(level, rs);
                }
                if (stateTicks >= darkTicks()) {
                    cycle++;
                    lightUp(level, "cycle end");
                    setState(cycle >= (hardSpawn ? CYCLES_HARD : CYCLES) ? State.GONE : State.REST);
                    if (state == State.GONE) RoomsDebug.log("A-525 finished cycles={}", cycle);
                }
            }
            case REST -> { if (stateTicks >= REST) setState(State.CUE); }
            default -> {}
        }
    }

    /** 輪の中を打つ（1 回の暗転につき 1 回）。 */
    private void strike(ServerLevel level, RoomsState rs) {
        struck = true;
        for (Player q : level.players()) {
            if (!inShaft(rs, q)) continue;
            if (q instanceof ServerPlayer sp) for (int i = 0; i < 24; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * HAZ_R;
                level.sendParticles(sp, ParticleTypes.ELECTRIC_SPARK, true, hx + Math.cos(a) * r, q.getY() + random.nextDouble() * 2, hz + Math.sin(a) * r, 1, 0, 0.3, 0, 0.2);
            }
        }
        Player t = target(level, rs);
        if (t == null) return;
        level.playSound(null, hx, t.getY(), hz, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.5f, 0.6f);
        for (Player q : level.players()) {
            if (!inShaft(rs, q)) continue;
            double dx = q.getX() - hx, dz = q.getZ() - hz;
            double d = Math.sqrt(dx * dx + dz * dz);
            boolean safe = sheltered(rs, q, 0.2);
            RoomsDebug.log("A-525 strike player={} dist={} sheltered={} -> {}", q.getGameProfile().getName(), f(d), safe, d < HAZ_R && !safe ? "hit" : "miss");
            if (d < HAZ_R && !safe) RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD));
        }
    }

    /** 暗転を解く（縦穴の中のプレイヤー全員。狙いのプレイヤー以外に送っても害はない）。 */
    private void lightUp(ServerLevel level, String why) {
        if (targetId != null && level.getPlayerByUUID(targetId) instanceof ServerPlayer sp) AbyssFx.dark(sp, 0);
        RoomsDebug.log("A-525 light up: {}", why);
    }

    private void send(ServerLevel level, RoomsState rs, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q)) level.sendParticles(sp, type, true, x, y, z, 0, vx, vy, vz, 1.0);
    }

    /** 縦穴の内側いっぱいに落ちてくる白い光の粒（壁の位置・向きの手がかり）。 */
    private void motes(ServerLevel level, RoomsState rs, Player t) {
        AbyssRooms.Shaft s = rs.abyss;
        for (int i = 0; i < 4; i++)
            send(level, rs, ParticleTypes.END_ROD, cx(s) + (random.nextDouble() - 0.5) * 4.8, t.getY() + 3 + random.nextDouble() * 4, cz(s) + (random.nextDouble() - 0.5) * 4.8, 0, -0.08, 0);
        // 四隅の柱：壁の角を示す
        if (stateTicks % 3 == 0) for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            send(level, rs, ParticleTypes.END_ROD, cx(s) + sx * 2.4, t.getY() + 2.5, cz(s) + sz * 2.4, 0, -0.12, 0);
    }

    /** 危ない輪（橙の光る粒）。プレイヤーの高さのまわりに出す。 */
    private void ring(ServerLevel level, RoomsState rs, Player t) {
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            send(level, rs, ParticleTypes.WAX_ON, hx + Math.cos(a) * HAZ_R, t.getY() - 0.5 + random.nextDouble() * 3, hz + Math.sin(a) * HAZ_R, 0, -0.1, 0);
        }
        if (stateTicks % 2 == 0) send(level, rs, ParticleTypes.WAX_ON, hx + (random.nextDouble() - 0.5) * HAZ_R, t.getY() + 2.5, hz + (random.nextDouble() - 0.5) * HAZ_R, 0, -0.15, 0);
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず暗転を解く
        if (level() instanceof ServerLevel sl && targetId != null && sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp) AbyssFx.dark(sp, 0);
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "state=" + state + " cycle=" + cycle + " t=" + stateTicks + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Cycle", cycle); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        cycle = t.getInt("Cycle");
        state = State.REST; // 読み込み直後は明るいところから（予告からやり直す）
        stateTicks = 0;
    }
}
