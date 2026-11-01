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
 *   <li>section [id lock|unlock]：セクションの利用可否（既定は PLACEHOLDER だけロック）。ロック中はロビーのパネル・/rooms section で選べない。</li>
 *   <li>wallet &lt;player&gt; [set battery|bandage|coins &lt;n&gt;]：所持数を見る・変える。</li>
 *   <li>run reset [player]：周回の記録を消す（コインは出ない）。</li>
 *   <li>give flashlight | bandage [n] | battery [n] | coins &lt;n&gt;：自分に渡す（電池・包帯・コインは所持数へ、上限まで）。以前の OP 用 /rooms give から移動。</li>
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

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
            .requires(AdminCommands::isOwner)
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
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"battery", "bandage", "coins"}, b))
                            .then(Commands.argument("n", IntegerArgumentType.integer(0))
                                .executes(ctx -> setWallet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "what"), IntegerArgumentType.getInteger(ctx, "n"))))))))
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
                .then(Commands.literal("coins")
                    .then(Commands.argument("count", IntegerArgumentType.integer(-1000000, 1000000)).executes(ctx -> giveCoins(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))))
            .then(Commands.literal("tp")
                .then(Commands.argument("section", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Sections.ids(), b))
                    .then(Commands.argument("room", IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> tp(ctx.getSource(), StringArgumentType.getString(ctx, "section"), IntegerArgumentType.getInteger(ctx, "room"))))));
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
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.info(p.getGameProfile().getName() + ": 電池 " + w.battery + "/" + Wallet.MAX_BATTERY
                + "  包帯 " + w.bandage + "/" + Wallet.MAX_BANDAGE + "  コイン " + w.coins
                + "  周回 start=" + w.runStart + " furthest=" + w.runFurthest + " hit=" + w.runHit), false);
        return 1;
    }

    private static int setWallet(CommandSourceStack src, ServerPlayer p, String what, int n) {
        Wallet w = Wallet.of(p);
        switch (what.toLowerCase(Locale.ROOT)) {
            case "battery" -> w.battery = Math.min(n, Wallet.MAX_BATTERY);
            case "bandage" -> w.bandage = Math.min(n, Wallet.MAX_BANDAGE);
            case "coins", "coin" -> w.coins = n;
            default -> { src.sendFailure(com.example.roomsmod.util.RoomsMsg.err("battery / bandage / coins のどれか")); return 0; }
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
        Wallet.of(p).resetRun(Integer.MIN_VALUE); // テストのワープでコインが出ないように
        RoomsDebug.info("admin: tp {} -> section {} room {}", p.getGameProfile().getName(), id, room);
        return RoomsCommands.goToRoom(p, room) ? 1 : 0;
    }

    /** 所持数（Wallet）へ。上限を超えた分は渡さない。 */
    private static int giveBandage(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = src.getPlayerOrException();
        var w = com.example.roomsmod.economy.Wallet.of(p);
        int add = Math.min(n, com.example.roomsmod.economy.Wallet.MAX_BANDAGE - w.bandage);
        w.bandage += Math.max(0, add);
        com.example.roomsmod.economy.Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(net.minecraft.network.chat.Component.translatable("item.roomsmod.bandage").append(" +" + Math.max(0, add) + " (" + w.bandage + "/" + com.example.roomsmod.economy.Wallet.MAX_BANDAGE + ")")), false);
        return Math.max(0, add);
    }

    private static int giveBattery(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = src.getPlayerOrException();
        var w = com.example.roomsmod.economy.Wallet.of(p);
        int add = Math.min(n, com.example.roomsmod.economy.Wallet.MAX_BATTERY - w.battery);
        w.battery += Math.max(0, add);
        com.example.roomsmod.economy.Wallet.sync(p);
        src.sendSuccess(() -> com.example.roomsmod.util.RoomsMsg.ok(net.minecraft.network.chat.Component.translatable("item.roomsmod.battery").append(" +" + Math.max(0, add) + " (" + w.battery + "/" + com.example.roomsmod.economy.Wallet.MAX_BATTERY + ")")), false);
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

}
