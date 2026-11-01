package com.example.roomsmod.economy;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * お守り（所持数、自動）。部屋ディメンションで死ぬ時に1つ使って死亡を取り消し、体力 HEAL、PROTECT_TICKS の間ダメージ無効。
 * この MOD の敵の攻撃・即死（RoomsDamage.hurt → die）は全部 LivingDeathEvent を通るので防げる。
 * 防がないもの：奈落（fell_out_of_world、戻しても落ち続けるため）と /kill（generic_kill）。
 * デバッグの DebugNoDeath（HIGHEST）が先に取り消した時は使わない（取り消し済みのイベントには呼ばれない）。
 */
public final class Charm {
    private Charm() {}

    public static final float HEAL = 25f;
    public static final int PROTECT_TICKS = 40; // 2 秒

    private static final Map<UUID, Long> PROTECT = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        if (e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || e.getSource().is(DamageTypes.GENERIC_KILL)) return;
        Wallet w = Wallet.of(p);
        if (w.charm <= 0) return;
        e.setCanceled(true);
        w.charm--;
        p.setHealth(Math.min(p.getMaxHealth(), HEAL));
        p.clearFire();
        p.fallDistance = 0;
        p.invulnerableTime = PROTECT_TICKS;
        PROTECT.put(p.getUUID(), p.serverLevel().getGameTime() + PROTECT_TICKS);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        p.displayClientMessage(Component.translatable("message.roomsmod.charm.used").withStyle(ChatFormatting.GOLD), true);
        Wallet.sync(p);
        RoomsDebug.log("charm used player={} cause={} left={} health={}", p.getGameProfile().getName(), e.getSource().getMsgId(), w.charm, p.getHealth());
    }

    /** お守りが働いた直後はダメージ無効（無敵時間を無視する経路も含めて）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = PROTECT.get(p.getUUID());
        if (until == null) return;
        if (p.serverLevel().getGameTime() >= until) { PROTECT.remove(p.getUUID()); return; }
        if (!e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) && !e.getSource().is(DamageTypes.GENERIC_KILL)) e.setCanceled(true);
    }

    public static boolean isProtected(ServerPlayer p) {
        Long until = PROTECT.get(p.getUUID());
        return until != null && p.serverLevel().getGameTime() < until;
    }
}
