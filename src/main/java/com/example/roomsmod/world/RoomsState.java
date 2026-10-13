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
    /** 現在の部屋の出口扉（下半分）。これを開けたときだけ次の部屋を生成する。 */
    public BlockPos expectedExitDoor = null;
    public final Set<BlockPos> generatedDoors = new HashSet<>();
    /** 部屋ごとのバウンディングボックス（index 0 = ロビー）。 */
    public final List<List<BoundingBox>> generatedRooms = new ArrayList<>();
    /** 部屋ごとの経路（入口→折れ点→出口扉、床+1）。generatedRoomsと同じindex。 */
    public final List<List<BlockPos>> roomPaths = new ArrayList<>();

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
        setDirty();
    }

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
        tag.putInt("currentRoom", currentRoom);
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
        RoomsDebug.log("state saved: currentRoom={}, rooms={}, doors={}, expectedExitDoor={}", currentRoom, generatedRooms.size(), generatedDoors.size(), expectedExitDoor);
        return tag;
    }

    public static RoomsState load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomsState s = new RoomsState();
        try {
        RoomsDebug.setEnabled(tag.getBoolean("debug"));
        s.currentRoom = tag.getInt("currentRoom");
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
        } catch (RuntimeException e) {
            RoomsDebug.error("failed to load RoomsState", e);
        }
        RoomsDebug.log("state loaded: currentRoom={}, rooms={}, doors={}, expectedExitDoor={}", s.currentRoom, s.generatedRooms.size(), s.generatedDoors.size(), s.expectedExitDoor);
        return s;
    }
}
