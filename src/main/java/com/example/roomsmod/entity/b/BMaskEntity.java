package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** B-30。 */
public class BMaskEntity extends BMotionWatcher {
    public BMaskEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-30"; }
    @Override protected int telegraphTicks() { return 50; }
    @Override protected int watchTicks() { return 100; }
    @Override protected int limit() { return 12; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return SoundEvents.WITCH_CELEBRATE; }
    @Override protected int cueInterval() { return 30; }
    @Override protected String chatStart() { return "B-30 が笑っているわ（予告 2.5秒）"; }
    @Override protected Vector3f glitchColor() { return new Vector3f(0.75f, 0.2f, 0.9f); }
    @Override protected boolean violates(Player p) {
        Vec3 m = moved(p);
        return !p.isShiftKeyDown();
    }
}
