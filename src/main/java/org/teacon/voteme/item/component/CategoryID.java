package org.teacon.voteme.item.component;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = "voteme", bus = EventBusSubscriber.Bus.MOD)
public class CategoryID {

    public static final ResourceLocation ID = ResourceLocation.parse("voteme:category_id");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> INSTANCE
            = DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, ID);

    @SubscribeEvent
    public static void on(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.DATA_COMPONENT_TYPE) {
            event.register(Registries.DATA_COMPONENT_TYPE, ID, () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());
        }
    }
}

