package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.item.FlashlightHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** GK-Moth。 */
public class GKMothEntity extends GKRoomWatcher {
    public GKMothEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Moth"; }
    @Override protected int telegraphTicks() { return 60; }
    @Override protected int watchTicks() { return 200; }
    @Override protected int limit() { return 15; }
    @Override protected float damage() { return 10f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_MOTH_FLUTTER.get(); }
    @Override protected int cueInterval() { return 20; }
    @Override protected boolean violates(Player p) { return !FlashlightHandler.heldOn(p).isEmpty(); }
    @Override protected String chatStart() { return "GK-Moth の羽音（予告 3秒）"; }
    @Override protected void clientTick() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX() + random.nextDouble() - 0.5, getY() + 1 + random.nextDouble(), getZ() + random.nextDouble() - 0.5, 0, 0, 0);
    }
}
