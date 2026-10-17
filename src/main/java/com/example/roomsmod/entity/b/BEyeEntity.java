package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** B-Eye。 */
public class BEyeEntity extends BMotionWatcher {
    public BEyeEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Eye"; }
    @Override protected int telegraphTicks() { return 50; }
    @Override protected int watchTicks() { return 120; }
    @Override protected int limit() { return 15; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return SoundEvents.ENDER_EYE_DEATH; }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "B-Eye が開くわ（予告 2.5秒）"; }
    @Override protected Vector3f glitchColor() { return new Vector3f(0.9f, 0.5f, 0.1f); }
    @Override protected boolean violates(Player p) {
        Vec3 m = moved(p);
        return p.hasLineOfSight(this);
    }
}
