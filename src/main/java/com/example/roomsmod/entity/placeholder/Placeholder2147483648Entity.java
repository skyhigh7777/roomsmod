package com.example.roomsmod.entity.placeholder;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PLACEHOLDER-2147483648。
 * <ol>
 *   <li>出現：対象プレイヤーからちょうど SPAWN_DIST ブロック、ランダムな水平方向。出現のたびに GLOW_TICKS の間輪郭が光る。</li>
 *   <li>W tick 待つ → グリッチ音を鳴らしてプレイヤーへ一直線に移動（移動中は金切り音、最終サイクルは叫び声）。</li>
 *   <li>待ち・移動のどちらでも、毎 tick プレイヤーの移動量をそのまま足す（プレイヤーから見ると、止まっても走っても一直線に近づいてくる）。</li>
 *   <li>1 ブロック以内で隠れ判定：ロッカーに隠れていれば助かる（机は不可）。隠れていなければ即死。判定の直後、また SPAWN_DIST 離れた所から次のサイクル。</li>
 *   <li>サイクル 1〜5：W=300, 1 ブロック/tick。6〜8：W=210, x1.2。9〜12：W=120, x1.44。13：W=0, x1.44、隠れても無駄（必ず即死）。13 が終わったら消える。</li>
 *   <li>出現時の最深部から ROOMS_TO_ESCAPE 部屋進むと、フェードアウトするグリッチ音と共に消える。</li>
 *   <li>生存中は他の敵の出現をすべて止める（通常・ハードの抽選、3!3!2 の召喚、/rooms spawn。{@link #blocksSpawns}）。</li>
 * </ol>
 * デバッグ時：進んだ部屋数・サイクル番号をログに出し、ボスバーのメーターでプレイヤーとの距離を示す。
 * 自然出現しない（/rooms spawn placeholder2147483648 と 3!3!2 のみ）。状態は NBT に保存。
 */
public class Placeholder2147483648Entity extends PlaceholderEntity {
    public static final String NAME = "PLACEHOLDER-2147483648";
    public static final double SPAWN_DIST = 100.0, HIT_DIST = 1.0, BASE_SPEED = 1.0; // 1 ブロック/tick = 20 ブロック/秒
    public static final int FINAL_CYCLE = 13, ROOMS_TO_ESCAPE = 30, GLOW_TICKS = 40;
    public static final int SCREECH_INTERVAL = 6, FADE_STEPS = 6, FADE_INTERVAL = 4;
    public static final float KILL_DAMAGE = 1000f;
    /** 3!3!2 の召喚候補としての重み（通常の候補 = 1.0）。1/20。 */
    public static final float SUMMON_WEIGHT = 1f / 20f;
    /** ハードモード抽選での確率の倍率（通常 = 1.0）。1/10。今はどの抽選表にも入っていないので効かない（入れたときのため）。 */
    public static final float HARD_CHANCE_MUL = 1f / 10f;

    /** 0=待ち, 1=移動, 2=フェードアウト（消える直前） */
    private int phase = 0, phaseTicks = 0, cycle = 1;
    /** 出現時の最深部の部屋 index、そこから進んだ最大部屋数。 */
    private int spawnDeepest = -1, advanced = 0;
    /** 次の tick で（対象プレイヤーから SPAWN_DIST の所へ）置き直すか。対象がいない間は保留。 */
    private boolean needsPlace = false;
    private int glowTicks = 0;
    private UUID targetId = null;
    private Vec3 lastPlayerPos = null;
    private ServerBossEvent bar = null;

    public Placeholder2147483648Entity(EntityType<? extends Mob> type, Level level) { super(type, level, NAME); }

    /** 13 サイクルは約 3 分。対象不在で止まる分も見込んで余裕を持たせる。 */
    @Override protected int maxLifeTicks() { return 20 * 600; }

    private static int bypass = 0;
    /** EXTRABOX の召喚の間だけ出現ブロックを無視する（サーバースレッドのみ、入れ子可）。 */
    public static void bypassBlock(boolean on) { bypass = Math.max(0, bypass + (on ? 1 : -1)); }

    /** 生存中は他の敵の出現をすべて止める。 */
    public static boolean blocksSpawns(ServerLevel level) {
        if (bypass > 0) return false; // EXTRABOX の召喚中は無視
        for (Entity e : level.getAllEntities()) if (e instanceof Placeholder2147483648Entity p && p.isActive()) return true;
        return false;
    }

    public static int waitTicks(int cycle) { return cycle <= 5 ? 300 : cycle <= 8 ? 210 : cycle <= 12 ? 120 : 0; }
    public static double speed(int cycle) { return BASE_SPEED * (cycle <= 5 ? 1.0 : cycle <= 8 ? 1.2 : 1.44); }

    private static int deepest(RoomsState rs) { return Math.max(1, rs.roomPaths.size() - 1); }

    @Override protected boolean wantsGlow() { return glowTicks > 0; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        ServerPlayer p = pickTarget(level, rs, playerRoom);
        if (p == null) { RoomsDebug.log("{} spawn skipped: no player", name); return null; }
        targetId = p.getUUID();
        spawnDeepest = deepest(rs);
        startRoom = playerRoom;
        cycle = 1; advanced = 0;
        placeAround(p);
        enter(0);
        RoomsDebug.log("{} spawn target={} deepest={} ({}) cycle 1/{} wait={} speed={}", name, p.getGameProfile().getName(),
                spawnDeepest, RoomTracker.format(rs, spawnDeepest), FINAL_CYCLE, waitTicks(1), speed(1));
        return BlockPos.containing(position());
    }

    /** playerRoom にいるプレイヤー。いなければ一番奥にいるプレイヤー。 */
    private static ServerPlayer pickTarget(ServerLevel level, RoomsState rs, int playerRoom) {
        ServerPlayer best = null; int bi = -2;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            int i = RoomTracker.roomIndexAt(rs, p.blockPosition());
            if (i == playerRoom) return p;
            if (i > bi) { bi = i; best = p; }
        }
        return best;
    }

    /** プレイヤーからちょうど SPAWN_DIST、ランダムな水平方向に置く。輪郭を光らせる。 */
    private void placeAround(ServerPlayer p) {
        double a = random.nextDouble() * Math.PI * 2;
        double x = p.getX() + Math.cos(a) * SPAWN_DIST, z = p.getZ() + Math.sin(a) * SPAWN_DIST;
        if (life > 0) preload(x, z); // 出現前（prepare）は VSpawns が読み込む
        moveTo(x, p.getY(), z, getYRot(), getXRot());
        faceTowards(p.position());
        lastPlayerPos = p.position();
        glowTicks = GLOW_TICKS;
        needsPlace = false;
    }

    private void enter(int ph) { phase = ph; phaseTicks = 0; }

    private ServerPlayer target(ServerLevel level) {
        if (targetId == null) return null;
        Entity e = level.getEntity(targetId);
        return e instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() ? p : null;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (glowTicks > 0) glowTicks--;
        if (phase == 2) { fadeTick(level); return; }

        ServerPlayer p = target(level);
        if (p == null) {
            // 対象がいない（死亡・別ディメンション・ログアウト）：生きている他のプレイヤーへ。誰もいなければ保留
            ServerPlayer other = pickTarget(level, rs, -1);
            if (other == null) { lastPlayerPos = null; needsPlace = true; updateBar(level, null); return; }
            RoomsDebug.log("{} retarget {} -> {}", name, targetId, other.getGameProfile().getName());
            targetId = other.getUUID();
            p = other;
            needsPlace = true;
        }
        if (needsPlace) { placeAround(p); enter(0); RoomsDebug.log("{} resume cycle {}/{} (re-placed)", name, cycle, FINAL_CYCLE); }

        if (trackRooms(rs, p)) return;

        // プレイヤーの移動量を足す（待ち・移動とも）
        Vec3 pp = p.position();
        if (lastPlayerPos != null) {
            Vec3 d = pp.subtract(lastPlayerPos);
            preload(getX() + d.x, getZ() + d.z); // プレイヤーのワープ等で大きく動くこともある
            setPos(getX() + d.x, getY() + d.y, getZ() + d.z);
        }
        lastPlayerPos = pp;

        phaseTicks++;
        if (phase == 0) {
            if (phaseTicks >= waitTicks(cycle)) {
                glitch(level, 1.0f, 1.0f);
                RoomsDebug.log("{} cycle {}/{} move speed={} dist={}", name, cycle, FINAL_CYCLE, speed(cycle), f(distanceToTarget(p)));
                enter(1);
            }
        }
        if (phase == 1) {
            if ((phaseTicks - 1) % SCREECH_INTERVAL == 0) approachSound(level);
            Vec3 to = p.position().subtract(position());
            double dist = to.length(), step = speed(cycle);
            if (dist <= step) setPos(p.getX(), p.getY(), p.getZ());
            else { Vec3 s = to.scale(step / dist); setPos(getX() + s.x, getY() + s.y, getZ() + s.z); }
            faceTowards(p.position());
            if (distanceToTarget(p) <= HIT_DIST) {
                check(level, p);
                if (cycle >= FINAL_CYCLE) { startFade(level, "finished cycle " + FINAL_CYCLE); return; }
                cycle++;
                RoomsDebug.log("{} cycle {}/{} start wait={} speed={} advanced={}/{}", name, cycle, FINAL_CYCLE, waitTicks(cycle), speed(cycle), advanced, ROOMS_TO_ESCAPE);
                ServerPlayer again = target(level);
                if (again != null) { placeAround(again); enter(0); } else needsPlace = true;
            }
        }
        updateBar(level, target(level));
    }

    private double distanceToTarget(ServerPlayer p) { return position().distanceTo(p.position()); }

    /** 出現時の最深部から何部屋進んだか。ROOMS_TO_ESCAPE に達したら消え始めて true。 */
    private boolean trackRooms(RoomsState rs, ServerPlayer p) {
        int room = RoomTracker.roomIndexAt(rs, p.blockPosition());
        if (room < 0 || spawnDeepest < 0) return false;
        int adv = room - spawnDeepest;
        if (adv > advanced) {
            advanced = adv;
            RoomsDebug.log("{} rooms advanced {}/{} (room {} = {}) cycle {}/{}", name, advanced, ROOMS_TO_ESCAPE, room, RoomTracker.format(rs, room), cycle, FINAL_CYCLE);
            if (advanced >= ROOMS_TO_ESCAPE) { startFade((ServerLevel) level(), "player advanced " + advanced + " rooms"); return true; }
        }
        return false;
    }

    /** 隠れ判定：ロッカーだけ有効（机は不可）。最終サイクルは隠れていても即死。 */
    private void check(ServerLevel level, ServerPlayer p) {
        boolean locker = LockerManager.isInLocker(p);
        String who = p.getGameProfile().getName();
        if (locker && cycle < FINAL_CYCLE) {
            RoomsDebug.log(p, name + ": ロッカーで助かったわ（" + cycle + "/" + FINAL_CYCLE + "）", "{} check cycle {}/{} player={} spared (locker)", name, cycle, FINAL_CYCLE, who);
            return;
        }
        RoomsDamage.hurt(this, p, KILL_DAMAGE);
        RoomsDebug.log(p, name + ": 即死（" + cycle + "/" + FINAL_CYCLE + "）", "{} check cycle {}/{} player={} killed (spot={})", name, cycle, FINAL_CYCLE, who, LockerManager.getSpot(p));
    }

    // ---- 音 ----

    /** グリッチ音：低いテレポート音 + ランダムなピッチのビット音。各プレイヤーの位置で鳴らす（どこにいても聞こえる）。 */
    private void glitch(ServerLevel level, float volume, float pitchMul) {
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, volume, 0.5f * pitchMul);
            for (int i = 0; i < 3; i++)
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, volume * 0.8f, (0.5f + random.nextFloat() * 1.5f) * pitchMul);
        }
    }

    /** 移動中の音（エンティティの位置で、遠くまで届く音量）。1〜12 は金切り音、最終サイクルは叫び声。 */
    private void approachSound(ServerLevel level) {
        SoundEvent s; float pitch;
        if (cycle >= FINAL_CYCLE) { s = SoundEvents.ENDERMAN_SCREAM; pitch = 0.7f + random.nextFloat() * 0.1f; }
        else { s = SoundEvents.PHANTOM_AMBIENT; pitch = 1.6f + random.nextFloat() * 0.2f; }
        level.playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, 7.0f, pitch);
    }

    private void startFade(ServerLevel level, String why) {
        RoomsDebug.log("{} fading out: {} cycle={}/{} advanced={}/{}", name, why, cycle, FINAL_CYCLE, advanced, ROOMS_TO_ESCAPE);
        setInvisible(true);
        glowTicks = 0;
        clearBar();
        enter(2);
        fadeTick(level);
    }

    /** だんだん小さく・低くなるグリッチ音を鳴らしてから消える。 */
    private void fadeTick(ServerLevel level) {
        if (phaseTicks % FADE_INTERVAL == 0) {
            int step = phaseTicks / FADE_INTERVAL;
            if (step >= FADE_STEPS) { leave("faded out"); return; }
            float k = 1f - step / (float) FADE_STEPS;
            glitch(level, k, 0.6f + 0.4f * k);
        }
        phaseTicks++;
    }

    // ---- デバッグ用ボスバー（メーター＝プレイヤーとの距離） ----

    private void updateBar(ServerLevel level, ServerPlayer p) {
        if (!RoomsDebug.isEnabled()) { clearBar(); return; }
        if (bar == null) bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        double d = p == null ? SPAWN_DIST : distanceToTarget(p);
        bar.setProgress((float) Mth.clamp(d / SPAWN_DIST, 0.0, 1.0));
        bar.setName(Component.literal(NAME + " #" + cycle + "/" + FINAL_CYCLE + " " + (phase == 0 ? "wait" : "move") + " dist=" + f(d) + " rooms=" + advanced + "/" + ROOMS_TO_ESCAPE));
        List<ServerPlayer> here = level.players();
        for (ServerPlayer q : new ArrayList<>(bar.getPlayers())) if (!here.contains(q) || q.isRemoved()) bar.removePlayer(q);
        for (ServerPlayer q : here) if (!bar.getPlayers().contains(q)) bar.addPlayer(q);
    }

    private void clearBar() {
        if (bar != null) { bar.removeAllPlayers(); bar.setVisible(false); bar = null; }
    }

    @Override
    public void onRemovedFromLevel() {
        clearBar();
        super.onRemovedFromLevel();
    }

    @Override protected String debugState() {
        return "phase=" + phase + " phaseTicks=" + phaseTicks + " cycle=" + cycle + "/" + FINAL_CYCLE + " advanced=" + advanced + "/" + ROOMS_TO_ESCAPE + " spawnDeepest=" + spawnDeepest;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase); t.putInt("PhaseTicks", phaseTicks); t.putInt("Cycle", cycle);
        t.putInt("SpawnDeepest", spawnDeepest); t.putInt("Advanced", advanced);
        t.putBoolean("NeedsPlace", needsPlace); t.putInt("GlowTicks", glowTicks);
        if (targetId != null) t.putUUID("Target", targetId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase"); phaseTicks = t.getInt("PhaseTicks"); cycle = Math.max(1, t.getInt("Cycle"));
        spawnDeepest = t.contains("SpawnDeepest") ? t.getInt("SpawnDeepest") : -1; advanced = t.getInt("Advanced");
        needsPlace = t.getBoolean("NeedsPlace"); glowTicks = t.getInt("GlowTicks");
        targetId = t.hasUUID("Target") ? t.getUUID("Target") : null;
        lastPlayerPos = null; // ロード直後は差分なし（次の tick から追従）
        setInvisible(phase == 2);
        RoomsDebug.log("{} loaded {}", name, debugState());
    }
}
