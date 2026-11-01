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
    public static final double ENEMY_RADIUS = 48;

    static boolean inRooms(ServerPlayer p) { return p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION); }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        enforceItem(p);
        if (!inRooms(p) || !p.isAlive() || p.isSpectator()) return;
        RoomsState rs = RoomsState.get(p.serverLevel());
        Wallet w = Wallet.of(p);
        if (w.runEpoch != rs.epoch) {
            if (w.runEpoch != Integer.MIN_VALUE && w.runStart >= 0 && w.runFurthest > w.runStart)
                settle(p, rs, false, "run reset");
            w.resetRun(rs.epoch);
        }
        int idx = RoomTracker.roomIndexAt(rs, p.blockPosition());
        if (idx < 1) return;
        if (w.runStart < 0) w.runStart = idx == 1 ? 0 : idx; // 部屋1＝ロビーの扉から。それ以外（/rooms to・GO）はワープ先から
        if (idx > w.runFurthest) w.runFurthest = idx;
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getNewDamage() > 0 && inRooms(p)) {
            Wallet w = Wallet.of(p);
            if (!w.runHit && w.runStart >= 0) RoomsDebug.log("run: {} was hit (no-hit bonus lost)", p.getGameProfile().getName());
            w.runHit = true;
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled() || !inRooms(p)) return;
        Wallet w = Wallet.of(p);
        RoomsState rs = RoomsState.get(p.serverLevel());
        if (w.runEpoch == rs.epoch && w.runStart >= 0 && w.runFurthest > w.runStart) settle(p, rs, false, "death");
        w.resetRun(rs.epoch);
    }

    /** 精算してコインを渡す。 */
    static CoinFormula.Result settle(ServerPlayer p, RoomsState rs, boolean survived, String why) {
        Wallet w = Wallet.of(p);
        int bat = survived ? w.battery : 0, band = survived ? w.bandage : 0;
        CoinFormula.Result r = CoinFormula.compute(rs, w.runStart, w.runFurthest, !w.runHit, survived, rs.hardMode, bat, band);
        if (survived) { w.battery = 0; w.bandage = 0; } // 生還時は残りの電池・包帯をコインに換える（消費）
        w.coins += r.coins();
        Wallet.sync(p);
        String from = w.runStart <= 0 ? "LOBBY" : Sections.formatRoom(rs, w.runStart), to = Sections.formatRoom(rs, w.runFurthest);
        p.sendSystemMessage(Component.translatable(survived ? "message.roomsmod.run.returned" : "message.roomsmod.run.ended",
                from, to, r.rooms(), r.coins()).withStyle(ChatFormatting.GOLD));
        if (survived) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (!w.runHit) parts.add("NO HIT +" + Math.round(CoinFormula.NO_HIT_BONUS * 100) + "%");
            parts.add("SURVIVED +" + Math.round(CoinFormula.SURVIVE_BONUS * 100) + "%");
            if (rs.hardMode) parts.add("HARD x" + CoinFormula.HARD_MUL);
            if (bat + band > 0) parts.add("ITEMS +" + String.format(java.util.Locale.ROOT, "%.2f", r.leftover()) + " (battery " + bat + ", bandage " + band + ")");
            p.sendSystemMessage(Component.literal("  " + String.join(" / ", parts)).withStyle(ChatFormatting.GRAY));
        }
        RoomsDebug.log("run settled ({}) player={} start={} furthest={} rooms={} base={} mul={} leftover={} converted_battery={} converted_bandage={} hit={} hard={} -> +{} coins (total {})",
                why, p.getGameProfile().getName(), w.runStart, w.runFurthest, r.rooms(), String.format("%.2f", r.base()), String.format("%.2f", r.multiplier()), String.format("%.2f", r.leftover()), bat, band,
                w.runHit, rs.hardMode, r.coins(), w.coins);
        return r;
    }

    // ===================== 帰還 =====================

    /** 「ロビーへ帰る」を使った（サーバー）。 */
    public static void tryReturn(ServerPlayer p) {
        if (!inRooms(p)) return;
        ServerLevel level = p.serverLevel();
        RoomsState rs = RoomsState.get(level);
        Wallet w = Wallet.of(p);
        if (com.example.roomsmod.world.RoomsTravel.busy()) { deny(p, Component.translatable("message.roomsmod.return.busy")); return; }
        int idx = RoomTracker.roomIndexAt(rs, p.blockPosition());
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
