package org.teacon.voteme.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterItem extends Item {

    public static final String ID = "voteme:voter";

    @ObjectHolder(ID)
    public static final VoterItem INSTANCE = null;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new VoterItem(new Properties().maxStackSize(1).group(ItemGroup.MISC)));
    }

    private VoterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }
}
