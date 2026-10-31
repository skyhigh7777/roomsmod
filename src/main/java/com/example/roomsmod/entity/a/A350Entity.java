package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A-350：横穴そっくりの口。入ると噛みつく。
 * <ul>
 *   <li>出現：プレイヤーの 3〜6 部屋下の「本物の横穴のない部屋」に、見た目だけの横穴（ロッカー2つ）を足す。本物の横穴は 1 つも減らさない
 *       （A-25・A-130 などの出現条件も本物の横穴だけで数える）ので、これが唯一の隠れ場所になることはない。
 *       A-25 / A-50 / A-130（隠れる必要のある敵）が生きている間は出ない。後から出てきたら口を閉じて消える（中にいれば、出るまでその横穴は残る＝安全）。</li>
 *   <li>見分け方：中の照明（ランタン）がなくて暗い。近く（約 1 部屋以内）では低い息づかいが聞こえる。</li>
 *   <li>中に入る（体の中心が入る、またはそこのロッカーに隠れる）と噛みつく：ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、
 *       縦穴の中心へ吐き出して口を閉じる（壁に戻す）。1 回だけ。通り過ぎたら（{@value #PASS_BELOW} ブロック下）消える。縦穴を通るだけなら何もしない。</li>
 * </ul>
 */
public class A350Entity extends AbyssEnemy {
    public static final float DAMAGE = 7f, DAMAGE_HARD = 9f;
    public static final double PASS_BELOW = 10, ENTER_INFLATE = 0.0;
    public static final int MIN_AHEAD = 3, MAX_AHEAD = 6;
    private int fakeRoom = -1, sideIdx = 0, off = 1;
    private boolean bit = false;

    public A350Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-350"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        if (conflictAlive(level)) { RoomsDebug.log("A-350 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null) return null;
        if (anyAlive(level, A25Entity.class, A50Entity.class, A130Entity.class)) { RoomsDebug.log("A-350 prepare skipped: an enemy that needs hiding is alive"); return null; }
        int room = Math.max(AbyssRooms.roomOf(t, playerRoom), AbyssSpace.lead(rs)); // まだ見えていない部屋（層3より先）
        java.util.List<Integer> cands = new java.util.ArrayList<>();
        for (int r = room + MIN_AHEAD; r <= room + MAX_AHEAD; r++)
            if (AbyssSpace.isAbyssRoom(rs, r) && AbyssRooms.alcoveSpot(level, rs.abyss, r) == null && !AbyssRooms.hasFake(rs, r)) cands.add(r);
        if (cands.isEmpty()) { RoomsDebug.log("A-350 prepare skipped: no room without an alcove {}..{} ahead of {}", MIN_AHEAD, MAX_AHEAD, room); return null; }
        targetId = t.getUUID();
        fakeRoom = cands.get(random.nextInt(cands.size()));
        sideIdx = random.nextInt(4);
        off = 1 + random.nextInt(7);
        setVirtualPos(new Vec3(cx(rs.abyss), AbyssSpace.roomTop(rs.abyss, fakeRoom).getY() - 6, cz(rs.abyss)));
        RoomsDebug.log("A-350 prepare target={} room={} fakeRoom={} side={} off={}", t.getGameProfile().getName(), room, fakeRoom, SIDES[sideIdx], off);
        return blockPosition();
    }

    /** 見た目だけの横穴を登録（読み込み後も毎 tick 確かめる）。登録できなければ false。 */
    private boolean ensureFake(ServerLevel level, RoomsState rs) {
        if (AbyssRooms.hasFake(rs, fakeRoom)) return true;
        return AbyssRooms.setFake(level, rs, fakeRoom, SIDES[sideIdx], off);
    }

    private void close(ServerLevel level, RoomsState rs, String why) {
        if (fakeRoom >= 0 && AbyssRooms.hasFake(rs, fakeRoom)) AbyssRooms.setFake(level, rs, fakeRoom, null, 0);
        leave(why);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null || fakeRoom < 0) { leave("no shaft"); return; }
        if (!ensureFake(level, rs)) { leave("could not place"); return; }
        AbyssRooms.AlcoveSpot spot = AbyssRooms.fakeSpot(rs, fakeRoom);
        // 姿は横穴の奥の壁の中（見えない）
        Vec3 in = spot.back().subtract(spot.mouth()).normalize();
        setVirtualPos(spot.back().add(in.scale(1.3)).add(0, 0.2, 0));
        Player t = target(level, rs);
        if (t == null) { if (life > 100) close(level, rs, "target gone"); return; }
        if (life == 1) RoomsDebug.announce(java.util.List.of(t), "A-350：" + com.example.roomsmod.world.section.Sections.formatRoom(rs, fakeRoom) + " の横穴は偽物よ", "A-350 placed fakeRoom={}", fakeRoom);
        Vec3 p = pv(rs, t);
        double rel = p.y - spot.floorY();
        if (rel < -PASS_BELOW) { close(level, rs, "passed"); return; }
        // 見分け方：近くで低い息づかい
        if (Math.abs(rel) < AbyssRooms.L && life % 40 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_BREATHE, SoundSource.HOSTILE, 0.8f, 0.4f);
        if (bit) return;
        // 隠れる必要のある敵が出てきたら口を閉じて消える（追われている間に偽物へ飛び込ませない。見えていれば横穴が消えるのも合図）
        if (anyAlive(level, A25Entity.class, A50Entity.class, A130Entity.class)) { close(level, rs, "an enemy that needs hiding appeared"); return; }
        AABB box = AbyssRooms.fakeInterior(rs, fakeRoom);
        if (box == null) return;
        for (Player q : level.players()) {
            if (!inShaft(rs, q) || !(q instanceof ServerPlayer sp)) continue;
            BlockPos hide = HidingRules.isHiding(q) ? LockerManager.getSpotPos(q) : null;
            boolean inside = box.inflate(ENTER_INFLATE).contains(q.position().add(0, 0.5, 0)) || (hide != null && box.contains(Vec3.atCenterOf(hide)));
            if (!inside) continue;
            bite(level, rs, sp, hide != null);
            return;
        }
    }

    private void bite(ServerLevel level, RoomsState rs, ServerPlayer sp, boolean hiding) {
        bit = true;
        if (hiding) LockerManager.exit(sp, "A-350");
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 3.0f, 0.6f);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, sp.getX(), sp.getY() + 1, sp.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
        RoomsDamage.hurt(this, sp, hardDamage(DAMAGE, DAMAGE_HARD));
        // 縦穴の中心（狭まっていれば一番近い残るマス）へ吐き出す → 口を閉じる
        AbyssRooms.Shaft s = rs.abyss;
        Vec3 c = AbyssRooms.safeColumnPoint(s, cx(s), sp.getY(), cz(s), sp.getBbWidth() / 2 + 0.05); // A-250 で狭まっていれば残るマスへ
        sp.connection.teleport(c.x, c.y, c.z, sp.getYRot(), sp.getXRot());
        sp.setDeltaMovement(0, -AbyssRooms.MAX_FALL, 0);
        sp.fallDistance = 0;
        RoomsDebug.log("A-350 bit {} (hiding={})", sp.getGameProfile().getName(), hiding);
        close(level, rs, "bit");
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは偽の横穴も消す（チャンクの読み込み解除・終了時は何もしない：登録は保存されず、読み込み後は縦穴を作り直す）
        if (level() instanceof ServerLevel sl && fakeRoom >= 0) {
            RemovalReason r = getRemovalReason();
            RoomsState rs = RoomsState.get(sl);
            if ((r == null || r.shouldDestroy()) && AbyssRooms.hasFake(rs, fakeRoom)) AbyssRooms.setFake(sl, rs, fakeRoom, null, 0);
        }
        super.onRemovedFromLevel();
    }

    @Override protected String debugState() { return "fakeRoom=" + fakeRoom + " side=" + SIDES[sideIdx] + " off=" + off + " bit=" + bit + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("FakeRoom", fakeRoom); t.putInt("Side", sideIdx); t.putInt("Off", off); t.putBoolean("Bit", bit); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        fakeRoom = t.contains("FakeRoom") ? t.getInt("FakeRoom") : -1; sideIdx = Math.floorMod(t.getInt("Side"), 4); off = Math.max(1, t.getInt("Off")); bit = t.getBoolean("Bit");
    }
}
