package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.economy.Wallet;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 電池・包帯・コインの所持数 HUD（ホットバーの右）と、包帯キー（既定 H、押し続けて巻く）。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class WalletClient {
    private WalletClient() {}

    public static final KeyMapping BANDAGE_KEY = new KeyMapping("key.roomsmod.bandage", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.roomsmod");
    private static boolean wasDown;

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) { e.register(BANDAGE_KEY); }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.player != null && mc.screen == null && BANDAGE_KEY.isDown();
        if (down != wasDown && mc.getConnection() != null) PacketDistributor.sendToServer(new Wallet.BandagePayload(down));
        wasDown = down;
        while (BANDAGE_KEY.consumeClick()) { /* 押しっぱなしで判定するのでクリックは捨てる */ }
    }

    private static ItemStack battery, bandage;
    private static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET);

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Post e) {
        if (!e.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        if (!mc.level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        if (battery == null) { battery = new ItemStack(RoomsMod.BATTERY.get()); bandage = new ItemStack(RoomsMod.BANDAGE.get()); }
        GuiGraphics g = e.getGuiGraphics();
        int x = g.guiWidth() / 2 + 91 + 8, y = g.guiHeight() - 20;
        if (!mc.player.getOffhandItem().isEmpty() && mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) x += 29; // オフハンド枠が右に出る時はその外へ
        x = entry(g, mc, battery, Wallet.clientBattery + "/" + Wallet.MAX_BATTERY, Wallet.clientBattery >= Wallet.MAX_BATTERY ? 0xFFE0A040 : 0xFFFFFFFF, x, y);
        x = entry(g, mc, bandage, Wallet.clientBandage + "/" + Wallet.MAX_BANDAGE, Wallet.clientBandage >= Wallet.MAX_BANDAGE ? 0xFFE0A040 : 0xFFFFFFFF, x, y);
        entry(g, mc, COIN, String.valueOf(Wallet.clientCoins), 0xFFFFD040, x, y);
    }

    private static int entry(GuiGraphics g, Minecraft mc, ItemStack icon, String text, int color, int x, int y) {
        g.fill(x - 1, y + 1, x + 18 + mc.font.width(text) + 3, y + 19, 0x80000000);
        g.renderItem(icon, x, y + 2);
        g.drawString(mc.font, text, x + 18, y + 7, color, true);
        return x + 18 + mc.font.width(text) + 8;
    }
}
