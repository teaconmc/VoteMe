package org.teacon.voteme.crafting;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoterFromCounterRecipe extends NormalCraftingRecipe {
    public static final Identifier ID = Identifier.parse("voteme:crafting_special_counter_from_voter");
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VoterFromCounterRecipe>> SERIALIZER
            = DeferredHolder.create(Registries.RECIPE_SERIALIZER, ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        CraftingBookInfo info = new CraftingBookInfo(CraftingBookCategory.MISC, "");
        VoterFromCounterRecipe r = new VoterFromCounterRecipe(new Recipe.CommonInfo(false), info);
        Supplier<RecipeSerializer<?>> supplier = () -> new RecipeSerializer<>(MapCodec.unit(r), StreamCodec.unit(r));
        event.register(Registries.RECIPE_SERIALIZER, ID, supplier);
    }

    private VoterFromCounterRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo) {
        super(commonInfo, bookInfo);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int voterCount = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = input.size(); i < size; ++i) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(VoterItem.INSTANCE.get())) {
                ++voterCount;
                continue;
            }
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE.get())) {
                counter = stack;
                continue;
            }
            return false;
        }
        return voterCount > 0 && voterCount <= 8 && !counter.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        int voterCount = 0;
        ItemStack counter = ItemStack.EMPTY;
        for (int i = 0, size = input.size(); i < size; ++i) {
            ItemStack stack = input.getItem(i);
            if (counter.isEmpty() && stack.is(CounterItem.INSTANCE.get())) {
                counter = stack;
                continue;
            }
            if (stack.is(VoterItem.INSTANCE.get())) {
                ++voterCount;
                continue;
            }
            if (stack.isEmpty()) {
                continue;
            }
            return ItemStack.EMPTY;
        }
        if (voterCount > 0 && voterCount <= 8 && !counter.isEmpty()) {
            return VoterItem.INSTANCE.get().copyFrom(voterCount, counter);
        }
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0, size = input.size(); i < size; ++i) {
            ItemStack stack = input.getItem(i);
            if (stack.is(CounterItem.INSTANCE.get())) {
                list.set(i, stack.copy());
                continue;
            }
            ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
            list.set(i, remainder != null ? remainder.create() : ItemStack.EMPTY);
        }
        return list;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<VoterFromCounterRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
