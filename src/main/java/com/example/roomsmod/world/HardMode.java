package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** ハードモードの切り替え（ロビーの左の壁のボタン・/rooms hard）と表示。 */
public final class HardMode {
    private HardMode() {}

    // ===== ハードモードの強化（全セクション共通） =====
    /** 扉1枚あたりの出現上限（DoorEventHandler.HARD_MAX_PER_ROLL）。 */
    public static final int MAX_PER_ROLL = 2;
    /** 出現確率の倍率（上限 1.0）。 */
    public static final float CHANCE_MUL = 1.2f;
    /** 経路を走るエンティティの猶予（出現〜走り出し）の倍率。 */
    public static final double GRACE_MUL = 0.8;
    /** 経路を走るエンティティの速度の倍率。 */
    public static final double SPEED_MUL = 1.1;
    /** 折り返すエンティティの追加の折り返し回数。 */
    public static final int EXTRA_TURNS = 1;
    /** 突進型が走り終えて消えたとき、もう一度抽選する確率。 */
    public static final float CHAIN_CHANCE = 0.15f;

    /** 猶予 tick にハード倍率をかける（最低 10 tick）。 */
    public static int grace(int ticks, boolean hard) { return hard ? Math.max(10, (int) Math.round(ticks * GRACE_MUL)) : ticks; }
    public static double speed(double s, boolean hard) { return hard ? s * SPEED_MUL : s; }

    /**
     * 突進型が走り終えて消えたとき（ハードのみ）：CHAIN_CHANCE で次の tick にもう一度抽選する。
     * エンティティの tick 中に別のエンティティを足さないよう、サーバーのタスクとして積む。
     */
    public static void chainReroll(ServerLevel level, String who) {
        RoomsState rs = RoomsState.get(level);
        if (!rs.hardMode) return;
        if (level.random.nextFloat() >= CHAIN_CHANCE) { RoomsDebug.log("hard chain roll after {}: no", who); return; }
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            RoomsState s = RoomsState.get(level);
            if (!s.hardMode || s.roomPaths.size() < 2) return;
            RoomsDebug.log("hard chain roll after {} at room {}", who, s.currentRoom);
            try { com.example.roomsmod.event.DoorEventHandler.rollRushers(level, s, null, s.currentRoom, false); }
            catch (RuntimeException e) { RoomsDebug.error("hard chain roll failed", e); }
        }));
    }

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
