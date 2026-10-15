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

/** GK-Substitute：偽の扉。 */
public class GKSubstituteEntity extends VEntity {
    public static final int LIFE = 20 * 60;
    public static final float DAMAGE = 6f;
    private BlockPos door = null;
    private boolean restored = false;
    public GKSubstituteEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Substitute"; }
    @Override protected int maxLifeTicks() { return Math.min(LIFE, GKEntity.MAX_LIFE_TICKS); }

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
                    if (!level.getBlockState(w).isSolid() || !level.getBlockState(w.above()).isSolid()) continue;
                    if (!level.getBlockState(inside).isAir() || !level.getBlockState(inside.above()).isAir()) continue;
                    if (!level.getBlockState(w.north()).isSolid() || !level.getBlockState(w.south()).isSolid()) continue;
                    cand.add(new Object[]{w, in});
                }
            }
        if (cand.isEmpty()) { RoomsDebug.log("GK-Substitute spawn skipped: no wall spot in room {}", playerRoom); return null; }
        Object[] c = cand.get(level.random.nextInt(cand.size()));
        door = (BlockPos) c[0];
        Direction in = (Direction) c[1];
        BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, in.getOpposite());
        level.setBlock(door, lower, 3);
        level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos at = door.relative(in);
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.playSound(null, door, RoomsMod.GK_SUBSTITUTE_CREAK.get(), SoundSource.HOSTILE, 2.0f, 0.8f);
        RoomsDebug.log("GK-Substitute fake door at {} facing {}", door, in.getOpposite());
        return at;
    }

    public boolean isFake(BlockPos p) { return door != null && !restored && door.equals(p); }

    public void trigger(ServerLevel level, Player p) {
        level.playSound(null, door, RoomsMod.GK_SUBSTITUTE_CREAK.get(), SoundSource.HOSTILE, 2.5f, 0.5f);
        RoomsDebug.log(p, "GK-Substitute: 偽の扉だったわ", "GK-Substitute fake door opened {} player={} creative={}", door, p == null ? "?" : p.getGameProfile().getName(), p != null && p.isCreative());
        if (p != null && !p.isCreative()) {
            p.hurt(damageSources().mobAttack(this), DAMAGE);
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
        }
        leave("fake door used");
    }

    private void restore() {
        if (restored || door == null || !(level() instanceof ServerLevel sl)) return;
        restored = true;
        if (sl.getBlockState(door).getBlock() instanceof DoorBlock) {
            sl.setBlock(door.above(), Blocks.WHITE_TERRACOTTA.defaultBlockState(), 2 | 16);
            sl.setBlock(door, Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(), 2 | 16);
            sl.sendBlockUpdated(door, sl.getBlockState(door), sl.getBlockState(door), 3);
            RoomsDebug.log("GK-Substitute fake door removed at {}", door);
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
        if (tickCount % 5 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 0, 0.02, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); t.putBoolean("Restored", restored); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); restored = t.getBoolean("Restored"); }
}
