package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.RusherHits;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.HardMode;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A-25：上から一緒に落ちてきて、少しずつ近づく。
 * <ul>
 *   <li>出現はプレイヤーの約 {@value #START_ROOMS_ABOVE} 部屋上（見えない高さ）。プレイヤーの落ちる速さに関係なく、毎 tick
 *       {@value #CLOSE} ブロック（ハード {@value #CLOSE_HARD}）ずつ距離を詰める（スニークしても不利にならない）。触れるまで約 20 秒（ハード約 17 秒）。</li>
 *   <li>横穴のロッカーに隠れるとやり過ごせる：隠れている間は追うのをやめて下へ通り過ぎ、数部屋下で消える。</li>
 *   <li>出現条件：プレイヤーが隠れていない・{@value AbyssSpace#MAX_ALCOVE_GAP} 部屋以内に（A-90 のいない）横穴がある。
 *       横穴は 2〜3 部屋ごと（約 5〜7 秒、スニーク中でも約 14 秒）なので、触れられる前に必ずたどり着ける。</li>
 * </ul>
 */
public class A25Entity extends AbyssEnemy {
    public static final double START_ROOMS_ABOVE = 1.75, CLOSE = 0.05, CLOSE_HARD = 0.06, PASS_SPEED = 0.35, SIDE_SPEED = 0.06;
    public static final double HIT_RANGE = 1.4;
    public static final int GRACE = 40;
    private boolean passing = false;
    private double passUntilY = 0;

    public A25Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-25"; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        return null; // prepareFor を使う（VSpawns から）
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        if (AbyssSpace.alcoveWithin(level, rs, room + 1, AbyssSpace.MAX_ALCOVE_GAP, A90Entity.claimedAlcoves(level)) < 0) {
            RoomsDebug.log("A-25 prepare skipped: no free alcove within {} rooms of {}", AbyssSpace.MAX_ALCOVE_GAP, room);
            return null;
        }
        targetId = t.getUUID();
        AbyssRooms.Shaft s = rs.abyss;
        Vec3 p = pv(rs, t);
        setVirtualPos(new Vec3(cx(s), p.y + START_ROOMS_ABOVE * AbyssRooms.L, cz(s)));
        RoomsDebug.log("A-25 prepare target={} room={} start dy={}", t.getGameProfile().getName(), room, f(START_ROOMS_ABOVE * AbyssRooms.L));
        return blockPosition();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        if (life == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0f, 0.5f);
            if (t != null) RoomsDebug.announce(java.util.List.of(t), "A-25 が来るわ（上）", "A-25 telegraph target={}", t.getGameProfile().getName());
        }
        if (t == null) { // 縦穴から出た・いなくなった：上へ消える
            setVirtualPos(v.add(0, 0.4, 0));
            if (life > 60) leave("target gone");
            return;
        }
        if (life % 30 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
        Vec3 p = pv(rs, t).add(0, 0.9, 0);
        if (!passing && HidingRules.isHiding(t)) {
            passing = true;
            passUntilY = p.y - 3 * AbyssRooms.L;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 2.0f, 0.5f);
            RoomsDebug.log("A-25 target hid, passing by");
        }
        if (passing) { // 隠れたらまっすぐ下へ通り過ぎて、数部屋下で消える
            setVirtualPos(new Vec3(cx(rs.abyss), v.y - PASS_SPEED * (hardSpawn ? HardMode.SPEED_MUL : 1.0), cz(rs.abyss)));
            if (virtualPos().y < passUntilY) leave("passed");
            return;
        }
        if (life < GRACE) return;
        // 毎 tick 一定量だけ距離を詰める（プレイヤーの落ちる速さに合わせて一緒に落ちる）
        double close = hardSpawn ? CLOSE_HARD : CLOSE;
        Vec3 d = p.subtract(v);
        double dist = d.length();
        Vec3 next;
        if (dist <= close) next = p; // 追いついた
        else {
            // 縦：残りの差を close だけ縮める（プレイヤーが落ちたぶんはそのまま付いていく）
            double ny = p.y - Math.signum(p.y - v.y) * Math.max(0, Math.abs(p.y - v.y) - close);
            double nx = v.x + Math.max(-SIDE_SPEED, Math.min(SIDE_SPEED, p.x - v.x));
            double nz = v.z + Math.max(-SIDE_SPEED, Math.min(SIDE_SPEED, p.z - v.z));
            next = new Vec3(nx, ny, nz);
        }
        setVirtualPos(clampInner(rs.abyss, next, 0.3));
        faceTowards(position().add(p.subtract(virtualPos())));
        if (virtualDistanceTo(t) <= HIT_RANGE) {
            boolean hit = RusherHits.tryHit(this, vName(), t, canSeeVirtual(t), hardDamage(14f, 20f));
            if (hit) leave("hit");
        }
    }

    @Override
    protected void clientTick() {
        for (int i = 0; i < 2; i++)
            level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - 0.5) * 1.0, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 1.0, 0, 0.02, 0);
    }

    @Override protected String debugState() { return "passing=" + passing + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Passing", passing); t.putDouble("PassUntil", passUntilY); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); passing = t.getBoolean("Passing"); passUntilY = t.getDouble("PassUntil"); }
}
