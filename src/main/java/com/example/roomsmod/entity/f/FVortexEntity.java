package com.example.roomsmod.entity.f;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * F-230（渦）。開けた部屋の通路に渦ができ（予告のあと ACTIVE_TICKS）、半径内にいてロッカーに入っていないプレイヤーを
 * 1 部屋前へ引き戻す（小さなダメージ）。テーブルの下では守れない。S-145 の転送を水の位置ギミックにしたもの。
 */
public class FVortexEntity extends VEntity {
    public static final int TELEGRAPH = 40, HARD_TELEGRAPH = 28, ACTIVE_TICKS = 80;
    public static final double RADIUS = 2.2;
    public static final float DAMAGE = 10f;
    private int phase = 0;
    private boolean pulled = false;

    public FVortexEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-230"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int telegraph() { return hardSpawn ? HARD_TELEGRAPH : TELEGRAPH; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        // 通路の真ん中（経路上）に出す
        var path = rs.path(playerRoom);
        if (path.size() >= 3) {
            BlockPos mid = path.get(path.size() / 2);
            setPos(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
            return mid;
        }
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.HOSTILE, 1.8f, 0.7f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.f230_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "F-230: 渦ができた", "{} telegraph room={}", vName(), startRoom);
        }
        if (phase < telegraph()) {
            if (phase % 5 == 0) level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 6, 0.6, 0.1, 0.6, 0.02);
            return;
        }
        if (phase % 4 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.2, getZ(), 10, 0.8, 0.2, 0.8, 0.04);
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 6, 0.9, 0.05, 0.9, 0.02);
        }
        if (!pulled) {
            RoomsState rs = RoomsState.get(level);
            int back = Math.max(1, startRoom - 1);
            Vec3 to = SafeSpot.find(level, rs, back);
            for (Player p : playersNear(RADIUS + 2)) {
                if (p.isSpectator() || LockerManager.isInLocker(p)) continue;
                double dx = p.getX() - getX(), dz = p.getZ() - getZ();
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                pulled = true;
                RoomsDamage.hurt(this, p, DAMAGE);
                RoomsDebug.log(p, "F-230: 渦に巻き込まれた", "{} pull player={} to room {}", vName(), p.getGameProfile().getName(), back);
                if (to != null) p.teleportTo(to.x, to.y, to.z);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 1.5f, 0.8f);
            }
        }
        if (phase >= telegraph() + ACTIVE_TICKS) leave(pulled ? "pulled" : "missed");
    }

    @Override protected void clientTick() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.08, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " pulled=" + pulled; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); t.putBoolean("Pulled", pulled); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); pulled = t.getBoolean("Pulled"); }
}
