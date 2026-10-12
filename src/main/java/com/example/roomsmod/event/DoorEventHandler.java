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

import java.util.HashSet;
import java.util.Set;

public class DoorEventHandler {
    public static int currentRoom = 0;
    public static final Set<BlockPos> generatedDoors = new HashSet<>();
    public static int baseFloorY = 1;
    public static int lastGeneratedZ = 0; // 最後に生成した部屋の出口Z座標

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof DoorBlock) {
            ServerLevel level = (ServerLevel) event.getLevel();
            BlockPos doorPos = event.getPos();

            // 既に生成済みの扉は無視
            if (generatedDoors.contains(doorPos)) {
                return;
            }

            // 今の進捗より後ろの扉（Zが小さい）は無視する
            // ロビー出口はZ=31、部屋が進むほどZが増える
            if (doorPos.getZ() < lastGeneratedZ - 2) {
                event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal("これは前の部屋の扉よ。無視したわ。"));
                return;
            }

            currentRoom++;
            BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, baseFloorY, doorPos.getZ() + 1);
            RoomGenerator.generateNextRoom(level, nextOrigin, currentRoom);
            generatedDoors.add(doorPos);
            lastGeneratedZ = doorPos.getZ();
            event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal("次の部屋を生成したわ（A-" + String.format("%03d", currentRoom) + "）"));

            if (currentRoom >= 20 && level.random.nextFloat() < 0.4f) {
                // ロビーの中央で出現
                double spawnX = 10.5;
                double spawnY = 2.0;
                double spawnZ = 16.0;

                A60Entity a60 = RoomsMod.A60.get().create(level);
                if (a60 != null) {
                    a60.setPos(spawnX, spawnY, spawnZ);
                    level.addFreshEntity(a60);
                    event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal("A-60が出現したわ！"));
                }
            }
        }
    }

    public static void markDoorGenerated(BlockPos pos) {
        generatedDoors.add(pos);
        if (pos.getZ() > lastGeneratedZ) {
            lastGeneratedZ = pos.getZ();
        }
    }

    public static void setCurrentRoom(int room) {
        currentRoom = room;
    }

    public static void setLastGeneratedZ(int z) {
        lastGeneratedZ = z;
    }
}
