package com.example.roomsmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class RoomsCommands {
    public static final ResourceKey<Level> ROOMS_DIMENSION = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("roomsmod", "rooms"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rooms")
            .then(Commands.literal("enter")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
                    if (roomsLevel != null) {
                        com.example.roomsmod.event.DoorEventHandler.reset();
                        player.teleportTo(roomsLevel, 10.5, 2, 16.5, 0, 0);
                        com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, new BlockPos(0, 1, 0));
                        player.sendSystemMessage(Component.literal("Roomsディメンションへ移動したわ"));
                    } else {
                        player.sendSystemMessage(Component.literal("ディメンションが見つからないわ"));
                    }
                    return 1;
                }))
            .then(Commands.literal("to")
                .then(Commands.argument("room", IntegerArgumentType.integer(1, 200))
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
                        if (roomsLevel == null) {
                            player.sendSystemMessage(Component.literal("ディメンションが見つからないわ"));
                            return 0;
                        }

                        int target = IntegerArgumentType.getInteger(ctx, "room");
                        com.example.roomsmod.event.DoorEventHandler.reset();
                        BlockPos origin = new BlockPos(0, 1, 0);
                        BlockPos lobbyExit = com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, origin);
                        if (lobbyExit != null) {
                            com.example.roomsmod.event.DoorEventHandler.markDoorGenerated(lobbyExit);
                        }

                        BlockPos currentExit = lobbyExit != null ? lobbyExit : origin.offset(10, 1, 31);
                        for (int i = 1; i <= target; i++) {
                            BlockPos nextOrigin = new BlockPos(currentExit.getX() - 10, 1, currentExit.getZ() + 1);
                            BlockPos exitDoor = com.example.roomsmod.world.RoomGenerator.generateNextRoom(roomsLevel, nextOrigin, i);
                            if (exitDoor != null) {
                                currentExit = exitDoor;
                                if (i < target) {
                                    com.example.roomsmod.event.DoorEventHandler.markDoorGenerated(exitDoor);
                                }
                            }
                        }

                        com.example.roomsmod.event.DoorEventHandler.setCurrentRoom(target);

                        // 最後の部屋の中央付近にワープ（出口扉の手前）
                        player.teleportTo(roomsLevel, currentExit.getX() + 0.5, 2, currentExit.getZ() - 8, 0, 0);
                        player.sendSystemMessage(Component.literal("A-" + String.format("%03d", target) + " まで生成してワープしたわ"));
                        return 1;
                    })))
            .then(Commands.literal("deepest")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("最深部屋へワープ（実装中）"));
                    return 1;
                }))
        );
    }
}
