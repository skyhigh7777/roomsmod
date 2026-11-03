package com.example.roomsmod.economy;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * アドレナリン（所持数から使う、キー既定 J）。DURATION の間スタミナが減らない（息切れも解除）＋移動速度上昇 II。
 * 効果が切れてから COOLDOWN の間は次を使えない。部屋ディメンション（ロビー含む）でのみ使える。調整はここだけ。
 */
public final class Adrenaline {
    private Adrenaline() {}

    public static final int DURATION = 160;     // 8 秒
    public static final int COOLDOWN = 200;     // 効果が切れてから 10 秒
    public static final int SPEED_AMPLIFIER = 1; // 移動速度上昇 II

    /** プレイヤー → {効果の終わり, 次に使える時刻}（ゲーム時刻）。 */
    private static final Map<UUID, long[]> STATE = new ConcurrentHashMap<>();

    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }

    public static boolean isActive(ServerPlayer p) { long[] s = STATE.get(p.getUUID()); return s != null && now(p) < s[0]; }
    public static int activeTicks(ServerPlayer p) { long[] s = STATE.get(p.getUUID()); return s == null ? 0 : (int) Math.max(0, s[0] - now(p)); }
    public static int cooldownTicks(ServerPlayer p) { long[] s = STATE.get(p.getUUID()); return s == null ? 0 : (int) Math.max(0, s[1] - now(p)); }

    static void use(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || !p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        Wallet w = Wallet.of(p);
        if (w.adrenaline <= 0) { deny(p, "message.roomsmod.adrenaline.none"); return; }
        if (isActive(p)) { deny(p, "message.roomsmod.adrenaline.active"); return; }
        int cd = cooldownTicks(p);
        if (cd > 0) { p.displayClientMessage(Component.translatable("message.roomsmod.adrenaline.cooldown", (cd + 19) / 20).withStyle(ChatFormatting.RED), true); return; }
        w.adrenaline--;
        long t = now(p);
        STATE.put(p.getUUID(), new long[]{t + DURATION, t + DURATION + COOLDOWN});
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, SPEED_AMPLIFIER, false, false, true));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.3f);
        p.displayClientMessage(Component.translatable("message.roomsmod.adrenaline.used", DURATION / 20).withStyle(ChatFormatting.GOLD), true);
        Wallet.sync(p);
        RoomsDebug.log("adrenaline used player={} left={} duration={} cooldown={}", p.getGameProfile().getName(), w.adrenaline, DURATION, COOLDOWN);
    }

    private static void deny(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.isCanceled()) STATE.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { STATE.remove(e.getEntity().getUUID()); }
}
