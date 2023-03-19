package org.teacon.voteme.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMeItemGroup {
    public static final ResourceLocation ID = new ResourceLocation("voteme:voteme");

    @SubscribeEvent
    public static void register(CreativeModeTabEvent.Register event) {
        event.registerCreativeModeTab(ID, builder -> builder.icon(VoteMeItemGroup::makeIcon));
    }

    private static ItemStack makeIcon() {
        return VoterItem.INSTANCE.get().getDefaultInstance();
    }
}
