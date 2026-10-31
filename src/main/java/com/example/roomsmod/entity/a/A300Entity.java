package com.example.roomsmod.entity.a;

import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.AbyssSqueeze;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
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

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * A-300：プレイヤーの影。同じ高さを一緒に落ちながら、プレイヤーが {@value #DELAY} tick（ハード {@value #DELAY_HARD}）前にいた
 * 横の位置（x, z）をなぞる。今いる場所が「さっきいた場所」に近いと（横の距離 {@value #HIT_R} 未満）触れてダメージ。
 * <ul>
 *   <li>予告：{@link #appearTicks()} tick の間、影は見えていて追ってくるが触れても平気（低い声と煙）。この間に動き始める。
 *       足元には、影がこれからなぞる道（自分の少し前の通り道）に薄い煙が出る。</li>
 *   <li>本番：{@value #ACTIVE} tick（ハード {@value #ACTIVE_HARD}）。横穴・ロッカーに入ると見失い、出てきてから予告と同じ長さは触れても平気。触れるとダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、
 *       そのあと {@value #COOLDOWN} tick は影が薄れて触れても平気（続けて当たらない）。終わると消える。</li>
 *   <li>避け方：止まらずに動き続ける（行ったり来たりではなく、ぐるっと回る）。スニークの空中の速さ（約 0.06 ブロック/tick）でも
 *       {@value #DELAY} tick で 1.5 ブロック以上進むので、半径 1.5 の円を回れば影との距離は 1.4 ブロック以上（ハードでも 1.3 以上）。
 *       または横穴（体の中心）・ロッカーへ入る：影は縦穴の中だけ、隠れていれば当たらない。出現の条件は横穴が 3+2 部屋以内にあること。</li>
 *   <li>予告の長さ：止まった状態からスニークで {@value #HIT_R}+0.05 ブロック動く時間（反応込み 30 tick）の 1.5 倍（ハード 1.35 倍）以上。</li>
 *   <li>A-50 / A-90 / A-180 / A-200 / A-250 / A-450 とは同時に出ない（止められる・狭まる・寄る先が決まる・重力で動きにくい）。</li>
 * </ul>
 */
public class A300Entity extends AbyssEnemy {
    public static final double HIT_R = 0.6;
    public static final float DAMAGE = 3f, DAMAGE_HARD = 4f;
    public static final int DELAY = 30, DELAY_HARD = 24, APPEAR = 50, APPEAR_HARD = 44, ACTIVE = 160, ACTIVE_HARD = 200, COOLDOWN = 40;

    private enum State { APPEAR, ACTIVE, GONE }
    private State state = State.APPEAR;
    private int stateTicks = 0, cooldown = 0;
    /** プレイヤーの横の位置の履歴（新しいものが後ろ）。 */
    private final ArrayDeque<double[]> hist = new ArrayDeque<>();

    public A300Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-300"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A50Entity.class, A90Entity.class, A180Entity.class, A200Entity.class, A250Entity.class, A450Entity.class};
    }

    private int delay() { return hardSpawn ? DELAY_HARD : DELAY; }

    int appearTicks() {
        int need = (int) Math.ceil(AbyssSqueeze.sneakTicks(HIT_R + 0.05) * (hardSpawn ? AbyssSqueeze.MARGIN_HARD : AbyssSqueeze.MARGIN));
        return Math.max(hardSpawn ? APPEAR_HARD : APPEAR, need);
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level)) { RoomsDebug.log("A-300 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        Vec3 p = pv(rs, t);
        if (freeAlcoveBelow(level, rs, room, AbyssSpace.MAX_ALCOVE_GAP + 2, p.y + 1) < 0) { RoomsDebug.log("A-300 prepare skipped: no free alcove near {}", room); return null; }
        targetId = t.getUUID();
        setVirtualPos(clampInner(rs.abyss, p, 0.3));
        RoomsDebug.log("A-300 prepare target={} delay={} appear={}", t.getGameProfile().getName(), delay(), appearTicks());
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (t == null || state == State.GONE) {
            if (state != State.GONE) setState(State.GONE);
            setVirtualPos(virtualPos().add(0, -0.3, 0));
            if (stateTicks > 20) leave(t == null ? "target gone" : "faded");
            return;
        }
        Vec3 p = pv(rs, t);
        if (life == 1) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.5f, 0.6f);
            RoomsDebug.announce(java.util.List.of(t), "A-300 があなたの影を踏んでいるわ（動き続けて）", "A-300 telegraph appear={} delay={}", appearTicks(), delay());
        }
        // 履歴：縦穴の中だけ（横穴の中は入口の手前に収める）
        Vec3 in = clampInner(rs.abyss, p, 0.3);
        hist.addLast(new double[]{in.x, in.z});
        while (hist.size() > delay() + 1) hist.removeFirst();
        double[] at = hist.peekFirst();
        setVirtualPos(new Vec3(at[0], p.y, at[1]));
        trail(level, rs, t);
        if (stateTicks % 30 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 1.2f, 0.5f);
        switch (state) {
            case APPEAR -> { if (stateTicks >= appearTicks()) { setState(State.ACTIVE); level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.8f); } }
            case ACTIVE -> {
                // 横穴・ロッカーの中：見失う。出てきたら予告と同じ長さは触れても平気（入口に居座った影に出口で当たらない）
                if (sheltered(rs, t, 0.15)) cooldown = Math.max(cooldown, appearTicks());
                else if (cooldown > 0) cooldown--;
                else if (Math.hypot(p.x - at[0], p.z - at[1]) < HIT_R) {
                    cooldown = COOLDOWN;
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.5f, 0.7f);
                    RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
                    RoomsDebug.log("A-300 touched {}", t.getGameProfile().getName());
                }
                if (stateTicks >= (hardSpawn ? ACTIVE_HARD : ACTIVE)) setState(State.GONE);
            }
            default -> {}
        }
    }

    /** 影がこれからなぞる道（履歴）に薄い煙。本番中で薄れていない時は濃く。 */
    private void trail(ServerLevel level, RoomsState rs, Player t) {
        if (stateTicks % 2 != 0) return;
        boolean live = state == State.ACTIVE && cooldown == 0;
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q)) {
            int i = 0;
            for (Iterator<double[]> it = hist.iterator(); it.hasNext(); i++) {
                double[] h = it.next();
                if (i % 4 != 0) continue;
                level.sendParticles(sp, live ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, true, h[0], t.getY() + 0.1, h[1], 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    @Override
    protected void clientTick() {
        level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
    }

    @Override protected String debugState() { return "state=" + state + " t=" + stateTicks + " cooldown=" + cooldown + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("StateTicks", stateTicks); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.APPEAR; }
        if (state == State.ACTIVE) state = State.APPEAR; // 履歴は保存しない：読み込み直後は予告からやり直す
        stateTicks = 0;
    }
}
