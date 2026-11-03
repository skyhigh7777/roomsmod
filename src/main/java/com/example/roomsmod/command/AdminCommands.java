package com.example.roomsmod.command;

import com.example.roomsmod.economy.Wallet;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.LobbyPanels;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

/**
 * オーナー専用の管理コマンド /rooms admin ...（OP 権限・サーバーコンソール・コマンドブロック・/execute as では使えない）。
 * 実行者が本人のプレイヤー（CommandSourceStack.source がそのプレイヤーで、実体も同じ）で、UUID が {@link #OWNER} の時だけ。
 * <ul>
 *   <li>section [id lock|unlock]：セクションの利用可否（既定は PLACEHOLDER・H・F がロック）。ロック中はロビーのパネル・/rooms section で選べない。H・F はロック中は進行表にも入らない（解放で S → H → F → A）。</li>
 *   <li>wallet &lt;player&gt; [set battery|bandage|coins &lt;n&gt;]：所持数を見る・変える。</li>
 *   <li>run reset [player]：周回の記録を消す（コインは出ない）。</li>
 *   <li>record reset &lt;player&gt;：到達記録（RoomsRecords、オフラインの人も名前で）を消す。</li>
 *   <li>give flashlight | bandage [n] | battery [n] | adrenaline [n] | charm [n] | radar [n] | scanner [n] | coins &lt;n&gt;：自分に渡す（電池・包帯・コインは所持数へ、上限まで）。以前の OP 用 /rooms give から移動。</li>
 *   <li>deepest：自分のいちばん深い部屋（Wallet.deepSection / deepLocal、ORDER の通しの深さで比べる、周回・死亡をまたいで残る）へ。
 *       今の部屋の並びにその部屋が生成済みならそこの安全な床へワープ（進行はそのまま）、なければ tp と同じくそのセクションから作り直してワープ。</li>
 *   <li>to &lt;n&gt; / hard on|off / debug ... / spawn ... / section ...：以前の OP 用コマンド（RoomsCommands.adminSubtrees）。</li>
 *   <li>tp &lt;section&gt; &lt;room&gt;：そのセクションのその部屋へ（ロビー作り直し＝GO と同じ。ロックを無視、自分の周回の記録は消す）。</li>
 * </ul>
 */
public final class AdminCommands {
    private AdminCommands() {}

    /** オーナー（Soratti_、PC の usercache.json より）。 */
    public static final UUID OWNER = UUID.fromString("e4bc3d43-f5bc-41ec-9572-b49fa2abf570");

    public static boolean isOwner(CommandSourceStack src) {
        return src.source instanceof ServerPlayer sp && sp == src.getEntity() && OWNER.equals(sp.getUUID());
    }

    // ---- 回復のオーブ（オーバーワールドに 1 つ） ----
    private static int orbInfo(CommandSourceStack src) {
        var ow = src.getServer().overworld();
        String at = com.example.roomsmod.entity.orb.HealingOrb.describe(ow);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("回復のオーブ（オーバーワールド）：" + at + "　ワールドスポーン " + ow.getSharedSpawnPos().toShortString()), false);
        return 1;
    }

    private static int orbPlace(CommandSourceStack src, boolean here) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var ow = src.getServer().overworld();
        net.minecraft.core.BlockPos at = null;
        if (here) {
            ServerPlayer p = src.getPlayerOrException();
            if (p.level() != ow) { src.sendFailure(net.minecraft.network.chat.Component.literal("オーバーワールドで使ってください")); return 0; }
            at = p.blockPosition();
        }
        net.minecraft.core.BlockPos placed = com.example.roomsmod.entity.orb.HealingOrb.place(ow, at);
        if (placed == null) { src.sendFailure(net.minecraft.network.chat.Component.literal("置けませんでした")); return 0; }
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("回復のオーブを置きました：" + placed.toShortString()), false);
        RoomsDebug.info("admin: healing orb placed at {} by {}", placed, src.getTextName());
        return 1;
    }

    private static int orbRemove(CommandSourceStack src) {
        boolean had = com.example.roomsmod.entity.orb.HealingOrb.remove(src.getServer().overworld());
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(had ? "回復のオーブを消しました（自動では置き直しません。戻すなら /rooms admin orb place）" : "オーブはありません"), false);
        RoomsDebug.info("admin: healing orb removed by {}", src.getTextName());
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return RoomsCommands.adminSubtrees(Commands.literal("admin")
            .requires(AdminCommands::isOwner)
            .then(Commands.literal("orb")
                .executes(ctx -> orbInfo(ctx.getSource()))
                .then(Commands.literal("place")
                    .executes(ctx -> orbPlace(ctx.getSource(), false))
                    .then(Commands.literal("here").executes(ctx -> orbPlace(ctx.getSource(), true))))
                .then(Commands.literal("remove").executes(ctx -> orbRemove(ctx.getSource()))))
            .then(Commands.literal("deepest").executes(ctx -> deepest(ctx.getSource())))
            .then(Commands.literal("skip")
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 2000))
                    .executes(ctx -> skip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
            .then(Commands.literal("section")
                .executes(ctx -> listSections(ctx.getSource()))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Sections.ids(), b))
                    .then(Commands.literal("lock").executes(ctx -> lock(ctx, true)))
                    .then(Commands.literal("unlock").executes(ctx -> lock(ctx, false)))))
            .then(Commands.literal("wallet")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> showWallet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                    .then(Commands.literal("set")
                        .then(Commands.argument("what", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"battery", "bandage", "adrenaline", "charm", "radar", "scanner", "coins"}, b))
                            .then(Commands.argument("n", IntegerArgumentType.integer(0))
                                .executes(ctx -> setWallet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "what"), IntegerArgumentType.getInteger(ctx, "n"))))))))
            .then(Commands.literal("record")
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RoomsCommands.recordNames(c.getSource()), b))
                        .executes(ctx -> resetRecord(ctx.getSource(), StringArgumentType.getString(ctx, "player"))))))
            .then(Commands.literal("run")
                .then(Commands.literal("reset")
                    .executes(ctx -> resetRun(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> resetRun(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
            .then(Commands.literal("give")
                .then(Commands.literal("flashlight").executes(ctx -> {
                    ctx.getSource().getPlayerOrException().getInventory().add(com.example.roomsmod.item.FlashlightHandler.newFlashlight(com.example.roomsmod.item.FlashlightItem.BATTERY_TICKS));
                    ctx.getSource().sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(net.minecraft.network.chat.Component.translatable("item.roomsmod.flashlight")), false);
                    return 1;
                }))
                .then(Commands.literal("bandage").executes(ctx -> giveBandage(ctx.getSource(), 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveBandage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("battery").executes(ctx -> giveBattery(ctx.getSource(), 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveBattery(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("adrenaline").executes(ctx -> giveExtra(ctx.getSource(), "adrenaline", 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveExtra(ctx.getSource(), "adrenaline", IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("charm").executes(ctx -> giveExtra(ctx.getSource(), "charm", 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveExtra(ctx.getSource(), "charm", IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("radar").executes(ctx -> giveExtra(ctx.getSource(), "radar", 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveExtra(ctx.getSource(), "radar", IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("scanner").executes(ctx -> giveExtra(ctx.getSource(), "scanner", 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> giveExtra(ctx.getSource(), "scanner", IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("coins")
                    .then(Commands.argument("count", IntegerArgumentType.integer(-1000000, 1000000)).executes(ctx -> giveCoins(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))))
            .then(Commands.literal("tp")
                .then(Commands.argument("section", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Sections.ids(), b))
                    .then(Commands.argument("room", IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> tp(ctx.getSource(), StringArgumentType.getString(ctx, "section"), IntegerArgumentType.getInteger(ctx, "room")))))));
    }

    private static ServerLevel rooms(CommandSourceStack src) { return src.getServer().getLevel(RoomsCommands.ROOMS_DIMENSION); }

    private static int listSections(CommandSourceStack src) {
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        RoomsState rs = RoomsState.get(lv);
        StringBuilder sb = new StringBuilder("セクション: ");
        for (String id : Sections.ids())
            sb.append(id.toUpperCase(Locale.ROOT)).append(rs.isSectionLocked(id) ? "[LOCK] " : "[OPEN] ");
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info(sb.toString().trim()), false);
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> ctx, boolean locked) {
        CommandSourceStack src = ctx.getSource();
        String id = Sections.canonical(StringArgumentType.getString(ctx, "id"));
        if (Sections.find(id) == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("未登録のセクション: " + id)); return 0; }
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        RoomsState rs = RoomsState.get(lv);
        rs.setSectionLocked(id, locked);
        LobbyPanels.onAccessChanged(lv);
        RoomsDebug.info("admin: section {} {} by {}", id, locked ? "locked" : "unlocked", src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("セクション " + id.toUpperCase(Locale.ROOT) + " を" + (locked ? "ロック" : "解放") + "した"), false);
        return 1;
    }

    private static int showWallet(CommandSourceStack src, ServerPlayer p) {
        Wallet w = Wallet.of(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info(p.getGameProfile().getName() + ": 電池 " + Wallet.label(w.battery, Wallet.MAX_BATTERY)
                + "  包帯 " + Wallet.label(w.bandage, Wallet.MAX_BANDAGE) + "  アドレナリン " + Wallet.label(w.adrenaline, Wallet.MAX_ADRENALINE) + "  お守り " + Wallet.label(w.charm, Wallet.MAX_CHARM) + "  レーダー " + Wallet.label(w.radar, Wallet.MAX_RADAR) + "  スキャナー " + Wallet.label(w.scanner, Wallet.MAX_SCANNER) + "  コイン " + w.coins
                + "  周回 start=" + w.runStart + " furthest=" + w.runFurthest + " hit=" + w.runHit), false);
        return 1;
    }

    private static int setWallet(CommandSourceStack src, ServerPlayer p, String what, int n) {
        Wallet w = Wallet.of(p);
        switch (what.toLowerCase(Locale.ROOT)) {
            case "battery" -> w.battery = Math.min(n, Wallet.space(Wallet.MAX_BATTERY, 0));
            case "bandage" -> w.bandage = Math.min(n, Wallet.space(Wallet.MAX_BANDAGE, 0));
            case "adrenaline" -> w.adrenaline = Math.min(n, Wallet.space(Wallet.MAX_ADRENALINE, 0));
            case "charm" -> w.charm = Math.min(n, Wallet.space(Wallet.MAX_CHARM, 0));
            case "radar" -> w.radar = Math.min(n, Wallet.space(Wallet.MAX_RADAR, 0));
            case "scanner" -> w.scanner = Math.min(n, Wallet.space(Wallet.MAX_SCANNER, 0));
            case "coins", "coin" -> w.coins = n;
            default -> { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("battery / bandage / adrenaline / charm / radar / scanner / coins のどれか")); return 0; }
        }
        Wallet.sync(p);
        RoomsDebug.info("admin: wallet {} {}={} by {}", p.getGameProfile().getName(), what, n, src.getTextName());
        return showWallet(src, p);
    }

    private static int resetRun(CommandSourceStack src, ServerPlayer p) {
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        Wallet.of(p).resetRun(RoomsState.get(lv).epoch);
        RoomsDebug.info("admin: run reset {} by {}", p.getGameProfile().getName(), src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(p.getGameProfile().getName() + " の周回の記録を消した（コインなし）"), false);
        return 1;
    }

    private static int resetRecord(CommandSourceStack src, String name) {
        var e = RoomsCommands.findRecord(src, name);
        if (e == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(name + " の記録はない")); return 0; }
        com.example.roomsmod.economy.RoomsRecords.get(src.getServer()).remove(e.id);
        ServerPlayer online = src.getServer().getPlayerList().getPlayer(e.id);
        if (online != null) com.example.roomsmod.economy.RoomsRecords.forget(online);
        RoomsDebug.info("admin: record reset {} ({}) by {}", e.name, e.id, src.getTextName());
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(e.name + " の到達記録を消した"), false);
        return 1;
    }

    private static int tp(CommandSourceStack src, String sectionId, int room) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        String id = Sections.canonical(sectionId);
        if (Sections.find(id) == null) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("未登録のセクション: " + id)); return 0; }
        if (com.example.roomsmod.world.RoomsTravel.busy()) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("部屋を生成中。完了まで待機")); return 0; }
        RoomsState rs = RoomsState.get(lv);
        int max = Sections.length(id);
        if (room > max && Sections.ORDER.contains(id)) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err(id.toUpperCase(Locale.ROOT) + " は " + max + " 部屋まで")); return 0; }
        rs.setChain(id); // ロックは無視（テスト用）
        Wallet.of(p).resetRun(Integer.MIN_VALUE); // 今の周回は精算しない
        com.example.roomsmod.economy.RoomsRun.adminTeleport(p); // この周回はコインなし
        RoomsDebug.info("admin: tp {} -> section {} room {}", p.getGameProfile().getName(), id, room);
        return RoomsCommands.goToRoom(p, room) ? 1 : 0;
    }

    /**
     * /rooms admin skip &lt;n&gt;：今いる部屋から n 部屋先へ（セクションの境目もそのまま越える。今の進行表のまま、ロックは見ない）。
     * もう生成済みの部屋ならそこへワープ、まだなら /rooms admin to と同じくロビーから作り直してワープ。管理ワープなのでこの周回は記録・コインなし。
     */
    private static int skip(CommandSourceStack src, int n) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        if (com.example.roomsmod.world.RoomsTravel.busy()) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("部屋を生成中。完了まで待機")); return 0; }
        RoomsState rs = RoomsState.get(lv);
        int cur = 0;
        if (p.level() == lv) {
            int i = com.example.roomsmod.event.RoomTracker.roomIndexAt(rs, p.blockPosition());
            cur = i >= 0 ? i : 0;
            if (rs.abyss != null && rs.abyss.contains(p.position())) cur = com.example.roomsmod.world.AbyssRooms.roomOf(p, Math.max(cur, rs.currentRoom));
        }
        int target = cur + n;
        String from = cur <= 0 ? "LOBBY" : Sections.formatRoom(rs, cur), to = Sections.formatRoom(rs, target);
        Wallet.of(p).resetRun(Integer.MIN_VALUE); // 今の周回は精算しない
        com.example.roomsmod.economy.RoomsRun.adminTeleport(p); // 管理ワープ：記録・コインなし
        RoomsDebug.info("admin: skip {} +{}: {} ({}) -> {} ({})", p.getGameProfile().getName(), n, cur, from, target, to);
        if (target < rs.roomPaths.size()) {
            net.minecraft.world.phys.Vec3 v = com.example.roomsmod.world.SafeSpot.find(lv, rs, target);
            if (v != null) {
                p.teleportTo(lv, v.x, v.y, v.z, p.getYRot(), p.getXRot());
                src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(from + " → " + to + "（" + n + " 部屋先、生成済み）"), false);
                return 1;
            }
        }
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(from + " → " + to + "（" + n + " 部屋先）を生成してワープ"), false);
        return RoomsCommands.goToRoom(p, target) ? 1 : 0;
    }

    /** 所持数（Wallet）へ。上限を超えた分は渡さない。 */
    private static int giveBandage(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = src.getPlayerOrException();
        var w = com.example.roomsmod.economy.Wallet.of(p);
        int add = Math.min(n, com.example.roomsmod.economy.Wallet.space(com.example.roomsmod.economy.Wallet.MAX_BANDAGE, w.bandage));
        w.bandage += Math.max(0, add);
        com.example.roomsmod.economy.Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(net.minecraft.network.chat.Component.translatable("item.roomsmod.bandage").append(" +" + Math.max(0, add) + " (" + com.example.roomsmod.economy.Wallet.label(w.bandage, com.example.roomsmod.economy.Wallet.MAX_BANDAGE) + ")")), false);
        return Math.max(0, add);
    }

    private static int giveBattery(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = src.getPlayerOrException();
        var w = com.example.roomsmod.economy.Wallet.of(p);
        int add = Math.min(n, com.example.roomsmod.economy.Wallet.space(com.example.roomsmod.economy.Wallet.MAX_BATTERY, w.battery));
        w.battery += Math.max(0, add);
        com.example.roomsmod.economy.Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(net.minecraft.network.chat.Component.translatable("item.roomsmod.battery").append(" +" + Math.max(0, add) + " (" + com.example.roomsmod.economy.Wallet.label(w.battery, com.example.roomsmod.economy.Wallet.MAX_BATTERY) + ")")), false);
        return Math.max(0, add);
    }

    private static int giveCoins(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = src.getPlayerOrException();
        var w = com.example.roomsmod.economy.Wallet.of(p);
        w.coins = Math.max(0, w.coins + n);
        com.example.roomsmod.economy.Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok("COIN " + (n >= 0 ? "+" : "") + n + " → " + w.coins), false);
        return 1;
    }


    /** アドレナリン・お守り・レーダー・スキャナーを所持数へ（上限まで）。 */
    private static int giveExtra(CommandSourceStack src, String what, int n) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Wallet w = Wallet.of(p);
        int add;
        String label;
        switch (what) {
            case "adrenaline" -> { add = Math.min(n, Wallet.space(Wallet.MAX_ADRENALINE, w.adrenaline)); w.adrenaline += add; label = Wallet.label(w.adrenaline, Wallet.MAX_ADRENALINE); }
            case "charm" -> { add = Math.min(n, Wallet.space(Wallet.MAX_CHARM, w.charm)); w.charm += add; label = Wallet.label(w.charm, Wallet.MAX_CHARM); }
            case "radar" -> { add = Math.min(n, Wallet.space(Wallet.MAX_RADAR, w.radar)); w.radar += add; label = Wallet.label(w.radar, Wallet.MAX_RADAR); }
            default -> { add = Math.min(n, Wallet.space(Wallet.MAX_SCANNER, w.scanner)); w.scanner += add; label = Wallet.label(w.scanner, Wallet.MAX_SCANNER); }
        }
        Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(Component.translatable("shop.roomsmod." + what).append(" +" + add + " (" + label + ")")), false);
        return add;
    }

    private static int deepest(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel lv = rooms(src);
        if (lv == null) return 0;
        Wallet w = Wallet.of(p);
        if (w.deepSection.isEmpty() || w.deepLocal < 1) { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("記録がない（まだ部屋に入っていない）")); return 0; }
        RoomsState rs = RoomsState.get(lv);
        String label = Sections.byId(w.deepSection).formatRoom(w.deepLocal);
        int g = Sections.globalRoom(rs, w.deepSection, w.deepLocal);
        if (g >= 1 && g < rs.roomPaths.size()) {
            net.minecraft.world.phys.Vec3 v = com.example.roomsmod.world.SafeSpot.find(lv, rs, g);
            if (v != null) {
                com.example.roomsmod.economy.RoomsRun.adminTeleport(p); // この周回はコインなし（開始はワープ先）
                p.teleportTo(lv, v.x, v.y, v.z, p.getYRot(), p.getXRot());
                RoomsDebug.info("admin: deepest {} -> {} (room {}, existing)", p.getGameProfile().getName(), label, g);
                src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(label + " へワープした（生成済み）"), false);
                return 1;
            }
        }
        RoomsDebug.info("admin: deepest {} -> {} (rebuild)", p.getGameProfile().getName(), label);
        return tp(src, w.deepSection, w.deepLocal);
    }
}
