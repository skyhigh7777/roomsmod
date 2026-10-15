package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** GK-Whisper. */
public class GKWhisperEntity extends GKRoomWatcher {
    public static final double MOVE_EPSILON = 0.03;
    private final Map<UUID, Vec3> last = new HashMap<>();
    public GKWhisperEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Whisper"; }
    @Override protected int telegraphTicks() { return 60; }
    @Override protected int watchTicks() { return 120; }
    @Override protected int limit() { return 10; }
    @Override protected float damage() { return 8f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_WHISPER_SOUND.get(); }
    @Override protected int cueInterval() { return 30; }
    @Override protected String chatStart() { return "GK-Whisper のささやき（予告 3秒）"; }
    @Override protected boolean violates(Player p) {
        Vec3 now = p.position(), prev = last.put(p.getUUID(), now);
        return prev != null && (now.subtract(prev).horizontalDistance() > MOVE_EPSILON || now.y - prev.y > 0.05);
    }
    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SOUL, getX() + random.nextDouble() - 0.5, getY() + 1.5, getZ() + random.nextDouble() - 0.5, 0, 0.02, 0);
    }
}
