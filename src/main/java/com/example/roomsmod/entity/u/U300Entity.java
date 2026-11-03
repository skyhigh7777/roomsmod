package com.example.roomsmod.entity.u;

import com.example.roomsmod.RoomsMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** U-300。前の部屋から来て往復する。往復のたびに速くなる。 */
public class U300Entity extends URusher {
    public static final double START_SPEED = 1.0, SPEED_STEP = 0.5, MAX_SPEED = 2.5, ACCEL = 0.06, DECEL = 0.12;
    /** 出現はプレイヤーの部屋の ROOMS_AHEAD_START 部屋先（なければ最新の部屋）、最初はプレイヤーの ROOMS_BACK 部屋後ろまで走る。 */
    public static final int ROOMS_AHEAD_START = 2, ROOMS_BACK = 3, TURNS = 3, TURN_ROOMS = 3, TURN_PAUSE = 25, GRACE = 70;
    public static final double HIT_RANGE = 18.0;
    public U300Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-300"; }
    @Override protected double speed() { return Math.min(MAX_SPEED, START_SPEED + SPEED_STEP * turnsDone); }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return DECEL; }
    @Override protected int roomsBehind() { return -ROOMS_AHEAD_START; }
    @Override protected int roomsAhead() { return -ROOMS_BACK; }
    @Override protected int maxTurns() { return TURNS; }
    @Override protected int turnRooms() { return TURN_ROOMS; }
    @Override protected int turnPause() { return TURN_PAUSE; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.CHIMES; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected float noisePitch() { return 0.5f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.U300_APPROACH.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.95f, 0.8f, 0.2f); }
}
