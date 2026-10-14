package com.example.roomsmod.block;

import com.example.roomsmod.hiding.LockerManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1マスの机。天板（y13〜16）と四隅の脚だけで、下に高さ13pxの隙間がある（這い姿勢0.6ブロックが入る）。
 * 右クリックで下に潜る／もう一度右クリックかスニークで出る（LockerManager）。
 */
public class HidingTableBlock extends Block {
    public static final MapCodec<HidingTableBlock> CODEC = simpleCodec(HidingTableBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 13, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 13, 2),
            Block.box(14, 0, 0, 16, 13, 2),
            Block.box(0, 0, 14, 2, 13, 16),
            Block.box(14, 0, 14, 16, 13, 16));

    public HidingTableBlock(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) LockerManager.toggleTable(sp, pos);
        return InteractionResult.CONSUME;
    }
}
