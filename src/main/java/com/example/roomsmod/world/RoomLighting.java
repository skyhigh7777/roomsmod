package com.example.roomsmod.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 部屋の照明の段階的な減光（セクション内の部屋番号ごと）。
 * 次元は has_skylight=false / ambient_light=0 なので、明るさは置いた光源だけで決まる。
 * 段階（tier = (localRoom - fadeStart) / fadeStep）:
 * 0: ランタン間隔5（最大10） / 1: 間隔6（最大6） / 2: 間隔8（最大4） / 3: 間隔10（最大2）
 * 4: 魂のランタン(光10)間隔8（最大3） / 5: 間隔10（最大2） / 6: 魂のランタン1個 / 7+: 隅にろうそく3本(光9)×3
 */
public final class RoomLighting {
    private RoomLighting() {}

    /** hanging=true は天井直下に吊るす、false は床の隅に置く。 */
    public record Fixture(Supplier<BlockState> state, boolean hanging, int spacing, int maxCount) {}

    public static final int LAST_TIER = 7;

    public static int tier(int localRoom, int fadeStart, int fadeStep) {
        return Math.min(LAST_TIER, Math.max(0, localRoom - fadeStart) / Math.max(1, fadeStep));
    }

    public static Fixture fixture(int tier, Supplier<BlockState> lantern) {
        Supplier<BlockState> soul = () -> Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        return switch (tier) {
            case 0 -> new Fixture(lantern, true, 5, 10);
            case 1 -> new Fixture(lantern, true, 6, 6);
            case 2 -> new Fixture(lantern, true, 8, 4);
            case 3 -> new Fixture(lantern, true, 10, 2);
            case 4 -> new Fixture(soul, true, 8, 3);
            case 5 -> new Fixture(soul, true, 10, 2);
            case 6 -> new Fixture(soul, true, 99, 1);
            default -> new Fixture(() -> candle(3), false, 0, 3);
        };
    }

    private static BlockState candle(int n) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
    }

    /** 内寸矩形（両端含む）内の光源候補位置 {x,z}。吊り下げは格子、置き型は四隅（大きい矩形から）。 */
    public static List<int[]> candidates(List<int[]> interiors, Fixture f) {
        List<int[]> out = new ArrayList<>();
        for (int[] r : interiors) {
            int w = r[2] - r[0] + 1, d = r[3] - r[1] + 1;
            if (f.hanging()) {
                int nx = Math.max(1, Math.round((float) w / f.spacing())), nz = Math.max(1, Math.round((float) d / f.spacing()));
                for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++)
                    out.add(new int[]{r[0] + (2 * i + 1) * w / (2 * nx), r[1] + (2 * j + 1) * d / (2 * nz)});
            } else {
                out.add(new int[]{r[0], r[3]});
                out.add(new int[]{r[2], r[1]});
                out.add(new int[]{r[2], r[3]});
                out.add(new int[]{r[0], r[1]});
            }
        }
        // 1個しか置かない段は部屋全体の中央に最も近い候補を先頭に
        if (f.hanging() && f.maxCount() == 1 && out.size() > 1) {
            double cx = out.stream().mapToInt(a -> a[0]).average().orElse(0), cz = out.stream().mapToInt(a -> a[1]).average().orElse(0);
            out.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[1] - cz), Math.hypot(b[0] - cx, b[1] - cz)));
        }
        // 上限より多いときは格子全体から等間隔に間引く（手前に偏らないように）
        if (f.hanging() && f.maxCount() > 1 && out.size() > f.maxCount()) {
            List<int[]> pick = new ArrayList<>();
            for (int k = 0; k < f.maxCount(); k++) pick.add(out.get((2 * k + 1) * out.size() / (2 * f.maxCount())));
            for (int[] c : out) if (!pick.contains(c)) pick.add(c); // 置けなかった時の予備
            return pick;
        }
        return out;
    }

    /** 経路折れ線（{x,z}列）から距離1以内か。置き型光源が通路を塞がないように。 */
    public static boolean nearPath(List<int[]> path, int x, int z) {
        for (int i = 0; i + 1 < path.size(); i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            if (x >= Math.min(a[0], b[0]) - 1 && x <= Math.max(a[0], b[0]) + 1
                    && z >= Math.min(a[1], b[1]) - 1 && z <= Math.max(a[1], b[1]) + 1) return true;
        }
        return false;
    }
}
