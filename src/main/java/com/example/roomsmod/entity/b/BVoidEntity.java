package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * B-Void。最奥の部屋の先（出口側）から現れる。予告のあと、隠れていないプレイヤーがいれば経路を外れてまっすぐ追い、
 * KILL_RADIUS 以内で即死。全員隠れていれば経路を奥から手前へたどって去る。
 */
public class BVoidEntity extends VPathWalker {
    public static final int TELEGRAPH = 110, ROOMS_BACK = 7;
    public static final double HOME_SPEED = 0.3, KILL_RADIUS = 2.5, SENSE = 64;
    private boolean homing = false;
    public BVoidEntity(EntityType<? extends Mob> type, Level level) { super(type, level); this.noPhysics = true; }
    @Override public String vName() { return "B-Void"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return 1.1; }
    @Override protected int graceTicks() { return TELEGRAPH; }
    @Override protected double hitRange() { return 0.1; } // 経路上の当たりは使わない（隠れていない人は追跡で処理）
    @Override protected float damage() { return 0f; }
    @Override protected SoundEvent approachSound() { return SoundEvents.WARDEN_AMBIENT; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int last = rs.roomPaths.size() - 1;
        if (last < 1) return null;
        return placeAtStart(rs, last, Math.max(0, last - ROOMS_BACK));
    }

    private Player exposedTarget(ServerLevel level) {
        Player best = null;
        for (Player p : playersNear(SENSE))
            if (!p.isCreative() && !HidingRules.isHiding(p) && (best == null || distanceTo(p) < distanceTo(best))) best = p;
        return best;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (grace < graceTicks()) {
            if (grace == 0) {
                RoomsDebug.announce(playersNear(200), "B-Void が前から来るわ（" + TELEGRAPH / 20.0 + "秒）", "B-Void telegraph from room {} toward {}", startRoom, endRoom);
            }
            if (grace % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4.0f, 0.4f);
        }
        if (grace < graceTicks()) { super.serverTick(level); return; }
        Player t = exposedTarget(level);
        if (t != null) {
            if (!homing) { homing = true; RoomsDebug.log("B-Void homing player={} dist={}", t.getGameProfile().getName(), String.format("%.1f", distanceTo(t))); }
            Vec3 to = t.position().add(0, 0.5, 0).subtract(position());
            double d = to.length();
            if (d <= KILL_RADIUS) {
                RoomsDebug.log(t, "B-Void に捕まったわ", "B-Void caught player={}", t.getGameProfile().getName());
                if (!t.isCreative()) com.example.roomsmod.util.RoomsDamage.hurt(this, t, 1000f);
                leave("caught " + t.getGameProfile().getName());
                return;
            }
            Vec3 step = to.normalize().scale(Math.min(HOME_SPEED, d));
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            faceTowards(t.position());
            if (tickCount % 40 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0f, 0.6f);
            return;
        }
        if (homing) { homing = false; RoomsDebug.log("B-Void lost target (all hiding) -> back to path at waypoint {}", waypointIndex); }
        super.serverTick(level);
    }

    @Override protected void clientTick() {
        super.clientTick();
        for (int i = 0; i < 3; i++)
            level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 2, getY() + random.nextDouble() * 2.4, getZ() + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
    }

    @Override protected String debugState() { return super.debugState() + " homing=" + homing; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Homing", homing); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); homing = t.getBoolean("Homing"); }
}
