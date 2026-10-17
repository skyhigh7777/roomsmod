package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-180。 */
public class BArgusEntity extends BRusher {
    public BArgusEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-180"; }
    @Override protected double speed() { return 1.8; }
    @Override protected int roomsBehind() { return 5; }
    @Override protected double hitRange() { return 15.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.ELDER_GUARDIAN_AMBIENT; }
    @Override protected Vector3f color() { return new Vector3f(0.85f, 0.85f, 0.95f); }
    @Override protected int extraPasses() { return 1; }
    @Override protected int turnPause() { return 60; }
    @Override protected int graceTicks() { return 85; }
}
