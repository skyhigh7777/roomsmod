package com.example.roomsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * 部屋生成ロジック。
 * 照明はA-025からA-100で徐々に暗くする。
 * 看板はText Displayで大きめに表示。
 */
public class RoomGenerator {

    public static BlockPos generateLobby(ServerLevel level, BlockPos origin) {
        int width = 20;
        int depth = 32;
        int height = 5;

        // 床・壁・天井
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                    for (int y = 1; y < height; y++) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(origin.offset(x, height, z), Blocks.STONE.defaultBlockState());
            }
        }

        // 椅子のみ
        level.setBlockAndUpdate(origin.offset(5, 1, 10), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(10, 1, 15), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(15, 1, 20), Blocks.OAK_STAIRS.defaultBlockState());

        // 照明を規則正しく配置（天井に吊るす）
        for (int x = 5; x <= 15; x += 5) {
            for (int z = 8; z <= 24; z += 8) {
                level.setBlockAndUpdate(origin.offset(x, height - 1, z), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
        }

        // 出口扉
        BlockPos doorPos = origin.offset(10, 1, depth - 1);
        level.setBlockAndUpdate(doorPos, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(doorPos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 出口と大きめ看板
        placeExitSign(level, origin.offset(10, 3, depth - 2), "A-001");

        return doorPos;
    }

    public static BlockPos generateNextRoom(ServerLevel level, BlockPos previousExit, int roomNumber) {
        // オフセット計算で入口を合わせる（左右位置は部屋ごとに可変）
        int variant = roomNumber % 5;
        int depth = 28 + (variant * 2); // 極端にかけ離れない範囲で変動
        BlockPos roomOrigin = previousExit; // 渡された位置をそのまま床の原点にする

        // 簡易生成（壁と天井も）
        int height = 5;
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlockAndUpdate(roomOrigin.offset(x, 0, z), Blocks.STONE.defaultBlockState()); // 床
                if (x == 0 || x == 19 || z == 0 || z == depth - 1) {
                    // 入口中央は開けておく
                    if (!(z == 0 && x >= 9 && x <= 11)) {
                        for (int y = 1; y < height; y++) {
                            level.setBlockAndUpdate(roomOrigin.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    }
                }
                level.setBlockAndUpdate(roomOrigin.offset(x, height, z), Blocks.STONE.defaultBlockState()); // 天井
            }
        }

        // 隠れ場所の配置（ルートに対してバリエーション）
        switch (variant) {
            case 0 -> {
                // 脇（左右の壁寄り）に隠れ場所
                level.setBlockAndUpdate(roomOrigin.offset(2, 1, depth / 3), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(17, 1, depth / 3), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(2, 1, depth * 2 / 3), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(17, 1, depth * 2 / 3), Blocks.OAK_PLANKS.defaultBlockState());
            }
            case 1 -> {
                // 中央に3個
                level.setBlockAndUpdate(roomOrigin.offset(10, 1, depth / 4), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(10, 1, depth / 2), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(10, 1, depth * 3 / 4), Blocks.OAK_PLANKS.defaultBlockState());
            }
            case 2 -> {
                // 入口寄りに集中
                level.setBlockAndUpdate(roomOrigin.offset(5, 1, 5), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(15, 1, 5), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(10, 1, 8), Blocks.OAK_PLANKS.defaultBlockState());
            }
            case 3 -> {
                // 出口寄りに集中
                level.setBlockAndUpdate(roomOrigin.offset(5, 1, depth - 8), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(15, 1, depth - 8), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(10, 1, depth - 5), Blocks.OAK_PLANKS.defaultBlockState());
            }
            default -> {
                // 分散
                level.setBlockAndUpdate(roomOrigin.offset(4, 1, depth / 3), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(16, 1, depth / 2), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(roomOrigin.offset(8, 1, depth * 2 / 3), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // 出口扉のみ設置
        BlockPos exitDoor = roomOrigin.offset(10, 1, depth - 1);
        level.setBlockAndUpdate(exitDoor, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(exitDoor.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 照明：天井に吊るす
        int lightLevel = Math.max(0, 15 - Math.max(0, roomNumber - 25) / 5);
        if (lightLevel > 0) {
            level.setBlockAndUpdate(roomOrigin.offset(10, height - 1, depth / 2), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }

        placeExitSign(level, roomOrigin.offset(10, 3, depth - 2), "A-" + String.format("%03d", roomNumber + 1));

        return exitDoor;
    }

    private static void placeExitSign(ServerLevel level, BlockPos pos, String text) {
        // TextDisplayのsetTextなどがprivateのため、一旦通常の看板で代用
        // 大きめ表示は後でText DisplayのEntityData経由で実装予定
        level.setBlockAndUpdate(pos, Blocks.OAK_WALL_SIGN.defaultBlockState());
        // TODO: SignBlockEntityでテキスト設定
    }
}
