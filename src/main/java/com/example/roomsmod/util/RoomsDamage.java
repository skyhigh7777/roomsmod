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
public final class RoomsDamage {
    private RoomsDamage() {}
    public static final ResourceKey<DamageType> ENTITY_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("roomsmod", "entity_hit"));

    public static DamageSource source(Entity attacker) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ENTITY_HIT), attacker);
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
