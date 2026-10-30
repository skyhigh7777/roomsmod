package com.example.roomsmod.entity.v;

import com.example.roomsmod.entity.RusherChunks;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * V（Village）区画のエンティティ共通部分：物理なし・重力なし・自然消滅なし、チャンクチケット、
 * デバッグ時のみ発光、寿命（MAX_LIFE_TICKS 経過で必ず消える）、視線判定、保存。
 */
public abstract class VEntity extends Mob {
    /** どの V エンティティも最終的にこの tick 数で消える（取り残し防止）。 */
    public static final int MAX_LIFE_TICKS = 20 * 120;

    protected int life = 0;
    protected int startRoom = 0;
    /** 出現時にハードモードだったか（猶予・速度・折り返しの強化。出現後に切り替えても変わらない）。 */
    protected boolean hardSpawn = false;
    /** 出現した部屋が、このエンティティ本来のセクションの外か（ハードの合同抽選で別セクションに出た）。 */
    protected boolean offSection = false;
    private ChunkPos lastForcedChunk = null;
    /** 最後に tick した時刻（ゲーム時間）。止まっている個体の検出用。保存しない。 */
    private long lastTickTime = Long.MIN_VALUE;

    /** この tick 数以上 tick していない個体は「生存中」として扱わない（排他・出現ブロックが古い個体で詰まらないように）。 */
    public static final int STALE_ALIVE_TICKS = 100;
    /** 見張り：この tick 数止まっていたらチャンクを読み込み直す。 */
    public static final int STALL_RELOAD_TICKS = 40;
    /** 見張り：読み込み直しても、この tick 数止まったままなら消す（取り残し防止）。 */
    public static final int STALL_DISCARD_TICKS = 20 * 30;
    private static final int WATCHDOG_INTERVAL = 20;

    protected VEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    /** 寿命の上限（tick）。GK は 120 秒未満に抑えるため上書きする。 */
    protected int maxLifeTicks() { return MAX_LIFE_TICKS; }

    /** 表示名（"V-10" など）。ログ用。 */
    public abstract String vName();

    public void setStartRoom(int index) { this.startRoom = index; }

    /** 出現処理（VSpawns.spawn）から prepare の前に呼ぶ。 */
    public void setSpawnContext(boolean hard, boolean offSection) { this.hardSpawn = hard; this.offSection = offSection; }

    /** このエンティティ本来のセクション id（null = 区別しない）。offSection の判定に使う。 */
    public String homeSection() { return null; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override public boolean isAlwaysTicking() { return true; }
    @Override public void travel(Vec3 input) { this.setDeltaMovement(Vec3.ZERO); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canRide(Entity e) { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float a) { return false; }
    /** /kill コマンドで消せるように（通常のダメージは無効のまま）。 */
    @Override public void kill() { discard(); }

    @Override
    public final void tick() {
        super.tick();
        this.noPhysics = true;
        if (level().isClientSide()) { clientTick(); return; }
        ServerLevel sl = (ServerLevel) level();
        lastTickTime = sl.getGameTime();
        ChunkPos c = new ChunkPos(blockPosition());
        RusherChunks.move(sl, this, lastForcedChunk, c);
        lastForcedChunk = c;
        boolean dbg = RoomsDebug.isEnabled();
        boolean glow = dbg || wantsGlow();
        if (hasGlowingTag() != glow) setGlowingTag(glow); // 輪郭はデバッグ時のみ（wantsGlow で個別に光らせる）
        if (++life > maxLifeTicks()) { leave("max lifetime " + maxLifeTicks() + " ticks"); return; }
        serverTick(sl);
        // 1 tick で遠くへ動いた（ワープ・高速移動）なら、その場でチケットを移して読み込む。
        // 次の tick の先頭まで待つと、チケットのないチャンクに入って tick されなくなり、二度と追従できない
        if (!isRemoved()) preload(getX(), getZ());
        setDeltaMovement(Vec3.ZERO);
        if (dbg && life % 100 == 0)
            RoomsDebug.log("{} status life={} {} pos=({}, {}, {})", vName(), life, debugState(),
                    String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()));
    }

    /** 出現前に位置と内部状態を決める。返り値は出現位置（null なら出現しない）。playerRoom はプレイヤーのいる部屋 index。 */
    protected abstract BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom);

    /** true なら prepare には「いま開けた部屋」（扉の先＝最新の部屋。V-100 と同じ）を渡す。プレイヤーがすでに最新の部屋ならそこ。 */
    public boolean targetsOpenedRoom() { return false; }

    /** デバッグ以外でも輪郭を光らせたいとき true（毎 tick 評価）。 */
    protected boolean wantsGlow() { return false; }

    protected void clientTick() {}
    protected abstract void serverTick(ServerLevel level);
    protected String debugState() { return ""; }

    /**
     * (x, z) のチャンクへチケットを移して同期的に読み込む（移動の前に呼ぶ）。
     * チケットのないチャンクへ飛ぶと、そこが entity-ticking でない限りこの個体は tick されなくなる。
     */
    protected void preload(double x, double z) {
        if (!(level() instanceof ServerLevel sl)) return;
        ChunkPos c = new ChunkPos(BlockPos.containing(x, 0, z));
        if (c.equals(lastForcedChunk)) return;
        RusherChunks.move(sl, this, lastForcedChunk, c);
        lastForcedChunk = c;
        sl.getChunk(c.x, c.z);
    }

    /** 生存中で、最近 tick している（止まった古い個体ではない）。 */
    public boolean isActive() {
        if (!isAlive()) return false;
        if (lastTickTime == Long.MIN_VALUE) return true; // 追加・ロード直後
        // プレイヤーのいないディメンションはしばらくするとエンティティを tick しなくなるので、そのときは止まっていても生存扱い
        if (level() instanceof ServerLevel sl && sl.players().isEmpty()) return true;
        return level().getGameTime() - lastTickTime <= STALE_ALIVE_TICKS;
    }

    /** 生存確認用：VEntity なら isActive、それ以外は isAlive。 */
    public static boolean isActive(Entity e) { return e instanceof VEntity v ? v.isActive() : e.isAlive(); }

    /**
     * サーバーのレベル tick から呼ぶ見張り。tick されなくなった V 系（チケットのないチャンクに入った等）を見つけたら
     * チャンクを読み込み直し、それでも STALL_DISCARD_TICKS 止まったままなら消す。
     */
    public static void watchdog(ServerLevel level) {
        long now = level.getGameTime();
        if (now % WATCHDOG_INTERVAL != 0) return;
        if (level.players().isEmpty()) { // 誰もいない間は止まるのが正常：戻ってきたときに即消さないよう数え直す
            for (Entity e : level.getAllEntities()) if (e instanceof VEntity v) v.lastTickTime = Long.MIN_VALUE;
            return;
        }
        List<VEntity> stalled = new ArrayList<>();
        for (Entity e : level.getAllEntities())
            if (e instanceof VEntity v && v.isAlive()) {
                if (v.lastTickTime == Long.MIN_VALUE) v.lastTickTime = now; // ロード直後：ここから数える
                else if (now - v.lastTickTime >= STALL_RELOAD_TICKS) stalled.add(v);
            }
        for (VEntity v : stalled) {
            long t = now - v.lastTickTime;
            if (t >= STALL_DISCARD_TICKS) {
                RoomsDebug.warn("{} stalled {} ticks at ({}, {}, {}), discarding", v.vName(), t, f(v.getX()), f(v.getY()), f(v.getZ()));
                v.discard();
                continue;
            }
            ChunkPos c = new ChunkPos(v.blockPosition());
            RoomsDebug.log("{} stalled {} ticks at chunk {}, reloading", v.vName(), t, c);
            RusherChunks.move(level, v, c.equals(v.lastForcedChunk) ? null : v.lastForcedChunk, c);
            RusherChunks.force(level, v, c, true);
            v.lastForcedChunk = c;
            level.getChunk(c.x, c.z);
        }
    }

    /** 正常な退場。 */
    protected void leave(String why) {
        RoomsDebug.log("{} despawn: {} life={} pos=({}, {}, {})", vName(), why, life,
                String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()));
        discard();
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level() instanceof ServerLevel sl) lastTickTime = sl.getGameTime();
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel sl) {
            RemovalReason r = getRemovalReason();
            RoomsDebug.log("{} removed from level reason={}", vName(), r);
            if (r == null || r.shouldDestroy() || r == RemovalReason.CHANGED_DIMENSION) {
                if (lastForcedChunk != null) RusherChunks.force(sl, this, lastForcedChunk, false);
                lastForcedChunk = null;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VLife", life);
        tag.putInt("StartRoom", startRoom);
        tag.putBoolean("HardSpawn", hardSpawn);
        tag.putBoolean("OffSection", offSection);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getInt("VLife");
        startRoom = tag.getInt("StartRoom");
        hardSpawn = tag.getBoolean("HardSpawn");
        offSection = tag.getBoolean("OffSection");
    }

    // ---- helpers ----

    /** 目から目への視線（ブロックの当たり判定で遮られなければ true）。 */
    public boolean canSee(LivingEntity target) {
        Vec3 a = getEyePosition(), b = target.getEyePosition();
        BlockHitResult hit = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || a.distanceTo(hit.getLocation()) >= a.distanceTo(b) - 0.5;
    }

    /** 部屋 from..to（含む）の経路を連結した経由点。 */
    public static List<BlockPos> pathOf(RoomsState rs, int from, int to) {
        List<BlockPos> w = new ArrayList<>();
        for (int i = Math.max(0, from); i <= Math.min(to, rs.roomPaths.size() - 1); i++)
            for (BlockPos p : rs.roomPaths.get(i))
                if (w.isEmpty() || !w.get(w.size() - 1).equals(p)) w.add(p);
        return w;
    }

    /** 半径内の生存プレイヤー（観戦者除く）。 */
    protected List<Player> playersNear(double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r)))
            if (!p.isSpectator() && p.isAlive() && distanceTo(p) <= r) out.add(p);
        return out;
    }

    /** 移動方向へ向きを合わせる（見た目用）。 */
    protected void faceTowards(Vec3 to) {
        Vec3 d = to.subtract(position());
        if (d.horizontalDistanceSqr() < 1.0E-4) return;
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        setYRot(yaw); setYHeadRot(yaw); yBodyRot = yaw;
    }

    protected static String f(double v) { return String.format("%.1f", v); }
}
