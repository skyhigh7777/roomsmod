package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** GK-12。 */
public class GKStareEntity extends GKRoomWatcher {
    public static final double LOOK_DOT = 0.9;
    public GKStareEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-12"; }
    @Override protected int telegraphTicks() { return 40; }
    @Override protected int watchTicks() { return 240; }
    @Override protected int limit() { return 20; }
    @Override protected float damage() { return 12f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_STARE_HUM.get(); }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "GK-12 のうなり（予告 2秒）"; }
    @Override protected boolean violates(Player p) {
        if (!canSee(p)) return false;
        Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
        return p.getViewVector(1f).dot(to) > LOOK_DOT;
    }
    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.1f), 1.2f);
        level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 1.6, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
    }
}
