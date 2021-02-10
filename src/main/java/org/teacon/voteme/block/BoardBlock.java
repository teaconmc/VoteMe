package org.teacon.voteme.block;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
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
public final class BoardBlock extends Block {

    public static final String ID = "voteme:board";

    @ObjectHolder(ID)
    public static BoardBlock INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(new BoardBlock(Properties.create(Material.IRON)));
    }

    @SubscribeEvent
    public static void registerItem(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new BlockItem(INSTANCE, new Item.Properties().group(ItemGroup.MISC)).setRegistryName(ID));
    }

    private BoardBlock(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }
}
