package com.example.roomsmod.world.section;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.event.DoorEventHandler;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** セクション定義の登録先。新しいセクションの足し方は docs/SECTIONS.md。 */
public final class Sections {
    private static final Map<String, SectionTheme> THEMES = new LinkedHashMap<>();

    /** 現在のA区画（旧ハードコード値そのまま）。将来 B（Basement）に改名予定。 */
    public static final SectionTheme A = register(new SectionTheme(
            "a", "A",
            new SectionTheme.Palette(
                    Blocks.STONE::defaultBlockState, Blocks.STONE_BRICKS::defaultBlockState, Blocks.STONE::defaultBlockState,
                    () -> Blocks.OAK_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            new SectionTheme.Furniture(
                    () -> Blocks.BOOKSHELF, 2, 4,
                    () -> Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), () -> Blocks.OAK_PRESSURE_PLATE, 3,
                    () -> Blocks.POLISHED_ANDESITE, () -> Blocks.STONE_BRICKS),
            new SectionTheme.Cover(0.7f, 1, 3,
                    Map.of("HALL", new int[]{2, 3, 2, 2}, "TALL", new int[]{1, 2, 3, 1},
                           "L_SHAPE", new int[]{2, 0, 1, 3}, "CORRIDOR", new int[]{1, 0, 1, 2}),
                    new int[]{3, 1, 2, 2},
                    Blocks.BOOKSHELF::defaultBlockState, Blocks.POLISHED_ANDESITE::defaultBlockState, Blocks.STONE_BRICKS::defaultBlockState,
                    Blocks.STONE_BRICKS::defaultBlockState, Blocks.BARREL::defaultBlockState, Blocks.SPRUCE_PLANKS::defaultBlockState),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.3f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.7f)),
            3, 2,
            List.of(
                    new SectionTheme.RusherSpawn("A-20", 20, 0.6f, DoorEventHandler::isA20Alive,
                            (l, rs, p) -> { DoorEventHandler.spawnA20(l, rs, p); return true; }),
                    new SectionTheme.RusherSpawn("A-40", 40, 0.25f, DoorEventHandler::isA40Alive, DoorEventHandler::spawnA40)),
            new SectionTheme.SignStyle(() -> Blocks.OAK_WALL_SIGN, DyeColor.WHITE, true),
            25, 5));

    /** V（Village）区画。お試し用：見た目と隠れ場所の比率だけ変更、ラッシャーなし、部屋の寸法は A と同じ。 */
    public static final SectionTheme V = register(new SectionTheme(
            "v", "V",
            new SectionTheme.Palette(
                    Blocks.DIRT_PATH::defaultBlockState, Blocks.OAK_PLANKS::defaultBlockState, Blocks.SPRUCE_PLANKS::defaultBlockState,
                    () -> Blocks.SPRUCE_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{3, 0, 2, 1, 2},                       // 廊下なし、ホール/L字多め
            new SectionTheme.Furniture(
                    () -> Blocks.BARREL, 1, 3,
                    () -> Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), () -> Blocks.FLOWER_POT, 3,
                    () -> Blocks.STRIPPED_OAK_LOG, () -> Blocks.OAK_LOG),
            new SectionTheme.Cover(0.8f, 1, 3, Map.of(), new int[]{1, 2, 1, 3},
                    Blocks.BOOKSHELF::defaultBlockState, Blocks.OAK_LOG::defaultBlockState, Blocks.OAK_PLANKS::defaultBlockState,
                    Blocks.OAK_PLANKS::defaultBlockState, Blocks.HAY_BLOCK::defaultBlockState, Blocks.BARREL::defaultBlockState),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.6f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.4f)),
            3, 2,
            List.of(),
            new SectionTheme.SignStyle(() -> Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false),
            0, 5));

    private Sections() {}

    public static SectionTheme register(SectionTheme t) {
        if (THEMES.putIfAbsent(t.id(), t) != null) throw new IllegalStateException("duplicate section " + t.id());
        return t;
    }

    public static SectionTheme byId(String id) { return THEMES.getOrDefault(id, A); }

    /** 登録済みなら定義、なければ null。 */
    public static SectionTheme find(String id) { return THEMES.get(id); }

    public static java.util.Set<String> ids() { return THEMES.keySet(); }

    /** 部屋番号 room が属するセクション（RoomsState のセクション開始表から）。 */
    public static SectionTheme forRoom(RoomsState rs, int room) {
        SectionTheme cur = A;
        for (RoomsState.SectionStart s : rs.sections) if (room >= s.startRoom()) cur = byId(s.themeId());
        return cur;
    }

    /** room が属するセクション開始エントリの startRoom（該当なしは 0）。 */
    public static int sectionStart(RoomsState rs, int room) {
        int start = 0;
        for (RoomsState.SectionStart s : rs.sections) if (room >= s.startRoom()) start = s.startRoom();
        return start;
    }

    /**
     * セクション内の部屋番号。セクション最初の部屋が 1。
     * startRoom 0 のセクション（A）は部屋 0 がロビーなので最初の番号付き部屋は 1 → 全体番号と同じ。
     * 式: local = room - max(startRoom, 1) + 1
     */
    public static int localRoom(RoomsState rs, int room) {
        return room - Math.max(sectionStart(rs, room), 1) + 1;
    }

    /** 表示用の部屋番号（セクション接頭辞 + セクション内番号）。 */
    public static String formatRoom(RoomsState rs, int room) { return forRoom(rs, room).formatRoom(localRoom(rs, room)); }
}
