package com.example.roomsmod.item;

import com.example.roomsmod.RoomsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 懐中電灯。右クリックで ON/OFF（しゃがみ右クリックで電池を手動装填）。
 * 電池の残りは耐久値バー・ツールチップ・（ON 中は）アクションバーで表示。光と消費は FlashlightHandler。
 */
public class FlashlightItem extends Item {
    /** 電池1本の持ち時間（tick）。1200 = 1分。 */
    public static final int BATTERY_TICKS = 1200;

    public FlashlightItem(Properties p) { super(p); }

    public static FlashlightData data(ItemStack s) { return s.getOrDefault(RoomsMod.FLASHLIGHT_DATA.get(), FlashlightData.EMPTY); }
    public static void setData(ItemStack s, FlashlightData d) { s.set(RoomsMod.FLASHLIGHT_DATA.get(), d); }
    public static boolean isOn(ItemStack s) { return s.getItem() instanceof FlashlightItem && data(s).on(); }

    public static String time(int ticks) { int sec = (ticks + 19) / 20; return (sec / 60) + ":" + String.format("%02d", sec % 60); }

    /** 持ち物の電池を1本消費して満タンに。成功したら true。 */
    public static boolean loadBattery(Player p, ItemStack flashlight) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack b = inv.getItem(i);
            if (b.is(RoomsMod.BATTERY.get())) {
                b.shrink(1);
                setData(flashlight, data(flashlight).withCharge(BATTERY_TICKS));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6f, 1.6f);
                return true;
            }
        }
        return false;
    }

    public static void click(Player p, boolean on) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, on ? 1.6f : 1.2f);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(s);
        FlashlightData d = data(s);
        if (p.isShiftKeyDown()) {
            if (d.charge() >= BATTERY_TICKS) msg(p, "item.roomsmod.flashlight.full");
            else if (!loadBattery(p, s)) { msg(p, "item.roomsmod.flashlight.no_battery"); click(p, false); }
            return InteractionResultHolder.success(s);
        }
        if (d.on()) {
            setData(s, d.withOn(false));
            click(p, false);
        } else {
            if (d.charge() <= 0 && !loadBattery(p, s)) {
                msg(p, "item.roomsmod.flashlight.no_battery");
                click(p, false);
                return InteractionResultHolder.success(s);
            }
            setData(s, data(s).withOn(true));
            click(p, true);
        }
        com.example.roomsmod.util.RoomsDebug.log("flashlight {} player={} charge={}", data(s).on() ? "on" : "off", p.getGameProfile().getName(), data(s).charge());
        return InteractionResultHolder.success(s);
    }

    static void msg(Player p, String key) {
        if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key), true);
    }

    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13f * Mth.clamp(data(s).charge(), 0, BATTERY_TICKS) / BATTERY_TICKS); }
    @Override public int getBarColor(ItemStack s) {
        float f = Mth.clamp(data(s).charge() / (float) BATTERY_TICKS, 0f, 1f);
        return Mth.hsvToRgb(f / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem() || data(oldStack).on() != data(newStack).on();
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        FlashlightData d = data(s);
        lines.add(Component.translatable("item.roomsmod.flashlight.battery", time(d.charge())).withStyle(d.charge() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        lines.add(Component.translatable(d.on() ? "item.roomsmod.flashlight.state_on" : "item.roomsmod.flashlight.state_off").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("item.roomsmod.flashlight.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
