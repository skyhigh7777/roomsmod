package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Reach。 */
public class BReachEntity extends BRusher {
    public BReachEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Reach"; }
    @Override protected double speed() { return 1.0; }
    @Override protected int roomsBehind() { return 5; }
    @Override protected double hitRange() { return 13.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.SKELETON_AMBIENT; }
    @Override protected Vector3f color() { return new Vector3f(0.9f, 0.9f, 0.95f); }
    @Override protected int maxRebounds() { return 1; }
}
