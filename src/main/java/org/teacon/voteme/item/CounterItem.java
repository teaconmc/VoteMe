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
public final class CounterItem extends Item {

    public static final String ID = "voteme:counter";

    @ObjectHolder(ID)
    public static final CounterItem INSTANCE = null;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new CounterItem(new Properties().maxStackSize(1).group(ItemGroup.MISC)));
    }

    private CounterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }
}
