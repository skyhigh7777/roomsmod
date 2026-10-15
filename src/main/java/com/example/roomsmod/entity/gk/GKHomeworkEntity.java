package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** GK-Homework. */
public class GKHomeworkEntity extends GKRoomWatcher {
    public GKHomeworkEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Homework"; }
    @Override protected int telegraphTicks() { return 60; }
    @Override protected int watchTicks() { return 160; }
    @Override protected int limit() { return 30; }
    @Override protected float damage() { return 8f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_HOMEWORK_PAGE.get(); }
    @Override protected int cueInterval() { return 20; }
    @Override protected String chatStart() { return "GK-Homework のページをめくる音（予告 3秒）"; }
    @Override protected boolean violates(Player p) { return !p.isCrouching(); }
    @Override protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.ENCHANT, getX(), getY() + 1.2, getZ(), random.nextDouble() - 0.5, 0.5, random.nextDouble() - 0.5);
    }
}
