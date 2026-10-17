package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Grin。 */
public class BGrinEntity extends BRusher {
    public BGrinEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Grin"; }
    @Override protected double speed() { return pathSize() == 0 ? 0.6 : 0.6 + 1.6 * Math.min(1.0, waypointIndex / (double) pathSize()); }
    @Override protected int roomsBehind() { return 6; }
    @Override protected double hitRange() { return 15.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.WARDEN_AMBIENT; }
    @Override protected Vector3f color() { return new Vector3f(0.95f, 0.1f, 0.2f); }
    @Override protected double midStopAt() { return 0.55; }
    @Override protected int midStopTicks() { return 70; }
    @Override protected int graceTicks() { return 90; }
}
