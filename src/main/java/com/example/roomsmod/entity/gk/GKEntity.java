package com.example.roomsmod.entity.gk;

/** GK 区画の共通値。どの GK エンティティも 120 秒未満で必ず消える。 */
public final class GKEntity {
    private GKEntity() {}
    /** 100 秒（ハード上限 120 秒未満）。 */
    public static final int MAX_LIFE_TICKS = 20 * 100;
}
