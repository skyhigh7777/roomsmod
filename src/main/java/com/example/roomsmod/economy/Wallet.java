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
 *   <li>電池は最大 {@value #MAX_BATTERY}、包帯は最大 {@value #MAX_BANDAGE}、コインは上限なし。</li>
 *   <li>床の電池・包帯は拾うとここに入る（いっぱいなら拾わない＝床に残る）。持ち物に残っている電池・包帯（旧セーブ・creative）も入れられる分だけ移す。</li>
 *   <li>死ぬと電池・包帯はなくなる（以前の持ち物と同じ）。コインは残る。</li>
 *   <li>周回の記録（どの部屋から始めたか・どこまで進んだか・被弾したか）もここ（RoomsRun）。</li>
 * </ul>
 */
public final class Wallet implements INBTSerializable<CompoundTag> {
    public static final int MAX_BATTERY = 10, MAX_BANDAGE = 5;

    public int battery, bandage;
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
        t.putInt("runEpoch", runEpoch); t.putInt("runStart", runStart); t.putInt("runFurthest", runFurthest); t.putBoolean("runHit", runHit);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider p, CompoundTag t) {
        battery = t.getInt("battery"); bandage = t.getInt("bandage"); coins = t.getLong("coins");
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
    public record SyncPayload(int battery, int bandage, long coins) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "wallet"));
        public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::battery, ByteBufCodecs.VAR_INT, SyncPayload::bandage, ByteBufCodecs.VAR_LONG, SyncPayload::coins, SyncPayload::new);
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

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> { clientBattery = msg.battery(); clientBandage = msg.bandage(); clientCoins = msg.coins(); }))
                .playToServer(BandagePayload.TYPE, BandagePayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Bandaging.request(sp, msg.start()); })));
    }

    public static Wallet of(Player p) { return p.getData(WALLET); }

    public static void sync(ServerPlayer p) {
        Wallet w = of(p);
        PacketDistributor.sendToPlayer(p, new SyncPayload(w.battery, w.bandage, w.coins));
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
        int room = bat ? MAX_BATTERY - w.battery : MAX_BANDAGE - w.bandage;
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

    /** 持ち物に残っている電池・包帯（旧セーブ・コマンド）を入れられる分だけ移す。 */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 7) return;
        Wallet w = of(sp);
        var inv = sp.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(RoomsMod.BATTERY.get()) && w.battery < MAX_BATTERY) {
                int n = Math.min(MAX_BATTERY - w.battery, s.getCount()); w.battery += n; s.shrink(n); changed = true;
            } else if (s.is(RoomsMod.BANDAGE.get()) && w.bandage < MAX_BANDAGE) {
                int n = Math.min(MAX_BANDAGE - w.bandage, s.getCount()); w.bandage += n; s.shrink(n); changed = true;
            }
        }
        if (changed) { sync(sp); RoomsDebug.log("wallet moved inventory items player={} -> battery={} bandage={}", sp.getGameProfile().getName(), w.battery, w.bandage); }
    }

    /** 死亡：電池・包帯はなくなる（以前の持ち物と同じ）。周回のコインは RoomsRun が先に精算する。 */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.isCanceled()) return;
        if (!sp.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        Wallet w = of(sp);
        w.battery = 0; w.bandage = 0;
        sync(sp);
    }
}
