package org.teacon.voteme.item.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "voteme")
public class CategoryID {

    public static final Identifier ID = Identifier.parse("voteme:category_id");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> INSTANCE
            = DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, ID);

    @SubscribeEvent
    public static void on(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.DATA_COMPONENT_TYPE) {
            event.register(Registries.DATA_COMPONENT_TYPE, ID, () -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build());
        }
    }
}
