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
        boolean ok = p.hurt(source(attacker), amount);
        RoomsDebug.log("damage attacker={} player={} amount={} applied={} health {} -> {}", attacker.getName().getString(), p.getGameProfile().getName(), amount, ok, before, p.getHealth());
        return ok;
    }
}
