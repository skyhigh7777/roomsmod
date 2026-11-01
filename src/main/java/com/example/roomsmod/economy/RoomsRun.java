package com.example.roomsmod.economy;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 周回の記録とロビーへの帰還・コインの精算。
 * <ul>
 *   <li>周回 = RoomsState.epoch（ロビーの作り直しごと）。最初に入った部屋（部屋1ならロビーから＝0）を開始、いちばん先の部屋を記録。</li>
 *   <li>ホットバー9番（スロット8）に「ロビーへ帰る」を常に置く（部屋ディメンションの中だけ。動かす・捨てるとすぐ戻る、死んでも落ちない）。</li>
 *   <li>帰れる条件：今の部屋が開始から CoinFormula.MIN_ROOMS_TO_RETURN 部屋以上先（セクションをまたいでも通算）、かつ近く（48 ブロック以内か同じ部屋）にこの MOD の敵がいない。</li>
 *   <li>精算（CoinFormula）：帰還＝生還、死亡＝DEATH_MUL、ほかの人の死亡などで周回が作り直された時も DEATH_MUL（生還ボーナスなし）。</li>
 * </ul>
 */
public final class RoomsRun {
    private RoomsRun() {}

    public static final int HOTBAR_SLOT = 8;

    /** サーバー → クライアント：左下の部屋番号 HUD（label 空 = 表示しない）。 */
    public record RoomPayload(String label, boolean hard) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<RoomPayload> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "room_hud"));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, RoomPayload> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, RoomPayload::label, net.minecraft.network.codec.ByteBufCodecs.BOOL, RoomPayload::hard, RoomPayload::new);
        @Override public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return TYPE; }
    }
    private static final java.util.Map<java.util.UUID, String> HUD_SENT = new java.util.concurrent.ConcurrentHashMap<>();

    /** 今いる部屋の表示（部屋の間の通路などで部屋が取れない時は前の表示のまま）。変わった時だけ送る。 */
    static void sendRoomHud(ServerPlayer p, RoomsState rs, int idx) {
        String prev = HUD_SENT.get(p.getUUID());
        String label;
        if (!inRooms(p)) label = "";
        else if (idx == 0) label = "LOBBY";
        else if (idx > 0) label = Sections.formatRoom(rs, idx);
        else { if (prev != null) return; label = ""; }
        String key = label + "|" + (rs != null && rs.hardMode);
        if (key.equals(prev)) return;
        HUD_SENT.put(p.getUUID(), key);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new RoomPayload(label, rs != null && rs.hardMode));
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { HUD_SENT.remove(e.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onDim(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) { HUD_SENT.remove(e.getEntity().getUUID()); }
    public static final double ENEMY_RADIUS = 48;

    static boolean inRooms(ServerPlayer p) { return p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION); }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (inRooms(p) && p.isAlive() && !p.isSpectator()) track(p); // 部屋の移動は毎 tick 見る（隣の部屋へ歩いたかの判定）
        if (p.tickCount % 5 != 0) return;
        enforceItem(p);
        if (!inRooms(p)) { if (HUD_SENT.containsKey(p.getUUID()) && !"|false".equals(HUD_SENT.get(p.getUUID()))) sendRoomHud(p, null, -1); return; }
        RoomsState rs = RoomsState.get(p.serverLevel());
        int hudIdx = playerRoom(p, rs);
        sendRoomHud(p, rs, hudIdx);
        if (hudIdx == 0 && p.tickCount % 40 == 0) com.example.roomsmod.world.LobbyPanels.ensure(p.serverLevel()); // 既存ワールドのロビーも今のパネル・売店に
        if (!p.isAlive() || p.isSpectator()) return;
        Wallet w = Wallet.of(p);
        if (w.runEpoch != rs.epoch) {
            if (w.runEpoch != Integer.MIN_VALUE && w.runStart >= 0 && w.runFurthest > w.runStart)
                settle(p, rs, false, "run reset");
            w.resetRun(rs.epoch);
        }
        int idx = w.runLast;
        if (idx < 1) return;
        // いちばん深い部屋の記録（/rooms admin deepest）
        String sec = Sections.canonical(Sections.forRoom(rs, idx).id());
        int local = Sections.localRoom(rs, idx), depth = Sections.absoluteDepth(sec, local);
        if (depth > 0 && depth > (w.deepSection.isEmpty() ? 0 : Sections.absoluteDepth(w.deepSection, w.deepLocal))) {
            w.deepSection = sec; w.deepLocal = local;
        }
    }

    /** 1 tick にこの部屋数までの移動は歩いた（落ちた）ものとして数える。これより大きい移動はワープ＝開始をそこにし直す。 */
    public static final int WALK_TOLERANCE = 2;

    /** プレイヤーの今の部屋。A の縦穴は全部屋が同じ箱なので AbyssRooms の部屋（ループ込み）を使う。 */
    public static int playerRoom(ServerPlayer p, RoomsState rs) {
        int idx = RoomTracker.roomIndexAt(rs, p.blockPosition());
        if (idx >= 0 && rs.abyss != null && idx >= rs.abyss.entryRoom && Sections.forRoom(rs, idx) == Sections.A)
            idx = com.example.roomsmod.world.AbyssRooms.roomOf(p, idx);
        return idx;
    }


    /**
     * 周回の進み具合：実際に部屋から部屋へ移った分だけ数える（生成した範囲やワープは数えない）。
     * <ul>
     *   <li>最初に見た部屋が開始（ロビーなら 0 = ロビーの扉から）。</li>
     *   <li>前の部屋から WALK_TOLERANCE 以内の移動は歩き。いちばん先（runFurthest）を更新。</li>
     *   <li>それより大きい移動（GO・/rooms admin to・tp・deepest・最奥パネル・ほかのワープ）は、そこを新しい開始にする（進んだ部屋数 0 から）。被弾の記録はそのまま。</li>
     * </ul>
     */
    static void track(ServerPlayer p) {
        RoomsState rs = RoomsState.get(p.serverLevel());
        Wallet w = Wallet.of(p);
        if (w.runEpoch != rs.epoch) return; // 周回の切り替えは 5 tick ごとの処理で（精算を先に）
        int idx = playerRoom(p, rs);
        if (idx < 0) return; // 部屋の外（境目など）
        if (w.runStart < 0 || w.runLast < 0) { w.runStart = idx; w.runFurthest = idx; w.runLast = idx; return; }
        if (idx == w.runLast) return;
        if (Math.abs(idx - w.runLast) > WALK_TOLERANCE) {
            RoomsDebug.log("run jump player={} {} -> {}: start reset (was start={} furthest={})", p.getGameProfile().getName(), w.runLast, idx, w.runStart, w.runFurthest);
            w.runStart = idx; w.runFurthest = idx; w.runLast = idx;
            return;
        }
        w.runLast = idx;
        if (idx > w.runFurthest) w.runFurthest = idx;
    }

    /** 管理ワープ（/rooms admin tp・to・deepest）：この周回はコインなし。 */
    public static void adminTeleport(ServerPlayer p) {
        Wallet w = Wallet.of(p);
        w.runNoPay = true;
        RoomsDebug.log("run marked no-pay (admin teleport) player={}", p.getGameProfile().getName());
    }

    /** ロビーの GO（ふつうのワープ）：管理ワープのコインなしの印を消す（開始はワープ先＝track のジャンプ判定）。 */
    public static void panelTeleport(ServerPlayer p) { Wallet.of(p).runNoPay = false; }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getNewDamage() > 0 && inRooms(p)) {
            markHit(p);
        }
    }

    /** 被弾の印（NO HIT ボーナスを消す）。クリエイティブの直接ダメージからも呼ぶ。 */
    public static void markHit(ServerPlayer p) {
        if (!inRooms(p)) return;
        Wallet w = Wallet.of(p);
        if (!w.runHit && w.runStart >= 0) RoomsDebug.log("run: {} was hit (no-hit bonus lost)", p.getGameProfile().getName());
        w.runHit = true;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled() || !inRooms(p)) return;
        Wallet w = Wallet.of(p);
        RoomsState rs = RoomsState.get(p.serverLevel());
        if (w.runEpoch == rs.epoch && w.runStart >= 0 && w.runFurthest > w.runStart) settle(p, rs, false, "death");
        w.resetRun(rs.epoch);
        w.runNoPay = false;
    }

    /** 精算してコインを渡す。 */
    static CoinFormula.Result settle(ServerPlayer p, RoomsState rs, boolean survived, String why) {
        Wallet w = Wallet.of(p);
        int bat = survived ? w.battery : 0, band = survived ? w.bandage : 0;
        CoinFormula.Result r = CoinFormula.compute(rs, w.runStart, w.runFurthest, !w.runHit, survived, rs.hardMode, bat, band);
        boolean noPay = w.runNoPay;
        if (noPay) { bat = 0; band = 0; r = new CoinFormula.Result(r.rooms(), r.base(), r.multiplier(), 0, 0); } // 管理ワープの周回はコインなし（換金もしない）
        w.runNoPay = false;
        if (survived && !noPay) { w.battery = 0; w.bandage = 0; } // 生還時は残りの電池・包帯をコインに換える（消費）
        w.coins += r.coins();
        Wallet.sync(p);
        String from = w.runStart <= 0 ? "LOBBY" : Sections.formatRoom(rs, w.runStart), to = Sections.formatRoom(rs, w.runFurthest);
        p.sendSystemMessage(Component.translatable(survived ? "message.roomsmod.run.returned" : "message.roomsmod.run.ended",
                from, to, r.rooms(), r.coins()).withStyle(ChatFormatting.GOLD));
        if (noPay) p.sendSystemMessage(Component.literal("  ADMIN TELEPORT: no coins").withStyle(ChatFormatting.GRAY));
        else if (survived) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (!w.runHit) parts.add("NO HIT +" + Math.round(CoinFormula.NO_HIT_BONUS * 100) + "%");
            parts.add("SURVIVED +" + Math.round(CoinFormula.SURVIVE_BONUS * 100) + "%");
            if (rs.hardMode) parts.add("HARD x" + CoinFormula.HARD_MUL);
            if (bat + band > 0) parts.add("ITEMS +" + String.format(java.util.Locale.ROOT, "%.2f", r.leftover()) + " (battery " + bat + ", bandage " + band + ")");
            p.sendSystemMessage(Component.literal("  " + String.join(" / ", parts)).withStyle(ChatFormatting.GRAY));
        }
        RoomsDebug.log("run settled ({}) player={} no_pay={} start={} furthest={} rooms={} base={} mul={} leftover={} converted_battery={} converted_bandage={} hit={} hard={} -> +{} coins (total {})",
                why, p.getGameProfile().getName(), noPay, w.runStart, w.runFurthest, r.rooms(), String.format("%.2f", r.base()), String.format("%.2f", r.multiplier()), String.format("%.2f", r.leftover()), bat, band,
                w.runHit, rs.hardMode, r.coins(), w.coins);
        return r;
    }

    // ===================== やり直し（/rooms restart） =====================

    /** /rooms restart を使える範囲：周回の開始部屋からこの部屋数先まで（いちばん先に進んだ部屋で判定）。 */
    public static final int RESTART_MAX_ROOMS = 5;

    /**
     * /rooms restart（誰でも）。部屋ディメンションの外からはロビーへ送るだけ（以前の /rooms enter と同じ）。
     * 周回中は開始から RESTART_MAX_ROOMS 部屋以内なら、コインなし・換金なし（所持数はそのまま）で周回を終えてロビーへ（ロビーを作り直す）。
     */
    public static boolean restart(ServerPlayer p) {
        if (!inRooms(p)) {
            boolean ok = RoomsCommands.sendToLobby(p);
            p.sendSystemMessage(ok ? com.example.roomsmod.util.RoomsMsg.ok("ロビーへ移動した") : com.example.roomsmod.util.RoomsMsg.err("ディメンションが見つからない"));
            return ok;
        }
        if (com.example.roomsmod.world.RoomsTravel.busy()) { p.sendSystemMessage(com.example.roomsmod.util.RoomsMsg.err("部屋を生成中。完了まで待機")); return false; }
        RoomsState rs = RoomsState.get(p.serverLevel());
        Wallet w = Wallet.of(p);
        int idx = playerRoom(p, rs);
        if (w.runEpoch == rs.epoch && w.runStart >= 0) {
            int traveled = Math.max(w.runFurthest, idx) - w.runStart;
            if (traveled > RESTART_MAX_ROOMS) {
                p.sendSystemMessage(com.example.roomsmod.util.RoomsMsg.err("やり直しは開始から " + RESTART_MAX_ROOMS + " 部屋以内のみ（現在 " + traveled + " 部屋）"));
                return false;
            }
        }
        RoomsDebug.log("run restart player={} start={} furthest={} room={} (no coins)", p.getGameProfile().getName(), w.runStart, w.runFurthest, idx);
        w.resetRun(Integer.MIN_VALUE); // 精算しない
        w.runNoPay = false;
        boolean ok = RoomsCommands.sendToLobby(p);
        if (ok) p.sendSystemMessage(com.example.roomsmod.util.RoomsMsg.ok("周回をやり直した（コインなし）"));
        return ok;
    }

    // ===================== 帰還 =====================

    /** 「ロビーへ帰る」を使った（サーバー）。 */
    public static void tryReturn(ServerPlayer p) {
        if (!inRooms(p)) return;
        ServerLevel level = p.serverLevel();
        RoomsState rs = RoomsState.get(level);
        Wallet w = Wallet.of(p);
        if (com.example.roomsmod.world.RoomsTravel.busy()) { deny(p, Component.translatable("message.roomsmod.return.busy")); return; }
        int idx = playerRoom(p, rs);
        if (idx < 1 || w.runEpoch != rs.epoch || w.runStart < 0) { deny(p, Component.translatable("message.roomsmod.return.lobby")); return; }
        int need = w.runStart + CoinFormula.MIN_ROOMS_TO_RETURN - idx;
        if (need > 0) { deny(p, Component.translatable("message.roomsmod.return.too_early", need)); return; }
        Entity enemy = nearbyEnemy(level, rs, p, idx);
        if (enemy != null) {
            deny(p, Component.translatable("message.roomsmod.return.enemy"));
            RoomsDebug.log("return denied for {}: enemy {} at {}", p.getGameProfile().getName(), enemy.getType(), enemy.blockPosition());
            return;
        }
        if (w.runFurthest < idx) w.runFurthest = idx;
        settle(p, rs, true, "return");
        w.resetRun(rs.epoch);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 0.8f);
        RoomsCommands.sendToLobby(p); // ロビーを作り直す（周回の終わり）
    }

    private static void deny(ServerPlayer p, Component why) {
        p.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    /** 近く（ENEMY_RADIUS 以内）か同じ部屋にいる、この MOD の動いている敵。 */
    static Entity nearbyEnemy(ServerLevel level, RoomsState rs, ServerPlayer p, int room) {
        for (Entity e : level.getAllEntities()) {
            if (e.getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER || e.isRemoved() || !e.isAlive()) continue;
            if (!RoomsMod.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) continue;
            if (!com.example.roomsmod.entity.v.VEntity.isActive(e)) continue;
            if (e.distanceToSqr(p) <= ENEMY_RADIUS * ENEMY_RADIUS || RoomTracker.roomIndexAt(rs, e.blockPosition()) == room) return e;
        }
        return null;
    }

    // ===================== ホットバー9番の固定アイテム =====================

    public static boolean isReturnItem(ItemStack s) { return s.is(RoomsMod.LOBBY_RETURN.get()); }

    static void enforceItem(ServerPlayer p) {
        var inv = p.getInventory();
        boolean want = inRooms(p) && !p.isSpectator();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (isReturnItem(inv.getItem(i)) && (!want || i != HOTBAR_SLOT)) inv.setItem(i, ItemStack.EMPTY);
        if (isReturnItem(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        if (!want) return;
        ItemStack cur = inv.getItem(HOTBAR_SLOT);
        if (isReturnItem(cur)) return;
        if (!cur.isEmpty()) {
            inv.setItem(HOTBAR_SLOT, ItemStack.EMPTY);
            if (!inv.add(cur)) p.drop(cur, false); // 9番にあった物はほかの空きへ（なければ足元）
        }
        inv.setItem(HOTBAR_SLOT, new ItemStack(RoomsMod.LOBBY_RETURN.get()));
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent e) {
        if (isReturnItem(e.getEntity().getItem())) { e.getEntity().discard(); e.setCanceled(true); } // 捨てられない（次の tick で 9 番に戻る）
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        e.getDrops().removeIf((ItemEntity it) -> isReturnItem(it.getItem()));
    }
}
