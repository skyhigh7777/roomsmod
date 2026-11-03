package com.example.roomsmod.entity.h;

import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.RoomsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * H-150（カーテンの陰）。次の部屋（なければプレイヤーの部屋）の、経路の近くのカーテン（白・灰色の羊毛）の陰に潜む。
 * 姿は見えず、ときどきカーテンが揺れる（音と粒子）。H の外の部屋ではカーテンの代わりに経路の近くの床に潜む。
 * <ul>
 *   <li>隠れていないプレイヤーが TRIGGER_RANGE 以内に来ると気づく：走っていれば即座に、歩いていれば少しずつ（しゃがんでいれば気づかない）。
 *       気づいたら姿を見せ、ALERT_TICKS（ハードは HARD_ALERT_TICKS）後に飛びかかる。その時 LUNGE_RANGE 以内で見えていれば被弾。</li>
 *   <li>このカーテンの HIDE_RANGE 以内の隠れ場所に隠れていると、DRAG_TICKS 後に引き出されて被弾する（その間カーテンが強く揺れる）。</li>
 * </ul>
 * 揺れるカーテンのそばはしゃがんで通り、そのそばの隠れ場所は使わない。
 */
public class HCurtainEntity extends VEntity {
    public static final int LIFE = 20 * 60;
    public static final double TRIGGER_RANGE = 3.0, LUNGE_RANGE = 4.5, HIDE_RANGE = 2.5, PATH_RANGE = 3.0;
    public static final int NOTICE_TICKS = 10, ALERT_TICKS = 20, HARD_ALERT_TICKS = 14, DRAG_TICKS = 60;
    public static final float DAMAGE = 35f;
    private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(HCurtainEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos curtain = null;
    private int notice = 0, alert = 0, drag = 0;

    public HCurtainEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-150"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int maxLifeTicks() { return LIFE; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_REVEALED, false); }
    /** 飛びかかる前（気づいた後）だけ姿を描く。 */
    public boolean isRevealed() { return entityData.get(DATA_REVEALED); }
    private int alertLimit() { return hardSpawn ? HARD_ALERT_TICKS : ALERT_TICKS; }

    static boolean isCurtain(BlockState s) { return s.is(Blocks.WHITE_WOOL) || s.is(Blocks.LIGHT_GRAY_WOOL); }

    private static double distToPath(RoomsState rs, int room, BlockPos q) {
        double best = Double.MAX_VALUE;
        for (BlockPos p : rs.path(room)) best = Math.min(best, Math.sqrt(p.distToLowCornerSqr(q.getX(), p.getY(), q.getZ())));
        // 経路の点の間（直線）も見る
        List<BlockPos> path = rs.path(room);
        for (int i = 0; i + 1 < path.size(); i++) {
            BlockPos a = path.get(i), b = path.get(i + 1);
            if (a.getX() == b.getX() && q.getZ() >= Math.min(a.getZ(), b.getZ()) && q.getZ() <= Math.max(a.getZ(), b.getZ())) best = Math.min(best, Math.abs(q.getX() - a.getX()));
            if (a.getZ() == b.getZ() && q.getX() >= Math.min(a.getX(), b.getX()) && q.getX() <= Math.max(a.getX(), b.getX())) best = Math.min(best, Math.abs(q.getZ() - a.getZ()));
        }
        return best;
    }

    /** room の経路の近く（PATH_RANGE 以内）の、足の高さで横に空気があるカーテン。 */
    static List<BlockPos> curtains(ServerLevel l, RoomsState rs, int room) {
        List<BlockPos> out = new ArrayList<>();
        if (room < 0 || room >= rs.generatedRooms.size() || rs.path(room).isEmpty()) return out;
        int y = rs.path(room).get(0).getY();
        for (BoundingBox b : rs.boxes(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX() + 1, y, b.minZ() + 1, b.maxX() - 1, y, b.maxZ() - 1)) {
                if (!isCurtain(l.getBlockState(q)) || !isCurtain(l.getBlockState(q.above()))) continue;
                boolean open = false;
                for (Direction d : Direction.Plane.HORIZONTAL) open |= l.getBlockState(q.relative(d)).isAir() && l.getBlockState(q.relative(d).above()).isAir();
                if (open && distToPath(rs, room, q) <= PATH_RANGE) out.add(q.immutable());
            }
        return out;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int room = rs.roomPaths.size() - 1;
        List<BlockPos> cs = curtains(level, rs, room);
        if (cs.isEmpty()) { room = playerRoom; cs = curtains(level, rs, room); }
        if (cs.isEmpty()) { // カーテンのない部屋（H の外）：経路の近くの床
            room = rs.roomPaths.size() - 1;
            for (BlockPos q : GKUtil.freeSpots(level, rs, room, level.random, 24)) if (distToPath(rs, room, q) <= PATH_RANGE) cs.add(q);
        }
        if (cs.isEmpty()) { RoomsDebug.log("{} spawn skipped: no curtain or floor near the path (room {})", vName(), room); return null; }
        startRoom = room;
        curtain = cs.get(level.random.nextInt(cs.size()));
        setPos(curtain.getX() + 0.5, curtain.getY(), curtain.getZ() + 0.5);
        RoomsDebug.log("{} lurks at {} room={} curtain={} (of {}) hard={}", vName(), curtain, room, isCurtain(level.getBlockState(curtain)), cs.size(), hardSpawn);
        return curtain;
    }

    private void rustle(ServerLevel level, float vol, float pitch) {
        level.playSound(null, getX(), getY() + 1, getZ(), RoomsMod.H150_RUSTLE.get(), SoundSource.HOSTILE, vol, pitch);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState()), getX(), getY() + 1.2, getZ(), 6, 0.4, 0.6, 0.4, 0.02);
    }

    private double hdist(Player p) { double dx = p.getX() - getX(), dz = p.getZ() - getZ(); return Math.sqrt(dx * dx + dz * dz); }

    @Override
    protected void serverTick(ServerLevel level) {
        if (alert == 0 && drag == 0 && life % 50 == 1) rustle(level, 0.6f, 0.9f + random.nextFloat() * 0.2f);
        if (alert > 0) {
            if (++alert >= alertLimit()) { lunge(level); return; }
            return;
        }
        boolean hidNear = false;
        Player trigger = null;
        for (Player p : playersNear(LUNGE_RANGE + 2)) {
            if (Math.abs(p.getY() - getY()) > 2.5) continue;
            if (HidingRules.isHiding(p)) {
                if (hdist(p) <= HIDE_RANGE) {
                    hidNear = true;
                    if (++drag % 20 == 1) rustle(level, 1.4f, 0.7f);
                    if (drag == 1) RoomsDebug.log(p, vName() + ": 隠れ場所のそばで揺れている", "{} drag start player={}", vName(), p.getGameProfile().getName());
                    if (drag >= DRAG_TICKS) {
                        RoomsDebug.log(p, vName() + ": 隠れ場所から引き出した", "{} drag player={} spot={} creative={}", vName(), p.getGameProfile().getName(), LockerManager.getSpot(p), p.isCreative());
                        if (p instanceof ServerPlayer sp) LockerManager.exit(sp, vName());
                        RoomsDamage.hurt(this, p, DAMAGE);
                        leave("dragged " + p.getGameProfile().getName());
                        return;
                    }
                }
                continue;
            }
            if (hdist(p) > TRIGGER_RANGE) continue;
            if (p.isSprinting()) { notice = NOTICE_TICKS; trigger = p; }
            else if (!p.isCrouching()) { notice++; trigger = p; }
        }
        if (!hidNear && drag > 0) drag = Math.max(0, drag - 1);
        if (trigger == null) { notice = Math.max(0, notice - 1); return; }
        if (notice >= NOTICE_TICKS) {
            alert = 1;
            entityData.set(DATA_REVEALED, true);
            rustle(level, 2.0f, 0.6f);
            GKUtil.actionbar(playersNear(LUNGE_RANGE + 4), Component.translatable("message.roomsmod.h150_alert"));
            RoomsDebug.log(trigger, vName() + ": 気づいた（" + alertLimit() + " tick 後に飛びかかる）", "{} alert player={} sprinting={} lungeIn={}", vName(), trigger.getGameProfile().getName(), trigger.isSprinting(), alertLimit());
        }
    }

    private void lunge(ServerLevel level) {
        rustle(level, 2.5f, 0.5f);
        int hit = 0;
        for (Player p : playersNear(LUNGE_RANGE)) {
            if (HidingRules.isProtectedFrom(this, p)) { RoomsDebug.log(p, vName() + ": 隠れていて助かった", "{} lunge spared player={} (hiding)", vName(), p.getGameProfile().getName()); continue; }
            if (!seesFromCurtain(level, p)) continue;
            RoomsDebug.log(p, vName() + ": 飛びかかった", "{} lunge hit player={} creative={}", vName(), p.getGameProfile().getName(), p.isCreative());
            if (RoomsDamage.hurt(this, p, DAMAGE)) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            hit++;
        }
        leave("lunged hits=" + hit);
    }

    /** カーテンの中から見えるか：本体はカーテン（羊毛）の中なので、プレイヤーに近い側の空いたマスから視線を見る。 */
    private boolean seesFromCurtain(ServerLevel level, Player p) {
        if (curtain == null || !isCurtain(level.getBlockState(curtain))) return canSee(p);
        net.minecraft.world.phys.Vec3 best = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = curtain.relative(d);
            if (!level.getBlockState(q).isAir()) continue;
            net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(q.getX() + 0.5, q.getY() + 1.5, q.getZ() + 0.5);
            if (best == null || c.distanceToSqr(p.getEyePosition()) < best.distanceToSqr(p.getEyePosition())) best = c;
        }
        if (best == null) return false;
        var hit = level.clip(new net.minecraft.world.level.ClipContext(best, p.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || best.distanceTo(hit.getLocation()) >= best.distanceTo(p.getEyePosition()) - 0.5;
    }

    @Override protected void clientTick() {
        if (tickCount % 10 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 0.5 + random.nextDouble() * 2.0, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " curtain=" + curtain + " notice=" + notice + " alert=" + alert + " drag=" + drag; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (curtain != null) t.put("Curtain", net.minecraft.nbt.NbtUtils.writeBlockPos(curtain)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); curtain = net.minecraft.nbt.NbtUtils.readBlockPos(t, "Curtain").orElse(null); }
}
