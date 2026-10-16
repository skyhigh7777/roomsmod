package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GK-Whisper：部屋にスピーカーを置く。ささやきの間はスピーカーのそばにいないと危ない（隠れても無駄）。 */
public class GKWhisperEntity extends GKTimedEntity {
    public static final int TELEGRAPH = 80, WATCH = 120, LIMIT = 40;
    public static final double NEAR = 3.5;
    public static final float DAMAGE = 20f;
    private BlockPos speaker = null;
    private final Map<UUID, Integer> away = new HashMap<>();
    public GKWhisperEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Whisper"; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> s = GKUtil.freeSpots(level, rs, playerRoom, level.random, 1);
        if (s.isEmpty()) return null;
        speaker = s.get(0);
        place(level, speaker, Blocks.NOTE_BLOCK.defaultBlockState());
        setPos(speaker.getX() + 0.5, speaker.getY() + 1, speaker.getZ() + 0.5);
        RoomsDebug.log("GK-Whisper speaker at {}", speaker);
        return speaker;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        List<Player> ps = GKUtil.players(level, this, startRoom, 24);
        if (phase % 30 == 1) level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_WHISPER_SOUND.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
        if (phase == 1) for (Player p : ps) RoomsDebug.log(p, "GK-Whisper: スピーカーへ（" + TELEGRAPH / 20 + "秒）", "GK-Whisper telegraph room={} speaker={}", startRoom, speaker);
        if (phase % 20 == 0) GKUtil.actionbar(ps, Component.translatable(phase <= TELEGRAPH ? "message.roomsmod.gk_whisper_go" : "message.roomsmod.gk_whisper_stay", (TELEGRAPH + WATCH - phase) / 20));
        if (phase <= TELEGRAPH) return;
        if (phase > TELEGRAPH + WATCH) { leave("whisper over"); return; }
        for (Player p : ps) {
            if (p.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(speaker)) <= NEAR) continue;
            int n = away.merge(p.getUUID(), 1, Integer::sum);
            if (n == 1 || n % 10 == 0) RoomsDebug.log(p, "GK-Whisper: スピーカーから離れている " + n + "/" + LIMIT, "GK-Whisper away player={} count={}/{}", p.getGameProfile().getName(), n, LIMIT);
            if (n >= LIMIT) {
                if (!p.isCreative()) RoomsDamage.hurt(this, p, DAMAGE);
                leave("struck " + p.getGameProfile().getName());
                return;
            }
        }
    }

    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.NOTE, getX(), getY() + 0.3, getZ(), random.nextDouble(), 0, 0);
    }
}
