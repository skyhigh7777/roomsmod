package com.example.roomsmod.entity.orb;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * 回復のオーブの設置（部屋ディメンションのロビーに 1 つ）。まだ置いていなければ、ロビーのスポーン（10.5, LOBBY_Y+1, 16.5、出口扉の向き +Z）の
 * {@value #AHEAD} ブロック先＝中央の通路の上（ブロック (10, LOBBY_Y+1, 20)）に置く。オーブも光源（light ブロック）も当たり判定がないので
 * 通路・扉・パネル・売店の邪魔にならない。置いたこと・位置・UUID は部屋ディメンションの SavedData（{@value #NAME}）に保存（再起動しても増えない）。
 * ロビーを作り直すと中身（光源）が消えるので、作り直しの後とロビーに人がいる間（2 秒ごと）に光源とオーブ本体を戻す（{@link #lobbyCheck}）。
 * 以前のオーバーワールドのオーブ（{@value #OLD_NAME}）は起動時に光源ごと片付ける。置き直し・消すのは /rooms admin orb。
 */
public final class HealingOrb extends SavedData {
    public static final String NAME = "roomsmod_lobby_orb";
    /** 以前（オーバーワールドに置いていた頃）の SavedData。 */
    static final String OLD_NAME = "roomsmod_healing_orb";
    public static final int AHEAD = 4;

    boolean placed = false;
    BlockPos pos = null;
    UUID id = null;

    static HealingOrb data(ServerLevel level, String name) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HealingOrb::new, HealingOrb::load, null), name);
    }

    static HealingOrb get(ServerLevel rooms) { return data(rooms, NAME); }

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

    public static boolean isRooms(ServerLevel level) { return level.dimension().equals(RoomsCommands.ROOMS_DIMENSION); }

    /** このオーブが今のオーブか（部屋ディメンションで、保存された UUID と同じ）。 */
    static boolean isCurrent(ServerLevel level, HealingOrbEntity e) {
        if (!isRooms(level)) return false;
        HealingOrb o = get(level);
        return o.placed && e.getUUID().equals(o.id);
    }

    /** 既定の位置（ロビーのスポーンの AHEAD ブロック先、出口扉の向き）。オーブのいるマス。 */
    public static BlockPos defaultSpot() {
        return BlockPos.containing(RoomsLayout.SPAWN_X, RoomsLayout.SPAWN_Y, RoomsLayout.SPAWN_Z + AHEAD);
    }

    /** ロビーの中（壁の内側、床の上）か。 */
    public static boolean inLobby(BlockPos p) {
        BlockPos o = RoomsLayout.LOBBY_ORIGIN;
        int x = p.getX() - o.getX(), y = p.getY() - o.getY(), z = p.getZ() - o.getZ();
        return x >= 1 && x <= RoomsLayout.LOBBY_WIDTH - 2 && z >= 1 && z <= 30 && y >= 1 && y <= 4;
    }

    /** サーバー起動時：オーバーワールドの古いオーブを片付ける。ロビーへの設置は lobbyCheck（ロビーのチャンクが読み込まれている時）。 */
    public static void onServerStarted(MinecraftServer server) {
        cleanupOverworld(server.overworld());
        ServerLevel lv = server.getLevel(RoomsCommands.ROOMS_DIMENSION);
        if (lv != null) lobbyCheck(lv);
    }

    /** 以前オーバーワールドに置いたオーブの光源を消し、オーブ本体（読み込まれていれば）を消す。本体がまだ読み込まれていなくても、読み込まれた時に自分で消える。 */
    static void cleanupOverworld(ServerLevel ow) {
        HealingOrb old = data(ow, OLD_NAME);
        if (old.pos == null && old.id == null) return;
        BlockPos lp = old.pos;
        if (lp != null) {
            ow.getChunk(lp); // 読み込む（光源を確実に消す）
            if (ow.getBlockState(lp).is(Blocks.LIGHT)) ow.setBlockAndUpdate(lp, Blocks.AIR.defaultBlockState());
        }
        for (var e : ow.getEntities().getAll()) if (e instanceof HealingOrbEntity) e.discard();
        log("healing orb: removed old overworld orb at {} (id {})", lp, old.id);
        old.pos = null; old.id = null; old.placed = true;
        old.setDirty();
    }

    /**
     * ロビーの点検（ロビーを作り直した後・ロビーに人がいる間 2 秒ごと・起動時）。まだ置いていなければ置く（1 回だけ）。置いてあれば光源を戻し、
     * 本体が消えていたら（チャンクのエンティティが読み込み済みなのに見つからない）同じ位置に置き直す。ロビーのチャンクが読み込まれていなければ何もしない。
     */
    public static void lobbyCheck(ServerLevel lv) {
        if (!isRooms(lv)) return;
        HealingOrb o = get(lv);
        BlockPos at = o.placed ? o.pos : defaultSpot();
        if (at == null) return; // 管理コマンドで消した
        if (!lv.isLoaded(at) || !lv.areEntitiesLoaded(ChunkPos.asLong(at))) return;
        if (!o.placed) { place(lv, null); return; }
        if (lv.getBlockState(at).isAir()) lv.setBlockAndUpdate(at, light());
        if (o.id == null || lv.getEntity(o.id) == null) { log("healing orb missing at {} -> re-spawn", at); spawn(lv, o, at); }
    }

    private static net.minecraft.world.level.block.state.BlockState light() { return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15); }

    /** 置く（at が null ならロビーの既定の位置）。前のオーブは消す（読み込まれていないものも保存の UUID が変わるので、読み込まれた時に自分で消える）。 */
    public static BlockPos place(ServerLevel lv, BlockPos at) {
        HealingOrb o = get(lv);
        removeLoaded(lv, o);
        BlockPos spot = at != null ? at : defaultSpot();
        lv.getChunk(spot);
        if (!spawn(lv, o, spot)) return null;
        if (lv.getBlockState(spot).isAir()) lv.setBlockAndUpdate(spot, light());
        log("healing orb placed at {}", spot);
        return spot;
    }

    private static boolean spawn(ServerLevel lv, HealingOrb o, BlockPos spot) {
        HealingOrbEntity e = RoomsMod.HEALING_ORB.get().create(lv);
        if (e == null) { RoomsDebug.warn("healing orb: create failed"); return false; }
        e.moveTo(spot.getX() + 0.5, spot.getY() + 0.9, spot.getZ() + 0.5, 0, 0);
        o.placed = true; o.pos = spot; o.id = e.getUUID();
        o.setDirty();
        lv.addFreshEntity(e);
        return true;
    }

    /** 消す（置いた光源も）。もう自動では置かない（/rooms admin orb place で戻す）。 */
    public static boolean remove(ServerLevel lv) {
        HealingOrb o = get(lv);
        boolean had = o.id != null;
        removeLoaded(lv, o);
        o.placed = true; // 自動で置き直さない
        o.id = null;
        o.pos = null;
        o.setDirty();
        log("healing orb removed (had={})", had);
        return had;
    }

    public static String describe(ServerLevel lv) {
        HealingOrb o = get(lv);
        if (o.id == null || o.pos == null) return o.placed ? "なし（消してある）" : "まだ置いていない";
        return o.pos.getX() + " " + o.pos.getY() + " " + o.pos.getZ();
    }

    private static void removeLoaded(ServerLevel lv, HealingOrb o) {
        if (o.pos != null && lv.isLoaded(o.pos) && lv.getBlockState(o.pos).is(Blocks.LIGHT)) lv.setBlockAndUpdate(o.pos, Blocks.AIR.defaultBlockState());
        for (var e : lv.getEntities().getAll()) if (e instanceof HealingOrbEntity) e.discard();
    }
}
