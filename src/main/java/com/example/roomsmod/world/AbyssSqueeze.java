package com.example.roomsmod.world;

import java.util.Random;

/**
 * A-250（縦穴が狭まる）の形。縦穴の内側 5x5 のうち「空いたまま残るマス」をビットで持つ（bit = (dx + 2) * 5 + (dz + 2)、dx, dz は中心から -2..2）。
 * 残るマスは上から下まで同じ（落ちる道は途切れない）。それ以外のマスが壁になる（横穴の入口・松明・看板・プレイヤーの体にかかるマスは除く）。
 * <p>
 * 予告の長さは形ごとに計算する：縦穴の中のどこにいても（最悪の位置から）一番近い安全な位置まで、スニークしながら空中で動いて
 * 間に合う tick 数（反応 {@value #REACT} tick を含む）に余裕の倍率をかける。
 */
public final class AbyssSqueeze {
    private AbyssSqueeze() {}

    /** 形（回す前）。weight は抽選の重み（真ん中は少なめ）。 */
    public enum Shape {
        CENTER(1.0), SIDE(2.0), CORNER(2.0), STRIP(1.5), L(1.5), POCKETS(1.5);
        public final double weight;
        Shape(double w) { weight = w; }

        /** 回す前の、空いたまま残るマス。 */
        boolean open0(int dx, int dz) {
            return switch (this) {
                case CENTER -> Math.abs(dx) <= 1 && Math.abs(dz) <= 1;                     // 真ん中 3x3
                case SIDE -> dx >= 0 && Math.abs(dz) <= 1;                                   // 片側の壁へ寄せた 3x3
                case CORNER -> dx >= 0 && dz >= 0;                                           // 角の 3x3
                case STRIP -> dx >= 1;                                                       // 壁沿いの 2x5
                case L -> (dx >= 1 && dz >= -1) || (dz >= 1 && dx >= -1);                    // L 字（幅 2）
                case POCKETS -> (dx >= 1 && dz >= 1) || (dx <= -1 && dz <= -1);              // 対角の 2x2 が 2 つ
            };
        }
    }

    public static int bit(int dx, int dz) { return (dx + 2) * 5 + (dz + 2); }

    public static boolean open(int mask, int dx, int dz) {
        return Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && (mask >> bit(dx, dz) & 1) != 0;
    }

    /** 形を rot 回（90 度ずつ）回したマスク。 */
    public static int mask(Shape s, int rot) {
        int m = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = dx, z = dz;
            for (int i = 0; i < Math.floorMod(rot, 4); i++) { int t = x; x = -z; z = t; } // 逆に回して元の形で調べる
            if (s.open0(x, z)) m |= 1 << bit(dx, dz);
        }
        return m;
    }

    public static Shape pick(Random r) {
        double sum = 0;
        for (Shape s : Shape.values()) sum += s.weight;
        double x = r.nextDouble() * sum;
        for (Shape s : Shape.values()) { x -= s.weight; if (x < 0) return s; }
        return Shape.CENTER;
    }

    // ===== 位置の計算（中心からの相対座標。マス (dx, dz) は dx-0.5..dx+0.5） =====

    /** プレイヤーの中心が動ける範囲（壁から体の半分）。 */
    public static final double REACH = 2.5 - 0.3;
    /** 当たり判定の甘さ：体（半幅 0.3）がこれより深く壁になるマスに入っていれば当たる → 安全な中心は半幅 0.3 - これ の正方形が残るマスに収まる位置。 */
    public static final double TOLERANCE = 0.1;
    static final double STEP = 0.05;

    /** 中心 (px, pz)、半幅 h の正方形が残るマスだけに収まるか。 */
    public static boolean fits(int mask, double px, double pz, double h) {
        int x0 = (int) Math.floor(px - h + 0.5), x1 = (int) Math.floor(px + h - 1.0E-9 + 0.5);
        int z0 = (int) Math.floor(pz - h + 0.5), z1 = (int) Math.floor(pz + h - 1.0E-9 + 0.5);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (!open(mask, x, z)) return false;
        return true;
    }

    /** (px, pz) から一番近い「半幅 h が収まる位置」（相対座標、なければ null）。 */
    public static double[] nearestFit(int mask, double px, double pz, double h) {
        double best = Double.MAX_VALUE; double[] out = null;
        int n = (int) Math.round(REACH / STEP);
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
            double x = i * STEP, z = j * STEP;
            double d = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (d < best && fits(mask, x, z, h)) { best = d; out = new double[]{x, z}; }
        }
        return out;
    }

    private static final java.util.Map<Integer, Double> WORST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 縦穴の中で最も不利な位置から、当たらない位置までの距離（ブロック）。
     * 当たらない中心の集合 = 全部が残るマスでできた長方形（マスの並び）を半幅ぶん縮めたものの和（体の正方形がかかるマスは高々 2x2 の長方形なので正確）。
     */
    public static double worstDistance(int mask) {
        return WORST.computeIfAbsent(mask, m -> {
            double h = 0.3 - TOLERANCE;
            java.util.List<double[]> rects = new java.util.ArrayList<>();
            for (int x0 = -2; x0 <= 2; x0++) for (int x1 = x0; x1 <= 2; x1++) for (int z0 = -2; z0 <= 2; z0++) for (int z1 = z0; z1 <= 2; z1++) {
                boolean all = true;
                for (int x = x0; x <= x1 && all; x++) for (int z = z0; z <= z1; z++) if (!open(m, x, z)) { all = false; break; }
                if (all) rects.add(new double[]{x0 - 0.5 + h, x1 + 0.5 - h, z0 - 0.5 + h, z1 + 0.5 - h});
            }
            if (rects.isEmpty()) return Double.POSITIVE_INFINITY;
            int n = (int) Math.round(REACH / STEP);
            double worst = 0;
            for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
                double x = i * STEP, z = j * STEP, b = Double.MAX_VALUE;
                for (double[] r : rects) {
                    double dx = Math.max(0, Math.max(r[0] - x, x - r[1])), dz = Math.max(0, Math.max(r[2] - z, z - r[3]));
                    b = Math.min(b, Math.hypot(dx, dz));
                }
                worst = Math.max(worst, b);
            }
            return worst;
        });
    }

    /** 反応にかかるとみなす tick。 */
    public static final int REACT = 10;
    /** 空中でスニークして横へ動くときの 1 tick の加速（飛行速度 0.02 × スニーク 0.3 × 入力 0.98）と空気抵抗。 */
    static final double SNEAK_AIR_ACCEL = 0.02 * 0.3 * 0.98, AIR_DRAG = 0.91;

    /** 空中でスニークしながら止まった状態から d ブロック動くのにかかる tick（反応を含む）。 */
    public static int sneakTicks(double d) {
        double v = 0, s = 0; int n = 0;
        while (s < d && n < 1000) { v = (v + SNEAK_AIR_ACCEL) * AIR_DRAG; s += v; n++; }
        return n + REACT;
    }

    /** 予告の倍率（最悪の位置・スニークでも、必要な時間のこれだけ倍）と最低の長さ。 */
    public static final double MARGIN = 1.5, MARGIN_HARD = 1.35;
    public static final int MIN_WARN = 50, MIN_WARN_HARD = 44;

    public static int warnTicks(int mask, boolean hard) {
        int need = sneakTicks(worstDistance(mask));
        return Math.max(hard ? MIN_WARN_HARD : MIN_WARN, (int) Math.ceil(need * (hard ? MARGIN_HARD : MARGIN)));
    }
}
