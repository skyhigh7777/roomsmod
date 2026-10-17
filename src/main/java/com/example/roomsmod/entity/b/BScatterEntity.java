package com.example.roomsmod.entity.b;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** B-Scatter。 */
public class BScatterEntity extends BRoomEntity {
    public static final int PIECES = 4, TIME = 1000;
    public static final double PICK = 1.6;
    private final List<BlockPos> pieces = new ArrayList<>();
    public BScatterEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Scatter"; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        BlockPos at = super.prepare(level, rs, playerRoom);
        if (at == null) return null;
        for (int i = 0; i < 20 && pieces.size() < PIECES; i++) {
            Vec3 v = SafeSpot.findRandom(level, rs, playerRoom, level.random);
            if (v == null) continue;
            BlockPos b = BlockPos.containing(v);
            if (b.closerThan(at, 2.5) || pieces.stream().anyMatch(q -> q.closerThan(b, 2.5))) continue;
            pieces.add(b);
        }
        if (pieces.size() < 2) { RoomsDebug.log("B-Scatter spawn skipped: only {} piece spots", pieces.size()); return null; }
        RoomsDebug.log("B-Scatter pieces={}", pieces);
        return at;
    }

    public List<BlockPos> pieces() { return pieces; }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.5f, 0.6f);
            RoomsDebug.announce(playersNear(48), "B-Scatter が砕け散ったわ（欠片 " + pieces.size() + " 個、" + TIME / 20 + "秒）", "B-Scatter telegraph room={} pieces={}", startRoom, pieces.size());
        }
        if (phase % 5 == 0) {
            DustParticleOptions d = new DustParticleOptions(new Vector3f(0.05f, 0.05f, 0.05f), 2.0f);
            for (BlockPos b : pieces) level.sendParticles(d, b.getX() + 0.5, b.getY() + 0.8, b.getZ() + 0.5, 6, 0.25, 0.3, 0.25, 0);
        }
        if (phase % 40 == 0 && !pieces.isEmpty()) level.playSound(null, pieces.get(0), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.2f, 0.5f);
        for (Player p : roomPlayers(level, 48, true))
            pieces.removeIf(b -> {
                boolean got = p.position().distanceTo(Vec3.atBottomCenterOf(b)) < PICK;
                if (got) RoomsDebug.log(p, "B-Scatter の欠片を拾ったわ", "B-Scatter piece picked player={} at={} left={}", p.getGameProfile().getName(), b, pieces.size() - 1);
                return got;
            });
        if (pieces.isEmpty()) { level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f); leave("all pieces collected"); return; }
        if (phase >= TIME) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 2f, 0.5f);
            for (Player p : roomPlayers(level, 40, true)) { RoomsDebug.log(p, "B-Scatter が叫んだわ", "B-Scatter scream player={} left={}", p.getGameProfile().getName(), pieces.size()); hit(p, 20f); }
            leave("time over");
        }
    }

    @Override protected String debugState() { return super.debugState() + " pieces=" + pieces.size(); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        ListTag l = new ListTag(); for (BlockPos b : pieces) l.add(NbtUtils.writeBlockPos(b)); t.put("Pieces", l);
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        pieces.clear();
        ListTag l = t.getList("Pieces", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < l.size(); i++) pieces.add(new BlockPos(l.getIntArray(i)[0], l.getIntArray(i)[1], l.getIntArray(i)[2]));
    }
}
