package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** GK-Detention：出口扉の前に立つ。 */
public class GKDetentionEntity extends VEntity {
    public static final int HOLD_TICKS = 20 * 12;
    public static final float DAMAGE = 4f;
    private BlockPos door = null;
    private int lastPunish = -100;
    public GKDetentionEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Detention"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> path = playerRoom < rs.roomPaths.size() ? rs.roomPaths.get(playerRoom) : List.of();
        if (path.size() < 2) return null;
        door = path.get(path.size() - 1);
        if (rs.generatedDoors.contains(door)) { RoomsDebug.log("GK-Detention spawn skipped: room {} exit {} already used", playerRoom, door); return null; }
        BlockPos s = door.north();
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        level.playSound(null, s, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        return s;
    }

    public boolean guards(BlockPos p) { return door != null && door.equals(p); }

    public void punish(ServerLevel level, Player p) {
        level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
        boolean hurt = life - lastPunish >= 20;
        RoomsDebug.log(p, "GK-Detention: 扉は施錠中（残り " + Math.max(0, HOLD_TICKS - life) / 20 + "秒）", "GK-Detention blocked door {} player={} hurt={} life={}", door, p == null ? "?" : p.getGameProfile().getName(), hurt, life);
        if (hurt && p != null && !p.isCreative()) { lastPunish = life; p.hurt(damageSources().mobAttack(this), DAMAGE); }
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (life % 40 == 1 && door != null) level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        if (life >= HOLD_TICKS) leave("detention over");
    }

    @Override protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.CRIT, getX(), getY() + 1 + random.nextDouble(), getZ(), 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); }
}
