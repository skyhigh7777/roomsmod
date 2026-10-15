package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** GK-Bell2。 */
public class GKBell2Entity extends VPathWalker {
    public static final int ROOMS_BEHIND = 5, ROOMS_AHEAD = 1, TURN_PAUSE = 50;
    private boolean turned = false;
    private int pause = 0;
    public GKBell2Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Bell2"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return 1.2; }
    @Override protected int graceTicks() { return 80; }
    @Override protected double hitRange() { return 15.0; }
    @Override protected float damage() { return 8f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.GK_BELL2_BELL.get(); }
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        return placeAtStart(rs, from, Math.min(rs.roomPaths.size() - 1, playerRoom + ROOMS_AHEAD));
    }
    @Override
    protected boolean holdTick(ServerLevel level) {
        if (super.holdTick(level)) return true;
        if (pause > 0) { pause--; return true; }
        return false;
    }
    @Override
    protected boolean onPathEnd(ServerLevel level) {
        if (turned) return false;
        turned = true; pause = TURN_PAUSE;
        reversePath();
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_BELL2_BELL.get(), SoundSource.HOSTILE, 3.0f, 0.7f);
        for (var p : playersNear(160)) RoomsDebug.log(p, "GK-Bell2 が折り返すわ（" + TURN_PAUSE / 20.0 + "秒後）", "GK-Bell2 turning back pause={} room={}", TURN_PAUSE, startRoom);
        return true;
    }
    @Override protected void clientTick() {
        super.clientTick();
        level().addParticle(ParticleTypes.NOTE, getX(), getY() + 2.2, getZ(), random.nextDouble(), 0, 0);
    }
    @Override protected String debugState() { return super.debugState() + " turned=" + turned + " pause=" + pause; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Turned", turned); t.putInt("Pause", pause); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); turned = t.getBoolean("Turned"); pause = t.getInt("Pause"); }
}
