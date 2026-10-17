package com.example.roomsmod.entity.gk;

import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 部屋に一時的なブロック（ベル・スピーカー・カーペット）を置く GK エンティティの共通部分。退場時に空気へ戻す。phase は経過 tick。 */
public abstract class GKTimedEntity extends VEntity {
    protected int phase = 0;
    protected final List<BlockPos> placed = new ArrayList<>();
    private boolean restored = false;

    protected GKTimedEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }

    protected void place(ServerLevel l, BlockPos p, BlockState s) { l.setBlockAndUpdate(p, s); placed.add(p.immutable()); }

    private void restore() {
        if (restored || !(level() instanceof ServerLevel l)) return;
        restored = true;
        for (BlockPos p : placed) l.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        if (!placed.isEmpty()) RoomsDebug.log("{} removed {} placed block(s)", vName(), placed.size());
    }

    @Override protected void leave(String why) { restore(); super.leave(why); }
    @Override public void onRemovedFromLevel() {
        RemovalReason r = getRemovalReason();
        if (r != null && r.shouldDestroy()) restore();
        super.onRemovedFromLevel();
    }
    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " placed=" + placed.size(); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t); t.putInt("Phase", phase);
        ListTag l = new ListTag(); for (BlockPos p : placed) l.add(NbtUtils.writeBlockPos(p)); t.put("Placed", l);
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t); phase = t.getInt("Phase"); placed.clear();
        ListTag l = t.getList("Placed", Tag.TAG_INT_ARRAY);
        for (Tag x : l) if (x instanceof net.minecraft.nbt.IntArrayTag a && a.size() == 3) placed.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
    }
}
