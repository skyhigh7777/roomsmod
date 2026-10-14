package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * V25-BUFFED：強化版の捜索者（V-25-T/L と同じ排他グループ "searcher"）。
 * 出現直後は実体なし（描画なし・当たり判定なし・攻撃なし）で、ディメンション内の全員の画面中央に
 * カウントダウン（COUNTDOWN_SECONDS 秒、毎秒チック音）→ 指示文を ANNOUNCE_TICKS 表示 → 姿を現して
 * 指示文どおりの種類の隠れ場所だけを高速で調べる（プレイヤーの部屋＋次の部屋）→ 出口で消える。
 * 指示文は lang の "roomsmod.v25_buffed.say.<table|locker>.<n>"（n=1,2,… 連番、en_us に全キー必須）。
 * table = テーブルを調べる（ロッカーは安全）、locker = ロッカーを調べる（テーブルは安全）。
 */
public class V25BuffedEntity extends VSearcherEntity {
    public static final int COUNTDOWN_SECONDS = 5;
    public static final int ANNOUNCE_TICKS = 20;
    public static final double FAST_WALK_SPEED = 0.45; // V-25 は 0.14
    public static final int FAST_CHECK_TICKS = 12;     // V-25 は 40
    public static final int FAST_STEP_INTERVAL = 4;
    /** lang に連番が見つからない時の上限保険。 */
    private static final int MAX_MESSAGES = 200;

    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(V25BuffedEntity.class, EntityDataSerializers.BOOLEAN);

    /** /rooms spawn v25buffed <table|locker> 用：次の1体の対象を固定（null=ランダム）。 */
    public static LockerManager.Spot forcedNext = null;

    private LockerManager.Spot target = LockerManager.Spot.TABLE;
    /** 0=カウントダウン, 1=指示表示, 2=捜索 */
    private int phase = 0, phaseTicks = 0, msgIndex = 1;

    public V25BuffedEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        if (forcedNext != null) { target = forcedNext; forcedNext = null; forced = true; }
        else target = level.random.nextBoolean() ? LockerManager.Spot.TABLE : LockerManager.Spot.LOCKER;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(REVEALED, false); }
    public boolean isRevealed() { return entityData.get(REVEALED); }

    @Override public String vName() { return "V25-BUFFED"; }
    @Override protected LockerManager.Spot spotType() { return target; }
    @Override protected SoundEvent stepSound() { return target == LockerManager.Spot.TABLE ? RoomsMod.V25T_STEP.get() : RoomsMod.V25L_STEP.get(); }
    @Override protected SoundEvent checkSound() { return target == LockerManager.Spot.TABLE ? RoomsMod.V25T_CHECK.get() : RoomsMod.V25L_CHECK.get(); }
    @Override protected double walkSpeed() { return FAST_WALK_SPEED; }
    @Override protected int checkTicks() { return FAST_CHECK_TICKS; }
    @Override protected int enterGraceTicks() { return 0; }
    @Override protected int stepInterval() { return FAST_STEP_INTERVAL; }
    @Override protected boolean entrySound() { return false; }
    @Override public boolean isInvisible() { return !isRevealed() || super.isInvisible(); }
    @Override public boolean isPickable() { return isRevealed(); }

    private static String key(LockerManager.Spot t, int n) {
        return "roomsmod.v25_buffed.say." + (t == LockerManager.Spot.TABLE ? "table" : "locker") + "." + n;
    }

    /** lang（サーバー側は en_us）に連番で何個あるか。 */
    public static int messageCount(LockerManager.Spot t) {
        Language l = Language.getInstance();
        int n = 0;
        while (n < MAX_MESSAGES && l.has(key(t, n + 1))) n++;
        return Math.max(1, n);
    }

    private void title(ServerLevel level, Component main, Component sub, int stay) {
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, stay, 4));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(main));
        }
    }

    private void enter(int ph, String what) {
        phase = ph; phaseTicks = 0;
        RoomsDebug.log("V25-BUFFED phase {} target={} msg={} ({})", what, target, key(target, msgIndex), debugState());
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        if (phase == 0) {
            if (phaseTicks == 0) RoomsDebug.log("V25-BUFFED phase countdown target={} seconds={}", target, COUNTDOWN_SECONDS);
            if (phaseTicks % 20 == 0 && phaseTicks / 20 < COUNTDOWN_SECONDS) {
                int sec = COUNTDOWN_SECONDS - phaseTicks / 20;
                title(level, Component.literal(String.valueOf(sec)).withStyle(net.minecraft.ChatFormatting.RED), Component.empty(), 22);
                for (ServerPlayer p : level.players()) p.playNotifySound(RoomsMod.V25B_TICK.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                RoomsDebug.log("V25-BUFFED countdown {}", sec);
            }
            if (++phaseTicks >= COUNTDOWN_SECONDS * 20) {
                msgIndex = 1 + random.nextInt(messageCount(target));
                enter(1, "announce");
                title(level, Component.translatable(key(target, msgIndex)).withStyle(net.minecraft.ChatFormatting.DARK_RED), Component.empty(), ANNOUNCE_TICKS);
            }
            return true;
        }
        if (phase == 1) {
            if (++phaseTicks >= ANNOUNCE_TICKS) {
                enter(2, "search");
                entityData.set(REVEALED, true);
                for (ServerPlayer p : level.players()) p.playNotifySound(RoomsMod.V25B_REVEAL.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
            }
            return true;
        }
        return false;
    }

    private boolean forced = false;

    @Override
    protected boolean retargetWhenNoSpots() {
        if (forced) return false; // コマンドで指定された種類は変えない
        target = target == LockerManager.Spot.TABLE ? LockerManager.Spot.LOCKER : LockerManager.Spot.TABLE;
        RoomsDebug.log("V25-BUFFED no spots of the rolled type -> retarget {}", target);
        return true;
    }

    @Override protected String debugState() { return super.debugState() + " phase=" + phase + " target=" + target; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase); tag.putInt("PhaseTicks", phaseTicks); tag.putInt("Msg", msgIndex);
        tag.putString("Target", target.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        phase = tag.getInt("Phase"); phaseTicks = tag.getInt("PhaseTicks"); msgIndex = Math.max(1, tag.getInt("Msg"));
        if (tag.contains("Target")) target = LockerManager.Spot.valueOf(tag.getString("Target"));
        entityData.set(REVEALED, phase >= 2);
    }
}
