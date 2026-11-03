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
            src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない"));
            return 0;
        }
        com.example.roomsmod.world.RoomsState.get(roomsLevel).setDebug(on);
        // 輪郭（壁越し表示）を即座に全個体へ反映（tick 待ちにしない。読み込み済みの全ディメンションの roomsmod エンティティ）
        int n = 0;
        for (ServerLevel lv : src.getServer().getAllLevels())
            for (net.minecraft.world.entity.Entity e : lv.getAllEntities())
                if ("roomsmod".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace()) && e.hasGlowingTag() != on) { e.setGlowingTag(on); n++; }
        com.example.roomsmod.util.RoomsDebug.info("debug outline sync -> {} entities={}", on, n);
        com.example.roomsmod.util.RoomsDebug.info("debug {} by {}", on ? "ON" : "OFF", src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("Roomsデバッグを" + (on ? "ON" : "OFF") + "にした"), true);
        return 1;
    }

    private static int spawnRusher(CommandSourceStack src, boolean a40) {
        ServerLevel roomsLevel = src.getServer().getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(roomsLevel);
        if (rs.roomPaths.isEmpty()) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("部屋がまだ生成されていない")); return 0; }
        if (com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.blocksSpawns(roomsLevel)) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("PLACEHOLDER-2147483648 が生存中は他の敵を出せない（/kill で消せる）")); return 0; }
        boolean sameAlive = a40 ? com.example.roomsmod.event.DoorEventHandler.isA40Alive(roomsLevel)
                                : com.example.roomsmod.event.DoorEventHandler.isA20Alive(roomsLevel);
        if (sameAlive) {
            src.sendFailure(com.example.roomsmod.util.RoomsMsg.err((a40 ? "B-40" : "B-20") + "はすでに生存中")); return 0;
        }
        if (com.example.roomsmod.entity.v.VSpawns.isAlive(roomsLevel, com.example.roomsmod.RoomsMod.B_ERROR.get())) {
            src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("B-110 が生存中なので " + (a40 ? "B-40" : "B-20") + " は出せない")); return 0;
        }
        var player = src.getPlayer();
        if (a40) {
            if (!com.example.roomsmod.world.AbyssSpace.asCommand(() -> com.example.roomsmod.event.DoorEventHandler.spawnA40(roomsLevel, rs, player))) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("B-40の出現に失敗した")); return 0; }
        } else {
            com.example.roomsmod.world.AbyssSpace.asCommand(() -> { com.example.roomsmod.event.DoorEventHandler.spawnA20(roomsLevel, rs, player); return true; });
        }
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok((a40 ? "B-40" : "B-20") + "を出現させた"), true);
        return 1;
    }

    /** V エンティティの強制出現（同種・排他グループの生存チェックあり）。プレイヤーのいる部屋（RCON なら最新の1つ手前）基準。 */
    private static int spawnBuffed(CommandSourceStack src, com.example.roomsmod.hiding.LockerManager.Spot t) {
        com.example.roomsmod.entity.v.V25BuffedEntity.forcedNext = t;
        try { return spawnV(src, "v25buffed"); } finally { com.example.roomsmod.entity.v.V25BuffedEntity.forcedNext = null; }
    }

    private static int spawnV(CommandSourceStack src, String id) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        if (rs.roomPaths.isEmpty()) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("部屋がまだ生成されていない")); return 0; }
        var k = com.example.roomsmod.entity.v.VSpawns.Kind.byId(id);
        if (com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.blocksSpawns(lv)) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("PLACEHOLDER-2147483648 が生存中は他の敵を出せない（/kill で消せる）")); return 0; }
        if (k.alive(lv)) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(k.display + "（または同じ排他グループ）はすでに生存中")); return 0; }
        com.example.roomsmod.util.RoomsDebug.log("force spawn {} by {}", k.display, src.getTextName());
        if (!com.example.roomsmod.world.AbyssSpace.asCommand(() -> k.spawn(lv, rs, src.getPlayer()))) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(k.display + "の出現に失敗した（条件を満たす場所がない？デバッグログ参照）")); return 0; }
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(k.display + "を出現させた"), true);
        return 1;
    }

    private static int addSection(CommandSourceStack src, String id, Integer startRoom) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない")); return 0; }
        id = com.example.roomsmod.world.section.Sections.canonical(id);
        var theme = com.example.roomsmod.world.section.Sections.find(id);
        if (theme == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("セクション '" + id + "' は未登録（登録済み: " + com.example.roomsmod.world.section.Sections.ids() + "）")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        if (rs.isSectionLocked(id)) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(Component.translatable("message.roomsmod.section_locked", id.toUpperCase(java.util.Locale.ROOT)))); return 0; }
        int start = startRoom != null ? startRoom : rs.currentRoom + 1;
        rs.chainFrom(id, start); // 以降は固定長（500部屋ごと）で次のセクションへ
        boolean relabeled = start == rs.currentRoom + 1 && com.example.roomsmod.world.RoomGenerator.relabelNextExitSign(lv, rs);
        String label = com.example.roomsmod.world.section.Sections.formatRoom(rs, start);
        com.example.roomsmod.util.RoomsDebug.info("section {} added at global room {} ({}) by {} table={}", id, start, label, src.getTextName(), rs.sections);
        String msg = "セクション " + theme.prefix() + " を部屋 " + start + " から開始する（その部屋は " + label + "）"
                + (start <= rs.currentRoom ? "。※生成済みの部屋は変わらない" : "")
                + (relabeled ? "。次の扉の番号も書き換えた" : "");
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info(msg), true);
        return 1;
    }

    private static int resetSections(CommandSourceStack src) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない")); return 0; }
        var rs = com.example.roomsmod.world.RoomsState.get(lv);
        rs.setChain(com.example.roomsmod.world.section.Sections.firstId());
        com.example.roomsmod.world.RoomGenerator.relabelNextExitSign(lv, rs);
        java.util.List<String> ids = rs.sections.stream().map(x -> x.themeId().toUpperCase(java.util.Locale.ROOT)).toList(); // ロック中の H は入らない
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("セクション表を既定（" + String.join(" → ", ids) + "、" + rs.sections.stream().map(x -> x.themeId().toUpperCase(java.util.Locale.ROOT) + " " + com.example.roomsmod.world.section.Sections.length(x.themeId())).collect(java.util.stream.Collectors.joining(" / ")) + " 部屋）に戻した"), true);
        return 1;
    }

    /** ロビー（/rooms enter と同じ地点）へ送る。ロビーは毎回生成し直す（従来どおり）。 */

    /** ロビーを作り直して部屋 1..target を生成し、最後の部屋の安全な床へワープ（/rooms to とロビーの GO パネル）。
     *  生成は数 tick に分けて行う（RoomsTravel）。 */
    public static boolean goToRoom(ServerPlayer player, int target) {
        ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) {
            player.sendSystemMessage(com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない"));
            return false;
        }
        return com.example.roomsmod.world.RoomsTravel.start(roomsLevel, player, target);
    }

    public static boolean sendToLobby(ServerPlayer player) {
        ServerLevel roomsLevel = player.server.getLevel(ROOMS_DIMENSION);
        if (roomsLevel == null) return false;
        player.teleportTo(roomsLevel, com.example.roomsmod.world.RoomsLayout.SPAWN_X, com.example.roomsmod.world.RoomsLayout.SPAWN_Y, com.example.roomsmod.world.RoomsLayout.SPAWN_Z, 0, 0);
        // /rooms to の生成中にロビーを作り直すと進行表が途中で消え、部屋番号がずれる（死亡→リスポーン等）。生成中は移動だけ
        if (com.example.roomsmod.world.RoomsTravel.busy()) {
            com.example.roomsmod.util.RoomsDebug.log("send to lobby {}: rooms-to in progress -> teleport only (no lobby reset)", player.getGameProfile().getName());
            return true;
        }
        com.example.roomsmod.world.RoomGenerator.generateLobby(roomsLevel, com.example.roomsmod.world.RoomsLayout.LOBBY_ORIGIN);
        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rooms")
            .then(AdminCommands.build())
            .then(Commands.literal("record")
                .executes(ctx -> showRecord(ctx.getSource(), null))
                .then(Commands.literal("top").executes(ctx -> showTop(ctx.getSource()))
                    .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> { java.util.List<String> o = new java.util.ArrayList<>(com.example.roomsmod.world.section.Sections.ORDER); o.add("streak");
                            return net.minecraft.commands.SharedSuggestionProvider.suggest(o, b); })
                        .executes(ctx -> showTopKind(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(recordNames(c.getSource()), b))
                    .executes(ctx -> showRecord(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("restart")
                .executes(ctx -> com.example.roomsmod.economy.RoomsRun.restart(ctx.getSource().getPlayerOrException()) ? 1 : 0))
            .then(Commands.literal("hard")
                .then(Commands.literal("status").executes(ctx -> {
                    ServerLevel lv = ctx.getSource().getServer().getLevel(ROOMS_DIMENSION);
                    if (lv == null) return 0;
                    boolean on = com.example.roomsmod.world.HardMode.isOn(lv);
                    com.example.roomsmod.util.RoomsDebug.log("hard mode status {}", on ? "on" : "off");
                    ctx.getSource().sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("HARD MODE: " + (on ? "ON" : "OFF")), false);
                    return on ? 1 : 0;
                })))
        );
    }

    /** 記録のある人＋オンラインの人の名前（補完用）。 */
    static java.util.List<String> recordNames(CommandSourceStack src) {
        java.util.Set<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var e : com.example.roomsmod.economy.RoomsRecords.get(src.getServer()).all()) if (e.name != null && !e.name.isEmpty()) out.add(e.name);
        out.addAll(java.util.Arrays.asList(src.getServer().getPlayerNames()));
        return new java.util.ArrayList<>(out);
    }

    /** 名前（記録の最後の名前、なければオンラインの人）から記録を探す。 */
    static com.example.roomsmod.economy.RoomsRecords.Entry findRecord(CommandSourceStack src, String name) {
        var db = com.example.roomsmod.economy.RoomsRecords.get(src.getServer());
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return db.find(online.getUUID());
        return db.findByName(name);
    }

    /** /rooms record [player]（誰でも）。 */
    private static int showRecord(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.example.roomsmod.economy.RoomsRecords.Entry e;
        String who;
        if (name == null) {
            ServerPlayer p = src.getPlayerOrException();
            who = p.getGameProfile().getName();
            e = com.example.roomsmod.economy.RoomsRecords.get(src.getServer()).find(p.getUUID());
        } else { who = name; e = findRecord(src, name); }
        if (e == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(who + " の記録はまだない")); return 0; }
        for (var line : com.example.roomsmod.economy.RoomsRecords.describe(e)) src.sendSuccess(() -> line, false);
        return 1;
    }

    /** /rooms record top（誰でも）。 */
    private static int showTop(CommandSourceStack src) {
        for (var line : com.example.roomsmod.economy.RoomsRecords.get(src.getServer()).ranking(10)) src.sendSuccess(() -> line, false);
        return 1;
    }

    /** /rooms record top &lt;section|streak&gt;（誰でも）。 */
    private static int showTopKind(CommandSourceStack src, String kind) {
        var db = com.example.roomsmod.economy.RoomsRecords.get(src.getServer());
        String k = com.example.roomsmod.world.section.Sections.canonical(kind);
        java.util.List<net.minecraft.network.chat.MutableComponent> lines;
        if ("streak".equals(k)) lines = db.rankingStreak(10);
        else if (com.example.roomsmod.world.section.Sections.ORDER.contains(k)) lines = db.rankingFromOne(k, 10);
        else { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("セクション（" + String.join(" / ", com.example.roomsmod.world.section.Sections.ORDER) + "）か streak を指定")); return 0; }
        for (var line : lines) src.sendSuccess(() -> line, false);
        return 1;
    }

    private static int setHard(CommandSourceStack src, boolean on) {
        ServerLevel lv = src.getServer().getLevel(ROOMS_DIMENSION);
        if (lv == null) return 0;
        com.example.roomsmod.world.HardMode.set(lv, on, src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("HARD MODE: " + (on ? "ON" : "OFF")), true);
        return 1;
    }

    /** /rooms admin build &lt;true|false&gt;：部屋ディメンションでのブロック設置の可否（サーバー設定 allowBlockPlacing を変更して保存）。 */
    private static int setBuild(CommandSourceStack src, boolean on) {
        if (!com.example.roomsmod.config.RoomsConfig.setAllowBlockPlacing(on)) {
            src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("サーバー設定が読み込まれていない"));
            return 0;
        }
        com.example.roomsmod.util.RoomsDebug.info("allowBlockPlacing {} by {}", on, src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("ブロック設置を" + (on ? "ON（置ける）" : "OFF（置けない）") + "にした"), true);
        return 1;
    }

    /** /rooms admin の下に入れる管理用のコマンド（以前は OP 用の /rooms to・hard on/off・debug・spawn・section）。AdminCommands.build から。 */
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> adminSubtrees(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> admin) {
        return admin
            .then(Commands.literal("to")
                .then(Commands.argument("room", IntegerArgumentType.integer(1, com.example.roomsmod.world.LobbyPanels.MAX_ROOM))
                    .executes(ctx -> {
                        ServerPlayer tp = ctx.getSource().getPlayerOrException();
                        com.example.roomsmod.economy.Wallet.of(tp).resetRun(Integer.MIN_VALUE); // 今の周回は精算しない
                        com.example.roomsmod.economy.RoomsRun.adminTeleport(tp); // この周回はコインなし
                        return goToRoom(tp, IntegerArgumentType.getInteger(ctx, "room")) ? 1 : 0;
                    })))
            .then(Commands.literal("hard")
                .then(Commands.literal("on").executes(ctx -> setHard(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setHard(ctx.getSource(), false))))
            .then(Commands.literal("build")
                .executes(ctx -> {
                    boolean on = com.example.roomsmod.config.RoomsConfig.allowBlockPlacing();
                    ctx.getSource().sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info("ブロック設置（allowBlockPlacing）: " + (on ? "ON" : "OFF")), false);
                    return 1;
                })
                .then(Commands.argument("allow", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                    .executes(ctx -> setBuild(ctx.getSource(), com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "allow")))))
            .then(Commands.literal("debug")
                .then(Commands.literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setDebug(ctx.getSource(), false)))
                .then(Commands.literal("section").executes(ctx -> {
                    ServerLevel lv = ctx.getSource().getServer().getLevel(ROOMS_DIMENSION);
                    if (lv == null) return 0;
                    var rs = com.example.roomsmod.world.RoomsState.get(lv);
                    var cur = com.example.roomsmod.world.section.Sections.forRoom(rs, rs.currentRoom);
                    String msg = "section current=" + cur.id() + " (prefix " + cur.prefix() + ") room=" + com.example.roomsmod.world.section.Sections.formatRoom(rs, rs.currentRoom) + " (global " + rs.currentRoom + ", local " + com.example.roomsmod.world.section.Sections.localRoom(rs, rs.currentRoom) + ") table=" + rs.sections;
                    com.example.roomsmod.util.RoomsDebug.info("{}", msg);
                    ctx.getSource().sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info(msg), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info("Roomsデバッグ: " + (com.example.roomsmod.util.RoomsDebug.isEnabled() ? "ON" : "OFF")), false);
                    return 1;
                })))
            .then(Commands.literal("spawn")
                .then(Commands.literal("b20").executes(ctx -> spawnRusher(ctx.getSource(), false)))
                .then(Commands.literal("b40").executes(ctx -> spawnRusher(ctx.getSource(), true)))
                .then(Commands.literal("v10").executes(ctx -> spawnV(ctx.getSource(), "v10")))
                .then(Commands.literal("v25t").executes(ctx -> spawnV(ctx.getSource(), "v25t")))
                .then(Commands.literal("v25l").executes(ctx -> spawnV(ctx.getSource(), "v25l")))
                .then(Commands.literal("v25buffed").executes(ctx -> spawnV(ctx.getSource(), "v25buffed"))
                    .then(Commands.literal("table").executes(ctx -> spawnBuffed(ctx.getSource(), com.example.roomsmod.hiding.LockerManager.Spot.TABLE)))
                    .then(Commands.literal("locker").executes(ctx -> spawnBuffed(ctx.getSource(), com.example.roomsmod.hiding.LockerManager.Spot.LOCKER))))
                .then(Commands.literal("v100").executes(ctx -> spawnV(ctx.getSource(), "v100")))
                // 番号名（B-15 / S-10 / B-110 など）
                .then(Commands.literal("b15").executes(ctx -> spawnV(ctx.getSource(), "bstatic")))
                .then(Commands.literal("b30").executes(ctx -> spawnV(ctx.getSource(), "bmask")))
                .then(Commands.literal("b50").executes(ctx -> spawnV(ctx.getSource(), "breach")))
                .then(Commands.literal("b70").executes(ctx -> spawnV(ctx.getSource(), "beye")))
                .then(Commands.literal("b90").executes(ctx -> spawnV(ctx.getSource(), "bsmile")))
                .then(Commands.literal("s10").executes(ctx -> spawnV(ctx.getSource(), "smoth")))
                .then(Commands.literal("s25").executes(ctx -> spawnV(ctx.getSource(), "schime")))
                .then(Commands.literal("s45").executes(ctx -> spawnV(ctx.getSource(), "swhisper")))
                .then(Commands.literal("s65").executes(ctx -> spawnV(ctx.getSource(), "sdetention")))
                .then(Commands.literal("s85").executes(ctx -> spawnV(ctx.getSource(), "squiz")))
                .then(Commands.literal("s105").executes(ctx -> spawnV(ctx.getSource(), "ssubstitute")))
                .then(Commands.literal("s125").executes(ctx -> spawnV(ctx.getSource(), "s07")))
                .then(Commands.literal("s145").executes(ctx -> spawnV(ctx.getSource(), "srewind")))
                .then(Commands.literal("s165").executes(ctx -> spawnV(ctx.getSource(), "smimic")))
                .then(Commands.literal("s185").executes(ctx -> spawnV(ctx.getSource(), "spa")))
                .then(Commands.literal("s205").executes(ctx -> spawnV(ctx.getSource(), "sstare")))
                .then(Commands.literal("s225").executes(ctx -> spawnV(ctx.getSource(), "sjanitor")))
                .then(Commands.literal("s245").executes(ctx -> spawnV(ctx.getSource(), "seraser")))
                .then(Commands.literal("s270").executes(ctx -> spawnV(ctx.getSource(), "smirror")))
                .then(Commands.literal("s300").executes(ctx -> spawnV(ctx.getSource(), "sbell2")))
                .then(Commands.literal("u20").executes(ctx -> spawnV(ctx.getSource(), "u20")))
                .then(Commands.literal("a25").executes(ctx -> spawnV(ctx.getSource(), "a25")))
                .then(Commands.literal("a90").executes(ctx -> spawnV(ctx.getSource(), "a90")))
                .then(Commands.literal("a180").executes(ctx -> spawnV(ctx.getSource(), "a180")))
                .then(Commands.literal("a50").executes(ctx -> spawnV(ctx.getSource(), "a50")))
                .then(Commands.literal("a130").executes(ctx -> spawnV(ctx.getSource(), "a130")))
                .then(Commands.literal("a250").executes(ctx -> spawnV(ctx.getSource(), "a250")))
                .then(Commands.literal("a350").executes(ctx -> spawnV(ctx.getSource(), "a350")))
                .then(Commands.literal("a200").executes(ctx -> spawnV(ctx.getSource(), "a200")))
                .then(Commands.literal("a300").executes(ctx -> spawnV(ctx.getSource(), "a300")))
                .then(Commands.literal("a450").executes(ctx -> spawnV(ctx.getSource(), "a450")))
                .then(Commands.literal("a525").executes(ctx -> spawnV(ctx.getSource(), "a525")))
                .then(Commands.literal("a600").executes(ctx -> spawnV(ctx.getSource(), "a600")))
                .then(Commands.literal("a666").executes(ctx -> spawnV(ctx.getSource(), "a666")))
                .then(Commands.literal("a750").executes(ctx -> spawnV(ctx.getSource(), "a750")))
                .then(Commands.literal("a800").executes(ctx -> spawnV(ctx.getSource(), "a800")))
                .then(Commands.literal("a1000").executes(ctx -> spawnV(ctx.getSource(), "a1000")))
                .then(Commands.literal("a1050").executes(ctx -> spawnV(ctx.getSource(), "a1050")))
                .then(Commands.literal("a1100").executes(ctx -> spawnV(ctx.getSource(), "a1100")))
                .then(Commands.literal("a1111").executes(ctx -> spawnV(ctx.getSource(), "a1111")))
                .then(Commands.literal("a1180").executes(ctx -> spawnV(ctx.getSource(), "a1180")))
                .then(Commands.literal("u60").executes(ctx -> spawnV(ctx.getSource(), "u60")))
                .then(Commands.literal("u120").executes(ctx -> spawnV(ctx.getSource(), "u120")))
                .then(Commands.literal("u200").executes(ctx -> spawnV(ctx.getSource(), "u200")))
                .then(Commands.literal("u300").executes(ctx -> spawnV(ctx.getSource(), "u300")))
                .then(Commands.literal("h30").executes(ctx -> spawnV(ctx.getSource(), "h30")))
                .then(Commands.literal("h90").executes(ctx -> spawnV(ctx.getSource(), "h90")))
                .then(Commands.literal("h150").executes(ctx -> spawnV(ctx.getSource(), "h150")))
                .then(Commands.literal("h260").executes(ctx -> spawnV(ctx.getSource(), "h260")))
                .then(Commands.literal("f5").executes(ctx -> spawnV(ctx.getSource(), "f5")))
                .then(Commands.literal("f35").executes(ctx -> spawnV(ctx.getSource(), "f35")))
                .then(Commands.literal("f100").executes(ctx -> spawnV(ctx.getSource(), "f100")))
                .then(Commands.literal("f200").executes(ctx -> spawnV(ctx.getSource(), "f200")))
                .then(Commands.literal("mimic").executes(ctx -> spawnV(ctx.getSource(), "mimic")))
                .then(Commands.literal("placeholder32767").executes(ctx -> spawnV(ctx.getSource(), "placeholder32767")))
                .then(Commands.literal("placeholder65535").executes(ctx -> spawnV(ctx.getSource(), "placeholder65535")))
                .then(Commands.literal("placeholder2147483648").executes(ctx -> spawnV(ctx.getSource(), "placeholder2147483648")))
                .then(Commands.literal("extrabox").executes(ctx -> spawnV(ctx.getSource(), "extrabox")))
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
                .then(Commands.literal("reset").executes(ctx -> resetSections(ctx.getSource())))
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(com.example.roomsmod.world.section.Sections.ids(), b))
                    .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), null))
                    .then(Commands.literal("now")
                        .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), null)))
                    .then(Commands.argument("startRoom", IntegerArgumentType.integer(1))
                        .executes(ctx -> addSection(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "startRoom"))))));
    }

}
