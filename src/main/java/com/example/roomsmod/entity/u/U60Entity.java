package com.example.roomsmod.entity.u;

import com.example.roomsmod.RoomsMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** U-60。 */
public class U60Entity extends URusher {
    public static final double SPEED = 1.4, ACCEL = 0.05, DECEL = 0.10;
    public static final int ROOMS_BEHIND = 5, ROOMS_AHEAD = 1, TURNS = 1, TURN_ROOMS = 4, TURN_PAUSE = 30, GRACE = 70;
    public static final double HIT_RANGE = 22.5;
    public U60Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-60"; }
    @Override protected double speed() { return SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return DECEL; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return ROOMS_AHEAD; }
    @Override protected int maxTurns() { return TURNS; }
    @Override protected int turnRooms() { return TURN_ROOMS; }
    @Override protected int turnPause() { return TURN_PAUSE; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.CHIMES; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected float noisePitch() { return 0.8f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.U60_APPROACH.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.3f, 0.8f, 0.3f); }
}
