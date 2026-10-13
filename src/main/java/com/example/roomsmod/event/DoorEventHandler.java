package com.example.roomsmod.event;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.entity.A60Entity;
import com.example.roomsmod.world.RoomGenerator;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * 出口扉の右クリックで次の部屋を生成する。
 * 状態は RoomsState（SavedData）に永続化。リセットはロビー生成（/rooms enter, /rooms to）時のみ。
 */
public class DoorEventHandler {
    public static final int baseFloorY = 1;
    /** A-60 が出現する、プレイヤーから何部屋後ろか。 */
    public static final int A60_ROOMS_BEHIND = 10;

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        // MAIN_HAND / OFF_HAND の2回発火を防ぐ
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;

        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof DoorBlock)) return;

        // 下半分に正規化
        BlockPos doorPos = event.getPos().immutable();
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) doorPos = doorPos.below();

        RoomsState rs = RoomsState.get(level);
        // 現在の部屋の出口扉以外（使用済みの扉など）は普通に開閉するだけ
        if (rs.expectedExitDoor == null || !rs.expectedExitDoor.equals(doorPos)) return;
        if (rs.generatedDoors.contains(doorPos)) return;

        rs.markDoorGenerated(doorPos);
        int room = rs.currentRoom + 1;
        BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, baseFloorY, doorPos.getZ() + 1);
        RoomGenerator.generateNextRoom(level, nextOrigin, room); // currentRoom / expectedExitDoor を更新
        event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal("次の部屋を生成したわ（A-" + String.format("%03d", room) + "）"));

        if (room >= 10 && level.random.nextFloat() < 0.6f) {
            spawnA60(level, rs, event.getEntity());
        }
    }

    private static void spawnA60(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return;
        // プレイヤーがいるのは直前の部屋（index = room - 1）。そこから約N部屋後ろ。
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A60_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) return;
        A60Entity a60 = RoomsMod.A60.get().create(level);
        if (a60 == null) return;
        BlockPos start = startPath.get(0);
        double spawnX = start.getX() + 0.5, spawnY = start.getY(), spawnZ = start.getZ() + 0.5;
        a60.setPos(spawnX, spawnY, spawnZ);
        a60.setStartRoom(startIndex);

        // 出現位置のチャンクを強制読み込み
        int chunkX = start.getX() >> 4;
        int chunkZ = start.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(chunkX + dx, chunkZ + dz, true);
            }
        }

        level.addFreshEntity(a60);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "A-60が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", spawnX) + ", Z=" + String.format("%.0f", spawnZ) + ")"
        ));
    }
}
