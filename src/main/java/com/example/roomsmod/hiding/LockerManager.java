package com.example.roomsmod.hiding;

import com.example.roomsmod.block.LockerBlock;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** サーバー側のロッカー入退室管理。NeoForge.EVENT_BUS に登録する。 */
public final class LockerManager {
    /**
     * 隠れ場所の種類。safety は将来「机はロッカーより危ない」等のルール用（1.0=常に守られる）。
     * 例：HidingRules.addExposure((rusher, p) -> LockerManager.getSpot(p) == Spot.TABLE && rnd > Spot.TABLE.safety)
     * 現在はどちらも1.0で、どこからも参照していない。
     */
    public enum Spot {
        LOCKER(1.0f), TABLE(1.0f);
        public final float safety;
        Spot(float safety) { this.safety = safety; }
    }

    /** exitPos: 出るときに立たせる位置。 */
    private record Hide(Spot spot, ServerLevel level, BlockPos pos, BlockPos exitPos, boolean sneakReleased) {
        Hide withSneakReleased() { return new Hide(spot, level, pos, exitPos, true); }
    }

    private static final Map<UUID, Hide> HIDING = new ConcurrentHashMap<>();

    private LockerManager() {}

    /** サーバー側で隠れている場所の種類（隠れていなければ null）。 */
    public static Spot getSpot(Player player) {
        if (player.level().isClientSide()) return null;
        Hide h = HIDING.get(player.getUUID());
        return h == null ? null : h.spot;
    }

    public static boolean isInLocker(Player player) { return getSpot(player) == Spot.LOCKER; }

    public static boolean isUnderTable(Player player) { return getSpot(player) == Spot.TABLE; }

    private static boolean occupied(ServerPlayer player, BlockPos pos) {
        for (Hide h : HIDING.values()) if (h.level == player.serverLevel() && h.pos.equals(pos)) return true;
        return false;
    }

    /** 机の下に潜る／出る。這い姿勢（SWIMMING）を強制し、机の中央に固定する。 */
    public static void toggleTable(ServerPlayer player, BlockPos pos) {
        if (HIDING.containsKey(player.getUUID())) { exit(player, "right-click"); return; }
        if (player.isSpectator() || !player.isAlive()) return;
        if (occupied(player, pos)) {
            RoomsDebug.log(player, "机の下は使用中よ", "table enter denied pos={} player={} (occupied)", pos, player.getGameProfile().getName());
            return;
        }
        BlockPos exitPos = player.blockPosition().equals(pos) ? pos.relative(player.getDirection().getOpposite()) : player.blockPosition().immutable();
        HIDING.put(player.getUUID(), new Hide(Spot.TABLE, player.serverLevel(), pos.immutable(), exitPos, !player.isShiftKeyDown()));
        player.stopRiding();
        player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 c = inside(pos);
        player.teleportTo(player.serverLevel(), c.x, c.y, c.z, player.getYRot(), 0f);
        RoomsDebug.log(player, "机の下に隠れたわ", "table enter pos={} exit={} player={}", pos, exitPos, player.getGameProfile().getName());
    }

    public static void toggle(ServerPlayer player, BlockPos base, Direction facing) {
        Hide cur = HIDING.get(player.getUUID());
        if (cur != null) {
            exit(player, "right-click");
            return;
        }
        if (player.isSpectator() || !player.isAlive()) return;
        if (occupied(player, base)) {
            RoomsDebug.log(player, "ロッカーは使用中よ", "locker enter denied pos={} player={} (occupied)", base, player.getGameProfile().getName());
            return;
        }
        HIDING.put(player.getUUID(), new Hide(Spot.LOCKER, player.serverLevel(), base.immutable(), base.relative(facing), !player.isShiftKeyDown()));
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 c = inside(base);
        player.teleportTo(player.serverLevel(), c.x, c.y, c.z, facing.toYRot(), 0f);
        RoomsDebug.log(player, "ロッカーに入ったわ", "locker enter pos={} facing={} player={}", base, facing, player.getGameProfile().getName());
    }

    private static Vec3 inside(BlockPos base) {
        return new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
    }

    /** ロッカーから出して正面のマスに置く。 */
    public static void exit(ServerPlayer player, String reason) {
        Hide h = HIDING.remove(player.getUUID());
        if (h == null) return;
        if (h.spot == Spot.TABLE) player.setForcedPose(null);
        BlockPos out = h.exitPos;
        if (player.level() == h.level && player.isAlive()) {
            player.teleportTo(h.level, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        String what = h.spot == Spot.TABLE ? "机の下" : "ロッカー";
        RoomsDebug.log(player, what + "から出たわ（" + reason + "）", "{} exit pos={} reason={} player={}", h.spot, h.pos, reason, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Hide h = HIDING.get(player.getUUID());
        if (h == null) return;
        BlockState st = h.level.getBlockState(h.pos);
        boolean present = h.spot == Spot.TABLE ? st.getBlock() instanceof com.example.roomsmod.block.HidingTableBlock : st.getBlock() instanceof LockerBlock;
        if (player.level() != h.level || !present) {
            exit(player, "spot gone");
            return;
        }
        // 入る時にスニークしていた場合は、一度離すまでスニーク退出しない
        if (!h.sneakReleased) {
            if (!player.isShiftKeyDown()) HIDING.put(player.getUUID(), h.withSneakReleased());
        } else if (player.isShiftKeyDown()) {
            exit(player, "sneak");
            return;
        }
        if (h.spot == Spot.TABLE && player.getForcedPose() != net.minecraft.world.entity.Pose.SWIMMING)
            player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        Vec3 c = inside(h.pos);
        player.setDeltaMovement(Vec3.ZERO);
        if (player.position().distanceToSqr(c) > 0.01) {
            player.teleportTo(h.level, c.x, c.y, c.z, player.getYRot(), player.getXRot());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) exit(sp, "logout");
    }

    /** 状態が失われた（サーバー停止中など）まま中でログインした場合は外へ出す。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || HIDING.containsKey(sp.getUUID())) return;
        BlockPos feet = sp.blockPosition();
        BlockState st = sp.level().getBlockState(feet);
        if (st.getBlock() instanceof LockerBlock && st.getValue(LockerBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            BlockPos out = feet.relative(st.getValue(LockerBlock.FACING));
            sp.teleportTo(sp.serverLevel(), out.getX() + 0.5, out.getY(), out.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            RoomsDebug.log("locker login-inside fix pos={} player={}", feet, sp.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Hide h = HIDING.remove(event.getEntity().getUUID());
        if (h != null && h.spot == Spot.TABLE) event.getEntity().setForcedPose(null);
        if (h != null)
            RoomsDebug.log("hiding exit reason=dimension change player={}", event.getEntity().getName().getString());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player p && HIDING.containsKey(p.getUUID())) {
            if (HIDING.remove(p.getUUID()).spot == Spot.TABLE) p.setForcedPose(null);
            RoomsDebug.log("hiding exit reason=death player={}", p.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { HIDING.clear(); }
}
