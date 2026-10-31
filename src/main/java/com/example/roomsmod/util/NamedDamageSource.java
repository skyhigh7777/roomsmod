package com.example.roomsmod.util;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 死因の文に敵の名前（攻撃した時点の表示名）を必ず入れるダメージ。
 * 敵がその後に消えても・姿のない敵（壁・横穴・重力・雲の毒など、攻撃者のエンティティが残らない場合）でも名前が出る。
 * 文は death.attack.&lt;message_id&gt;（%1$s = やられた人、%2$s = 敵の名前）。
 */
public class NamedDamageSource extends DamageSource {
    private final Component name;

    public NamedDamageSource(Holder<DamageType> type, @Nullable Entity attacker, Component name) {
        super(type, attacker);
        this.name = name;
    }

    public Component enemyName() { return name; }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        return Component.translatable("death.attack." + type().msgId(), victim.getDisplayName(), name);
    }
}
