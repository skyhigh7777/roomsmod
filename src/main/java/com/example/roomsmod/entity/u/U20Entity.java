package com.example.roomsmod.entity.u;

import com.example.roomsmod.RoomsMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** U-20。 */
public class U20Entity extends URusher {
    public static final double SPEED = 1.6, ACCEL = 0.08;
    public static final int ROOMS_BEHIND = 6, ROOMS_AHEAD = 2, GRACE = 70;
    public static final double HIT_RANGE = 22.5;
    public U20Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-20"; }
    @Override protected double speed() { return SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return 0; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return ROOMS_AHEAD; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.BOOM; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected SoundEvent approachSound() { return RoomsMod.U20_APPROACH.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.95f, 0.3f, 0.2f); }
}
