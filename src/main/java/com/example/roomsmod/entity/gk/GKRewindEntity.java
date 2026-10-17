package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** GK-Rewind：時計の針の音のあと、部屋にいる（隠れていない）プレイヤーを2部屋前へ戻す。ダメージなし。 */
public class GKRewindEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int TELEGRAPH = 20 * 6, ROOMS_BACK = 2;
    private int phase = 0;
    public GKRewindEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Rewind"; }
    @Override protected int maxLifeTicks() { return TELEGRAPH + 20; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        if (playerRoom < 1) return null;
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        RoomsState rs = RoomsState.get(level);
        List<Player> ps = GKUtil.players(level, this, startRoom, 0);
        if (phase % 10 == 1) level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_HOMEWORK_PAGE.get(), SoundSource.HOSTILE, 1.6f, 0.6f + phase / (float) TELEGRAPH);
        if (phase == 1) for (Player p : playersNear(48)) RoomsDebug.log(p, "GK-Rewind: 時計の音（" + TELEGRAPH / 20 + "秒）", "GK-Rewind telegraph room={}", startRoom);
        if (phase % 20 == 0) GKUtil.actionbar(ps, Component.translatable("message.roomsmod.gk_rewind", (TELEGRAPH - phase) / 20));
        if (phase < TELEGRAPH) return;
        int back = Math.max(1, startRoom - ROOMS_BACK);
        Vec3 to = SafeSpot.find(level, rs, back);
        for (Player p : ps) {
            boolean hid = HidingRules.isHiding(p);
            RoomsDebug.log(p, "GK-Rewind: " + (hid ? "隠れて無事" : "2部屋戻されたわ"), "GK-Rewind player={} hiding={} to room {} {}", p.getGameProfile().getName(), hid, back, to);
            if (hid || to == null) continue;
            p.teleportTo(to.x, to.y, to.z);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
        }
        leave("rewound");
    }

    @Override protected void clientTick() {
        for (int i = 0; i < 2; i++) level().addParticle(ParticleTypes.PORTAL, getX() + (random.nextDouble() - 0.5) * 2, getY() + random.nextDouble() * 2, getZ() + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase; }
}
