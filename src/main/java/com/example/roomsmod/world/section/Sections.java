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

    private Sections() {}

    public static SectionTheme register(SectionTheme t) {
        if (THEMES.putIfAbsent(t.id(), t) != null) throw new IllegalStateException("duplicate section " + t.id());
        return t;
    }

    public static SectionTheme byId(String id) { return THEMES.getOrDefault(id, A); }

    /** 部屋番号 room が属するセクション（RoomsState のセクション開始表から）。 */
    public static SectionTheme forRoom(RoomsState rs, int room) {
        SectionTheme cur = A;
        for (RoomsState.SectionStart s : rs.sections) if (room >= s.startRoom()) cur = byId(s.themeId());
        return cur;
    }

    /** 区画内での番号（セクション開始からの通し番号ではなく、現状は全体の部屋番号をそのまま使う）。 */
    public static String formatRoom(RoomsState rs, int room) { return forRoom(rs, room).formatRoom(room); }
}
