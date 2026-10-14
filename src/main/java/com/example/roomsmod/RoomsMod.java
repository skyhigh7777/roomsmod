package com.example.roomsmod;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.entity.A60Entity;
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
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> A60_APPROACH = SOUND_EVENTS.register("a60_approach", () -> SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "a60_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> A75_APPROACH = SOUND_EVENTS.register("a75_approach", () -> SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "a75_approach")));

    public static final DeferredHolder<EntityType<?>, EntityType<A60Entity>> A60 = ENTITIES.register("a60",
            () -> EntityType.Builder.of(A60Entity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f)
                    .build("a60"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.example.roomsmod.entity.A75Entity>> A75 = ENTITIES.register("a75",
            () -> EntityType.Builder.of(com.example.roomsmod.entity.A75Entity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f)
                    .build("a75"));

    public RoomsMod(IEventBus modEventBus) {
        LOGGER.info("Rooms Mod is loading!");
        ENTITIES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(RoomsMod::onEntityAttributeCreation);
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
        event.put(A60.get(), A60Entity.createAttributes().build());
        event.put(A75.get(), com.example.roomsmod.entity.A75Entity.createAttributes().build());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomsCommands.register(event.getDispatcher());
    }
}
