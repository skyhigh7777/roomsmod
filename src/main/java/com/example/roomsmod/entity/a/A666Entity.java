package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/**
 * A-666「数える声」：声が 3、2、1 と数え、0 の瞬間に横へ動いているとダメージ。姿はない。
 * <ul>
 *   <li>予告：ささやき声と、アクションバー「…数える声がする」（{@value #INTRO} tick）。</li>
 *   <li>数える：3・2・1 を {@value #STEP} tick（ハード {@value #STEP_HARD}）ごとに、画面の真ん中の大きな文字（タイトル）と低い音で。</li>
 *   <li>0：直前 {@value #WINDOW} tick の横の移動量の平均が {@value #THRESHOLD} ブロック/tick より大きければダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）。
 *       縦（落ちる）は数えない。空中で手を離すと横の速さは 1 秒ほどで消えるので、「1」で止まれば間に合う。ロッカーの中は動かないので安全。</li>
 *   <li>全部で {@value #ROUNDS} 回（ハード {@value #ROUNDS_HARD}）、あいだに {@value #REST} tick。</li>
 *   <li>A-350 / A-450 / A-1000 以外の A の敵とは同時に出ない（横へ動いて避ける・隠れに行く敵とぶつかる）。</li>
 * </ul>
 */
public class A666Entity extends AbyssEnemy {
    public static final int INTRO = 30, STEP = 20, STEP_HARD = 16, WINDOW = 6, REST = 70, ROUNDS = 2, ROUNDS_HARD = 3;
    public static final double THRESHOLD = 0.04;
    public static final float DAMAGE = 5f, DAMAGE_HARD = 8f;

    private enum State { INTRO, COUNT, REST, GONE }
    private State state = State.INTRO;
    private int stateTicks = 0, round = 0;
    /** 直近の横の位置（WINDOW+1 個）。 */
    private final java.util.ArrayDeque<double[]> hist = new java.util.ArrayDeque<>();

    public A666Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-666"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class,
                A300Entity.class, A525Entity.class, A600Entity.class, A750Entity.class, A800Entity.class};
    }

    private int step() { return hardSpawn ? STEP_HARD : STEP; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A666Entity.class)) { RoomsDebug.log("A-666 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-666 prepare target={} rounds={} step={}", t.getGameProfile().getName(), hardSpawn ? ROUNDS_HARD : ROUNDS, step());
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    private static void title(Player t, Component c, int stay) {
        if (!(t instanceof ServerPlayer sp)) return;
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(0, stay, 4));
        sp.connection.send(new ClientboundSetTitleTextPacket(c));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (state == State.GONE || t == null) { if (stateTicks > 5 || t == null) leave(t == null ? "target gone" : "done"); return; }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        // 横の位置の記録（1 周のテレポートは縦だけなので横はそのまま。大きく飛んだら＝ワープ・寄せ、記録をやり直す）
        double[] last = hist.peekLast();
        if (last != null && Math.hypot(t.getX() - last[0], t.getZ() - last[1]) > 2.0) hist.clear();
        hist.addLast(new double[]{t.getX(), t.getZ()});
        while (hist.size() > WINDOW + 1) hist.removeFirst();
        switch (state) {
            case INTRO -> {
                if (stateTicks == 1) {
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.EVOKER_AMBIENT, SoundSource.HOSTILE, 2.0f, 0.5f);
                    t.displayClientMessage(Component.literal("…数える声がする。0 で動いていてはいけない").withStyle(ChatFormatting.DARK_RED), true);
                    RoomsDebug.announce(java.util.List.of(t), "A-666：0 の瞬間は横に動かない（落ちるのは平気）", "A-666 telegraph target={}", t.getGameProfile().getName());
                }
                if (stateTicks >= INTRO) setState(State.COUNT);
            }
            case COUNT -> {
                int n = 3 - (stateTicks - 1) / step(); // 3, 2, 1, 0
                if ((stateTicks - 1) % step() == 0) {
                    if (n > 0) {
                        title(t, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.DARK_RED), step());
                        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 2.5f, 0.5f + (3 - n) * 0.05f);
                        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.EVOKER_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.4f + (3 - n) * 0.1f);
                    } else {
                        zero(level, rs, t);
                        round++;
                        setState(round >= (hardSpawn ? ROUNDS_HARD : ROUNDS) ? State.GONE : State.REST);
                    }
                }
            }
            case REST -> { if (stateTicks >= REST) setState(State.INTRO); }
            default -> {}
        }
    }

    /** 0：直前 WINDOW tick の横の移動量を見る。 */
    private void zero(ServerLevel level, RoomsState rs, Player t) {
        double moved = 0;
        double[] prev = null;
        int n = 0;
        for (double[] h : hist) { if (prev != null) { moved += Math.hypot(h[0] - prev[0], h[1] - prev[1]); n++; } prev = h; }
        double avg = n == 0 ? 0 : moved / n;
        boolean hit = avg > THRESHOLD && !HidingRules.isHiding(t);
        title(t, Component.literal("0").withStyle(hit ? ChatFormatting.RED : ChatFormatting.GRAY), 14);
        RoomsDebug.log("A-666 zero round={} player={} avgMove={} ticks={} -> {}", round + 1, t.getGameProfile().getName(), String.format("%.3f", avg), n, hit ? "hit" : "safe");
        if (hit) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.6f);
            if (t instanceof ServerPlayer sp) level.sendParticles(sp, ParticleTypes.SONIC_BOOM, true, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
            RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
        } else {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 0.4f);
        }
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "state=" + state + " round=" + round + " t=" + stateTicks + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Round", round); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        round = t.getInt("Round");
        state = State.REST; // 読み込み直後は数え直し（予告から）
        stateTicks = 0;
    }
}
