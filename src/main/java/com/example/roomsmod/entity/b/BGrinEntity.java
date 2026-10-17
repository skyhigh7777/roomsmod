package com.example.roomsmod.entity.b;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** B-Grin。 */
public class BGrinEntity extends BMotionWatcher {
    public BGrinEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Grin"; }
    @Override protected int telegraphTicks() { return 50; }
    @Override protected int watchTicks() { return 80; }
    @Override protected int limit() { return 6; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return SoundEvents.WARDEN_HEARTBEAT; }
    @Override protected int cueInterval() { return 20; }
    @Override protected String chatStart() { return "B-Grin がにやけているわ（予告 2.5秒）"; }
    @Override protected Vector3f glitchColor() { return new Vector3f(0.95f, 0.1f, 0.2f); }
    @Override protected boolean violates(Player p) {
        Vec3 m = moved(p);
        return m.horizontalDistance() > 0.03;
    }
}
