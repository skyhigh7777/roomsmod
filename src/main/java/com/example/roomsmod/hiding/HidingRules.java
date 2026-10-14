package com.example.roomsmod.hiding;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 「隠れているか」の判定窓口。ラッシャーはダメージ前に {@link #isHiding(Player)} か
 * {@link #isProtectedFrom(Entity, Player)} を見る。
 * 隠れ方を増やすときは {@link #addHidingCheck}、隠れていても当たる例外（例：特定エンティティはロッカーを開ける）
 * を作るときは {@link #addExposure} を使う。
 */
public final class HidingRules {
    private static final List<Predicate<Player>> HIDING_CHECKS = new CopyOnWriteArrayList<>();
    private static final List<BiPredicate<Entity, Player>> EXPOSURES = new CopyOnWriteArrayList<>();

    static {
        HIDING_CHECKS.add(LockerManager::isInLocker);
    }

    private HidingRules() {}

    public static void addHidingCheck(Predicate<Player> check) { HIDING_CHECKS.add(check); }

    /** 隠れていてもこの攻撃者からは守られない条件。 */
    public static void addExposure(BiPredicate<Entity, Player> exposure) { EXPOSURES.add(exposure); }

    public static boolean isHiding(Player player) {
        for (Predicate<Player> c : HIDING_CHECKS) if (c.test(player)) return true;
        return false;
    }

    /** attacker から守られているか（隠れていて、かつ例外に当たらない）。 */
    public static boolean isProtectedFrom(Entity attacker, Player player) {
        if (!isHiding(player)) return false;
        for (BiPredicate<Entity, Player> e : EXPOSURES) if (e.test(attacker, player)) return false;
        return true;
    }
}
