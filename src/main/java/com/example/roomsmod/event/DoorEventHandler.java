package com.example.roomsmod.event;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.entity.A60Entity;
import com.example.roomsmod.world.RoomGenerator;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * 出口扉の右クリックで次の部屋を生成する。
 * 状態は RoomsState（SavedData）に永続化。リセットはロビー生成（/rooms enter, /rooms to）時のみ。
 */
public class DoorEventHandler {
    public static final int baseFloorY = 1;
    /** A-60 が出現する、プレイヤーから何部屋後ろか。 */
    public static final int A60_ROOMS_BEHIND = 10;

    // ラッシャー出現率（調整用）
    public static final int A60_MIN_ROOM = 10;
    public static final float A60_CHANCE = 0.6f;
    public static final int A75_MIN_ROOM = 15;
    public static final float A75_CHANCE = 0.25f;
    /** 当選したが出せなかった A-75（メモリのみ。サーバー再起動で消える）。 */
    private static boolean pendingA75 = false;

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        // MAIN_HAND / OFF_HAND の2回発火を防ぐ
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;

        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof DoorBlock)) return;

        // 下半分に正規化
        BlockPos doorPos = event.getPos().immutable();
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) doorPos = doorPos.below();

        RoomsState rs = RoomsState.get(level);
        // 現在の部屋の出口扉以外（使用済みの扉など）は普通に開閉するだけ
        if (rs.expectedExitDoor == null || !rs.expectedExitDoor.equals(doorPos)) {
            RoomsDebug.log(event.getEntity(), "扉 " + doorPos.toShortString() + " 無視（出口扉ではない）",
                "door click pos={} hand={} expectedExitDoor={} -> ignored (not expected exit door)", doorPos, event.getHand(), rs.expectedExitDoor);
            return;
        }
        if (rs.generatedDoors.contains(doorPos)) {
            RoomsDebug.log(event.getEntity(), "扉 " + doorPos.toShortString() + " 無視（生成済み）",
                "door click pos={} hand={} expectedExitDoor={} -> ignored (already generated)", doorPos, event.getHand(), rs.expectedExitDoor);
            return;
        }

        rs.markDoorGenerated(doorPos);
        int room = rs.currentRoom + 1;
        BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, baseFloorY, doorPos.getZ() + 1);
        try {
            RoomGenerator.generateNextRoom(level, nextOrigin, room); // currentRoom / expectedExitDoor を更新
        } catch (RuntimeException e) {
            RoomsDebug.error("room generation failed (room=" + room + ", origin=" + nextOrigin + ")", e);
            return;
        }
        RoomsDebug.log(event.getEntity(), "次の部屋を生成したわ（A-" + String.format("%03d", room) + "）",
            "door click pos={} hand={} expectedExitDoor={} -> generated room {} at {}", doorPos, event.getHand(), doorPos, room, nextOrigin);

        // 同時に複数のラッシャーは出さない
        try {
            rollRushers(level, rs, event.getEntity(), room);
        } catch (RuntimeException e) {
            RoomsDebug.error("rusher spawn failed", e);
        }
    }

    /**
     * ラッシャー抽選。A-60 と A-75 はそれぞれ独立に抽選し、両方当たったら50/50で片方。
     * A-75 が当たったのに他のラッシャー生存中で出せなかった場合は保留し、次の部屋でラッシャーがいなければ出す。
     */
    private static void rollRushers(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, int room) {
        boolean alive = isRusherAlive(level);
        if (pendingA75) {
            if (alive) {
                RoomsDebug.log("rusher room={} A-75 pending still blocked: another rusher is alive", room);
            } else {
                RoomsDebug.log("rusher room={} spawning pending A-75", room);
                if (spawnA75(level, rs, player)) pendingA75 = false;
                return; // 保留分を出した部屋では新規抽選しない
            }
        }

        boolean a60Hit = room >= A60_MIN_ROOM && level.random.nextFloat() < A60_CHANCE;
        boolean a75Hit = room >= A75_MIN_ROOM && level.random.nextFloat() < A75_CHANCE;
        RoomsDebug.log("rusher roll room={} A-60={} (room>={}, {}) A-75={} (room>={}, {}) rusherAlive={}",
            room, a60Hit, A60_MIN_ROOM, A60_CHANCE, a75Hit, A75_MIN_ROOM, A75_CHANCE, alive);
        if (a60Hit && a75Hit) {
            if (level.random.nextBoolean()) a75Hit = false; else a60Hit = false;
            RoomsDebug.log("rusher roll room={} both hit -> picked {}", room, a60Hit ? "A-60" : "A-75");
        }
        if (!a60Hit && !a75Hit) return;

        if (alive) {
            if (a75Hit) {
                pendingA75 = true;
                RoomsDebug.log("rusher roll room={} A-75 blocked by alive rusher -> pending", room);
            } else {
                RoomsDebug.log("rusher roll room={} A-60 skipped: another rusher is alive", room);
            }
            return;
        }
        if (a60Hit) {
            spawnA60(level, rs, player);
        } else if (!spawnA75(level, rs, player)) {
            pendingA75 = true;
            RoomsDebug.log("rusher roll room={} A-75 spawn failed -> pending", room);
        }
    }

    /** A-60 または A-75 が生存中か。 */
    public static boolean isRusherAlive(ServerLevel level) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if ((e instanceof A60Entity || e instanceof com.example.roomsmod.entity.A75Entity) && e.isAlive()) return true;
        }
        return false;
    }

    public static boolean spawnA75(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return false;
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A60_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) { RoomsDebug.warn("A-75 spawn skipped: empty path at room index {}", startIndex); return false; }
        com.example.roomsmod.entity.A75Entity a75 = RoomsMod.A75.get().create(level);
        if (a75 == null) { RoomsDebug.warn("A-75 spawn failed: entity create returned null"); return false; }
        BlockPos start = startPath.get(0);
        double x = start.getX() + 0.5, y = start.getY(), z = start.getZ() + 0.5;
        a75.setPos(x, y, z);
        a75.setStartRoom(startIndex);
        a75.setRoomsBack(com.example.roomsmod.entity.A75Entity.DEFAULT_ROOMS_BACK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.setChunkForced((start.getX() >> 4) + dx, (start.getZ() >> 4) + dz, true);
        level.addFreshEntity(a75);
        RoomsDebug.log(player,
            "A-75が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", x) + ", Z=" + String.format("%.0f", z) + ")",
            "A-75 spawn roomIndex={} pos=({}, {}, {}) roomsBack={}", startIndex, x, y, z, com.example.roomsmod.entity.A75Entity.DEFAULT_ROOMS_BACK);
        return true;
    }

    public static void spawnA60(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return;
        // プレイヤーがいるのは直前の部屋（index = room - 1）。そこから約N部屋後ろ。
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A60_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) { RoomsDebug.warn("A-60 spawn skipped: empty path at room index {}", startIndex); return; }
        A60Entity a60 = RoomsMod.A60.get().create(level);
        if (a60 == null) { RoomsDebug.warn("A-60 spawn failed: entity create returned null"); return; }
        BlockPos start = startPath.get(0);
        double spawnX = start.getX() + 0.5, spawnY = start.getY(), spawnZ = start.getZ() + 0.5;
        a60.setPos(spawnX, spawnY, spawnZ);
        a60.setStartRoom(startIndex);

        // 出現位置のチャンクを強制読み込み
        int chunkX = start.getX() >> 4;
        int chunkZ = start.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(chunkX + dx, chunkZ + dz, true);
            }
        }

        level.addFreshEntity(a60);
        int pathLen = 0;
        for (int i = startIndex; i < rs.roomPaths.size(); i++) pathLen += rs.roomPaths.get(i).size();
        RoomsDebug.log(player,
            "A-60が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", spawnX) + ", Z=" + String.format("%.0f", spawnZ) + ")",
            "A-60 spawn roomIndex={} pos=({}, {}, {}) pathLength={}", startIndex, spawnX, spawnY, spawnZ, pathLen);
    }
}
