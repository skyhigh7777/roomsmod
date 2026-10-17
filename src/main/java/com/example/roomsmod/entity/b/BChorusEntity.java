package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** B-Chorus。 */
public class BChorusEntity extends BMotionWatcher {
    public BChorusEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Chorus"; }
    @Override protected int telegraphTicks() { return 40; }
    @Override protected int watchTicks() { return 200; }
    @Override protected int limit() { return 6; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM; }
    @Override protected int cueInterval() { return 25; }
    @Override protected String chatStart() { return "B-Chorus の合唱（予告 2秒）"; }
    @Override protected Vector3f glitchColor() { return new Vector3f(0.2f, 0.95f, 0.3f); }
    @Override protected boolean violates(Player p) {
        Vec3 m = moved(p);
        return p.isSprinting() || (!p.onGround() && m.y > 0.05);
    }
}
