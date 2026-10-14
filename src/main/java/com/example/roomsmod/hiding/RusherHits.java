package com.example.roomsmod.hiding;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A-20 / A-40 の共通当たり処理。クリエイティブ・無敵のスキップより前に判定し、
 * デバッグON時は「被弾するはず／隠れて助かった」をそのプレイヤーのチャットに出す（ラッシャー×プレイヤーごとに1秒1回）。
 */
public final class RusherHits {
    private static final Map<String, Long> LAST_REPORT = new HashMap<>();
    private static final long REPORT_INTERVAL_TICKS = 20;

    private RusherHits() {}

    /**
     * @param lineOfSight ラッシャーからプレイヤーが見えているか
     * @return 実際にダメージを与えたか
     */
    public static boolean tryHit(Mob rusher, String name, Player player, boolean lineOfSight, float damage) {
        if (player.isSpectator() || !player.isAlive()) return false;
        LockerManager.Spot spot = LockerManager.getSpot(player);
        boolean hiding = HidingRules.isHiding(player);
        if (!lineOfSight && !hiding) { // 遮蔽物で見えていない：ダメージなし
            reportCover(rusher, name, player, player.isCreative());
            return false;
        }
        boolean spared = HidingRules.isProtectedFrom(rusher, player);
        if (!spared && !lineOfSight) return false; // 隠れているが例外で露出、ただし見えていない
        report(rusher, name, player, spared, spot, player.isCreative());
        if (spared) return false;
        if (player.isCreative()) return false;
        return player.hurt(rusher.damageSources().mobAttack(rusher), damage);
    }

    private static boolean rateLimited(Mob rusher, Player player) {
        long now = rusher.level().getGameTime();
        String key = rusher.getUUID() + "|" + player.getUUID();
        Long last = LAST_REPORT.get(key);
        if (last != null && now - last < REPORT_INTERVAL_TICKS) return true;
        if (LAST_REPORT.size() > 256) LAST_REPORT.entrySet().removeIf(e -> now - e.getValue() > 200);
        LAST_REPORT.put(key, now);
        return false;
    }

    private static void reportCover(Mob rusher, String name, Player player, boolean creative) {
        if (!RoomsDebug.isEnabled() || rateLimited(rusher, player)) return;
        RoomsDebug.log(player, name + ": spared by cover (no line of sight, creative=" + creative + ")",
                "rusher hit check entity={} player={} result=cover spot=none creative={}",
                name, player.getGameProfile().getName(), creative);
    }

    private static void report(Mob rusher, String name, Player player, boolean spared, LockerManager.Spot spot, boolean creative) {
        if (!RoomsDebug.isEnabled() || rateLimited(rusher, player)) return;
        String spotName = spot == null ? "none" : spot.name().toLowerCase();
        String chat = spared
                ? name + ": spared by hiding (spot=" + spotName + ", creative=" + creative + ")"
                : name + ": would hurt you (spot=" + spotName + ", creative=" + creative + ")";
        RoomsDebug.log(player, chat, "rusher hit check entity={} player={} result={} spot={} creative={}",
                name, player.getGameProfile().getName(), spared ? "spared" : "hurt", spotName, creative);
    }
}
