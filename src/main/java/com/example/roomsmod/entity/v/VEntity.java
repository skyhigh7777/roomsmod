package com.example.roomsmod.entity.v;

import com.example.roomsmod.entity.RusherChunks;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * V（Village）区画のエンティティ共通部分：物理なし・重力なし・自然消滅なし、チャンクチケット、
 * デバッグ時のみ発光、寿命（MAX_LIFE_TICKS 経過で必ず消える）、視線判定、保存。
 */
public abstract class VEntity extends Mob {
    /** どの V エンティティも最終的にこの tick 数で消える（取り残し防止）。 */
    public static final int MAX_LIFE_TICKS = 20 * 120;

    protected int life = 0;
    protected int startRoom = 0;
    private ChunkPos lastForcedChunk = null;

    protected VEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    /** 寿命の上限（tick）。GK は 120 秒未満に抑えるため上書きする。 */
    protected int maxLifeTicks() { return MAX_LIFE_TICKS; }

    /** 表示名（"V-10" など）。ログ用。 */
    public abstract String vName();

    public void setStartRoom(int index) { this.startRoom = index; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override public boolean isAlwaysTicking() { return true; }
    @Override public void travel(Vec3 input) { this.setDeltaMovement(Vec3.ZERO); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canRide(Entity e) { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float a) { return false; }

    @Override
    public final void tick() {
        super.tick();
        this.noPhysics = true;
        if (level().isClientSide()) { clientTick(); return; }
        ServerLevel sl = (ServerLevel) level();
        ChunkPos c = new ChunkPos(blockPosition());
        RusherChunks.move(sl, this, lastForcedChunk, c);
        lastForcedChunk = c;
        boolean dbg = RoomsDebug.isEnabled();
        if (isCurrentlyGlowing() != dbg) setGlowingTag(dbg); // 輪郭はデバッグ時のみ
        if (++life > maxLifeTicks()) { leave("max lifetime " + maxLifeTicks() + " ticks"); return; }
        serverTick(sl);
        setDeltaMovement(Vec3.ZERO);
        if (dbg && life % 100 == 0)
            RoomsDebug.log("{} status life={} {} pos=({}, {}, {})", vName(), life, debugState(),
                    String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()));
    }

    /** 出現前に位置と内部状態を決める。返り値は出現位置（null なら出現しない）。playerRoom はプレイヤーのいる部屋 index。 */
    protected abstract BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom);

    protected void clientTick() {}
    protected abstract void serverTick(ServerLevel level);
    protected String debugState() { return ""; }

    /** 正常な退場。 */
    protected void leave(String why) {
        RoomsDebug.log("{} despawn: {} life={} pos=({}, {}, {})", vName(), why, life,
                String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()));
        discard();
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel sl) {
            RemovalReason r = getRemovalReason();
            RoomsDebug.log("{} removed from level reason={}", vName(), r);
            if (r == null || r.shouldDestroy() || r == RemovalReason.CHANGED_DIMENSION) {
                if (lastForcedChunk != null) RusherChunks.force(sl, this, lastForcedChunk, false);
                lastForcedChunk = null;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VLife", life);
        tag.putInt("StartRoom", startRoom);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getInt("VLife");
        startRoom = tag.getInt("StartRoom");
    }

    // ---- helpers ----

    /** 目から目への視線（ブロックの当たり判定で遮られなければ true）。 */
    public boolean canSee(LivingEntity target) {
        Vec3 a = getEyePosition(), b = target.getEyePosition();
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || a.distanceTo(hit.getLocation()) >= a.distanceTo(b) - 0.5;
    }

    /** 部屋 from..to（含む）の経路を連結した経由点。 */
    public static List<BlockPos> pathOf(RoomsState rs, int from, int to) {
        List<BlockPos> w = new ArrayList<>();
        for (int i = Math.max(0, from); i <= Math.min(to, rs.roomPaths.size() - 1); i++)
            for (BlockPos p : rs.roomPaths.get(i))
                if (w.isEmpty() || !w.get(w.size() - 1).equals(p)) w.add(p);
        return w;
    }

    /** 半径内の生存プレイヤー（観戦者除く）。 */
    protected List<Player> playersNear(double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r)))
            if (!p.isSpectator() && p.isAlive() && distanceTo(p) <= r) out.add(p);
        return out;
    }

    /** 移動方向へ向きを合わせる（見た目用）。 */
    protected void faceTowards(Vec3 to) {
        Vec3 d = to.subtract(position());
        if (d.horizontalDistanceSqr() < 1.0E-4) return;
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        setYRot(yaw); setYHeadRot(yaw); yBodyRot = yaw;
    }

    protected static String f(double v) { return String.format("%.1f", v); }
}
