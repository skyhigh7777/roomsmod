package com.example.roomsmod.entity.u;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * U-80（非常灯）。開けた廊下で赤い灯が点滅する。点いているあいだ、通路の真ん中にいる（壁際でも隠れてもいない）と被弾。
 * 壁に寄るか、ロッカー／テーブルに隠れる。
 */
public class UFlareEntity extends VEntity {
    public static final int TELEGRAPH = 40, FLASHES = 4, FLASH_EVERY = 35, FLASH_LEN = 10;
    public static final float DAMAGE = 20f;
    public static final double WALL_DIST = 1.15;
    private int phase = 0;

    public UFlareEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-80"; }
    @Override public String homeSection() { return "u"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }

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

    /** 部屋の壁（箱の外周）に寄り添っているか。 */
    static boolean huggingWall(RoomsState rs, int room, Player p) {
        for (BoundingBox b : rs.boxes(room)) {
            if (p.getX() < b.minX() - 1 || p.getX() > b.maxX() + 1 || p.getZ() < b.minZ() - 1 || p.getZ() > b.maxZ() + 1) continue;
            double dx = Math.min(p.getX() - (b.minX() + 1), (b.maxX() - 1) - p.getX());
            double dz = Math.min(p.getZ() - (b.minZ() + 1), (b.maxZ() - 1) - p.getZ());
            if (Math.min(dx, dz) <= WALL_DIST) return true;
        }
        // 箱が取れないとき：隣が個体のブロック
        BlockPos feet = p.blockPosition();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos w = feet.relative(d);
            if (p.level().getBlockState(w).isSolidRender(p.level(), w)) return true;
        }
        return false;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.8f, 0.6f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.u80_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "U-80: 非常灯の予告", "{} telegraph room={}", vName(), startRoom);
        }
        if (phase < TELEGRAPH) return;
        int t = phase - TELEGRAPH;
        int flash = t / FLASH_EVERY;
        int into = t % FLASH_EVERY;
        if (flash >= FLASHES && into >= FLASH_LEN) { leave("flashes done"); return; }
        if (flash >= FLASHES) return;
        if (into == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            DustParticleOptions red = new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.1f), 2.0f);
            level.sendParticles(red, getX(), getY() + 1.5, getZ(), 20, 1.4, 0.8, 1.4, 0);
            for (Player p : playersNear(48)) if (p instanceof ServerPlayer sp && inRoom(level, p)) {
                sp.connection.send(new ClientboundSetTitlesAnimationPacket(0, FLASH_LEN, 4));
                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("█").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            }
        }
        if (into == 1 || into == FLASH_LEN / 2) {
            RoomsState rs = RoomsState.get(level);
            for (Player p : playersNear(48)) {
                if (p.isSpectator() || !inRoom(level, p) || HidingRules.isHiding(p) || huggingWall(rs, startRoom, p)) continue;
                RoomsDebug.log(p, "U-80: 通路の真ん中で被弾", "{} flash hit player={}", vName(), p.getGameProfile().getName());
                RoomsDamage.hurt(this, p, DAMAGE);
            }
        }
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.85f, 0.15f, 0.15f), 1.1f);
        if (tickCount % 5 == 0) level().addParticle(d, getX(), getY() + 1.8, getZ(), 0, 0, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
