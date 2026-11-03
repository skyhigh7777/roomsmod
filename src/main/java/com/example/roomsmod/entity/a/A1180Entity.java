package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A-1180「重い空気」：しばらく落下が速くなり、その間に下から重い塊（灰色の円盤）が来る。姿はない。
 * <ul>
 *   <li>予告 {@value #WARN} tick（ハード {@value #WARN_HARD}）：低い音とアクションバー「空気が重くなる…」。</li>
 *   <li>重い間 {@value #DURATION} tick（ハード {@value #DURATION_HARD}）：落下速度の上限がその部屋の速さ + {@value #ADD}（ハード {@value #ADD_HARD}）。
 *       AbyssRooms.boost で送り、クライアントが自分で数えて必ず元に戻す（サーバー側も期限で数えなくなる。終わる・消える・ログアウト・次元移動でも解除）。
 *       スニークは今までどおり半分の速さ。</li>
 *   <li>重い間、{@value #INTERVAL} tick（ハード {@value #INTERVAL_HARD}）ごとに約 {@value #BELOW} ブロック下に塊を 1 つ（全部で {@value #BLOCKS} 個、ハード {@value #BLOCKS_HARD} 個）。
 *       塊は縦穴の内側の四隅のどれかに寄った半径 {@value #RADIUS}（ハード {@value #RADIUS_HARD}）の円盤で、反対の角は必ず空いている。{@value #BLOCK_TICKS} tick 残る。
 *       体が触れるとダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 個 1 人 1 回。横穴・ロッカーの中は当たらない。
 *       0.4 ブロック/tick で落ちても塊まで約 60 tick あるので、横へ動いて避けられる。</li>
 *   <li>全速・遅く落ちる・寄る・狭まる・床・重力の敵（A-25 / A-50 / A-130 / A-180 / A-200 / A-250 / A-450 / A-600 / A-800 / A-1000）、ほかの深い A の敵（A-1050 / A-1100 / A-1111）とは同時に出ない。</li>
 * </ul>
 */
public class A1180Entity extends AbyssEnemy {
    public static final float ADD = 0.08f, ADD_HARD = 0.10f, DAMAGE = 4f, DAMAGE_HARD = 6f;
    public static final int WARN = 40, WARN_HARD = 34, DURATION = 160, DURATION_HARD = 200, INTERVAL = 50, INTERVAL_HARD = 40, BLOCKS = 2, BLOCKS_HARD = 3, BLOCK_TICKS = 120;
    public static final double BELOW = 24, RADIUS = 1.5, RADIUS_HARD = 1.7, THICK = 0.8, CORNER = 1.2;
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.5f), 1.6f);

    private static final class Mass { final double x, y, z, r; int left; final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        Mass(double x, double y, double z, double r, int left) { this.x = x; this.y = y; this.z = z; this.r = r; this.left = left; } }
    private final List<Mass> masses = new ArrayList<>();
    private boolean heavy = false, done = false;
    private int stateTicks = 0, dropped = 0;

    public A1180Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-1180"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class, A450Entity.class, A600Entity.class,
                A800Entity.class, A1000Entity.class, A1050Entity.class, A1100Entity.class, A1111Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A1180Entity.class)) { RoomsDebug.log("A-1180 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t) || AbyssRooms.boosted(t)) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-1180 prepare target={} base fall={} add={}", t.getGameProfile().getName(), f(AbyssRooms.baseFall(t)), hardSpawn ? ADD_HARD : ADD);
        return blockPosition();
    }

    private void finish(ServerLevel level, Player t, String why) {
        if (!done) RoomsDebug.log("A-1180 end: {}", why);
        done = true;
        if (t instanceof ServerPlayer sp) AbyssRooms.clearBoost(sp);
        else if (targetId != null && level.getPlayerByUUID(targetId) instanceof ServerPlayer sp) AbyssRooms.clearBoost(sp);
        masses.clear();
        leave(why);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { finish(level, null, "no shaft"); return; }
        Player t = target(level, rs);
        if (t == null || done) { finish(level, null, "target gone"); return; }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        stateTicks++;
        if (!heavy) {
            if (stateTicks == 1) {
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
                t.displayClientMessage(Component.literal("空気が重くなる…").withStyle(ChatFormatting.GOLD), true);
                RoomsDebug.announce(java.util.List.of(t), "A-1180：落下が速くなる。下の灰色の塊を横へ避ける", "A-1180 telegraph target={}", t.getGameProfile().getName());
            }
            if (stateTicks >= (hardSpawn ? WARN_HARD : WARN)) {
                heavy = true; stateTicks = 0;
                if (t instanceof ServerPlayer sp) AbyssRooms.boost(sp, hardSpawn ? ADD_HARD : ADD, hardSpawn ? DURATION_HARD : DURATION);
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
            }
            return;
        }
        if (stateTicks % (hardSpawn ? INTERVAL_HARD : INTERVAL) == 10 && dropped < (hardSpawn ? BLOCKS_HARD : BLOCKS)) drop(rs, t);
        tickMasses(level, rs, t);
        if (stateTicks % 30 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.5f, 0.4f);
        if (stateTicks >= (hardSpawn ? DURATION_HARD : DURATION)) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.6f);
            t.displayClientMessage(Component.literal("空気が軽くなった").withStyle(ChatFormatting.GRAY), true);
            finish(level, t, "time over");
        }
    }

    private void drop(RoomsState rs, Player t) {
        Vec3 p = pv(rs, t);
        int corner = random.nextInt(4);
        double sx = (corner & 1) == 0 ? -1 : 1, sz = (corner & 2) == 0 ? -1 : 1;
        masses.add(new Mass(cx(rs.abyss) + sx * CORNER, p.y - BELOW, cz(rs.abyss) + sz * CORNER, hardSpawn ? RADIUS_HARD : RADIUS, BLOCK_TICKS));
        dropped++;
        RoomsDebug.log("A-1180 mass {} at corner ({}, {}) {} below", dropped, sx, sz, f(BELOW));
    }

    private void tickMasses(ServerLevel level, RoomsState rs, Player t) {
        for (java.util.Iterator<Mass> it = masses.iterator(); it.hasNext(); ) {
            Mass m = it.next();
            if (--m.left <= 0) { it.remove(); continue; }
            if (stateTicks % 2 == 0) for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q)) {
                Vec3 c = nearPhysical(rs, q, new Vec3(m.x, m.y, m.z));
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * m.r;
                    level.sendParticles(sp, DUST, true, c.x + Math.cos(a) * r, c.y + (random.nextDouble() - 0.5) * THICK, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            for (Player q : level.players()) {
                if (!inShaft(rs, q) || m.hit.contains(q.getUUID()) || HidingRules.isHiding(q) || sheltered(rs, q, 0.2)) continue;
                Vec3 p = pv(rs, q);
                if (p.y > m.y + THICK / 2 || p.y + q.getBbHeight() < m.y - THICK / 2) continue;
                if (Math.hypot(p.x - m.x, p.z - m.z) > m.r + q.getBbWidth() / 2) continue;
                m.hit.add(q.getUUID());
                boolean hit = RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD));
                level.playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.5f);
                if (q instanceof ServerPlayer sp) level.sendParticles(sp, ParticleTypes.CLOUD, true, q.getX(), q.getY() + 1, q.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
                RoomsDebug.log("A-1180 mass hit {} applied={}", q.getGameProfile().getName(), hit);
            }
        }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず加速を解除（クライアントも自分の数えで戻るが、すぐ戻す）
        if (level() instanceof ServerLevel sl && heavy && targetId != null && sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp) AbyssRooms.clearBoost(sp);
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "heavy=" + heavy + " t=" + stateTicks + " masses=" + masses.size() + " dropped=" + dropped + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Heavy", heavy); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        heavy = t.getBoolean("Heavy");
        done = true; // 読み込み直後は終わり（加速は期限で切れている／次の tick で解除）
    }
}
