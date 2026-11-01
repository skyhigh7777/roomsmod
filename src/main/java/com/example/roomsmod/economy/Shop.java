package com.example.roomsmod.economy;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** ロビーの売店（右の壁 x=0、LobbyPanels が置く）。値段はここだけ。 */
public final class Shop {
    private Shop() {}

    public enum Product {
        BATTERY("battery", 15), BANDAGE("bandage", 25), ADRENALINE("adrenaline", 60), CHARM("charm", 300), RADAR("radar", 100), SCANNER("scanner", 200, 10);
        /** pack = 1回の購入で増える数（スキャナーは 10 個パック。上限を超える分は切り捨て、値段はそのまま）。 */
        public final String key; public final int price, pack;
        Product(String key, int price) { this(key, price, 1); }
        Product(String key, int price, int pack) { this.key = key; this.price = price; this.pack = pack; }
    }

    public static void buy(ServerPlayer p, Product item) {
        Wallet w = Wallet.of(p);
        Component name = Component.translatable("shop.roomsmod." + item.key);
        String why = switch (item) {
            case BATTERY -> Wallet.full(Wallet.MAX_BATTERY, w.battery) ? "message.roomsmod.shop.full" : null;
            case BANDAGE -> Wallet.full(Wallet.MAX_BANDAGE, w.bandage) ? "message.roomsmod.shop.full" : null;
            case ADRENALINE -> Wallet.full(Wallet.MAX_ADRENALINE, w.adrenaline) ? "message.roomsmod.shop.full" : null;
            case CHARM -> Wallet.full(Wallet.MAX_CHARM, w.charm) ? "message.roomsmod.shop.full" : null;
            case RADAR -> Wallet.full(Wallet.MAX_RADAR, w.radar) ? "message.roomsmod.shop.full" : null;
            case SCANNER -> Wallet.full(Wallet.MAX_SCANNER, w.scanner) ? "message.roomsmod.shop.full" : null;
        };
        if (why == null && w.coins < item.price) why = "message.roomsmod.shop.no_coins";
        if (why != null) {
            p.displayClientMessage(Component.translatable(why, name, item.price, w.coins).withStyle(ChatFormatting.RED), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.5f, 1.0f);
            return;
        }
        w.coins -= item.price;
        int before = w.scanner;
        switch (item) {
            case BATTERY -> w.battery++;
            case BANDAGE -> w.bandage++;
            case ADRENALINE -> w.adrenaline++;
            case CHARM -> w.charm++;
            case RADAR -> w.radar++;
            case SCANNER -> w.scanner = Math.min(w.scanner + item.pack, Wallet.MAX_SCANNER); // 上限 10 で頭打ち（1 個でも空きがあれば買える、値段は 200 のまま）
        }
        if (item.pack > 1) name = name.copy().append(" x" + (w.scanner - before));
        Wallet.sync(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.3f);
        p.displayClientMessage(Component.translatable("message.roomsmod.shop.bought", name, item.price, w.coins).withStyle(ChatFormatting.GOLD), true);
        RoomsDebug.log("shop: {} bought {} for {} -> coins={} battery={} bandage={} scanner={}", p.getGameProfile().getName(), item.key, item.price, w.coins, w.battery, w.bandage, w.scanner);
    }
}
