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

    private static int setDebug(CommandSourceStack src, boolean on) {
        ServerLevel roomsLevel = src.getServer().getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) {
            src.sendFailure(Component.literal("ディメンションが見つからないわ"));
            return 0;
        }
        com.example.roomsmod.world.RoomsState.get(roomsLevel).setDebug(on);
        com.example.roomsmod.util.RoomsDebug.LOGGER.info("[RoomsDebug] debug {} by {}", on ? "ON" : "OFF", src.getTextName());
        src.sendSuccess(() -> Component.literal("Roomsデバッグを" + (on ? "ON" : "OFF") + "にしたわ"), true);
        return 1;
    }

    private static int spawnRusher(CommandSourceStack src, boolean a75) {
        ServerLevel roomsLevel = src.getServer().getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(roomsLevel);
        if (rs.roomPaths.isEmpty()) { src.sendFailure(Component.literal("部屋がまだ生成されていないわ")); return 0; }
        boolean sameAlive = a75 ? com.example.roomsmod.event.DoorEventHandler.isA75Alive(roomsLevel)
                                : com.example.roomsmod.event.DoorEventHandler.isA60Alive(roomsLevel);
        if (sameAlive) {
            src.sendFailure(Component.literal((a75 ? "A-75" : "A-60") + "はすでに生存中よ")); return 0;
        }
        var player = src.getPlayer();
        if (a75) {
            if (!com.example.roomsmod.event.DoorEventHandler.spawnA75(roomsLevel, rs, player)) { src.sendFailure(Component.literal("A-75の出現に失敗したわ")); return 0; }
        } else {
            com.example.roomsmod.event.DoorEventHandler.spawnA60(roomsLevel, rs, player);
        }
        src.sendSuccess(() -> Component.literal((a75 ? "A-75" : "A-60") + "を出現させたわ"), true);
        return 1;
    }

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
                        BlockPos lobbyExit = com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, origin);
                        var state = com.example.roomsmod.world.RoomsState.get(roomsLevel);
                        state.markDoorGenerated(lobbyExit);

                        BlockPos currentExit = lobbyExit != null ? lobbyExit : origin.offset(10, 1, 31);
                        for (int i = 1; i <= target; i++) {
                            BlockPos nextOrigin = new BlockPos(currentExit.getX() - 10, 1, currentExit.getZ() + 1);
                            BlockPos exitDoor = com.example.roomsmod.world.RoomGenerator.generateNextRoom(roomsLevel, nextOrigin, i);
                            if (exitDoor != null) {
                                currentExit = exitDoor;
                                if (i < target) {
                                    state.markDoorGenerated(exitDoor);
                                }
                            }
                        }

                        // 生成器が expectedExitDoor = 最後の部屋の出口 に設定済み
                        state.setCurrentRoom(target);

                        // 最後の部屋の中央付近にワープ（出口扉の手前）
                        player.teleportTo(roomsLevel, currentExit.getX() + 0.5, 2, currentExit.getZ() - 8, 0, 0);
                        player.sendSystemMessage(Component.literal("A-" + String.format("%03d", target) + " まで生成してワープしたわ"));
                        return 1;
                    })))
            .then(Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setDebug(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Roomsデバッグ: " + (com.example.roomsmod.util.RoomsDebug.isEnabled() ? "ON" : "OFF")), false);
                    return 1;
                })))
            .then(Commands.literal("spawn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("a60").executes(ctx -> spawnRusher(ctx.getSource(), false)))
                .then(Commands.literal("a75").executes(ctx -> spawnRusher(ctx.getSource(), true))))
            .then(Commands.literal("deepest")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("最深部屋へワープ（実装中）"));
                    return 1;
                }))
        );
    }
}
