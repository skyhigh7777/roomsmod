package com.example.roomsmod.world;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ロビーの作り直し（進行表の reset：リスポーン・/rooms enter・/rooms to・GO パネル）のとき、それまでに生成した部屋のブロックを
 * 空気に戻して部屋ディメンションを最初の状態にする。
 * <ul>
 *   <li>範囲は reset 前の進行表の全部屋の範囲（{@value #MARGIN} マスの余白込み：窓の外の空気ポケットと黒コンクリート、出口のふた、扉）と、
 *       A の縦穴全体（上へ足した層・横穴・底を含む）。</li>
 *   <li>作り直したロビー（と出口のふた）と、その後に作られた今の部屋の範囲は壊さない。</li>
 *   <li>16x16x16 のチャンクセクション単位で、1 tick あたり約 {@value #BUDGET_MS} ms まで（空のセクションはすぐ飛ばす）。
 *       途中でサーバーが止まっても RoomsState に残りが保存されていて続きから。</li>
 *   <li>新しい部屋を作る前（扉を開けた時）に残りを全部片付ける（新しい部屋を壊さないため）。/rooms to は片付けが終わるまで待つ。</li>
 *   <li>片付ける範囲にいるプレイヤー（作り直しの時にロビーの外にいた人）は先にロビーへ移す（埋まったり落ちたりしない）。</li>
 *   <li>片付けた範囲のアイテム・経験値オーブ・この MOD のエンティティは消す（プレイヤーは消さない）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "roomsmod")
public final class RoomsCleanup {
    private RoomsCleanup() {}

    /** 部屋の範囲の外へ広げる余白（窓の外の空気ポケット +1・黒コンクリート +2、出口のふた +1）。 */
    public static final int MARGIN = 3;
    public static final int BUDGET_MS = 15;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    /** y=1 時代のロビー（0,1,0 から 20x32、天井 +5）と出口のふた。 */
    private static final BoundingBox LEGACY_LOBBY = new BoundingBox(0, 1, 0, 19, 6, 31);
    private static final BoundingBox LEGACY_SEAL = new BoundingBox(10, 2, 32, 10, 3, 32);

    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final Map<Long, List<BoundingBox>> bySection = new HashMap<>();
    private static RoomsState indexedFor = null;
    private static int indexed = 0;
    private static long blocks = 0, entities = 0, startedMs = 0;

    /** generateLobby から（reset の前に呼ぶ）：今の進行表の全範囲（余白込み）。 */
    static List<BoundingBox> snapshot(RoomsState st) {
        List<BoundingBox> out = new ArrayList<>();
        for (List<BoundingBox> room : st.generatedRooms) for (BoundingBox b : room) out.add(b.inflatedBy(MARGIN));
        if (st.abyss != null) out.add(shaftRegion(st.abyss));
        for (BlockPos d : st.generatedDoors) out.add(new BoundingBox(d).inflatedBy(MARGIN));
        if (st.expectedExitDoor != null) out.add(new BoundingBox(st.expectedExitDoor).inflatedBy(MARGIN));
        return out;
    }

    private static BoundingBox shaftRegion(AbyssRooms.Shaft s) {
        int top = Math.max(s.oy + 5, s.capY());
        return new BoundingBox(s.ox + AbyssRooms.X0 - MARGIN, s.bottomY - MARGIN, s.oz + AbyssRooms.Z0 - MARGIN,
                s.ox + AbyssRooms.X1 + MARGIN, top + MARGIN, s.oz + AbyssRooms.Z1 + MARGIN);
    }

    /** generateLobby から（reset と新しいロビーの登録の後）：古い範囲を片付け待ちに入れ、ロビーの外にいるプレイヤーをロビーへ。 */
    static void schedule(ServerLevel level, RoomsState st, List<BoundingBox> regions, List<BoundingBox> protect) {
        if (regions.isEmpty()) return;
        boolean was = !st.pendingClear.isEmpty();
        st.pendingClear.addAll(regions);
        for (BoundingBox b : protect) if (!st.pendingProtect.contains(b)) st.pendingProtect.add(b);
        st.setDirty();
        if (!was) { blocks = 0; entities = 0; startedMs = System.currentTimeMillis(); }
        RoomsDebug.log("rooms cleanup scheduled: {} regions (pending total {})", regions.size(), st.pendingClear.size());
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (!p.isAlive() || p.isSpectator() || inAny(st.pendingProtect, p.blockPosition())) continue;
            evacuate(level, p, "rooms reset");
        }
    }

    /** 片付けの途中か（/rooms to はこの間待つ）。 */
    public static boolean busy(ServerLevel level) { return !RoomsState.get(level).pendingClear.isEmpty(); }

    /** 新しい部屋を作る前に：残りを全部片付ける（新しい部屋の場所に古い部屋が残っていると、あとで新しい部屋ごと壊してしまう）。 */
    public static void flush(ServerLevel level) {
        RoomsState st = RoomsState.get(level);
        if (st.pendingClear.isEmpty()) return;
        long t0 = System.nanoTime();
        run(level, st, Long.MAX_VALUE);
        RoomsDebug.log("rooms cleanup flushed before room generation in {} ms", (System.nanoTime() - t0) / 1_000_000);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        ServerLevel level = e.getServer().getLevel(RoomsCommands.ROOMS_DIMENSION);
        if (level == null) return;
        RoomsState st = RoomsState.get(level);
        if (!st.legacyLobbyCleared && !st.boxes(0).isEmpty() && st.boxes(0).get(0).minY() == RoomsLayout.LOBBY_Y) {
            // y=1 時代（5a089f2 より前）のロビーの跡（今のロビーが y=1 ならしない）。その先の古い部屋は位置が残っていないので対象外。
            // 新しいロビー（y=LOBBY_Y）がもうある時だけ（まだ y=1 の進行表のままなら、次の作り直しで保存してある範囲ごと片付く）
            st.legacyLobbyCleared = true;
            st.setDirty();
            if (RoomsLayout.LOBBY_Y != 1) {
                st.pendingClear.add(LEGACY_LOBBY);
                st.pendingClear.add(LEGACY_SEAL);
                if (startedMs == 0) startedMs = System.currentTimeMillis();
                RoomsDebug.log("rooms cleanup: legacy y=1 lobby queued");
            }
        }
        if (st.pendingClear.isEmpty()) return;
        run(level, st, BUDGET_MS * 1_000_000L);
    }

    private static void index(RoomsState st) {
        if (indexedFor != st) { queue.clear(); bySection.clear(); indexed = 0; indexedFor = st; }
        if (indexed > st.pendingClear.size()) indexed = 0;
        for (; indexed < st.pendingClear.size(); indexed++) {
            BoundingBox b = st.pendingClear.get(indexed);
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
                for (int cy = b.minY() >> 4; cy <= b.maxY() >> 4; cy++)
                    for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                        long key = SectionPos.asLong(cx, cy, cz);
                        List<BoundingBox> l = bySection.get(key);
                        if (l == null) { l = new ArrayList<>(); bySection.put(key, l); queue.add(key); }
                        l.add(b);
                    }
        }
    }

    private static void run(ServerLevel level, RoomsState st, long budgetNs) {
        index(st);
        if (startedMs == 0) startedMs = System.currentTimeMillis(); // 読み込み後の続き
        long t0 = System.nanoTime();
        DropGuard.begin();
        try {
            while (!queue.isEmpty()) {
                long key = queue.poll();
                List<BoundingBox> regs = bySection.remove(key);
                if (regs != null) clearSection(level, st, key, regs);
                if (System.nanoTime() - t0 > budgetNs) break;
            }
        } finally { DropGuard.end("rooms cleanup"); }
        if (queue.isEmpty()) {
            RoomsDebug.log("rooms cleanup done: {} regions, {} blocks, {} entities removed in {} ms",
                    st.pendingClear.size(), blocks, entities, System.currentTimeMillis() - startedMs);
            st.pendingClear.clear();
            st.pendingProtect.clear();
            indexed = 0;
            blocks = 0; entities = 0; startedMs = 0;
            st.setDirty();
        }
    }

    private static void clearSection(ServerLevel level, RoomsState st, long key, List<BoundingBox> regs) {
        int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        if (y0 + 15 < level.getMinBuildHeight() || y0 >= level.getMaxBuildHeight()) return;
        BoundingBox sec = new BoundingBox(x0, y0, z0, x0 + 15, y0 + 15, z0 + 15);
        // 壊さない範囲：作り直したロビー・今の進行表の部屋・今の縦穴
        List<BoundingBox> prot = new ArrayList<>();
        for (BoundingBox b : st.pendingProtect) if (b.intersects(sec)) prot.add(b);
        for (List<BoundingBox> room : st.generatedRooms) for (BoundingBox b : room) if (b.intersects(sec)) prot.add(b);
        if (st.abyss != null) { BoundingBox b = shaftRegion(st.abyss); if (b.intersects(sec)) prot.add(b); }
        List<BoundingBox> mine = new ArrayList<>();
        for (BoundingBox b : regs) if (b.intersects(sec)) mine.add(b);
        if (mine.isEmpty()) return;

        // この中にいるプレイヤーは先にロビーへ（作り直しの後に入ってきた人）
        AABB area = new AABB(x0 - 2, y0 - 2, z0 - 2, x0 + 18, y0 + 18, z0 + 18);
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (!p.isAlive() || p.isSpectator() || !area.contains(p.position())) continue;
            BlockPos bp = p.blockPosition();
            if (inAny(mine, bp) && !inAny(prot, bp)) evacuate(level, p, "inside cleanup area");
        }

        LevelChunk chunk = level.getChunk(sx, sz); // 必要なら読み込む
        LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
        if (!section.hasOnlyAir()) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BoundingBox b : mine) {
                int ax = Math.max(b.minX(), x0), bx = Math.min(b.maxX(), x0 + 15);
                int ay = Math.max(b.minY(), y0), by = Math.min(b.maxY(), y0 + 15);
                int az = Math.max(b.minZ(), z0), bz = Math.min(b.maxZ(), z0 + 15);
                for (int y = ay; y <= by; y++) for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) {
                    if (section.getBlockState(x & 15, y & 15, z & 15).isAir()) continue;
                    m.set(x, y, z);
                    if (inAny(prot, m)) continue;
                    if (level.setBlock(m, air, FLAGS)) blocks++;
                }
            }
        }

        // 落ちているアイテム・経験値・この MOD のエンティティ（プレイヤー以外）
        for (Entity e : level.getEntities((Entity) null, new AABB(x0, y0, z0, x0 + 16, y0 + 16, z0 + 16), RoomsCleanup::sweepable)) {
            BlockPos bp = e.blockPosition();
            if (inAny(mine, bp) && !inAny(prot, bp)) { e.discard(); entities++; }
        }
    }

    private static boolean sweepable(Entity e) {
        if (e instanceof Player || e.isRemoved()) return false;
        if (e instanceof ItemEntity || e instanceof ExperienceOrb) return true;
        return "roomsmod".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    private static boolean inAny(List<BoundingBox> boxes, BlockPos p) {
        for (BoundingBox b : boxes) if (b.isInside(p)) return true;
        return false;
    }

    private static void evacuate(ServerLevel level, ServerPlayer p, String why) {
        com.example.roomsmod.hiding.LockerManager.exit(p, "rooms reset");
        p.teleportTo(level, RoomsLayout.SPAWN_X, RoomsLayout.SPAWN_Y, RoomsLayout.SPAWN_Z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.displayClientMessage(Component.literal("部屋が作り直されたのでロビーへ戻ったわ"), true);
        RoomsDebug.log("rooms cleanup: {} moved to lobby ({})", p.getGameProfile().getName(), why);
    }
}
