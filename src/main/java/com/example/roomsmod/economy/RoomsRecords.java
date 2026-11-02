package com.example.roomsmod.economy;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.util.RoomsMsg;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * プレイヤーごとの到達記録（どこまでクリアしたか）。オーバーワールドの SavedData（roomsmod_records）に UUID ごとに保存
 * （オフラインのプレイヤーも一覧できるよう、最後に見た名前も持つ）。通常・ハードは別々。
 * <ul>
 *   <li>深さは Sections.absoluteDepth（ORDER V→B→U→S→H→F→A の通しの深さ）で比べる。セクション表（GO・section）で通し番号が変わっても同じ部屋は同じ深さ。
 *       ORDER にないセクション（PLACEHOLDER）は記録しない。</li>
 *   <li>記録するのは周回中に歩いて進んだ部屋だけ：Wallet.runFurthest（WALK_TOLERANCE を超えるジャンプは開始のやり直し＝数えない）が runStart より先、かつ runNoPay でない（管理ワープ /rooms admin tp・to・deepest の周回は数えない）。</li>
 *   <li>周回数・ノーヒット生還（被弾なしでロビーへ帰還）の最深は精算（RoomsRun.settle）で。</li>
 *   <li>自己ベスト更新の通知は1周回につき：記録を超えて新しいセクションに入った時（そのセクションで1回）と、精算時（最終の到達）。</li>
 * </ul>
 */
public final class RoomsRecords extends SavedData {
    public static final String NAME = "roomsmod_records";

    /** 1つの記録（セクション・セクション内番号・通しの深さ・その時の通し部屋番号・時刻 epoch ms）。 */
    public record Best(String section, int local, int depth, int global, long time) {
        public String label() { return Sections.byId(section).formatRoom(local); }
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("s", section); t.putInt("l", local); t.putInt("d", depth); t.putInt("g", global); t.putLong("t", time);
            return t;
        }
        static Best load(CompoundTag t) { return new Best(t.getString("s"), t.getInt("l"), t.getInt("d"), t.getInt("g"), t.getLong("t")); }
    }

    /** 通常またはハードの記録。 */
    public static final class Mode {
        public Best deepest;
        public Best noHit;
        public int runs;
        /** セクション id → そのセクションの最深。 */
        public final Map<String, Best> perSection = new LinkedHashMap<>();
        boolean empty() { return deepest == null && noHit == null && runs == 0 && perSection.isEmpty(); }
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (deepest != null) t.put("deepest", deepest.save());
            if (noHit != null) t.put("noHit", noHit.save());
            t.putInt("runs", runs);
            CompoundTag ps = new CompoundTag();
            perSection.forEach((k, v) -> ps.put(k, v.save()));
            t.put("sections", ps);
            return t;
        }
        static Mode load(CompoundTag t) {
            Mode m = new Mode();
            if (t.contains("deepest")) m.deepest = Best.load(t.getCompound("deepest"));
            if (t.contains("noHit")) m.noHit = Best.load(t.getCompound("noHit"));
            m.runs = t.getInt("runs");
            CompoundTag ps = t.getCompound("sections");
            for (String k : ps.getAllKeys()) m.perSection.put(k, Best.load(ps.getCompound(k)));
            return m;
        }
    }

    public static final class Entry {
        public final UUID id;
        public String name;
        public final Mode normal = new Mode(), hard = new Mode();
        Entry(UUID id, String name) { this.id = id; this.name = name; }
        public Mode mode(boolean hardMode) { return hardMode ? hard : normal; }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static RoomsRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RoomsRecords::new, RoomsRecords::load, null), NAME);
    }

    public Collection<Entry> all() { return entries.values(); }
    public Entry find(UUID id) { return entries.get(id); }
    public Entry findByName(String name) {
        for (Entry e : entries.values()) if (e.name != null && e.name.equalsIgnoreCase(name)) return e;
        return null;
    }
    public boolean remove(UUID id) { boolean r = entries.remove(id) != null; if (r) setDirty(); return r; }

    Entry entry(ServerPlayer p) {
        String n = p.getGameProfile().getName();
        Entry e = entries.computeIfAbsent(p.getUUID(), k -> { setDirty(); return new Entry(k, n); });
        if (!n.equals(e.name)) { e.name = n; setDirty(); }
        return e;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.id);
            t.putString("name", e.name == null ? "" : e.name);
            if (!e.normal.empty()) t.put("normal", e.normal.save());
            if (!e.hard.empty()) t.put("hard", e.hard.save());
            list.add(t);
        }
        tag.put("players", list);
        return tag;
    }

    public static RoomsRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomsRecords r = new RoomsRecords();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("id")) continue;
            Entry e = new Entry(t.getUUID("id"), t.getString("name"));
            if (t.contains("normal")) copy(Mode.load(t.getCompound("normal")), e.normal);
            if (t.contains("hard")) copy(Mode.load(t.getCompound("hard")), e.hard);
            r.entries.put(e.id, e);
        }
        return r;
    }
    private static void copy(Mode from, Mode to) { to.deepest = from.deepest; to.noHit = from.noHit; to.runs = from.runs; to.perSection.putAll(from.perSection); }

    // ===================== 周回中の更新（RoomsRun から） =====================

    /** 周回中の通知の状態（メモリのみ）：周回を始めた時点の最深と、通知済みのセクション。 */
    private static final class RunNote { int baseline; String baselineSection = ""; boolean hard; String announcedSection = ""; int lastObserved = -1; }
    private static final Map<UUID, RunNote> NOTES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 記録できる周回か（歩いて開始より先へ進み、管理ワープの周回でない）。 */
    static boolean legit(Wallet w) { return !w.runNoPay && w.runStart >= 0 && w.runFurthest > w.runStart; }

    private static Best bestOf(RoomsState rs, int room) {
        if (room < 1) return null;
        String sec = Sections.canonical(Sections.forRoom(rs, room).id());
        int local = Sections.localRoom(rs, room), depth = Sections.absoluteDepth(sec, local);
        if (depth <= 0 || local < 1) return null;
        return new Best(sec, local, depth, room, System.currentTimeMillis());
    }

    /** 5 tick ごと（RoomsRun.onTick）：runFurthest が記録を超えたら更新。記録を超えて新しいセクションに入ったらその時に1回通知。 */
    static void observe(ServerPlayer p, RoomsState rs, Wallet w) {
        if (!legit(w)) return;
        RunNote note = NOTES.get(p.getUUID());
        if (note != null && note.lastObserved == w.runFurthest && note.hard == rs.hardMode) return; // 変化なし
        RoomsRecords db = get(p.server);
        Entry e = db.entry(p);
        Mode m = e.mode(rs.hardMode);
        if (note == null || note.hard != rs.hardMode) {
            note = new RunNote();
            note.hard = rs.hardMode;
            note.baseline = m.deepest == null ? 0 : m.deepest.depth();
            note.baselineSection = m.deepest == null ? "" : m.deepest.section();
            NOTES.put(p.getUUID(), note);
        }
        note.lastObserved = w.runFurthest;
        Best b = bestOf(rs, w.runFurthest);
        if (b == null) return;
        boolean changed = false;
        Best ps = m.perSection.get(b.section());
        if (ps == null || b.local() > ps.local()) { m.perSection.put(b.section(), b); changed = true; }
        if (m.deepest == null || b.depth() > m.deepest.depth()) {
            m.deepest = b; changed = true;
            RoomsDebug.log("record: {} new best {} (depth {}, global {}, hard={})", e.name, b.label(), b.depth(), b.global(), rs.hardMode);
            // 記録を超えて新しいセクションへ入った時だけ（同じセクション内の更新は精算時にまとめて）
            if (b.depth() > note.baseline && !b.section().equals(note.baselineSection) && !b.section().equals(note.announcedSection)) {
                note.announcedSection = b.section();
                p.displayClientMessage(Component.literal("自己ベスト更新：" + b.label() + (rs.hardMode ? "（HARD）" : "")).withStyle(ChatFormatting.GOLD), true);
            }
        }
        if (changed) db.setDirty();
    }

    /** 精算時（RoomsRun.settle）：周回数・ノーヒット生還・最終の自己ベスト通知。 */
    static void onSettle(ServerPlayer p, RoomsState rs, Wallet w, boolean survived) {
        RunNote note = NOTES.remove(p.getUUID());
        if (!legit(w)) return;
        observeFinal(p, rs, w, note);
    }

    private static void observeFinal(ServerPlayer p, RoomsState rs, Wallet w, RunNote note) {
        RoomsRecords db = get(p.server);
        Entry e = db.entry(p);
        Mode m = e.mode(rs.hardMode);
        int baseline = note != null && note.hard == rs.hardMode ? note.baseline : (m.deepest == null ? 0 : m.deepest.depth());
        Best b = bestOf(rs, w.runFurthest);
        if (b != null) {
            Best ps = m.perSection.get(b.section());
            if (ps == null || b.local() > ps.local()) m.perSection.put(b.section(), b);
            if (m.deepest == null || b.depth() > m.deepest.depth()) m.deepest = b;
        }
        m.runs++;
        db.setDirty();
        if (b != null && b.depth() > baseline)
            p.sendSystemMessage(RoomsMsg.ok("自己ベスト更新：" + b.label() + (rs.hardMode ? "（HARD）" : "")));
        RoomsDebug.log("record: {} run counted (runs={}, furthest={}, hard={})", e.name, m.runs, b == null ? "-" : b.label(), rs.hardMode);
    }

    /** 生還かつ被弾なしの周回（精算の後で RoomsRun から）。 */
    static void onNoHitClear(ServerPlayer p, RoomsState rs, Wallet w) {
        Best b = bestOf(rs, w.runFurthest);
        if (b == null) return;
        RoomsRecords db = get(p.server);
        Mode m = db.entry(p).mode(rs.hardMode);
        if (m.noHit == null || b.depth() > m.noHit.depth()) {
            m.noHit = b; db.setDirty();
            p.sendSystemMessage(RoomsMsg.ok("ノーヒット生還の自己ベスト：" + b.label() + (rs.hardMode ? "（HARD）" : "")));
        }
    }

    /** やり直し・管理ワープなど精算しない周回の終わり。 */
    public static void forget(ServerPlayer p) { NOTES.remove(p.getUUID()); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { RoomsRecords db = get(p.server); Entry en = db.find(p.getUUID()); if (en != null) db.entry(p); } // 名前の更新
    }
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { NOTES.remove(e.getEntity().getUUID()); }

    // ===================== 表示（コマンド） =====================

    private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static String when(long t) { return java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).format(DATE); }

    /** /rooms record [player] の本文。 */
    public static List<MutableComponent> describe(Entry e) {
        List<MutableComponent> out = new ArrayList<>();
        out.add(RoomsMsg.info(e.name + " の到達記録"));
        describeMode(out, "通常", e.normal);
        describeMode(out, "ハード", e.hard);
        return out;
    }

    private static void describeMode(List<MutableComponent> out, String title, Mode m) {
        if (m.empty()) { out.add(Component.literal("  " + title + "：記録なし").withStyle(ChatFormatting.GRAY)); return; }
        out.add(Component.literal("  " + title + "：最深 " + (m.deepest == null ? "-" : m.deepest.label() + "（通し " + m.deepest.depth() + "、" + when(m.deepest.time()) + "）")
                + "  周回 " + m.runs).withStyle(ChatFormatting.WHITE));
        if (m.noHit != null)
            out.add(Component.literal("    ノーヒット生還 " + m.noHit.label() + "（" + when(m.noHit.time()) + "）").withStyle(ChatFormatting.GRAY));
        List<String> secs = new ArrayList<>();
        for (String id : Sections.ORDER) { Best b = m.perSection.get(id); if (b != null) secs.add(b.label()); }
        if (!secs.isEmpty()) out.add(Component.literal("    セクション別 " + String.join(" / ", secs)).withStyle(ChatFormatting.GRAY));
    }

    /** /rooms record top：通常・ハードそれぞれ最深の上位 n 人（同じ深さは先に到達した人が上）。 */
    public List<MutableComponent> ranking(int n) {
        List<MutableComponent> out = new ArrayList<>();
        for (boolean hard : new boolean[]{false, true}) {
            out.add(RoomsMsg.info((hard ? "ハード" : "通常") + " 最深ランキング"));
            List<Entry> list = new ArrayList<>();
            for (Entry e : entries.values()) if (e.mode(hard).deepest != null) list.add(e);
            list.sort(Comparator.<Entry>comparingInt(e -> -e.mode(hard).deepest.depth()).thenComparingLong(e -> e.mode(hard).deepest.time()));
            if (list.isEmpty()) out.add(Component.literal("  記録なし").withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < Math.min(n, list.size()); i++) {
                Best b = list.get(i).mode(hard).deepest;
                out.add(Component.literal("  " + (i + 1) + ". " + list.get(i).name + "  " + b.label() + "（通し " + b.depth() + "）")
                        .withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            }
        }
        return out;
    }
}
