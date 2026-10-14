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
 * V-35：窓（板ガラス＋外側の黒コンクリート）の外に現れる覗き魔。
 * 先にノック音（KNOCK_TICKS）→姿を見せて PEEK_TICKS 覗く→消える。
 * 覗いている間、プレイヤーが視線（視野円錐＋遮るものなし）を 累計 GAZE_KILL_TICKS より長く向けると攻撃（目を逸らしても数え直さない）。
 * 出口扉と同じ壁（+Z 側の壁）の窓には出ない（出口を見ないと進めない＝詰みを防ぐ）。
 */
public class V35Entity extends VEntity {
    public static final int KNOCK_TICKS = 30;        // ノック〜出現（1.5秒）
    public static final int PEEK_TICKS = 20 * 12;    // 覗いている時間
    /** 見た時間の累計（tick）がこれを超えると攻撃。目を逸らしてもリセットしない（この個体が生きている間ずっと累積）。 */
    public static final int GAZE_KILL_TICKS = 10;
    /** 視線（画面中央）から V-35 の顔のどこか（顔の中心までの角度 − 見かけの半径 atan(FACE_RADIUS/距離)）までがこの半角以内なら「見ている」＝視界の中央付近に入っていれば数える。 */
    public static final double GAZE_CONE_DEG = 30.0;
    /** 顔の中心の高さ（足元から。描画のビルボード 1.1 の中心）と、見かけの大きさに使う半径。 */
    public static final double FACE_HEIGHT = 0.55, FACE_RADIUS = 0.55;
    public static final double RANGE = 24.0;
    public static final float DAMAGE = 40.0f;        // ほぼ即死

    private static final EntityDataAccessor<Boolean> PEEKING = SynchedEntityData.defineId(V35Entity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos window = null;
    private Direction windowOut = null;
    private int knockStart = 0;
    private final Map<UUID, Integer> gaze = new HashMap<>();

    public V35Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public String vName() { return "V-35"; }

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
        if (ws.isEmpty()) { window = null; RoomsDebug.log("V-35: room {} ({}) has no eligible window -> waiting", room, com.example.roomsmod.event.RoomTracker.format(rs, room)); return false; }
        Object[] w = ws.get(level.random.nextInt(ws.size()));
        window = (BlockPos) w[0];
        windowOut = (Direction) w[1];
        BlockPos at = window.relative(windowOut); // 窓のすぐ外の空気ポケット
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, this, new net.minecraft.world.level.ChunkPos(at));
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5); // 足元＝ポケットのブロック（描画中心 y+0.55 がガラスの高さ）
        faceTowards(Vec3.atCenterOf(window.relative(windowOut.getOpposite())));
        knockStart = life;
        RoomsDebug.log("V-35 chose window {} pocket {} in room {} ({}) of {} candidates", window, at, room, com.example.roomsmod.event.RoomTracker.format(rs, room), ws.size());
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
            RoomsDebug.log("V-35 player moved room {} -> {}: re-pick window", startRoom, fr);
            if (isPeeking()) entityData.set(PEEKING, false);
            pickWindow(level, rs, fr);
        }
        // 窓が壊れた・無効になった
        if (window != null && !isValidWindow(level, rs, startRoom, window, windowOut)) {
            RoomsDebug.log("V-35 window {} no longer valid: re-pick", window);
            if (isPeeking()) entityData.set(PEEKING, false);
            pickWindow(level, rs, startRoom);
        }
        if (window == null && life % 20 == 0) pickWindow(level, rs, startRoom); // 古いセーブ等
        if (window == null) { if (isPeeking()) entityData.set(PEEKING, false); return; } // 窓のない部屋：見えないまま待つ（寿命で消える）
        int t = life - knockStart;
        if (t == 1 || t == 12) level.playSound(null, window, RoomsMod.V35_KNOCK.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        if (t < KNOCK_TICKS) return;
        if (!isPeeking()) { entityData.set(PEEKING, true); RoomsDebug.log("V-35 peeking at window {} room {} inPocket={}", window, startRoom, isInPocket(level)); }
        if (t > KNOCK_TICKS + PEEK_TICKS) { leave("peek time over"); return; }
        for (Player p : playersNear(RANGE)) if (gazeTick(level, p)) return;
    }

    public BlockPos window() { return window; }
    public Direction windowOut() { return windowOut; }

    /** 顔の中心（描画の中心＝足元 + FACE_HEIGHT）。 */
    public Vec3 faceCenter() { return position().add(0, FACE_HEIGHT, 0); }

    /**
     * 1プレイヤー分の視線判定（毎tick）。見ていれば累計+1、GAZE_KILL_TICKS で攻撃（クリエイティブはログのみ）。
     * @return 攻撃して消えたら true
     */
    public boolean gazeTick(ServerLevel level, Player p) {
        if (!isPeeking() || isRemoved()) return false;
        String why = HidingRules.isProtectedFrom(this, p) ? "hiding" : lookFailReason(p, faceCenter());
        if (why != null) {
            if (RoomsDebug.isEnabled() && life % 10 == 0) RoomsDebug.log("V-35 not looking player={} reason={}", p.getGameProfile().getName(), why);
            return false;
        }
        int g = gaze.getOrDefault(p.getUUID(), 0) + 1; // 累積（逸らしても減らない）
        gaze.put(p.getUUID(), g);
        RoomsDebug.log(p, "V-35 gaze " + g + "/" + GAZE_KILL_TICKS, "V-35 gaze {}/{} player={}", g, GAZE_KILL_TICKS, p.getGameProfile().getName());
        if (g < GAZE_KILL_TICKS) return false;
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.V35_SCREAM.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        boolean immune = p.isCreative() || p.isSpectator();
        RoomsDebug.log(p, immune ? "V-35: would kill（クリエイティブ）" : "V-35 に見つかったわ",
                "V-35 attack player={} after gaze {} ticks creative={}{}", p.getGameProfile().getName(), g, p.isCreative(), immune ? " -> would kill" : "");
        if (!immune) p.hurt(damageSources().mobAttack(this), DAMAGE);
        leave("attacked");
        return true;
    }

    /** 見ていれば null、見ていなければ理由。視野円錐（顔の見かけの大きさも足す）＋ガラス・板ガラスを透過する視線。 */
    public String lookFailReason(Player p, Vec3 face) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = face.subtract(eye);
        double dist = to.length();
        if (dist < 1.0E-3) return null;
        double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, p.getViewVector(1.0f).dot(to.normalize())))));
        double allowed = GAZE_CONE_DEG + Math.toDegrees(Math.atan(FACE_RADIUS / dist));
        if (ang > allowed) return String.format("cone %.1f>%.1f", ang, allowed);
        // 窓は1ブロック幅なので、斜めから見ると顔の中心への線は窓の横の壁に当たる。
        // 窓（板ガラス）まで見通せればその奥の V-35 も見えているとみなす。
        if (lineOfSight(eye, face, p)) return null;
        if (window != null && lineOfSight(eye, Vec3.atCenterOf(window), p)) return null;
        return "blocked";
    }

    private boolean lineOfSight(Vec3 eye, Vec3 face, Player p) {
        Vec3 to = face.subtract(eye);
        // 板ガラス・ガラスは透明扱い：当たったら少し先から撃ち直す
        Vec3 from = eye;
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level().clip(new ClipContext(from, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockPos h = hit.getBlockPos();
            if (h.equals(blockPosition()) || (h.equals(window) && face.distanceTo(Vec3.atCenterOf(window)) < 0.01)) return true;
            var s = level().getBlockState(h);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) && !(s.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock)) {
                if (RoomsDebug.isEnabled() && life % 10 == 0) RoomsDebug.log("V-35 sight blocked by {} at {}", s.getBlock(), h);
                return false;
            }
            from = hit.getLocation().add(to.normalize().scale(0.05));
            // ガラスブロックの中から出るまで進める
            while (level().getBlockState(BlockPos.containing(from)).equals(s) && from.distanceTo(face) > 0.1) from = from.add(to.normalize().scale(0.1));
        }
        return false;
    }

    @Override
    protected void clientTick() {
        if (isPeeking() && tickCount % 10 == 0)
            level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 0, 0.01, 0);
    }

    @Override
    protected String debugState() { return "window=" + window + " peeking=" + isPeeking() + " gaze=" + gaze.values(); }

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
