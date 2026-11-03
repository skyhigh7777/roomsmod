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
 * 所持数 HUD（画面右下、電池・包帯・アドレナリン・お守り・コイン）と、包帯キー（既定 H、押し続けて巻く）・アドレナリンキー（既定 J）。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class WalletClient {
    private WalletClient() {}

    public static final KeyMapping BANDAGE_KEY = new KeyMapping("key.roomsmod.bandage", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.roomsmod");
    /** アドレナリン（既定 J、押した時に1回）。 */
    public static final KeyMapping ADRENALINE_KEY = new KeyMapping("key.roomsmod.adrenaline", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.roomsmod");
    private static boolean wasDown;

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) { e.register(BANDAGE_KEY); e.register(ADRENALINE_KEY); }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.player != null && mc.screen == null && BANDAGE_KEY.isDown();
        if (down != wasDown && mc.getConnection() != null) PacketDistributor.sendToServer(new Wallet.BandagePayload(down));
        wasDown = down;
        while (BANDAGE_KEY.consumeClick()) { /* 押しっぱなしで判定するのでクリックは捨てる */ }
        boolean adr = false;
        while (ADRENALINE_KEY.consumeClick()) adr = true;
        if (adr && mc.player != null && mc.screen == null && mc.getConnection() != null) PacketDistributor.sendToServer(new Wallet.AdrenalinePayload());
        if (Wallet.clientAdrenalineTicks > 0) Wallet.clientAdrenalineTicks--;
        if (Wallet.clientAdrenalineCooldown > 0) Wallet.clientAdrenalineCooldown--;
        if (Wallet.clientCharmProtect > 0) Wallet.clientCharmProtect--;
    }

    private static ItemStack battery, bandage;
    private static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET), ADRENALINE = new ItemStack(Items.SUGAR), CHARM = new ItemStack(Items.TOTEM_OF_UNDYING),
            RADAR = new ItemStack(Items.COMPASS), SCANNER = new ItemStack(Items.SPYGLASS);
    private static final int WHITE = 0xFFFFFFFF, FULL = 0xFFE0A040;

    /** 画面右下に縦に並べる（ホットバーと重ならない）。下から コイン・スキャナー・レーダー・お守り・アドレナリン・包帯・電池。 */
    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Post e) {
        if (!e.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        if (!mc.level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        if (battery == null) { battery = new ItemStack(RoomsMod.BATTERY.get()); bandage = new ItemStack(RoomsMod.BANDAGE.get()); }
        GuiGraphics g = e.getGuiGraphics();
        int right = g.guiWidth() - 4, y = g.guiHeight() - 22;
        String adr = Wallet.label(Wallet.clientAdrenaline, Wallet.MAX_ADRENALINE);
        int adrColor = Wallet.full(Wallet.MAX_ADRENALINE, Wallet.clientAdrenaline) ? FULL : WHITE;
        if (Wallet.clientAdrenalineTicks > 0) { adr += "  " + (Wallet.clientAdrenalineTicks + 19) / 20 + "s"; adrColor = 0xFF60FF60; }
        else if (Wallet.clientAdrenalineCooldown > 0) { adr += "  CD " + (Wallet.clientAdrenalineCooldown + 19) / 20 + "s"; adrColor = 0xFFA0A0A0; }
        y = entry(g, mc, COIN, String.valueOf(Wallet.clientCoins), 0xFFFFD040, right, y);
        String charm = Wallet.label(Wallet.clientCharm, Wallet.MAX_CHARM);
        int charmColor = Wallet.full(Wallet.MAX_CHARM, Wallet.clientCharm) ? FULL : WHITE;
        if (Wallet.clientCharmProtect > 0) { charm += "  " + (Wallet.clientCharmProtect + 19) / 20 + "s"; charmColor = 0xFFFFE070; } // お守りの無敵の残り
        String radar = Wallet.label(Wallet.clientRadar, Wallet.MAX_RADAR);
        int radarColor = Wallet.full(Wallet.MAX_RADAR, Wallet.clientRadar) ? FULL : WHITE;
        if (GadgetsClient.radarTicksLeft() > 0) { radar += "  " + (GadgetsClient.radarTicksLeft() + 19) / 20 + "s"; radarColor = 0xFF7FE0FF; }
        y = entry(g, mc, SCANNER, Wallet.label(Wallet.clientScanner, Wallet.MAX_SCANNER), Wallet.full(Wallet.MAX_SCANNER, Wallet.clientScanner) ? FULL : WHITE, right, y);
        y = entry(g, mc, RADAR, radar, radarColor, right, y);
        y = entry(g, mc, CHARM, charm, charmColor, right, y);
        y = entry(g, mc, ADRENALINE, adr, adrColor, right, y);
        y = entry(g, mc, bandage, Wallet.label(Wallet.clientBandage, Wallet.MAX_BANDAGE), Wallet.full(Wallet.MAX_BANDAGE, Wallet.clientBandage) ? FULL : WHITE, right, y);
        entry(g, mc, battery, Wallet.label(Wallet.clientBattery, Wallet.MAX_BATTERY), Wallet.full(Wallet.MAX_BATTERY, Wallet.clientBattery) ? FULL : WHITE, right, y);
    }

    /** 右端 right に揃えて1行（高さ 18）。次の行の y（上）を返す。 */
    private static int entry(GuiGraphics g, Minecraft mc, ItemStack icon, String text, int color, int right, int y) {
        int w = 18 + mc.font.width(text) + 3, x = right - w;
        g.fill(x - 1, y, right, y + 18, 0x80000000);
        g.renderItem(icon, x, y + 1);
        g.drawString(mc.font, text, x + 18, y + 5, color, true);
        return y - 19;
    }
}
