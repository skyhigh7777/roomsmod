package com.example.roomsmod.entity.f;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * F-35（浮かび上がるもの）。水の下を追ってくる。プレイヤーの足元が泡立ち（BUBBLE_TICKS、ハード HARD_BUBBLE_TICKS）、
 * その場所に水から飛び出す：その瞬間、泡の中心から HIT_RADIUS 以内にいた（隠れていない）プレイヤーに DAMAGE＋鈍化。
 * 少しして沈み、また足元が泡立つ。CYCLES 回（ハード +1）で去る。隠れている間は待ち、PATIENCE tick 隠れ続けると去る。
 * 泡が出たらその場を離れる（立ち止まらない）か、隠れる。
 */
public class FSurfacerEntity extends VEntity {
    public static final int FIRST_DELAY = 40, BUBBLE_TICKS = 30, HARD_BUBBLE_TICKS = 22, UP_TICKS = 20, DOWN_TICKS = 25, CYCLES = 5, PATIENCE = 200;
    public static final double HIT_RADIUS = 1.8, TARGET_RANGE = 32;
    public static final float DAMAGE = 20f;
    private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(FSurfacerEntity.class, EntityDataSerializers.BOOLEAN);

    /** 0=待ち（次の泡を選ぶ） 1=泡 2=水上 3=沈む */
    private int phase = 0, phaseT = 0, cycles = 0, waitHidden = 0;
    private UUID target = null;
    private Vec3 spot = null;

    public FSurfacerEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-35"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_REVEALED, false); }
    /** 水上に出ている間だけ描く。 */
    public boolean isRevealed() { return entityData.get(DATA_REVEALED); }
    private int bubbleTicks() { return hardSpawn ? HARD_BUBBLE_TICKS : BUBBLE_TICKS; }
    private int maxCycles() { return hardSpawn ? CYCLES + 1 : CYCLES; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        startRoom = playerRoom;
        target = player == null ? null : player.getUUID();
        Vec3 at = player != null ? player.position() : null;
        if (at == null) { BlockPos p = rs.path(playerRoom).isEmpty() ? null : rs.path(playerRoom).get(0); if (p == null) return null; at = Vec3.atBottomCenterOf(p); }
        setPos(at.x, at.y, at.z);
        phaseT = -FIRST_DELAY;
        RoomsDebug.log("{} under the water room={} target={} hard={} offSection={}", vName(), playerRoom, player == null ? "-" : player.getGameProfile().getName(), hardSpawn, offSection);
        return blockPosition();
    }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return prepareFor(level, rs, null, playerRoom); }

    private Player pickTarget() {
        Player best = null;
        for (Player p : playersNear(TARGET_RANGE)) {
            if (p.isSpectator()) continue;
            if (p.getUUID().equals(target)) return p;
            if (best == null || distanceTo(p) < distanceTo(best)) best = p;
        }
        if (best != null) target = best.getUUID();
        return best;
    }

    private void enter(int ph) { phase = ph; phaseT = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        phaseT++;
        switch (phase) {
            case 0 -> {
                if (phaseT < 0) return;
                if (cycles >= maxCycles()) { leave("cycles done"); return; }
                Player p = pickTarget();
                if (p == null) { leave("no target"); return; }
                if (HidingRules.isHiding(p)) {
                    if (++waitHidden >= PATIENCE) leave("target stayed hidden");
                    else if (waitHidden == 1) RoomsDebug.log(p, vName() + ": 隠れているので待っている", "{} waits (target hiding)", vName());
                    return;
                }
                spot = new Vec3(p.getX(), p.getY(), p.getZ());
                setPos(spot.x, spot.y - 1.2, spot.z);
                level.playSound(null, spot.x, spot.y, spot.z, RoomsMod.F35_BUBBLE.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
                if (cycles == 0) GKUtil.actionbar(java.util.List.of(p), Component.translatable("message.roomsmod.f35_bubbles"));
                RoomsDebug.log(p, vName() + ": 足元が泡立った (" + (cycles + 1) + "/" + maxCycles() + ")", "{} bubbles at {} cycle={}/{} in={}", vName(), BlockPos.containing(spot), cycles + 1, maxCycles(), bubbleTicks());
                enter(1);
            }
            case 1 -> {
                if (phaseT % 2 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP, spot.x, spot.y + 0.45, spot.z, 4, 0.5, 0.05, 0.5, 0.01);
                    level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, spot.x, spot.y + 0.2, spot.z, 3, 0.4, 0.05, 0.4, 0.05);
                }
                if (phaseT == bubbleTicks() / 2) level.playSound(null, spot.x, spot.y, spot.z, RoomsMod.F35_BUBBLE.get(), SoundSource.HOSTILE, 1.6f, 1.3f);
                if (phaseT >= bubbleTicks()) surface(level);
            }
            case 2 -> { if (phaseT >= UP_TICKS) { entityData.set(DATA_REVEALED, false); setPos(spot.x, spot.y - 1.2, spot.z); enter(3); } }
            case 3 -> { if (phaseT >= DOWN_TICKS) enter(0); }
            default -> enter(0);
        }
    }

    private void surface(ServerLevel level) {
        cycles++;
        setPos(spot.x, spot.y, spot.z);
        entityData.set(DATA_REVEALED, true);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.5f, 0.7f);
        level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 0.6, spot.z, 40, 0.6, 0.3, 0.6, 0.2);
        int hit = 0;
        for (Player p : playersNear(HIT_RADIUS + 2)) {
            double dx = p.getX() - spot.x, dz = p.getZ() - spot.z;
            if (p.isSpectator() || Math.sqrt(dx * dx + dz * dz) > HIT_RADIUS || Math.abs(p.getY() - spot.y) > 2) continue;
            if (HidingRules.isProtectedFrom(this, p)) continue;
            RoomsDebug.log(p, vName() + ": 足元から飛び出した", "{} surfaced under player={} creative={}", vName(), p.getGameProfile().getName(), p.isCreative());
            if (RoomsDamage.hurt(this, p, DAMAGE)) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            hit++;
        }
        if (hit == 0) RoomsDebug.log("{} surfaced at {} (nobody there)", vName(), BlockPos.containing(spot));
        enter(2);
    }

    @Override protected void clientTick() {
        if (isRevealed() && tickCount % 3 == 0) level().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 1.0 + random.nextDouble(), getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " t=" + phaseT + " cycles=" + cycles + "/" + maxCycles() + " waitHidden=" + waitHidden; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); t.putInt("PhaseT", phaseT); t.putInt("Cycles", cycles); if (target != null) t.putUUID("Target", target); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); phaseT = t.getInt("PhaseT"); cycles = t.getInt("Cycles"); target = t.hasUUID("Target") ? t.getUUID("Target") : null; if (phase != 0) phase = 3; }
}
