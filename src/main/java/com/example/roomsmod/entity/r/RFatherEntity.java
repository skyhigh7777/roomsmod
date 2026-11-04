package com.example.roomsmod.entity.r;

import com.example.roomsmod.RoomsMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * R-200（父）。R-100（家族）の重い版：重い足音と砂嵐、5 部屋後ろから速く、1 部屋先で 1 回折り返して 3 部屋戻る（ハード +1 回）。
 * テーブルの下は R-100 と同じく見つかる（RFamilyEntity.findsUnderTable）。ロッカーに隠れ、折り返しが通り過ぎるまで出ない。
 */
public class RFatherEntity extends RFamilyEntity {
    public RFatherEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-200"; }
    @Override protected double speed() { return 1.45; }
    @Override protected int roomsBehind() { return 5; }
    @Override protected int maxTurns() { return 1; }
    @Override protected int turnRooms() { return 3; }
    @Override protected int turnPause() { return 40; }
    @Override protected double hitRange() { return 22.0; }
    @Override protected float noisePitch() { return 0.7f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.R200_STEPS.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.35f, 0.3f, 0.3f); }
}
