package com.example.roomsmod.util;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * ログの出力（フラグは RoomsState に保存、既定 OFF。warn / error / info は常に出る）。
 * <p>形式：{@code [roomsmod] <Category>: message key=value ...}。Category は呼び出し元のクラス名
 * （末尾の Entity / Handler / Manager / Commands などを取ったもの。例: V100Entity → V100, FlashlightHandler → Flashlight, RoomsState → State, *Commands → Command）。
 * デバッグ時のチャットは灰色の {@code [Debug] } ＋文（RoomsMsg.debug）。</p>
 */
public final class RoomsDebug {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean enabled = false;
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private RoomsDebug() {}

    public static boolean isEnabled() { return enabled; }

    /** RoomsState のロード時やコマンドから設定する。永続化は呼び出し側で setDirty する。 */
    public static void setEnabled(boolean value) { enabled = value; }

    /** 呼び出し元のクラスからカテゴリ名。 */
    private static String category() {
        Class<?> c = WALKER.walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(k -> k != RoomsDebug.class).findFirst().orElse(RoomsDebug.class));
        while (c.getEnclosingClass() != null) c = c.getEnclosingClass();
        return category(c.getSimpleName());
    }

    static String category(String n) {
        if (n.endsWith("Commands")) return "Command"; // RoomsCommands / AdminCommands
        for (String suf : new String[]{"Entity", "Handler", "Manager", "Commands", "Command"})
            if (n.endsWith(suf) && n.length() > suf.length()) { n = n.substring(0, n.length() - suf.length()); break; }
        if (n.startsWith("Rooms") && n.length() > 5) n = n.substring(5);
        return n;
    }

    private static String prefix() { return "[roomsmod] " + category() + ": "; }

    /** フラグONのときだけログに出す。 */
    public static void log(String fmt, Object... args) {
        if (enabled) LOGGER.info(prefix() + fmt, args);
    }

    /** フラグONのときログに出し、プレイヤーがいれば短いチャットも送る。 */
    public static void log(Player player, String chat, String fmt, Object... args) {
        if (!enabled) return;
        LOGGER.info(prefix() + fmt, args);
        if (player != null && chat != null) player.sendSystemMessage(RoomsMsg.debug(chat));
    }

    /** ログは1回だけ出し、チャットは全員に送る（近くにプレイヤーがいなくてもログは残る）。 */
    public static void announce(java.util.Collection<? extends Player> players, String chat, String fmt, Object... args) {
        if (!enabled) return;
        LOGGER.info(prefix() + fmt, args);
        if (chat != null) for (Player p : players) p.sendSystemMessage(RoomsMsg.debug(chat));
    }

    /** フラグに関係なく常に出す（管理操作・状態変更の記録）。 */
    public static void info(String fmt, Object... args) { LOGGER.info(prefix() + fmt, args); }

    /** フラグに関係なく常に出す。 */
    public static void error(String msg, Throwable t) { LOGGER.error(prefix() + msg, t); }

    public static void warn(String fmt, Object... args) { LOGGER.warn(prefix() + fmt, args); }
}
