package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Static。 */
public class BStaticEntity extends VPathWalker {
    public static final int ROOMS_BEHIND = 6;
    public BStaticEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Static"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return 1.5; }
    @Override protected int graceTicks() { return 70; }
    @Override protected double hitRange() { return 18.0; }
    @Override protected float damage() { return 40f; }
    @Override protected SoundEvent approachSound() { return SoundEvents.BEACON_AMBIENT; }
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        return placeAtStart(rs, Math.max(0, playerRoom - ROOMS_BEHIND), rs.roomPaths.size() - 1);
    }
    @Override protected void clientTick() {
        super.clientTick();
        for (int i = 0; i < 4; i++) {
            DustParticleOptions d = new DustParticleOptions(i % 2 == 0 ? new Vector3f(1f, 0.1f, 0.15f) : new Vector3f(0.1f, 0.9f, 1f), 1.6f);
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, -0.05, 0);
        }
    }
}
