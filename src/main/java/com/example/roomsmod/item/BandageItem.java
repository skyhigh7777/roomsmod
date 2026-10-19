package com.example.roomsmod.item;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** 包帯：右クリック長押し（USE_TICKS）で体力 HEAL 回復。途中で離すと何も起きない。満タンでは使えない。 */
public class BandageItem extends Item {
    public static final int USE_TICKS = 30;   // 1.5 秒
    public static final float HEAL = 25f;     // 部屋ディメンションの最大体力 100 の 1/4

    public BandageItem(Properties p) { super(p); }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack s, LivingEntity e) { return USE_TICKS; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (p.getHealth() >= p.getMaxHealth()) {
            if (!level.isClientSide()) p.displayClientMessage(Component.translatable("message.roomsmod.bandage_full"), true);
            return InteractionResultHolder.fail(s);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int remaining) {
        if (!level.isClientSide() && remaining % 8 == 0)
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (!level.isClientSide() && e.getHealth() < e.getMaxHealth()) {
            float before = e.getHealth();
            e.heal(HEAL);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
            if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.0, e.getZ(), 8, 0.35, 0.4, 0.35, 0.0);
            if (!(e instanceof Player p && p.getAbilities().instabuild)) s.shrink(1);
            RoomsDebug.log("bandage used by {} health {} -> {}", e.getName().getString(), before, e.getHealth());
        }
        return s;
    }
}
