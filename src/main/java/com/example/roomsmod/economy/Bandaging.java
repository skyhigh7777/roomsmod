package com.example.roomsmod.economy;

import com.example.roomsmod.item.BandageItem;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 包帯（所持数から使う）：キーを押し続けている間 BandageItem.USE_TICKS（1.5 秒）巻いて体力 BandageItem.HEAL 回復。
 * 離す・攻撃を受ける・死ぬと中断（包帯は減らない）。巻いている間は遅くなる（以前のアイテム使用中と同じくらい）。
 */
public final class Bandaging {
    private Bandaging() {}
    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();

    static void request(ServerPlayer p, boolean start) {
        if (!start) { if (ACTIVE.remove(p.getUUID()) != null) RoomsDebug.log("bandage cancelled (released) player={}", p.getGameProfile().getName()); return; }
        if (ACTIVE.containsKey(p.getUUID()) || !p.isAlive() || p.isSpectator()) return;
        Wallet w = Wallet.of(p);
        if (w.bandage <= 0) { p.displayClientMessage(Component.translatable("message.roomsmod.no_bandage"), true); return; }
        if (p.getHealth() >= p.getMaxHealth()) { p.displayClientMessage(Component.translatable("message.roomsmod.bandage_full"), true); return; }
        ACTIVE.put(p.getUUID(), 0);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    public static boolean isBandaging(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Integer t = ACTIVE.get(p.getUUID());
        if (t == null) return;
        if (!p.isAlive()) { ACTIVE.remove(p.getUUID()); return; }
        t++;
        ACTIVE.put(p.getUUID(), t);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, true, false, false));
        if (t % 8 == 0) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
        if (t < BandageItem.USE_TICKS) return;
        ACTIVE.remove(p.getUUID());
        Wallet w = Wallet.of(p);
        if (w.bandage <= 0 || p.getHealth() >= p.getMaxHealth()) return;
        float before = p.getHealth();
        p.heal(BandageItem.HEAL);
        w.bandage--;
        Wallet.sync(p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.35, 0.4, 0.35, 0.0);
        RoomsDebug.log("bandage (wallet) used by {} health {} -> {} left={}", p.getGameProfile().getName(), before, p.getHealth(), w.bandage);
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getNewDamage() > 0 && ACTIVE.remove(p.getUUID()) != null)
            p.displayClientMessage(Component.translatable("message.roomsmod.bandage_interrupted"), true);
    }
}
