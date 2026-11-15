package com.example.roomsmod.entity.f;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * F-150（満潮）。開けた部屋の床上（経路の高さ）に一時的に水を張り、胸までの水位にする。溺れは既存どおり防がれる。
 * 予告のあと HIGH_TICKS の間だけ。片付けは必ず行う（退場・除去・保存）。F-200 とは同時に出ない（f_tide）。
 */
public class FTideEntity extends VEntity {
    public static final int TELEGRAPH = 40, HARD_TELEGRAPH = 28, HIGH_TICKS = 100, HARD_HIGH_TICKS = 80;
    private int phase = 0;
    private final List<BlockPos> placed = new ArrayList<>();
    private boolean high = false;

    public FTideEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-150"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int telegraph() { return hardSpawn ? HARD_TELEGRAPH : TELEGRAPH; }
    private int highTicks() { return hardSpawn ? HARD_HIGH_TICKS : HIGH_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    private void rise(ServerLevel level, RoomsState rs) {
        if (startRoom < 0 || startRoom >= rs.generatedRooms.size() || rs.path(startRoom).isEmpty()) return;
        int y = rs.path(startRoom).get(0).getY(); // 経路＝床+1
        for (BoundingBox b : rs.boxes(startRoom))
            for (BlockPos q : BlockPos.betweenClosed(b.minX() + 1, y, b.minZ() + 1, b.maxX() - 1, y, b.maxZ() - 1)) {
                if (!level.getBlockState(q).isAir()) continue;
                if (level.getBlockState(q.below()).getBlock() instanceof DoorBlock) continue;
                if (level.getBlockState(q.north()).getBlock() instanceof DoorBlock
                        || level.getBlockState(q.south()).getBlock() instanceof DoorBlock
                        || level.getBlockState(q.east()).getBlock() instanceof DoorBlock
                        || level.getBlockState(q.west()).getBlock() instanceof DoorBlock) continue;
                // 近傍更新を抑えて隣室へ流れにくくする（短時間）
                level.setBlock(q, Blocks.WATER.defaultBlockState(), 2);
                placed.add(q.immutable());
            }
        high = true;
        RoomsDebug.log("{} water placed {} cells room={} y={}", vName(), placed.size(), startRoom, y);
    }

    private void drain(ServerLevel level) {
        for (BlockPos q : placed) {
            if (level.getBlockState(q).getBlock() instanceof LiquidBlock)
                level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
        }
        if (!placed.isEmpty()) RoomsDebug.log("{} water drained cells={}", vName(), placed.size());
        placed.clear();
        high = false;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6f, 0.6f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.f150_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "F-150: 水位が上がる", "{} telegraph room={}", vName(), startRoom);
        }
        if (phase == telegraph() && !high) {
            rise(level, RoomsState.get(level));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SWIM, SoundSource.HOSTILE, 1.8f, 0.5f);
        }
        if (high && phase < telegraph() + highTicks() && phase % 8 == 0)
            level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.6, getZ(), 8, 1.5, 0.4, 1.5, 0.02);
        if (high && phase >= telegraph() + highTicks()) {
            drain(level);
            leave("tide out");
        }
    }

    @Override protected void leave(String why) {
        if (level() instanceof ServerLevel sl) drain(sl);
        super.leave(why);
    }

    @Override public void onRemovedFromLevel() {
        if (level() instanceof ServerLevel sl) drain(sl);
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + 0.4, getZ() + (random.nextDouble() - 0.5) * 1.5, 0, 0.05, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " high=" + high + " cells=" + placed.size(); }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase);
        t.putBoolean("High", high);
        ListTag list = new ListTag();
        for (BlockPos q : placed) {
            CompoundTag n = new CompoundTag();
            n.putInt("x", q.getX()); n.putInt("y", q.getY()); n.putInt("z", q.getZ());
            list.add(n);
        }
        t.put("Placed", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase");
        high = t.getBoolean("High");
        placed.clear();
        for (var n0 : t.getList("Placed", 10)) {
            CompoundTag n = (CompoundTag) n0;
            placed.add(new BlockPos(n.getInt("x"), n.getInt("y"), n.getInt("z")));
        }
    }
}
