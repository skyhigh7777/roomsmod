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
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<com.example.roomsmod.item.FlashlightData>> FLASHLIGHT_DATA =
            DATA_COMPONENTS.registerComponentType("flashlight", b -> b.persistent(com.example.roomsmod.item.FlashlightData.CODEC).networkSynchronized(com.example.roomsmod.item.FlashlightData.STREAM_CODEC));
    public static final net.neoforged.neoforge.registries.DeferredBlock<com.example.roomsmod.block.FlashlightLightBlock> FLASHLIGHT_LIGHT = BLOCKS.registerBlock("flashlight_light",
            com.example.roomsmod.block.FlashlightLightBlock::new,
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().replaceable().noCollission().noOcclusion().noLootTable()
                    .instabreak().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(st -> com.example.roomsmod.item.FlashlightHandler.BRIGHTNESS));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.example.roomsmod.item.FlashlightItem> FLASHLIGHT = ITEMS.registerItem("flashlight",
            com.example.roomsmod.item.FlashlightItem::new, new net.minecraft.world.item.Item.Properties().stacksTo(1));
    public static final net.neoforged.neoforge.registries.DeferredItem<com.example.roomsmod.item.BandageItem> BANDAGE = ITEMS.registerItem("bandage", com.example.roomsmod.item.BandageItem::new, new net.minecraft.world.item.Item.Properties().stacksTo(8));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> BATTERY = ITEMS.registerSimpleItem("battery", new net.minecraft.world.item.Item.Properties().stacksTo(16));
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
    public static final DeferredHolder<SoundEvent, SoundEvent> V10_APPROACH = SOUND_EVENTS.register("v10_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v10_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25T_STEP = SOUND_EVENTS.register("v25t_step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25t_step")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25T_CHECK = SOUND_EVENTS.register("v25t_check", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25t_check")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25L_STEP = SOUND_EVENTS.register("v25l_step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25l_step")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25L_CHECK = SOUND_EVENTS.register("v25l_check", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25l_check")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V100_KNOCK = SOUND_EVENTS.register("v100_knock", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v100_knock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V100_SCREAM = SOUND_EVENTS.register("v100_scream", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v100_scream")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25B_TICK = SOUND_EVENTS.register("v25_buffed_tick", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25_buffed_tick")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V25B_REVEAL = SOUND_EVENTS.register("v25_buffed_reveal", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v25_buffed_reveal")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V100_WATCH = SOUND_EVENTS.register("v100_watch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v100_watch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V50_APPROACH = SOUND_EVENTS.register("v50_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v50_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V50_TICK = SOUND_EVENTS.register("v50_tick", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v50_tick")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V50_BUZZER = SOUND_EVENTS.register("v50_buzzer", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v50_buzzer")));
    public static final DeferredHolder<SoundEvent, SoundEvent> V50_EXPLODE = SOUND_EVENTS.register("v50_explode", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "v50_explode")));

    // ---- V（Village）区画のエンティティ（仮名。改名は id・VSpawns.Kind・lang・テクスチャ名）----
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V10Entity>> V10 = ENTITIES.register("v10",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V10Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.8f).clientTrackingRange(12).updateInterval(1).build("v10"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V25TEntity>> V25T = ENTITIES.register("v25t",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V25TEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.2f).clientTrackingRange(12).updateInterval(1).build("v25t"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V25LEntity>> V25L = ENTITIES.register("v25l",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V25LEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.2f).clientTrackingRange(12).updateInterval(1).build("v25l"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V25BuffedEntity>> V25_BUFFED = ENTITIES.register("v25_buffed",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V25BuffedEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.2f).clientTrackingRange(12).updateInterval(1).build("v25_buffed"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V100Entity>> V100 = ENTITIES.register("v100",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V100Entity::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.9f).clientTrackingRange(12).updateInterval(1).build("v100"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.v.V50Entity>> V50 = ENTITIES.register("v50",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.v.V50Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.4f).clientTrackingRange(12).updateInterval(1).build("v50"));

    // ---- S（School、旧 GK）区画（定数名・登録 ID は GK のまま） ----
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_CHIME_APPROACH = SOUND_EVENTS.register("gk_chime_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_chime_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_ROLLCALL_STEP = SOUND_EVENTS.register("gk_rollcall_step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_rollcall_step")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_ROLLCALL_CHECK = SOUND_EVENTS.register("gk_rollcall_check", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_rollcall_check")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_MOTH_FLUTTER = SOUND_EVENTS.register("gk_moth_flutter", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_moth_flutter")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_STARE_HUM = SOUND_EVENTS.register("gk_stare_hum", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_stare_hum")));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKChimeEntity>> GK_CHIME = ENTITIES.register("gk_chime",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKChimeEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_chime"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKMimicEntity>> GK_MIMIC = ENTITIES.register("gk_mimic",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKMimicEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.2f).clientTrackingRange(12).updateInterval(1).build("gk_mimic"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKMothEntity>> GK_MOTH = ENTITIES.register("gk_moth",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKMothEntity::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.2f).clientTrackingRange(12).updateInterval(1).build("gk_moth"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKStareEntity>> GK_STARE = ENTITIES.register("gk12",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKStareEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk12"));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_WHISPER_SOUND = SOUND_EVENTS.register("gk_whisper", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_whisper")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_DETENTION_CHAIN = SOUND_EVENTS.register("gk_detention_chain", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_detention_chain")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_SUBSTITUTE_CREAK = SOUND_EVENTS.register("gk_substitute_creak", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_substitute_creak")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_ERASER_SCRATCH = SOUND_EVENTS.register("gk_eraser_scratch", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_eraser_scratch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_DESK_CHECK = SOUND_EVENTS.register("gk_desk_check", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_desk_check")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_PA_CHIME = SOUND_EVENTS.register("gk_pa_chime", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_pa_chime")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_JANITOR_SQUEAK = SOUND_EVENTS.register("gk_janitor_squeak", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_janitor_squeak")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_MIRROR_CHIME = SOUND_EVENTS.register("gk_mirror_chime", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_mirror_chime")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_07_KNOCK = SOUND_EVENTS.register("gk_07_knock", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_07_knock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_BELL2_BELL = SOUND_EVENTS.register("gk_bell2_bell", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_bell2_bell")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GK_HOMEWORK_PAGE = SOUND_EVENTS.register("gk_homework_page", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "gk_homework_page")));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKWhisperEntity>> GK_WHISPER = ENTITIES.register("gk_whisper",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKWhisperEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 1.8f).clientTrackingRange(12).updateInterval(1).build("gk_whisper"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKDetentionEntity>> GK_DETENTION = ENTITIES.register("gk_detention",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKDetentionEntity::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.1f).clientTrackingRange(12).updateInterval(1).build("gk_detention"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKSubstituteEntity>> GK_SUBSTITUTE = ENTITIES.register("gk_substitute",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKSubstituteEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_substitute"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKEraserEntity>> GK_ERASER = ENTITIES.register("gk_eraser",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKEraserEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_eraser"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKQuizEntity>> GK_QUIZ = ENTITIES.register("gk_quiz",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKQuizEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.2f).clientTrackingRange(12).updateInterval(1).build("gk_quiz"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKPAEntity>> GK_PA = ENTITIES.register("gk_pa",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKPAEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.8f).clientTrackingRange(12).updateInterval(1).build("gk_pa"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKJanitorEntity>> GK_JANITOR = ENTITIES.register("gk_janitor",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKJanitorEntity::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_janitor"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKMirrorEntity>> GK_MIRROR = ENTITIES.register("gk_mirror",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKMirrorEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_mirror"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GK07Entity>> GK_07 = ENTITIES.register("gk07",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GK07Entity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk07"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKBell2Entity>> GK_BELL2 = ENTITIES.register("gk_bell2",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKBell2Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("gk_bell2"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.gk.GKRewindEntity>> GK_REWIND = ENTITIES.register("gk_rewind",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.gk.GKRewindEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 1.6f).clientTrackingRange(12).updateInterval(1).build("gk_rewind"));

    // U（廊下）区画。音は既存の音声ファイルを sounds.json でピッチ違いにして使う（新しい音声ファイルなし）
    public static final DeferredHolder<SoundEvent, SoundEvent> U20_APPROACH = SOUND_EVENTS.register("u20_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "u20_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> U60_APPROACH = SOUND_EVENTS.register("u60_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "u60_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> U120_APPROACH = SOUND_EVENTS.register("u120_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "u120_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> U200_STEP = SOUND_EVENTS.register("u200_step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "u200_step")));
    public static final DeferredHolder<SoundEvent, SoundEvent> U300_APPROACH = SOUND_EVENTS.register("u300_approach", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "u300_approach")));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.u.U20Entity>> U20 = ENTITIES.register("u20",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.u.U20Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.2f).clientTrackingRange(12).updateInterval(1).build("u20"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.u.U60Entity>> U60 = ENTITIES.register("u60",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.u.U60Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.2f).clientTrackingRange(12).updateInterval(1).build("u60"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.u.U120Entity>> U120 = ENTITIES.register("u120",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.u.U120Entity::new, MobCategory.MONSTER)
                    .sized(0.9f, 2.0f).clientTrackingRange(12).updateInterval(1).build("u120"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.u.U200Entity>> U200 = ENTITIES.register("u200",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.u.U200Entity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.0f).clientTrackingRange(12).updateInterval(1).build("u200"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.u.U300Entity>> U300 = ENTITIES.register("u300",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.u.U300Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("u300"));

    // PLACEHOLDER（テスト用）区画。自然出現しない、/rooms spawn のみ
    public static final DeferredHolder<SoundEvent, SoundEvent> PLACEHOLDER_BEEP = SOUND_EVENTS.register("placeholder_beep", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "placeholder_beep")));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.placeholder.PlaceholderEntity>> PLACEHOLDER_32767 = ENTITIES.register("placeholder_32767",
            () -> EntityType.Builder.<com.example.roomsmod.entity.placeholder.PlaceholderEntity>of(com.example.roomsmod.entity.placeholder.Placeholder32767Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).build("placeholder_32767"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.placeholder.PlaceholderEntity>> PLACEHOLDER_65535 = ENTITIES.register("placeholder_65535",
            () -> EntityType.Builder.<com.example.roomsmod.entity.placeholder.PlaceholderEntity>of(com.example.roomsmod.entity.placeholder.Placeholder65535Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).build("placeholder_65535"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.placeholder.PlaceholderEntity>> PLACEHOLDER_2147483648 = ENTITIES.register("placeholder_2147483648",
            () -> EntityType.Builder.<com.example.roomsmod.entity.placeholder.PlaceholderEntity>of(com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).build("placeholder_2147483648"));

    // 3!3!2（ハードモード専用、どのセクションにも属さない）。姿なし
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.hard.ExtraboxEntity>> EXTRABOX = ENTITIES.register("extrabox",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.hard.ExtraboxEntity::new, MobCategory.MONSTER)
                    .sized(0.01f, 0.01f).noSummon().clientTrackingRange(4).updateInterval(20).build("extrabox"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.hard.E332Entity>> E332 = ENTITIES.register("e332",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.hard.E332Entity::new, MobCategory.MONSTER)
                    .sized(0.01f, 0.01f).noSummon().clientTrackingRange(4).updateInterval(20).build("e332"));

    public RoomsMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.example.roomsmod.config.RoomsConfig.SPEC);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.RoomsSpawnHandler.class);
        LOGGER.info("Rooms Mod is loading!");
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.item.FlashlightHandler.class);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.DebugNoDeath.class);
        com.example.roomsmod.stamina.Stamina.init(modEventBus);
        com.example.roomsmod.world.AbyssRooms.init(modEventBus);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.stamina.Stamina.class);
        ENTITIES.register(modEventBus);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.hiding.LockerManager.class);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.RoomTracker.class);
        com.example.roomsmod.hiding.HidingRules.addHidingCheck(com.example.roomsmod.hiding.LockerManager::isUnderTable);
        modEventBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) { e.accept(LOCKER_ITEM.get()); e.accept(HIDING_TABLE_ITEM.get()); }
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) { e.accept(com.example.roomsmod.item.FlashlightHandler.newFlashlight(com.example.roomsmod.item.FlashlightItem.BATTERY_TICKS)); e.accept(BATTERY.get()); e.accept(BANDAGE.get()); }
        });
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(RoomsMod::onEntityAttributeCreation);
        modEventBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) -> e.register(com.example.roomsmod.entity.RusherChunks.CONTROLLER));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) { com.example.roomsmod.entity.RusherChunks.cleanupStale(sl); com.example.roomsmod.entity.v.VEntity.watchdog(sl); }
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

    // B 区画
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BGrinEntity>> B_GRIN = ENTITIES.register("b_grin",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BGrinEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.6f).clientTrackingRange(12).updateInterval(1).build("b_grin"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BMaskEntity>> B_MASK = ENTITIES.register("b_mask",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BMaskEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.8f).clientTrackingRange(12).updateInterval(1).build("b_mask"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BEyeEntity>> B_EYE = ENTITIES.register("b_eye",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BEyeEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f).clientTrackingRange(12).updateInterval(1).build("b_eye"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BChorusEntity>> B_CHORUS = ENTITIES.register("b_chorus",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BChorusEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("b_chorus"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BStaticEntity>> B_STATIC = ENTITIES.register("b_static",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BStaticEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("b_static"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BErrorEntity>> B_ERROR = ENTITIES.register("b_error",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BErrorEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 1.4f).clientTrackingRange(12).updateInterval(1).build("b_error"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BArgusEntity>> B_ARGUS = ENTITIES.register("b_argus",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BArgusEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.6f).clientTrackingRange(12).updateInterval(1).build("b_argus"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BMawEntity>> B_MAW = ENTITIES.register("b_maw",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BMawEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("b_maw"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BScatterEntity>> B_SCATTER = ENTITIES.register("b_scatter",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BScatterEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.0f).clientTrackingRange(12).updateInterval(1).build("b_scatter"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BReachEntity>> B_REACH = ENTITIES.register("b_reach",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BReachEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.4f).clientTrackingRange(12).updateInterval(1).build("b_reach"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BSmileEntity>> B_SMILE = ENTITIES.register("b_smile",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BSmileEntity::new, MobCategory.MONSTER)
                    .sized(1.0f, 1.4f).clientTrackingRange(12).updateInterval(1).build("b_smile"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.BVoidEntity>> B_VOID = ENTITIES.register("b_void",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.BVoidEntity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.4f).clientTrackingRange(12).updateInterval(1).build("b_void"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.b.B17Entity>> B_17 = ENTITIES.register("b_17",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.b.B17Entity::new, MobCategory.MONSTER)
                    .sized(0.8f, 1.2f).clientTrackingRange(12).updateInterval(1).build("b_17"));

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(A20.get(), A20Entity.createAttributes().build());
        event.put(A40.get(), com.example.roomsmod.entity.A40Entity.createAttributes().build());
        for (var t : java.util.List.of(V10, V25T, V25L, V25_BUFFED, V100, V50, GK_CHIME, GK_MIMIC, GK_MOTH, GK_STARE, GK_WHISPER, GK_DETENTION, GK_SUBSTITUTE, GK_ERASER, GK_QUIZ, GK_PA, GK_JANITOR, GK_MIRROR, GK_07, GK_BELL2, GK_REWIND,
                B_GRIN, B_MASK, B_EYE, B_CHORUS, B_STATIC, B_ERROR, B_ARGUS, B_MAW, B_SCATTER, B_REACH, B_SMILE, B_VOID, B_17,
                U20, U60, U120, U200, U300, PLACEHOLDER_32767, PLACEHOLDER_65535, PLACEHOLDER_2147483648, E332, EXTRABOX))
            event.put(t.get(), com.example.roomsmod.entity.v.VEntity.createAttributes().build());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomsCommands.register(event.getDispatcher());
    }
}
