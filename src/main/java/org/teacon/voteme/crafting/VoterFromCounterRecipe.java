package org.teacon.voteme.crafting;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterFromCounterRecipe extends CustomRecipe {
    public static final ResourceLocation ID = new ResourceLocation("voteme:crafting_special_counter_from_voter");

    public static final RegistryObject<SimpleCraftingRecipeSerializer<VoterFromCounterRecipe>> SERIALIZER = RegistryObject.create(ID, ForgeRegistries.RECIPE_SERIALIZERS);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.RECIPE_SERIALIZERS.getRegistryKey(), ID,
                () -> new SimpleCraftingRecipeSerializer<>(VoterFromCounterRecipe::new));
    }

    private VoterFromCounterRecipe(ResourceLocation location, CraftingBookCategory category) {
        super(location, category);
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
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        int voterSize = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = inv.getContainerSize(); i < size; ++i) {
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
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        NonNullList<ItemStack> list = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
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
