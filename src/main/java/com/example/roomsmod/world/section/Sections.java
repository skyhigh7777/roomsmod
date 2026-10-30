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
    public static final SectionTheme B = register(new SectionTheme(
            "b", "B",
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
                    new SectionTheme.RusherSpawn("B-20", 20, 0.6f, l -> DoorEventHandler.isA20Alive(l) || errorAlive(l),
                            (l, rs, p) -> { DoorEventHandler.spawnA20(l, rs, p); return true; }, "err_b20"),
                    new SectionTheme.RusherSpawn("B-40", 40, 0.25f, l -> DoorEventHandler.isA40Alive(l) || errorAlive(l), DoorEventHandler::spawnA40, "err_b40"),
                    // B: B-15〜B-290 にかけて易しい順に解禁（1回の抽選で最大 GK_MAX_PER_ROLL 体）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_STATIC, 15, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_MASK, 30, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_REACH, 50, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_EYE, 70, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_SMILE, 90, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_ERROR, 110, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_GRIN, 130, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_MAW, 155, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_ARGUS, 180, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_SCATTER, 205, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_CHORUS, 230, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_VOID, 260, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_17, 290, 0.04f)),
            new SectionTheme.SignStyle(() -> Blocks.OAK_WALL_SIGN, DyeColor.WHITE, true),
            1, 7)); // 減光: セクション内1部屋目から7部屋ごとに1段（RoomLighting）

    /** V（Village）区画。部屋の形・家具は VillageRooms（小さな民家、2階建てあり）。shapeWeights/furniture は未使用、cover は遮蔽物のブロック。エンティティは V-10/V-25-T/V-25-L/V-100/V-50。 */
    public static final SectionTheme V = register(new SectionTheme(
            "v", "V",
            new SectionTheme.Palette(
                    Blocks.SPRUCE_PLANKS::defaultBlockState, Blocks.OAK_PLANKS::defaultBlockState, Blocks.DARK_OAK_PLANKS::defaultBlockState, // 床は全高ブロック（土の道は壁下に隙間が見えた）
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
            // V のエンティティ（minRoom はセクション内番号）。上から独立抽選、同種は1体まで、V-25-T/L は排他グループ。
            List.of(
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V10, 10, 0.35f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V25T, 25, 0.12f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V25L, 25, 0.12f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V25_BUFFED, 125, 0.10f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V100, 100, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.V50, 50, 0.12f)),
            new SectionTheme.SignStyle(() -> Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false),
            1, 7));

    /**
     * GK（学校 / Gakkou）区画。部屋は GKRooms（教室・廊下・準備室、内寸 5〜15、A 寄りの中間サイズ）。
     * どの部屋にもロッカーとテーブルを最低1つずつ置く。進行上は V の後（/rooms section gk now で開始）。
     * palette.floor/wall/ceiling/door/light と hidingMix・hidingMin/Extra・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     */
    public static final SectionTheme GK = register(new SectionTheme(
            "gk", "GK",
            new SectionTheme.Palette(
                    Blocks.BIRCH_PLANKS::defaultBlockState, Blocks.WHITE_TERRACOTTA::defaultBlockState, Blocks.SMOOTH_QUARTZ::defaultBlockState,
                    () -> Blocks.BIRCH_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.5f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.5f)),
            3, 2,
            List.of(
                    // GK: セクション内番号 GK-10〜GK-300 にかけて易しい順に解禁。1回の抽選で出るのは最大 GK_MAX_PER_ROLL 体（DoorEventHandler）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_MOTH, 10, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_CHIME, 25, 0.12f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_WHISPER, 45, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_DETENTION, 65, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_QUIZ, 85, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_SUBSTITUTE, 105, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_07, 125, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_REWIND, 145, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_MIMIC, 165, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_PA, 185, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_STARE, 205, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_JANITOR, 225, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_ERASER, 245, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_MIRROR, 270, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.GK_BELL2, 300, 0.04f)),
            new SectionTheme.SignStyle(() -> Blocks.BIRCH_WALL_SIGN, DyeColor.BLACK, false),
            1, 8));

    /**
     * U 区画。まっすぐな廊下だけでできた部屋（world/CorridorRooms.java）。進行上は GK の後。
     * palette・hidingMix・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     */
    public static final SectionTheme U = register(new SectionTheme(
            "u", "U",
            new SectionTheme.Palette(
                    Blocks.SMOOTH_STONE::defaultBlockState, Blocks.LIGHT_GRAY_CONCRETE::defaultBlockState, Blocks.SMOOTH_STONE::defaultBlockState,
                    () -> Blocks.DARK_OAK_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.8f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.2f)),
            2, 2,
            List.of(
                    // U: セクション内番号 U-20〜U-300 にかけて易しい順に解禁。1回の抽選で最大 GK_MAX_PER_ROLL 体
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.U20, 20, 0.30f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.U60, 60, 0.15f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.U120, 120, 0.10f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.U200, 200, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.U300, 300, 0.07f)),
            new SectionTheme.SignStyle(() -> Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true),
            1, 10));

    /**
     * PLACEHOLDER（テスト用）。壁も天井もない 11x11 の床に、テーブルとロッカーだけ（world/PlaceholderRooms.java）。
     * 固定長の進行（ORDER）には入らず、ロビーのパネルにも出ない。/rooms section placeholder でだけ始まり、以後ずっと続く。
     * エンティティは自然出現しない（rushers は空）。/rooms spawn placeholder32767 などで出す。
     */
    public static final SectionTheme PLACEHOLDER = register(new SectionTheme(
            "placeholder", "PLACEHOLDER",
            new SectionTheme.Palette(
                    Blocks.WHITE_CONCRETE::defaultBlockState, Blocks.GRAY_CONCRETE::defaultBlockState, Blocks.GRAY_CONCRETE::defaultBlockState,
                    () -> Blocks.OAK_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 1.0f)),
            2, 1,
            List.of(),
            new SectionTheme.SignStyle(() -> Blocks.OAK_WALL_SIGN, DyeColor.BLACK, false),
            0, 0));

    /** テスト用のセクション（ロビーのパネルに出さない、進行に入れない）。 */
    public static final java.util.Set<String> TEST_ONLY = java.util.Set.of("placeholder");

    /** ロビーのスタート選択パネルで巡回するセクション（テスト用を除く）。 */
    public static List<String> lobbyIds() {
        List<String> out = new java.util.ArrayList<>();
        for (String id : THEMES.keySet()) if (!TEST_ONLY.contains(id)) out.add(id);
        return out;
    }

    // ===================== 固定長のセクション進行 =====================

    /** どのセクションも 500 部屋。 */
    public static final int SECTION_LENGTH = 500;
    /** 進行順。最後のセクションの後は、そのセクションがそのまま続く。 */
    public static final List<String> ORDER = List.of("b", "v", "gk", "u");

    /**
     * first から始まるセクション表：first は通し番号 1（B はロビーが 0 なので 0）から、以降 ORDER の順に
     * SECTION_LENGTH 部屋ごとに次のセクションへ切り替わる（B → V → GK → U）。
     * TODO: 最後のセクション（U）の 500 部屋目より先をどうするか未定。今は最後のセクションがそのまま続く（U-501, U-502 …）。
     */
    public static List<RoomsState.SectionStart> fixedChain(String firstId) {
        return chainFrom(firstId, "b".equals(canonical(firstId)) ? 0 : 1);
    }

    /** start から id、以降 SECTION_LENGTH ごとに ORDER の次のセクション。ORDER にない id はそれ1つだけ。 */
    public static List<RoomsState.SectionStart> chainFrom(String id, int start) {
        String c = canonical(id);
        List<RoomsState.SectionStart> out = new java.util.ArrayList<>();
        out.add(new RoomsState.SectionStart(c, start));
        int i = ORDER.indexOf(c);
        if (i < 0) return out;
        int first = Math.max(start, 1); // 番号付きの最初の部屋（B はロビー 0 の次の 1）
        for (int k = i + 1; k < ORDER.size(); k++) out.add(new RoomsState.SectionStart(ORDER.get(k), first + SECTION_LENGTH * (k - i)));
        return out;
    }

    private Sections() {}

    /** B-110 が生存中（B-20 / B-40 と同時に出さない）。 */
    private static boolean errorAlive(net.minecraft.server.level.ServerLevel l) {
        return com.example.roomsmod.entity.v.VSpawns.isAlive(l, com.example.roomsmod.RoomsMod.B_ERROR.get());
    }

    private static SectionTheme.RusherSpawn vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind k, int minRoom, float chance) {
        return new SectionTheme.RusherSpawn(k.display, minRoom, chance, k::alive, k::spawn, k.exclusionGroup);
    }

    public static SectionTheme register(SectionTheme t) {
        if (THEMES.putIfAbsent(t.id(), t) != null) throw new IllegalStateException("duplicate section " + t.id());
        return t;
    }

    public static SectionTheme byId(String id) { return THEMES.getOrDefault(canonical(id), B); }

    /** 登録済みなら定義、なければ null。 */
    public static SectionTheme find(String id) { return THEMES.get(canonical(id)); }

    /** 旧 ID の別名：セクション A は B に改名した（"a" は古いコマンド・保存データ用の非推奨の別名）。 */
    public static String canonical(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(java.util.Locale.ROOT);
        return "a".equals(s) ? "b" : s;
    }

    /**
     * ハードモードの抽選表：登録済みの全セクションの表をつなげたもの（同名は最初の1つ）。
     * 各エンティティの出現開始部屋（minRoom）はそのまま、今いるセクション内の部屋番号と比べる。
     */
    /** ハードモードの抽選表にだけ入るエンティティ（どのセクションにも属さない）。 */
    private static final List<SectionTheme.RusherSpawn> HARD_ONLY = List.of(
            vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.E332, 1, 0.05f),
            // PLACEHOLDER 区画の敵（区画自体の表は空のまま）。PLACEHOLDER-2147483648 は入れない
            vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.PLACEHOLDER_32767, 1, 0.05f),
            vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.PLACEHOLDER_65535, 1, 0.05f));

    public static List<SectionTheme.RusherSpawn> hardTable() {
        List<SectionTheme.RusherSpawn> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SectionTheme t : THEMES.values()) for (var r : t.rushers()) if (seen.add(r.name())) out.add(r);
        for (var r : HARD_ONLY) if (seen.add(r.name())) out.add(r);
        return out;
    }

    public static java.util.Set<String> ids() { return THEMES.keySet(); }

    /** 部屋番号 room が属するセクション（RoomsState のセクション開始表から）。 */
    public static SectionTheme forRoom(RoomsState rs, int room) {
        SectionTheme cur = B;
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
