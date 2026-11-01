package com.example.roomsmod.economy;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 電池・包帯・コインの所持数（インベントリの外、HUD に表示）。プレイヤーごとに保存し、クライアントへ同期する。
 * <ul>
 *   <li>上限は MAX_BATTERY / MAX_BANDAGE（{@value #UNLIMITED} = 上限なし。2026-11-01 は電池・包帯ともいったん上限なし。戻すなら 10 / 5）。コインは上限なし。</li>
 *   <li>床の電池・包帯は拾うとここに入る（いっぱいなら拾わない＝床に残る）。持ち物には存在させない：持ち物・カーソルにある電池・包帯（旧セーブ・チェスト・creative 等）は毎 tick 所持数へ入れ、上限を超えた分は消す。</li>
 *   <li>死ぬと電池・包帯はなくなる（以前の持ち物と同じ）。コインは残る。</li>
 *   <li>周回の記録（どの部屋から始めたか・どこまで進んだか・被弾したか）もここ（RoomsRun）。</li>
 * </ul>
 */
public final class Wallet implements INBTSerializable<CompoundTag> {
    /** 上限なしを表す値。 */
    public static final int UNLIMITED = -1;
    /** 所持数の上限（UNLIMITED = なし）。以前は 10 / 5。 */
    public static final int MAX_BATTERY = UNLIMITED, MAX_BANDAGE = UNLIMITED;
    /** アドレナリン・お守りの上限。 */
    public static final int MAX_ADRENALINE = 8, MAX_CHARM = 1;

    /** あと何個入るか。 */
    public static int space(int max, int cur) { return max < 0 ? Integer.MAX_VALUE - Math.max(0, cur) : Math.max(0, max - cur); }
    public static boolean full(int max, int cur) { return max >= 0 && cur >= max; }
    /** 表示用：上限なしなら "3"、あれば "3/10"。 */
    public static String label(int cur, int max) { return max < 0 ? String.valueOf(cur) : cur + "/" + max; }

    public int battery, bandage;
    /** アドレナリン・お守り（売店で買う。生還では残り、死亡でなくなる）。 */
    public int adrenaline, charm;
    public long coins;
    /** 周回（RoomsState.epoch）。違えば下の記録はリセット。 */
    public int runEpoch = Integer.MIN_VALUE;
    /** この周回で最初に入った部屋（-1 = まだ）と、いちばん先の部屋。 */
    public int runStart = -1, runFurthest = -1;
    public boolean runHit = false;

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider p) {
        CompoundTag t = new CompoundTag();
        t.putInt("battery", battery); t.putInt("bandage", bandage); t.putLong("coins", coins);
        t.putInt("adrenaline", adrenaline); t.putInt("charm", charm);
        t.putInt("runEpoch", runEpoch); t.putInt("runStart", runStart); t.putInt("runFurthest", runFurthest); t.putBoolean("runHit", runHit);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider p, CompoundTag t) {
        battery = t.getInt("battery"); bandage = t.getInt("bandage"); coins = t.getLong("coins");
        adrenaline = t.getInt("adrenaline"); charm = t.getInt("charm");
        runEpoch = t.contains("runEpoch") ? t.getInt("runEpoch") : Integer.MIN_VALUE;
        runStart = t.contains("runStart") ? t.getInt("runStart") : -1;
        runFurthest = t.contains("runFurthest") ? t.getInt("runFurthest") : -1;
        runHit = t.getBoolean("runHit");
    }

    public void resetRun(int epoch) { runEpoch = epoch; runStart = -1; runFurthest = -1; runHit = false; }

    // ===================== 登録・同期 =====================

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, RoomsMod.MOD_ID);
    public static final Supplier<AttachmentType<Wallet>> WALLET = ATTACHMENTS.register("wallet",
            () -> AttachmentType.serializable(Wallet::new).copyOnDeath().build());

    /** サーバー → クライアント：所持数。 */
    public record SyncPayload(int battery, int bandage, long coins, int adrenaline, int charm, int adrenalineTicks, int adrenalineCooldown) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "wallet"));
        public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.battery()); ByteBufCodecs.VAR_INT.encode(buf, m.bandage()); ByteBufCodecs.VAR_LONG.encode(buf, m.coins());
            ByteBufCodecs.VAR_INT.encode(buf, m.adrenaline()); ByteBufCodecs.VAR_INT.encode(buf, m.charm());
            ByteBufCodecs.VAR_INT.encode(buf, m.adrenalineTicks()); ByteBufCodecs.VAR_INT.encode(buf, m.adrenalineCooldown());
        }, buf -> new SyncPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント → サーバー：アドレナリンを使う（キー）。 */
    public record AdrenalinePayload() implements CustomPacketPayload {
        public static final Type<AdrenalinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "use_adrenaline"));
        public static final StreamCodec<ByteBuf, AdrenalinePayload> STREAM_CODEC = StreamCodec.unit(new AdrenalinePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント → サーバー：包帯を使う（キー）。 */
    public record BandagePayload(boolean start) implements CustomPacketPayload {
        public static final Type<BandagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "use_bandage"));
        public static final StreamCodec<ByteBuf, BandagePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, BandagePayload::start, BandagePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側の表示用。 */
    public static int clientBattery, clientBandage;
    public static long clientCoins;
    public static int clientAdrenaline, clientCharm, clientAdrenalineTicks, clientAdrenalineCooldown;

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> { clientBattery = msg.battery(); clientBandage = msg.bandage(); clientCoins = msg.coins();
                            clientAdrenaline = msg.adrenaline(); clientCharm = msg.charm(); clientAdrenalineTicks = msg.adrenalineTicks(); clientAdrenalineCooldown = msg.adrenalineCooldown(); }))
                .playToServer(AdrenalinePayload.TYPE, AdrenalinePayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Adrenaline.use(sp); }))
                .playToServer(BandagePayload.TYPE, BandagePayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Bandaging.request(sp, msg.start()); })));
    }

    public static Wallet of(Player p) { return p.getData(WALLET); }

    public static void sync(ServerPlayer p) {
        Wallet w = of(p);
        PacketDistributor.sendToPlayer(p, new SyncPayload(w.battery, w.bandage, w.coins, w.adrenaline, w.charm, Adrenaline.activeTicks(p), Adrenaline.cooldownTicks(p)));
    }

    /** 電池を1本使う（あれば true）。 */
    public static boolean takeBattery(ServerPlayer p) {
        Wallet w = of(p);
        if (w.battery <= 0) return false;
        w.battery--; sync(p);
        return true;
    }

    public static void addCoins(ServerPlayer p, long n) { if (n <= 0) return; of(p).coins += n; sync(p); }

    // ===================== イベント =====================

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer sp) sync(sp); }
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer sp) sync(sp); }
    @SubscribeEvent
    public static void onDim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer sp) sync(sp); }

    /** 床の電池・包帯を拾う：所持数へ（上限まで）。いっぱいなら拾わない。 */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        ItemEntity it = e.getItemEntity();
        ItemStack s = it.getItem();
        boolean bat = s.is(RoomsMod.BATTERY.get()), band = s.is(RoomsMod.BANDAGE.get());
        if (!bat && !band) return;
        if (it.hasPickUpDelay()) return;
        e.setCanPickup(TriState.FALSE); // 持ち物には入れない
        Wallet w = of(sp);
        int room = bat ? space(MAX_BATTERY, w.battery) : space(MAX_BANDAGE, w.bandage);
        if (room <= 0) {
            if (sp.tickCount % 40 == 0) sp.displayClientMessage(Component.translatable(bat ? "message.roomsmod.battery_full" : "message.roomsmod.bandage_carry_full"), true);
            return;
        }
        int n = Math.min(room, s.getCount());
        if (bat) w.battery += n; else w.bandage += n;
        s.shrink(n);
        if (s.isEmpty()) it.discard(); else it.setItem(s);
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.4f);
        sync(sp);
        RoomsDebug.log("wallet pickup {} x{} player={} -> battery={} bandage={}", bat ? "battery" : "bandage", n, sp.getGameProfile().getName(), w.battery, w.bandage);
    }

    /**
     * 電池・包帯は持ち物に存在させない：持ち物（とカーソルで持っている物）にあれば所持数へ入れ、上限を超えた分は消す。
     * 拾う以外の経路（旧セーブ・チェスト等から取り出し・クリエイティブ・ほかの MOD）もここで吸収する。毎 tick。
     */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Wallet w = of(sp);
        var inv = sp.getInventory();
        int gotBat = 0, gotBand = 0, lostBat = 0, lostBand = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int[] r = absorb(w, s);
            if (r == null) continue;
            gotBat += r[0]; gotBand += r[1]; lostBat += r[2]; lostBand += r[3];
            inv.setItem(i, ItemStack.EMPTY);
        }
        ItemStack carried = sp.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            int[] r = absorb(w, carried);
            if (r != null) { gotBat += r[0]; gotBand += r[1]; lostBat += r[2]; lostBand += r[3]; sp.containerMenu.setCarried(ItemStack.EMPTY); sp.containerMenu.broadcastChanges(); }
        }
        if (gotBat + gotBand + lostBat + lostBand == 0) return;
        sync(sp);
        RoomsDebug.log("inventory items absorbed player={} battery=+{} bandage=+{} discarded_battery={} discarded_bandage={} -> battery={} bandage={}",
                sp.getGameProfile().getName(), gotBat, gotBand, lostBat, lostBand, w.battery, w.bandage);
    }

    /** 電池・包帯なら所持数へ（上限まで）入れて {入った電池, 入った包帯, 消えた電池, 消えた包帯}、ほかの物は null。 */
    private static int[] absorb(Wallet w, ItemStack s) {
        if (s.is(RoomsMod.BATTERY.get())) {
            int n = Math.min(space(MAX_BATTERY, w.battery), s.getCount());
            w.battery += n;
            return new int[]{n, 0, s.getCount() - n, 0};
        }
        if (s.is(RoomsMod.BANDAGE.get())) {
            int n = Math.min(space(MAX_BANDAGE, w.bandage), s.getCount());
            w.bandage += n;
            return new int[]{0, n, 0, s.getCount() - n};
        }
        return null;
    }

    /** 死亡：電池・包帯はなくなる（以前の持ち物と同じ）。周回のコインは RoomsRun が先に精算する。 */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.isCanceled()) return;
        if (!sp.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        Wallet w = of(sp);
        w.battery = 0; w.bandage = 0; w.adrenaline = 0; w.charm = 0;
        sync(sp);
    }
}
