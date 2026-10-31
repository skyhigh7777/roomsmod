package com.example.roomsmod.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Rooms のエンティティの攻撃。バニラの mobAttack は難易度でスケールし、ピースフルでは 0（イージーは半減）になるため、
 * 難易度に依存しない専用のダメージタイプ roomsmod:entity_hit（scaling=never）を使う。全エンティティの攻撃はここを通す。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.example.roomsmod.RoomsMod.MOD_ID)
public final class RoomsDamage {
    private RoomsDamage() {}
    public static final ResourceKey<DamageType> ENTITY_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("roomsmod", "entity_hit"));

    /** 雲の毒（A-180 のウィザー）を置き換える、敵の名前つきのダメージタイプ（防具無視・難易度に依存しない）。 */
    public static final ResourceKey<DamageType> WITHER = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("roomsmod", "wither"));

    /** 敵の攻撃のダメージ：死因の文に敵の名前（攻撃した時点の表示名）が必ず入る（NamedDamageSource）。 */
    public static DamageSource source(Entity attacker) {
        return new NamedDamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ENTITY_HIT), attacker, attacker.getDisplayName().copy());
    }

    // ---- 敵がかけたウィザー：その間のウィザーのダメージを敵の名前つきに置き換える ----

    private record Curse(net.minecraft.network.chat.Component name, long until) {}
    private static final java.util.Map<java.util.UUID, Curse> CURSES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 敵 attacker が p にウィザーを ticks tick かけた（効果は呼ぶ側で付ける）。 */
    public static void witherFrom(Entity attacker, Player p, int ticks) {
        CURSES.put(p.getUUID(), new Curse(attacker.getDisplayName().copy(), p.level().getGameTime() + ticks + 5));
    }

    /** ウィザーのダメージを、かけた敵の名前つき（roomsmod:wither）に置き換える。 */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onIncoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.level().isClientSide() || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER)) return;
        Curse c = CURSES.get(p.getUUID());
        if (c == null) return;
        if (p.level().getGameTime() > c.until() || !p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) { CURSES.remove(p.getUUID()); return; }
        e.setCanceled(true);
        p.hurt(new NamedDamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(WITHER), null, c.name()), e.getAmount());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { CURSES.remove(e.getEntity().getUUID()); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide() && !e.isCanceled()) CURSES.remove(p.getUUID());
    }

    /** 実際に体力が減ったか（無敵時間中などは false）。 */
    public static boolean hurt(Entity attacker, Player p, float amount) {
        float before = p.getHealth();
        boolean ok = p.isCreative() && !p.isSpectator() && p.level().dimension().equals(com.example.roomsmod.command.RoomsCommands.ROOMS_DIMENSION)
                ? hurtCreative(attacker, p, amount) : p.hurt(source(attacker), amount);
        RoomsDebug.log("damage attacker={} player={} amount={} applied={} health {} -> {}", attacker instanceof com.example.roomsmod.entity.v.VEntity v ? v.vName() : attacker.getName().getString(), p.getGameProfile().getName(), amount, ok, before, p.getHealth());
        return ok;
    }

    /**
     * 部屋ディメンションのクリエイティブ：無敵を無視して体力を直接減らす（衝撃吸収→体力の順、ノックバックなし）。
     * 0 になったら通常の死亡処理（デバッグ時は DebugNoDeath が取り消して回復）。
     */
    private static boolean hurtCreative(Entity attacker, Player p, float amount) {
        if (p.invulnerableTime > 10 || amount <= 0 || !p.isAlive()) return false;
        DamageSource src = source(attacker);
        float abs = p.getAbsorptionAmount(), fromAbs = Math.min(abs, amount);
        p.setAbsorptionAmount(abs - fromAbs);
        float rest = amount - fromAbs;
        // 死因の文（CombatTracker）とキルの記録に残す（直接体力を減らすので hurt の中の記録が通らない）
        p.getCombatTracker().recordDamage(src, rest);
        if (attacker instanceof net.minecraft.world.entity.LivingEntity le) p.setLastHurtByMob(le);
        p.invulnerableTime = 20;
        p.hurtDuration = 10;
        p.hurtTime = 10;
        if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) sl.broadcastDamageEvent(p, src); // 赤く光る・被ダメ音（クライアント側）
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.example.roomsmod.event.DebugNoDeath.noteDamage(sp, amount);
        float nh = p.getHealth() - rest;
        if (nh <= 0f) { p.setHealth(0f); p.die(src); }
        else p.setHealth(nh);
        return true;
    }
}
