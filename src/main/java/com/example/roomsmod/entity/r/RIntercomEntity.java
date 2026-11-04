package com.example.roomsmod.entity.r;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * R-5（インターホン）。開けた部屋でインターホンが鳴り（RING_TICKS の間に 2 回）、出口ではない壁際の扉（ResidenceRooms のクローゼット）が少し開いて、
 * 向こうに誰かが立つ。そこから HIDE_TICKS（ハード HARD_HIDE_TICKS）以内に<b>ロッカー</b>に隠れていないプレイヤーがその部屋（か1つ前の部屋、HEAR_RANGE 以内）にいたら、
 * 「誰かが入ってきた」：次の出現抽選の確率が FAIL_CHANCE_MUL 倍（RoomsState.nextRollChanceMul、1回だけ）。テーブルの下は数えない（見つかる）。
 * 鳴っている扉は開け閉めできない（GKDoorTricks の扉フック）。終わると扉を閉めて消える。ダメージはない。
 */
public class RIntercomEntity extends VEntity {
    public static final int RING_TICKS = 30, HIDE_TICKS = 120, HARD_HIDE_TICKS = 85;
    public static final float FAIL_CHANCE_MUL = 2.0f;
    public static final double HEAR_RANGE = 16;
    private int phase = 0;
    private BlockPos door; // 扉の下半分（null = 扉がない部屋：その場に立つだけ）

    public RIntercomEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-5"; }
    @Override public String homeSection() { return "r"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int hideTicks() { return hardSpawn ? HARD_HIDE_TICKS : HIDE_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        door = findDoor(level, rs, playerRoom);
        if (door != null) {
            setPos(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
            return door;
        }
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    /** 部屋の中の閉じた扉（出口・生成済みの扉を除く）。 */
    static BlockPos findDoor(ServerLevel level, RoomsState rs, int room) {
        if (room < 0 || room >= rs.generatedRooms.size()) return null;
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        for (BoundingBox bb : rs.generatedRooms.get(room))
            for (BlockPos p : BlockPos.betweenClosed(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ())) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || s.getValue(DoorBlock.OPEN)) continue;
                if (p.equals(rs.expectedExitDoor) || rs.generatedDoors.contains(p)) continue;
                if (bb.maxZ() - p.getZ() <= 1 || p.getZ() - bb.minZ() <= 0) continue; // 入口・出口の壁の扉は使わない
                found.add(p.immutable());
            }
        return found.isEmpty() ? null : found.get(level.random.nextInt(found.size()));
    }

    /** この扉（上下どちらの半分でも）を鳴らしているか。クライアントでも位置から判定する。 */
    public boolean guards(BlockPos pos) {
        if (!isAlive()) return false;
        BlockPos at = BlockPos.containing(getX(), getY() + 0.1, getZ());
        return pos.equals(at) || pos.equals(at.above());
    }

    /** 鳴っている扉を触った。 */
    public void knock(ServerLevel level, Player p) {
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.HOSTILE, 1.0f, 0.5f);
        GKUtil.actionbar(java.util.List.of(p), Component.translatable("message.roomsmod.r5_knock"));
    }

    private void setDoor(ServerLevel level, boolean open) {
        if (door == null) return;
        BlockState s = level.getBlockState(door);
        if (!(s.getBlock() instanceof DoorBlock db) || s.getValue(DoorBlock.OPEN) == open) return;
        db.setOpen(null, level, s, door, open);
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < HEAR_RANGE;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1 || phase == 16) level.playSound(null, getX(), getY() + 1.5, getZ(), RoomsMod.R5_INTERCOM.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        if (phase == 1) for (Player p : playersNear(48)) RoomsDebug.log(p, "R-5: インターホンが鳴った", "{} ring room={} door={}", vName(), startRoom, door);
        if (phase == RING_TICKS) {
            setDoor(level, true);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.r5_ring"));
        }
        if (phase < RING_TICKS + hideTicks()) return;
        // 判定：ロッカーに隠れていない人がいたら次の抽選を強く
        boolean fail = false;
        for (Player p : playersNear(48)) {
            if (p.isSpectator() || !inRoom(level, p)) continue;
            if (!LockerManager.isInLocker(p)) { fail = true; RoomsDebug.log(p, "R-5: ロッカーに隠れていなかった", "{} fail player={}", vName(), p.getGameProfile().getName()); }
        }
        RoomsState rs = RoomsState.get(level);
        if (fail) {
            rs.nextRollChanceMul = Math.max(rs.nextRollChanceMul, FAIL_CHANCE_MUL);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.HOSTILE, 2.0f, 0.6f);
            GKUtil.actionbar(playersNear(48), Component.translatable("message.roomsmod.r5_fail"));
        } else {
            GKUtil.actionbar(playersNear(48), Component.translatable("message.roomsmod.r5_safe"));
        }
        RoomsDebug.log("{} result fail={} nextRollChanceMul={}", vName(), fail, rs.nextRollChanceMul);
        setDoor(level, false);
        leave(fail ? "someone came in" : "went away");
    }

    @Override protected void clientTick() {
        if (tickCount % 10 == 0) level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.0 + random.nextDouble(), getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + "/" + (RING_TICKS + hideTicks()) + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); door = NbtUtils.readBlockPos(t, "Door").orElse(null); }
}
