package org.teacon.voteme.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.item.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.block.BoardBlock;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BoardItem extends BlockItem {

    public static final String ID = "voteme:board";

    @ObjectHolder(ID)
    public static BoardItem INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new BoardItem(new Properties().group(ItemGroup.MISC)));
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemModelsProperties.registerProperty(INSTANCE,
                new ResourceLocation("size"), (stack, world, entity) -> (INSTANCE.getBoardSize(stack) + 1) / 8.0F));
    }

    @Override
    public void fillItemGroup(ItemGroup group, NonNullList<ItemStack> items) {
        if (this.isInGroup(group)) {
            IntStream.range(0, 8).mapToObj(i -> Util.make(new ItemStack(this),
                    stack -> stack.getOrCreateTag().putInt("BoardSize", i))).forEach(items::add);
        }
    }

    public int getBoardSize(ItemStack stack) {
        return stack.hasTag() ? Objects.requireNonNull(stack.getTag()).getInt("BoardSize") : 0;
    }

    private BoardItem(Properties builder) {
        super(BoardBlock.INSTANCE, builder);
        this.setRegistryName(ID);
    }
}
