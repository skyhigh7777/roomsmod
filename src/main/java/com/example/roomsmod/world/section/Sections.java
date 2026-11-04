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
                    new SectionTheme.RusherSpawn("B-20", 20, 0.12f, l -> DoorEventHandler.isA20Alive(l) || errorAlive(l),
                            (l, rs, p) -> { DoorEventHandler.spawnA20(l, rs, p); return true; }, "err_b20"),
                    new SectionTheme.RusherSpawn("B-40", 40, 0.09f, l -> DoorEventHandler.isA40Alive(l) || errorAlive(l), DoorEventHandler::spawnA40, "err_b40"),
                    // B: B-15〜B-290 にかけて易しい順に解禁（1回の抽選で最大 GK_MAX_PER_ROLL 体）。
                    // 2026-11-01 確率をならした（B-20 0.6 → 0.12、B-40 0.25 → 0.09、ほかは 0.05〜0.08。B は 300 部屋）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_STATIC, 15, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_MASK, 30, 0.08f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_REACH, 50, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_EYE, 70, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_SMILE, 90, 0.07f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_ERROR, 110, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_GRIN, 130, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_MAW, 155, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_ARGUS, 180, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_SCATTER, 205, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_CHORUS, 230, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_VOID, 260, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.B_17, 290, 0.05f)),
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
     * S（School）区画（旧 GK）。部屋は GKRooms（教室・廊下・準備室、内寸 5〜15、B 寄りの中間サイズ）。
     * どの部屋にもロッカーとテーブルを最低1つずつ置く。進行上は U の後（/rooms section s now で開始）。
     * Java の定数名・クラス名は GK のまま（表示・ID・コマンドは S）。
     * palette.floor/wall/ceiling/door/light と hidingMix・hidingMin/Extra・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     */
    public static final SectionTheme GK = register(new SectionTheme(
            "s", "S",
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
                    // S: セクション内番号 S-10〜S-300 にかけて易しい順に解禁。1回の抽選で出るのは最大 GK_MAX_PER_ROLL 体（DoorEventHandler）
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
     * U 区画。まっすぐな廊下だけでできた部屋（world/CorridorRooms.java）。進行上は B の後。
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
     * A（Abyss）区画。縦穴を落ち続ける（world/AbyssRooms.java）。1 周（縦穴の同じ見た目の上の層へ戻される）ごとに 1 部屋進む。
     * palette（壁・床）・sign・hidingMix（ロッカーのみ）を使う（shapeWeights/furniture/cover は未使用）。
     * 出現表は A 専用の敵（A-25 / A-50 / A-90 / A-130 / A-180 / A-200 / A-250 / A-300 / A-350 / A-450 / A-525 / A-600 / A-666 / A-750 / A-800 / A-1000 / A-1050 / A-1100 / A-1111 / A-1180、entity/a）だけ（minRoom はセクション内番号）。
     */
    public static final SectionTheme A = register(new SectionTheme(
            "a", "A",
            new SectionTheme.Palette(
                    Blocks.POLISHED_DEEPSLATE::defaultBlockState, Blocks.DEEPSLATE_TILES::defaultBlockState, Blocks.DEEPSLATE_TILES::defaultBlockState,
                    () -> Blocks.DARK_OAK_DOOR, // 扉は使わない（ロビー等で使われても手で開けられるもの）
                    () -> Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 1.0f)),
            2, 1,
            List.of(
                    // A 専用の敵だけ（ハードでもほかのセクションの敵は出さない）。1 部屋 約 2.4 秒なので確率は控えめ
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A25, 25, 0.06f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A50, 50, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A90, 90, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A130, 130, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A180, 180, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A200, 200, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A250, 250, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A300, 300, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A350, 350, 0.05f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A450, 450, 0.03f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A525, 525, 0.03f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A600, 600, 0.03f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A666, 666, 0.035f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A750, 750, 0.035f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A800, 800, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A1000, 1000, 0.04f),
                    // A-1000 より深い所だけ（落下速度も深いほど速い：AbyssRooms.fallSpeed）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A1050, 1050, 0.04f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A1100, 1100, 0.035f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A1111, 1111, 0.03f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.A1180, 1180, 0.035f)),
            new SectionTheme.SignStyle(() -> Blocks.DARK_OAK_WALL_SIGN, DyeColor.LIGHT_BLUE, true),
            0, 0));

    /**
     * R（Residence、住宅）区画。家の1区画ずつ：玄関・廊下 / リビング / キッチン / 寝室 / 和室（world/ResidenceRooms.java）。進行上は S の後、H の前。
     * 既定はロック（Sections.LOCKED_BY_DEFAULT）。ロック中は進行表に入らない（S の次は H / A のまま）。/rooms admin section r unlock で解放。
     * palette・hidingMix・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     * 敵は R 専用の 4 体だけ（entity/r：R-5 インターホン / R-35 覗く人 / R-100 家族 / R-200 父。1回の上限は GK_MAX_PER_ROLL）。
     * ハードモードでは全セクション共通の合同表（Sections.hardTable）になる。
     */
    public static final SectionTheme R = register(new SectionTheme(
            "r", "R",
            new SectionTheme.Palette(
                    Blocks.OAK_PLANKS::defaultBlockState, Blocks.CALCITE::defaultBlockState, Blocks.BIRCH_PLANKS::defaultBlockState,
                    () -> Blocks.CHERRY_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.5f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.5f)),
            2, 2,
            List.of(
                    // R: R 専用の敵だけ（通常モード。ハードは合同表）。名前の番号＝出現開始。
                    // P(1体以上)：R-5 0.22 / R-35 0.42 / R-100 0.59 / R-200〜 0.75（H・F と同じ曲線。1回の上限 2、R-100 と R-200 は排他）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.R5, 5, 0.22f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.R35, 35, 0.25f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.R100, 100, 0.30f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.R200, 200, 0.40f)),
            new SectionTheme.SignStyle(() -> Blocks.CHERRY_WALL_SIGN, DyeColor.BROWN, false),
            1, 8));

    /**
     * H（Hospital、病棟）区画。白いタイルの病室にベッドの列、ベッドの間はカーテンで仕切る（world/HospitalRooms.java）。進行上は S の後、A の前。
     * 既定はロック（Sections.LOCKED_BY_DEFAULT）。ロック中は進行表に入らない（S の次は A のまま）。/rooms admin section h unlock で解放すると進行表に入る。
     * palette・hidingMix・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     * 床の包帯がほかより多い（RoomGenerator.HOSPITAL_BANDAGE_ROOM_CHANCE）。
     * 敵は H 専用の 4 体だけ（entity/h：H-30 徘徊する患者 / H-90 夜勤の看護師 / H-150 カーテンの陰 / H-260 暴走するストレッチャー。
     * 1回の抽選の上限は GK_MAX_PER_ROLL のまま）。ハードモードでは全セクション共通の合同表（Sections.hardTable）になる。
     */
    public static final SectionTheme H = register(new SectionTheme(
            "h", "H",
            new SectionTheme.Palette(
                    Blocks.WHITE_CONCRETE::defaultBlockState, Blocks.QUARTZ_BRICKS::defaultBlockState, Blocks.SMOOTH_QUARTZ::defaultBlockState,
                    () -> Blocks.WARPED_DOOR,
                    () -> Blocks.END_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.EndRodBlock.FACING, net.minecraft.core.Direction.DOWN)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.55f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.45f)),
            2, 2,
            List.of(
                    // H: H 専用の敵だけ（通常モード。ハードは合同表）。名前の番号と出現開始は別（H-30 は H-5 から）。
                    // P(1体以上)：H-5 0.22 / H-35 0.42 / H-100 0.59 / H-200〜 0.75（1回の上限 2、H-90 と H-150 は排他）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.H30, 5, 0.22f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.H90, 35, 0.25f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.H150, 100, 0.30f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.H260, 200, 0.40f)),
            new SectionTheme.SignStyle(() -> Blocks.BIRCH_WALL_SIGN, DyeColor.RED, false),
            1, 8));

    /**
     * F（Flooded、水没区画）。床一面にくるぶしほどの水（水浸しのハーフブロック）、苔むした石レンガのまっすぐな部屋（world/FloodedRooms.java）。進行上は H の後、A の前。
     * 既定はロック（Sections.LOCKED_BY_DEFAULT）。ロック中は進行表に入らない（H と同じ扱い）。/rooms admin section f unlock で解放。
     * この区画の中ではダッシュのスタミナ消費が FloodedRooms.STAMINA_DRAIN_MUL 倍（Stamina）。溺れない（FloodedRooms.onPlayerTick で空気を満タンに保つ）。
     * palette・hidingMix・sign・lightFade を使う（shapeWeights/furniture/cover は未使用）。
     * 敵は F 専用の 4 体だけ（entity/f：F-5 聞き耳 / F-35 浮かび上がるもの / F-100 溺れた人影 / F-200 引き波。1回の上限は GK_MAX_PER_ROLL）。
     * ハードモードでは全セクション共通の合同表（Sections.hardTable）になる。
     */
    public static final SectionTheme F = register(new SectionTheme(
            "f", "F",
            new SectionTheme.Palette(
                    Blocks.STONE_BRICKS::defaultBlockState, Blocks.MOSSY_STONE_BRICKS::defaultBlockState, Blocks.CRACKED_STONE_BRICKS::defaultBlockState,
                    () -> Blocks.MANGROVE_DOOR,
                    () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
            new int[]{1, 1, 1, 1, 1},
            B.furniture(), B.cover(),
            List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.65f),
                    new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.35f)),
            2, 2,
            List.of(
                    // F: F 専用の敵だけ（通常モード。ハードは合同表）。名前の番号＝出現開始（F-5 は F-5 から）。
                    // P(1体以上)：F-5 0.22 / F-35 0.42 / F-100 0.59 / F-200〜 0.75（H と同じ曲線。1回の上限 2、F-5 と F-35 は排他）
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.F5, 5, 0.22f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.F35, 35, 0.25f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.F100, 100, 0.30f),
                    vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.F200, 200, 0.40f)),
            new SectionTheme.SignStyle(() -> Blocks.MANGROVE_WALL_SIGN, DyeColor.LIGHT_BLUE, true),
            1, 8));

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

    /**
     * 既定でロックするセクション（RoomsState.isSectionLocked）。テスト用（PLACEHOLDER）と未公開の H・F。
     * ORDER にあるものはロック中は進行表に入らない（RoomsState.outOfProgression）。ほかのセクションは手でロックしても進行表はそのまま。
     */
    public static final java.util.Set<String> LOCKED_BY_DEFAULT = java.util.Set.of("placeholder", "r", "h", "f");

    public static boolean isLockedByDefault(String id) { return LOCKED_BY_DEFAULT.contains(canonical(id)); }

    /** ロビーのスタート選択パネルで巡回するセクション（ORDER の順、その後に ORDER にない登録済みのもの。テスト用を除く）。 */
    public static List<String> lobbyIds() {
        List<String> out = new java.util.ArrayList<>(ORDER);
        for (String id : THEMES.keySet()) if (!TEST_ONLY.contains(id) && !out.contains(id)) out.add(id);
        return out;
    }

    /** ロビーのパネルで選べるセクション：登録済みのうちロックされていないもの（ORDER の順、PLACEHOLDER はロック解除時だけ最後に）。 */
    public static List<String> selectableIds(RoomsState rs) {
        List<String> out = new java.util.ArrayList<>(ORDER);
        for (String id : THEMES.keySet()) if (!out.contains(id)) out.add(id);
        out.removeIf(rs::isSectionLocked);
        if (out.isEmpty()) out.add(firstId());
        return out;
    }

    /** 進行の最初のセクション（ロビー＝部屋 0 から始まる）。 */
    public static String firstId() { return ORDER.get(0); }

    /** 進行の最初のセクションの定義。 */
    public static SectionTheme first() { return byId(firstId()); }

    // ===================== 固定長のセクション進行 =====================

    /** どのセクションも 500 部屋。 */
    public static final int SECTION_LENGTH = 500;
    /** セクションごとの部屋数（ここにないものは SECTION_LENGTH）。2026-11-01 に B を 500 → 300。 */
    private static final Map<String, Integer> LENGTHS = Map.of("b", 300);

    /** セクション id の部屋数（セクション内の最後の番号）。 */
    public static int length(String id) { return LENGTHS.getOrDefault(canonical(id), SECTION_LENGTH); }
    /**
     * 進行順。最初のセクション（V）はロビー（部屋 0）から始まる。最後のセクションの後は、そのセクションがそのまま続く。
     * H・F は既定でロック（ロック中は進行表で飛ばす＝V → B → U → S → A）。
     */
    public static final List<String> ORDER = List.of("v", "b", "u", "s", "r", "h", "f", "a");
    /** H を足す前の進行順（旧仕様の 500 長の表の判定用）。 */
    private static final List<String> LEGACY_ORDER = List.of("v", "b", "u", "s", "a");

    /**
     * first から始まるセクション表：first は通し番号 1（ORDER の最初のセクションはロビーが 0 なので 0）から、以降 ORDER の順に
     * 各セクションの長さごとに次のセクションへ切り替わる（V → B → U → S → [H] → A）。skip に当たるセクション（first 以外）は飛ばす。
     * 最後のセクション（A）の 500 部屋目より先は、そのまま続く（A-501, A-502 …）。
     */
    public static List<RoomsState.SectionStart> fixedChain(String firstId, java.util.function.Predicate<String> skip) {
        return chainFrom(firstId, firstId().equals(canonical(firstId)) ? 0 : 1, skip);
    }

    /** 既定のロック（LOCKED_BY_DEFAULT）で飛ばす版。 */
    public static List<RoomsState.SectionStart> fixedChain(String firstId) { return fixedChain(firstId, Sections::isLockedByDefault); }

    /** start から id、以降そのセクションの長さごとに ORDER の次のセクション（skip に当たるものは飛ばす）。ORDER にない id はそれ1つだけ。 */
    public static List<RoomsState.SectionStart> chainFrom(String id, int start, java.util.function.Predicate<String> skip) {
        String c = canonical(id);
        List<RoomsState.SectionStart> out = new java.util.ArrayList<>();
        out.add(new RoomsState.SectionStart(c, start));
        int i = ORDER.indexOf(c);
        if (i < 0) return out;
        int pos = Math.max(start, 1) + length(c); // 番号付きの最初の部屋（ロビー 0 から始まるセクションは 1）＋ 部屋数
        for (int k = i + 1; k < ORDER.size(); k++) {
            String n = ORDER.get(k);
            if (skip != null && skip.test(n)) continue;
            out.add(new RoomsState.SectionStart(n, pos));
            pos += length(n);
        }
        return out;
    }

    /** id から旧仕様（全セクション SECTION_LENGTH、H なし）でつないだ進行。 */
    private static List<RoomsState.SectionStart> legacyChain(String id, int start) {
        String c = canonical(id);
        List<RoomsState.SectionStart> out = new java.util.ArrayList<>();
        out.add(new RoomsState.SectionStart(c, start));
        int i = LEGACY_ORDER.indexOf(c);
        if (i < 0) return out;
        int first = Math.max(start, 1);
        for (int k = i + 1; k < LEGACY_ORDER.size(); k++) out.add(new RoomsState.SectionStart(LEGACY_ORDER.get(k), first + SECTION_LENGTH * (k - i)));
        return out;
    }

    /**
     * 旧仕様の進行表を今の長さにしたもの（後ろの部分が旧仕様のつなぎ方なら、そこを今の長さでつなぎ直す）。旧仕様でなければ null。
     * 例：[v0, b501, u1001, …] → [v0, b501, u801, …]、/rooms section b 1 の [v0, b1, u501, …] → [v0, b1, u301, …]。
     * 長さだけ直す（旧仕様の表にないセクション＝H は足さない。H を入れるのは RoomsState.relinkProgression）。
     */
    public static List<RoomsState.SectionStart> migratedChain(List<RoomsState.SectionStart> table) {
        for (int k = 0; k < table.size(); k++) {
            RoomsState.SectionStart f = table.get(k);
            if (LEGACY_ORDER.indexOf(canonical(f.themeId())) < 0) continue;
            List<RoomsState.SectionStart> legacy = legacyChain(f.themeId(), f.startRoom());
            if (legacy.size() < 2 || !legacy.equals(table.subList(k, table.size()))) continue;
            List<RoomsState.SectionStart> now = new java.util.ArrayList<>(table.subList(0, k));
            now.addAll(chainFrom(f.themeId(), f.startRoom(), n -> !LEGACY_ORDER.contains(n)));
            return now.equals(table) ? null : now;
        }
        return null;
    }

    public static boolean isLegacyChain(List<RoomsState.SectionStart> table) { return migratedChain(table) != null; }

    /**
     * room のセクションの最大の部屋番号（セクション内番号。次のセクションの開始の1つ手前）。
     * 次のセクションがない（最後のセクション、進行表の最後）なら -1（上限なし）。
     */
    public static int maxLocalRoom(RoomsState rs, int room) {
        int start = sectionStart(rs, room);
        for (RoomsState.SectionStart s : rs.sections) if (s.startRoom() > start) return s.startRoom() - Math.max(start, 1);
        return -1;
    }

    /** room がセクションの最初の番号付き部屋か。 */
    public static boolean isSectionFirstRoom(RoomsState rs, int room) { return room >= 1 && localRoom(rs, room) == 1; }

    private Sections() {}

    /** B-110 が生存中（B-20 / B-40 と同時に出さない）。 */
    private static boolean errorAlive(net.minecraft.server.level.ServerLevel l) {
        return com.example.roomsmod.entity.v.VSpawns.isAlive(l, com.example.roomsmod.RoomsMod.B_ERROR.get());
    }

    /** 別のセクションの敵（名前で探す）を、この区画の出現開始・確率で使う。生存確認・出現処理・排他グループは元のまま。 */
    private static SectionTheme.RusherSpawn reuse(SectionTheme from, String name, int minRoom, float chance) {
        for (SectionTheme.RusherSpawn r : from.rushers())
            if (r.name().equals(name)) return new SectionTheme.RusherSpawn(r.name(), minRoom, chance, r.alive(), r.spawn(), r.exclusionGroup());
        throw new IllegalStateException("unknown spawn " + name + " in section " + from.id());
    }

    private static SectionTheme.RusherSpawn vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind k, int minRoom, float chance) {
        return new SectionTheme.RusherSpawn(k.display, minRoom, chance, k::alive, k::spawn, k.exclusionGroup);
    }

    public static SectionTheme register(SectionTheme t) {
        if (THEMES.putIfAbsent(t.id(), t) != null) throw new IllegalStateException("duplicate section " + t.id());
        return t;
    }

    public static SectionTheme byId(String id) { SectionTheme t = THEMES.get(canonical(id)); return t != null ? t : THEMES.get(ORDER.get(0)); }

    /** 登録済みなら定義、なければ null。 */
    public static SectionTheme find(String id) { return THEMES.get(canonical(id)); }

    /**
     * 旧 ID の別名：GK は S に改名した（"gk" は古い保存データ用の非推奨の別名。コマンドの候補には出さない）。
     * 以前の "a"→"b" の別名は、新しい A（Abyss）区画と衝突するので廃止した。
     */
    public static String canonical(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(java.util.Locale.ROOT);
        return "gk".equals(s) ? "s" : s;
    }

    /**
     * ハードモードの抽選表：登録済みの全セクションの表をつなげたもの（同名は最初の1つ）。
     * 各エンティティの出現開始部屋（minRoom）はそのまま、今いるセクション内の部屋番号と比べる。
     */
    /** ハードモードの抽選表にだけ入るエンティティ（どのセクションにも属さない）。 */
    private static final List<SectionTheme.RusherSpawn> HARD_ONLY = List.of(
            // PLACEHOLDER の敵（32767 / 65535 / 2147483648）はどの抽選表にも入れない：3!3!2 の召喚（E332Entity.summonPool）と /rooms admin spawn だけで出る
            vSpawn(com.example.roomsmod.entity.v.VSpawns.Kind.E332, 1, 0.05f));

    /**
     * ギミック系（謎解き・決まった動き方を求める敵）：ハードモードでもホームのセクションの部屋にしか出さない（名前 → セクション id）。
     * 隠れる・逃げるで避けるふつうの追いかけ型（ラッシャー・経路を歩く敵）はハードの合同表でほかのセクションにも出る。
     * 通常モードはもともと各セクションの表だけなので変わらない。コマンド（/rooms admin spawn）は制限しない。
     */
    private static final Map<String, String> HOME_ONLY = Map.ofEntries(
            // S：ベル・スピーカー・通行証・小テスト・偽の扉・扉を閉める・巻き戻し・ロッカーの擬態・校内放送・見つめない・影（動き続ける）・懐中電灯
            Map.entry("S-10", "s"), Map.entry("S-25", "s"), Map.entry("S-45", "s"), Map.entry("S-65", "s"), Map.entry("S-85", "s"),
            Map.entry("S-105", "s"), Map.entry("S-125", "s"), Map.entry("S-145", "s"), Map.entry("S-165", "s"), Map.entry("S-185", "s"),
            Map.entry("S-205", "s"), Map.entry("S-270", "s"),
            // B：動くな（B-30 / B-70）・ウィンドウを閉じる（B-110）・扉に噛みつく（B-155）・欠片を拾う（B-205）
            Map.entry("B-30", "b"), Map.entry("B-70", "b"), Map.entry("B-110", "b"), Map.entry("B-155", "b"), Map.entry("B-205", "b"),
            // V：窓の覗き魔（しゃがむ・止まる）・指示どおりの隠れ場所（V25-BUFFED）
            Map.entry("V-100", "v"), Map.entry("V25-BUFFED", "v"),
            // F：音を立てない（F-5）
            Map.entry("F-5", "f"),
            // R：インターホン（ロッカーに隠れる）・覗く人（見つめない）
            Map.entry("R-5", "r"), Map.entry("R-35", "r"),
            // A：縦穴専用の敵は全部 A だけ（3!3!2 の召喚でもほかのセクションには出さない）
            Map.entry("A-25", "a"), Map.entry("A-50", "a"), Map.entry("A-90", "a"), Map.entry("A-130", "a"), Map.entry("A-180", "a"),
            Map.entry("A-200", "a"), Map.entry("A-250", "a"), Map.entry("A-300", "a"), Map.entry("A-350", "a"), Map.entry("A-450", "a"),
            Map.entry("A-525", "a"), Map.entry("A-600", "a"), Map.entry("A-666", "a"), Map.entry("A-750", "a"), Map.entry("A-800", "a"),
            Map.entry("A-1000", "a"), Map.entry("A-1050", "a"), Map.entry("A-1100", "a"), Map.entry("A-1111", "a"), Map.entry("A-1180", "a"));

    /** name がギミック系ならホームのセクション id、でなければ null。 */
    public static String homeOnly(String name) { return HOME_ONLY.get(name); }

    /** name（ギミック系）を部屋 room に出してはいけないか（room のセクションがホームでない）。 */
    public static boolean gimmickBlocked(RoomsState rs, int room, String name) {
        String h = HOME_ONLY.get(name);
        return h != null && !h.equals(canonical(forRoom(rs, room).id()));
    }

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
        SectionTheme cur = first();
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
     * startRoom 0 のセクション（ORDER の最初＝V）は部屋 0 がロビーなので最初の番号付き部屋は 1 → 全体番号と同じ。
     * 式: local = room - max(startRoom, 1) + 1
     */
    public static int localRoom(RoomsState rs, int room) {
        return room - Math.max(sectionStart(rs, room), 1) + 1;
    }

    /** ORDER の通しの深さ（前のセクションの部屋数の合計 + local）。ORDER にないセクションは -1。 */
    public static int absoluteDepth(String id, int local) {
        int i = ORDER.indexOf(canonical(id));
        if (i < 0) return -1;
        int off = 0;
        for (int k = 0; k < i; k++) off += length(ORDER.get(k));
        return off + local;
    }

    /** 今のセクション表で、セクション id の local 番目の部屋の通し番号（表にないなら -1）。 */
    public static int globalRoom(RoomsState rs, String id, int local) {
        String c = canonical(id);
        for (RoomsState.SectionStart s : rs.sections) {
            if (!canonical(s.themeId()).equals(c)) continue;
            int g = Math.max(s.startRoom(), 1) + local - 1;
            if (canonical(forRoom(rs, g).id()).equals(c) && localRoom(rs, g) == local) return g;
        }
        return -1;
    }

    /** 表示用の部屋番号（セクション接頭辞 + セクション内番号）。 */
    public static String formatRoom(RoomsState rs, int room) { return forRoom(rs, room).formatRoom(localRoom(rs, room)); }
}
