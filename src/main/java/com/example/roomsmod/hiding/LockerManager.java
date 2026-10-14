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
    private record Hide(ServerLevel level, BlockPos pos, Direction facing, boolean sneakReleased) {
        Hide withSneakReleased() { return new Hide(level, pos, facing, true); }
    }

    private static final Map<UUID, Hide> HIDING = new ConcurrentHashMap<>();

    private LockerManager() {}

    public static boolean isInLocker(Player player) {
        return !player.level().isClientSide() && HIDING.containsKey(player.getUUID());
    }

    public static void toggle(ServerPlayer player, BlockPos base, Direction facing) {
        Hide cur = HIDING.get(player.getUUID());
        if (cur != null) {
            exit(player, "right-click");
            return;
        }
        if (player.isSpectator() || !player.isAlive()) return;
        for (Hide h : HIDING.values()) {
            if (h.level == player.serverLevel() && h.pos.equals(base)) {
                RoomsDebug.log(player, "ロッカーは使用中よ", "locker enter denied pos={} player={} (occupied)", base, player.getGameProfile().getName());
                return;
            }
        }
        HIDING.put(player.getUUID(), new Hide(player.serverLevel(), base.immutable(), facing, !player.isShiftKeyDown()));
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
        BlockPos out = h.pos.relative(h.facing);
        if (player.level() == h.level && player.isAlive()) {
            player.teleportTo(h.level, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        RoomsDebug.log(player, "ロッカーから出たわ（" + reason + "）", "locker exit pos={} reason={} player={}", h.pos, reason, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Hide h = HIDING.get(player.getUUID());
        if (h == null) return;
        BlockState st = h.level.getBlockState(h.pos);
        if (player.level() != h.level || !(st.getBlock() instanceof LockerBlock)) {
            exit(player, "locker gone");
            return;
        }
        // 入る時にスニークしていた場合は、一度離すまでスニーク退出しない
        if (!h.sneakReleased) {
            if (!player.isShiftKeyDown()) HIDING.put(player.getUUID(), h.withSneakReleased());
        } else if (player.isShiftKeyDown()) {
            exit(player, "sneak");
            return;
        }
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
        if (HIDING.remove(event.getEntity().getUUID()) != null)
            RoomsDebug.log("locker exit reason=dimension change player={}", event.getEntity().getName().getString());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player p && HIDING.remove(p.getUUID()) != null)
            RoomsDebug.log("locker exit reason=death player={}", p.getName().getString());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { HIDING.clear(); }
}
