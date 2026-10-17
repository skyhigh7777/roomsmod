package com.example.roomsmod.event;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.config.RoomsConfig;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Rooms を既定の出現地点にする：初参加時と、ベッド等なしのリスポーン時にロビーへ。設定 spawnInRooms で無効化可。 */
public final class RoomsSpawnHandler {
    private RoomsSpawnHandler() {}
    static final String JOINED_KEY = "roomsmod_joined";
    /** テスト用（ハーネス）：判定とログのみでテレポートしない。 */
    public static boolean dryRun = false;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CompoundTag persisted = sp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(JOINED_KEY)) return;
        persisted.putBoolean(JOINED_KEY, true);
        sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (!RoomsConfig.spawnInRooms()) return;
        RoomsDebug.log("first join {} -> rooms lobby", sp.getGameProfile().getName());
        if (!dryRun) RoomsCommands.sendToLobby(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer p0) com.example.roomsmod.item.FlashlightHandler.markRespawn(p0);
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer sp) || !RoomsConfig.spawnInRooms()) return;
        if (sp.getRespawnPosition() != null) return; // ベッド・アンカーあり（または /spawnpoint）
        RoomsDebug.log("respawn {} without bed/anchor -> rooms lobby", sp.getGameProfile().getName());
        if (!dryRun) RoomsCommands.sendToLobby(sp);
    }
}
