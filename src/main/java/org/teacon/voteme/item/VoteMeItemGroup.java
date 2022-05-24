package org.teacon.voteme.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMeItemGroup extends CreativeModeTab {
    public static final VoteMeItemGroup INSTANCE = new VoteMeItemGroup();

    public VoteMeItemGroup() {
        super("voteme");
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(VoterItem.INSTANCE);
    }
}
