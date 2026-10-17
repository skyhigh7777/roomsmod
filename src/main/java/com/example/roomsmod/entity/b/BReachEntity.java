package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKBell2Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** B-Reach。 */
public class BReachEntity extends GKBell2Entity {
    public BReachEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Reach"; }
    @Override protected double speed() { return 1.0; }
    @Override protected double hitRange() { return 12.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.SKELETON_AMBIENT; }
    @Override protected void clientTick() {
        level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 3, getY() + random.nextDouble() * 2.5, getZ() + (random.nextDouble() - 0.5) * 3, 0, 0, 0);
    }
}
