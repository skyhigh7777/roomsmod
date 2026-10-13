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

    public static final DeferredHolder<EntityType<?>, EntityType<A60Entity>> A60 = ENTITIES.register("a60",
            () -> EntityType.Builder.of(A60Entity::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.0f)
                    .build("a60"));

    public RoomsMod(IEventBus modEventBus) {
        LOGGER.info("Rooms Mod is loading!");
        ENTITIES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modEventBus.addListener(RoomsMod::onEntityAttributeCreation);
        NeoForge.EVENT_BUS.addListener(RoomsMod::onRegisterCommands);
        NeoForge.EVENT_BUS.register(com.example.roomsmod.event.DoorEventHandler.class);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(A60.get(), A60Entity.createAttributes().build());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        RoomsCommands.register(event.getDispatcher());
    }
}
