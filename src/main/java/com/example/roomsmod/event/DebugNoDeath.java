package com.example.roomsmod.event;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * デバッグ ON（/rooms debug on）の間、プレイヤーの死亡を取り消す（サバイバルで本物のダメージを試せるように）。
 * 体力を全回復・火を消し、原因とダメージをチャットに出す。奈落に落ちた場合は今いる部屋の安全な床（無ければロビー）へ戻す。
 */
public final class DebugNoDeath {
    private DebugNoDeath() {}
    private static final Map<UUID, Float> LAST_DAMAGE = new HashMap<>();

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) LAST_DAMAGE.put(sp.getUUID(), e.getNewDamage());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!RoomsDebug.isEnabled() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        e.setCanceled(true);
        DamageSource src = e.getSource();
        Float dmg = LAST_DAMAGE.remove(sp.getUUID());
        String cause = causeName(src);
        sp.setHealth(sp.getMaxHealth());
        sp.clearFire();
        sp.hurtTime = 0;
        sp.invulnerableTime = 20;
        sp.fallDistance = 0;
        sp.getFoodData().setFoodLevel(Math.max(sp.getFoodData().getFoodLevel(), 6));
        boolean void_ = src.is(DamageTypes.FELL_OUT_OF_WORLD) || sp.getY() < sp.level().getMinBuildHeight();
        if (void_) rescue(sp);
        String amount = dmg == null ? "?" : String.format("%.1f", dmg);
        sp.sendSystemMessage(Component.literal("[Debug] 死亡を回避したわ（原因: " + cause + ", ダメージ " + amount + (void_ ? "、奈落から戻したわ" : "") + "）"));
        RoomsDebug.log("debug no-death player={} cause={} damage={} void={} pos={}", sp.getGameProfile().getName(), cause, amount, void_, sp.blockPosition());
    }

    static String causeName(DamageSource src) {
        if (src.getEntity() != null) return src.getEntity().getName().getString();
        if (src.getDirectEntity() != null) return src.getDirectEntity().getName().getString();
        return src.getMsgId();
    }

    /** 奈落から：部屋ディメンションなら今の部屋（位置から判定、無ければ最新の部屋）の安全な床、それ以外はロビー。 */
    static void rescue(ServerPlayer sp) {
        sp.setDeltaMovement(Vec3.ZERO);
        if (sp.level() instanceof ServerLevel lv && lv.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) {
            RoomsState rs = RoomsState.get(lv);
            var feet = sp.blockPosition();
            int room = RoomTracker.roomIndexAt(rs, new net.minecraft.core.BlockPos(feet.getX(), Math.max(feet.getY(), lv.getMinBuildHeight()), feet.getZ()));
            if (room < 0) room = nearestRoomXZ(rs, sp.getX(), sp.getZ());
            Vec3 at = room >= 0 ? SafeSpot.find(lv, rs, room) : null;
            if (at != null) {
                sp.teleportTo(lv, at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
                if (sp instanceof net.neoforged.neoforge.common.util.FakePlayer) sp.setPos(at.x, at.y, at.z); // 偽プレイヤー（ハーネス）は接続が無く移動が反映されない
                return;
            }
        }
        RoomsCommands.sendToLobby(sp);
    }

    /** 高さを無視して、XZ が入っている（無ければ一番近い）部屋。 */
    static int nearestRoomXZ(RoomsState rs, double x, double z) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (int r = 0; r < rs.generatedRooms.size(); r++)
            for (var b : rs.generatedRooms.get(r)) {
                double dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX() - 1)), dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ() - 1));
                double d = dx * dx + dz * dz;
                if (d < bd) { bd = d; best = r; }
            }
        return best;
    }
}
