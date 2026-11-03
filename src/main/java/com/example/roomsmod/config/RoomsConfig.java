package com.example.roomsmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** サーバー設定（world/serverconfig/roomsmod-server.toml）。 */
public final class RoomsConfig {
    private RoomsConfig() {}
    public static final ModConfigSpec SPEC;
    /** 初参加時と、ベッド・リスポーンアンカー未設定でのリスポーン時に Rooms のロビーへ送る。 */
    public static final ModConfigSpec.BooleanValue SPAWN_IN_ROOMS;
    /** 部屋ディメンションでブロックを置けるようにする（既定 false＝置けない）。壊す制限・ロビーのパネルの保護はそのまま。 */
    public static final ModConfigSpec.BooleanValue ALLOW_BLOCK_PLACING;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SPAWN_IN_ROOMS = b.comment("Send players to the Rooms lobby on first join and on respawn without a bed/anchor.")
                .define("spawnInRooms", true);
        ALLOW_BLOCK_PLACING = b.comment("Allow players to place blocks in the Rooms dimension (breaking stays restricted; lobby panels stay protected). Can be changed in-game with /rooms admin build <true|false>.")
                .define("allowBlockPlacing", false);
        SPEC = b.build();
    }
    public static boolean spawnInRooms() {
        try { return SPAWN_IN_ROOMS.get(); } catch (IllegalStateException e) { return true; } // 未ロード時は既定値
    }
    public static boolean allowBlockPlacing() {
        try { return ALLOW_BLOCK_PLACING.get(); } catch (IllegalStateException e) { return false; } // 未ロード時は既定値
    }
    /** 実行中に変更してファイルへ保存（/rooms admin build）。未ロードなら false。 */
    public static boolean setAllowBlockPlacing(boolean on) {
        try { ALLOW_BLOCK_PLACING.set(on); ALLOW_BLOCK_PLACING.save(); return true; } catch (IllegalStateException e) { return false; }
    }
}
