package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
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

/**
 * A-600「逆流」：下から風が吹き上げて、しばらく落ちるのがとても遅くなる。そのあいだに下から何かが昇ってくる。
 * <ul>
 *   <li>予告：{@value #WARN} tick、風の音と、プレイヤーのまわりを上へ昇る粒（雲・白い灰）。この間はふつうに落ちる。</li>
 *   <li>逆流：{@value #UPDRAFT} tick（ハード {@value #UPDRAFT_HARD}）。縦の速さを {@value #SLOW} ブロック/tick（下向き）に固定（AbyssRooms.hold、横は自由）。
 *       ロッカーに隠れている間は固定しない。</li>
 *   <li>昇ってくるもの：逆流の始めにプレイヤーの {@value #START_BELOW} ブロック下から、{@value #ARRIVE} tick（ハード {@value #ARRIVE_HARD}）でプレイヤーの高さまで来る。
 *       着いたら横へ {@value #SIDE} ブロック/tick（ハード {@value #SIDE_HARD}）で追う。横 {@value #HIT_R}・縦 1.2 ブロック以内に入ると
 *       ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 回だけ（当てたら下へ沈んで逆流も終わる）。</li>
 *   <li>避け方：(1) 横穴（体の中心）・ロッカーに入る：昇ってくるものは見失って下へ沈む（逆流が終わるまで出てこない）。
 *       出現の条件は、予告と逆流の間に落ちて届く高さ（足から 1〜{@value #REACH} ブロック下）に、危なくない本物の横穴があること。
 *       (2) 逆流が終わるまで逃げ切る：空中を歩けば（約 0.2 ブロック/tick）追う速さより速いので、縦穴の中をぐるっと回れば当たらない（スニークでは逃げ切れない）。</li>
 *   <li>終わる・消えるときは必ず放す（AbyssRooms も期限で自動的に放す）。ほかの A の敵とは同時に出ない（引き止め・隠れる先・止まるなとぶつかる）。</li>
 * </ul>
 */
public class A600Entity extends AbyssEnemy {
    public static final int WARN = 40, UPDRAFT = 200, UPDRAFT_HARD = 220, ARRIVE = 100, ARRIVE_HARD = 90;
    public static final float SLOW = 0.06f;
    public static final double START_BELOW = 24, SIDE = 0.05, SIDE_HARD = 0.065, HIT_R = 0.9, REACH = 12;
    public static final float DAMAGE = 6f, DAMAGE_HARD = 10f;

    private enum State { WARN, UPDRAFT, SINK }
    private State state = State.WARN;
    private int stateTicks = 0;
    private int alcoveRoom = -1;
    /** プレイヤー（胸の高さ）から見た縦のずれ（仮想座標、下が負）。 */
    private double rel = -START_BELOW;

    public A600Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-600"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override public int reservedAlcove() { return state == State.SINK ? -1 : alcoveRoom; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class,
                A300Entity.class, A350Entity.class, A450Entity.class, A525Entity.class, A666Entity.class, A750Entity.class, A800Entity.class, A1000Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A600Entity.class)) { RoomsDebug.log("A-600 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t) || AbyssRooms.isHeld(t)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        Vec3 p = pv(rs, t);
        java.util.Set<Integer> bad = dangerAlcoves(level);
        int found = -1;
        for (int r = Math.max(room - 1, rs.abyss.entryRoom); r <= room + 2 && found < 0; r++) {
            if (!AbyssSpace.isAbyssRoom(rs, r) || bad.contains(r)) continue;
            AbyssRooms.AlcoveSpot a = AbyssRooms.alcoveSpot(level, rs.abyss, r);
            if (a != null && a.floorY() <= p.y - 1 && a.floorY() >= p.y - REACH) found = r;
        }
        if (found < 0) { RoomsDebug.log("A-600 prepare skipped: no free alcove 1..{} blocks below (room {})", (int) REACH, room); return null; }
        targetId = t.getUUID();
        alcoveRoom = found;
        setVirtualPos(clampInner(rs.abyss, new Vec3(p.x, p.y - START_BELOW - 6, p.z), 0.4));
        RoomsDebug.log("A-600 prepare target={} room={} alcoveRoom={}", t.getGameProfile().getName(), room, found);
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    private void release(ServerLevel level, Player t, String why) {
        if (t instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) {
            AbyssRooms.release(sp);
            sp.fallDistance = 0;
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        if (state != State.SINK) RoomsDebug.log("A-600 updraft end: {}", why);
        setState(State.SINK);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        stateTicks++;
        if (t == null || state == State.SINK) {
            if (t == null && state != State.SINK) release(level, targetId == null ? null : level.getPlayerByUUID(targetId), "target gone");
            setVirtualPos(v.add(0, -0.4, 0)); // 下へ沈んで消える
            if (stateTicks > 40) leave("sank");
            return;
        }
        Vec3 p = pv(rs, t).add(0, 0.9, 0);
        if (life == 1) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 3.0f, 0.5f);
            RoomsDebug.announce(java.util.List.of(t), "A-600：下から風（" + com.example.roomsmod.world.section.Sections.formatRoom(rs, alcoveRoom) + " の横穴へ、または逃げ回る）",
                    "A-600 telegraph alcoveRoom={}", alcoveRoom);
        }
        switch (state) {
            case WARN -> {
                gust(level, rs, t, 4);
                setVirtualPos(clampInner(rs.abyss, new Vec3(v.x, p.y - START_BELOW - 6, v.z), 0.4)); // まだ見えない深さで一緒に落ちる
                if (stateTicks % 10 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.6f, 0.6f + stateTicks / 100f);
                if (stateTicks >= WARN) {
                    setState(State.UPDRAFT);
                    rel = -START_BELOW;
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
                    RoomsDebug.log("A-600 updraft start vy={} arrive={}", -SLOW, hardSpawn ? ARRIVE_HARD : ARRIVE);
                }
            }
            case UPDRAFT -> {
                if (!(t instanceof ServerPlayer sp)) { release(level, t, "not a server player"); return; }
                gust(level, rs, t, 2);
                if (stateTicks % 25 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 2.0f, 0.5f);
                if (stateTicks >= (hardSpawn ? UPDRAFT_HARD : UPDRAFT)) { release(level, t, "time over"); return; }
                boolean hiding = HidingRules.isHiding(t);
                if (hiding) { if (AbyssRooms.isHeld(sp)) AbyssRooms.release(sp); } // ロッカーの中は動かさない
                else AbyssRooms.hold(sp, -SLOW, 3);
                // 昇ってくる：ずれを一定の量ずつ詰める（プレイヤーの落ちる速さに関係なく ARRIVE tick で着く）
                double close = START_BELOW / (hardSpawn ? ARRIVE_HARD : ARRIVE);
                rel = Math.min(0, rel + close);
                if (sheltered(rs, t, 0.2)) { // 隠れた：見失って沈む（逆流は終わるまで続く）
                    if (rel > -8) rel = Math.max(rel - 0.4, -START_BELOW);
                    setVirtualPos(clampInner(rs.abyss, new Vec3(v.x, p.y + rel, v.z), 0.4));
                    return;
                }
                double nx = v.x, nz = v.z;
                if (rel > -3) { // 着いた：横へ追う
                    double side = hardSpawn ? SIDE_HARD : SIDE;
                    nx += Math.max(-side, Math.min(side, p.x - v.x));
                    nz += Math.max(-side, Math.min(side, p.z - v.z));
                }
                setVirtualPos(clampInner(rs.abyss, new Vec3(nx, p.y + rel, nz), 0.4));
                if (rel > -6) faceTowards(position().add(p.subtract(virtualPos())));
                double dh = Math.hypot(p.x - nx, p.z - nz);
                if (rel > -1.2 && dh < HIT_R) {
                    boolean hit = RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 2.5f, 0.5f);
                    RoomsDebug.log("A-600 hit {} applied={} dh={}", t.getGameProfile().getName(), hit, f(dh));
                    release(level, t, "hit");
                }
            }
            default -> {}
        }
    }

    /** 上へ昇る風の粒（縦穴の中のプレイヤーにだけ）。 */
    private void gust(ServerLevel level, RoomsState rs, Player t, int n) {
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q))
            for (int i = 0; i < n; i++) {
                double x = cx(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, z = cz(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, y = t.getY() - 3 + random.nextDouble() * 5;
                level.sendParticles(sp, i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_ASH, true, x, y, z, 0, 0, 0.45, 0, 1.0);
            }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず放す（AbyssRooms も期限で自動的に放す）
        if (level() instanceof ServerLevel sl && targetId != null && sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) { AbyssRooms.release(sp); sp.fallDistance = 0; }
        super.onRemovedFromLevel();
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(2) == 0)
            level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + random.nextDouble() * 2.0, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.05, 0);
    }

    @Override protected String debugState() { return "state=" + state + " rel=" + f(rel) + " alcoveRoom=" + alcoveRoom + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("AlcoveRoom", alcoveRoom); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        alcoveRoom = t.getInt("AlcoveRoom");
        state = State.SINK; // 読み込み直後は引き止め直さない（沈んで消える）
        stateTicks = 0;
    }
}
