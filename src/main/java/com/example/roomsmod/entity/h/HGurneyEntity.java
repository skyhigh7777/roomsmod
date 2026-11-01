package com.example.roomsmod.entity.h;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.u.URusher;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * H-260（暴走するストレッチャー）。4 部屋後ろから車輪の音を鳴らして走り抜け、1 部屋先で一度跳ね返り、3 部屋戻る（ハードは +1 回）。
 * 動きは URusher（加減速つき）。H の外の部屋（ハードの合同抽選）では URusher と同じく少し弱める。隠れてやり過ごす。
 */
public class HGurneyEntity extends URusher {
    public static final double SPEED = 1.3, ACCEL = 0.06, DECEL = 0.10;
    public static final int ROOMS_BEHIND = 4, ROOMS_AHEAD = 1, TURNS = 1, TURN_ROOMS = 3, TURN_PAUSE = 25, GRACE = 70;
    public static final double HIT_RANGE = 20.0;
    public HGurneyEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-260"; }
    @Override public String homeSection() { return "h"; }
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
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.NONE; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected float noisePitch() { return 1.2f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.H260_RATTLE.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.75f, 0.85f, 0.9f); }
}
