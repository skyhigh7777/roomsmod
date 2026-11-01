package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.economy.Gadgets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * レーダー（既定 K）・スキャナー（既定 N）のキーと表示。
 * レーダーはクライアント側だけで敵の発光フラグ（共有フラグ 6）を立てる＝本人にだけ壁越しの輪郭が見える。終わったら元の値に戻す。
 * スキャナーは画面右上（ステータス効果のアイコンの下）に一覧を SCAN_TICKS 表示。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class GadgetsClient {
    private GadgetsClient() {}

    public static final KeyMapping RADAR_KEY = new KeyMapping("key.roomsmod.radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.roomsmod");
    public static final KeyMapping SCANNER_KEY = new KeyMapping("key.roomsmod.scanner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.roomsmod");

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) { e.register(RADAR_KEY); e.register(SCANNER_KEY); }

    /** 発光させているエンティティ id → 元の発光フラグ。 */
    private static final Map<Integer, Boolean> GLOWING = new HashMap<>();
    private static List<Integer> radarIds = List.of();
    private static int radarTicks;
    private static List<String> scanLines = List.of();
    private static int scanTicks;

    public static int radarTicksLeft() { return radarTicks; }

    public static void radar(Gadgets.RadarPayload msg) { radarIds = msg.ids(); radarTicks = msg.ticks(); if (radarTicks <= 0) clearGlow(); }
    public static void scan(Gadgets.ScanPayload msg) { scanLines = msg.lines(); scanTicks = msg.ticks(); }

    private static Method setSharedFlag;
    private static void setGlow(Entity e, boolean on) {
        try {
            if (setSharedFlag == null) { setSharedFlag = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class); setSharedFlag.setAccessible(true); }
            setSharedFlag.invoke(e, 6, on);
        } catch (ReflectiveOperationException ex) {
            com.example.roomsmod.util.RoomsDebug.warn("radar glow unavailable: {}", ex.toString());
        }
    }

    private static void clearGlow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) for (Map.Entry<Integer, Boolean> en : GLOWING.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e != null) setGlow(e, en.getValue());
        }
        GLOWING.clear();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && mc.getConnection() != null) {
            boolean r = false, s = false;
            while (RADAR_KEY.consumeClick()) r = true;
            while (SCANNER_KEY.consumeClick()) s = true;
            if (r) PacketDistributor.sendToServer(new Gadgets.UsePayload(Gadgets.RADAR));
            if (s) PacketDistributor.sendToServer(new Gadgets.UsePayload(Gadgets.SCANNER));
        }
        if (scanTicks > 0) scanTicks--;
        if (mc.level == null) { GLOWING.clear(); radarTicks = 0; return; }
        if (radarTicks > 0) {
            radarTicks--;
            for (int id : radarIds) {
                Entity en = mc.level.getEntity(id);
                if (en == null) continue;
                GLOWING.computeIfAbsent(id, k -> en.isCurrentlyGlowing());
                if (!en.isCurrentlyGlowing()) setGlow(en, true); // サーバーからのフラグ更新で消えても毎 tick 付け直す
            }
            if (radarTicks == 0) clearGlow();
        } else if (!GLOWING.isEmpty()) clearGlow();
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Post e) {
        if (!e.getName().equals(VanillaGuiLayers.HOTBAR) || scanTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int right = g.guiWidth() - 4, y = 56, lh = 10;
        int alpha = scanTicks < 20 ? Math.max(0x20, scanTicks * 0xFF / 20) : 0xFF;
        String title = Component.translatable("hud.roomsmod.scanner.title").getString();
        List<String[]> rows = scanLines.stream().map(l -> l.split("\t", -1)).toList();
        int w0 = 0, w1 = 0, w2 = 0;
        for (String[] r : rows) { w0 = Math.max(w0, mc.font.width(r[0])); w1 = Math.max(w1, mc.font.width(r.length > 1 ? r[1] : "")); w2 = Math.max(w2, mc.font.width(r.length > 2 ? r[2] : "")); }
        String none = Component.translatable("hud.roomsmod.scanner.none").getString();
        int width = Math.max(mc.font.width(title), rows.isEmpty() ? mc.font.width(none) : w0 + 8 + w1 + 8 + w2) + 8;
        int height = lh * (1 + Math.max(1, rows.size())) + 6;
        int x = right - width;
        g.fill(x, y, right, y + height, (Math.min(alpha, 0x90) << 24));
        int a = alpha << 24;
        g.drawString(mc.font, title, x + 4, y + 3, a | 0x7FE0FF, false);
        int yy = y + 3 + lh;
        if (rows.isEmpty()) g.drawString(mc.font, none, x + 4, yy, a | 0xA0A0A0, false);
        for (String[] r : rows) {
            g.drawString(mc.font, r[0], x + 4, yy, a | 0xFFFFFF, false);
            if (r.length > 1) g.drawString(mc.font, r[1], x + 4 + w0 + 8, yy, a | 0xC0C0C0, false);
            if (r.length > 2) g.drawString(mc.font, r[2], right - 4 - mc.font.width(r[2]), yy, a | 0xA0A0A0, false);
            yy += lh;
        }
    }
}
