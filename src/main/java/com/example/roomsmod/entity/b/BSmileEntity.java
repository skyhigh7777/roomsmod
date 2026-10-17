package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKJanitorEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Smile。 */
public class BSmileEntity extends GKJanitorEntity {
    public BSmileEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Smile"; }
    @Override protected double speed() { return 0.22; }
    @Override protected SoundEvent approachSound() { return SoundEvents.PHANTOM_AMBIENT; }
    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.1f), 1.3f);
        level().addParticle(d, getX() + (random.nextDouble() - 0.5), getY() + 0.8 + random.nextDouble() * 0.4, getZ() + (random.nextDouble() - 0.5), 0, -0.08, 0);
    }
}
