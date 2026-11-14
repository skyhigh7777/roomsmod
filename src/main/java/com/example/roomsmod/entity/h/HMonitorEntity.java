package com.example.roomsmod.entity.h;

import com.example.roomsmod.entity.gk.GKRoomWatcher;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H-70（心電図）。開けた部屋で心拍モニターが鳴る（予告 TELEGRAPH_TICKS）→ 監視（WATCH_TICKS）。
 * 監視中、隠れていないプレイヤーが走ると毎 tick、被弾するとそのあと HIT_ALARM tick 分アラームが貯まる。
 * 歩く・しゃがむ・止まる・隠れるは数えない。LIMIT（ハード HARD_LIMIT）に達したら飛びかかって DAMAGE。
 */
public class HMonitorEntity extends GKRoomWatcher {
    public static final int TELEGRAPH_TICKS = 40, HARD_TELEGRAPH_TICKS = 28, WATCH_TICKS = 160;
    public static final int LIMIT = 30, HARD_LIMIT = 20, HIT_ALARM = 10;
    public static final float DAMAGE = 25f;
    public static final double HEAR_RANGE = 16;
    private final Map<UUID, Integer> lastInvuln = new HashMap<>();
    private final Map<UUID, Integer> hitRemain = new HashMap<>();

    public HMonitorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-70"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int telegraphTicks() { return hardSpawn ? HARD_TELEGRAPH_TICKS : TELEGRAPH_TICKS; }
    @Override protected int watchTicks() { return WATCH_TICKS; }
    @Override protected int limit() { return hardSpawn ? HARD_LIMIT : LIMIT; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent cue() { return SoundEvents.WARDEN_HEARTBEAT; }
    @Override protected int cueInterval() { return 25; }
    @Override protected String chatStart() { return "H-70: 心電図が鳴り始めた"; }

    /** 開けた部屋とその 1 つ前、または HEAR_RANGE 以内。 */
    @Override
    protected boolean inMyRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < HEAR_RANGE;
    }

    @Override
    protected boolean violates(Player p) {
        int prev = lastInvuln.getOrDefault(p.getUUID(), 0);
        int now = p.invulnerableTime;
        lastInvuln.put(p.getUUID(), now);
        if (now > 10 && now > prev) {
            hitRemain.put(p.getUUID(), HIT_ALARM);
            RoomsDebug.log(p, vName() + ": 被弾でアラーム +" + HIT_ALARM, "{} alarm from hit player={}", vName(), p.getGameProfile().getName());
        }
        int left = hitRemain.getOrDefault(p.getUUID(), 0);
        if (left > 0) { hitRemain.put(p.getUUID(), left - 1); return true; }
        return p.isSprinting();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (isRemoved()) return;
        if (phaseTick == telegraphTicks() + 1) {
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.h70_watch"));
            RoomsDebug.log("{} monitoring limit={}", vName(), limit());
        }
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.85f, 0.15f, 0.15f), 1.1f);
        if (tickCount % 4 == 0)
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 0.8, getY() + 1.2 + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
    }
}
