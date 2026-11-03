package com.example.roomsmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** ホットバー9番の「ロビーへ帰る」。右クリックで帰還（条件は RoomsRun.tryReturn）。 */
public class LobbyReturnItem extends Item {
    public LobbyReturnItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!level.isClientSide() && p instanceof ServerPlayer sp) com.example.roomsmod.economy.RoomsRun.tryReturn(sp);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.roomsmod.lobby_return.tip", com.example.roomsmod.economy.CoinFormula.MIN_ROOMS_TO_RETURN).withStyle(ChatFormatting.GRAY));
    }
}
