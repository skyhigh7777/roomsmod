package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V-100：窓（板ガラス＋外側の黒コンクリート）の外に現れる覗き魔。
 * 先にノック音（KNOCK_TICKS）→姿を見せて PEEK_TICKS 覗く→消える。
 * 覗いている間（ノック後 REACT_TICKS の猶予の後）、見張っている部屋のプレイヤーはしゃがむか止まっていなければならない。
 * しゃがまずに動いた（ジャンプ含む）tick が累計 MOVE_KILL_TICKS に達すると攻撃。首振り・しゃがみ歩きは可。
 * 出口扉と同じ壁（+Z 側の壁）の窓には出ない（出口を見ないと進めない＝詰みを防ぐ）。
 */
public class V100Entity extends VEntity {
    public static final int KNOCK_TICKS = 30;        // ノック〜出現（1.5秒）
    public static final int PEEK_TICKS = 20 * 8;     // 覗いている時間（約8秒）
    /** ノックが終わってから判定が始まるまでの反応の猶予（tick）。 */
    public static final int REACT_TICKS = 20;
    /** 動いた回数（ジャンプ1回＝1、歩きは連続の始まりで1＋WALK_REPEAT_TICKS ごとに1）の累計がこれに達すると攻撃（V-100 1体・プレイヤーごと、減らない）。 */
    public static final int MOVE_KILL_TICKS = 3;
    /** 1tick の水平移動がこれ以下なら「止まっている」（揺れ・押され対策、ブロック）。歩き 0.216/tick、しゃがみ歩き 0.065。 */
    public static final double MOVE_EPSILON = 0.03;
    /** 歩き続けている間、何 tick ごとに1回数えるか（最初の tick で1回目）。1秒歩き続けると 1+2=3 回でアウト。 */
    public static final int WALK_REPEAT_TICKS = 10;
    /** ジャンプとみなす上向きの移動（ブロック/tick、接地していない時）。 */
    public static final double JUMP_EPSILON = 0.05;
    public static final float DAMAGE = 40.0f;        // ほぼ即死

    private static final EntityDataAccessor<Boolean> PEEKING = SynchedEntityData.defineId(V100Entity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos window = null;
    private Direction windowOut = null;
    private int knockStart = 0;
    /** クライアント：見張り音を再生中か（WatchSoundInstance が更新）。 */
    public boolean watchSoundActive = false;
    private final Map<UUID, Integer> moves = new HashMap<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final java.util.Set<UUID> airborne = new java.util.HashSet<>();
    private final Map<UUID, Integer> walkRun = new HashMap<>();

    public V100Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public String vName() { return "V-100"; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PEEKING, false);
    }

    public boolean isPeeking() { return entityData.get(PEEKING); }

    /** 部屋 room の窓のうち、出口の壁でないもの。{窓, 外向き方向}。 */
    static List<Object[]> windows(ServerLevel level, RoomsState rs, int room) {
        List<Object[]> out = new ArrayList<>();
        if (room < 0 || room >= rs.generatedRooms.size()) return out;
        List<BlockPos> path = VSpawns.path(rs, room);
        BlockPos exit = path.isEmpty() ? null : path.get(path.size() - 1);
        for (BoundingBox b : rs.generatedRooms.get(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                if (!level.getBlockState(q).is(Blocks.GLASS_PANE)) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    // 板ガラスの外の空気ポケット（奥が黒コンクリート）
                    if (!level.getBlockState(q.relative(d)).isAir() || !level.getBlockState(q.relative(d, 2)).is(Blocks.BLACK_CONCRETE)) continue;
                    // 出口扉は +Z（SOUTH）側の壁。その壁、または出口扉と同じ z の窓は除外
                    if (d == Direction.SOUTH || (exit != null && q.getZ() >= exit.getZ() - 1)) continue;
                    if (!isValidWindow(level, rs, room, q, d)) continue;
                    out.add(new Object[]{q.immutable(), d});
                }
            }
        return out;
    }

    public static List<Object[]> windowsForTest(ServerLevel l, RoomsState rs, int room) { return windows(l, rs, room); }

    /** 本物の窓か：板ガラス、外がこの部屋の外の空気ポケット、その奥が黒コンクリート、内側がこの部屋の中の通れる空間。 */
    static boolean isValidWindow(ServerLevel level, RoomsState rs, int room, BlockPos q, Direction out) {
        if (!level.getBlockState(q).is(Blocks.GLASS_PANE)) return false;
        BlockPos pocket = q.relative(out), in = q.relative(out.getOpposite());
        if (!level.getBlockState(pocket).isAir() || !level.getBlockState(q.relative(out, 2)).is(Blocks.BLACK_CONCRETE)) return false;
        if (!level.getBlockState(in).getCollisionShape(level, in).isEmpty()) return false;
        boolean inInside = false;
        for (BoundingBox b : rs.generatedRooms.get(room)) {
            if (b.isInside(in)) inInside = true;
        }
        if (!inInside) return false;
        // ポケットはどの部屋の内部でもない（隣の部屋の中を窓と誤認しない）
        for (int r = Math.max(0, room - 2); r < Math.min(rs.generatedRooms.size(), room + 3); r++)
            if (com.example.roomsmod.world.SafeSpot.inInterior(rs, r, pocket)) return false;
        return true;
    }

    /** 今いちばん先の部屋にいるプレイヤーの部屋（いなければ -1）。 */
    /** テスト用（ハーネス）：>=0 なら「いちばん先のプレイヤーの部屋」として使う。通常は -1。 */
    public static int testPlayerRoom = -1;

    private int furthestPlayerRoom(ServerLevel level, RoomsState rs) {
        int best = testPlayerRoom;
        for (Player p : level.players())
            if (!p.isSpectator() && p.isAlive()) best = Math.max(best, com.example.roomsmod.event.RoomTracker.roomIndexAt(rs, p.blockPosition()));
        return best;
    }

    /** 部屋 room の有効な窓を選んでポケットに入る。なければ false（窓なし＝隠れて待つ）。 */
    private boolean pickWindow(ServerLevel level, RoomsState rs, int room) {
        startRoom = room;
        List<Object[]> ws = windows(level, rs, room);
        if (ws.isEmpty()) { window = null; RoomsDebug.log("V-100: room {} ({}) has no eligible window -> waiting", room, com.example.roomsmod.event.RoomTracker.format(rs, room)); return false; }
        Object[] w = ws.get(level.random.nextInt(ws.size()));
        window = (BlockPos) w[0];
        windowOut = (Direction) w[1];
        BlockPos at = window.relative(windowOut); // 窓のすぐ外の空気ポケット
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, this, new net.minecraft.world.level.ChunkPos(at));
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5); // 足元＝ポケットのブロック（描画中心 y+0.55 がガラスの高さ）
        faceTowards(Vec3.atCenterOf(window.relative(windowOut.getOpposite())));
        knockStart = life;
        RoomsDebug.log("V-100 chose window {} pocket {} in room {} ({}) of {} candidates", window, at, room, com.example.roomsmod.event.RoomTracker.format(rs, room), ws.size());
        return true;
    }

    /** 今の位置が選んだ窓のポケットか（テスト・自己点検用）。 */
    public boolean isInPocket(ServerLevel level) {
        if (window == null || windowOut == null) return false;
        BlockPos at = window.relative(windowOut);
        return blockPosition().equals(at) && isValidWindow(level, RoomsState.get(level), startRoom, window, windowOut);
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int fr = furthestPlayerRoom(level, rs);
        int room = Math.max(playerRoom, fr);
        if (!pickWindow(level, rs, room)) return null;
        return window.relative(windowOut);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        // プレイヤーが別の部屋へ移ったら、その部屋の窓に移り直す（ノックからやり直し）
        int fr = furthestPlayerRoom(level, rs);
        if (fr >= 0 && fr != startRoom) {
            RoomsDebug.log("V-100 player moved room {} -> {}: re-pick window", startRoom, fr);
            if (isPeeking()) entityData.set(PEEKING, false);
            pickWindow(level, rs, fr);
        }
        // 窓が壊れた・無効になった
        if (window != null && !isValidWindow(level, rs, startRoom, window, windowOut)) {
            RoomsDebug.log("V-100 window {} no longer valid: re-pick", window);
            if (isPeeking()) entityData.set(PEEKING, false);
            pickWindow(level, rs, startRoom);
        }
        if (window == null && life % 20 == 0) pickWindow(level, rs, startRoom); // 古いセーブ等
        if (window == null) { if (isPeeking()) entityData.set(PEEKING, false); return; } // 窓のない部屋：見えないまま待つ（寿命で消える）
        int t = life - knockStart;
        if (t == 1 || t == 12) level.playSound(null, window, RoomsMod.V100_KNOCK.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        if (t < KNOCK_TICKS) return;
        if (!isPeeking()) { entityData.set(PEEKING, true); RoomsDebug.log("V-100 peeking at window {} room {} inPocket={}", window, startRoom, isInPocket(level)); }
        if (t > KNOCK_TICKS + PEEK_TICKS) { leave("peek time over"); return; }
        if (t < KNOCK_TICKS + REACT_TICKS) { for (Player p : level.players()) lastPos.put(p.getUUID(), p.position()); return; } // 反応の猶予
        for (Player p : level.players()) if (moveTick(level, rs, p)) return;
    }

    public BlockPos window() { return window; }
    public Direction windowOut() { return windowOut; }

    /**
     * 1プレイヤー分の動き判定（毎tick）。見張っている部屋にいて、しゃがまずに動いた（水平移動 > MOVE_EPSILON、またはジャンプ）tick を累計し、
     * MOVE_KILL_TICKS で攻撃（クリエイティブ・観戦者はログのみ）。視線の向き（首振り）は関係ない。
     * @return 攻撃して消えたら true
     */
    public boolean moveTick(ServerLevel level, RoomsState rs, Player p) {
        Vec3 now = p.position(), prev = lastPos.put(p.getUUID(), now);
        if (!isPeeking() || isRemoved() || prev == null || !p.isAlive()) return false;
        if (com.example.roomsmod.event.RoomTracker.roomIndexAt(rs, p.blockPosition()) != startRoom) return false;
        if (HidingRules.isHiding(p)) return false; // 隠れ場所の中は動けない
        double dx = now.x - prev.x, dz = now.z - prev.z, dy = now.y - prev.y;
        UUID id = p.getUUID();
        boolean jumped = false, walked = false;
        if (!p.onGround()) {
            // 空中：踏み切り（上向き）の最初の tick だけ1回。着地するまで数えない。空中の水平移動（勢い・落下）も数えない
            if (dy > JUMP_EPSILON && airborne.add(id)) jumped = true;
            else airborne.add(id);
            walkRun.remove(id);
        } else {
            if (airborne.remove(id)) { walkRun.remove(id); return false; } // 着地の tick（落下・勢いの水平移動）は数えない
            boolean moving = Math.sqrt(dx * dx + dz * dz) > MOVE_EPSILON && !p.isShiftKeyDown();
            if (!moving) walkRun.remove(id);
            else {
                // 歩き：連続して歩いている間、最初の tick で1回、その後 WALK_REPEAT_TICKS ごとに1回（ちょっとの一歩は1回だけ）
                int run = walkRun.getOrDefault(id, 0);
                walked = run % WALK_REPEAT_TICKS == 0;
                walkRun.put(id, run + 1);
            }
        }
        if (!jumped && !walked) return false;
        int n = moves.getOrDefault(p.getUUID(), 0) + 1; // 累積
        moves.put(p.getUUID(), n);
        RoomsDebug.log(p, "V-100 move " + n + "/" + MOVE_KILL_TICKS + (jumped ? "（ジャンプ）" : ""), "V-100 move {}/{} player={} jumped={} dxz={}",
                n, MOVE_KILL_TICKS, p.getGameProfile().getName(), jumped, String.format("%.3f", Math.sqrt(dx * dx + dz * dz)));
        if (n < MOVE_KILL_TICKS) return false;
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.V100_SCREAM.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        boolean immune = p.isCreative() || p.isSpectator();
        RoomsDebug.log(p, immune ? "V-100: would kill（クリエイティブ）" : "V-100 に見つかったわ",
                "V-100 attack player={} after {} moves creative={}{}", p.getGameProfile().getName(), n, p.isCreative(), immune ? " -> would kill" : "");
        if (!immune) p.hurt(damageSources().mobAttack(this), DAMAGE);
        leave("attacked");
        return true;
    }

    @Override
    protected void clientTick() {
        if (isPeeking() && !watchSoundActive) com.example.roomsmod.util.ApproachSoundHook.startLoop(this, RoomsMod.V100_WATCH.get());
        if (isPeeking() && tickCount % 10 == 0)
            level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 0, 0.01, 0);
    }

    @Override
    protected String debugState() { return "window=" + window + " peeking=" + isPeeking() + " moves=" + moves.values(); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (window != null) tag.putLong("Window", window.asLong());
        if (windowOut != null) tag.putInt("WindowOut", windowOut.get3DDataValue());
        tag.putInt("KnockStart", knockStart);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        window = tag.contains("Window") ? BlockPos.of(tag.getLong("Window")) : null;
        windowOut = tag.contains("WindowOut") ? Direction.from3DDataValue(tag.getInt("WindowOut")) : null;
        knockStart = tag.getInt("KnockStart");
        if (windowOut == null) window = null; // 古いセーブ：次の tick で選び直す
    }
}
