package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** S-85：足し算の小テスト。赤・緑・青のカーペットのうち、正解の色の上に立つ（隠れても無駄）。 */
public class GKQuizEntity extends GKTimedEntity {
    public static final int TELEGRAPH = 40, TIME = 20 * 15;
    public static final float DAMAGE = 20f;
    static final Block[] CARPETS = {Blocks.RED_CARPET, Blocks.LIME_CARPET, Blocks.LIGHT_BLUE_CARPET};
    static final String[] COLORS = {"red", "green", "blue"};
    private int a, b, correct;
    private final int[] answers = new int[3];
    public GKQuizEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-85"; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> s = new ArrayList<>(GKUtil.freeSpots(level, rs, playerRoom, level.random, 4));
        if (s.size() < 3) { RoomsDebug.log("S-85 spawn skipped: only {} carpet spots", s.size()); return null; }
        a = 2 + level.random.nextInt(8); b = 2 + level.random.nextInt(8);
        List<Integer> opts = new ArrayList<>(List.of(a + b, a + b + 1 + level.random.nextInt(2), a + b - 1 - level.random.nextInt(2)));
        java.util.Random shuf = new java.util.Random(level.random.nextLong());
        Collections.shuffle(opts, shuf);
        Collections.shuffle(s, shuf);
        for (int i = 0; i < 3; i++) { answers[i] = opts.get(i); if (opts.get(i) == a + b) correct = i; place(level, s.get(i), CARPETS[i].defaultBlockState()); }
        // 立ち位置は選択肢と無関係に：4つ目の空き床があればそこ、なければランダムな選択肢の上
        BlockPos c = s.size() > 3 ? s.get(3) : s.get(level.random.nextInt(3));
        setPos(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        RoomsDebug.log("S-85 {}+{} answers red={} green={} blue={} correct={} carpets={} standsAt={}", a, b, answers[0], answers[1], answers[2], COLORS[correct], placed, c);
        return c;
    }

    /** 正解のカーペットの位置（テスト・デバッグ用）。 */
    public BlockPos correctCarpet() { return placed.size() > correct ? placed.get(correct) : null; }

    private Component question(int left) {
        return Component.translatable("message.roomsmod.gk_quiz", a, b, answers[0], answers[1], answers[2], left);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        List<Player> ps = GKUtil.players(level, this, startRoom, 24);
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_DESK_CHECK.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
            for (Player p : ps) { p.sendSystemMessage(question(TIME / 20)); RoomsDebug.log(p, "S-85 正解は " + COLORS[correct], "S-85 telegraph room={}", startRoom); }
        }
        if (phase % 20 == 0) GKUtil.actionbar(ps, question(Math.max(0, TELEGRAPH + TIME - phase) / 20));
        if (phase < TELEGRAPH + TIME) return;
        BlockPos ok = placed.get(correct);
        for (Player p : ps) {
            BlockPos f = p.blockPosition();
            boolean right = f.getX() == ok.getX() && f.getZ() == ok.getZ() && Math.abs(f.getY() - ok.getY()) <= 1;
            RoomsDebug.log(p, "S-85: " + (right ? "正解" : "不正解"), "S-85 result player={} correct={} at={} carpet={}", p.getGameProfile().getName(), right, f, ok);
            if (!right) RoomsDamage.hurt(this, p, DAMAGE);
        }
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_DESK_CHECK.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
        leave("quiz over");
    }

    @Override public boolean isInvisible() { return true; }
    @Override protected void clientTick() {
        if (tickCount % 5 == 0) level().addParticle(ParticleTypes.ENCHANT, getX(), getY() + 1, getZ(), 0, 0.3, 0);
    }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putIntArray("Quiz", new int[]{a, b, correct, answers[0], answers[1], answers[2]}); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t); int[] q = t.getIntArray("Quiz");
        if (q.length == 6) { a = q[0]; b = q[1]; correct = q[2]; answers[0] = q[3]; answers[1] = q[4]; answers[2] = q[5]; }
    }
}
