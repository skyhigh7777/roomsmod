package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** GK-Janitor。 */
public class GKJanitorEntity extends VPathWalker {
    public static final int ROOMS_BEHIND = 2;
    public GKJanitorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Janitor"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return 0.19; }
    @Override protected int graceTicks() { return 60; }
    @Override protected double hitRange() { return 4.0; }
    @Override protected float damage() { return 6f; }
    @Override protected int loopTicks() { return 20; }
    @Override protected SoundEvent approachSound() { return RoomsMod.GK_JANITOR_SQUEAK.get(); }
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, rs.roomPaths.size() - 1);
        RoomsDebug.log("GK-Janitor start room={} (player room {})", from, playerRoom);
        return s;
    }
    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 0, 0, 0);
    }
}
