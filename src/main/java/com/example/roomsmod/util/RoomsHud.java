package com.example.roomsmod.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * アクションバーの優先度。エンティティの表示（問題・カウントダウン・タイトル等）を出したプレイヤーには、
 * HOLD_TICKS のあいだ懐中電灯の電池残量の表示を出さない（上書きして読めなくなるため）。電池はアイテムのバーでも見える。
 */
public final class RoomsHud {
    private RoomsHud() {}
    public static final int HOLD_TICKS = 60;
    private static final Map<UUID, Long> HOLD = new ConcurrentHashMap<>();

    /** エンティティのアクションバー表示（優先）。 */
    public static void entity(ServerPlayer p, Component c) {
        p.displayClientMessage(c, true);
        hold(p, HOLD_TICKS);
    }

    /** タイトル等、アクションバー以外のエンティティ表示の間も電池表示を止める。 */
    public static void hold(ServerPlayer p, int ticks) {
        long until = p.serverLevel().getGameTime() + ticks;
        HOLD.merge(p.getUUID(), until, Math::max);
    }

    public static boolean batteryAllowed(ServerPlayer p) {
        Long u = HOLD.get(p.getUUID());
        if (u == null) return true;
        if (p.serverLevel().getGameTime() >= u) { HOLD.remove(p.getUUID()); return true; }
        return false;
    }
}
