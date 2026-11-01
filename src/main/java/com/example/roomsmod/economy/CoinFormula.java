package com.example.roomsmod.economy;

import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;

/**
 * コインの計算（調整はここだけ）。
 * <pre>
 *   基本   = Σ（周回で進んだ各部屋 r = 開始の次 .. いちばん先）weight(r)
 *   weight = ノーマル: BASE + DEPTH × (min(local, max) − 1) / (max − 1)    （セクションの奥ほど高い。max を越えた部屋は max と同じ）
 *            ハード  : BASE（深さの加算なし、代わりに全体 × HARD_MUL）
 *   倍率   = (1 + NO_HIT_BONUS[被弾なし] + SURVIVE_BONUS[生還]) × (死亡なら DEATH_MUL) × (ハードなら HARD_MUL)
 *   コイン = floor(基本 × 倍率)
 * </pre>
 * 例（ノーマル）：V-401〜V-500 の 100 部屋 ≈ 122.5、V-451〜B-050 の 100 部屋 ≈ 112.9（B の始めは浅いので少なめ）。
 */
public final class CoinFormula {
    private CoinFormula() {}

    public static final double BASE = 1.0;
    /** セクションの最後の部屋で BASE にこれだけ足す（ノーマルのみ）。 */
    public static final double DEPTH = 0.25;
    public static final double NO_HIT_BONUS = 0.30;
    public static final double SURVIVE_BONUS = 0.20;
    /** 死亡した時は基本の半分（被弾なし・生還のボーナスもなし）。 */
    public static final double DEATH_MUL = 0.5;
    public static final double HARD_MUL = 2.0;
    /** ロビーへ戻れるのに必要な部屋数（周回の開始の部屋から、セクションをまたいでも通算）。 */
    public static final int MIN_ROOMS_TO_RETURN = 50;

    /** 部屋 room の重み。 */
    public static double weight(RoomsState rs, int room, boolean hard) {
        if (hard) return BASE;
        int max = Sections.maxLocalRoom(rs, room);
        if (max <= 0) max = Sections.length(Sections.forRoom(rs, room).id()); // 最後のセクション（上限なし）はその長さで頭打ち
        int local = Math.max(1, Math.min(Sections.localRoom(rs, room), max));
        double depth = max <= 1 ? 1.0 : (local - 1) / (double) (max - 1);
        return BASE + DEPTH * depth;
    }

    /** 基本（倍率の前）。start の次の部屋から furthest まで。 */
    public static double base(RoomsState rs, int start, int furthest, boolean hard) {
        double sum = 0;
        for (int r = Math.max(1, start + 1); r <= furthest; r++) sum += weight(rs, r, hard);
        return sum;
    }

    public record Result(int rooms, double base, double multiplier, long coins) {}

    public static Result compute(RoomsState rs, int start, int furthest, boolean noHit, boolean survived, boolean hard) {
        int rooms = Math.max(0, furthest - start);
        double b = base(rs, start, furthest, hard);
        double m = survived ? 1 + (noHit ? NO_HIT_BONUS : 0) + SURVIVE_BONUS : DEATH_MUL;
        if (hard) m *= HARD_MUL;
        return new Result(rooms, b, m, (long) Math.floor(b * m));
    }
}
