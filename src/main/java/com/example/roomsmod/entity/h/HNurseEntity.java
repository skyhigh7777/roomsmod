package com.example.roomsmod.entity.h;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.hiding.RusherHits;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.HardMode;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * H-90（夜勤の看護師）。プレイヤーの部屋の入口から入り、その部屋と次の部屋の経路をゆっくり見回って出口で消える。
 * CHECK_EVERY ブロックごとに立ち止まって見回す（確認の音）。
 * <ul>
 *   <li>隠れていない・見えているプレイヤー（OPEN_RANGE 以内）：RusherHits の通常ルールで被弾し、鈍化する。</li>
 *   <li>隠れているプレイヤー：GAZE_RANGE 以内で看護師を見つめ続ける（視線が看護師の方を向いている）と気づかれる。
 *       GAZE_TICKS（ハードは HARD_GAZE_TICKS）たまると隠れ場所から引き出されて被弾し、看護師は去る。目をそらせば少しずつ減る。</li>
 * </ul>
 * 隠れて、看護師の方を見ないでやり過ごす。
 */
public class HNurseEntity extends VEntity {
    public static final double WALK_SPEED = 0.11;
    public static final int GRACE = 60;
    public static final double CHECK_EVERY = 7.0;
    public static final int CHECK_TICKS = 30, STEP_INTERVAL = 10;
    public static final double OPEN_RANGE = 10.0;
    public static final float OPEN_DAMAGE = 12f;
    public static final double GAZE_RANGE = 9.0;
    /** 視線の向きと看護師への向きの内積がこれ以上なら「見ている」。 */
    public static final double GAZE_DOT = 0.96;
    public static final int GAZE_TICKS = 30, HARD_GAZE_TICKS = 20;
    public static final float FOUND_DAMAGE = 30f;

    private List<BlockPos> wps = null;
    private int wpIndex = 0, grace = 0, checking = 0, lastStep = 0;
    private double walked = 0;
    private final Map<UUID, Integer> gaze = new HashMap<>();

    public HNurseEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-90"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }

    private int graceTicks() { return HardMode.grace(GRACE, hardSpawn); }
    private double walkSpeed() { return HardMode.speed(WALK_SPEED, hardSpawn); }
    private int gazeLimit() { return hardSpawn ? HARD_GAZE_TICKS : GAZE_TICKS; }

    private List<BlockPos> waypoints(RoomsState rs) {
        if (wps == null) wps = pathOf(rs, startRoom, Math.min(startRoom + 1, rs.roomPaths.size() - 1));
        return wps;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> w = waypoints(rs);
        if (w.isEmpty()) return null;
        BlockPos s = w.get(0);
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        level.playSound(null, s, RoomsMod.H90_CHECK.get(), SoundSource.HOSTILE, 2.0f, 0.8f); // 入って来る合図
        RoomsDebug.log("{} round rooms {}..{} waypoints={} hard={} gazeLimit={}", vName(), startRoom, Math.min(startRoom + 1, rs.roomPaths.size() - 1), w.size(), hardSpawn, gazeLimit());
        return s;
    }

    /** p の視線が看護師（体の中ほど）を向いているか。 */
    private boolean looksAtMe(Player p) {
        Vec3 to = new Vec3(getX(), getY() + 1.0, getZ()).subtract(p.getEyePosition());
        if (to.lengthSqr() < 1.0E-4) return true;
        return p.getViewVector(1.0f).dot(to.normalize()) >= GAZE_DOT;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (grace < graceTicks()) { grace++; return; }
        List<BlockPos> w = waypoints(rs);
        if (wpIndex >= w.size()) { leave("round over"); return; }

        // 隠れていないプレイヤー
        if (life % 10 == 0)
            for (Player p : playersNear(OPEN_RANGE))
                if (!HidingRules.isHiding(p) && RusherHits.tryHit(this, vName(), p, canSee(p), OPEN_DAMAGE))
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        // 隠れているプレイヤーの視線
        for (Player p : playersNear(GAZE_RANGE)) {
            if (!HidingRules.isHiding(p)) continue;
            UUID id = p.getUUID();
            int g = gaze.getOrDefault(id, 0);
            if (looksAtMe(p)) {
                g++;
                if (g == 1) {
                    level.playSound(null, getX(), getY(), getZ(), RoomsMod.H90_CHECK.get(), SoundSource.HOSTILE, 1.0f, 1.4f);
                    GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.h90_gaze"));
                    RoomsDebug.log(p, vName() + ": 視線に気づき始めた", "{} gaze start player={}", vName(), p.getGameProfile().getName());
                }
                if (g >= gazeLimit()) {
                    RoomsDebug.log(p, vName() + ": 隠れ場所から引き出した", "{} found player={} spot={} creative={}", vName(), p.getGameProfile().getName(), LockerManager.getSpot(p), p.isCreative());
                    if (p instanceof ServerPlayer sp) LockerManager.exit(sp, vName());
                    RoomsDamage.hurt(this, p, FOUND_DAMAGE);
                    leave("found " + p.getGameProfile().getName());
                    return;
                }
            } else g = Math.max(0, g - 2);
            gaze.put(id, g);
        }

        if (checking > 0) { checking--; return; }
        BlockPos t = w.get(wpIndex);
        Vec3 goal = new Vec3(t.getX() + 0.5, t.getY(), t.getZ() + 0.5);
        Vec3 d = goal.subtract(position());
        faceTowards(goal);
        double step = walkSpeed();
        if (d.length() <= step) { setPos(goal.x, goal.y, goal.z); wpIndex++; walked += d.length(); }
        else { Vec3 n = position().add(d.normalize().scale(step)); setPos(n.x, n.y, n.z); walked += step; }
        if (life - lastStep >= STEP_INTERVAL) {
            lastStep = life;
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.H90_STEP.get(), SoundSource.HOSTILE, 1.4f, 0.9f + random.nextFloat() * 0.2f);
        }
        if (walked >= CHECK_EVERY) {
            walked = 0;
            checking = CHECK_TICKS;
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.H90_CHECK.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
            RoomsDebug.log("{} checks at ({}, {}, {})", vName(), f(getX()), f(getY()), f(getZ()));
        }
    }

    @Override protected String debugState() { return "room=" + startRoom + " wp=" + wpIndex + "/" + (wps == null ? "?" : wps.size()) + " grace=" + grace + " checking=" + checking + " gaze=" + gaze.values(); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Wp", wpIndex); t.putInt("Grace", grace); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); wpIndex = t.getInt("Wp"); grace = t.getInt("Grace"); wps = null; }
}
