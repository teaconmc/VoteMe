package org.teacon.voteme.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMeItemGroup {
    public static final ResourceLocation ID = new ResourceLocation("voteme:voteme");

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.CREATIVE_MODE_TAB.key(), ID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.voteme")).icon(VoteMeItemGroup::makeIcon).build());
    }

    private static ItemStack makeIcon() {
        return VoterItem.INSTANCE.get().getDefaultInstance();
    }
}
