package com.example.roomsmod.entity.hard;

import com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EXTRABOX（表示名 {@link #NAME}）。コマンド専用（/rooms spawn extrabox）。どの抽選表にも 3!3!2 の候補にも入らない。
 * 姿なし（3!3!2 と同じ）。何体同時にいてもよい。
 * <ol>
 *   <li>出現から START_DELAY tick 後に召喚を始める。</li>
 *   <li>BATCH_INTERVAL tick ごとに BATCH_SIZE 体をまとめて召喚（専用の音）、BATCHES 回。最後の回のあと消える。</li>
 *   <li>候補はハードモードの抽選表（Sections.hardTable）。1回の中では全部別の敵（回をまたいだ重複は可）。</li>
 *   <li>召喚は 3!3!2 と同じ通常の出現処理（ハードの強化・セクション外の扱いもそのまま）。
 *       排他グループ・同時出現の規則・生存中チェック・PLACEHOLDER-2147483648 の出現ブロックは無視。
 *       ただし S-65 の「1つの扉に1体」だけは残る（GKDetentionEntity.prepare）。</li>
 * </ol>
 * 状態は NBT に保存。
 */
public class ExtraboxEntity extends VEntity {
    public static final String NAME = "5|_||>3|2(4|_1|=|2491|_1571(3><|>14|_1|)0(10|_|5";
    public static final String ALIAS = "EXTRABOX";
    public static final int START_DELAY = 100, BATCH_INTERVAL = 20, BATCHES = 7, BATCH_SIZE = 5;

    private int ticks = 0, batches = 0;

    public ExtraboxEntity(EntityType<? extends Mob> type, Level level) {
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
        RoomsDebug.log("{} ({}) spawn room={} first batch in {} ticks", ALIAS, NAME, playerRoom, START_DELAY);
        return at;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (!isInvisible()) setInvisible(true);
        ticks++;
        if (ticks < START_DELAY || (ticks - START_DELAY) % BATCH_INTERVAL != 0) return;
        batch(level);
        if (batches >= BATCHES) leave("finished " + BATCHES + " batches");
    }

    /** 1回分：ハード表から別々の BATCH_SIZE 体を選び、次の tick（エンティティの tick の外）でまとめて出す。 */
    private void batch(ServerLevel level) {
        batches++;
        int n = batches;
        List<SectionTheme.RusherSpawn> pool = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SectionTheme.RusherSpawn r : Sections.hardTable()) if (seen.add(r.name())) pool.add(r); // 名前で重複を除く
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        List<SectionTheme.RusherSpawn> picks = pool.subList(0, Math.min(BATCH_SIZE, pool.size()));
        List<SectionTheme.RusherSpawn> chosen = new ArrayList<>(picks);
        playCue(level);
        List<String> names = new ArrayList<>();
        for (SectionTheme.RusherSpawn r : chosen) names.add(r.name());
        RoomsDebug.log("{} batch {}/{} picks={}", ALIAS, n, BATCHES, names);
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            RoomsState rs = RoomsState.get(level);
            if (rs.roomPaths.isEmpty()) { RoomsDebug.log("{} batch {} skipped: no rooms", ALIAS, n); return; }
            Player front = frontPlayer(level, rs);
            int ok = 0;
            List<String> failed = new ArrayList<>();
            Placeholder2147483648Entity.bypassBlock(true);
            try {
                for (SectionTheme.RusherSpawn r : chosen) {
                    try {
                        if (com.example.roomsmod.world.AbyssSpace.asCommand(() -> r.spawn().spawn(level, rs, front))) ok++; // コマンド専用の枠：A の制限なし else failed.add(r.name());
                    } catch (RuntimeException e) { failed.add(r.name()); RoomsDebug.error(ALIAS + " -> " + r.name() + " spawn failed", e); }
                }
            } finally { Placeholder2147483648Entity.bypassBlock(false); }
            RoomsDebug.log("{} batch {}/{} spawned {}/{} failed={}", ALIAS, n, BATCHES, ok, chosen.size(), failed);
        }));
    }

    /** 専用の音：低いシュルカーボックス＋高い樽＋低いアメジスト＋高いエンダーチェスト。各プレイヤーの位置で鳴らす。 */
    private void playCue(ServerLevel level) {
        Object[][] cue = {
                {SoundEvents.SHULKER_BOX_OPEN, 0.5f}, {SoundEvents.BARREL_OPEN, 1.8f},
                {SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f}, {SoundEvents.ENDER_CHEST_OPEN, 1.6f}};
        for (ServerPlayer p : level.players())
            for (Object[] c : cue) level.playSound(null, p.getX(), p.getY(), p.getZ(), (SoundEvent) c[0], SoundSource.HOSTILE, 1.0f, (float) c[1]);
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

    @Override protected String debugState() { return "alias=" + ALIAS + " ticks=" + ticks + " batches=" + batches + "/" + BATCHES; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("XTicks", ticks); t.putInt("Batches", batches); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        ticks = t.getInt("XTicks"); batches = t.getInt("Batches");
        RoomsDebug.log("{} loaded {}", ALIAS, debugState());
    }
}
