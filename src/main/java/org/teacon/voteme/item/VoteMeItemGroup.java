package org.teacon.voteme.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public final class VoteMeItemGroup {
    public static final ResourceLocation ID = ResourceLocation.parse("voteme:voteme");

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.CREATIVE_MODE_TAB) {
            return;
        }
        event.register(Registries.CREATIVE_MODE_TAB, ID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.voteme")).icon(VoteMeItemGroup::makeIcon).build());
    }

    private static ItemStack makeIcon() {
        return VoterItem.INSTANCE.get().getDefaultInstance();
    }
}
