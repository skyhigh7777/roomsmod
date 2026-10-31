package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rooms の進行状態。部屋を生成したレベル（Roomsディメンション）に永続化する。 */
public class RoomsState extends SavedData {
    private static final String NAME = "roomsmod_state";

    public int currentRoom = 0;
    /** 進行表を作り直した回数（reset ごとに +1、保存する）。出現した時と違えば、その敵の部屋番号はもう別の部屋（VEntity が消える）。 */
    public int epoch = 0;
    /** 現在の部屋の出口扉（下半分）。これを開けたときだけ次の部屋を生成する。 */
    public BlockPos expectedExitDoor = null;
    public final Set<BlockPos> generatedDoors = new HashSet<>();
    /** 部屋ごとのバウンディングボックス（index 0 = ロビー）。 */
    public final List<List<BoundingBox>> generatedRooms = new ArrayList<>();
    /** 部屋ごとの経路（入口→折れ点→出口扉、床+1）。generatedRoomsと同じindex。 */
    public final List<List<BlockPos>> roomPaths = new ArrayList<>();
    /** セクションの開始表（部屋番号 startRoom 以降が themeId）。startRoom 昇順。 */
    /** ハードモード：全セクションのエンティティがどの部屋でも出る（サーバー共有・セーブに保存）。 */
    public boolean hardMode = false;
    /** 既定は V → B → U → S → A（各 Sections.SECTION_LENGTH 部屋、V はロビー 0 から）。 */
    public final List<SectionStart> sections = new ArrayList<>(com.example.roomsmod.world.section.Sections.fixedChain(com.example.roomsmod.world.section.Sections.firstId()));

    public record SectionStart(String themeId, int startRoom) {}

    /** セクション表の形式。2 = 進行順 V → B → U → S → A（S は旧 GK、A は Abyss）。 */
    public static final int SECTIONS_VERSION = 2;

    /** A（Abyss）区画の縦穴（AbyssRooms）。まだ作っていなければ null。ロビー生成（reset）で消える。 */
    public com.example.roomsmod.world.AbyssRooms.Shaft abyss = null;

    /** 片付け待ちの古い部屋の範囲（余白込み）。RoomsCleanup が数 tick に分けて空気に戻す。途中で止まっても続きから（保存する）。 */
    public final List<BoundingBox> pendingClear = new ArrayList<>();
    /** 片付けで壊してはいけない範囲（作り直したロビーと出口のふた）。 */
    public final List<BoundingBox> pendingProtect = new ArrayList<>();
    /** y=1 時代（5a089f2 より前）のロビーの跡を片付けたか（1回だけ）。 */
    public boolean legacyLobbyCleared = false;

    /** セクションを追加（startRoom 昇順に並べる）。 */
    public void addSection(String themeId, int startRoom) {
        sections.removeIf(s -> s.startRoom() == startRoom);
        sections.add(new SectionStart(com.example.roomsmod.world.section.Sections.canonical(themeId), startRoom));
        sections.sort(java.util.Comparator.comparingInt(SectionStart::startRoom));
        setDirty();
    }

    /** セクション表を first から始まる固定長の進行（Sections.fixedChain）にする。 */
    public void setChain(String firstId) {
        sections.clear();
        sections.addAll(com.example.roomsmod.world.section.Sections.fixedChain(firstId));
        setDirty();
        RoomsDebug.log("section table set to fixed chain from {}: {}", firstId, sections);
    }

    /** 部屋 start 以降を id から始まる固定長の進行に置き換える（それより前はそのまま）。/rooms section 用。 */
    public void chainFrom(String id, int start) {
        sections.removeIf(s -> s.startRoom() >= start);
        sections.addAll(com.example.roomsmod.world.section.Sections.chainFrom(id, start));
        sections.sort(java.util.Comparator.comparingInt(SectionStart::startRoom));
        setDirty();
        RoomsDebug.log("section table chained from {} at {}: {}", id, start, sections);
    }

    public static RoomsState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RoomsState::new, RoomsState::load, null), NAME);
    }

    public void reset() {
        RoomsDebug.log("state reset (currentRoom was {}, rooms={})", currentRoom, generatedRooms.size());
        currentRoom = 0;
        expectedExitDoor = null;
        generatedDoors.clear();
        generatedRooms.clear();
        roomPaths.clear();
        abyss = null;
        epoch++;
        setDirty();
    }

    /** 部屋 room の範囲（なければ空。進行表のリセット後に古い番号で呼ばれても落ちない）。 */
    public List<BoundingBox> boxes(int room) { return room >= 0 && room < generatedRooms.size() ? generatedRooms.get(room) : List.of(); }

    /** 部屋 room の経路（なければ空）。 */
    public List<BlockPos> path(int room) { return room >= 0 && room < roomPaths.size() ? roomPaths.get(room) : List.of(); }

    /** 部屋 room が今の進行表にあるか。 */
    public boolean hasRoom(int room) { return room >= 0 && room < generatedRooms.size(); }

    public void addRoom(List<BoundingBox> boxes, List<BlockPos> path) {
        generatedRooms.add(new ArrayList<>(boxes));
        roomPaths.add(new ArrayList<>(path));
        setDirty();
    }

    public void setExpectedExitDoor(BlockPos pos) {
        expectedExitDoor = pos == null ? null : pos.immutable();
        setDirty();
    }

    public void markDoorGenerated(BlockPos pos) {
        generatedDoors.add(pos.immutable());
        setDirty();
    }

    /** デバッグフラグを変更して永続化する。 */
    public void setDebug(boolean on) {
        RoomsDebug.setEnabled(on);
        setDirty();
    }

    public void setCurrentRoom(int room) {
        currentRoom = room;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("debug", RoomsDebug.isEnabled());
        tag.putBoolean("hardMode", hardMode);
        tag.putInt("currentRoom", currentRoom);
        tag.putInt("epoch", epoch);
        if (expectedExitDoor != null) tag.putLong("expectedExitDoor", expectedExitDoor.asLong());
        ListTag doors = new ListTag();
        for (BlockPos p : generatedDoors) doors.add(LongTag.valueOf(p.asLong()));
        tag.put("generatedDoors", doors);
        ListTag rooms = new ListTag();
        for (List<BoundingBox> boxes : generatedRooms) {
            ListTag l = new ListTag();
            for (BoundingBox b : boxes) {
                l.add(new net.minecraft.nbt.IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
            }
            rooms.add(l);
        }
        tag.put("generatedRooms", rooms);
        ListTag paths = new ListTag();
        for (List<BlockPos> path : roomPaths) {
            ListTag l = new ListTag();
            for (BlockPos p : path) l.add(LongTag.valueOf(p.asLong()));
            paths.add(l);
        }
        tag.put("roomPaths", paths);
        ListTag secs = new ListTag();
        for (SectionStart sec : sections) {
            CompoundTag c = new CompoundTag();
            c.putString("theme", sec.themeId());
            c.putInt("start", sec.startRoom());
            secs.add(c);
        }
        tag.put("sections", secs);
        if (abyss != null) tag.put("abyss", abyss.save());
        tag.put("pendingClear", boxesTag(pendingClear));
        tag.put("pendingProtect", boxesTag(pendingProtect));
        tag.putBoolean("legacyLobbyCleared", legacyLobbyCleared);
        tag.putBoolean("sectionsFixed", true);
        tag.putInt("sectionsVersion", SECTIONS_VERSION);
        RoomsDebug.log("state saved: currentRoom={}, rooms={}, doors={}, expectedExitDoor={}", currentRoom, generatedRooms.size(), generatedDoors.size(), expectedExitDoor);
        return tag;
    }

    private static ListTag boxesTag(List<BoundingBox> list) {
        ListTag l = new ListTag();
        for (BoundingBox b : list) l.add(new net.minecraft.nbt.IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
        return l;
    }

    private static void readBoxes(CompoundTag tag, String key, List<BoundingBox> out) {
        for (Tag t : tag.getList(key, Tag.TAG_INT_ARRAY)) {
            int[] a = ((net.minecraft.nbt.IntArrayTag) t).getAsIntArray();
            if (a.length == 6) out.add(new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]));
        }
    }

    public static RoomsState load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomsState s = new RoomsState();
        try {
        readBoxes(tag, "pendingClear", s.pendingClear);
        readBoxes(tag, "pendingProtect", s.pendingProtect);
        s.legacyLobbyCleared = tag.getBoolean("legacyLobbyCleared");
        RoomsDebug.setEnabled(tag.getBoolean("debug"));
        s.currentRoom = tag.getInt("currentRoom");
        s.epoch = tag.getInt("epoch");
        if (tag.contains("expectedExitDoor")) s.expectedExitDoor = BlockPos.of(tag.getLong("expectedExitDoor"));
        for (Tag t : tag.getList("generatedDoors", Tag.TAG_LONG)) s.generatedDoors.add(BlockPos.of(((LongTag) t).getAsLong()));
        for (Tag t : tag.getList("generatedRooms", Tag.TAG_LIST)) {
            List<BoundingBox> boxes = new ArrayList<>();
            for (Tag bt : (ListTag) t) {
                int[] a = ((net.minecraft.nbt.IntArrayTag) bt).getAsIntArray();
                if (a.length == 6) boxes.add(new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]));
            }
            s.generatedRooms.add(boxes);
        }
        for (Tag t : tag.getList("roomPaths", Tag.TAG_LIST)) {
            List<BlockPos> path = new ArrayList<>();
            for (Tag pt : (ListTag) t) path.add(BlockPos.of(((LongTag) pt).getAsLong()));
            s.roomPaths.add(path);
        }
        s.hardMode = tag.getBoolean("hardMode");
        if (tag.contains("abyss")) s.abyss = com.example.roomsmod.world.AbyssRooms.Shaft.load(tag.getCompound("abyss"));
        if (tag.contains("sections")) {
            s.sections.clear();
            for (Tag t : tag.getList("sections", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                String theme = c.getString("theme");
                // version 1 以前の "a" は B の旧名（今の "a" は Abyss）
                if (tag.getInt("sectionsVersion") < 2 && "a".equalsIgnoreCase(theme)) theme = "b";
                s.sections.add(new SectionStart(com.example.roomsmod.world.section.Sections.canonical(theme), c.getInt("start")));
            }
            if (s.sections.isEmpty()) s.sections.add(new SectionStart(com.example.roomsmod.world.section.Sections.firstId(), 0));
            if (!tag.getBoolean("sectionsFixed") || tag.getInt("sectionsVersion") < SECTIONS_VERSION) {
                // 旧形式（長さ固定なし、または進行順が V → B → U → S → A になる前）：
                // 開始セクション（通し番号 1 までに始まる最後のもの）から今の固定長の進行に作り直す
                String first = com.example.roomsmod.world.section.Sections.firstId();
                for (SectionStart sec : s.sections) if (sec.startRoom() <= 1) first = sec.themeId();
                List<SectionStart> old = new ArrayList<>(s.sections);
                s.sections.clear();
                s.sections.addAll(com.example.roomsmod.world.section.Sections.fixedChain(first));
                RoomsDebug.log("state migrated section table {} -> {}", old, s.sections);
            }
        }
        } catch (RuntimeException e) {
            RoomsDebug.error("failed to load RoomsState", e);
        }
        RoomsDebug.log("state loaded: currentRoom={}, rooms={}, doors={}, expectedExitDoor={}", s.currentRoom, s.generatedRooms.size(), s.generatedDoors.size(), s.expectedExitDoor);
        return s;
    }
}
