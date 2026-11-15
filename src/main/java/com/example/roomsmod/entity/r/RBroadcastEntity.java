package com.example.roomsmod.entity.r;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R-150（深夜放送）。リビングのテレビ（黒いコンクリ＋灰色のガラス板）が砂嵐になり、画面を見つめ続けると攻撃。
 * R-35（部屋の隅の顔を見ない）とは対象が違う。テレビのない部屋では出現しない。
 */
public class RBroadcastEntity extends VEntity {
    public static final int TELEGRAPH = 30, WATCH = 160, LIMIT = 25, HARD_LIMIT = 16;
    public static final float DAMAGE = 25f;
    public static final double LOOK_DOT = 0.82;
    private int phase = 0;
    private BlockPos screen;
    private final Map<UUID, Integer> stares = new HashMap<>();

    public RBroadcastEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-150"; }
    @Override public String homeSection() { return "r"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int limit() { return hardSpawn ? HARD_LIMIT : LIMIT; }

    static List<BlockPos> tvs(ServerLevel level, RoomsState rs, int room) {
        List<BlockPos> out = new ArrayList<>();
        if (room < 0 || room >= rs.generatedRooms.size()) return out;
        for (BoundingBox b : rs.boxes(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY() + 1, b.minZ(), b.maxX(), b.maxY() - 1, b.maxZ() - 1)) {
                var s = level.getBlockState(q);
                if (s.is(Blocks.GRAY_STAINED_GLASS_PANE) || s.is(Blocks.BLACK_CONCRETE)) out.add(q.immutable());
            }
        out.sort((a, b) -> Boolean.compare(level.getBlockState(b).is(Blocks.GRAY_STAINED_GLASS_PANE), level.getBlockState(a).is(Blocks.GRAY_STAINED_GLASS_PANE)));
        return out;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> found = tvs(level, rs, playerRoom);
        if (found.isEmpty()) found = tvs(level, rs, Math.max(0, rs.roomPaths.size() - 1));
        if (found.isEmpty()) { RoomsDebug.log("{} spawn skipped: no TV in room {}", vName(), playerRoom); return null; }
        screen = found.get(0);
        setPos(screen.getX() + 0.5, screen.getY(), screen.getZ() + 0.5);
        return screen;
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    private boolean looking(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = new Vec3(getX(), getY() + 0.5, getZ()).subtract(eye);
        if (to.lengthSqr() < 1e-6) return true;
        return p.getViewVector(1f).dot(to.normalize()) > LOOK_DOT;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.r150_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "R-150: テレビが砂嵐になった", "{} start room={} screen={}", vName(), startRoom, screen);
        }
        if (phase < TELEGRAPH) return;
        if (phase % 15 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 0.8f, 0.4f + random.nextFloat() * 0.8f);
        if (phase > TELEGRAPH + WATCH) { leave("broadcast over"); return; }
        for (Player p : playersNear(40)) {
            if (p.isSpectator() || !inRoom(level, p) || !looking(p)) continue;
            int n = stares.merge(p.getUUID(), 1, Integer::sum);
            if (n == 1 || n % 5 == 0) RoomsDebug.log(p, "R-150: 画面を見ている " + n + "/" + limit(), "{} stare player={} {}/{}", vName(), p.getGameProfile().getName(), n, limit());
            if (n >= limit()) {
                RoomsDamage.hurt(this, p, DAMAGE);
                leave("stared " + p.getGameProfile().getName());
                return;
            }
        }
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.85f, 0.85f, 0.9f), 1.0f);
        if (tickCount % 2 == 0) level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.3 + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " screen=" + screen; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
