package com.example.roomsmod.economy;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.item.FlashlightHandler;
import com.example.roomsmod.item.FlashlightItem;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** ロビーの売店（右の壁 x=0、LobbyPanels が置く）。値段はここだけ。 */
public final class Shop {
    private Shop() {}

    public enum Product {
        BATTERY("battery", 15), BANDAGE("bandage", 25), FLASHLIGHT("flashlight", 40);
        public final String key; public final int price;
        Product(String key, int price) { this.key = key; this.price = price; }
    }

    public static void buy(ServerPlayer p, Product item) {
        Wallet w = Wallet.of(p);
        Component name = Component.translatable("shop.roomsmod." + item.key);
        String why = switch (item) {
            case BATTERY -> Wallet.full(Wallet.MAX_BATTERY, w.battery) ? "message.roomsmod.shop.full" : null;
            case BANDAGE -> Wallet.full(Wallet.MAX_BANDAGE, w.bandage) ? "message.roomsmod.shop.full" : null;
            case FLASHLIGHT -> p.getInventory().countItem(RoomsMod.FLASHLIGHT.get()) > 0 ? "message.roomsmod.shop.have_flashlight" : null;
        };
        if (why == null && w.coins < item.price) why = "message.roomsmod.shop.no_coins";
        if (why != null) {
            p.displayClientMessage(Component.translatable(why, name, item.price, w.coins).withStyle(ChatFormatting.RED), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.5f, 1.0f);
            return;
        }
        w.coins -= item.price;
        switch (item) {
            case BATTERY -> w.battery++;
            case BANDAGE -> w.bandage++;
            case FLASHLIGHT -> {
                ItemStack s = FlashlightHandler.newFlashlight(FlashlightItem.BATTERY_TICKS);
                if (!p.getInventory().add(s)) p.drop(s, false);
            }
        }
        Wallet.sync(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.3f);
        p.displayClientMessage(Component.translatable("message.roomsmod.shop.bought", name, item.price, w.coins).withStyle(ChatFormatting.GOLD), true);
        RoomsDebug.log("shop: {} bought {} for {} -> coins={} battery={} bandage={}", p.getGameProfile().getName(), item.key, item.price, w.coins, w.battery, w.bandage);
    }
}
