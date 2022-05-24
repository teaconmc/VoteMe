package org.teacon.voteme.crafting;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
import net.minecraft.world.level.Level;
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
public final class VoterFromCounterRecipe extends CustomRecipe {
    public static final String ID = "voteme:crafting_special_counter_from_voter";

    @ObjectHolder(ID)
    public static SimpleRecipeSerializer<VoterFromCounterRecipe> SERIALIZER;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<RecipeSerializer<?>> event) {
        event.getRegistry().register(new SimpleRecipeSerializer<>(VoterFromCounterRecipe::new).setRegistryName(ID));
    }

    private VoterFromCounterRecipe(ResourceLocation location) {
        super(location);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level worldIn) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.getContainerSize(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(VoterItem.INSTANCE)) {
                ++voterSize;
                continue;
            }
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE)) {
                counter = stack;
                continue;
            }
            return false;
        }
        return voterSize > 0 && !counter.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer inv) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.getContainerSize(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE)) {
                counter = stack;
                continue;
            }
            if (stack.is(VoterItem.INSTANCE)) {
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
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        NonNullList<ItemStack> list = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0, size = list.size(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.hasContainerItem()) {
                list.set(i, stack.getContainerItem());
            } else if (stack.is(CounterItem.INSTANCE)) {
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
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
