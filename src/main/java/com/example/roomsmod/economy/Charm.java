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
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * お守り（所持数、自動）。部屋ディメンションで死ぬ時に1つ使って死亡を取り消し、体力 HEAL、PROTECT_TICKS（10 秒）の間は完全に無敵。
 * <ul>
 *   <li>無敵中：ダメージはすべて無効（LivingIncomingDamageEvent と、この MOD の敵の攻撃 RoomsDamage.hurt の両方で止める。クリエイティブの直接ダメージ経路も）。
 *       死亡イベントも取り消す。悪い効果（ウィザー・毒など）は復活時と無敵中ずっと消す。つかみ（A-50 / A-450 の hold）は解除し、無敵中はつかまない。</li>
 *   <li>残り時間は HUD のお守りの行に表示（Wallet の同期）。</li>
 *   <li>この MOD の敵の即死（B-260 の 1000 ダメージ・PLACEHOLDER など）も全部 RoomsDamage.hurt → die（LivingDeathEvent）なので防げる。</li>
 *   <li>防がないもの：奈落（fell_out_of_world）と /kill（generic_kill）。</li>
 *   <li>デバッグ ON の時は DebugNoDeath（HIGHEST）が先に取り消すので使わない。</li>
 * </ul>
 */
public final class Charm {
    private Charm() {}

    public static final float HEAL = 25f;
    public static final int PROTECT_TICKS = 200; // 10 秒

    private static final Map<UUID, Long> PROTECT = new ConcurrentHashMap<>();

    private static boolean bypass(net.minecraft.world.damagesource.DamageSource s) {
        return s.is(DamageTypes.FELL_OUT_OF_WORLD) || s.is(DamageTypes.GENERIC_KILL);
    }

    public static boolean isProtected(ServerPlayer p) { return protectTicks(p) > 0; }
    public static int protectTicks(ServerPlayer p) {
        Long until = PROTECT.get(p.getUUID());
        return until == null ? 0 : (int) Math.max(0, until - p.serverLevel().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION) || bypass(e.getSource())) return;
        if (isProtected(p)) { // 無敵中は死なない（ほかの経路で死にかけた時も）
            e.setCanceled(true);
            p.setHealth(Math.max(p.getHealth(), 1f));
            return;
        }
        Wallet w = Wallet.of(p);
        if (w.charm <= 0) return;
        e.setCanceled(true);
        w.charm--;
        p.setHealth(Math.min(p.getMaxHealth(), HEAL));
        p.clearFire();
        p.fallDistance = 0;
        p.invulnerableTime = 20;
        PROTECT.put(p.getUUID(), p.serverLevel().getGameTime() + PROTECT_TICKS);
        cleanse(p);
        com.example.roomsmod.world.AbyssRooms.release(p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        p.displayClientMessage(Component.translatable("message.roomsmod.charm.used", PROTECT_TICKS / 20).withStyle(ChatFormatting.GOLD), true);
        Wallet.sync(p);
        RoomsDebug.log("charm used player={} cause={} left={} health={} protect_ticks={}", p.getGameProfile().getName(), e.getSource().getMsgId(), w.charm, p.getHealth(), PROTECT_TICKS);
    }

    /** 悪い効果（ウィザー・毒・鈍化など）を消す。 */
    private static void cleanse(ServerPlayer p) {
        for (MobEffectInstance m : java.util.List.copyOf(p.getActiveEffects()))
            if (m.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(m.getEffect());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isProtected(p) && !bypass(e.getSource())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = PROTECT.get(p.getUUID());
        if (until == null) return;
        int left = protectTicks(p);
        if (left <= 0) {
            PROTECT.remove(p.getUUID());
            Wallet.sync(p);
            RoomsDebug.log("charm protection ended player={}", p.getGameProfile().getName());
            return;
        }
        cleanse(p);
        p.clearFire();
        if (left % 20 == 0) Wallet.sync(p); // HUD の残り秒
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { PROTECT.remove(e.getEntity().getUUID()); }
}
