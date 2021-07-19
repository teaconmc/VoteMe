package org.teacon.voteme.item;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.EditCounterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CounterItem extends Item {

    public static final String ID = "voteme:counter";

    @ObjectHolder(ID)
    public static CounterItem INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new CounterItem(new Properties().maxStackSize(1).group(VoteMeItemGroup.INSTANCE)));
    }

    private CounterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT tag = stack.getTag();
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            UUID artifactID = tag.getUniqueId("CurrentArtifact");
            if (!VoteListHandler.getArtifactName(artifactID).isEmpty()) {
                IFormattableTextComponent artifactText = VoteListHandler.getArtifactText(artifactID).mergeStyle(TextFormatting.GREEN);
                tooltip.add(new TranslationTextComponent("gui.voteme.counter.current_artifact_hint", artifactText).mergeStyle(TextFormatting.GRAY));
                ResourceLocation categoryID = new ResourceLocation(tag.getString("CurrentCategory"));
                Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                if (categoryOptional.isPresent()) {
                    ITextComponent categoryName = categoryOptional.get().name;
                    IFormattableTextComponent categoryText = new StringTextComponent("").append(categoryName).mergeStyle(TextFormatting.YELLOW);
                    tooltip.add(new TranslationTextComponent("gui.voteme.counter.current_category_hint", categoryText).mergeStyle(TextFormatting.GRAY));
                }
            } else {
                tooltip.add(new TranslationTextComponent("gui.voteme.counter.empty_artifact_hint").mergeStyle(TextFormatting.GRAY));
            }
        } else {
            tooltip.add(new TranslationTextComponent("gui.voteme.counter.empty_artifact_hint").mergeStyle(TextFormatting.GRAY));
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        CompoundNBT tag = itemStack.getTag();
        if (player instanceof ServerPlayerEntity) {
            Optional<EditCounterPacket> packet = Optional.empty();
            MinecraftServer server = Objects.requireNonNull(player.getServer());
            int inventoryId = hand == Hand.MAIN_HAND ? player.inventory.currentItem : 40;
            if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
                ResourceLocation category = new ResourceLocation(tag.getString("CurrentCategory"));
                packet = EditCounterPacket.create(inventoryId, tag.getUniqueId("CurrentArtifact"), category, server);
            }
            if (!packet.isPresent()) {
                packet = EditCounterPacket.create(inventoryId, server);
            }
            if (packet.isPresent()) {
                PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player);
                VoteMePacketManager.CHANNEL.send(target, packet.get());
                return ActionResult.resultConsume(itemStack);
            }
        } else {
            return ActionResult.resultSuccess(itemStack);
        }
        return ActionResult.resultFail(itemStack);
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            UUID artifactID = tag.getUniqueId("CurrentArtifact");
            String artifactName = VoteListHandler.getArtifactName(artifactID);
            if (!artifactName.isEmpty()) {
                return new TranslationTextComponent("item.voteme.counter.with_artifact", artifactName);
            }
        }
        return new TranslationTextComponent("item.voteme.counter");
    }

    public void applyChanges(ServerPlayerEntity sender, ItemStack stack, UUID artifactID, ResourceLocation currentCategory,
                             ImmutableList<ResourceLocation> enabledCategories, ImmutableList<ResourceLocation> disabledCategories) {
        boolean matchArtifact = true;
        CompoundNBT tag = stack.getTag();
        VoteListHandler handler = VoteListHandler.get(Objects.requireNonNull(sender.getServer()));
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            String artifactName = VoteListHandler.getArtifactName(artifactID);
            matchArtifact = !artifactName.isEmpty() && tag.getUniqueId("CurrentArtifact").equals(artifactID);
        }
        if (matchArtifact) {
            stack.getOrCreateTag().putString("CurrentCategory", currentCategory.toString());
            stack.getOrCreateTag().putUniqueId("CurrentArtifact", artifactID);
            for (ResourceLocation category : enabledCategories) {
                if (VoteCategoryHandler.getCategory(category).filter(c -> c.enabledModifiable).isPresent()) {
                    int entryID = handler.getIdOrCreate(artifactID, category);
                    handler.getEntry(entryID).ifPresent(entry -> entry.votes.setEnabled(true));
                } else {
                    VoteMe.LOGGER.warn("Unmodifiable vote category {} submitted by {}.", category, sender.getGameProfile());
                }
            }
            for (ResourceLocation category : disabledCategories) {
                if (VoteCategoryHandler.getCategory(category).filter(c -> c.enabledModifiable).isPresent()) {
                    int entryID = handler.getIdOrCreate(artifactID, category);
                    handler.getEntry(entryID).ifPresent(entry -> entry.votes.setEnabled(false));
                } else {
                    VoteMe.LOGGER.warn("Unmodifiable vote category {} submitted by {}.", category, sender.getGameProfile());
                }
            }
        }
    }
}
