package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.hiding.RusherHits;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * A-90：数部屋下の横穴（ロッカーの間）に潜んでいて、近くを通ると飛びかかる。
 * <ul>
 *   <li>出現：プレイヤーの 2〜6 部屋下の横穴。ほかに使える横穴（{@value AbyssSpace#MAX_ALCOVE_GAP} 部屋以内）が残るときだけ。</li>
 *   <li>プレイヤーが 2 部屋上まで来るとカチカチ音（予告）。横穴から顔を出したり引っ込めたりする。</li>
 *   <li>横穴の高さを通るとき、入口から {@value #TRIGGER} ブロック（ハード {@value #TRIGGER_HARD}）以内なら飛びかかる。
 *       反対側の壁に寄って通れば当たらない（縦穴の内側は 5 ブロック幅、反対の壁際まで約 4 ブロック）。</li>
 *   <li>ほかの横穴のロッカーに隠れていれば安全。この敵のいる横穴のロッカーに隠れる（不用意に隠れる）と、少しして引きずり出されて攻撃される。</li>
 *   <li>通り過ぎたら（横穴より 8 ブロック下）消える。</li>
 * </ul>
 */
public class A90Entity extends AbyssEnemy {
    public static final double TRIGGER = 2.6, TRIGGER_HARD = 3.2, LUNGE_SPEED = 0.8, HIT_RANGE = 1.4;
    public static final int LUNGE_TICKS = 15, SNIFF_TICKS = 25;
    private enum State { WAIT, LUNGE, RETREAT }
    private State state = State.WAIT;
    private int alcoveRoom = -1, stateTicks = 0, sniff = 0;
    private boolean warned = false;

    public A90Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-90"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }

    /** 生きている A-90 が使っている横穴の部屋。 */
    public static Set<Integer> claimedAlcoves(ServerLevel level) {
        Set<Integer> out = new HashSet<>();
        for (Entity e : level.getAllEntities()) if (e instanceof A90Entity a && a.isAlive() && a.alcoveRoom >= 0) out.add(a.alcoveRoom);
        return out;
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        Player t = pickTarget(level, rs, player);
        if (t == null) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        Set<Integer> claimed = claimedAlcoves(level);
        int r = AbyssSpace.alcoveWithin(level, rs, room + 2, 4, claimed);
        if (r < 0) { RoomsDebug.log("A-90 prepare skipped: no alcove 2..6 rooms below {}", room); return null; }
        // 隠れる場所を奪い切らない：この横穴以外にも、すぐ下に使える横穴があること
        Set<Integer> ex = new HashSet<>(claimed); ex.add(r);
        if (AbyssSpace.alcoveWithin(level, rs, room + 1, AbyssSpace.MAX_ALCOVE_GAP, ex) < 0) { RoomsDebug.log("A-90 prepare skipped: would leave no free alcove near {}", room); return null; }
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, r);
        targetId = t.getUUID();
        alcoveRoom = r;
        setVirtualPos(spot.back());
        RoomsDebug.log("A-90 prepare target={} room={} alcoveRoom={} back={}", t.getGameProfile().getName(), room, r, spot.back());
        return blockPosition();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null || alcoveRoom < 0) { leave("no shaft"); return; }
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, alcoveRoom);
        if (spot == null) { leave("no alcove"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (t == null) { if (stateTicks > 100) leave("target gone"); return; }
        Vec3 p = pv(rs, t);
        double rel = p.y - spot.floorY();
        if (rel < -8) { leave("passed"); return; }
        if (!warned && rel < 2 * AbyssRooms.L) {
            warned = true;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE, 3.0f, 0.6f);
            RoomsDebug.announce(java.util.List.of(t), "A-90 が潜んでいるわ（" + com.example.roomsmod.world.section.Sections.formatRoom(rs, alcoveRoom) + " の横穴）",
                    "A-90 telegraph alcoveRoom={} rel={}", alcoveRoom, f(rel));
        }
        switch (state) {
            case WAIT -> {
                // 顔を出したり引っ込めたり
                double k = (Math.sin(life * 0.08) + 1) * 0.3;
                setVirtualPos(spot.back().add(spot.mouth().subtract(spot.back()).scale(k)));
                // 不用意に隠れた：この横穴のロッカー
                BlockPos sp = HidingRules.isHiding(t) ? LockerManager.getSpotPos(t) : null;
                if (sp != null && AbyssSpace.playerVirtual(rs, t, Vec3.atCenterOf(sp)).distanceTo(spot.back()) < 2.6) {
                    if (sniff++ % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 2.0f, 0.8f);
                    if (sniff >= SNIFF_TICKS) {
                        if (t instanceof ServerPlayer s2) LockerManager.exit(s2, "A-90");
                        RoomsDamage.hurt(this, t, hardDamage(16f, 20f));
                        RoomsDebug.log("A-90 pulled {} out of its own alcove locker", t.getGameProfile().getName());
                        state = State.RETREAT; stateTicks = 0;
                    }
                    return;
                }
                sniff = 0;
                if (HidingRules.isHiding(t)) return;
                double hd = Math.hypot(p.x - spot.mouth().x, p.z - spot.mouth().z);
                if (rel > -2.0 && rel < 3.0 && hd < (hardSpawn ? TRIGGER_HARD : TRIGGER)) {
                    state = State.LUNGE; stateTicks = 0;
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 3.0f, 0.6f);
                    RoomsDebug.log("A-90 lunge rel={} hd={}", f(rel), f(hd));
                }
            }
            case LUNGE -> {
                Vec3 v = virtualPos(), goal = p.add(0, 0.6, 0), d = goal.subtract(v);
                double len = d.length();
                setVirtualPos(len <= LUNGE_SPEED ? goal : v.add(d.scale(LUNGE_SPEED / len)));
                faceTowards(position().add(d));
                if (virtualDistanceTo(t) <= HIT_RANGE) {
                    RusherHits.tryHit(this, vName(), t, canSeeVirtual(t), hardDamage(10f, 14f));
                    state = State.RETREAT; stateTicks = 0;
                } else if (stateTicks > LUNGE_TICKS) { state = State.RETREAT; stateTicks = 0; }
            }
            case RETREAT -> {
                Vec3 v = virtualPos(), d = spot.back().subtract(v);
                double len = d.length();
                setVirtualPos(len <= 0.5 ? spot.back() : v.add(d.scale(0.5 / len)));
                if (stateTicks > 30) leave("retreated");
            }
        }
    }

    @Override
    protected void clientTick() {
        if (tickCount % 3 != 0) return;
        DustParticleOptions eye = new DustParticleOptions(new Vector3f(1f, 0.95f, 0.6f), 0.8f);
        level().addParticle(eye, getX() - 0.18, getY() + 1.5, getZ(), 0, 0, 0);
        level().addParticle(eye, getX() + 0.18, getY() + 1.5, getZ(), 0, 0, 0);
    }

    @Override protected String debugState() { return "state=" + state + " alcoveRoom=" + alcoveRoom + " warned=" + warned + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("AlcoveRoom", alcoveRoom); t.putBoolean("Warned", warned); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.WAIT; }
        alcoveRoom = t.contains("AlcoveRoom") ? t.getInt("AlcoveRoom") : -1; warned = t.getBoolean("Warned");
    }
}
