package com.example.roomsmod.util;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * Rooms のデバッグ出力。フラグは RoomsState（SavedData）に永続化され、既定はOFF。
 * エラーは常に出力する。
 */
public final class RoomsDebug {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[RoomsDebug] ";
    private static volatile boolean enabled = false;

    private RoomsDebug() {}

    public static boolean isEnabled() { return enabled; }

    /** RoomsState のロード時やコマンドから設定する。永続化は呼び出し側で setDirty する。 */
    public static void setEnabled(boolean value) { enabled = value; }

    /** フラグONのときだけログに出す。 */
    public static void log(String fmt, Object... args) {
        if (enabled) LOGGER.info(PREFIX + fmt, args);
    }

    /** フラグONのときログに出し、プレイヤーがいれば短いチャットも送る。 */
    public static void log(Player player, String chat, String fmt, Object... args) {
        if (!enabled) return;
        LOGGER.info(PREFIX + fmt, args);
        if (player != null && chat != null) player.sendSystemMessage(Component.literal("§7[Debug] " + chat));
    }

    /** フラグに関係なく常に出す。 */
    public static void error(String msg, Throwable t) {
        LOGGER.error(PREFIX + msg, t);
    }

    public static void warn(String fmt, Object... args) {
        LOGGER.warn(PREFIX + fmt, args);
    }
}
