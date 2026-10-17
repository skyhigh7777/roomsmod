package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-090。 */
public class BSmileEntity extends BRusher {
    public BSmileEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-090"; }
    @Override protected double speed() { return 0.9; }
    @Override protected int roomsBehind() { return 5; }
    @Override protected double hitRange() { return 15.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.SOUL_ESCAPE.value(); }
    @Override protected Vector3f color() { return new Vector3f(0.9f, 0.05f, 0.1f); }
    @Override protected int graceTicks() { return 100; }
}
