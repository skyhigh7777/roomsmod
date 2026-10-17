package com.example.roomsmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 懐中電灯の光（見えない・当たり判定なし・選択不可・置き換え可能な光源ブロック）。
 * 当たり判定も視線用の形も空なので、隠れ場所や視線判定に影響しない。
 * 持ち主の懐中電灯が管理していない（取り残された）ものは定期チェックで自分を消す。
 */
public class FlashlightLightBlock extends Block {
    public static final int CHECK_TICKS = 20;
    public FlashlightLightBlock(Properties p) { super(p); }

    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override protected VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override protected boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }
    @Override protected float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0f; }

    @Override
    protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (com.example.roomsmod.item.FlashlightHandler.owns(level, pos)) level.scheduleTick(pos, this, CHECK_TICKS);
        else level.removeBlock(pos, false);
    }
}
