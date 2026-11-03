package com.example.roomsmod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * コマンドの返答・チャットの見た目をそろえる。
 * <ul>
 *   <li>チャット：灰色の {@code [Rooms] } ＋本文。成功＝緑、情報＝白、警告＝黄、失敗＝赤（sendFailure も赤）。</li>
 *   <li>アクションバー（パネル・アイテム）：接頭辞なし。情報＝白、拒否・失敗＝赤、購入・報酬＝金。</li>
 *   <li>デバッグのチャット：灰色の {@code [Debug] } ＋本文（灰色）。</li>
 *   <li>部屋は常に Sections.formatRoom の形（V-001、B-020）。</li>
 * </ul>
 */
public final class RoomsMsg {
    private RoomsMsg() {}
    private static MutableComponent tag(String t) { return Component.literal("[" + t + "] ").withStyle(ChatFormatting.DARK_GRAY); }

    public static MutableComponent ok(String s) { return tag("Rooms").append(Component.literal(s).withStyle(ChatFormatting.GREEN)); }
    public static MutableComponent info(String s) { return tag("Rooms").append(Component.literal(s).withStyle(ChatFormatting.WHITE)); }
    public static MutableComponent warn(String s) { return tag("Rooms").append(Component.literal(s).withStyle(ChatFormatting.YELLOW)); }
    public static MutableComponent err(String s) { return tag("Rooms").append(Component.literal(s).withStyle(ChatFormatting.RED)); }
    public static MutableComponent ok(Component c) { return tag("Rooms").append(c.copy().withStyle(ChatFormatting.GREEN)); }
    public static MutableComponent err(Component c) { return tag("Rooms").append(c.copy().withStyle(ChatFormatting.RED)); }
    public static MutableComponent debug(String s) { return tag("Debug").append(Component.literal(s).withStyle(ChatFormatting.GRAY)); }

    /** アクションバー用。 */
    public static MutableComponent bar(String s) { return Component.literal(s).withStyle(ChatFormatting.WHITE); }
    public static MutableComponent barDeny(String s) { return Component.literal(s).withStyle(ChatFormatting.RED); }
    public static MutableComponent barDeny(Component c) { return c.copy().withStyle(ChatFormatting.RED); }
}
