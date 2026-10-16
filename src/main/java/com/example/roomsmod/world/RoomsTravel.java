package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * /rooms to とロビーの GO パネル：ロビーを作り直し、部屋 1..target を1 tick あたり ROOMS_PER_TICK 部屋ずつ生成して、
 * 終わったら最後の部屋の安全な床へワープ（数百部屋を1 tick で作るとサーバーが止まるため）。同時に1件まで。
 */
@EventBusSubscriber(modid = "roomsmod")
public final class RoomsTravel {
    private RoomsTravel() {}

    public static final int ROOMS_PER_TICK = 8;

    private static ServerLevel level;
    private static UUID player;
    private static int target, next;
    private static BlockPos currentExit;
    private static long startedMs;

    public static boolean busy() { return level != null; }

    public static boolean start(ServerLevel lv, ServerPlayer p, int n) {
        if (busy()) { p.displayClientMessage(Component.literal("いま部屋を生成中よ、少し待ってね"), true); return false; }
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos lobbyExit = RoomGenerator.generateLobby(lv, origin);
        RoomsState.get(lv).markDoorGenerated(lobbyExit);
        level = lv; player = p.getUUID(); target = n; next = 1; startedMs = System.currentTimeMillis();
        currentExit = lobbyExit != null ? lobbyExit : origin.offset(10, 1, 31);
        RoomsDebug.log("rooms to {}: start by {} ({} rooms/tick)", n, p.getGameProfile().getName(), ROOMS_PER_TICK);
        step();
        return true;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) { if (busy()) step(); }

    private static void step() {
        RoomsState state = RoomsState.get(level);
        for (int k = 0; k < ROOMS_PER_TICK && next <= target; k++, next++) {
            BlockPos nextOrigin = new BlockPos(currentExit.getX() - 10, currentExit.getY() - 1, currentExit.getZ() + 1);
            BlockPos exitDoor = RoomGenerator.generateNextRoom(level, nextOrigin, next);
            if (exitDoor != null) {
                currentExit = exitDoor;
                if (next < target) state.markDoorGenerated(exitDoor);
            }
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        if (next <= target) {
            if (p != null && (next - 1) % 40 < ROOMS_PER_TICK) p.displayClientMessage(Component.literal("部屋を生成中… " + (next - 1) + " / " + target), true);
            return;
        }
        // 生成器が expectedExitDoor = 最後の部屋の出口 に設定済み
        state.setCurrentRoom(target);
        // 部屋の床の上の安全な場所へ（V の部屋は2階建てで床の高さが変わるので固定 y は使わない）
        Vec3 at = SafeSpot.find(level, state, target);
        if (at == null) at = new Vec3(currentExit.getX() + 0.5, currentExit.getY(), currentExit.getZ() - 0.5);
        RoomsDebug.log("rooms to {}: teleport to ({}, {}, {}) after {} ms", target, at.x, at.y, at.z, System.currentTimeMillis() - startedMs);
        if (p != null) {
            p.teleportTo(level, at.x, at.y, at.z, 0, 0);
            p.sendSystemMessage(Component.literal(Sections.formatRoom(state, target) + " まで生成してワープしたわ"));
        }
        level = null; player = null;
    }
}
