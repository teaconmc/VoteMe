package org.teacon.voteme.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMeItemGroup extends ItemGroup {
    public static final VoteMeItemGroup INSTANCE = new VoteMeItemGroup();

    public VoteMeItemGroup() {
        super("voteme");
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ItemStack makeIcon() {
        return new ItemStack(VoterItem.INSTANCE);
    }
}
