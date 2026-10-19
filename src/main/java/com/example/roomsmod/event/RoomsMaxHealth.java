package com.example.roomsmod.event;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 部屋ディメンション（ロビー含む）にいる間だけ最大体力を 100 にする（+80 の修飾子 roomsmod:rooms_max_health）。
 * 修飾子は永続（セーブに残る）なので、再ログイン時に体力が 20 に切り詰められない。ディメンションを出たら外して体力を 20 以内に収める。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID)
public final class RoomsMaxHealth {
    private RoomsMaxHealth() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "rooms_max_health");
    public static final double BONUS = 80.0;

    static boolean inRooms(ServerPlayer p) { return p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION); }

    /** 状態を合わせる。fill=true なら付けたとき（または常に）体力を最大まで回復。戻り値：修飾子を新しく付けたか。 */
    public static boolean sync(ServerPlayer p, boolean fill, String why) {
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return false;
        boolean has = a.hasModifier(ID);
        if (inRooms(p)) {
            boolean added = false;
            if (!has) {
                a.addPermanentModifier(new AttributeModifier(ID, BONUS, AttributeModifier.Operation.ADD_VALUE));
                added = true;
            }
            if (added || fill) p.setHealth(p.getMaxHealth());
            if (added || fill) RoomsDebug.log("rooms max health {} player={} max={} health={} ({})", added ? "applied" : "refill", p.getGameProfile().getName(), p.getMaxHealth(), p.getHealth(), why);
            return added;
        }
        if (has) {
            a.removeModifier(ID);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            RoomsDebug.log("rooms max health removed player={} max={} health={} ({})", p.getGameProfile().getName(), p.getMaxHealth(), p.getHealth(), why);
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, false, "login"); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, !e.isEndConquered(), "respawn"); }

    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, false, "dimension change"); }

    /** 保険（他の Mod のテレポート等）：1秒ごとに確認。 */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0 && p.isAlive()) sync(p, false, "periodic");
    }
}
