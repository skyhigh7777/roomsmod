package com.example.roomsmod.entity.hard;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 3!3!2。ハードモード専用（どのセクションにも属さない。Sections.hardTable と /rooms spawn 332 のみ）。
 * 姿なし（不可視・当たり判定なし・描画なし）。タイミングの定数は下を参照。状態は NBT に保存。
 */
public class E332Entity extends VEntity {
    public static final String NAME = "3!3!2";
    public static final int BAR_DELAY_MIN = 20, BAR_DELAY_MAX = 55;
    public static final int FILL_MIN = 60, FILL_MAX = 80;
    public static final int LINGER = 30;
    public static final String TITLE_START = "!Tz 3!3!2iii", TITLE_PREFIX = "!Tz ";

    /** 各エンティティ（抽選表の名前）の出現・接近の音。ランダムに1つ選んでピッチを変えて鳴らす。 */
    private static final Map<String, Supplier<SoundEvent>> SOUNDS = new LinkedHashMap<>();
    static {
        SOUNDS.put("B-20", RoomsMod.A20_APPROACH::get);
        SOUNDS.put("B-40", RoomsMod.A40_APPROACH::get);
        SOUNDS.put("B-15", () -> SoundEvents.PORTAL_AMBIENT);
        SOUNDS.put("B-30", () -> SoundEvents.WITCH_CELEBRATE);
        SOUNDS.put("B-50", () -> SoundEvents.SKELETON_AMBIENT);
        SOUNDS.put("B-70", () -> SoundEvents.ENDER_EYE_DEATH);
        SOUNDS.put("B-90", () -> SoundEvents.SOUL_ESCAPE.value());
        SOUNDS.put("B-110", () -> SoundEvents.NOTE_BLOCK_BIT.value());
        SOUNDS.put("B-130", () -> SoundEvents.WARDEN_AMBIENT);
        SOUNDS.put("B-155", () -> SoundEvents.EVOKER_FANGS_ATTACK);
        SOUNDS.put("B-180", () -> SoundEvents.ELDER_GUARDIAN_AMBIENT);
        SOUNDS.put("B-205", () -> SoundEvents.GHAST_SCREAM);
        SOUNDS.put("B-230", () -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM);
        SOUNDS.put("B-260", () -> SoundEvents.WARDEN_SONIC_CHARGE);
        SOUNDS.put("B-290", () -> SoundEvents.CREEPER_PRIMED);
        SOUNDS.put("V-10", RoomsMod.V10_APPROACH::get);
        SOUNDS.put("V-25-T", RoomsMod.V25T_STEP::get);
        SOUNDS.put("V-25-L", RoomsMod.V25L_STEP::get);
        SOUNDS.put("V25-BUFFED", RoomsMod.V25B_REVEAL::get);
        SOUNDS.put("V-100", RoomsMod.V100_KNOCK::get);
        SOUNDS.put("V-50", RoomsMod.V50_APPROACH::get);
        SOUNDS.put("S-10", RoomsMod.GK_MOTH_FLUTTER::get);
        SOUNDS.put("S-25", RoomsMod.GK_CHIME_APPROACH::get);
        SOUNDS.put("S-45", RoomsMod.GK_WHISPER_SOUND::get);
        SOUNDS.put("S-65", RoomsMod.GK_DETENTION_CHAIN::get);
        SOUNDS.put("S-85", RoomsMod.GK_DESK_CHECK::get);
        SOUNDS.put("S-105", RoomsMod.GK_SUBSTITUTE_CREAK::get);
        SOUNDS.put("S-125", RoomsMod.GK_07_KNOCK::get);
        SOUNDS.put("S-145", RoomsMod.GK_HOMEWORK_PAGE::get);
        SOUNDS.put("S-165", RoomsMod.GK_ROLLCALL_STEP::get);
        SOUNDS.put("S-185", RoomsMod.GK_PA_CHIME::get);
        SOUNDS.put("S-205", RoomsMod.GK_STARE_HUM::get);
        SOUNDS.put("S-225", RoomsMod.GK_JANITOR_SQUEAK::get);
        SOUNDS.put("S-245", RoomsMod.GK_ERASER_SCRATCH::get);
        SOUNDS.put("S-270", RoomsMod.GK_MIRROR_CHIME::get);
        SOUNDS.put("S-300", RoomsMod.GK_BELL2_BELL::get);
        SOUNDS.put("U-20", RoomsMod.U20_APPROACH::get);
        SOUNDS.put("U-60", RoomsMod.U60_APPROACH::get);
        SOUNDS.put("U-120", RoomsMod.U120_APPROACH::get);
        SOUNDS.put("U-200", RoomsMod.U200_STEP::get);
        SOUNDS.put("U-300", RoomsMod.U300_APPROACH::get);
    }

    /** 0=出現直後, 1=待ち, 2=バー進行中, 3=満タン後 */
    private int phase = 0, phaseTicks = 0, barDelay = 0, fillTicks = 0;
    private String chosen = null;
    private ServerBossEvent bar = null;

    public E332Entity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setInvisible(true);
        setSilent(true);
    }

    @Override public String vName() { return NAME; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldShowName() { return false; }

    /** 位置は意味を持たない（姿なし）。プレイヤーのいる部屋の経路の先頭に置くだけ。 */
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        List<BlockPos> p = playerRoom >= 0 && playerRoom < rs.roomPaths.size() ? rs.path(playerRoom) : List.of();
        if (p.isEmpty()) return null;
        BlockPos at = p.get(0);
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        startRoom = playerRoom;
        return at;
    }

    private void enter(int ph) { phase = ph; phaseTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        if (!isInvisible()) setInvisible(true);
        phaseTicks++;
        switch (phase) {
            case 0 -> {
                playCue(level);
                barDelay = BAR_DELAY_MIN + random.nextInt(BAR_DELAY_MAX - BAR_DELAY_MIN + 1);
                fillTicks = FILL_MIN + random.nextInt(FILL_MAX - FILL_MIN + 1);
                RoomsDebug.log("{} appeared, bar in {} ticks, fill {} ticks", NAME, barDelay, fillTicks);
                enter(1);
            }
            case 1 -> {
                if (phaseTicks >= barDelay) {
                    ensureBar().setName(Component.literal(TITLE_START));
                    bar.setProgress(0f);
                    RoomsDebug.log("{} bar shown", NAME);
                    enter(2);
                }
            }
            case 2 -> {
                ServerBossEvent b = ensureBar();
                b.setName(Component.literal(TITLE_START));
                b.setProgress(Math.min(1f, phaseTicks / (float) Math.max(1, fillTicks)));
                if (phaseTicks >= fillTicks) {
                    b.setProgress(1f);
                    reveal(level);
                    enter(3);
                }
            }
            case 3 -> {
                ServerBossEvent b = ensureBar();
                b.setProgress(1f);
                if (chosen != null) b.setName(Component.literal(TITLE_PREFIX + chosen));
                if (phaseTicks >= LINGER) { leave("done"); return; }
            }
            default -> enter(0);
        }
        if (bar != null) syncBarPlayers(level);
    }

    /** ランダムなエンティティの音を、ピッチをはっきり変えて（低い ~0.5 か高い ~1.8〜2.0）部屋ディメンションの全員の位置で鳴らす。 */
    private void playCue(ServerLevel level) {
        List<String> names = new ArrayList<>(SOUNDS.keySet());
        String who = names.get(random.nextInt(names.size()));
        SoundEvent s = SOUNDS.get(who).get();
        float pitch = random.nextBoolean() ? 0.5f + random.nextFloat() * 0.1f : 1.8f + random.nextFloat() * 0.2f;
        for (ServerPlayer p : level.players()) level.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.HOSTILE, 1.0f, pitch);
        RoomsDebug.log("{} cue sound of {} pitch={}", NAME, who, String.format("%.2f", pitch));
    }

    /**
     * 名前を決めてバーを書き換え、通常の抽選と同じ出現処理で出す（次の tick、エンティティの tick の外で）。
     * 例外：3!3!2 が呼んだ敵は重なりの規則をすべて無視する（生存中の同種・排他グループ、分類の矛盾、S-270 の単独ルール）。
     * 候補は 3!3!2 以外の全エンティティ（summonPool）。通常の抽選（DoorEventHandler.rollRushers）は変えない。
     */
    private void reveal(ServerLevel level) {
        List<SectionTheme.RusherSpawn> all = summonPool();
        if (all.isEmpty()) { chosen = "?"; return; }
        SectionTheme.RusherSpawn pick = weightedPick(all);
        chosen = pick.name();
        ensureBar().setName(Component.literal(TITLE_PREFIX + chosen));
        RoomsDebug.log("{} full -> {} (of {} candidates, overlap rules ignored, alive={})", NAME, chosen, all.size(), pick.alive().test(level));
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            RoomsState rs = RoomsState.get(level);
            if (rs.roomPaths.isEmpty()) { RoomsDebug.log("{} -> {} spawn skipped: no rooms", NAME, pick.name()); return; }
            if (com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.blocksSpawns(level)) { RoomsDebug.log("{} -> {} summon blocked: PLACEHOLDER-2147483648 alive", NAME, pick.name()); return; }
            Player front = frontPlayer(level, rs);
            try {
                if (!pick.spawn().spawn(level, rs, front)) RoomsDebug.log("{} -> {} spawn failed (placement)", NAME, pick.name());
            } catch (RuntimeException e) { RoomsDebug.error(NAME + " -> " + pick.name() + " spawn failed", e); }
        }));
    }

    /** 召喚候補の重み（通常 1.0、個別に下げたいものだけ小さく）。 */
    public static float summonWeight(String name) {
        return com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.NAME.equals(name)
                ? com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.SUMMON_WEIGHT : 1f;
    }

    /** summonWeight による重み付き抽選。 */
    private SectionTheme.RusherSpawn weightedPick(List<SectionTheme.RusherSpawn> all) {
        double total = 0;
        for (SectionTheme.RusherSpawn r : all) total += summonWeight(r.name());
        double x = random.nextDouble() * total;
        for (SectionTheme.RusherSpawn r : all) { x -= summonWeight(r.name()); if (x < 0) return r; }
        return all.get(all.size() - 1);
    }

    /**
     * 呼べる敵：3!3!2 以外の全エンティティ。ハード表（B-20/B-40 と各セクションの敵）に、
     * どの抽選表にも入らない種類（PLACEHOLDER など、/rooms spawn と同じ VSpawns.Kind.spawn で出す）を足したもの。
     * 通常・ハードの抽選表は変えない。
     */
    public static List<SectionTheme.RusherSpawn> summonPool() {
        List<SectionTheme.RusherSpawn> all = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SectionTheme.RusherSpawn r : Sections.hardTable()) if (!NAME.equals(r.name()) && seen.add(r.name())) all.add(r);
        for (com.example.roomsmod.entity.v.VSpawns.Kind k : com.example.roomsmod.entity.v.VSpawns.Kind.values())
            if (k != com.example.roomsmod.entity.v.VSpawns.Kind.E332 && k != com.example.roomsmod.entity.v.VSpawns.Kind.EXTRABOX && seen.add(k.display))
                all.add(new SectionTheme.RusherSpawn(k.display, 0, 0f, k::alive, k::spawn, k.exclusionGroup));
        return all;
    }

    /** 一番奥の部屋にいるプレイヤー（扉を開けた人の代わり）。いなければ null。 */
    private static Player frontPlayer(ServerLevel level, RoomsState rs) {
        Player best = null; int bi = -2;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            int i = RoomTracker.roomIndexAt(rs, p.blockPosition());
            if (i > bi) { bi = i; best = p; }
        }
        return best;
    }

    private ServerBossEvent ensureBar() {
        if (bar == null) {
            String title = phase >= 3 && chosen != null ? TITLE_PREFIX + chosen : TITLE_START;
            bar = new ServerBossEvent(Component.literal(title), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(0f);
        }
        return bar;
    }

    /** 部屋ディメンションにいるプレイヤーだけに見せる（出入りに追従）。 */
    private void syncBarPlayers(ServerLevel level) {
        List<ServerPlayer> here = level.players();
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!here.contains(p) || p.isRemoved()) bar.removePlayer(p);
        for (ServerPlayer p : here) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
    }

    private void clearBar() {
        if (bar != null) { bar.removeAllPlayers(); bar.setVisible(false); bar = null; }
    }

    @Override
    public void onRemovedFromLevel() {
        clearBar();
        super.onRemovedFromLevel();
    }

    @Override protected String debugState() { return "phase=" + phase + " phaseTicks=" + phaseTicks + " barDelay=" + barDelay + " fill=" + fillTicks + " chosen=" + chosen; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase); t.putInt("PhaseTicks", phaseTicks); t.putInt("BarDelay", barDelay); t.putInt("FillTicks", fillTicks);
        if (chosen != null) t.putString("Chosen", chosen);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase"); phaseTicks = t.getInt("PhaseTicks"); barDelay = t.getInt("BarDelay"); fillTicks = t.getInt("FillTicks");
        chosen = t.contains("Chosen") ? t.getString("Chosen") : null;
        RoomsDebug.log("{} loaded {}", NAME, debugState());
    }
}
