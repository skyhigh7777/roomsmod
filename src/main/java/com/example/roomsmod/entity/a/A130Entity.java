package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A-130：下から縦穴いっぱいの「波」がせり上がってくる。横穴に入ってやり過ごす。
 * <ul>
 *   <li>出現の条件：プレイヤーの足より {@value #MIN_LEAD_ROOMS} 部屋以上下に、危なくない（A-90 のいない）横穴がある（{@value AbyssSpace#MAX_ALCOVE_GAP}+2 部屋以内）。
 *       その横穴を予約（A-90 は潜まない）。プレイヤーが隠れていない時だけ。</li>
 *   <li>予告：出現と同時に低い音、波はその横穴の床の {@value #GAP_BELOW} ブロック下で止まって待ち、上へ灰のパーティクルを上げ続ける（20 tick ごとに音、近づくほど高く）。
 *       横穴までは 1.5 部屋以上（通常 3.6 秒以上、スニーク中 7 秒以上）あるので、落ちる速さに関係なく間に合う。</li>
 *   <li>せり上がる：プレイヤーがどれかの横穴に入って（体の中心、またはロッカー）{@value #SHELTER_TICKS} tick たつ、または予約の横穴を素通りする（床より下へ落ちる）と、
 *       咆哮のあと毎 tick {@value #RISE}（ハード {@value #RISE_HARD}）ブロックずつ上がる。体を通り過ぎる間に横穴・ロッカーの外にいると
 *       ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 人 1 回。通り過ぎた後（波の下）は安全。プレイヤーの 2 部屋上まで上がったら消える。</li>
 * </ul>
 */
public class A130Entity extends AbyssEnemy {
    public static final double MIN_LEAD_ROOMS = 1.5, GAP_BELOW = 14, RISE = 0.35, RISE_HARD = 0.42, SAFE_INFLATE = 0.15;
    public static final float DAMAGE = 8f, DAMAGE_HARD = 11f;
    public static final int SHELTER_TICKS = 6, WARN_MAX = 20 * 40, ROAR_TICKS = 10;
    private enum State { WARN, ROAR, RISE }
    private State state = State.WARN;
    private int alcoveRoom = -1, stateTicks = 0, sheltered = 0;
    private final Set<UUID> done = new HashSet<>();

    public A130Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-130"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override public int reservedAlcove() { return state == State.WARN ? alcoveRoom : -1; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        Vec3 p = pv(rs, t);
        int r = freeAlcoveBelow(level, rs, room, AbyssSpace.MAX_ALCOVE_GAP + 2, p.y - MIN_LEAD_ROOMS * AbyssRooms.L);
        if (r < 0) { RoomsDebug.log("A-130 prepare skipped: no free alcove {} rooms below {}", MIN_LEAD_ROOMS, room); return null; }
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, r);
        targetId = t.getUUID();
        alcoveRoom = r;
        setVirtualPos(new Vec3(cx(rs.abyss), spot.floorY() - GAP_BELOW, cz(rs.abyss)));
        RoomsDebug.log("A-130 prepare target={} room={} alcoveRoom={} lead={} blocks", t.getGameProfile().getName(), room, r, f(p.y - spot.floorY()));
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null || alcoveRoom < 0) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        stateTicks++;
        if (life == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
            if (t != null) RoomsDebug.announce(java.util.List.of(t), "A-130 が下で待っているわ（" + com.example.roomsmod.world.section.Sections.formatRoom(rs, alcoveRoom) + " の横穴へ）",
                    "A-130 telegraph alcoveRoom={}", alcoveRoom);
        }
        if (t == null) { // 縦穴から出た・いなくなった：沈んで消える
            setVirtualPos(v.add(0, -0.4, 0));
            if (stateTicks > 60) leave("target gone");
            return;
        }
        Vec3 p = pv(rs, t);
        switch (state) {
            case WARN -> {
                AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, alcoveRoom);
                if (spot == null) { leave("alcove missing"); return; }
                ash(level, rs, 3);
                if (life % 20 == 0) {
                    double d = Math.max(0, p.y - v.y);
                    float pitch = (float) Math.max(0.5, Math.min(1.4, 1.4 - d / 40.0));
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.2f, pitch * 0.6f);
                }
                if (sheltered(rs, t, SAFE_INFLATE)) sheltered++; else sheltered = 0;
                String why = sheltered >= SHELTER_TICKS ? "target sheltered" : p.y < spot.floorY() - 0.5 ? "target passed the alcove" : null;
                if (why != null) {
                    setState(State.ROAR);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
                    RoomsDebug.log("A-130 rising: {}", why);
                } else if (life > WARN_MAX) leave("warn timeout");
            }
            case ROAR -> { // 咆哮の間はまだ上がらない（せり上がりの合図）
                ash(level, rs, 8);
                if (stateTicks >= ROAR_TICKS) setState(State.RISE);
            }
            case RISE -> {
                double ny = v.y + (hardSpawn ? RISE_HARD : RISE);
                setVirtualPos(new Vec3(v.x, ny, v.z));
                surge(level, rs);
                if (stateTicks % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 3.0f, 0.5f);
                for (Player q : level.players()) {
                    if (!inShaft(rs, q) || done.contains(q.getUUID())) continue;
                    Vec3 qv = pv(rs, q);
                    if (ny < qv.y) continue;           // まだ足の下
                    if (ny > qv.y + 1.8) { done.add(q.getUUID()); continue; } // 通り過ぎた
                    if (sheltered(rs, q, SAFE_INFLATE)) continue;           // 横穴・ロッカーの中：通り過ぎるまで見逃す
                    done.add(q.getUUID());
                    RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD));
                    RoomsDebug.log("A-130 hit {}", q.getGameProfile().getName());
                }
                if (ny > p.y + 2 * AbyssRooms.L || stateTicks > 20 * 30) leave("passed");
            }
        }
    }

    /** 予告：波の高さから上へ昇る灰（縦穴の中のプレイヤーにだけ）。 */
    private void ash(ServerLevel level, RoomsState rs, int n) {
        if (abyssHidden[0]) return;
        for (Player p : level.players()) if (p instanceof ServerPlayer sp && inShaft(rs, p))
            for (int i = 0; i < n; i++)
                level.sendParticles(sp, ParticleTypes.ASH, true, cx(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, getY() + random.nextDouble() * 3, cz(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, 1, 0, 0.3, 0, 0.05);
    }

    /** 波の面（縦穴の内側いっぱい）。 */
    private void surge(ServerLevel level, RoomsState rs) {
        if (abyssHidden[0]) return;
        for (Player p : level.players()) if (p instanceof ServerPlayer sp && inShaft(rs, p))
            for (int i = 0; i < 14; i++)
                level.sendParticles(sp, ParticleTypes.SCULK_SOUL, true, cx(rs.abyss) + (random.nextDouble() - 0.5) * 4.8, getY() + random.nextDouble() * 0.8, cz(rs.abyss) + (random.nextDouble() - 0.5) * 4.8, 1, 0, 0, 0, 0);
    }

    @Override
    protected void clientTick() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 2, getY() + random.nextDouble(), getZ() + (random.nextDouble() - 0.5) * 2, 0, 0.05, 0);
    }

    @Override protected String debugState() { return "state=" + state + " alcoveRoom=" + alcoveRoom + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("AlcoveRoom", alcoveRoom); t.putInt("StateTicks", stateTicks); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.WARN; }
        alcoveRoom = t.contains("AlcoveRoom") ? t.getInt("AlcoveRoom") : -1; stateTicks = t.getInt("StateTicks");
    }
}
