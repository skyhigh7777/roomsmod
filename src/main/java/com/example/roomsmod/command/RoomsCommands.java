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
                        BlockPos origin = new BlockPos(0, 1, 0);
                        com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, origin);

                        BlockPos currentExit = origin.offset(10, 1, 31);
                        com.example.roomsmod.event.DoorEventHandler.markDoorGenerated(currentExit);

                        for (int i = 1; i <= target; i++) {
                            BlockPos nextOrigin = new BlockPos(currentExit.getX() - 10, 1, currentExit.getZ() + 1);
                            com.example.roomsmod.world.RoomGenerator.generateNextRoom(roomsLevel, nextOrigin, i);
                            int depth = 28 + ((i % 5) * 2);
                            currentExit = nextOrigin.offset(10, 1, depth - 1);
                            // 最後の部屋の出口扉はマークしない（玩者が開けられるように）
                            if (i < target) {
                                com.example.roomsmod.event.DoorEventHandler.markDoorGenerated(currentExit);
                            }
                        }

                        com.example.roomsmod.event.DoorEventHandler.setCurrentRoom(target);

                        int lastDepth = 28 + ((target % 5) * 2);
                        BlockPos lastOrigin = new BlockPos(currentExit.getX() - 10, 1, currentExit.getZ() - lastDepth + 1);
                        player.teleportTo(roomsLevel, lastOrigin.getX() + 10.5, 2, lastOrigin.getZ() + lastDepth / 2.0, 0, 0);
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
