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
 * 覗いている間、プレイヤーが視線（視野円錐＋遮るものなし）を GAZE_GRACE_TICKS より長く向け続けると攻撃。目を逸らせば数え直し。
 * 出口扉と同じ壁（+Z 側の壁）の窓には出ない（出口を見ないと進めない＝詰みを防ぐ）。
 */
public class V35Entity extends VEntity {
    public static final int KNOCK_TICKS = 30;        // ノック〜出現（1.5秒）
    public static final int PEEK_TICKS = 20 * 12;    // 覗いている時間
    public static final int GAZE_GRACE_TICKS = 50;   // 見続けて許される時間（2.5秒）
    public static final double LOOK_CONE_DEG = 20.0; // 視線の中心からこの角度以内なら「見ている」
    public static final double RANGE = 24.0;
    public static final float DAMAGE = 40.0f;        // ほぼ即死

    private static final EntityDataAccessor<Boolean> PEEKING = SynchedEntityData.defineId(V35Entity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos window = null;
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
                    out.add(new Object[]{q.immutable(), d});
                }
            }
        return out;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<Object[]> ws = windows(level, rs, playerRoom);
        if (ws.isEmpty()) { RoomsDebug.log("V-35: room {} has no eligible window", playerRoom); return null; }
        Object[] w = ws.get(level.random.nextInt(ws.size()));
        window = (BlockPos) w[0];
        Direction out = (Direction) w[1];
        BlockPos at = window.relative(out); // 窓のすぐ外の空気ポケット
        setPos(at.getX() + 0.5, at.getY() - 0.05, at.getZ() + 0.5); // 顔（描画中心 y+0.55）がガラスの高さに来る
        faceTowards(Vec3.atCenterOf(window.relative(out.getOpposite())));
        return at;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (window == null) { leave("no window"); return; }
        if (life == 1 || life == 12) level.playSound(null, window, RoomsMod.V35_KNOCK.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        if (life < KNOCK_TICKS) return;
        if (!isPeeking()) { entityData.set(PEEKING, true); RoomsDebug.log("V-35 peeking at window {}", window); }
        if (life > KNOCK_TICKS + PEEK_TICKS) { leave("peek time over"); return; }
        Vec3 face = new Vec3(getX(), window.getY() + 0.5, getZ()); // ポケット内の顔
        for (Player p : playersNear(RANGE)) {
            boolean looking = !HidingRules.isProtectedFrom(this, p) && isLookingAt(p, face);
            int g = looking ? gaze.getOrDefault(p.getUUID(), 0) + 1 : 0;
            gaze.put(p.getUUID(), g);
            if (looking && (g == 1 || g % 20 == 0))
                RoomsDebug.log(p, "V-35 を見ている " + g + "/" + GAZE_GRACE_TICKS, "V-35 gaze player={} ticks={}/{}", p.getGameProfile().getName(), g, GAZE_GRACE_TICKS);
            if (g > GAZE_GRACE_TICKS) {
                level.playSound(null, getX(), getY(), getZ(), RoomsMod.V35_SCREAM.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
                RoomsDebug.log(p, "V-35 に見つかったわ", "V-35 attack player={} after gaze {} ticks creative={}", p.getGameProfile().getName(), g, p.isCreative());
                if (!p.isCreative()) p.hurt(damageSources().mobAttack(this), DAMAGE);
                leave("attacked");
                return;
            }
        }
    }

    /** 視野円錐内かつ、目から窓までブロックに遮られない（窓ガラス・外の黒コンクリートは除く）。 */
    private boolean isLookingAt(Player p, Vec3 face) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = face.subtract(eye);
        if (to.length() < 1.0E-3) return true;
        if (p.getViewVector(1.0f).dot(to.normalize()) < Math.cos(Math.toRadians(LOOK_CONE_DEG))) return false;
        // 板ガラス・ガラスは透明扱い：当たったら少し先から撃ち直す
        Vec3 from = eye;
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level().clip(new ClipContext(from, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockPos h = hit.getBlockPos();
            if (h.equals(blockPosition())) return true;
            var s = level().getBlockState(h);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) && !(s.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock)) return false;
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        window = tag.contains("Window") ? BlockPos.of(tag.getLong("Window")) : null;
    }
}
