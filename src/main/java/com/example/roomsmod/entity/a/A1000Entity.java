package com.example.roomsmod.entity.a;

import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A-1000「底」：上から縦穴いっぱいの「底」が落ちてくる。全速で落ち続けていれば追いつかれない。隠れても無駄。
 * <ul>
 *   <li>出現：プレイヤーの {@value #START_ABOVE} ブロック（ハード {@value #START_ABOVE_HARD}）上。叫ぶような音と、タイトル「底が落ちてくる」「止まるな」。
 *       {@value #GRACE} tick はプレイヤーと同じだけ落ちる（離れも近づきもしない＝予告）。</li>
 *   <li>追う：毎 tick {@value #SPEED} ブロック（ハード {@value #SPEED_HARD}）ずつ落ちる（プレイヤーの動きに関係なく一定。全速の落下 {@value AbyssRooms#MAX_FALL} より少し遅い）。
 *       全速で落ちていれば少しずつ離れる（離れすぎたら {@value #MAX_GAP} ブロックで追いつく）。スニーク（半分の速さ）・横穴・ロッカー・何かの上に立つと、
 *       そのぶん追いつかれる（スニークだと 1 秒で約 2.2 ブロック）。底の下面がプレイヤーの頭まで来たらダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 回だけ。
 *       横穴・ロッカーの中でも当たる（A-25 と違い、隠れてやり過ごせない）。</li>
 *   <li>{@value #DURATION} tick（ハード {@value #DURATION_HARD}）追ったら、上へ遠ざかって消える。アクションバーに底までの距離。</li>
 *   <li>引き止め・逆流・床・隠れる必要のある敵（A-25 / A-50 / A-130 / A-450 / A-600 / A-750 / A-800）とは同時に出ない（落ち続けられない）。</li>
 * </ul>
 */
public class A1000Entity extends AbyssEnemy {
    public static final double START_ABOVE = 14, START_ABOVE_HARD = 12, SPEED = 0.235, SPEED_HARD = 0.24, MAX_GAP = 22, HIT_GAP = 1.0;
    public static final int GRACE = 40, DURATION = 300, DURATION_HARD = 360;
    public static final float DAMAGE = 10f, DAMAGE_HARD = 14f;

    private boolean leaving = false;
    /** プレイヤー（頭の高さ）から見た縦のずれ（仮想座標、上が正。底の下面まで）。 */
    private double rel = Double.NaN;

    public A1000Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-1000"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A130Entity.class, A450Entity.class, A600Entity.class, A750Entity.class, A800Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A1000Entity.class)) { RoomsDebug.log("A-1000 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        rel = hardSpawn ? START_ABOVE_HARD : START_ABOVE;
        Vec3 p = pv(rs, t);
        setVirtualPos(new Vec3(cx(rs.abyss), p.y + 1.8 + rel, cz(rs.abyss)));
        RoomsDebug.log("A-1000 prepare target={} gap={} speed={}", t.getGameProfile().getName(), f(rel), hardSpawn ? SPEED_HARD : SPEED);
        return blockPosition();
    }

    private static void title(Player t, String main, String sub) {
        if (!(t instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(main).withStyle(ChatFormatting.DARK_RED)));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        if (t == null || leaving) { // 上へ遠ざかって消える
            setVirtualPos(v.add(0, 0.5, 0));
            if (!leaving) { leaving = true; RoomsDebug.log("A-1000 leaving: target gone"); }
            if (t == null || (pv(rs, t).y + 40 < virtualPos().y)) leave("receded");
            return;
        }
        Vec3 head = pv(rs, t).add(0, 1.8, 0);
        if (Double.isNaN(rel)) rel = v.y - head.y;
        if (life == 1) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 3.0f, 0.4f);
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
            title(t, "底が落ちてくる", "止まるな");
            RoomsDebug.announce(java.util.List.of(t), "A-1000：全速で落ち続ける（スニーク・横穴は追いつかれる）", "A-1000 telegraph target={}", t.getGameProfile().getName());
        }
        if (life <= GRACE) {
            // 予告：プレイヤーと同じだけ落ちる
        } else {
            double speed = hardSpawn ? SPEED_HARD : SPEED;
            double ny = Math.max(v.y - speed, head.y + 0); // 頭より下へは行かない
            rel = Math.min(MAX_GAP, ny - head.y);
        }
        setVirtualPos(new Vec3(cx(rs.abyss), head.y + rel, cz(rs.abyss)));
        if (life % 40 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.4f);
        if (life % 20 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, (float) Math.max(0.6, 2.5 - rel / 10), 0.5f + (float) Math.max(0, 0.6 - rel / 30));
        if (life % 10 == 0) t.displayClientMessage(Component.literal("底まで " + Math.max(0, (int) Math.round(rel)) + "m").withStyle(rel < 6 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY), true);
        dust(level, rs);
        if (life > GRACE && rel <= HIT_GAP) {
            boolean hit = RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD)); // 隠れていても当たる（RusherHits を通さない）
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.5f, 0.4f);
            RoomsDebug.log("A-1000 hit {} applied={} sneaking={} sheltered={}", t.getGameProfile().getName(), hit, t.isShiftKeyDown(), sheltered(rs, t, 0.2));
            leaving = true;
            return;
        }
        if (life > (hardSpawn ? DURATION_HARD : DURATION)) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 2.0f, 0.5f);
            RoomsDebug.log("A-1000 duration over, receding gap={}", f(rel));
            leaving = true;
        }
    }

    /** 底の下面からこぼれる黒い粒（縦穴の内側いっぱい）。 */
    private void dust(ServerLevel level, RoomsState rs) {
        if (abyssHidden[0]) return;
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q))
            for (int i = 0; i < 3; i++)
                level.sendParticles(sp, i == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SQUID_INK, true, cx(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, getY() - 0.2, cz(rs.abyss) + (random.nextDouble() - 0.5) * 4.6, 0, 0, -0.15, 0, 1.0);
    }

    @Override
    protected void clientTick() {
        for (int i = 0; i < 3; i++)
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5) * 4.0, getY() + random.nextDouble() * 2.5, getZ() + (random.nextDouble() - 0.5) * 4.0, 0, -0.02, 0);
    }

    @Override protected String debugState() { return "rel=" + (Double.isNaN(rel) ? "?" : f(rel)) + " leaving=" + leaving + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Leaving", leaving); if (!Double.isNaN(rel)) t.putDouble("Rel", rel); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        leaving = t.getBoolean("Leaving");
        rel = t.contains("Rel") ? t.getDouble("Rel") : Double.NaN;
    }
}
