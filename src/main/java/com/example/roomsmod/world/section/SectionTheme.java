package com.example.roomsmod.world.section;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 1つのセクション（例：A、将来の B=Basement、V=Village）の見た目と出現内容。
 * Java の record にしたのは、ブロック・エンティティ・生成処理を型付きでそのまま参照でき、
 * レジストリ読込順や codec を気にせず「今の出力と完全一致」を保証しやすいため（データ駆動JSONは後で codec を足せば載せ替え可）。
 *
 * 乱数の消費順は旧ハードコード実装と同じになるよう、各値の使われ方は RoomGenerator 側で固定している。
 */
public record SectionTheme(
        String id,
        /** 部屋番号の接頭辞（"A" → A-020）。 */
        String prefix,
        Palette palette,
        /** 部屋の形の重み（RoomShape の順: BOX, CORRIDOR, HALL, TALL, L_SHAPE）。 */
        int[] shapeWeights,
        Furniture furniture,
        Cover cover,
        /** 隠れ場所の種類と重み（上から順に累積判定）。 */
        List<Weighted<HidingType>> hidingMix,
        /** 隠れ場所の個数: min + nextInt(extra)。 */
        int hidingMin, int hidingExtra,
        /** ラッシャー出現表（上から順に独立抽選）。 */
        List<RusherSpawn> rushers,
        SignStyle sign,
        /** 照明: 部屋番号 fadeStart 以降、fadeStep 部屋ごとに明るさ1段階ずつ暗く（0で照明なし）。 */
        int lightFadeStart, int lightFadeStep) {

    public record Palette(Supplier<BlockState> floor, Supplier<BlockState> wall, Supplier<BlockState> ceiling,
                          Supplier<Block> door, Supplier<BlockState> light) {}

    /** 壁際の本棚・机・柱。数は min + nextInt(extra)。 */
    public record Furniture(Supplier<Block> shelf, int shelfMin, int shelfExtra,
                            Supplier<BlockState> deskTop, Supplier<Block> deskItem, int deskExtra,
                            Supplier<Block> pillarA, Supplier<Block> pillarB) {}

    /** 遮蔽物。kindWeights は RoomShape ごとに CoverKind の順（BOOKSHELF_ROW, PILLAR_2X2, PARTITION, CRATES）。 */
    public record Cover(float roomChance, int min, int max, java.util.Map<String, int[]> kindWeights, int[] defaultKindWeights,
                        Supplier<BlockState> shelf, Supplier<BlockState> pillar, Supplier<BlockState> pillarWing,
                        Supplier<BlockState> partition, Supplier<BlockState> crateA, Supplier<BlockState> crateB) {
        public int[] weightsFor(String shape) { return kindWeights.getOrDefault(shape, defaultKindWeights); }
    }

    public record SignStyle(Supplier<Block> wallSign, net.minecraft.world.item.DyeColor color, boolean glowing) {}

    public record Weighted<T>(T value, float weight) {}

    /** 隠れ場所の種類。新しい種類はここに足し、RoomGenerator.placeHiding で置き方を実装する。 */
    public enum HidingType { LOCKER, TABLE }

    /** ラッシャー1種類。alive は「同種が生存中か」、spawn は出現処理。 */
    public record RusherSpawn(String name, int minRoom, float chance, Alive alive, Spawner spawn) {
        @FunctionalInterface public interface Alive { boolean test(ServerLevel level); }
        @FunctionalInterface public interface Spawner { boolean spawn(ServerLevel level, com.example.roomsmod.world.RoomsState rs, Player player); }
    }

    /** "A-020" 形式。 */
    public String formatRoom(int room) { return prefix + "-" + String.format("%03d", room); }

    /** 重み付き選択（重みが全て1なら rand.nextInt(n) と同じ結果・同じ乱数消費）。 */
    public static int pickWeighted(Random rand, int[] w) {
        int total = 0;
        for (int v : w) total += v;
        int r = rand.nextInt(total);
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) return i; }
        return w.length - 1;
    }
}
