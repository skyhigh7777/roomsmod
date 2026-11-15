package com.example.roomsmod.entity.u;

import com.example.roomsmod.entity.gk.GKRoomWatcher;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * U-160（振り返るな）。開けた廊下で、入ってきた側（部屋の入口）を見つめ続けると攻撃。前を向いて進めばよい。
 * S-205 / R-35（実体を見ない）とは違い、「来た方向を見ない」。
 */
public class ULookbackEntity extends GKRoomWatcher {
    public static final int TELEGRAPH_TICKS = 35, WATCH_TICKS = 160, LIMIT = 24, HARD_LIMIT = 16;
    public static final float DAMAGE = 25f;
    public static final double LOOK_DOT = 0.72;

    public ULookbackEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-160"; }
    @Override public String homeSection() { return "u"; }
    @Override protected int telegraphTicks() { return hardSpawn ? 24 : TELEGRAPH_TICKS; }
    @Override protected int watchTicks() { return WATCH_TICKS; }
    @Override protected int limit() { return hardSpawn ? HARD_LIMIT : LIMIT; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent cue() { return SoundEvents.AMBIENT_CAVE.value(); }
    @Override protected int cueInterval() { return 50; }
    @Override protected String chatStart() { return "U-160: 振り返るな"; }

    @Override
    protected boolean inMyRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    @Override
    protected boolean violates(Player p) {
        if (!(level() instanceof ServerLevel sl)) return false;
        List<BlockPos> path = RoomsState.get(sl).path(startRoom);
        if (path.isEmpty()) return false;
        BlockPos entrance = path.get(0);
        Vec3 to = new Vec3(entrance.getX() + 0.5, p.getEyeY(), entrance.getZ() + 0.5).subtract(p.getEyePosition());
        if (to.horizontalDistanceSqr() < 1) return false; // 入口のすぐそばは判定しない
        return p.getViewVector(1f).dot(to.normalize()) > LOOK_DOT;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (isRemoved()) return;
        if (phaseTick == telegraphTicks() + 1)
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.u160_warn"));
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.4f, 0.35f, 0.5f), 1.0f);
        if (tickCount % 5 == 0) level().addParticle(d, getX(), getY() + 1.5, getZ(), 0, 0, 0);
    }
}
