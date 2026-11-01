package com.example.roomsmod.entity.f;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKRoomWatcher;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F-5（聞き耳）。プレイヤーの部屋の水面から大きな耳の頭を出し、低くうなる（予告 TELEGRAPH_TICKS）→ 水音に耳をすます（WATCH_TICKS）→ 沈む。
 * 耳をすましている間、その部屋と 1 つ前の部屋（扉越し）で隠れていないプレイヤーの「水音」を数える：
 * 走る・跳ぶ＝毎 tick、歩く＝2 tick に 1 回、しゃがみ歩き・止まっている＝数えない。LIMIT（ハード HARD_LIMIT）に達したら飛びかかって DAMAGE。
 * 止まるか、しゃがんで歩くか、隠れる。
 */
public class FListenerEntity extends GKRoomWatcher {
    public static final int TELEGRAPH_TICKS = 50, HARD_TELEGRAPH_TICKS = 35, WATCH_TICKS = 140, LIMIT = 24, HARD_LIMIT = 18;
    public static final float DAMAGE = 30f;
    /** 1 tick にこれ以上横に動いたら「歩いている」。 */
    public static final double MOVE_EPS = 0.06, HEAR_RANGE = 16;
    private final Map<UUID, Vec3> last = new HashMap<>();

    public FListenerEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-5"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int telegraphTicks() { return hardSpawn ? HARD_TELEGRAPH_TICKS : TELEGRAPH_TICKS; }
    @Override protected int watchTicks() { return WATCH_TICKS; }
    @Override protected int limit() { return hardSpawn ? HARD_LIMIT : LIMIT; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent cue() { return RoomsMod.F5_HUM.get(); }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "F-5: 水面から耳が出た"; }

    /** 水音は扉越しにも聞こえる：出た部屋（開けた部屋）とその 1 つ前の部屋、または HEAR_RANGE 以内。 */
    @Override
    protected boolean inMyRoom(ServerLevel level, Player p) {
        int r = com.example.roomsmod.event.RoomTracker.roomIndexAt(com.example.roomsmod.world.RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < HEAR_RANGE;
    }

    @Override
    protected boolean violates(Player p) {
        Vec3 prev = last.put(p.getUUID(), p.position());
        if (prev == null) return false;
        Vec3 d = p.position().subtract(prev);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        boolean noisy;
        if (p.isSprinting() && h > MOVE_EPS) noisy = true;
        else if (d.y > 0.15) noisy = true; // 跳んだ
        else if (h > MOVE_EPS && !p.isCrouching()) noisy = (tickCount & 1) == 0;
        else noisy = false;
        if (noisy && tickCount % 6 == 0 && level() instanceof ServerLevel sl)
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_SWIM, SoundSource.PLAYERS, 0.5f, 1.4f);
        return noisy;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (isRemoved()) return;
        if (phaseTick == telegraphTicks() + 1) {
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.f5_listen"));
            RoomsDebug.log("{} listening limit={}", vName(), limit());
        }
    }

    @Override protected void clientTick() {
        if (tickCount % 6 == 0) level().addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.6, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0, 0);
    }
}
