package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.block.LockerBlock;
import com.example.roomsmod.util.RoomsDamage;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** S-165：ロッカーの1つに潜む。そのロッカーはガタガタ鳴る。入ると捕まる。 */
public class GKMimicEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int LIFE = 20 * 60;
    public static final float DAMAGE = 40f;
    private BlockPos locker = null;
    public GKMimicEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-165"; }
    @Override protected int maxLifeTicks() { return LIFE; }

    static List<BlockPos> lockers(ServerLevel l, RoomsState rs, int room) {
        List<BlockPos> out = new ArrayList<>();
        if (room < 0 || room >= rs.generatedRooms.size()) return out;
        for (BoundingBox b : rs.generatedRooms.get(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                BlockState s = l.getBlockState(q);
                if (s.getBlock() instanceof LockerBlock && s.getValue(LockerBlock.HALF) == DoubleBlockHalf.LOWER) out.add(q.immutable());
            }
        return out;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        // 次の部屋（最新）を優先：プレイヤーがこれから隠れる場所
        int room = rs.roomPaths.size() - 1;
        List<BlockPos> ls = lockers(level, rs, room);
        if (ls.isEmpty()) { room = playerRoom; ls = lockers(level, rs, room); }
        if (ls.isEmpty()) return null;
        startRoom = room;
        locker = ls.get(level.random.nextInt(ls.size()));
        setPos(locker.getX() + 0.5, locker.getY(), locker.getZ() + 0.5);
        RoomsDebug.log("S-165 hides in locker {} room={} (of {})", locker, room, ls.size());
        return locker;
    }

    public boolean owns(BlockPos base) { return locker != null && locker.equals(base); }

    public void caught(ServerLevel level, Player p) {
        level.playSound(null, locker, RoomsMod.GK_ROLLCALL_CHECK.get(), SoundSource.HOSTILE, 3.0f, 0.5f);
        RoomsDebug.log(p, "S-165: ロッカーの中にいたわ", "S-165 caught player={} locker={} creative={}", p.getGameProfile().getName(), locker, p.isCreative());
        RoomsDamage.hurt(this, p, DAMAGE);
        leave("caught");
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (locker == null || !(level.getBlockState(locker).getBlock() instanceof LockerBlock)) { leave("locker gone"); return; }
        if (life % 30 == 1) level.playSound(null, locker, RoomsMod.GK_ROLLCALL_STEP.get(), SoundSource.HOSTILE, 1.4f, 0.6f + random.nextFloat() * 0.3f);
    }

    @Override public boolean isInvisible() { return true; }
    @Override protected void clientTick() {
        if (tickCount % 6 == 0) level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 2.1, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " locker=" + locker; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (locker != null) t.put("Locker", NbtUtils.writeBlockPos(locker)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); locker = NbtUtils.readBlockPos(t, "Locker").orElse(null); }
}
