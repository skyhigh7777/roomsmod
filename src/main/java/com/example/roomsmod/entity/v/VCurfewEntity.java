package com.example.roomsmod.entity.v;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * V-70（門限）。村の鐘が数回鳴ったあと、通路（部屋の経路）の上にいると被弾。家の中（経路から外れた場所）か隠れ場所へ入る。
 */
public class VCurfewEntity extends VEntity {
    public static final int RINGS = 3, RING_EVERY = 30, GRACE_AFTER = 20;
    public static final float DAMAGE = 25f;
    public static final double PATH_SAFE = 1.6;
    private int phase = 0;

    public VCurfewEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "V-70"; }
    @Override public String homeSection() { return "v"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    /** 部屋の経路（村の道）から外れているか。 */
    static boolean offPath(RoomsState rs, int room, Player p) {
        List<BlockPos> path = rs.path(room);
        if (path.isEmpty()) return true;
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < path.size(); i++) {
            BlockPos a = path.get(i), b = path.get(i + 1);
            best = Math.min(best, distToSeg(p.getX(), p.getZ(), a.getX() + 0.5, a.getZ() + 0.5, b.getX() + 0.5, b.getZ() + 0.5));
        }
        for (BlockPos a : path) best = Math.min(best, Math.hypot(p.getX() - (a.getX() + 0.5), p.getZ() - (a.getZ() + 0.5)));
        return best > PATH_SAFE;
    }

    private static double distToSeg(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
        double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        int ringAt = (phase - 1) / RING_EVERY;
        if (phase == 1) {
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.v70_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "V-70: 門限の鐘", "{} telegraph room={}", vName(), startRoom);
        }
        if (phase <= RINGS * RING_EVERY && (phase - 1) % RING_EVERY == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.8f);
            RoomsDebug.log("{} bell {}/{}", vName(), ringAt + 1, RINGS);
        }
        int judge = RINGS * RING_EVERY + GRACE_AFTER;
        if (phase == judge) {
            RoomsState rs = RoomsState.get(level);
            int hit = 0, spared = 0;
            for (Player p : playersNear(48)) {
                if (p.isSpectator() || !inRoom(level, p)) continue;
                if (HidingRules.isHiding(p) || offPath(rs, startRoom, p) || (startRoom > 0 && offPath(rs, startRoom - 1, p))) {
                    spared++;
                    RoomsDebug.log(p, "V-70: 家の中（または隠れ）で無事", "{} spared player={} hide={}", vName(), p.getGameProfile().getName(), HidingRules.isHiding(p));
                    continue;
                }
                RoomsDebug.log(p, "V-70: 道にいた", "{} hit player={}", vName(), p.getGameProfile().getName());
                RoomsDamage.hurt(this, p, DAMAGE);
                hit++;
            }
            leave("curfew hits=" + hit + " spared=" + spared);
        }
    }

    @Override protected void clientTick() {
        if (tickCount % 6 == 0) level().addParticle(ParticleTypes.NOTE, getX(), getY() + 1.8, getZ(), 0, 0, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
