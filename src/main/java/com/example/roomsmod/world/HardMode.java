package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** ハードモードの切り替え（ロビーの左の壁のボタン・/rooms hard）と表示。 */
public final class HardMode {
    private HardMode() {}

    public static boolean isOn(ServerLevel level) { return RoomsState.get(level).hardMode; }

    /** 切り替えて保存・看板更新・全員に通知。by はデバッグ用（プレイヤー名や Rcon）。 */
    public static void set(ServerLevel level, boolean on, String by) {
        RoomsState rs = RoomsState.get(level);
        boolean was = rs.hardMode;
        rs.hardMode = on;
        rs.setDirty();
        LobbyPanels.refreshHard(level);
        String msg = on ? "§cHARD MODE: ON §7（全セクションのエンティティが出現）" : "§7HARD MODE: OFF";
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.displayClientMessage(Component.literal(msg), true);
        RoomsDebug.log("hard mode {} -> {} by {}", was ? "on" : "off", on ? "on" : "off", by);
    }

    /** 扉を開けて次の部屋ができたとき（ハードモードのみ）、部屋ディメンションの全員にアクションバーで表示。 */
    public static void announceRoom(ServerLevel level, RoomsState rs, int room) {
        Component c = Component.literal("§c☠ HARD MODE §f" + Sections.formatRoom(rs, room));
        for (ServerPlayer p : level.players()) p.displayClientMessage(c, true);
        RoomsDebug.log("hard mode notice room={} ({})", room, Sections.formatRoom(rs, room));
    }
}
