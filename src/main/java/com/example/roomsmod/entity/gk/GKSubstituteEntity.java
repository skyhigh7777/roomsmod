package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** S-105：偽の扉。壁の元のブロックを保存して戻すので、どのセクションの部屋でも使える。 */
public class GKSubstituteEntity extends VEntity {
    public static final int LIFE = 20 * 60;
    public static final float DAMAGE = 10f;
    private BlockPos door = null;
    private boolean restored = false;
    /** 扉を置く前の壁ブロック（下・上）。どのセクションの部屋でも元に戻せるように保存する。 */
    private BlockState wallLower = null, wallUpper = null;
    public GKSubstituteEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-105"; }
    @Override protected int maxLifeTicks() { return Math.min(LIFE, GKEntity.MAX_LIFE_TICKS); }

    @Override public boolean targetsOpenedRoom() { return true; }
    // 姿なし：描画は NoopRenderer。不可視にしておくと F3+B の当たり判定枠も出ない。照準・名札にも出さない
    @Override public boolean isInvisible() { return true; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldShowName() { return false; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        if (playerRoom < 1 || playerRoom >= rs.generatedRooms.size()) return null;
        List<BlockPos> path = rs.roomPaths.get(playerRoom);
        int y = path.get(0).getY();
        BlockPos entrance = path.get(0), exit = path.get(path.size() - 1);
        List<Object[]> cand = new ArrayList<>();
        for (BoundingBox b : rs.generatedRooms.get(playerRoom))
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? b.minX() : b.maxX();
                Direction in = side == 0 ? Direction.EAST : Direction.WEST;
                for (int z = b.minZ() + 2; z <= b.maxZ() - 2; z++) {
                    BlockPos w = new BlockPos(x, y, z), inside = w.relative(in);
                    if (w.distManhattan(entrance) < 4 || w.distManhattan(exit) < 4) continue;
                    if (nearDoor(level, w)) continue; // 本物の扉（他の部屋の入口・出口を含む）の近くには作らない
                    if (!level.getBlockState(w).isSolid() || !level.getBlockState(w.above()).isSolid()) continue;
                    if (!level.getBlockState(inside).isAir() || !level.getBlockState(inside.above()).isAir()) continue;
                    if (!level.getBlockState(w.north()).isSolid() || !level.getBlockState(w.south()).isSolid()) continue;
                    cand.add(new Object[]{w, in});
                }
            }
        if (cand.isEmpty()) { RoomsDebug.log("S-105 spawn skipped: no wall spot in room {}", playerRoom); return null; }
        Object[] c = cand.get(level.random.nextInt(cand.size()));
        door = (BlockPos) c[0];
        Direction in = (Direction) c[1];
        wallLower = level.getBlockState(door); wallUpper = level.getBlockState(door.above());
        BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, in.getOpposite());
        level.setBlock(door, lower, 3);
        level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos at = door.relative(in);
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.playSound(null, door, RoomsMod.GK_SUBSTITUTE_CREAK.get(), SoundSource.HOSTILE, 2.0f, 0.8f);
        RoomsDebug.log("S-105 fake door at {} facing {}", door, in.getOpposite());
        return at;
    }

    private static boolean nearDoor(ServerLevel level, BlockPos w) {
        for (BlockPos q : BlockPos.betweenClosed(w.offset(-2, -1, -2), w.offset(2, 2, 2)))
            if (level.getBlockState(q).getBlock() instanceof DoorBlock) return true;
        return false;
    }

    public boolean isFake(BlockPos p) { return door != null && !restored && door.equals(p); }

    public void trigger(ServerLevel level, Player p) {
        level.playSound(null, door, RoomsMod.GK_SUBSTITUTE_CREAK.get(), SoundSource.HOSTILE, 2.5f, 0.5f);
        RoomsDebug.log(p, "S-105: 偽の扉だったわ", "S-105 fake door opened {} player={} creative={}", door, p == null ? "?" : p.getGameProfile().getName(), p != null && p.isCreative());
        if (p != null) {
            com.example.roomsmod.util.RoomsDamage.hurt(this, p, DAMAGE);
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
        }
        leave("fake door used");
    }

    private void restore() {
        if (restored || door == null || !(level() instanceof ServerLevel sl)) return;
        restored = true;
        RoomsState rs = RoomsState.get(sl);
        // 自分の偽の扉（閉じた暗いオークの扉）だけを壁に戻す。本物の扉（出口・生成済み）は絶対に上書きしない
        var st = sl.getBlockState(door);
        boolean real = rs.generatedDoors.contains(door) || door.equals(rs.expectedExitDoor) || (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.OPEN));
        if (real) RoomsDebug.warn("S-105 restore skipped: {} is a real/open door", door);
        else if (st.is(Blocks.DARK_OAK_DOOR)) {
            sl.setBlock(door.above(), wallUpper != null ? wallUpper : Blocks.WHITE_TERRACOTTA.defaultBlockState(), 2 | 16);
            sl.setBlock(door, wallLower != null ? wallLower : Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(), 2 | 16);
            sl.sendBlockUpdated(door, sl.getBlockState(door), sl.getBlockState(door), 3);
            RoomsDebug.log("S-105 fake door removed at {}", door);
        }
    }

    @Override protected void leave(String why) { restore(); super.leave(why); }

    @Override
    protected void serverTick(ServerLevel level) {
        if (life % 60 == 1 && door != null) level.playSound(null, door, RoomsMod.GK_SUBSTITUTE_CREAK.get(), SoundSource.HOSTILE, 0.8f, 1.2f);
    }

    @Override
    public void onRemovedFromLevel() {
        RemovalReason r = getRemovalReason();
        if (r != null && r.shouldDestroy()) restore();
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {
        // かすかな煙：体の形の柱にならないよう、扉の足元〜腰あたりにまばらに散らす
        if (tickCount % 12 == 0) {
            var r = level().random;
            level().addParticle(ParticleTypes.SMOKE, getX() + (r.nextDouble() - 0.5) * 1.4, getY() + 0.1 + r.nextDouble() * 1.0,
                    getZ() + (r.nextDouble() - 0.5) * 1.4, (r.nextDouble() - 0.5) * 0.01, 0.005, (r.nextDouble() - 0.5) * 0.01);
        }
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); t.putBoolean("Restored", restored);
        if (wallLower != null) t.put("WallLower", NbtUtils.writeBlockState(wallLower)); if (wallUpper != null) t.put("WallUpper", NbtUtils.writeBlockState(wallUpper)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); restored = t.getBoolean("Restored");
        var lk = level().holderLookup(net.minecraft.core.registries.Registries.BLOCK);
        if (t.contains("WallLower")) wallLower = NbtUtils.readBlockState(lk, t.getCompound("WallLower"));
        if (t.contains("WallUpper")) wallUpper = NbtUtils.readBlockState(lk, t.getCompound("WallUpper")); }
}
