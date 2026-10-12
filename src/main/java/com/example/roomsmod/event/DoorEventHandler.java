package com.example.roomsmod.event;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.A60Entity;
import com.example.roomsmod.world.RoomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

public class DoorEventHandler {
    public static int currentRoom = 0;
    public static final Set<BlockPos> generatedDoors = new HashSet<>();
    public static int baseFloorY = 1;

    public static void reset() {
        currentRoom = 0;
        generatedDoors.clear();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        // ワールドロード時に状態をリセット
        reset();
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof DoorBlock) {
            ServerLevel level = (ServerLevel) event.getLevel();
            BlockPos doorPos = event.getPos();

            if (generatedDoors.contains(doorPos)) {
                return;
            }

            currentRoom++;
            BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, baseFloorY, doorPos.getZ() + 1);
            BlockPos exitDoor = RoomGenerator.generateNextRoom(level, nextOrigin, currentRoom);
            generatedDoors.add(doorPos);
            event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal("次の部屋を生成したわ（A-" + String.format("%03d", currentRoom) + "）"));

            if (currentRoom >= 10 && level.random.nextFloat() < 0.6f) {
                // プレイヤーの約10部屋後ろで出現
                A60Entity a60 = RoomsMod.A60.get().create(level);
                if (a60 != null) {
                    double spawnX = 10.5;
                    double spawnY = 2.0;
                    double spawnZ = event.getEntity().getZ() - 300.0;
                    a60.setPos(spawnX, spawnY, spawnZ);

                    // 出現位置のチャンクを強制読み込み
                    int chunkX = (int)Math.floor(spawnX) >> 4;
                    int chunkZ = (int)Math.floor(spawnZ) >> 4;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            level.setChunkForced(chunkX + dx, chunkZ + dz, true);
                        }
                    }

                    level.addFreshEntity(a60);
                    event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "A-60が出現したわ！ (Z=" + String.format("%.0f", spawnZ) + ")"
                    ));
                }
            }
        }
    }

    public static void markDoorGenerated(BlockPos pos) {
        generatedDoors.add(pos);
    }

    public static void setCurrentRoom(int room) {
        currentRoom = room;
    }
}
