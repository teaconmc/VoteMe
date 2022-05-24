package org.teacon.voteme.crafting;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.SpecialRecipe;
import net.minecraft.item.crafting.SpecialRecipeSerializer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterFromCounterRecipe extends SpecialRecipe {
    public static final String ID = "voteme:crafting_special_counter_from_voter";

    @ObjectHolder(ID)
    public static SpecialRecipeSerializer<VoterFromCounterRecipe> SERIALIZER;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<IRecipeSerializer<?>> event) {
        event.getRegistry().register(new SpecialRecipeSerializer<>(VoterFromCounterRecipe::new).setRegistryName(ID));
    }

    private VoterFromCounterRecipe(ResourceLocation location) {
        super(location);
    }

    @Override
    public boolean matches(CraftingInventory inv, World worldIn) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.getContainerSize(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == VoterItem.INSTANCE) {
                ++voterSize;
                continue;
            }
            if (counter.isEmpty() && stack.getItem() == CounterItem.INSTANCE) {
                counter = stack;
                continue;
            }
            return false;
        }
        return voterSize > 0 && !counter.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInventory inv) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.getContainerSize(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (counter.isEmpty() && stack.getItem() == CounterItem.INSTANCE) {
                counter = stack;
                continue;
            }
            if (stack.getItem() == VoterItem.INSTANCE) {
                ++voterSize;
                continue;
            }
            if (stack.isEmpty()) {
                continue;
            }
            return ItemStack.EMPTY;
        }
        if (voterSize > 0 && !counter.isEmpty()) {
            return VoterItem.INSTANCE.copyFrom(voterSize, counter);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInventory inv) {
        NonNullList<ItemStack> list = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0, size = list.size(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.hasContainerItem()) {
                list.set(i, stack.getContainerItem());
            } else if (stack.getItem() == CounterItem.INSTANCE) {
                list.set(i, stack.copy());
            }
        }
        return list;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public IRecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
