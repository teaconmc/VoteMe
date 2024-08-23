package org.teacon.voteme.crafting;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public final class VoterFromCounterRecipe extends CustomRecipe {
    public static final ResourceLocation ID = ResourceLocation.parse("voteme:crafting_special_counter_from_voter");

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<VoterFromCounterRecipe>> SERIALIZER
            = DeferredHolder.create(Registries.RECIPE_SERIALIZER, ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, ID, () -> new SimpleCraftingRecipeSerializer<>(VoterFromCounterRecipe::new));
    }

    private VoterFromCounterRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput inv, Level worldIn) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.size(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(VoterItem.INSTANCE.get())) {
                ++voterSize;
                continue;
            }
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE.get())) {
                counter = stack;
                continue;
            }
            return false;
        }
        return voterSize > 0 && !counter.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput inv, HolderLookup.Provider registries) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.size(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE.get())) {
                counter = stack;
                continue;
            }
            if (stack.is(VoterItem.INSTANCE.get())) {
                ++voterSize;
                continue;
            }
            if (stack.isEmpty()) {
                continue;
            }
            return ItemStack.EMPTY;
        }
        if (voterSize > 0 && !counter.isEmpty()) {
            return VoterItem.INSTANCE.get().copyFrom(voterSize, counter);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inv) {
        NonNullList<ItemStack> list = NonNullList.withSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0, size = list.size(); i < size; ++i) {
            ItemStack stack = inv.getItem(i);
            if (stack.hasCraftingRemainingItem()) {
                list.set(i, stack.getCraftingRemainingItem());
            } else if (stack.is(CounterItem.INSTANCE.get())) {
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
        return SERIALIZER.get();
    }
}
