package com.example.roomsmod.entity;

import com.example.roomsmod.world.RoomsState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A-20：高速で部屋を突進し、有限距離の視線が通ったプレイヤーにダメージを与える。
 * 隠れ場所（オーク板）で視線を遮れば回避可能。
 */
public class A20Entity extends Mob {
    private static final double MAX_RAYCAST_DISTANCE = 22.5;
    private static final float DAMAGE = 8.0f; // 連続ダメージで致命的に
    private ChunkPos lastForcedChunk = null;
    private List<BlockPos> waypoints = new ArrayList<>();
    private int waypointIndex = 0;

    private int startRoom = 0;
    private boolean waypointsBuilt = false;
    /** 経路終端後に進む方向（最後の区間の水平方向、既定は+Z）。 */
    private Vec3 exitDirection = new Vec3(0, 0, 1);

    public void setStartRoom(int index) {
        this.startRoom = index;
    }

    /** startRoom 以降の各部屋の経路を順に連結して経由点にする。 */
    private void buildWaypoints() {
        waypoints.clear();
        waypointsBuilt = true;
        if (!(this.level() instanceof ServerLevel sl)) return;
        RoomsState rs = RoomsState.get(sl);
        for (int i = Math.max(0, startRoom); i < rs.roomPaths.size(); i++) {
            for (BlockPos p : rs.roomPaths.get(i)) {
                if (waypoints.isEmpty() || !waypoints.get(waypoints.size() - 1).equals(p)) waypoints.add(p);
            }
        }
        if (waypointIndex > waypoints.size()) waypointIndex = waypoints.size();
        updateExitDirection();
    }

    private void updateExitDirection() {
        if (waypoints.size() >= 2) {
            BlockPos a = waypoints.get(waypoints.size() - 2), b = waypoints.get(waypoints.size() - 1);
            Vec3 d = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
            if (d.lengthSqr() > 1.0E-4) exitDirection = d.normalize();
        }
    }

    /** 走行中に新しい部屋が生成された場合に経路を延長する。 */
    private void refreshWaypointsIfGrown() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        RoomsState rs = RoomsState.get(sl);
        int expected = 0;
        for (int i = Math.max(0, startRoom); i < rs.roomPaths.size(); i++) expected += rs.roomPaths.get(i).size();
        if (expected > lastPathPointCount) {
            lastPathPointCount = expected;
            int idx = waypointIndex;
            buildWaypoints();
            waypointIndex = Math.min(idx, waypoints.size());
        }
    }
    private int lastPathPointCount = 0;

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("StartRoom", startRoom);
        tag.putInt("WaypointIndex", waypointIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        startRoom = tag.getInt("StartRoom");
        waypointIndex = tag.getInt("WaypointIndex");
        waypointsBuilt = false; // 次tickでRoomsStateから再構築
    }

    public A20Entity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true; // 壁やロッカーを完全に突き抜ける
        this.setNoGravity(true);
        this.setGlowingTag(true); // 光って目立つように
        this.setPersistenceRequired(); // 自然消滅しない
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    private void updateForcedChunks(ServerLevel serverLevel) {
        ChunkPos current = new ChunkPos(this.blockPosition());
        if (lastForcedChunk != null && lastForcedChunk.equals(current)) return;

        // 前回の強制読み込みを解除
        if (lastForcedChunk != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    serverLevel.setChunkForced(lastForcedChunk.x + dx, lastForcedChunk.z + dz, false);
                }
            }
        }

        // 現在位置周辺を強制読み込み
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                serverLevel.setChunkForced(current.x + dx, current.z + dz, true);
            }
        }
        lastForcedChunk = current;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) com.example.roomsmod.util.RoomsDebug.log("A-20 removed reason={} waypoint={}/{}", reason, waypointIndex, waypoints.size());
        if (this.level() instanceof ServerLevel serverLevel && lastForcedChunk != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    serverLevel.setChunkForced(lastForcedChunk.x + dx, lastForcedChunk.z + dz, false);
                }
            }
            lastForcedChunk = null;
        }
        super.remove(reason);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 1.5) // 高速
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true; // 毎tick強制

        if (this.level().isClientSide()) {
            // クライアント側で赤いパーティクルを大量に
            spawnVisualParticles();
            // 接近ループ音（2秒ごと、エンティティに追従するクライアント側サウンド）
            if (this.tickCount % 40 == 0) {
                com.example.roomsmod.util.ApproachSoundHook.start(this, com.example.roomsmod.RoomsMod.A20_APPROACH.get());
            }
            return;
        }

        // サーバー側：周辺チャンクを強制読み込みしてTickを維持
        if (this.level() instanceof ServerLevel serverLevel) {
            updateForcedChunks(serverLevel);
        }

        // 前方に高速移動。猶予中は動かない。扉を経由点として通る
        if (this.tickCount < 80) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!waypointsBuilt) {
                buildWaypoints();
            }
        } else {
            if (!waypointsBuilt) {
                int idx = waypointIndex;
                buildWaypoints();
                waypointIndex = Math.min(idx, waypoints.size());
            }
            if (this.tickCount % 20 == 0) refreshWaypointsIfGrown();
            if (waypointIndex < waypoints.size()) {
                BlockPos target = waypoints.get(waypointIndex);
                Vec3 goal = Vec3.atBottomCenterOf(target);
                Vec3 delta = goal.subtract(this.position());
                double dist = delta.length();
                if (dist < 1.0) {
                    waypointIndex++;
                    this.setDeltaMovement(Vec3.ZERO);
                } else {
                    // 行き過ぎないよう残り距離で速度を制限
                    this.setDeltaMovement(delta.scale(Math.min(0.9, dist) / dist));
                }
            } else {
                // 生成済み経路の終端：出口方向へ進み続ける
                this.setDeltaMovement(exitDirection.scale(0.9));
            }
        }
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());

        // デバッグ：10秒ごとに位置を出す
        if (this.tickCount % 200 == 0 && com.example.roomsmod.util.RoomsDebug.isEnabled()) {
            ServerLevel sl = (ServerLevel) this.level();
            Player nearest = sl.getNearestPlayer(this, 500);
            double nd = nearest != null ? this.distanceTo(nearest) : -1;
            com.example.roomsmod.util.RoomsDebug.log(nearest,
                nearest != null ? "A-20 at Z: " + String.format("%.1f", this.getZ()) + " (player Z: " + String.format("%.1f", nearest.getZ()) + ")" : null,
                "A-20 progress waypoint={}/{} pos=({}, {}, {}) nearestPlayerDist={}",
                waypointIndex, waypoints.size(), String.format("%.1f", this.getX()), String.format("%.1f", this.getY()), String.format("%.1f", this.getZ()), String.format("%.1f", nd));
        }

        // プレイヤーが自分よりも大きく前に進んだら消滅
        ServerLevel sl2 = (ServerLevel) this.level();
        Player nearest2 = sl2.getNearestPlayer(this, 500);
        if (nearest2 != null) {
            double ahead = this.getZ() - nearest2.getZ();
            if (ahead > 120.0) {
                com.example.roomsmod.util.RoomsDebug.log("A-20 despawn: passed player by {} blocks (Z={})", String.format("%.1f", ahead), String.format("%.1f", this.getZ()));
                this.discard();
                return;
            }
        } else if (this.tickCount > 1200) {
            // プレイヤーがいない場合の保険
            com.example.roomsmod.util.RoomsDebug.log("A-20 despawn: no player within 500 blocks after {} ticks", this.tickCount);
            this.discard();
            return;
        }

        // 音（バニラのウォーデン近くの音を使う）
        ServerLevel soundLevel = (ServerLevel) this.level();
        Player nearestForSound = soundLevel.getNearestPlayer(this, 500);
        if (nearestForSound != null) {
            double dist = this.distanceTo(nearestForSound);
            double t = Math.max(0.0, Math.min(1.0, (500.0 - dist) / 490.0));
            float baseVolume = (float) (1.5f + 3.0f * Math.pow(t, 1.3));

            Vec3 look = nearestForSound.getViewVector(1.0f);
            Vec3 toEntity = this.position().subtract(nearestForSound.position()).normalize();
            double facing = look.dot(toEntity);
            float facingMul = (float) Math.max(0.6f, (facing + 1.0) * 0.5);

            float volume = Math.min(5.0f, baseVolume * facingMul);

            // 遠くても聞こえるよう、プレイヤーからA-20方向へ最大8ブロックの位置で鳴らす
            // （位置で鳴らすと 16×音量 ブロック以内しか届かないため）
            Vec3 pp = nearestForSound.position();
            double off = Math.min(dist, 8.0);
            Vec3 src = pp.add(toEntity.scale(off));
            // 距離で音量を上げる（150ブロック以内で徐々に大きく）
            float distVol = (float) Math.max(0.3, Math.min(1.0, 1.0 - dist / 150.0)) * 3.0f;
            if (this.tickCount == 1) {
                this.level().playSound(null, src.x, src.y, src.z,
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, Math.max(1.0f, distVol), 0.5f);
            }
        }

        // 近くのプレイヤーに視線判定ダメージ
        AABB searchBox = this.getBoundingBox().inflate(MAX_RAYCAST_DISTANCE);
        for (Player player : this.level().getEntitiesOfClass(Player.class, searchBox)) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;

            double dist = this.distanceTo(player);
            if (dist > MAX_RAYCAST_DISTANCE) continue;

            if (hasLineOfSight(player)) {
                player.hurt(this.damageSources().mobAttack(this), DAMAGE);
            }
        }
    }

    private void spawnVisualParticles() {
        // 明るい赤い光の塊のように見せる
        DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 2.5f);
        for (int i = 0; i < 12; i++) {
            double ox = (this.random.nextDouble() - 0.5) * 1.8;
            double oy = this.random.nextDouble() * 2.2;
            double oz = (this.random.nextDouble() - 0.5) * 1.8;
            this.level().addParticle(redDust, this.getX() + ox, this.getY() + oy, this.getZ() + oz, 0, 0.02, 0);
        }
        // 炎のパーティクルも少し
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0, this.getZ(), 0, 0.05, 0);
            this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    private boolean hasLineOfSight(LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 end = target.getEyePosition();

        BlockHitResult hit = this.level().clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        ));

        // ヒットなし、またはヒット位置がターゲットより遠いなら視線が通っている
        if (hit.getType() == HitResult.Type.MISS) return true;

        // オーク板（隠れ場所）で遮られた場合はダメージなし
        BlockPos hitPos = hit.getBlockPos();
        if (this.level().getBlockState(hitPos).is(Blocks.OAK_PLANKS)) {
            return false;
        }

        // その他のブロックで遮られた場合も通っていない
        double hitDist = start.distanceTo(hit.getLocation());
        double targetDist = start.distanceTo(end);
        return hitDist >= targetDist - 0.5;
    }

    /** 接近ループ音のフェードイン：MAX_RANGE以遠で0、FULL_RANGE以内で最大、間は t^CURVE_EXPONENT（t=線形の近さ0..1）。 */
    public static final double APPROACH_MAX_RANGE = 120.0;
    public static final double APPROACH_FULL_RANGE = 4.0;
    public static final float APPROACH_MAX_VOLUME = 3.0f;
    public static final float MIN_APPROACH_VOLUME = 0.02f;
    /** 1.0=線形、<1で遠くから早めに聞こえる（0.75: 100ブロックで約27%）。 */
    public static final double APPROACH_CURVE_EXPONENT = 0.75;

    public static float approachVolume(double dist) {
        double t = (APPROACH_MAX_RANGE - dist) / (APPROACH_MAX_RANGE - APPROACH_FULL_RANGE);
        t = Math.max(0.0, Math.min(1.0, t));
        return (float) Math.pow(t, APPROACH_CURVE_EXPONENT) * APPROACH_MAX_VOLUME;
    }
}
