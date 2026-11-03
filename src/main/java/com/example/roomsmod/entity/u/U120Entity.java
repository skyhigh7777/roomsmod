package com.example.roomsmod.entity.u;

import com.example.roomsmod.RoomsMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** U-120。 */
public class U120Entity extends URusher {
    public static final double SPEED = 2.0, ACCEL = 0.15;
    public static final int ROOMS_BEHIND = 8, ROOMS_AHEAD = 1, GRACE = 50;
    public static final double HIT_RANGE = 20.0;
    public U120Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-120"; }
    @Override protected double speed() { return SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return 0; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return ROOMS_AHEAD; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.NONE; }
    @Override protected boolean rushNoise() { return true; }
    /** 出現音がないぶん、接近ループは出現した瞬間から 1 秒ごとに重ねて鳴らす。 */
    @Override protected int loopTicks() { return 20; }
    @Override protected SoundEvent approachSound() { return RoomsMod.U120_APPROACH.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.6f, 0.2f, 0.9f); }
}
