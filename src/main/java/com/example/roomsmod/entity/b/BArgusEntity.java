package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** B-Argus。 */
public class BArgusEntity extends BMotionWatcher {
    public BArgusEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Argus"; }
    @Override protected int telegraphTicks() { return 50; }
    @Override protected int watchTicks() { return 160; }
    @Override protected int limit() { return 40; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return SoundEvents.ELDER_GUARDIAN_AMBIENT; }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "B-Argus の目が開くわ（予告 2.5秒）"; }
    @Override protected Vector3f glitchColor() { return new Vector3f(0.9f, 0.9f, 0.95f); }
    @Override protected boolean violates(Player p) {
        Vec3 m = moved(p);
        return m.horizontalDistance() < 0.02;
    }
}
