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
    /** B-20 が出現する、プレイヤーから何部屋後ろか。 */
    public static final int A20_ROOMS_BEHIND = 10;
    /** GK 区画で1回の抽選で出る最大数。 */
    public static final int GK_MAX_PER_ROLL = 2;
    /** ハードモードの1回の抽選（扉1枚）で出る最大数。 */
    public static final int HARD_MAX_PER_ROLL = com.example.roomsmod.world.HardMode.MAX_PER_ROLL;

    // ラッシャー出現率（調整用）
    // 出現する部屋・確率は SectionTheme.rushers()（Sections.B）

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        // ロビーのパネル：ブロック操作禁止の例外（パネルとして処理し、ブロック本来の操作・看板の編集はさせない）
        if (com.example.roomsmod.world.LobbyPanels.isPanel(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND
                    && event.getLevel() instanceof ServerLevel sl && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)
                com.example.roomsmod.world.LobbyPanels.click(sl, sp, event.getPos().immutable());
            return;
        }
        // 一度開いた扉は閉められない（両手・上下どちらの半分も、クライアントでも取り消して予測で閉じないようにする）
        if (event.getLevel().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) {
            BlockState s0 = event.getLevel().getBlockState(event.getPos());
            if (s0.getBlock() instanceof DoorBlock && s0.getValue(DoorBlock.OPEN)) {
                BlockPos dp = s0.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? event.getPos().below() : event.getPos();
                if (com.example.roomsmod.entity.gk.GKDoorTricks.closableOpenDoor(event.getLevel(), dp)) {
                    // GK-125 が見張る扉だけは閉められる（サーバーで閉める）
                    event.setCanceled(true);
                    if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND && event.getLevel() instanceof ServerLevel sl)
                        com.example.roomsmod.entity.gk.GKDoorTricks.onDoorClick(sl, RoomsState.get(sl), event.getEntity(), dp);
                    return;
                }
                event.setCanceled(true);
                if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND && event.getLevel() instanceof ServerLevel sl) {
                    RoomsState rs0 = RoomsState.get(sl);
                    // 詰み防止：開いているのに次の部屋ができていない出口扉（生成失敗・扉だけ開いた等）は、もう一度生成を試す
                    if (dp.equals(rs0.expectedExitDoor)) {
                        rs0.generatedDoors.remove(dp);
                        RoomsDebug.log(event.getEntity(), "開いた出口扉の先に部屋がないので生成し直すわ", "door click pos={} -> open exit door without next room: retry generation", dp);
                        advanceThroughExitDoor(sl, rs0, event.getEntity(), dp, "stuck open exit door retry");
                        return;
                    }
                    // 出口扉（生成済み）以外の部屋の中の扉は閉められる（古いワールドで向きが逆の内扉が開いて塞がった場合の救済も兼ねる）
                    if (!rs0.generatedDoors.contains(dp)) {
                        BlockState ds = sl.getBlockState(dp);
                        if (ds.getBlock() instanceof DoorBlock db && ds.getValue(DoorBlock.OPEN)) {
                            sl.setBlock(dp, ds.setValue(DoorBlock.OPEN, false), 10);
                            sl.playSound(null, dp, db.type().doorClose(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, sl.getRandom().nextFloat() * 0.1f + 0.9f);
                            RoomsDebug.log(event.getEntity(), "扉を閉めたわ", "door click pos={} -> closed (inner door, not a generated exit)", dp);
                            return;
                        }
                    }
                    RoomsDebug.log(event.getEntity(), "扉は開いたままよ", "door click pos={} -> cancelled (already open)", event.getPos());
                }
                return;
            }
        }
        // 部屋ディメンションでは、Mod 自身の操作できるブロック（ロッカー・隠れテーブル・まだ閉じている扉）以外の右クリックを全て取り消す
        //（作業台・かまど・チェスト・樽・金床・ベッド・トラップドア・フェンスゲート・音符ブロック等の GUI や操作を防ぐ）。両手・サーバー/クライアント両方。
        if (event.getLevel().dimension().equals(RoomsCommands.ROOMS_DIMENSION) && !isAllowedInteraction(event.getLevel().getBlockState(event.getPos()))) {
            if (event.getItemStack().getItem() instanceof com.example.roomsmod.item.FlashlightItem) {
                // 懐中電灯：ブロックの操作だけ禁止して、アイテムの使用（ON/OFF）は通す
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                return;
            }
            event.setCanceled(true);
            if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND)
                RoomsDebug.log("block use cancelled pos={} block={} player={}", event.getPos(), event.getLevel().getBlockState(event.getPos()).getBlock(), event.getEntity().getGameProfile().getName());
            return;
        }
        if (event.getLevel().isClientSide()) return;
        // MAIN_HAND / OFF_HAND の2回発火を防ぐ
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;

        BlockState state = level.getBlockState(event.getPos());
        // このディメンションはベッドが爆発する（bed_works=false）ので、飾りのベッドは使えないようにする
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) { event.setCanceled(true); return; }
        if (state.getBlock() instanceof net.minecraft.world.level.block.BellBlock) { com.example.roomsmod.entity.gk.GKDoorTricks.onBell(level, event.getEntity(), event.getPos().immutable()); return; }
        if (!(state.getBlock() instanceof DoorBlock)) return;

        // 下半分に正規化
        BlockPos doorPos = event.getPos().immutable();
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) doorPos = doorPos.below();

        RoomsState rs = RoomsState.get(level);
        // V-50 の待機中に、潜んでいる部屋の扉を開けたら罰（隠れ・視線無視）
        // GK の扉の仕掛け（施錠・偽の扉）：扉を開けさせない
        if (!state.getValue(DoorBlock.OPEN) && com.example.roomsmod.entity.gk.GKDoorTricks.onDoorClick(level, rs, event.getEntity(), doorPos)) {
            event.setCanceled(true);
            return;
        }
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

        advanceThroughExitDoor(level, rs, event.getEntity(), doorPos, "door click hand=" + event.getHand());
    }

    /** 部屋ディメンションで右クリックしてよいブロック（許可リスト）。開いた扉は手前で取り消し済み。 */
    /** ロビーのパネルは壊せない（クリエイティブでも）。 */
    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level l && com.example.roomsmod.world.LobbyPanels.isPanel(l, event.getPos())) {
            event.setCanceled(true);
            RoomsDebug.log("lobby panel break cancelled pos={} player={}", event.getPos(), event.getPlayer().getGameProfile().getName());
        }
    }

    public static boolean isAllowedInteraction(BlockState s) {
        var b = s.getBlock();
        return b instanceof com.example.roomsmod.block.LockerBlock
                || b instanceof com.example.roomsmod.block.HidingTableBlock
                || b instanceof net.minecraft.world.level.block.BellBlock // GK-25 のベル
                || b instanceof DoorBlock;
    }

    /**
     * 出口扉を通って次へ：扉を生成済みにし（以後は普通の扉）、次の部屋を生成し、出現抽選をする。
     * プレイヤーのクリックと V-50 の飛び出し（扉を開ける）で共用。player は null 可。
     */
    /** エンティティ（B-20・V-50 など）が扉を開ける：プレイヤーと同じ開く音を鳴らして開け、出口扉なら次の部屋を生成して抽選。 */
    public static boolean openDoorByEntity(ServerLevel level, RoomsState rs, net.minecraft.world.entity.Entity by, BlockPos door, String what) {
        BlockState s = level.getBlockState(door);
        if (!(s.getBlock() instanceof DoorBlock db)) return false;
        if (!s.getValue(DoorBlock.OPEN)) {
            // バニラの setOpen と同じ状態変更（上下とも）＋ゲームイベント。音は source なしで全員に鳴らす（プレイヤーが開けたときと同じ音・音量・ピッチ）
            level.setBlock(door, s.setValue(DoorBlock.OPEN, true), 10);
            level.gameEvent(by, net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN, door);
            level.playSound(null, door, db.type().doorOpen(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.1f + 0.9f);
            RoomsDebug.log("{} door open sound at {}", what, door);
        }
        return advanceThroughExitDoor(level, rs, null, door, what);
    }

    public static boolean advanceThroughExitDoor(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, BlockPos doorPos, String by) {
        if (rs.expectedExitDoor == null || !rs.expectedExitDoor.equals(doorPos) || rs.generatedDoors.contains(doorPos)) return false;
        if (player == null) {
            // エンティティが開けた：扉に一番近いプレイヤーを基準に（部屋の判定・デバッグ通知はプレイヤーが開けたときと同じ）
            player = level.getNearestPlayer(doorPos.getX() + 0.5, doorPos.getY(), doorPos.getZ() + 0.5, -1, false);
            if (player != null) RoomsDebug.log(player, by + "（" + doorPos.toShortString() + "）", "{} pos={} -> notify {}", by, doorPos, player.getGameProfile().getName());
        }
        rs.markDoorGenerated(doorPos);
        int room = rs.currentRoom + 1;
        BlockPos nextOrigin = new BlockPos(doorPos.getX() - 10, doorPos.getY() - 1, doorPos.getZ() + 1); // 扉の床の高さ（V の2階建てで上がる）
        try {
            RoomGenerator.generateNextRoom(level, nextOrigin, room); // currentRoom / expectedExitDoor を更新
        } catch (RuntimeException e) {
            RoomsDebug.error("room generation failed (room=" + room + ", origin=" + nextOrigin + ")", e);
            rs.generatedDoors.remove(doorPos); // もう一度クリックすれば再試行できるように
            return false;
        }
        if (player != null) RoomsDebug.log(player, "次の部屋を生成したわ（" + com.example.roomsmod.world.section.Sections.formatRoom(rs, room) + "）",
            "{} pos={} -> generated room {} at {}", by, doorPos, room, nextOrigin);
        else RoomsDebug.log("{} pos={} -> generated room {} at {}", by, doorPos, room, nextOrigin);

        if (rs.hardMode) com.example.roomsmod.world.HardMode.announceRoom(level, rs, room);
        try {
            rollRushers(level, rs, player, room);
        } catch (RuntimeException e) {
            RoomsDebug.error("rusher spawn failed", e);
        }
        return true;
    }

    /**
     * ラッシャー抽選。B-20 と B-40 はそれぞれ独立に抽選し、同じ部屋で両方出ることもある。
     * 同じ種類は同時に1体まで（既に生存中ならその種類だけスキップ）。
     */
    private static void rollRushers(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, int room) {
        rollRushers(level, rs, player, room, false);
    }

    /** forceAll=true は全種類を当たり扱い（minRoom は無視しない）。テスト用。 */
    public static void rollRushers(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, int room, boolean forceAll) {
        var theme = com.example.roomsmod.world.section.Sections.forRoom(rs, room);
        boolean hard = rs.hardMode;
        var table = hard ? com.example.roomsmod.world.section.Sections.hardTable() : theme.rushers();
        int local = com.example.roomsmod.world.section.Sections.localRoom(rs, room); // minRoom はセクション内番号で判定
        boolean[] hit = new boolean[table.size()], alive = new boolean[table.size()];
        // 抽選→生存確認→出現の順（旧実装と同じ乱数消費・同じ判定順）
        for (int k = 0; k < table.size(); k++) {
            var r = table.get(k);
            float chance = hard ? Math.min(1f, r.chance() * com.example.roomsmod.world.HardMode.CHANCE_MUL) : r.chance(); // ハード：確率 x1.5
            hit[k] = local >= r.minRoom() && (level.random.nextFloat() < chance || forceAll);
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < table.size(); k++) {
            var r = table.get(k);
            alive[k] = r.alive().test(level);
            sb.append(k == 0 ? "" : " | ").append(r.name()).append('=').append(hit[k])
              .append(" (room>=").append(r.minRoom()).append(", ").append(r.chance()).append(") alive=").append(alive[k]);
        }
        RoomsDebug.log("rusher roll room={} local={} section={} hard={} {}", room, com.example.roomsmod.world.section.Sections.formatRoom(rs, room), theme.prefix(), hard, sb);
        // 排他グループ：同じ抽選で複数当たったら1つだけランダムに選ぶ（生存中のメンバーがいれば alive で全員スキップ）
        java.util.Map<String, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        for (int k = 0; k < table.size(); k++)
            if (hit[k] && !alive[k] && table.get(k).exclusionGroup() != null)
                for (String g : table.get(k).exclusionGroup().split(","))
                    groups.computeIfAbsent(g.trim(), x -> new java.util.ArrayList<>()).add(k);
        for (var e : groups.entrySet()) {
            var c = new java.util.ArrayList<Integer>();
            for (int k : e.getValue()) if (hit[k]) c.add(k); // 先のグループで外れたものは除く
            if (c.size() < 2) continue;
            int win = c.get(level.random.nextInt(c.size()));
            for (int k : c) if (k != win) {
                hit[k] = false;
                RoomsDebug.log("rusher roll room={} {} skipped: exclusion group {} ({} spawned this roll)", room, table.get(k).name(), e.getKey(), table.get(win).name());
            }
        }
        // 分類の矛盾（SpawnCategories）：生存中＋この抽選で先に通ったものと矛盾する候補は外す。順番は公平にランダム
        {
            java.util.Map<String, String> present = new java.util.LinkedHashMap<>();
            for (String n : com.example.roomsmod.world.section.SpawnCategories.aliveNames(level)) present.put(n, "alive");
            java.util.List<Integer> c = new java.util.ArrayList<>();
            for (int k = 0; k < table.size(); k++) if (hit[k] && !alive[k]) c.add(k);
            if (!c.isEmpty()) {
                java.util.Collections.shuffle(c, new java.util.Random(level.random.nextLong()));
                for (int k : c) {
                    String name = table.get(k).name();
                    String why = com.example.roomsmod.world.section.SpawnCategories.conflict(name, present, hard);
                    if (why != null) {
                        hit[k] = false;
                        RoomsDebug.log("rusher roll room={} {} skipped: category conflict ({})", room, name, why);
                    } else present.putIfAbsent(name, "roll");
                }
            }
        }
        // GK は1回の抽選で出るのを最大 GK_MAX_PER_ROLL 体に（運だけの重なりを防ぐ）。生存中でスキップされるものは数えない
        if (hard || theme == com.example.roomsmod.world.section.Sections.GK || theme == com.example.roomsmod.world.section.Sections.B || theme == com.example.roomsmod.world.section.Sections.U) {
            int cap = hard ? HARD_MAX_PER_ROLL : GK_MAX_PER_ROLL;
            java.util.List<Integer> c = new java.util.ArrayList<>();
            for (int k = 0; k < table.size(); k++) if (hit[k] && !alive[k]) c.add(k);
            java.util.Collections.shuffle(c, new java.util.Random(level.random.nextLong()));
            for (int n = cap; n < c.size(); n++) {
                hit[c.get(n)] = false;
                RoomsDebug.log("rusher roll room={} {} skipped: per-roll cap {}", room, table.get(c.get(n)).name(), cap);
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
        if (startPath.isEmpty()) { RoomsDebug.warn("B-40 spawn skipped: empty path at room index {}", startIndex); return false; }
        com.example.roomsmod.entity.A40Entity a40 = RoomsMod.A40.get().create(level);
        if (a40 == null) { RoomsDebug.warn("B-40 spawn failed: entity create returned null"); return false; }
        BlockPos start = startPath.get(0);
        double x = start.getX() + 0.5, y = start.getY(), z = start.getZ() + 0.5;
        a40.setPos(x, y, z);
        a40.setStartRoom(startIndex);
        a40.setRoomsBack(com.example.roomsmod.entity.A40Entity.DEFAULT_ROOMS_BACK);
        a40.setHard(rs.hardMode);
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, a40, new net.minecraft.world.level.ChunkPos(start));
        if (!level.addFreshEntity(a40)) { RoomsDebug.warn("B-40 spawn failed: addFreshEntity returned false"); return false; }
        RoomsDebug.log(player,
            "B-40が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", x) + ", Z=" + String.format("%.0f", z) + ")",
            "B-40 spawn roomIndex={} pos=({}, {}, {}) roomsBack={}", startIndex, x, y, z, com.example.roomsmod.entity.A40Entity.DEFAULT_ROOMS_BACK);
        return true;
    }

    public static void spawnA20(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player) {
        if (rs.roomPaths.isEmpty()) return;
        // プレイヤーがいるのは直前の部屋（index = room - 1）。そこから約N部屋後ろ。
        int playerRoomIndex = Math.max(0, rs.roomPaths.size() - 2);
        int startIndex = Math.max(0, playerRoomIndex - A20_ROOMS_BEHIND);
        List<BlockPos> startPath = rs.roomPaths.get(startIndex);
        if (startPath.isEmpty()) { RoomsDebug.warn("B-20 spawn skipped: empty path at room index {}", startIndex); return; }
        A20Entity a20 = RoomsMod.A20.get().create(level);
        if (a20 == null) { RoomsDebug.warn("B-20 spawn failed: entity create returned null"); return; }
        BlockPos start = startPath.get(0);
        double spawnX = start.getX() + 0.5, spawnY = start.getY(), spawnZ = start.getZ() + 0.5;
        a20.setPos(spawnX, spawnY, spawnZ);
        a20.setStartRoom(startIndex);
        a20.setHard(rs.hardMode);

        // 出現位置のチャンクを（このB-20のチケットで）読み込んでから追加する
        com.example.roomsmod.entity.RusherChunks.prepareSpawn(level, a20, new net.minecraft.world.level.ChunkPos(start));
        if (!level.addFreshEntity(a20)) { RoomsDebug.warn("B-20 spawn failed: addFreshEntity returned false"); return; }
        int pathLen = 0;
        for (int i = startIndex; i < rs.roomPaths.size(); i++) pathLen += rs.roomPaths.get(i).size();
        RoomsDebug.log(player,
            "B-20が出現したわ！ (部屋index=" + startIndex + ", X=" + String.format("%.0f", spawnX) + ", Z=" + String.format("%.0f", spawnZ) + ")",
            "B-20 spawn roomIndex={} pos=({}, {}, {}) pathLength={}", startIndex, spawnX, spawnY, spawnZ, pathLen);
    }
}
