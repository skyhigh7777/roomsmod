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
 *   <li>左の壁（入口から出口扉を向いて左 = +X 側の壁 x=LOBBY_WIDTH-1=20）：スタート地点の選択。
 *       [区画] [−] 表示 [＋] [GO]。区画は登録済みセクションを順に巡回（スニークで逆順）、番号は STEPS を巡回。
 *       GO で「/rooms section &lt;s&gt; 1」＋「/rooms to &lt;n&gt;」と同じ（ロビー再生成＝全員の進行がリセット）。
 *       選択はサーバーで共有（看板1枚に表示されるため）。</li>
 *   <li>右の壁（x=0）：売店（economy.Shop）。電池・包帯・アドレナリン・お守り・レーダー・スキャナーをコインで買う。</li>
 *   <li>出口扉側の壁（z=31, x=14）：いちばん奥の生成済み部屋の番号を表示し、右クリックでそこへワープ（進行はリセットしない）。</li>
 * </ul>
 * パネルのブロックと看板は壊せず、部屋ディメンションのブロック操作禁止の例外（DoorEventHandler）。
 */
public final class LobbyPanels {
    private LobbyPanels() {}

    public static final int MAX_ROOM = 500;
    public static final int[] STEPS = {1, 50, 100, 150, 200, 250, 300, 400, 500};
    static final BlockPos ORIGIN = RoomsLayout.LOBBY_ORIGIN;

    enum Kind { SECTION, MINUS, PLUS, GO, DEEPEST, LABEL, HARD, SHOP_BATTERY, SHOP_BANDAGE, SHOP_ADRENALINE, SHOP_CHARM, SHOP_RADAR, SHOP_SCANNER }

    // 左の壁: ボタンは壁の中（x=19, y=2）、看板は壁の手前（x=18）
    static final int LX = RoomsLayout.LOBBY_WIDTH - 1, BY = 2;
    static final int Z_SECTION = 11, Z_MINUS = 13, Z_DISPLAY = 15, Z_PLUS = 17, Z_GO = 19;
    /** ハードモードの切り替え（左の壁、スタート選択から少し離す）。 */
    static final int Z_HARD = 24;
    /** 売店（右の壁 x=0、柱 z=7/13/19 を避ける）。ボタンは壁の中、看板は x=1。値段は economy.Shop。 */
    static final int SX = 0, Z_SHOP_BATTERY = 10, Z_SHOP_BANDAGE = 11, Z_SHOP_ADRENALINE = 12, Z_SHOP_CHARM = 14, Z_SHOP_RADAR = 15, Z_SHOP_SCANNER = 16;
    // 正面（扉側）: 扉は x=10 なので x=14（扉の両脇 x=9..11 から離す）
    static final int FZ = 31, FX = 14;

    /** 共有の選択。 */
    static String section = Sections.firstId();
    static int stepIndex = 0;

    public static String selectedSection() { return section; }
    /** 選んでいるセクションで選べる番号（セクションの部屋数まで。B は 300 まで）。 */
    static int[] steps() {
        int len = Sections.length(section);
        int[] s = java.util.Arrays.stream(STEPS).filter(n -> n <= len).toArray();
        return s.length == 0 ? new int[]{1} : s;
    }
    public static int selectedRoom() { int[] s = steps(); return s[Math.min(stepIndex, s.length - 1)]; }

    private static BlockPos at(int x, int y, int z) { return ORIGIN.offset(x, y, z); }

    static List<BlockPos> buttons() {
        return List.of(at(LX, BY, Z_SECTION), at(LX, BY, Z_MINUS), at(LX, BY, Z_PLUS), at(LX, BY, Z_GO), at(FX, BY, FZ), at(LX, BY, Z_HARD),
                at(SX, BY, Z_SHOP_BATTERY), at(SX, BY, Z_SHOP_BANDAGE), at(SX, BY, Z_SHOP_ADRENALINE), at(SX, BY, Z_SHOP_CHARM),
                at(SX, BY, Z_SHOP_RADAR), at(SX, BY, Z_SHOP_SCANNER));
    }
    static List<BlockPos> signs() {
        List<BlockPos> l = new ArrayList<>();
        for (int z : new int[]{Z_SECTION, Z_MINUS, Z_DISPLAY, Z_PLUS, Z_GO}) l.add(at(LX - 1, BY + 1, z));
        l.add(at(LX - 1, BY, Z_DISPLAY));
        l.add(at(FX, BY + 1, FZ - 1));
        l.add(at(LX - 1, BY + 1, Z_HARD));
        for (int z : new int[]{Z_SHOP_BATTERY, Z_SHOP_BANDAGE, Z_SHOP_ADRENALINE, Z_SHOP_CHARM, Z_SHOP_RADAR, Z_SHOP_SCANNER}) l.add(at(SX + 1, BY + 1, z));
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
        if (p.getX() == SX || p.getX() == SX + 1) {
            if (p.getZ() == Z_SHOP_BATTERY) return Kind.SHOP_BATTERY;
            if (p.getZ() == Z_SHOP_BANDAGE) return Kind.SHOP_BANDAGE;
            if (p.getZ() == Z_SHOP_ADRENALINE) return Kind.SHOP_ADRENALINE;
            if (p.getZ() == Z_SHOP_CHARM) return Kind.SHOP_CHARM;
            if (p.getZ() == Z_SHOP_RADAR) return Kind.SHOP_RADAR;
            if (p.getZ() == Z_SHOP_SCANNER) return Kind.SHOP_SCANNER;
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

    /** パネル・売店の配置の版。配置を変えたら上げる（既存ワールドのロビーも ensure で作り直される）。2 = 売店 6 品（2026-11-01）。 */
    public static final int LAYOUT_VERSION = 4; // 3: スキャナーの看板を 10 個パックに、4: スキャナー・レーダーを 1 個ずつの値段に

    /**
     * 既存ワールドのロビー（jar 更新前に作られたもの）でもパネル・売店を今の配置にする。ロビーにプレイヤーがいる時に呼ぶ（RoomsRun）。
     * 版が違うか、売店のボタンが欠けていたら、売店の壁（x=0）の古いボタン・看板を壁に戻してから build し直す。何度呼んでも同じ。
     */
    public static void ensure(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        var lobby = rs.boxes(0);
        if (lobby.isEmpty() || lobby.get(0).minX() != ORIGIN.getX() || lobby.get(0).minY() != ORIGIN.getY() || lobby.get(0).minZ() != ORIGIN.getZ()) return; // 旧位置のロビー
        boolean missing = false;
        for (int z : new int[]{Z_SHOP_BATTERY, Z_SHOP_BANDAGE, Z_SHOP_ADRENALINE, Z_SHOP_CHARM, Z_SHOP_RADAR, Z_SHOP_SCANNER})
            if (!(level.getBlockState(at(SX + 1, BY + 1, z)).getBlock() instanceof WallSignBlock)) { missing = true; break; }
        if (rs.panelsVersion == LAYOUT_VERSION && !missing) return;
        int oldVersion = rs.panelsVersion;
        int depth = lobby.get(0).maxZ() - lobby.get(0).minZ() + 1, height = lobby.get(0).maxY() - lobby.get(0).minY();
        var quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        var bricks = Blocks.QUARTZ_BRICKS.defaultBlockState();
        var pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        // 売店の壁（x=0）を元の壁に、その手前（x=1）の看板を取る
        for (int z = 1; z < depth - 1; z++) {
            for (int y = 1; y < height; y++) place(level, at(SX, y, z), z % 6 == 1 ? pillar : y == height - 1 ? bricks : quartz);
            for (int y = 1; y < height; y++) if (level.getBlockState(at(SX + 1, y, z)).getBlock() instanceof WallSignBlock) level.setBlock(at(SX + 1, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        build(level);
        RoomsDebug.info("lobby panels rebuilt (layout {} -> {}, missing={})", oldVersion, LAYOUT_VERSION, missing);
    }

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
        place(level, at(SX, BY, Z_SHOP_BATTERY), Blocks.YELLOW_CONCRETE.defaultBlockState());
        place(level, at(SX, BY, Z_SHOP_BANDAGE), Blocks.WHITE_WOOL.defaultBlockState());
        place(level, at(SX, BY, Z_SHOP_ADRENALINE), Blocks.RED_CONCRETE.defaultBlockState());
        place(level, at(SX, BY, Z_SHOP_CHARM), Blocks.AMETHYST_BLOCK.defaultBlockState());
        place(level, at(SX, BY, Z_SHOP_RADAR), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        place(level, at(SX, BY, Z_SHOP_SCANNER), Blocks.CYAN_CONCRETE.defaultBlockState());
        sign(level, at(SX + 1, BY + 1, Z_SHOP_BATTERY), Direction.EAST, "SHOP", "電池 BATTERY", com.example.roomsmod.economy.Shop.Product.BATTERY.price + " COIN", "");
        sign(level, at(SX + 1, BY + 1, Z_SHOP_BANDAGE), Direction.EAST, "SHOP", "包帯 BANDAGE", com.example.roomsmod.economy.Shop.Product.BANDAGE.price + " COIN", "");
        sign(level, at(SX + 1, BY + 1, Z_SHOP_ADRENALINE), Direction.EAST, "SHOP", "アドレナリン", "ADRENALINE", com.example.roomsmod.economy.Shop.Product.ADRENALINE.price + " COIN");
        sign(level, at(SX + 1, BY + 1, Z_SHOP_CHARM), Direction.EAST, "SHOP", "お守り CHARM", com.example.roomsmod.economy.Shop.Product.CHARM.price + " COIN", "最大 " + com.example.roomsmod.economy.Wallet.MAX_CHARM);
        sign(level, at(SX + 1, BY + 1, Z_SHOP_RADAR), Direction.EAST, "SHOP", "レーダー RADAR", com.example.roomsmod.economy.Shop.Product.RADAR.price + " COIN", "最大 " + com.example.roomsmod.economy.Wallet.MAX_RADAR);
        sign(level, at(SX + 1, BY + 1, Z_SHOP_SCANNER), Direction.EAST, "SHOP", "スキャナー SCANNER", com.example.roomsmod.economy.Shop.Product.SCANNER.price + " COIN", "最大 " + com.example.roomsmod.economy.Wallet.MAX_SCANNER);
        refreshSelection(level);
        refreshDeepest(level);
        refreshHard(level);
        RoomsState.get(level).panelsVersion = LAYOUT_VERSION;
        RoomsState.get(level).setDirty();
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
        return (t == null ? Sections.first().prefix() : t.prefix()) + "-" + String.format("%03d", selectedRoom());
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
        List<String> ids = new ArrayList<>(Sections.selectableIds(RoomsState.get(level))); // ロックされたセクション（既定は PLACEHOLDER）は出さない
        switch (k) {
            case SECTION -> {
                int i = Math.max(0, ids.indexOf(section));
                section = ids.get(Math.floorMod(i + (player.isShiftKeyDown() ? -1 : 1), ids.size()));
                stepIndex = Math.min(stepIndex, steps().length - 1); // 短いセクション（B=300）では上限に合わせる
                selectionChanged(level, player, "section");
            }
            case MINUS -> { stepIndex = Math.floorMod(Math.min(stepIndex, steps().length - 1) - 1, steps().length); selectionChanged(level, player, "minus"); }
            case PLUS -> { stepIndex = Math.floorMod(Math.min(stepIndex, steps().length - 1) + 1, steps().length); selectionChanged(level, player, "plus"); }
            case LABEL -> player.displayClientMessage(Component.literal("スタート: " + label()), true);
            case GO -> go(level, player);
            case DEEPEST -> deepest(level, player);
            case SHOP_BATTERY -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.BATTERY);
            case SHOP_BANDAGE -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.BANDAGE);
            case SHOP_ADRENALINE -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.ADRENALINE);
            case SHOP_CHARM -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.CHARM);
            case SHOP_RADAR -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.RADAR);
            case SHOP_SCANNER -> com.example.roomsmod.economy.Shop.buy(player, com.example.roomsmod.economy.Shop.Product.SCANNER);
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

    /** セクションのロックが変わった（オーナーの /rooms admin section）。選択中がロックされたら最初のセクションへ。 */
    public static void onAccessChanged(ServerLevel level) {
        if (RoomsState.get(level).isSectionLocked(section)) { section = Sections.firstId(); stepIndex = 0; }
        refreshSelection(level);
    }

    static void go(ServerLevel level, ServerPlayer player) {
        if (RoomsTravel.busy()) { player.displayClientMessage(com.example.roomsmod.util.RoomsMsg.barDeny("部屋を生成中。完了まで待機"), true); return; }
        String s = section; int n = selectedRoom();
        RoomsState rs = RoomsState.get(level);
        if (rs.isSectionLocked(s)) { player.displayClientMessage(Component.translatable("message.roomsmod.section_locked", s.toUpperCase(java.util.Locale.ROOT)), true); return; }
        rs.setChain(s); // 選んだセクションから、500部屋ごとに次のセクションへ
        RoomsDebug.log(player, "ロビーパネル GO: " + label(), "lobby panel go by {} section={} room={} table={}", player.getGameProfile().getName(), s, n, rs.sections);
        if (com.example.roomsmod.world.RoomsTravel.busy()) { player.displayClientMessage(com.example.roomsmod.util.RoomsMsg.barDeny("部屋を生成中。完了まで待機"), true); return; }
        com.example.roomsmod.economy.RoomsRun.panelTeleport(player); // 開始はワープ先（RoomsRun.track のジャンプ判定）
        RoomsCommands.goToRoom(player, n);
    }

    static void deepest(ServerLevel level, ServerPlayer player) {
        RoomsState rs = RoomsState.get(level);
        int d = rs.roomPaths.size() - 1;
        if (d <= 0) { player.displayClientMessage(com.example.roomsmod.util.RoomsMsg.barDeny("部屋が未生成（出口の扉を開けると生成）"), true); RoomsDebug.log("lobby panel deepest by {} -> no rooms", player.getGameProfile().getName()); return; }
        Vec3 v = SafeSpot.find(level, rs, d);
        if (v == null) { player.displayClientMessage(com.example.roomsmod.util.RoomsMsg.barDeny("ワープ先が見つからない"), true); RoomsDebug.warn("lobby panel deepest: no safe spot room={}", d); return; }
        player.teleportTo(level, v.x, v.y, v.z, 0, 0);
        RoomsDebug.log(player, "最奥の部屋 " + Sections.formatRoom(rs, d) + " へワープ", "lobby panel deepest by {} -> room={} ({}) at ({}, {}, {})", player.getGameProfile().getName(), d, Sections.formatRoom(rs, d), v.x, v.y, v.z);
    }
}
