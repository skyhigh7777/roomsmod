package com.example.roomsmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** サーバー設定（world/serverconfig/roomsmod-server.toml）。 */
public final class RoomsConfig {
    private RoomsConfig() {}
    public static final ModConfigSpec SPEC;
    /** 初参加時と、ベッド・リスポーンアンカー未設定でのリスポーン時に Rooms のロビーへ送る。 */
    public static final ModConfigSpec.BooleanValue SPAWN_IN_ROOMS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SPAWN_IN_ROOMS = b.comment("Send players to the Rooms lobby on first join and on respawn without a bed/anchor.")
                .define("spawnInRooms", true);
        SPEC = b.build();
    }
    public static boolean spawnInRooms() {
        try { return SPAWN_IN_ROOMS.get(); } catch (IllegalStateException e) { return true; } // 未ロード時は既定値
    }
}
