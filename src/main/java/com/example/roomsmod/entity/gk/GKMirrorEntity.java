package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** GK-Mirror. 排他グループ gk_sense（GK-12 と同時に出ない）。 */
public class GKMirrorEntity extends GKRoomWatcher {
    public static final double LOOK_DOT = 0.8;
    public GKMirrorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Mirror"; }
    @Override protected int telegraphTicks() { return 40; }
    @Override protected int watchTicks() { return 160; }
    @Override protected int limit() { return 40; }
    @Override protected float damage() { return 10f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_MIRROR_CHIME.get(); }
    @Override protected int cueInterval() { return 20; }
    @Override protected String chatStart() { return "GK-Mirror の澄んだ音（予告 2秒）"; }
    @Override protected boolean violates(Player p) {
        if (!canSee(p)) return false;
        Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
        return p.getViewVector(1f).dot(to) < LOOK_DOT;
    }
    @Override protected void clientTick() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + random.nextDouble() * 2, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
    }
}
