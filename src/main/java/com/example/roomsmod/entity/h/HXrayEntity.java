package com.example.roomsmod.entity.h;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * H-175（レントゲン）。開けた部屋で予告（WARN_TICKS、ハード HARD_WARN_TICKS）のあと、通路が白く光る。
 * その瞬間、ロッカーに入っているかカーテン（白・灰色の羊毛）の陰にいないプレイヤーは被弾する。
 * テーブル（ベッドの下）は守られない。H-150 とは同時に出ない（排他グループ h_xray）。
 */
public class HXrayEntity extends VEntity {
    public static final int WARN_TICKS = 50, HARD_WARN_TICKS = 35;
    public static final float DAMAGE = 30f;
    public static final int CURTAIN_RANGE = 2;
    private int phase = 0;

    public HXrayEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-175"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int warnTicks() { return hardSpawn ? HARD_WARN_TICKS : WARN_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    /** 白・灰色の羊毛（カーテン）から CURTAIN_RANGE 以内。 */
    static boolean behindCurtain(ServerLevel level, Player p) {
        BlockPos feet = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(feet.offset(-CURTAIN_RANGE, 0, -CURTAIN_RANGE), feet.offset(CURTAIN_RANGE, 2, CURTAIN_RANGE)))
            if (HCurtainEntity.isCurtain(level.getBlockState(q))) return true;
        return false;
    }

    private boolean safe(ServerLevel level, Player p) {
        if (LockerManager.isInLocker(p)) return true;
        return behindCurtain(level, p);
    }

    private void flash(ServerLevel level, Player p) {
        if (!(p instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(0, 8, 6));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("█").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        level.sendParticles(sp, ParticleTypes.END_ROD, true, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.5, 1.0, 1.5, 0.05);
        level.sendParticles(sp, new DustParticleOptions(new Vector3f(1f, 1f, 1f), 2.0f), true, p.getX(), p.getY() + 1.0, p.getZ(), 30, 1.2, 0.8, 1.2, 0.0);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.h175_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "H-175: レントゲンの予告", "{} warn room={} ticks={}", vName(), startRoom, warnTicks());
        }
        if (phase < warnTicks() && phase % 10 == 0)
            level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 1.2f, 1.0f + phase / (float) warnTicks());
        if (phase < warnTicks()) return;
        if (phase == warnTicks()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 2.5f, 1.4f);
            int hit = 0, spared = 0;
            for (Player p : playersNear(48)) {
                if (p.isSpectator() || !inRoom(level, p)) continue;
                flash(level, p);
                if (safe(level, p)) {
                    spared++;
                    RoomsDebug.log(p, "H-175: カーテンかロッカーで助かった", "{} flash spared player={} locker={} curtain={}",
                            vName(), p.getGameProfile().getName(), LockerManager.isInLocker(p), behindCurtain(level, p));
                    continue;
                }
                RoomsDebug.log(p, "H-175: 露光した", "{} flash hit player={} creative={}", vName(), p.getGameProfile().getName(), p.isCreative());
                RoomsDamage.hurt(this, p, DAMAGE);
                hit++;
            }
            leave("xray hits=" + hit + " spared=" + spared);
        }
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 1f), 1.2f);
        if (tickCount % 3 == 0)
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.5 + random.nextDouble() * 1.6, getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + "/" + warnTicks(); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
