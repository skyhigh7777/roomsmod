package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 部屋ディメンション（ロビー含む）だけ、体力と満腹度をバニラのアイコンではなく横棒で表示する。
 * 位置は固定（低体力のハートの揺れ・満腹度0のガタガタは無し）。被ダメージ時は揺らさず白く光るだけ。
 * バニラの leftHeight / rightHeight を同じだけ進めるので、防具・空気ゲージはその上に通常どおり並ぶ。
 * アクションバー（電池 HUD・エンティティの表示）は棒より上なので重ならない。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class HudBars {
    private HudBars() {}

    static final int W = 81, H = 9;
    private static float lastHealth = -1;
    private static int flashUntil = 0, flashLen = 10, flashColor = 0xFFFFFF;

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.dimension().equals(RoomsCommands.ROOMS_DIMENSION);
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        GuiGraphics g = e.getGuiGraphics();
        if (e.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            e.setCanceled(true);
            int x = g.guiWidth() / 2 - 91, y = g.guiHeight() - mc.gui.leftHeight;
            drawHealth(g, mc.font, p, x, y);
            mc.gui.leftHeight += 10;
        } else if (e.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
            if (p.getVehicle() instanceof LivingEntity) return; // 乗り物の体力表示はバニラに任せる
            e.setCanceled(true);
            int x = g.guiWidth() / 2 + 91 - W, y = g.guiHeight() - mc.gui.rightHeight;
            drawFood(g, mc.font, p, x, y);
            mc.gui.rightHeight += 10;
        }
    }

    private static void drawHealth(GuiGraphics g, Font font, Player p, int x, int y) {
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        int tick = p.tickCount;
        if (lastHealth >= 0 && hp < lastHealth - 0.01f) { flashUntil = tick + flashLen; flashColor = 0xFFFFFF; }
        else if (lastHealth >= 0 && hp >= lastHealth + 4f) { flashUntil = tick + flashLen; flashColor = 0x60FF60; } // 包帯などの大きな回復
        lastHealth = hp;
        int fill;
        if (p.hasEffect(MobEffects.WITHER)) fill = 0xFF3A3A3A;
        else if (p.hasEffect(MobEffects.POISON)) fill = 0xFF6E9B2A;
        else if (p.isFullyFrozen()) fill = 0xFF7FC8F0;
        else fill = 0xFFD02828;
        frame(g, x, y);
        int w = Mth.clamp(Math.round((W - 2) * hp / max), 0, W - 2);
        g.fill(x + 1, y + 1, x + 1 + w, y + H - 1, fill);
        g.fill(x + 1, y + 1, x + 1 + w, y + 2, 0x40FFFFFF); // 上端のハイライト（静止）
        if (abs > 0) {
            int aw = Mth.clamp(Math.round((W - 2) * abs / max), 1, W - 2);
            g.fill(x + 1, y + H - 3, x + 1 + aw, y + H - 1, 0xFFF0C020); // 衝撃吸収：下側の黄色い帯
        }
        if (flashUntil > tick) {
            int a = (int) (0x90 * (flashUntil - tick) / (float) flashLen);
            g.fill(x + 1, y + 1, x + W - 1, y + H - 1, (a << 24) | flashColor);
        }
        String t = Mth.ceil(hp) + (abs > 0 ? "+" + Mth.ceil(abs) : "") + "/" + Mth.ceil(max);
        label(g, font, t, x, y);
    }

    private static void drawFood(GuiGraphics g, Font font, Player p, int x, int y) {
        var fd = p.getFoodData();
        int food = fd.getFoodLevel();
        float sat = fd.getSaturationLevel();
        int fill = p.hasEffect(MobEffects.HUNGER) ? 0xFF7A8B3A : 0xFFC87A2A;
        frame(g, x, y);
        int w = Mth.clamp(Math.round((W - 2) * food / 20f), 0, W - 2);
        // 右から左へ減る（バニラの満腹度アイコンと同じ向き）
        g.fill(x + W - 1 - w, y + 1, x + W - 1, y + H - 1, fill);
        g.fill(x + W - 1 - w, y + 1, x + W - 1, y + 2, 0x40FFFFFF);
        int sw = Mth.clamp(Math.round((W - 2) * Math.min(sat, food) / 20f), 0, W - 2);
        if (sw > 0) g.fill(x + W - 1 - sw, y + H - 3, x + W - 1, y + H - 1, 0xFFF2D7A0); // 隠し満腹度：下側の淡い帯
        label(g, font, food + "/20", x, y);
    }

    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + W, y + H, 0xFF101010);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFF2A2020);
    }

    private static void label(GuiGraphics g, Font font, String t, int x, int y) {
        var ps = g.pose();
        ps.pushPose();
        float s = 0.75f;
        float tw = font.width(t) * s;
        ps.translate(x + (W - tw) / 2f, y + (H - 8 * s) / 2f + 0.5f, 0);
        ps.scale(s, s, 1);
        g.drawString(font, t, 0, 0, 0xFFFFFFFF, true);
        ps.popPose();
    }
}
