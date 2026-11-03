package com.example.roomsmod.entity.orb;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 回復のオーブ（ワールドスポーン地点の少し先に 1 つ、{@link HealingOrb} が置く）。光る球（ClientSetup の HealingOrbRenderer、上下にゆっくり揺れる）。
 * <ul>
 *   <li>体が {@value #RANGE} ブロック以内（オーブの中心から体の一番近い点まで）のプレイヤーを {@value #INTERVAL} tick ごとに {@value #HEAL} 回復（1.5 秒で 0.5 ハート）。</li>
 *   <li>壊せない・倒せない：当たり判定なし、ダメージ・押し出し・爆発・重力なし（/kill と管理コマンドだけで消える）。</li>
 *   <li>保存された「今のオーブ」の UUID と違うオーブは、読み込まれた時点で自分で消える（置き直し・二重の防止）。</li>
 * </ul>
 */
public class HealingOrbEntity extends Entity {
    public static final double RANGE = 2.0;
    public static final int INTERVAL = 30;
    public static final float HEAL = 1.0f;

    public HealingOrbEntity(EntityType<? extends HealingOrbEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion e) { return true; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldBeSaved() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        Level lv = level();
        if (lv.isClientSide) {
            if (tickCount % 8 == 0) lv.addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 0.2 + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0.015, 0);
            return;
        }
        ServerLevel sl = (ServerLevel) lv;
        if (tickCount % 20 == 1 && !HealingOrb.isCurrent(sl, this)) { HealingOrb.log("healing orb {} at {} is not the current one -> removed", getUUID(), blockPosition());
            if (!HealingOrb.isRooms(sl) && sl.getBlockState(blockPosition()).is(net.minecraft.world.level.block.Blocks.LIGHT)) sl.setBlockAndUpdate(blockPosition(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); // 以前のオーバーワールドの光源
            discard(); return; }
        if (tickCount % INTERVAL != 0) return;
        Vec3 c = position().add(0, getBbHeight() / 2, 0);
        for (Player p : sl.players()) {
            if (!(p instanceof ServerPlayer sp) || sp.isSpectator() || !sp.isAlive()) continue;
            if (distSqr(sp.getBoundingBox(), c) > RANGE * RANGE) continue;
            if (sp.getHealth() >= sp.getMaxHealth()) continue;
            sp.heal(HEAL);
            sl.sendParticles(ParticleTypes.HEART, sp.getX(), sp.getY() + sp.getBbHeight() + 0.3, sp.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private static double distSqr(AABB b, Vec3 p) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }
}
