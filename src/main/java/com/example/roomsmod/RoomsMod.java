package com.example.roomsmod;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.entity.A20Entity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

@Mod(RoomsMod.MOD_ID)
public class RoomsMod {
    public static final String MOD_ID = "roomsmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.example.roomsmod.block.LockerBlock> LOCKER = BLOCKS.registerBlock("locker",
            com.example.roomsmod.block.LockerBlock::new,
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(net.minecraft.world.level.block.SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.example.roomsmod.block.HidingTableBlock> HIDING_TABLE = BLOCKS.registerBlock("hiding_table",
            com.example.roomsmod.block.HidingTableBlock::new,
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .sound(net.minecraft.world.level.block.SoundType.WOOD)
                    .noOcclusion());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> HIDING_TABLE_ITEM = ITEMS.registerSimpleBlockItem("hiding_table", HIDING_TABLE);
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> LOCKER_ITEM = ITEMS.registerSimpleBlockItem("locker", LOCKER);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> A20_APPROACH = SOUND_EVENTS.register("a20_approach", () -> SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "a20_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> A40_APPROACH = SOUND_EVENTS.register("a40_approach", () -> SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "a40_approach")));

    public static final DeferredHolder<EntityType<?>, EntityType<A20Entity>> A20 = ENTITIES.register("a20",
            () -> EntityType.Builder.of(A20Entity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f)
                    .clientTrackingRange(12) // 192ブロック：接近音(160ブロック)が届く距離まで同期
                    .updateInterval(1) // 高速移動するので毎tick位置同期（追従サウンド用）
                    .build("a20"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.A40Entity>> A40 = ENTITIES.register("a40",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.A40Entity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f)
                    .clientTrackingRange(12) // 192ブロック：接近音(160ブロック)が届く距離まで同期
                    .updateInterval(1) // 高速移動するので毎tick位置同期（追従サウンド用）
                    .build("a40"));

    public RoomsMod(IEventBus modEventBus) {
        LOGGER.info("Rooms Mod is loading!");
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.hiding.LockerManager.class);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.RoomTracker.class);
        com.example.roomsmod.hiding.HidingRules.addHidingCheck(com.example.roomsmod.hiding.LockerManager::isUnderTable);
        modEventBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(LOCKER_ITEM.get()); e.accept(HIDING_TABLE_ITEM.get()); }
        });
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(RoomsMod::onEntityAttributeCreation);
        modEventBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) -> e.register(com.example.roomsmod.entity.RusherChunks.CONTROLLER));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) com.example.roomsmod.entity.RusherChunks.cleanupStale(sl);
        });
        NeoForge.EVENT_BUS.addListener(RoomsMod::onRegisterCommands);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.DoorEventHandler.class);
        // 起動時に RoomsState を読み込んでデバッグフラグを復元、停止時にクリア
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) -> {
            com.example.roomsmod.util.RoomsDebug.setEnabled(false);
            var lvl = e.getServer().getLevel(RoomsCommands.ROOMS_DIMENSION);
            if (lvl != null) com.example.roomsmod.world.RoomsState.get(lvl);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
            com.example.roomsmod.util.RoomsDebug.setEnabled(false));
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(A20.get(), A20Entity.createAttributes().build());
        event.put(A40.get(), com.example.roomsmod.entity.A40Entity.createAttributes().build());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomsCommands.register(event.getDispatcher());
    }
}
