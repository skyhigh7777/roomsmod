package com.example.roomsmod.event;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.entity.A20Entity;
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
    /** A-20 が出現する、プレイヤーから何部屋後ろか。 */
    public static final int A20_ROOMS_BEHIND = 10;

    // ラッシャー出現率（調整用）
    public static final int A20_MIN_ROOM = 20;
    public static final float A20_CHANCE = 0.6f;
    public static final int A40_MIN_ROOM = 40;
    public static final float A40_CHANCE = 0.25f;

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

        try {
            rollRushers(level, rs, event.getEntity(), room);
        } catch (RuntimeException e) {
            RoomsDebug.error("rusher spawn failed", e);
        }
    }

    /**
     * ラッシャー抽選。A-20 と A-40 はそれぞれ独立に抽選し、同じ部屋で両方出ることもある。
     * 同じ種類は同時に1体まで（既に生存中ならその種類だけスキップ）。
     */
    private static void rollRushers(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, int room) {
        boolean a20Hit = room >= A20_MIN_ROOM && level.random.nextFloat() < A20_CHANCE;
        boolean a40Hit = room >= A40_MIN_ROOM && level.random.nextFloat() < A40_CHANCE;
        boolean a20Alive = isA20Alive(level);
        boolean a40Alive = isA40Alive(level);
        RoomsDebug.log("rusher roll room={} A-20={} (room>={}, {}) alive={} | A-40={} (room>={}, {}) alive={}",
            room, a20Hit, A20_MIN_ROOM, A20_CHANCE, a20Alive, a40Hit, A40_MIN_ROOM, A40_CHANCE, a40Alive);
        if (a20Hit) {
            if (a20Alive) RoomsDebug.log("rusher roll room={} A-20 skipped: an A-20 is already alive", room);
            else spawnA20(level, rs, player);
        }
        if (a40Hit) {
            if (a40Alive) RoomsDebug.log("rusher roll room={} A-40 skipped: an A-40 is already alive", room);
            else if (!spawnA40(level, rs, player)) RoomsDebug.log("rusher roll room={} A-40 spawn failed", room);
        }
    }

    public static boolean isA20Alive(ServerLevel level) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof A20Entity && e.isAlive()) return true;
        }
        return false;
    }

    public static boolean isA40Alive(ServerLevel level) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof com.example.roomsmod.entity.A40Entity && e.isAlive()) return true;
        }
        return false;
    }

    public static boolean spawnA40(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return false;
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A20_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) { RoomsDebug.warn("A-40 spawn skipped: empty path at room index {}", startIndex); return false; }
        com.example.roomsmod.entity.A40Entity a40 = RoomsMod.A40.get().create(level);
        if (a40 == null) { RoomsDebug.warn("A-40 spawn failed: entity create returned null"); return false; }
        BlockPos start = startPath.get(0);
        double x = start.getX() + 0.5, y = start.getY(), z = start.getZ() + 0.5;
        a40.setPos(x, y, z);
        a40.setStartRoom(startIndex);
        a40.setRoomsBack(com.example.roomsmod.entity.A40Entity.DEFAULT_ROOMS_BACK);
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, a40, new net.minecraft.world.level.ChunkPos(start));
        if (!level.addFreshEntity(a40)) { RoomsDebug.warn("A-40 spawn failed: addFreshEntity returned false"); return false; }
        RoomsDebug.log(player,
            "A-40が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", x) + ", Z=" + String.format("%.0f", z) + ")",
            "A-40 spawn roomIndex={} pos=({}, {}, {}) roomsBack={}", startIndex, x, y, z, com.example.roomsmod.entity.A40Entity.DEFAULT_ROOMS_BACK);
        return true;
    }

    public static void spawnA20(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return;
        // プレイヤーがいるのは直前の部屋（index = room - 1）。そこから約N部屋後ろ。
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A20_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) { RoomsDebug.warn("A-20 spawn skipped: empty path at room index {}", startIndex); return; }
        A20Entity a20 = RoomsMod.A20.get().create(level);
        if (a20 == null) { RoomsDebug.warn("A-20 spawn failed: entity create returned null"); return; }
        BlockPos start = startPath.get(0);
        double spawnX = start.getX() + 0.5, spawnY = start.getY(), spawnZ = start.getZ() + 0.5;
        a20.setPos(spawnX, spawnY, spawnZ);
        a20.setStartRoom(startIndex);

        // 出現位置のチャンクを（このA-20のチケットで）読み込んでから追加する
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, a20, new net.minecraft.world.level.ChunkPos(start));
        if (!level.addFreshEntity(a20)) { RoomsDebug.warn("A-20 spawn failed: addFreshEntity returned false"); return; }
        int pathLen = 0;
        for (int i = startIndex; i < rs.roomPaths.size(); i++) pathLen += rs.roomPaths.get(i).size();
        RoomsDebug.log(player,
            "A-20が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", spawnX) + ", Z=" + String.format("%.0f", spawnZ) + ")",
            "A-20 spawn roomIndex={} pos=({}, {}, {}) pathLength={}", startIndex, spawnX, spawnY, spawnZ, pathLen);
    }
}
