package com.example.roomsmod.world;

import net.minecraft.core.BlockPos;

/**
 * 部屋ディメンションの基準の高さ（ここだけで決める）。ロビーの床 = y {@value #LOBBY_Y}。部屋は全部ロビーの出口からの相対位置でつながるので、
 * ほかの部屋・セクションもこの高さの近くに並ぶ（上下どちらにも余裕：ディメンションは y -64 .. 319）。
 * 2026-11-01 に y=1 から 50 へ（A の縦穴の下・上へ足す層のため）。旧セーブの y=1 のロビーはそのまま残り、ロビーへ戻ると y=50 に作り直す。
 */
public final class RoomsLayout {
    private RoomsLayout() {}
    /** ロビーの床の y（ロビーの origin の y）。 */
    public static final int LOBBY_Y = 50;
    /** ロビーの origin（床の角）。 */
    public static final BlockPos LOBBY_ORIGIN = new BlockPos(0, LOBBY_Y, 0);
    /** ロビーで立つ位置（足元）。 */
    public static final double SPAWN_X = 10.5, SPAWN_Y = LOBBY_Y + 1, SPAWN_Z = 16.5;
}
