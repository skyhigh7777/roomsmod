package com.example.roomsmod.entity;

import net.minecraft.core.BlockPos;
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

/**
 * A-60：高速で部屋を突進し、有限距離の視線が通ったプレイヤーにダメージを与える。
 * 隠れ場所（オーク板）で視線を遮れば回避可能。
 */
public class A60Entity extends Mob {
    private static final double MAX_RAYCAST_DISTANCE = 22.5;
    private static final float DAMAGE = 8.0f;

    public A60Entity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setGlowingTag(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 1.5)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;

        if (this.level().isClientSide()) {
            spawnVisualParticles();
            return;
        }

        // 出現後60tick（約3秒）は動かない
        if (this.tickCount < 60) {
            this.setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 motion = new Vec3(0, 0, 0.9);
            this.setDeltaMovement(motion);
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        }

        if (this.tickCount == 1) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 0.6f, 0.5f);
        }
        if (this.tickCount % 40 == 0) {
            this.level().playSound(null, this.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 0.4f, 1.2f);
        }

        if (this.tickCount > 180) {
            this.discard();
            return;
        }

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
        DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 2.5f);
        for (int i = 0; i < 12; i++) {
            double ox = (this.random.nextDouble() - 0.5) * 1.8;
            double oy = this.random.nextDouble() * 2.2;
            double oz = (this.random.nextDouble() - 0.5) * 1.8;
            this.level().addParticle(redDust, this.getX() + ox, this.getY() + oy, this.getZ() + oz, 0, 0.02, 0);
        }
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

        if (hit.getType() == HitResult.Type.MISS) return true;

        BlockPos hitPos = hit.getBlockPos();
        if (this.level().getBlockState(hitPos).is(Blocks.OAK_PLANKS)) {
            return false;
        }

        double hitDist = start.distanceTo(hit.getLocation());
        double targetDist = start.distanceTo(end);
        return hitDist >= targetDist - 0.5;
    }
}
