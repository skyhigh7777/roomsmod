package com.example.roomsmod.entity;

import com.example.roomsmod.event.DoorEventHandler;
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
 * A-60：高速で部屋を突進し、有限距離の視線が通ったプレイヤーにダメージを与える。
 * 隠れ場所（オーク板）で視線を遮れば回避可能。
 */
public class A60Entity extends Mob {
    private static final double MAX_RAYCAST_DISTANCE = 22.5;
    private static final float DAMAGE = 8.0f; // 連続ダメージで致命的に
    private ChunkPos lastForcedChunk = null;
    private List<BlockPos> waypoints = new ArrayList<>();
    private int waypointIndex = 0;

    private void buildWaypoints() {
        waypoints.clear();
        // generatedDoorsからZ順にソートして経由点にする
        List<BlockPos> doors = new ArrayList<>(DoorEventHandler.generatedDoors);
        doors.sort((a, b) -> Integer.compare(a.getZ(), b.getZ()));
        for (BlockPos door : doors) {
            if (door.getZ() > this.getZ() - 10) {
                waypoints.add(door);
            }
        }
        waypointIndex = 0;
    }

    public A60Entity(EntityType<? extends Mob> type, Level level) {
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
            return;
        }

        // サーバー側：周辺チャンクを強制読み込みしてTickを維持
        if (this.level() instanceof ServerLevel serverLevel) {
            updateForcedChunks(serverLevel);
        }

        // 前方に高速移動。猶予中は動かない。扉を経由点として通る
        if (this.tickCount < 80) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.tickCount == 1) {
                buildWaypoints();
            }
        } else {
            if (waypointIndex < waypoints.size()) {
                BlockPos target = waypoints.get(waypointIndex);
                Vec3 dir = target.getCenter().subtract(this.position()).normalize();
                this.setDeltaMovement(dir.scale(0.9));
                if (this.position().distanceTo(target.getCenter()) < 2.5) {
                    waypointIndex++;
                }
            } else {
                this.setDeltaMovement(new Vec3(0, 0, 0.9));
            }
        }
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());

        // デバッグ：10秒ごとに位置を出す
        if (this.tickCount % 200 == 0) {
            ServerLevel sl = (ServerLevel) this.level();
            Player nearest = sl.getNearestPlayer(this, 500);
            if (nearest != null) {
                nearest.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "A-60 at Z: " + String.format("%.1f", this.getZ()) + " (player Z: " + String.format("%.1f", nearest.getZ()) + ")"
                ));
            }
        }

        // プレイヤーが自分よりも大きく前に進んだら消滅
        ServerLevel sl2 = (ServerLevel) this.level();
        Player nearest2 = sl2.getNearestPlayer(this, 500);
        if (nearest2 != null) {
            double ahead = this.getZ() - nearest2.getZ();
            if (ahead > 120.0) {
                this.discard();
                return;
            }
        } else if (this.tickCount > 1200) {
            // プレイヤーがいない場合の保険
            this.discard();
            return;
        }

        // 音
        if (this.tickCount == 1) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1.5f, 0.5f);
        }
        if (this.tickCount % 40 == 0) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.0f, 1.2f);
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
}
