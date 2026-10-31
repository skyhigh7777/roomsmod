package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A-180：プレイヤーの数部屋下を一緒に落ちながら、しばらく残る毒の雲（円盤）を縦穴に置いていく。
 * <ul>
 *   <li>プレイヤーの約 {@value #DEPTH_ROOMS} 部屋下を保ち、{@value #INTERVAL} tick（ハード {@value #INTERVAL_HARD}）ごとに雲を 1 つ（全部で
 *       {@value #CLOUDS} 個、ハード {@value #CLOUDS_HARD} 個）。雲は {@value #CLOUD_TICKS} tick（ハード {@value #CLOUD_TICKS_HARD}）残る。</li>
 *   <li>雲は縦穴の内側の片寄った位置（四隅のどれかの側）に半径 {@value #RADIUS}（ハード {@value #RADIUS_HARD}）。反対側には必ず通れる隙間が残る。
 *       雲どうしは縦に {@value #MIN_GAP} ブロック以上あける（横へ移る余裕）。</li>
 *   <li>避け方：横へ動いて隙間を通る、またはスニークで落ちる速さを落として雲が消えるのを待つ。触れると少しのダメージとウィザー。</li>
 * </ul>
 */
public class A180Entity extends AbyssEnemy {
    public static final double DEPTH_ROOMS = 2.6, RADIUS = 1.6, RADIUS_HARD = 1.9, THICK = 1.2, MIN_GAP = 8.0, MOVE = 0.35;
    public static final int INTERVAL = 50, INTERVAL_HARD = 40, CLOUDS = 6, CLOUDS_HARD = 8, CLOUD_TICKS = 160, CLOUD_TICKS_HARD = 200, GRACE = 40;
    private static final ColorParticleOption CLOUD_PARTICLE = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF6A2C8C);

    private static final class Cloud { double x, y, z, r; int left; Cloud(double x, double y, double z, double r, int left) { this.x = x; this.y = y; this.z = z; this.r = r; this.left = left; } }
    private final List<Cloud> clouds = new ArrayList<>();
    private int dropped = 0, sinceDrop = 0;
    private final Map<UUID, Long> lastHurt = new HashMap<>();

    public A180Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-180"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        if (conflictAlive(level)) { RoomsDebug.log("A-180 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null) return null;
        if (anyAlive(level, A250Entity.class)) { RoomsDebug.log("A-180 prepare skipped: A-250 alive"); return null; } // 雲の隙間（壁際）へ逃げられない
        targetId = t.getUUID();
        Vec3 p = pv(rs, t);
        setVirtualPos(new Vec3(cx(rs.abyss), p.y - (DEPTH_ROOMS + 0.6) * AbyssRooms.L, cz(rs.abyss)));
        RoomsDebug.log("A-180 prepare target={} start {} rooms below", t.getGameProfile().getName(), f(DEPTH_ROOMS + 0.6));
        return blockPosition();
    }

    private int maxClouds() { return hardSpawn ? CLOUDS_HARD : CLOUDS; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        AbyssRooms.Shaft s = rs.abyss;
        tickClouds(level, rs);
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        if (life == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
            if (t != null) RoomsDebug.announce(java.util.List.of(t), "A-180 が下にいるわ", "A-180 telegraph clouds={}", maxClouds());
        }
        boolean done = t == null || dropped >= maxClouds();
        if (done) { // 置き終わった：深いところへ沈み、雲が消えたら消える
            setVirtualPos(v.add(0, -0.4, 0));
            if (clouds.isEmpty() && life > GRACE) leave(t == null ? "target gone" : "all clouds dropped");
            return;
        }
        // プレイヤーの DEPTH_ROOMS 部屋下を保つ（横は少しふらつく）
        Vec3 p = pv(rs, t);
        double goalY = p.y - DEPTH_ROOMS * AbyssRooms.L;
        double ny = v.y + Math.max(-MOVE, Math.min(MOVE, goalY - v.y));
        double wob = 1.2;
        Vec3 goal = new Vec3(cx(s) + Math.sin(life * 0.05) * wob, ny, cz(s) + Math.cos(life * 0.037) * wob);
        setVirtualPos(clampInner(s, new Vec3(v.x + Math.max(-0.08, Math.min(0.08, goal.x - v.x)), ny, v.z + Math.max(-0.08, Math.min(0.08, goal.z - v.z))), 0.3));
        if (life < GRACE) return;
        if (++sinceDrop >= (hardSpawn ? INTERVAL_HARD : INTERVAL)) {
            double vy = virtualPos().y;
            boolean tooClose = false;
            for (Cloud c : clouds) if (Math.abs(c.y - vy) < MIN_GAP) { tooClose = true; break; }
            if (!tooClose && drop(level, RoomsState.get(level), s, vy)) sinceDrop = 0;
        }
    }

    /**
     * 雲を置く：中心は内側の四隅のどれかの側へ寄せる（反対側に必ず隙間が残る）。
     * 横穴の高さでは横穴と反対の側に置く（横穴の入口をふさがない）。A-90 の潜む横穴の高さには置かない（避ける側が重ならないように）。
     */
    private boolean drop(ServerLevel level, RoomsState rs, AbyssRooms.Shaft s, double vy) {
        double sx = random.nextBoolean() ? 1 : -1, sz = random.nextBoolean() ? 1 : -1;
        java.util.Set<Integer> peek = A90Entity.claimedAlcoves(level);
        int room = AbyssSpace.roomAtVirtualY(s, vy);
        for (int r = room - 1; r <= room + 1; r++) {
            AbyssRooms.AlcoveSpot a = AbyssRooms.alcoveSpot(level, s, r);
            if (a == null || !(vy + THICK > a.floorY() - 2 && vy < a.floorY() + 4)) continue;
            if (peek.contains(r)) { RoomsDebug.log("A-180 drop postponed: A-90 alcove height (room {})", r); return false; }
            double mx = a.mouth().x - cx(s), mz = a.mouth().z - cz(s);
            if (Math.abs(mx) > Math.abs(mz)) sx = -Math.signum(mx); else sz = -Math.signum(mz);
        }
        double ox = sx * (1.1 + random.nextDouble() * 0.4), oz = sz * (1.1 + random.nextDouble() * 0.4);
        if (random.nextInt(3) == 0) { if (random.nextBoolean()) ox = sx * 1.4; else oz = sz * 1.4; } // ときどき辺の側
        Cloud c = new Cloud(cx(s) + ox, vy, cz(s) + oz, hardSpawn ? RADIUS_HARD : RADIUS, hardSpawn ? CLOUD_TICKS_HARD : CLOUD_TICKS);
        clouds.add(c);
        dropped++;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 2.5f, 0.6f);
        RoomsDebug.log("A-180 cloud {}/{} at offset ({}, {}) r={} vy={}", dropped, maxClouds(), f(ox), f(oz), f(c.r), f(vy));
        return true;
    }

    private void tickClouds(ServerLevel level, RoomsState rs) {
        if (clouds.isEmpty()) return;
        List<Player> inShaft = new ArrayList<>();
        for (Player p : level.players()) if (inShaft(rs, p)) inShaft.add(p);
        boolean[] hidden = new boolean[1];
        long now = level.getGameTime();
        for (var it = clouds.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            if (--c.left <= 0) { it.remove(); continue; }
            // 見た目：実際の位置で見えていればパーティクル（円盤の中のランダムな点）
            Vec3 phys = AbyssSpace.toPhysical(rs, new Vec3(c.x, c.y, c.z), hidden);
            if (!hidden[0]) for (Player p : inShaft) if (p instanceof ServerPlayer sp)
                for (int i = 0; i < 5; i++) {
                    double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * c.r;
                    level.sendParticles(sp, CLOUD_PARTICLE, true, phys.x + Math.cos(a) * rr, phys.y + random.nextDouble() * THICK, phys.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            // 当たり（仮想座標）。隠れていれば当たらない
            for (Player p : inShaft) {
                if (HidingRules.isHiding(p)) continue;
                Vec3 v = AbyssSpace.playerVirtual(rs, p);
                if (Math.hypot(v.x - c.x, v.z - c.z) > c.r + 0.3 || v.y > c.y + THICK || v.y + 1.8 < c.y) continue;
                Long last = lastHurt.get(p.getUUID());
                if (last != null && now - last < 10) continue;
                lastHurt.put(p.getUUID(), now);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, hardSpawn ? 1 : 0));
                RoomsDamage.witherFrom(this, p, 60); // ウィザーで倒れても死因に A-180 の名前
                RoomsDamage.hurt(this, p, hardDamage(3f, 4f));
            }
        }
    }

    @Override
    protected void clientTick() {
        level().addParticle(CLOUD_PARTICLE, getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble() * 1.6, getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
    }

    @Override protected String debugState() { return "dropped=" + dropped + "/" + maxClouds() + " clouds=" + clouds.size() + (hardSpawn ? " hard" : ""); }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Dropped", dropped); t.putInt("SinceDrop", sinceDrop);
        ListTag l = new ListTag();
        for (Cloud c : clouds) { CompoundTag ct = new CompoundTag(); ct.putDouble("x", c.x); ct.putDouble("y", c.y); ct.putDouble("z", c.z); ct.putDouble("r", c.r); ct.putInt("left", c.left); l.add(ct); }
        t.put("Clouds", l);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        dropped = t.getInt("Dropped"); sinceDrop = t.getInt("SinceDrop");
        clouds.clear();
        for (Tag x : t.getList("Clouds", Tag.TAG_COMPOUND)) { CompoundTag ct = (CompoundTag) x; clouds.add(new Cloud(ct.getDouble("x"), ct.getDouble("y"), ct.getDouble("z"), ct.getDouble("r"), ct.getInt("left"))); }
    }
}
