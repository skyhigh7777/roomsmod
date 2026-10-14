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

    private static int spawnRusher(CommandSourceStack src, boolean a40) {
        ServerLevel roomsLevel = src.getServer().getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(roomsLevel);
        if (rs.roomPaths.isEmpty()) { src.sendFailure(Component.literal("部屋がまだ生成されていないわ")); return 0; }
        boolean sameAlive = a40 ? com.example.roomsmod.event.DoorEventHandler.isA40Alive(roomsLevel)
                                : com.example.roomsmod.event.DoorEventHandler.isA20Alive(roomsLevel);
        if (sameAlive) {
            src.sendFailure(Component.literal((a40 ? "A-40" : "A-20") + "はすでに生存中よ")); return 0;
        }
        var player = src.getPlayer();
        if (a40) {
            if (!com.example.roomsmod.event.DoorEventHandler.spawnA40(roomsLevel, rs, player)) { src.sendFailure(Component.literal("A-40の出現に失敗したわ")); return 0; }
        } else {
            com.example.roomsmod.event.DoorEventHandler.spawnA20(roomsLevel, rs, player);
        }
        src.sendSuccess(() -> Component.literal((a40 ? "A-40" : "A-20") + "を出現させたわ"), true);
        return 1;
    }

    private static int addSection(CommandSourceStack src, String id, Integer startRoom) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        id = id.toLowerCase(java.util.Locale.ROOT);
        var theme = com.example.roomsmod.world.section.Sections.find(id);
        if (theme == null) { src.sendFailure(Component.literal("セクション '" + id + "' は未登録よ（登録済み: " + com.example.roomsmod.world.section.Sections.ids() + "）")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        int start = startRoom != null ? startRoom : rs.currentRoom + 1;
        rs.addSection(id, start);
        boolean relabeled = start == rs.currentRoom + 1 && com.example.roomsmod.world.RoomGenerator.relabelNextExitSign(lv, rs);
        String label = com.example.roomsmod.world.section.Sections.formatRoom(rs, start);
        com.example.roomsmod.util.RoomsDebug.LOGGER.info("[RoomsDebug] section {} added at global room {} ({}) by {} table={}", id, start, label, src.getTextName(), rs.sections);
        String msg = "セクション " + theme.prefix() + " を部屋 " + start + " から開始するわ（その部屋は " + label + "）"
                + (start <= rs.currentRoom ? "。※生成済みの部屋は変わらないわ" : "")
                + (relabeled ? "。次の扉の番号も書き換えたわ" : "");
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int resetSections(CommandSourceStack src) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        rs.sections.clear();
        rs.sections.add(new com.example.roomsmod.world.RoomsState.SectionStart("a", 0));
        rs.setDirty();
        com.example.roomsmod.world.RoomGenerator.relabelNextExitSign(lv, rs);
        src.sendSuccess(() -> Component.literal("セクション表を A のみに戻したわ"), true);
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
                        player.sendSystemMessage(Component.literal(com.example.roomsmod.world.section.Sections.formatRoom(state, target) + " まで生成してワープしたわ"));
                        return 1;
                    })))
            .then(Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setDebug(ctx.getSource(), false)))
                .then(Commands.literal("section").executes(ctx -> {
                    ServerLevel lv = ctx.getSource().getServer().getLevel(ROOMS_DIMENSION);
                    if (lv == null) return 0;
                    var rs = com.example.roomsmod.world.RoomsState.get(lv);
                    var cur = com.example.roomsmod.world.section.Sections.forRoom(rs, rs.currentRoom);
                    String msg = "section current=" + cur.id() + " (prefix " + cur.prefix() + ") room=" + com.example.roomsmod.world.section.Sections.formatRoom(rs, rs.currentRoom) + " (global " + rs.currentRoom + ", local " + com.example.roomsmod.world.section.Sections.localRoom(rs, rs.currentRoom) + ") table=" + rs.sections;
                    com.example.roomsmod.util.RoomsDebug.LOGGER.info("[RoomsDebug] " + msg);
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Roomsデバッグ: " + (com.example.roomsmod.util.RoomsDebug.isEnabled() ? "ON" : "OFF")), false);
                    return 1;
                })))
            .then(Commands.literal("spawn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("a20").executes(ctx -> spawnRusher(ctx.getSource(), false)))
                .then(Commands.literal("a40").executes(ctx -> spawnRusher(ctx.getSource(), true))))
            .then(Commands.literal("section")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reset").executes(ctx -> resetSections(ctx.getSource())))
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(com.example.roomsmod.world.section.Sections.ids(), b))
                    .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), null))
                    .then(Commands.literal("now")
                        .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), null)))
                    .then(Commands.argument("startRoom", IntegerArgumentType.integer(1))
                        .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "startRoom"))))))
            .then(Commands.literal("deepest")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("最深部屋へワープ（実装中）"));
                    return 1;
                }))
        );
    }
}
