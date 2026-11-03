package com.example.roomsmod.entity.orb;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * 回復のオーブの設置（オーバーワールドに 1 つ）。サーバー起動時に、まだ置いていなければワールドスポーン地点の {@value #AHEAD} ブロック先
 * （スポーンの向き、地面の上）に置く。置いたこと・位置・UUID はオーバーワールドの SavedData（{@value #NAME}）に保存するので、再起動しても増えない。
 * 置き直し・消すのは /rooms admin orb。オーブのマスに光源（light ブロック、明るさ 15）を、空気のときだけ置く（建物は壊さない）。
 */
public final class HealingOrb extends SavedData {
    public static final String NAME = "roomsmod_healing_orb";
    public static final int AHEAD = 4;

    boolean placed = false;
    BlockPos pos = null;
    UUID id = null;

    static HealingOrb get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HealingOrb::new, HealingOrb::load, null), NAME);
    }

    static HealingOrb load(CompoundTag t, HolderLookup.Provider p) {
        HealingOrb o = new HealingOrb();
        o.placed = t.getBoolean("placed");
        if (t.contains("x")) o.pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
        if (t.hasUUID("id")) o.id = t.getUUID("id");
        return o;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        t.putBoolean("placed", placed);
        if (pos != null) { t.putInt("x", pos.getX()); t.putInt("y", pos.getY()); t.putInt("z", pos.getZ()); }
        if (id != null) t.putUUID("id", id);
        return t;
    }

    static void log(String fmt, Object... args) { RoomsDebug.info(fmt, args); }

    /** このオーブが今のオーブか（オーバーワールドで、保存された UUID と同じ）。 */
    static boolean isCurrent(ServerLevel level, HealingOrbEntity e) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        HealingOrb o = get(level);
        return o.placed && e.getUUID().equals(o.id);
    }

    /** サーバー起動時：まだ置いていなければ置く（置いてあれば何もしない）。 */
    public static void ensure(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        HealingOrb o = get(ow);
        if (o.placed) return;
        place(ow, null);
    }

    /** 置く（at が null なら既定の位置：スポーン地点の先）。前のオーブは保存の UUID が変わるので、読み込まれたときに自分で消える。 */
    public static BlockPos place(ServerLevel ow, BlockPos at) {
        HealingOrb o = get(ow);
        removeLoaded(ow, o);
        BlockPos ground = at != null ? at : defaultSpot(ow);
        HealingOrbEntity e = RoomsMod.HEALING_ORB.get().create(ow);
        if (e == null) { RoomsDebug.warn("healing orb: create failed"); return null; }
        e.moveTo(ground.getX() + 0.5, ground.getY() + 0.9, ground.getZ() + 0.5, 0, 0);
        o.placed = true; o.pos = ground; o.id = e.getUUID();
        o.setDirty();
        ow.addFreshEntity(e);
        BlockPos lp = ground; // オーブのいるマス
        if (ow.getBlockState(lp).isAir()) ow.setBlockAndUpdate(lp, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        log("healing orb placed at {} (world spawn {})", ground, ow.getSharedSpawnPos());
        return ground;
    }

    /** 消す（置いた光源も）。もう自動では置かない（/rooms admin orb place で戻す）。 */
    public static boolean remove(ServerLevel ow) {
        HealingOrb o = get(ow);
        boolean had = o.placed;
        removeLoaded(ow, o);
        o.placed = true; // 自動で置き直さない
        o.id = null;
        o.pos = null;
        o.setDirty();
        log("healing orb removed (had={})", had);
        return had;
    }

    public static String describe(ServerLevel ow) {
        HealingOrb o = get(ow);
        if (o.id == null || o.pos == null) return "なし";
        return o.pos.getX() + " " + o.pos.getY() + " " + o.pos.getZ();
    }

    private static void removeLoaded(ServerLevel ow, HealingOrb o) {
        if (o.pos != null) {
            BlockPos lp = o.pos;
            if (ow.isLoaded(lp) && ow.getBlockState(lp).is(Blocks.LIGHT)) ow.setBlockAndUpdate(lp, Blocks.AIR.defaultBlockState());
        }
        for (var e : ow.getEntities().getAll()) if (e instanceof HealingOrbEntity) e.discard();
    }

    /** スポーン地点から、スポーンの向きに AHEAD ブロック先の地面（固い・液体でない）。だめなら近くを探し、それもだめならスポーン地点。 */
    static BlockPos defaultSpot(ServerLevel ow) {
        BlockPos sp = ow.getSharedSpawnPos();
        float yaw = ow.getSharedSpawnAngle();
        int ax = sp.getX() + Math.round(-Mth.sin(yaw * Mth.DEG_TO_RAD) * AHEAD), az = sp.getZ() + Math.round(Mth.cos(yaw * Mth.DEG_TO_RAD) * AHEAD);
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                BlockPos g = groundAt(ow, ax + dx, az + dz);
                if (g != null) return g;
            }
        BlockPos g = groundAt(ow, sp.getX(), sp.getZ());
        return g != null ? g : sp;
    }

    /** (x, z) の一番上の地面の上のマス（足元が固く液体でない、そのマスと上が空き）。なければ null。 */
    private static BlockPos groundAt(ServerLevel ow, int x, int z) {
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockState below = ow.getBlockState(p.below());
        if (!below.getFluidState().isEmpty() || !below.isFaceSturdy(ow, p.below(), Direction.UP)) return null;
        if (!ow.getBlockState(p).isAir() || !ow.getBlockState(p.above()).isAir()) return null;
        return p;
    }
}
