package org.teacon.voteme.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
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
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterItem extends Item {

    public static final String ID = "voteme:voter";

    @ObjectHolder(ID)
    public static VoterItem INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new VoterItem(new Properties().group(VoteMeItemGroup.INSTANCE)));
    }

    private VoterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT tag = stack.getTag();
        tooltip.add(new StringTextComponent(""));
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            UUID artifactID = tag.getUniqueId("CurrentArtifact");
            if (!VoteListHandler.getArtifactName(artifactID).isEmpty()) {
                IFormattableTextComponent artifactText = VoteListHandler.getArtifactText(artifactID).mergeStyle(TextFormatting.GREEN);
                tooltip.add(new TranslationTextComponent("gui.voteme.voter.current_artifact_hint", artifactText).mergeStyle(TextFormatting.GRAY));
                if (!VoteCategoryHandler.getIds().isEmpty()) {
                    tooltip.add(new StringTextComponent(""));
                }
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                    if (categoryOptional.isPresent()) {
                        ITextComponent categoryName = categoryOptional.get().name;
                        IFormattableTextComponent categoryText = new StringTextComponent("").append(categoryName).mergeStyle(TextFormatting.YELLOW);
                        tooltip.add(new TranslationTextComponent("gui.voteme.counter.category_hint", categoryText).mergeStyle(TextFormatting.GRAY));
                    }
                }
            } else {
                tooltip.add(new TranslationTextComponent("gui.voteme.voter.empty_artifact_hint").mergeStyle(TextFormatting.GRAY));
            }
        } else {
            tooltip.add(new TranslationTextComponent("gui.voteme.voter.empty_artifact_hint").mergeStyle(TextFormatting.GRAY));
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        CompoundNBT tag = itemStack.getTag();
        if (player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            if (this.open(serverPlayer, tag)) {
                return ActionResult.resultConsume(itemStack);
            }
        } else if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            return ActionResult.resultSuccess(itemStack);
        }
        return ActionResult.resultFail(itemStack);
    }

    public boolean open(ServerPlayerEntity player, @Nullable CompoundNBT tag) {
        Stream<String> permissions = Stream.of("voteme.open.voter", "voteme.open", "voteme");
        if (permissions.anyMatch(p -> PermissionAPI.hasPermission(player, p))) {
            Optional<ShowVoterPacket> packet = Optional.empty();
            if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
                packet = ShowVoterPacket.create(tag.getUniqueId("CurrentArtifact"), player);
            }
            if (packet.isPresent()) {
                PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> player);
                VoteMePacketManager.CHANNEL.send(target, packet.get());
                return true;
            }
        }
        return false;
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            UUID artifactID = tag.getUniqueId("CurrentArtifact");
            String artifactName = VoteListHandler.getArtifactName(artifactID);
            if (!artifactName.isEmpty()) {
                return new TranslationTextComponent("item.voteme.voter.with_artifact", artifactName);
            }
        }
        return new TranslationTextComponent("item.voteme.voter");
    }

    public ItemStack copyFrom(int voterSize, ItemStack stack) {
        CompoundNBT tag = stack.getTag(), newTag = new CompoundNBT();
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            newTag.putUniqueId("CurrentArtifact", tag.getUniqueId("CurrentArtifact"));
        }
        ItemStack result = new ItemStack(this, voterSize);
        result.setTag(newTag);
        return result;
    }
}
