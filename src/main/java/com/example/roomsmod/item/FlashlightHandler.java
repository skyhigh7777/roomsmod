package com.example.roomsmod.item;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * 懐中電灯の電池消費と光。
 * - ON かつメイン/オフハンドに持っている間だけ消費・発光。持っていない間は消費も光も止まる（ON のまま、持ち直すと再開）。
 * - 残り 0 で持ち物の電池を自動装填。無ければ OFF にしてカチッ。
 * - 光は見えない光源ブロック（FlashlightLightBlock、明るさ10）を視線方向の空気に置く。プレイヤーごとに位置を記録し、
 *   OFF・持ち替え・ログアウト・死亡・ディメンション移動・サーバー停止で全部消す。空気以外は置き換えない。
 */
public final class FlashlightHandler {
    private FlashlightHandler() {}
    /** 光の明るさ。 */
    public static final int BRIGHTNESS = 10;
    /** 光を置く最大距離（ブロック）。 */
    public static final double BEAM_RANGE = 9.0;
    /** 中心線のサンプル間隔（ブロック）。 */
    public static final double BEAM_STEP = 2.5;
    /** 広がり：距離 d で横/縦に d*BEAM_SPREAD ずらしたサンプルを追加（約11度）。 */
    public static final double BEAM_SPREAD = 0.2;
    /** 横のサンプルを始める距離。 */
    public static final double SPREAD_FROM = 4.0;
    /** 光の更新間隔（tick）。 */
    public static final int BEAM_UPDATE_TICKS = 3;
    /** ON 中のアクションバー表示間隔（tick）。 */
    public static final int ACTIONBAR_TICKS = 10;
    static final String GIVEN_KEY = "roomsmod_flashlight_given";

    private record Beam(ResourceKey<Level> dim, Set<BlockPos> lights) {}
    private static final Map<UUID, Beam> BEAMS = new HashMap<>();

    /** pos がいずれかのプレイヤーの光として管理中か（取り残しの自動削除用）。 */
    public static boolean owns(ServerLevel level, BlockPos pos) {
        for (Beam b : BEAMS.values()) if (b.dim().equals(level.dimension()) && b.lights().contains(pos)) return true;
        return false;
    }

    public static int lightCount(Player p) { Beam b = BEAMS.get(p.getUUID()); return b == null ? 0 : b.lights().size(); }
    public static Set<BlockPos> lights(Player p) { Beam b = BEAMS.get(p.getUUID()); return b == null ? Set.of() : Set.copyOf(b.lights()); }

    /** 手に持っている ON の懐中電灯（メイン優先）。 */
    public static ItemStack heldOn(Player p) {
        if (FlashlightItem.isOn(p.getMainHandItem())) return p.getMainHandItem();
        if (FlashlightItem.isOn(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) tick(sp);
    }

    public static void tick(ServerPlayer p) {
        giveFirstFlashlight(p);
        ItemStack s = p.isAlive() && !p.isSpectator() ? heldOn(p) : ItemStack.EMPTY;
        if (s.isEmpty()) { clear(p); return; }
        FlashlightData d = FlashlightItem.data(s);
        if (d.charge() <= 0 && !FlashlightItem.loadBattery(p, s)) {
            FlashlightItem.setData(s, d.withOn(false));
            FlashlightItem.click(p, false);
            p.displayClientMessage(Component.translatable("item.roomsmod.flashlight.dead"), true);
            RoomsDebug.log("flashlight battery empty, no spare -> off player={}", p.getGameProfile().getName());
            clear(p);
            return;
        }
        if (d.charge() <= 0) RoomsDebug.log("flashlight auto-loaded battery player={}", p.getGameProfile().getName());
        d = FlashlightItem.data(s);
        int c = d.charge() - 1;
        FlashlightItem.setData(s, d.withCharge(c));
        if (p.tickCount % ACTIONBAR_TICKS == 0 && com.example.roomsmod.util.RoomsHud.batteryAllowed(p))
            p.displayClientMessage(Component.translatable("item.roomsmod.flashlight.actionbar", FlashlightItem.time(c)), true);
        Beam b = BEAMS.get(p.getUUID());
        if (b != null && !b.dim().equals(p.level().dimension())) { clear(p); b = null; }
        if (b == null || p.tickCount % BEAM_UPDATE_TICKS == 0) updateBeam(p);
    }

    private static boolean placeable(BlockState st) {
        return st.isAir() || st.is(RoomsMod.FLASHLIGHT_LIGHT.get());
    }

    static void updateBeam(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0f);
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(BEAM_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double max = hit.getLocation().distanceTo(eye) - 0.3;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        Set<BlockPos> want = new HashSet<>();
        List<Vec3> pts = new ArrayList<>();
        for (double d = 1.5; d <= BEAM_RANGE; d += BEAM_STEP) {
            Vec3 c = eye.add(look.scale(Math.min(d, Math.max(0.8, max))));
            pts.add(c);
            if (d >= SPREAD_FROM) {
                double w = d * BEAM_SPREAD;
                pts.add(c.add(right.scale(w))); pts.add(c.subtract(right.scale(w)));
                pts.add(c.add(up.scale(w))); pts.add(c.subtract(up.scale(w)));
            }
            if (d >= max) break;
        }
        for (Vec3 v : pts) {
            BlockPos q = BlockPos.containing(v);
            if (!level.isLoaded(q)) continue;
            if (!placeable(level.getBlockState(q))) {
                q = BlockPos.containing(v.subtract(look.scale(0.8))); // 壁や家具の手前
                if (!placeable(level.getBlockState(q))) continue;
            }
            if (q.equals(BlockPos.containing(eye)) || q.equals(p.blockPosition())) continue;
            want.add(q.immutable());
        }
        Beam b = BEAMS.computeIfAbsent(p.getUUID(), k -> new Beam(level.dimension(), new HashSet<>()));
        for (Iterator<BlockPos> it = b.lights().iterator(); it.hasNext(); ) {
            BlockPos q = it.next();
            if (want.contains(q)) continue;
            it.remove();
            removeLight(level, q);
        }
        for (BlockPos q : want) if (b.lights().add(q) && level.getBlockState(q).isAir())
            level.setBlock(q, RoomsMod.FLASHLIGHT_LIGHT.get().defaultBlockState(), 3);
        b.lights().removeIf(q -> !level.getBlockState(q).is(RoomsMod.FLASHLIGHT_LIGHT.get()));
    }

    private static void removeLight(ServerLevel level, BlockPos q) {
        if (level.isLoaded(q) && level.getBlockState(q).is(RoomsMod.FLASHLIGHT_LIGHT.get())) level.removeBlock(q, false);
    }

    /** そのプレイヤーの光を全部消す。 */
    public static void clear(Player p) {
        Beam b = BEAMS.remove(p.getUUID());
        if (b == null || p.getServer() == null) return;
        clear(p.getServer(), b);
    }

    private static void clear(MinecraftServer server, Beam b) {
        ServerLevel level = server.getLevel(b.dim());
        if (level != null) for (BlockPos q : b.lights()) removeLight(level, q);
    }

    /** 部屋ディメンションに初めて入ったプレイヤーに、電池1本分充電済みの懐中電灯を渡す（1回だけ）。 */
    static void giveFirstFlashlight(ServerPlayer p) {
        if (p.tickCount % 20 != 0 || !p.level().dimension().equals(com.example.roomsmod.command.RoomsCommands.ROOMS_DIMENSION)) return;
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_KEY)) return;
        persisted.putBoolean(GIVEN_KEY, true);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack s = newFlashlight(FlashlightItem.BATTERY_TICKS);
        if (!p.getInventory().add(s)) p.drop(s, false);
        RoomsDebug.log("flashlight given (first entry) player={}", p.getGameProfile().getName());
    }

    public static ItemStack newFlashlight(int charge) {
        ItemStack s = new ItemStack(RoomsMod.FLASHLIGHT.get());
        FlashlightItem.setData(s, new FlashlightData(charge, false));
        return s;
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity()); }
    @SubscribeEvent public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        Beam b = BEAMS.remove(e.getEntity().getUUID());
        if (b != null && e.getEntity().getServer() != null) clear(e.getEntity().getServer(), b);
    }
    @SubscribeEvent public static void onDeath(LivingDeathEvent e) { if (e.getEntity() instanceof Player p) clear(p); }
    @SubscribeEvent public static void onStopping(ServerStoppingEvent e) {
        for (Beam b : BEAMS.values()) clear(e.getServer(), b);
        BEAMS.clear();
    }
}
