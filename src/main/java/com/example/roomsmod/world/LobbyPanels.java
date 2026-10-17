package com.example.roomsmod.world;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * ロビーのパネル（ロビーを生成するたびに作り直す＝既存ワールドも次にロビーへ入れば付く）。
 * <ul>
 *   <li>左の壁（入口から出口扉を向いて左 = +X 側の壁 x=19）：スタート地点の選択。
 *       [区画] [−] 表示 [＋] [GO]。区画は登録済みセクションを順に巡回（スニークで逆順）、番号は STEPS を巡回。
 *       GO で「/rooms section &lt;s&gt; 1」＋「/rooms to &lt;n&gt;」と同じ（ロビー再生成＝全員の進行がリセット）。
 *       選択はサーバーで共有（看板1枚に表示されるため）。</li>
 *   <li>出口扉側の壁（z=31, x=14）：いちばん奥の生成済み部屋の番号を表示し、右クリックでそこへワープ（進行はリセットしない）。</li>
 * </ul>
 * パネルのブロックと看板は壊せず、部屋ディメンションのブロック操作禁止の例外（DoorEventHandler）。
 */
public final class LobbyPanels {
    private LobbyPanels() {}

    public static final int MAX_ROOM = 500;
    public static final int[] STEPS = {1, 50, 100, 150, 200, 250, 300, 400, 500};
    static final BlockPos ORIGIN = new BlockPos(0, 1, 0);

    enum Kind { SECTION, MINUS, PLUS, GO, DEEPEST, LABEL, HARD }

    // 左の壁: ボタンは壁の中（x=19, y=2）、看板は壁の手前（x=18）
    static final int LX = 19, BY = 2;
    static final int Z_SECTION = 11, Z_MINUS = 13, Z_DISPLAY = 15, Z_PLUS = 17, Z_GO = 19;
    /** ハードモードの切り替え（左の壁、スタート選択から少し離す）。 */
    static final int Z_HARD = 24;
    // 正面（扉側）: 扉は x=10 なので x=14（扉の両脇 x=9..11 から離す）
    static final int FZ = 31, FX = 14;

    /** 共有の選択。 */
    static String section = "b";
    static int stepIndex = 0;

    public static String selectedSection() { return section; }
    public static int selectedRoom() { return STEPS[stepIndex]; }

    private static BlockPos at(int x, int y, int z) { return ORIGIN.offset(x, y, z); }

    static List<BlockPos> buttons() {
        return List.of(at(LX, BY, Z_SECTION), at(LX, BY, Z_MINUS), at(LX, BY, Z_PLUS), at(LX, BY, Z_GO), at(FX, BY, FZ), at(LX, BY, Z_HARD));
    }
    static List<BlockPos> signs() {
        List<BlockPos> l = new ArrayList<>();
        for (int z : new int[]{Z_SECTION, Z_MINUS, Z_DISPLAY, Z_PLUS, Z_GO}) l.add(at(LX - 1, BY + 1, z));
        l.add(at(LX - 1, BY, Z_DISPLAY));
        l.add(at(FX, BY + 1, FZ - 1));
        l.add(at(LX - 1, BY + 1, Z_HARD));
        return l;
    }

    static Kind kindAt(BlockPos p) {
        if (p.getY() != BY && p.getY() != BY + 1) return null;
        if (p.getX() == LX || p.getX() == LX - 1) {
            if (p.getZ() == Z_SECTION) return Kind.SECTION;
            if (p.getZ() == Z_MINUS) return Kind.MINUS;
            if (p.getZ() == Z_PLUS) return Kind.PLUS;
            if (p.getZ() == Z_GO) return Kind.GO;
            if (p.getZ() == Z_HARD) return Kind.HARD;
            if (p.getZ() == Z_DISPLAY && (p.getX() == LX - 1)) return Kind.LABEL;
        }
        if (p.getX() == FX && (p.getZ() == FZ || p.getZ() == FZ - 1)) return Kind.DEEPEST;
        return null;
    }

    /** このブロックがパネルの一部か（右クリックの例外・破壊の保護）。部屋ディメンションのみ。 */
    public static boolean isPanel(Level level, BlockPos p) {
        if (!level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return false;
        return buttons().contains(p) || signs().contains(p);
    }

    // ===================== 生成 =====================

    /** generateLobby から（壁を作ったあと）。 */
    static void build(ServerLevel level) {
        place(level, at(LX, BY, Z_SECTION), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        place(level, at(LX, BY, Z_MINUS), Blocks.RED_CONCRETE.defaultBlockState());
        place(level, at(LX, BY, Z_PLUS), Blocks.LIME_CONCRETE.defaultBlockState());
        place(level, at(LX, BY, Z_GO), Blocks.GOLD_BLOCK.defaultBlockState());
        place(level, at(FX, BY, FZ), Blocks.EMERALD_BLOCK.defaultBlockState());
        sign(level, at(LX - 1, BY + 1, Z_SECTION), Direction.WEST, "", "SECTION", "区画", "");
        sign(level, at(LX - 1, BY + 1, Z_MINUS), Direction.WEST, "", "−", "戻す", "");
        sign(level, at(LX - 1, BY + 1, Z_PLUS), Direction.WEST, "", "＋", "進める", "");
        sign(level, at(LX - 1, BY + 1, Z_GO), Direction.WEST, "", "GO", "スタート", "");
        sign(level, at(LX - 1, BY + 1, Z_DISPLAY), Direction.WEST, "START ROOM", "スタート部屋", "", "");
        refreshSelection(level);
        refreshDeepest(level);
        refreshHard(level);
        RoomsDebug.log("lobby panels built selection={} deepest={}", label(), deepestLabel(RoomsState.get(level)));
    }

    private static void place(ServerLevel level, BlockPos p, BlockState s) { level.setBlockAndUpdate(p, s); }

    private static void sign(ServerLevel level, BlockPos p, Direction facing, String... lines) {
        level.setBlockAndUpdate(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        setText(level, p, lines);
    }

    private static void setText(ServerLevel level, BlockPos p, String... lines) { setText(level, p, DyeColor.WHITE, lines); }

    private static void setText(ServerLevel level, BlockPos p, DyeColor color, String... lines) {
        if (!(level.getBlockEntity(p) instanceof SignBlockEntity be)) return;
        SignText t = new SignText().setColor(color).setHasGlowingText(true);
        for (int i = 0; i < 4; i++) t = t.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        be.setText(t, true);
        be.setWaxed(true);
        be.setChanged();
        level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), 3);
    }

    static String label() {
        SectionTheme t = Sections.find(section);
        return (t == null ? "B" : t.prefix()) + "-" + String.format("%03d", selectedRoom());
    }

    static String deepestLabel(RoomsState rs) {
        int d = rs.roomPaths.size() - 1;
        return d <= 0 ? "---" : Sections.formatRoom(rs, d);
    }

    static void refreshSelection(ServerLevel level) {
        if (level.getBlockEntity(at(LX - 1, BY, Z_DISPLAY)) == null && !level.getBlockState(at(LX, BY, Z_DISPLAY)).isAir())
            level.setBlockAndUpdate(at(LX - 1, BY, Z_DISPLAY), Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
        setText(level, at(LX - 1, BY, Z_DISPLAY), "", label(), "", "");
    }

    /** 部屋の生成のたびに（RoomGenerator.generateNextRoom から）。ロビーがなければ何もしない。 */
    public static void refreshDeepest(ServerLevel level) {
        BlockPos p = at(FX, BY + 1, FZ - 1);
        if (!level.isLoaded(p)) return;
        RoomsState rs = RoomsState.get(level);
        if (!(level.getBlockEntity(p) instanceof SignBlockEntity)) {
            if (!level.getBlockState(at(FX, BY, FZ)).is(Blocks.EMERALD_BLOCK)) return;
            level.setBlockAndUpdate(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        }
        setText(level, p, "DEEPEST ROOM", "最奥の部屋", deepestLabel(rs), "右クリックでワープ");
    }

    /** ハードモードのボタンと看板（ON=赤、OFF=灰）。ロビーがなければ何もしない。 */
    public static void refreshHard(ServerLevel level) {
        BlockPos b = at(LX, BY, Z_HARD), sp = at(LX - 1, BY + 1, Z_HARD);
        if (!level.isLoaded(b) || !level.getBlockState(at(LX, BY, Z_GO)).is(Blocks.GOLD_BLOCK)) return;
        boolean on = RoomsState.get(level).hardMode;
        place(level, b, (on ? Blocks.RED_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
        if (!(level.getBlockEntity(sp) instanceof SignBlockEntity))
            level.setBlockAndUpdate(sp, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
        setText(level, sp, on ? DyeColor.RED : DyeColor.LIGHT_GRAY, "HARD MODE", on ? "ON" : "OFF", "ハードモード", "右クリックで切替");
    }

    // ===================== 操作 =====================

    /** 右クリック（サーバー・メインハンド）。 */
    public static void click(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Kind k = kindAt(pos.subtract(ORIGIN));
        if (k == null) return;
        List<String> ids = new ArrayList<>(Sections.ids());
        switch (k) {
            case SECTION -> {
                int i = Math.max(0, ids.indexOf(section));
                section = ids.get(Math.floorMod(i + (player.isShiftKeyDown() ? -1 : 1), ids.size()));
                selectionChanged(level, player, "section");
            }
            case MINUS -> { stepIndex = Math.floorMod(stepIndex - 1, STEPS.length); selectionChanged(level, player, "minus"); }
            case PLUS -> { stepIndex = Math.floorMod(stepIndex + 1, STEPS.length); selectionChanged(level, player, "plus"); }
            case LABEL -> player.displayClientMessage(Component.literal("スタート: " + label()), true);
            case GO -> go(level, player);
            case DEEPEST -> deepest(level, player);
            case HARD -> {
                boolean on = !RoomsState.get(level).hardMode;
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, on ? 0.7f : 1.2f);
                RoomsDebug.log(player, "ロビーパネル: HARD MODE " + (on ? "ON" : "OFF"), "lobby panel hard by {} -> {}", player.getGameProfile().getName(), on);
                HardMode.set(level, on, player.getGameProfile().getName());
            }
        }
    }

    private static void selectionChanged(ServerLevel level, ServerPlayer player, String what) {
        refreshSelection(level);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(Component.literal("スタート: " + label()), true);
        RoomsDebug.log(player, "ロビーパネル: " + label(), "lobby panel {} by {} -> section={} room={}", what, player.getGameProfile().getName(), section, selectedRoom());
    }

    static void go(ServerLevel level, ServerPlayer player) {
        if (RoomsTravel.busy()) { player.displayClientMessage(Component.literal("いま部屋を生成中よ、少し待ってね"), true); return; }
        String s = section; int n = selectedRoom();
        RoomsState rs = RoomsState.get(level);
        rs.sections.clear();
        rs.sections.add(new RoomsState.SectionStart("b", 0));
        if (!"b".equals(s)) rs.addSection(s, 1);
        rs.setDirty();
        RoomsDebug.log(player, "ロビーパネル GO: " + label(), "lobby panel go by {} section={} room={} table={}", player.getGameProfile().getName(), s, n, rs.sections);
        if (com.example.roomsmod.world.RoomsTravel.busy()) { player.displayClientMessage(Component.literal("いま部屋を生成中よ、少し待ってね"), true); return; }
        RoomsCommands.goToRoom(player, n);
    }

    static void deepest(ServerLevel level, ServerPlayer player) {
        RoomsState rs = RoomsState.get(level);
        int d = rs.roomPaths.size() - 1;
        if (d <= 0) { player.displayClientMessage(Component.literal("まだ部屋がないわ（出口の扉を開けてね）"), true); RoomsDebug.log("lobby panel deepest by {} -> no rooms", player.getGameProfile().getName()); return; }
        Vec3 v = SafeSpot.find(level, rs, d);
        if (v == null) { player.displayClientMessage(Component.literal("ワープ先が見つからないわ"), true); RoomsDebug.warn("lobby panel deepest: no safe spot room={}", d); return; }
        player.teleportTo(level, v.x, v.y, v.z, 0, 0);
        RoomsDebug.log(player, "最奥の部屋 " + Sections.formatRoom(rs, d) + " へワープ", "lobby panel deepest by {} -> room={} ({}) at ({}, {}, {})", player.getGameProfile().getName(), d, Sections.formatRoom(rs, d), v.x, v.y, v.z);
    }
}
