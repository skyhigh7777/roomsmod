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

/** GK-Chime。 */
public class GKChimeEntity extends VPathWalker {
    public static final int ROOMS_BEHIND = 6, ROOMS_AHEAD = 2;
    public static final double SPEED = 1.3;
    public static final int GRACE_TICKS = 80;
    public static final double HIT_RANGE = 16.0;
    public static final float DAMAGE = 8.0f;

    public GKChimeEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Chime"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE_TICKS; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent approachSound() { return RoomsMod.GK_CHIME_APPROACH.get(); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, Math.min(rs.roomPaths.size() - 1, playerRoom + ROOMS_AHEAD));
        RoomsDebug.log("GK-Chime start room={} ({} behind player room {})", from, playerRoom - from, playerRoom);
        return s;
    }

    @Override
    protected void clientTick() {
        super.clientTick();
        for (int i = 0; i < 3; i++)
            level().addParticle(ParticleTypes.NOTE, getX() + random.nextDouble() - 0.5, getY() + random.nextDouble() * 2, getZ() + random.nextDouble() - 0.5, random.nextDouble(), 0, 0);
    }
}
