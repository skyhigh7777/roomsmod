package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.example.roomsmod.world.SafeSpot;

import java.util.List;
import java.util.UUID;

/**
 * S-65：次の出口扉を施錠し、部屋のどこかに「通行証」を落とす。拾えば解錠（時間切れでも解錠）。
 * 通行証はこの個体専用（タグに持ち主の UUID）。別の S-65 の通行証では開かない（複数いても取り合わない）。
 * 同じ扉をすでに別の S-65 が施錠しているなら出現しない（3!3!2 の召喚でも）。
 * 置き場所は「経路を塞がない床」→「部屋の安全な床」→「経路の上」の順で探し、どこにも置けなければ施錠しない。
 * 施錠中に通行証が無くなった（拾われずに消えた・落として消えた等）ら置き直し、置けなければ解錠して去る。
 */
public class GKDetentionEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int LOCK_TICKS = 20 * 80;
    public static final String TAG = "roomsmod_gk_pass";
    /** 通行証の持ち主（S-65 の UUID）を入れるタグ。無い通行証（旧版）はどの S-65 でも使える。 */
    public static final String OWNER_TAG = "roomsmod_gk_pass_owner";
    /** 通行証がどこにも見つからない状態がこの tick 数続いたら置き直す。 */
    public static final int PASS_MISSING_TICKS = 40;
    /** 通行証を探す範囲（S-65 からの距離）。 */
    public static final double PASS_SEARCH_RADIUS = 96;
    private BlockPos door = null;
    private UUID passItem = null;
    private int missingTicks = 0;
    public GKDetentionEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-65"; }
    @Override protected int maxLifeTicks() { return LOCK_TICKS; }

    static boolean isPass(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(TAG);
    }

    /** この個体の通行証か（持ち主タグの無い旧版の通行証も可）。 */
    boolean isMyPass(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null || !d.contains(TAG)) return false;
        CompoundTag t = d.copyTag();
        return !t.hasUUID(OWNER_TAG) || t.getUUID(OWNER_TAG).equals(getUUID());
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = rs.roomPaths.size() - 1;
        door = rs.expectedExitDoor;
        if (door == null || rs.generatedDoors.contains(door)) { RoomsDebug.log("S-65 spawn skipped: no unopened exit door ({})", door); return null; }
        for (Entity e : level.getAllEntities())
            if (e instanceof GKDetentionEntity o && o != this && o.isAlive() && o.guards(door)) {
                RoomsDebug.log("S-65 spawn skipped: door {} already locked by another S-65", door); return null;
            }
        if (!dropPass(level, rs)) { RoomsDebug.log("S-65 spawn skipped: no spot for the pass in room {} (door not locked)", startRoom); return null; }
        BlockPos stand = door.north();
        setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        RoomsDebug.log("S-65 locked {} room={}", door, startRoom);
        return stand;
    }

    /** 通行証の置き場所：経路を塞がない床 → 部屋の安全な床 → 経路の上（入口寄りも含む）。無ければ null。 */
    private BlockPos passSpot(ServerLevel level, RoomsState rs) {
        List<BlockPos> s = GKUtil.freeSpots(level, rs, startRoom, level.random, 1);
        if (!s.isEmpty()) { RoomsDebug.log("S-65 pass spot: free floor {}", s.get(0)); return s.get(0); }
        Vec3 v = SafeSpot.findRandom(level, rs, startRoom, level.random);
        if (v != null) { RoomsDebug.log("S-65 pass spot: fallback room floor {}", v); return BlockPos.containing(v); }
        v = SafeSpot.find(level, rs, startRoom);
        if (v != null) { RoomsDebug.log("S-65 pass spot: fallback path {}", v); return BlockPos.containing(v); }
        return null;
    }

    /** この個体の通行証を部屋に落とす。置けなければ false。 */
    private boolean dropPass(ServerLevel level, RoomsState rs) {
        BlockPos at = passSpot(level, rs);
        if (at == null) return false;
        ItemStack pass = new ItemStack(Items.PAPER);
        pass.set(DataComponents.CUSTOM_NAME, Component.translatable("item.roomsmod.gk_hall_pass"));
        CompoundTag tag = new CompoundTag(); tag.putBoolean(TAG, true); tag.putUUID(OWNER_TAG, getUUID());
        pass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        ItemEntity it = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, pass, 0, 0, 0);
        it.setUnlimitedLifetime(); it.setGlowingTag(true);
        if (!level.addFreshEntity(it)) { RoomsDebug.warn("S-65 pass drop failed at {}", at); return false; }
        if (passItem != null) { Entity old = level.getEntity(passItem); if (old != null && old != it) old.discard(); }
        passItem = it.getUUID();
        missingTicks = 0;
        RoomsDebug.log("S-65 pass at {} (room {}) door={}", at, startRoom, door);
        return true;
    }

    /** 通行証がまだどこかにあるか（落ちている・誰かが持っている）。 */
    private boolean passExists(ServerLevel level) {
        if (passItem != null) { Entity e = level.getEntity(passItem); if (e != null && e.isAlive()) return true; }
        for (Player p : level.players()) {
            if (isMyPass(p.containerMenu.getCarried())) return true;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (isMyPass(inv.getItem(i))) return true;
        }
        return !level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(PASS_SEARCH_RADIUS), it -> it.isAlive() && isMyPass(it.getItem())).isEmpty();
    }

    public boolean guards(BlockPos p) { return door != null && door.equals(p); }

    public void punish(ServerLevel level, Player p) {
        level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
        GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.gk_detention_locked"));
        RoomsDebug.log(p, "S-65: 施錠中（通行証を探して）", "S-65 blocked door {} player={}", door, p.getGameProfile().getName());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (life % 40 == 1 && door != null) level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        if (door == null) { leave("no door"); return; }
        for (Player p : level.players()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++)
                if (isMyPass(inv.getItem(i))) {
                    inv.setItem(i, ItemStack.EMPTY);
                    level.playSound(null, door, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5f, 1.2f);
                    GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.gk_detention_open"));
                    RoomsDebug.log(p, "S-65: 通行証で解錠したわ", "S-65 unlocked by pass player={} door={}", p.getGameProfile().getName(), door);
                    leave("pass used");
                    return;
                }
        }
        // 安全策：通行証が無くなったら置き直す。置けなければ解錠して去る（ソフトロック防止）
        if (life % 10 == 0) {
            if (passExists(level)) missingTicks = 0;
            else if ((missingTicks += 10) >= PASS_MISSING_TICKS) {
                RoomsDebug.log("S-65 pass missing for {} ticks, re-dropping (door={})", missingTicks, door);
                if (!dropPass(level, RoomsState.get(level))) { leave("pass lost and no spot to re-drop (unlocked)"); return; }
            }
        }
    }

    @Override protected void leave(String why) {
        if (passItem != null && level() instanceof ServerLevel l) { Entity e = l.getEntity(passItem); if (e != null) e.discard(); }
        super.leave(why);
    }
    @Override protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.CRIT, getX(), getY() + 1 + random.nextDouble(), getZ(), 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door + " pass=" + passItem + " missing=" + missingTicks; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); if (passItem != null) t.putUUID("Pass", passItem); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); if (t.hasUUID("Pass")) passItem = t.getUUID("Pass"); }
}
