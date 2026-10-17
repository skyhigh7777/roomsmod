package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-015。 */
public class BStaticEntity extends BRusher {
    public BStaticEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-015"; }
    @Override protected double speed() { return 2.6; }
    @Override protected int roomsBehind() { return 7; }
    @Override protected double hitRange() { return 18.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.BEACON_AMBIENT; }
    @Override protected Vector3f color() { return new Vector3f(1f, 0.1f, 0.15f); }

    @Override protected int graceTicks() { return 110; }
}
