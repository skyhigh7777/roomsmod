package com.example.roomsmod.entity.placeholder;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.block.HidingTableBlock;
import com.example.roomsmod.block.LockerBlock;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * PLACEHOLDER-65535。
 * <ol>
 *   <li>出現：BEEP_TICKS の間ビープを鳴らし続ける（姿は見えない）。</li>
 *   <li>鳴り終わって MARK_DELAY tick 後、最奥から MARK_ROOMS 部屋（1 以上）のロッカー・机をそれぞれ MARK_CHANCE で印を付ける（赤い粒子）。</li>
 *   <li>APPEAR_DELAY tick 後、最奥の ROOMS_BEHIND 部屋手前（1 以上）の真ん中に姿を現す。</li>
 *   <li>印の付いた場所を手前の部屋から順に、MOVE_TICKS で移動して CHECK_TICKS で調べる。そこに隠れているプレイヤーは即死。調べた場所の粒子は消える。</li>
 *   <li>全部調べたら（印が1つもなければ姿を現した時点で）消える。</li>
 * </ol>
 * 通常は自然出現しない（ハードモードの抽選・3!3!2・/rooms spawn placeholder65535 のみ）。状態は NBT に保存。
 */
public class Placeholder65535Entity extends PlaceholderEntity {
    public static final int BEEP_TICKS = 10, MARK_DELAY = 30, APPEAR_DELAY = 40;
    public static final int MARK_ROOMS = 30, ROOMS_BEHIND = 50;
    public static final float MARK_CHANCE = 0.3f;
    public static final int MOVE_TICKS = 4, CHECK_TICKS = 1;
    public static final float KILL_DAMAGE = 1000f;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.05f), 1.5f);

    /** 印の付いた場所：pos（ロッカーは下半分）、部屋 index、ロッカーか。 */
    private record Mark(BlockPos pos, int room, boolean locker) {}

    /** 0=ビープ, 1=印待ち, 2=出現待ち, 3=巡回 */
    private int phase = 0, phaseTicks = 0, index = 0;
    private final List<Mark> marks = new ArrayList<>();
    private Vec3 from = null;

    public Placeholder65535Entity(EntityType<? extends Mob> type, Level level) {
        super(type, level, "PLACEHOLDER-65535");
        setInvisible(true);
    }

    @Override protected int maxLifeTicks() { return 20 * 600; }

    private static int deepest(RoomsState rs) { return Math.max(1, rs.roomPaths.size() - 1); }

    /** 出現時はまだ見えない。とりあえず姿を現す予定の部屋に置いておく。 */
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int room = appearRoom(rs);
        BlockPos at = Placeholder32767Entity.roomCenter(rs, room);
        if (at == null) return null;
        moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), getXRot());
        startRoom = room;
        RoomsDebug.log("{} spawn (hidden) room={} ({}) deepest={}", name, room, RoomTracker.format(rs, room), deepest(rs));
        return at;
    }

    private static int appearRoom(RoomsState rs) {
        int r = Math.max(1, deepest(rs) - ROOMS_BEHIND);
        return Math.min(r, rs.roomPaths.size() - 1);
    }

    /** 部屋ディメンションの各プレイヤーの位置で鳴らす（どこにいても聞こえる）。 */
    private void beepAll(ServerLevel level, float pitch) {
        SoundEvent s = RoomsMod.PLACEHOLDER_BEEP.get();
        for (ServerPlayer p : level.players()) level.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.HOSTILE, 1.0f, pitch);
    }

    private void enter(int ph) { phase = ph; phaseTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        phaseTicks++;
        switch (phase) {
            case 0 -> {
                beepAll(level, 1.0f); // 毎 tick 鳴らして、BEEP_TICKS の間つながった音にする
                if (phaseTicks >= BEEP_TICKS) { RoomsDebug.log("{} beep done ({} ticks)", name, BEEP_TICKS); enter(1); }
            }
            case 1 -> { if (phaseTicks >= MARK_DELAY) { mark(level, rs); enter(2); } }
            case 2 -> {
                if (phaseTicks >= APPEAR_DELAY) {
                    int room = appearRoom(rs);
                    BlockPos at = Placeholder32767Entity.roomCenter(rs, room);
                    if (at != null) { moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), getXRot()); startRoom = room; }
                    setInvisible(false);
                    RoomsDebug.log("{} appeared room={} ({}) marks={}", name, room, RoomTracker.format(rs, room), marks.size());
                    if (marks.isEmpty()) { leave("no marks"); return; }
                    index = 0; from = position();
                    enter(3);
                }
            }
            case 3 -> { if (patrol(level)) return; }
            default -> enter(0);
        }
        if (phase >= 2) showMarks(level);
    }

    /** 最奥から MARK_ROOMS 部屋のロッカー・机に、それぞれ MARK_CHANCE で印を付ける。手前の部屋から順。 */
    private void mark(ServerLevel level, RoomsState rs) {
        marks.clear();
        int d = deepest(rs);
        int lo = Math.max(1, d - MARK_ROOMS + 1);
        int total = 0;
        for (int room = lo; room <= d && room < rs.generatedRooms.size(); room++) {
            List<Mark> inRoom = new ArrayList<>();
            java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            for (BoundingBox b : rs.generatedRooms.get(room))
                for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                    Boolean locker = spotKind(level.getBlockState(q));
                    if (locker == null || !seen.add(q.immutable())) continue;
                    total++;
                    if (random.nextFloat() < MARK_CHANCE) inRoom.add(new Mark(q.immutable(), room, locker));
                }
            marks.addAll(orderInRoom(inRoom, marks.isEmpty() ? position() : center(marks.get(marks.size() - 1).pos())));
        }
        RoomsDebug.log("{} marked {}/{} spots in rooms {}..{} ({}..{})", name, marks.size(), total, lo, d, RoomTracker.format(rs, lo), RoomTracker.format(rs, d));
        for (Mark m : marks) RoomsDebug.log("{} mark {} room={} at {}", name, m.locker() ? "locker" : "table", m.room(), m.pos());
    }

    /** 部屋の中は、直前の場所から近い順にたどる。 */
    private static List<Mark> orderInRoom(List<Mark> in, Vec3 start) {
        List<Mark> left = new ArrayList<>(in), out = new ArrayList<>();
        Vec3 cur = start;
        while (!left.isEmpty()) {
            Mark best = null; double bd = Double.MAX_VALUE;
            for (Mark m : left) { double dd = center(m.pos()).distanceToSqr(cur); if (dd < bd) { bd = dd; best = m; } }
            left.remove(best); out.add(best); cur = center(best.pos());
        }
        return out;
    }

    /** ロッカー（下半分）なら true、机なら false、それ以外は null。 */
    private static Boolean spotKind(BlockState s) {
        if (s.getBlock() instanceof LockerBlock && s.getValue(LockerBlock.HALF) == DoubleBlockHalf.LOWER) return true;
        if (s.getBlock() instanceof HidingTableBlock) return false;
        return null;
    }

    private static Vec3 center(BlockPos p) { return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5); }

    /** まだ調べていない印に赤い粒子（4 tick ごと、遠くからも見えるように force で送る）。 */
    private void showMarks(ServerLevel level) {
        if (life % 4 != 0) return;
        int start = phase == 3 ? index : 0;
        for (int i = start; i < marks.size(); i++) {
            Mark m = marks.get(i);
            double h = m.locker() ? 1.0 : 0.5;
            for (ServerPlayer p : level.players())
                level.sendParticles(p, RED, true, m.pos().getX() + 0.5, m.pos().getY() + h, m.pos().getZ() + 0.5, 4, 0.3, h * 0.6, 0.3, 0);
        }
    }

    /** 1つの印につき MOVE_TICKS で移動、続く CHECK_TICKS で調べる。全部終わったら消えて true。 */
    private boolean patrol(ServerLevel level) {
        if (index >= marks.size()) { leave("checked all " + marks.size() + " marks"); return true; }
        Mark m = marks.get(index);
        Vec3 to = center(m.pos());
        if (from == null) from = position();
        if (phaseTicks == 1) beepAll(level, 1.0f);
        if (phaseTicks <= MOVE_TICKS) {
            Vec3 p = from.lerp(to, phaseTicks / (double) MOVE_TICKS);
            setPos(p.x, p.y, p.z);
            faceTowards(to);
            return false;
        }
        if (phaseTicks >= MOVE_TICKS + CHECK_TICKS) {
            check(level, m);
            index++;
            from = to;
            phaseTicks = 0;
            if (index >= marks.size()) { leave("checked all " + marks.size() + " marks"); return true; }
        }
        return false;
    }

    /** その印の場所に隠れているプレイヤーを即死させる（外に立っているプレイヤーは対象外）。 */
    private void check(ServerLevel level, Mark m) {
        int kills = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (!m.pos().equals(LockerManager.getSpotPos(p))) continue;
            RoomsDamage.hurt(this, p, KILL_DAMAGE);
            kills++;
            RoomsDebug.log(p, name + ": 隠れ場所で見つかったわ", "{} check {} at {} killed player={}", name, m.locker() ? "locker" : "table", m.pos(), p.getGameProfile().getName());
        }
        RoomsDebug.log("{} check {}/{} {} room={} at {} kills={}", name, index + 1, marks.size(), m.locker() ? "locker" : "table", m.room(), m.pos(), kills);
    }

    @Override protected String debugState() { return "phase=" + phase + " phaseTicks=" + phaseTicks + " marks=" + marks.size() + " index=" + index; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase); t.putInt("PhaseTicks", phaseTicks); t.putInt("Index", index);
        ListTag l = new ListTag();
        for (Mark m : marks) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", m.pos().asLong()); c.putInt("Room", m.room()); c.putBoolean("Locker", m.locker());
            l.add(c);
        }
        t.put("Marks", l);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase"); phaseTicks = t.getInt("PhaseTicks"); index = t.getInt("Index");
        marks.clear();
        for (Tag x : t.getList("Marks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            marks.add(new Mark(BlockPos.of(c.getLong("Pos")), c.getInt("Room"), c.getBoolean("Locker")));
        }
        from = null;
        setInvisible(phase < 3);
        RoomsDebug.log("{} loaded {}", name, debugState());
    }
}
