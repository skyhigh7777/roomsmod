package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Random;

/**
 * PLACEHOLDER（テスト用）区画の部屋：壁も天井もない 11x11 の床（x=5..15, z=0..10）に、テーブル1つとロッカー2つだけ。
 * <ul>
 *   <li>進行用の出口扉：+Z の縁の外（z=11）に、床1列と柱2本・まぐさだけの「自立した扉枠」を置き、その真ん中が出口扉。</li>
 *   <li>落下防止：床のまわり（x=4 / x=16 と z=-1 / z=11 の縁、高さ 1〜3）に見えないバリアを置く。
 *       z=-1 は前の部屋のブロック（扉・壁）がある所には置かない。バリアは3段なのでジャンプでも越えられない。</li>
 *   <li>明かり：天井がないので見えない光ブロック（minecraft:light）を床の上 3 マスに2つ。</li>
 * </ul>
 * 部屋はすべて +Z に一直線に並ぶので、既存の部屋と重ならない。
 */
final class PlaceholderRooms {
    private PlaceholderRooms() {}

    static final int EX = 10, HALF = 5, SIZE = 11;
    /** 扉枠の高さ（まぐさの y）。 */
    static final int FRAME_TOP = 3;

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        BlockState floor = theme.palette().floor().get(), frame = theme.palette().wall().get();
        BlockState air = Blocks.AIR.defaultBlockState(), barrier = Blocks.BARRIER.defaultBlockState();
        int x0 = EX - HALF, x1 = EX + HALF, zExit = SIZE; // 床 z=0..10、扉は z=11

        // 床（上は空にしておく）
        for (int x = x0; x <= x1; x++)
            for (int z = 0; z < SIZE; z++) {
                set(level, origin, x, 0, z, floor);
                for (int y = 1; y <= 4; y++) set(level, origin, x, y, z, air);
            }
        // 前の部屋の出口をふさいでいた黒コンクリート（sealExit）を開ける
        set(level, origin, EX, 1, 0, air);
        set(level, origin, EX, 2, 0, air);

        // 自立した扉枠：床1列（x=9..11）、柱（x=9, x=11）、まぐさ（y=3）、真ん中が扉
        for (int x = EX - 1; x <= EX + 1; x++) set(level, origin, x, 0, zExit, floor);
        for (int y = 1; y <= FRAME_TOP; y++) { set(level, origin, EX - 1, y, zExit, frame); set(level, origin, EX + 1, y, zExit, frame); }
        set(level, origin, EX, FRAME_TOP, zExit, frame);
        BlockPos exitDoor = origin.offset(EX, 1, zExit);
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(exitDoor, lower);
        level.setBlockAndUpdate(exitDoor.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        RoomGenerator.sealExit(level, exitDoor);

        // 落下防止のバリア（見えない）
        int barriers = 0;
        for (int y = 1; y <= 3; y++) {
            for (int z = -1; z <= zExit; z++) { barriers += barrier(level, origin, x0 - 1, y, z, barrier); barriers += barrier(level, origin, x1 + 1, y, z, barrier); }
            for (int x = x0; x <= x1; x++) {
                barriers += barrier(level, origin, x, y, -1, barrier);               // 入口側：前の部屋のブロックがある所はそのまま
                if (Math.abs(x - EX) > 1) barriers += barrier(level, origin, x, y, zExit, barrier); // 出口側：扉枠の所は空ける
            }
        }

        // 隠れ場所：テーブル1つ（床の真ん中の左右どちらか）とロッカー2つ（床の奥の左右の角）。通路 x=9..11 は空ける
        boolean left = rand.nextBoolean();
        int tx = left ? EX - 3 : EX + 3, tz = 5;
        RoomGenerator.placeHiding(level, origin.offset(tx, 1, tz), Direction.NORTH, SectionTheme.HidingType.TABLE);
        RoomGenerator.placeHidingSpot(level, origin.offset(x0, 1, 8), Direction.EAST);
        RoomGenerator.placeHidingSpot(level, origin.offset(x1, 1, 8), Direction.WEST);
        RoomsDebug.log("hiding spot type=table at {}", origin.offset(tx, 1, tz));
        RoomsDebug.log("hiding spot type=locker at {} and {}", origin.offset(x0, 1, 8), origin.offset(x1, 1, 8));

        // 明かり（見えない光ブロック）
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        set(level, origin, EX, 3, 2, light);
        set(level, origin, EX, 3, 8, light);

        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = List.of(origin.offset(EX, 1, 0), origin.offset(EX, 1, 1), origin.offset(EX, 1, SIZE - 1), exitDoor);
        List<BoundingBox> boxes = List.of(new BoundingBox(origin.getX() + x0 - 1, origin.getY(), origin.getZ(),
                origin.getX() + x1 + 1, origin.getY() + 4, origin.getZ() + zExit));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=PLACEHOLDER origin={} boxes={} exitDoor={} barriers={}",
                    roomNumber, Sections.formatRoom(state, roomNumber), origin, boxes, exitDoor, barriers);
        return exitDoor;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }

    /** 空気の所にだけバリアを置く。置いたら 1。 */
    private static int barrier(ServerLevel l, BlockPos o, int x, int y, int z, BlockState b) {
        BlockPos p = o.offset(x, y, z);
        if (!l.getBlockState(p).isAir()) return 0;
        l.setBlockAndUpdate(p, b);
        return 1;
    }
}
