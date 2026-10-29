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
        // 輪郭（壁越し表示）を即座に全個体へ反映（tick 待ちにしない。読み込み済みの全ディメンションの roomsmod エンティティ）
        int n = 0;
        for (ServerLevel lv : src.getServer().getAllLevels())
            for (net.minecraft.world.entity.Entity e : lv.getAllEntities())
                if ("roomsmod".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace()) && e.hasGlowingTag() != on) { e.setGlowingTag(on); n++; }
        com.example.roomsmod.util.RoomsDebug.LOGGER.info("[RoomsDebug] debug outline sync -> {} entities={}", on, n);
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
            src.sendFailure(Component.literal((a40 ? "B-40" : "B-20") + "はすでに生存中よ")); return 0;
        }
        if (com.example.roomsmod.entity.v.VSpawns.isAlive(roomsLevel, com.example.roomsmod.RoomsMod.B_ERROR.get())) {
            src.sendFailure(Component.literal("B-110 が生存中なので " + (a40 ? "B-40" : "B-20") + " は出せないわ")); return 0;
        }
        var player = src.getPlayer();
        if (a40) {
            if (!com.example.roomsmod.event.DoorEventHandler.spawnA40(roomsLevel, rs, player)) { src.sendFailure(Component.literal("B-40の出現に失敗したわ")); return 0; }
        } else {
            com.example.roomsmod.event.DoorEventHandler.spawnA20(roomsLevel, rs, player);
        }
        src.sendSuccess(() -> Component.literal((a40 ? "B-40" : "B-20") + "を出現させたわ"), true);
        return 1;
    }

    /** V エンティティの強制出現（同種・排他グループの生存チェックあり）。プレイヤーのいる部屋（RCON なら最新の1つ手前）基準。 */
    private static int spawnBuffed(CommandSourceStack src, com.example.roomsmod.hiding.LockerManager.Spot t) {
        com.example.roomsmod.entity.v.V25BuffedEntity.forcedNext = t;
        try { return spawnV(src, "v25buffed"); } finally { com.example.roomsmod.entity.v.V25BuffedEntity.forcedNext = null; }
    }

    private static int spawnV(CommandSourceStack src, String id) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        if (rs.roomPaths.isEmpty()) { src.sendFailure(Component.literal("部屋がまだ生成されていないわ")); return 0; }
        var k = com.example.roomsmod.entity.v.VSpawns.Kind.byId(id);
        if (k.alive(lv)) { src.sendFailure(Component.literal(k.display + "（または同じ排他グループ）はすでに生存中よ")); return 0; }
        com.example.roomsmod.util.RoomsDebug.log("force spawn {} by {}", k.display, src.getTextName());
        if (!k.spawn(lv, rs, src.getPlayer())) { src.sendFailure(Component.literal(k.display + "の出現に失敗したわ（条件を満たす場所がない？デバッグログ参照）")); return 0; }
        src.sendSuccess(() -> Component.literal(k.display + "を出現させたわ"), true);
        return 1;
    }

    private static int addSection(CommandSourceStack src, String id, Integer startRoom) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(Component.literal("ディメンションが見つからないわ")); return 0; }
        id = com.example.roomsmod.world.section.Sections.canonical(id);
        var theme = com.example.roomsmod.world.section.Sections.find(id);
        if (theme == null) { src.sendFailure(Component.literal("セクション '" + id + "' は未登録よ（登録済み: " + com.example.roomsmod.world.section.Sections.ids() + "）")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        int start = startRoom != null ? startRoom : rs.currentRoom + 1;
        rs.chainFrom(id, start); // 以降は固定長（500部屋ごと）で次のセクションへ
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
        rs.setChain("b");
        com.example.roomsmod.world.RoomGenerator.relabelNextExitSign(lv, rs);
        src.sendSuccess(() -> Component.literal("セクション表を既定（B → V → GK → U、各 " + com.example.roomsmod.world.section.Sections.SECTION_LENGTH + " 部屋）に戻したわ"), true);
        return 1;
    }

    /** ロビー（/rooms enter と同じ地点）へ送る。ロビーは毎回生成し直す（従来どおり）。 */
    private static int giveBandage(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        src.getPlayerOrException().getInventory().add(new net.minecraft.world.item.ItemStack(com.example.roomsmod.RoomsMod.BANDAGE.get(), n));
        src.sendSuccess(() -> Component.translatable("item.roomsmod.bandage").append(" x" + n), false);
        return n;
    }

    private static int giveBattery(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        src.getPlayerOrException().getInventory().add(new net.minecraft.world.item.ItemStack(com.example.roomsmod.RoomsMod.BATTERY.get(), n));
        src.sendSuccess(() -> Component.translatable("item.roomsmod.battery").append(" x" + n), false);
        return n;
    }

    /** ロビーを作り直して部屋 1..target を生成し、最後の部屋の安全な床へワープ（/rooms to とロビーの GO パネル）。
     *  生成は数 tick に分けて行う（RoomsTravel）。 */
    public static boolean goToRoom(ServerPlayer player, int target) {
        ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) {
            player.sendSystemMessage(Component.literal("ディメンションが見つからないわ"));
            return false;
        }
        return com.example.roomsmod.world.RoomsTravel.start(roomsLevel, player, target);
    }

    public static boolean sendToLobby(ServerPlayer player) {
        ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) return false;
        player.teleportTo(roomsLevel, 10.5, 2, 16.5, 0, 0);
        // /rooms to の生成中にロビーを作り直すと進行表が途中で消え、部屋番号がずれる（死亡→リスポーン等）。生成中は移動だけ
        if (com.example.roomsmod.world.RoomsTravel.busy()) {
            com.example.roomsmod.util.RoomsDebug.log("send to lobby {}: rooms-to in progress -> teleport only (no lobby reset)", player.getGameProfile().getName());
            return true;
        }
        com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, new BlockPos(0, 1, 0));
        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rooms")
            .then(Commands.literal("enter")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (sendToLobby(player)) {
                        player.sendSystemMessage(Component.literal("Roomsディメンションへ移動したわ"));
                    } else {
                        player.sendSystemMessage(Component.literal("ディメンションが見つからないわ"));
                    }
                    return 1;
                }))
            .then(Commands.literal("to")
                .then(Commands.argument("room", IntegerArgumentType.integer(1, com.example.roomsmod.world.LobbyPanels.MAX_ROOM))
                    .executes(ctx -> {
                        return goToRoom(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "room")) ? 1 : 0;
                    })))
            .then(Commands.literal("hard")
                .then(Commands.literal("on").requires(src -> src.hasPermission(2)).executes(ctx -> setHard(ctx.getSource(), true)))
                .then(Commands.literal("off").requires(src -> src.hasPermission(2)).executes(ctx -> setHard(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerLevel lv = ctx.getSource().getServer().getLevel(ROOMS_DIMENSION);
                    if (lv == null) return 0;
                    boolean on = com.example.roomsmod.world.HardMode.isOn(lv);
                    com.example.roomsmod.util.RoomsDebug.log("hard mode status {}", on ? "on" : "off");
                    ctx.getSource().sendSuccess(() -> Component.literal("HARD MODE: " + (on ? "ON" : "OFF")), false);
                    return on ? 1 : 0;
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
            .then(Commands.literal("give")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("flashlight").executes(ctx -> {
                    ctx.getSource().getPlayerOrException().getInventory().add(com.example.roomsmod.item.FlashlightHandler.newFlashlight(com.example.roomsmod.item.FlashlightItem.BATTERY_TICKS));
                    ctx.getSource().sendSuccess(() -> Component.translatable("item.roomsmod.flashlight"), false);
                    return 1;
                }))
                .then(Commands.literal("bandage").executes(ctx -> giveBandage(ctx.getSource(), 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveBandage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("battery").executes(ctx -> giveBattery(ctx.getSource(), 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveBattery(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))))
            .then(Commands.literal("spawn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("b20").executes(ctx -> spawnRusher(ctx.getSource(), false)))
                .then(Commands.literal("b40").executes(ctx -> spawnRusher(ctx.getSource(), true)))
                .then(Commands.literal("v10").executes(ctx -> spawnV(ctx.getSource(), "v10")))
                .then(Commands.literal("v25t").executes(ctx -> spawnV(ctx.getSource(), "v25t")))
                .then(Commands.literal("v25l").executes(ctx -> spawnV(ctx.getSource(), "v25l")))
                .then(Commands.literal("v25buffed").executes(ctx -> spawnV(ctx.getSource(), "v25buffed"))
                    .then(Commands.literal("table").executes(ctx -> spawnBuffed(ctx.getSource(), com.example.roomsmod.hiding.LockerManager.Spot.TABLE)))
                    .then(Commands.literal("locker").executes(ctx -> spawnBuffed(ctx.getSource(), com.example.roomsmod.hiding.LockerManager.Spot.LOCKER))))
                .then(Commands.literal("v100").executes(ctx -> spawnV(ctx.getSource(), "v100")))
                // 番号名（B-15 / GK-10 / B-110 など）
                .then(Commands.literal("b15").executes(ctx -> spawnV(ctx.getSource(), "bstatic")))
                .then(Commands.literal("b30").executes(ctx -> spawnV(ctx.getSource(), "bmask")))
                .then(Commands.literal("b50").executes(ctx -> spawnV(ctx.getSource(), "breach")))
                .then(Commands.literal("b70").executes(ctx -> spawnV(ctx.getSource(), "beye")))
                .then(Commands.literal("b90").executes(ctx -> spawnV(ctx.getSource(), "bsmile")))
                .then(Commands.literal("gk10").executes(ctx -> spawnV(ctx.getSource(), "gkmoth")))
                .then(Commands.literal("gk25").executes(ctx -> spawnV(ctx.getSource(), "gkchime")))
                .then(Commands.literal("gk45").executes(ctx -> spawnV(ctx.getSource(), "gkwhisper")))
                .then(Commands.literal("gk65").executes(ctx -> spawnV(ctx.getSource(), "gkdetention")))
                .then(Commands.literal("gk85").executes(ctx -> spawnV(ctx.getSource(), "gkquiz")))
                .then(Commands.literal("gk105").executes(ctx -> spawnV(ctx.getSource(), "gksubstitute")))
                .then(Commands.literal("gk125").executes(ctx -> spawnV(ctx.getSource(), "gk07")))
                .then(Commands.literal("gk145").executes(ctx -> spawnV(ctx.getSource(), "gkrewind")))
                .then(Commands.literal("gk165").executes(ctx -> spawnV(ctx.getSource(), "gkmimic")))
                .then(Commands.literal("gk185").executes(ctx -> spawnV(ctx.getSource(), "gkpa")))
                .then(Commands.literal("gk205").executes(ctx -> spawnV(ctx.getSource(), "gk12")))
                .then(Commands.literal("gk225").executes(ctx -> spawnV(ctx.getSource(), "gkjanitor")))
                .then(Commands.literal("gk245").executes(ctx -> spawnV(ctx.getSource(), "gkeraser")))
                .then(Commands.literal("gk270").executes(ctx -> spawnV(ctx.getSource(), "gkmirror")))
                .then(Commands.literal("gk300").executes(ctx -> spawnV(ctx.getSource(), "gkbell2")))
                .then(Commands.literal("u20").executes(ctx -> spawnV(ctx.getSource(), "u20")))
                .then(Commands.literal("u60").executes(ctx -> spawnV(ctx.getSource(), "u60")))
                .then(Commands.literal("u120").executes(ctx -> spawnV(ctx.getSource(), "u120")))
                .then(Commands.literal("u200").executes(ctx -> spawnV(ctx.getSource(), "u200")))
                .then(Commands.literal("u300").executes(ctx -> spawnV(ctx.getSource(), "u300")))
                .then(Commands.literal("332").executes(ctx -> spawnV(ctx.getSource(), "332")))
                .then(Commands.literal("placeholder32767").executes(ctx -> spawnV(ctx.getSource(), "placeholder32767")))
                .then(Commands.literal("placeholder65535").executes(ctx -> spawnV(ctx.getSource(), "placeholder65535")))
                .then(Commands.literal("placeholder2147483648").executes(ctx -> spawnV(ctx.getSource(), "placeholder2147483648")))
                .then(Commands.literal("b110").executes(ctx -> spawnV(ctx.getSource(), "berror")))
                .then(Commands.literal("b130").executes(ctx -> spawnV(ctx.getSource(), "bgrin")))
                .then(Commands.literal("b155").executes(ctx -> spawnV(ctx.getSource(), "bmaw")))
                .then(Commands.literal("b180").executes(ctx -> spawnV(ctx.getSource(), "bargus")))
                .then(Commands.literal("b205").executes(ctx -> spawnV(ctx.getSource(), "bscatter")))
                .then(Commands.literal("b230").executes(ctx -> spawnV(ctx.getSource(), "bchorus")))
                .then(Commands.literal("b260").executes(ctx -> spawnV(ctx.getSource(), "bvoid")))
                .then(Commands.literal("b290").executes(ctx -> spawnV(ctx.getSource(), "b17")))
                .then(Commands.literal("v50").executes(ctx -> spawnV(ctx.getSource(), "v50"))))
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

    private static int setHard(CommandSourceStack src, boolean on) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) return 0;
        com.example.roomsmod.world.HardMode.set(lv, on, src.getTextName());
        src.sendSuccess(() -> Component.literal("HARD MODE: " + (on ? "ON" : "OFF")), true);
        return 1;
    }
}
