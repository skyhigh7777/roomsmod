package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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

/**
 * A-50：上から一緒に降りてきて、横穴を素通りしたプレイヤーを糸で捕まえ、落下を止めて少し上へ引き戻す。
 * <ul>
 *   <li>出現時に鎖の音（予告）。出現の条件は「プレイヤーの足より 1 部屋以上下に、危なくない横穴がある」（{@value AbyssSpace#MAX_ALCOVE_GAP}+2 部屋以内）。
 *       その横穴（予約。A-90 は潜まない）を素通りする（床より下へ落ちる）まで捕まえない。</li>
 *   <li>横穴に入る・ロッカーに隠れると、しばらくで諦めて上へ消える（{@value #LOSE_INTEREST} tick）。</li>
 *   <li>素通りしたら糸を垂らす（{@value #CAST} tick、ハード {@value #CAST_HARD}、パーティクルの線と音）。そのあと見えていれば捕まえる：
 *       ダメージ {@value #GRAB_DAMAGE}（ハード {@value #GRAB_DAMAGE_HARD}）、落下が止まり毎 tick {@value #DRAG} ずつ上へ（横は自由に動ける）。
 *       素通りした横穴の高さまで引き戻したらそこで止まる。</li>
 *   <li>放す：横穴に入る（視線が切れる）・見えなくなる・{@value #HOLD_MAX} tick（ハード {@value #HOLD_MAX_HARD}）たつ。放したら上へ消える。</li>
 *   <li>A-25 / A-90 / A-180 がいる間は出ない（出てきたら放して消える）：引き止めで隠れるのが遅れたり、A-90 の横穴の前・避けた雲へ戻されたりしないように。
 *       引き止め中は AbyssRooms の見張り（止まっている判定）を数えない。テレポートはしない（1 周の処理はそのまま）。</li>
 * </ul>
 */
public class A50Entity extends AbyssEnemy {
    public static final double START_ABOVE = 14, FOLLOW_REL = 8, APPROACH = 0.05, RISE_AWAY = 0.4;
    public static final float DRAG = 0.1f, GRAB_DAMAGE = 2f, GRAB_DAMAGE_HARD = 3f;
    public static final int WARN_MIN = 40, CAST = 24, CAST_HARD = 20, HOLD_MAX = 60, HOLD_MAX_HARD = 70, LOSE_INTEREST = 30, STALK_MAX = 20 * 40;
    private static final DustParticleOptions THREAD = new DustParticleOptions(new Vector3f(0.75f, 0.75f, 0.8f), 0.6f);

    private enum State { STALK, CAST, HOLD, GONE }
    private State state = State.STALK;
    private int alcoveRoom = -1, stateTicks = 0, sheltered = 0;
    private double rel = START_ABOVE;

    public A50Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-50"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override public int reservedAlcove() { return state == State.STALK || state == State.CAST ? alcoveRoom : -1; }

    /** 一緒にいると引き止めが危なくなる敵（隠れるのが遅れる・A-90 の横穴の前や避けた雲へ引き戻される）。 */
    private static boolean blockers(ServerLevel level) { return anyAlive(level, A25Entity.class, A90Entity.class, A180Entity.class); }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t) || AbyssRooms.isHeld(t)) return null;
        if (blockers(level)) { RoomsDebug.log("A-50 prepare skipped: A-25 / A-90 / A-180 alive"); return null; }
        int room = AbyssRooms.roomOf(t, playerRoom);
        Vec3 p = pv(rs, t);
        int r = freeAlcoveBelow(level, rs, room, AbyssSpace.MAX_ALCOVE_GAP + 2, p.y - AbyssRooms.L);
        if (r < 0) { RoomsDebug.log("A-50 prepare skipped: no free alcove a room below {}", room); return null; }
        targetId = t.getUUID();
        alcoveRoom = r;
        setVirtualPos(new Vec3(cx(rs.abyss), p.y + START_ABOVE, cz(rs.abyss)));
        RoomsDebug.log("A-50 prepare target={} room={} alcoveRoom={}", t.getGameProfile().getName(), room, r);
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    private void letGo(ServerLevel level, Player t, String why) {
        if (t instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) AbyssRooms.release(sp);
        if (state != State.GONE) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 0.7f);
            RoomsDebug.log("A-50 lets go: {}", why);
        }
        setState(State.GONE);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null || alcoveRoom < 0) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        stateTicks++;
        if (state == State.GONE || t == null) {
            if (t == null && state != State.GONE) { Player raw = targetId == null ? null : level.getPlayerByUUID(targetId); letGo(level, raw, "target gone"); }
            setVirtualPos(v.add(0, RISE_AWAY, 0));
            if (stateTicks > 40) leave("gone");
            return;
        }
        if (life == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 3.0f, 0.5f);
            RoomsDebug.announce(java.util.List.of(t), "A-50 が来るわ（上・" + com.example.roomsmod.world.section.Sections.formatRoom(rs, alcoveRoom) + " の横穴へ）",
                    "A-50 telegraph alcoveRoom={}", alcoveRoom);
        }
        if (blockers(level)) { letGo(level, t, "A-25 / A-90 / A-180 appeared"); return; }
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, alcoveRoom);
        if (spot == null) { letGo(level, t, "alcove missing"); return; }
        Vec3 p = pv(rs, t);
        boolean safe = sheltered(rs, t, 0.15) || AbyssRooms.inAnyAlcove(rs.abyss, t);
        switch (state) {
            case STALK -> {
                follow(rs, p);
                if (life % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_STEP, SoundSource.HOSTILE, 2.5f, 0.5f);
                if (safe) { if (++sheltered >= LOSE_INTEREST) letGo(level, t, "target sheltered"); return; }
                sheltered = 0;
                if (life >= WARN_MIN && p.y < spot.floorY() - 1.0) {
                    setState(State.CAST);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 3.0f, 0.6f);
                    RoomsDebug.log("A-50 cast: target passed alcove room {} (rel {})", alcoveRoom, f(p.y - spot.floorY()));
                } else if (life > STALK_MAX) letGo(level, t, "stalk timeout");
            }
            case CAST -> {
                follow(rs, p);
                thread(level, rs, t, 3);
                if (safe) { letGo(level, t, "target sheltered while casting"); return; }
                if (stateTicks >= (hardSpawn ? CAST_HARD : CAST)) {
                    if (!canSeeVirtual(t)) { letGo(level, t, "no line of sight"); return; }
                    setState(State.HOLD);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 3.0f, 1.2f);
                    RoomsDamage.hurt(this, t, hardDamage(GRAB_DAMAGE, GRAB_DAMAGE_HARD));
                    RoomsDebug.log("A-50 grabbed {}", t.getGameProfile().getName());
                }
            }
            case HOLD -> {
                if (!(t instanceof ServerPlayer sp)) { letGo(level, t, "not a server player"); return; }
                thread(level, rs, t, 6);
                if (safe || HidingRules.isHiding(t)) { letGo(level, t, "target reached an alcove"); return; }
                if (stateTicks > 5 && !canSeeVirtual(t)) { letGo(level, t, "line of sight broken"); return; }
                if (stateTicks >= (hardSpawn ? HOLD_MAX_HARD : HOLD_MAX)) { letGo(level, t, "hold time over"); return; }
                // 素通りした横穴の高さ（床の少し上）まで引き戻したら止める。小部屋の床・天井には近づけない
                boolean up = p.y < spot.floorY() + 0.2 && t.getY() < AbyssRooms.holdCeiling(rs);
                AbyssRooms.hold(sp, up ? DRAG : 0f, 3);
                // 自分はその場（プレイヤーの上）に留まる
                if (v.y < p.y + 3) setVirtualPos(new Vec3(v.x, p.y + 3, v.z));
                if (stateTicks % 10 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.CHAIN_STEP, SoundSource.HOSTILE, 1.5f, 0.8f);
            }
            default -> {}
        }
    }

    /** プレイヤーの FOLLOW_REL ブロック上へ少しずつ近づきながら一緒に降りる。 */
    private void follow(RoomsState rs, Vec3 p) {
        rel = Math.max(FOLLOW_REL, rel - APPROACH);
        setVirtualPos(clampInner(rs.abyss, new Vec3(cx(rs.abyss), p.y + rel, cz(rs.abyss)), 0.3));
    }

    /** 自分からプレイヤーへの糸（パーティクル、縦穴の中のプレイヤーにだけ）。 */
    private void thread(ServerLevel level, RoomsState rs, Player t, int n) {
        Vec3 a = position().add(0, 0.8, 0), b = t.position().add(0, 1.2, 0);
        if (abyssHidden[0]) a = new Vec3(b.x, Math.min(b.y + 10, AbyssRooms.holdCeiling(rs) + 2), b.z);
        for (Player p : level.players()) if (p instanceof ServerPlayer sp && inShaft(rs, p))
            for (int i = 0; i < n; i++) {
                double k = random.nextDouble();
                Vec3 q = a.add(b.subtract(a).scale(k));
                level.sendParticles(sp, THREAD, true, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず放す（取り残さない。AbyssRooms も期限で自動的に放す）
        if (level() instanceof ServerLevel sl && targetId != null && sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) AbyssRooms.release(sp);
        super.onRemovedFromLevel();
    }

    @Override
    protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble() * 1.6, getZ() + (random.nextDouble() - 0.5), 0, -0.02, 0);
    }

    @Override protected String debugState() { return "state=" + state + " alcoveRoom=" + alcoveRoom + " rel=" + f(rel) + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("AlcoveRoom", alcoveRoom); t.putInt("StateTicks", stateTicks); t.putDouble("Rel", rel); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.STALK; }
        if (state == State.HOLD) state = State.GONE; // 読み込み直後は捕まえ直さない
        alcoveRoom = t.contains("AlcoveRoom") ? t.getInt("AlcoveRoom") : -1; stateTicks = t.getInt("StateTicks"); rel = t.contains("Rel") ? t.getDouble("Rel") : START_ABOVE;
    }
}
