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
    // 出現する部屋・確率は SectionTheme.rushers()（Sections.A）

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        // MAIN_HAND / OFF_HAND の2回発火を防ぐ
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;

        BlockState state = level.getBlockState(event.getPos());
        // このディメンションはベッドが爆発する（bed_works=false）ので、飾りのベッドは使えないようにする
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) { event.setCanceled(true); return; }
        if (!(state.getBlock() instanceof DoorBlock)) return;

        // 下半分に正規化
        BlockPos doorPos = event.getPos().immutable();
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) doorPos = doorPos.below();

        RoomsState rs = RoomsState.get(level);
        // V-50 の待機中に、潜んでいる部屋の扉を開けたら罰（隠れ・視線無視）
        if (!state.getValue(DoorBlock.OPEN))
            com.example.roomsmod.entity.v.V50Entity.onDoorOpened(level, rs, event.getEntity(), doorPos);
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
        BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, doorPos.getY() - 1, doorPos.getZ() + 1); // 扉の床の高さ（V の2階建てで上がる）
        try {
            RoomGenerator.generateNextRoom(level, nextOrigin, room); // currentRoom / expectedExitDoor を更新
        } catch (RuntimeException e) {
            RoomsDebug.error("room generation failed (room=" + room + ", origin=" + nextOrigin + ")", e);
            return;
        }
        RoomsDebug.log(event.getEntity(), "次の部屋を生成したわ（" + com.example.roomsmod.world.section.Sections.formatRoom(rs, room) + "）",
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
        rollRushers(level, rs, player, room, false);
    }

    /** forceAll=true は全種類を当たり扱い（minRoom は無視しない）。テスト用。 */
    public static void rollRushers(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, int room, boolean forceAll) {
        var theme = com.example.roomsmod.world.section.Sections.forRoom(rs, room);
        var table = theme.rushers();
        int local = com.example.roomsmod.world.section.Sections.localRoom(rs, room); // minRoom はセクション内番号で判定
        boolean[] hit = new boolean[table.size()], alive = new boolean[table.size()];
        // 抽選→生存確認→出現の順（旧実装と同じ乱数消費・同じ判定順）
        for (int k = 0; k < table.size(); k++) {
            var r = table.get(k);
            hit[k] = local >= r.minRoom() && (level.random.nextFloat() < r.chance() || forceAll);
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < table.size(); k++) {
            var r = table.get(k);
            alive[k] = r.alive().test(level);
            sb.append(k == 0 ? "" : " | ").append(r.name()).append('=').append(hit[k])
              .append(" (room>=").append(r.minRoom()).append(", ").append(r.chance()).append(") alive=").append(alive[k]);
        }
        RoomsDebug.log("rusher roll room={} local={} section={} {}", room, com.example.roomsmod.world.section.Sections.formatRoom(rs, room), theme.prefix(), sb);
        // 排他グループ：同じ抽選で複数当たったら1つだけランダムに選ぶ（生存中のメンバーがいれば alive で全員スキップ）
        java.util.Map<String, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        for (int k = 0; k < table.size(); k++)
            if (hit[k] && !alive[k] && table.get(k).exclusionGroup() != null)
                groups.computeIfAbsent(table.get(k).exclusionGroup(), g -> new java.util.ArrayList<>()).add(k);
        for (var e : groups.entrySet()) {
            var c = e.getValue();
            if (c.size() < 2) continue;
            int win = c.get(level.random.nextInt(c.size()));
            for (int k : c) if (k != win) {
                hit[k] = false;
                RoomsDebug.log("rusher roll room={} {} skipped: exclusion group {} ({} spawned this roll)", room, table.get(k).name(), e.getKey(), table.get(win).name());
            }
        }
        for (int k = 0; k < table.size(); k++) {
            if (!hit[k]) continue;
            var r = table.get(k);
            if (alive[k]) RoomsDebug.log("rusher roll room={} {} skipped: already alive", room, r.name());
            else if (!r.spawn().spawn(level, rs, player)) RoomsDebug.log("rusher roll room={} {} spawn failed", room, r.name());
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
