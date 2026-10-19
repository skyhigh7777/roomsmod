package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Chorus。 */
public class BChorusEntity extends BRusher {
    public BChorusEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Chorus"; }
    @Override protected double speed() { return 1.2; }
    @Override protected int roomsBehind() { return 4; }
    @Override protected double hitRange() { return 14.0; }
    @Override protected SoundEvent approachSound() { return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM; }
    @Override protected Vector3f color() { return new Vector3f(0.2f, 0.95f, 0.3f); }
    @Override protected int maxRebounds() { return 3; }
    @Override protected int turnPause() { return 30; }
    @Override protected int graceTicks() { return 70; }
}
