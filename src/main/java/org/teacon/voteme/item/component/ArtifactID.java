package org.teacon.voteme.item.component;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = "voteme")
public class ArtifactID {

    public static final Identifier ID = Identifier.parse("voteme:artifact_id");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> INSTANCE = DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, ID);

    @SubscribeEvent
    public static void on(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.DATA_COMPONENT_TYPE) {
            event.register(Registries.DATA_COMPONENT_TYPE, ID, () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());
        }
    }
}
