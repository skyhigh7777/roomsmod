package com.example.roomsmod.stamina;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 部屋ディメンション（ロビー含む）だけ、満腹度の代わりにスタミナ（0〜100、サーバーが正）を使う。
 * バニラの満腹度は FOOD_PIN に固定（自然回復も餓死もしない）。息切れ中は 6 に固定して、バニラ自身にダッシュを禁止させる。
 * 入るときに外の満腹度を保存し、出るときに戻す。
 */
public final class Stamina {
    private Stamina() {}

    public static final float MAX = 100f;
    public static final float DRAIN = MAX / 160f;        // ダッシュ 8 秒で 0
    public static final float REGEN_WALK = MAX / 120f;   // 歩きで 6 秒で満タン
    public static final float REGEN_IDLE = MAX / 80f;    // 静止で 4 秒で満タン
    public static final float RESUME = 25f;              // 息切れ後、ここまで戻るとダッシュ可
    public static final int FOOD_PIN = 17, FOOD_PIN_EXHAUSTED = 6;

    public record SavedFood(int food, float saturation, float exhaustion) {
        static final Codec<SavedFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("food").forGetter(SavedFood::food),
                Codec.FLOAT.fieldOf("saturation").forGetter(SavedFood::saturation),
                Codec.FLOAT.fieldOf("exhaustion").forGetter(SavedFood::exhaustion)).apply(i, SavedFood::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RoomsMod.MOD_ID);
    public static final Supplier<AttachmentType<Float>> STAMINA = ATTACHMENTS.register("stamina",
            () -> AttachmentType.builder(() -> MAX).serialize(Codec.FLOAT).build());
    public static final Supplier<AttachmentType<Boolean>> EXHAUSTED = ATTACHMENTS.register("stamina_exhausted",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());
    /** 外の満腹度。null 相当は「保存なし」= SavedFood(-1,…)。死亡でも引き継ぐ。 */
    public static final Supplier<AttachmentType<SavedFood>> OUTSIDE_FOOD = ATTACHMENTS.register("outside_food",
            () -> AttachmentType.builder(() -> new SavedFood(-1, 0, 0)).serialize(SavedFood.CODEC).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> { ClientStamina.value = msg.value(); ClientStamina.exhausted = msg.exhausted(); })));
    }

    private static final java.util.Map<java.util.UUID, double[]> LAST_POS = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean inRooms(Player p) { return p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION); }

    private static void pin(ServerPlayer p, boolean exhausted) {
        FoodData fd = p.getFoodData();
        fd.setFoodLevel(exhausted ? FOOD_PIN_EXHAUSTED : FOOD_PIN);
        fd.setSaturation(0f);
        fd.setExhaustion(0f);
    }

    /** 部屋の内外で満腹度を保存・復元し、部屋の中では固定する。 */
    private static boolean syncFood(ServerPlayer p) {
        SavedFood saved = p.getData(OUTSIDE_FOOD);
        boolean has = saved.food() >= 0;
        if (inRooms(p)) {
            if (!has) {
                FoodData fd = p.getFoodData();
                p.setData(OUTSIDE_FOOD, new SavedFood(fd.getFoodLevel(), fd.getSaturationLevel(), fd.getExhaustionLevel()));
                p.setData(STAMINA.get(), MAX);
                p.setData(EXHAUSTED.get(), false);
                RoomsDebug.log("stamina on player={} saved food={} sat={}", p.getGameProfile().getName(), fd.getFoodLevel(), fd.getSaturationLevel());
            }
            return true;
        }
        if (has) {
            FoodData fd = p.getFoodData();
            fd.setFoodLevel(saved.food());
            fd.setSaturation(saved.saturation());
            fd.setExhaustion(saved.exhaustion());
            p.setData(OUTSIDE_FOOD, new SavedFood(-1, 0, 0));
            RoomsDebug.log("stamina off player={} restored food={} sat={}", p.getGameProfile().getName(), saved.food(), saved.saturation());
        }
        return false;
    }

    @SubscribeEvent
    public static void onTickPre(PlayerTickEvent.Pre e) {
        if (e.getEntity() instanceof ServerPlayer p && syncFood(p)) pin(p, p.getData(EXHAUSTED));
    }

    @SubscribeEvent
    public static void onTickPost(PlayerTickEvent.Post e) {
        Player pl = e.getEntity();
        if (pl.level().isClientSide()) {
            // 保険（クリエイティブは満腹度に関係なくダッシュできるので）：息切れ中はクライアントでもダッシュを止める
            if (inRooms(pl) && ClientStamina.exhausted && pl.isSprinting() && !pl.isSpectator()) pl.setSprinting(false);
            return;
        }
        if (!(pl instanceof ServerPlayer p) || !syncFood(p)) return;
        float s = p.getData(STAMINA), before = s;
        boolean ex = p.getData(EXHAUSTED), exBefore = ex;
        if (p.isSpectator()) { s = MAX; ex = false; }
        else {
            double[] last = LAST_POS.get(p.getUUID());
            double dx = last == null ? 0 : p.getX() - last[0], dz = last == null ? 0 : p.getZ() - last[1];
            LAST_POS.put(p.getUUID(), new double[]{p.getX(), p.getZ()});
            boolean moving = dx * dx + dz * dz > 1.0e-4;
            boolean flying = p.getAbilities().flying || p.isPassenger() || p.isFallFlying();
            if (p.isSprinting() && moving && !flying) s -= DRAIN;
            else if (!p.isSprinting()) s += moving ? REGEN_WALK : REGEN_IDLE;
            s = Math.max(0f, Math.min(MAX, s));
            if (s <= 0f && !ex) { ex = true; RoomsDebug.log("stamina exhausted player={}", p.getGameProfile().getName()); }
            if (ex && s >= RESUME) ex = false;
            if (ex && p.isSprinting()) p.setSprinting(false);
        }
        if (s != before) p.setData(STAMINA, s);
        if (ex != exBefore) p.setData(EXHAUSTED, ex);
        pin(p, ex);
        if (Math.round(s) != Math.round(before) || ex != exBefore || p.tickCount % 20 == 0)
            PacketDistributor.sendToPlayer(p, new StaminaPayload(s, ex));
    }
}
