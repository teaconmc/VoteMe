package org.teacon.voteme.item;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ObjectHolder;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.OPEN;
import static org.teacon.voteme.command.VoteMePermissions.OPEN_VOTER;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterItem extends Item {

    public static final String ID = "voteme:voter";

    @ObjectHolder(ID)
    public static VoterItem INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new VoterItem(new Properties().tab(VoteMeItemGroup.INSTANCE)));
    }

    private VoterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        tooltip.add(new TextComponent(""));
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            UUID artifactID = tag.getUUID("CurrentArtifact");
            if (!VoteArtifactNames.getArtifactName(artifactID).isEmpty()) {
                MutableComponent artifactText = VoteArtifactNames.getArtifactText(artifactID).withStyle(ChatFormatting.GREEN);
                tooltip.add(new TranslatableComponent("gui.voteme.voter.current_artifact_hint", artifactText).withStyle(ChatFormatting.GRAY));
                if (!VoteCategoryHandler.getIds().isEmpty()) {
                    tooltip.add(new TextComponent(""));
                }
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                    if (categoryOptional.isPresent()) {
                        Component categoryName = categoryOptional.get().name;
                        MutableComponent categoryText = new TextComponent("").append(categoryName).withStyle(ChatFormatting.YELLOW);
                        tooltip.add(new TranslatableComponent("gui.voteme.counter.category_hint", categoryText).withStyle(ChatFormatting.GRAY));
                    }
                }
            } else {
                tooltip.add(new TranslatableComponent("gui.voteme.voter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(new TranslatableComponent("gui.voteme.voter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        CompoundTag tag = itemStack.getTag();
        if (player instanceof ServerPlayer serverPlayer) {
            if (this.open(serverPlayer, tag)) {
                return InteractionResultHolder.consume(itemStack);
            }
        } else if (tag != null && tag.hasUUID("CurrentArtifact")) {
            return InteractionResultHolder.success(itemStack);
        }
        return InteractionResultHolder.fail(itemStack);
    }

    public boolean open(ServerPlayer player, @Nullable CompoundTag tag) {
        Stream<PermissionNode<Boolean>> permissions = Stream.of(OPEN_VOTER, OPEN);
        if (permissions.anyMatch(p -> PermissionAPI.getPermission(player, p))) {
            Optional<ShowVoterPacket> packet = Optional.empty();
            if (tag != null && tag.hasUUID("CurrentArtifact")) {
                packet = ShowVoterPacket.create(tag.getUUID("CurrentArtifact"), player);
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
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            UUID artifactID = tag.getUUID("CurrentArtifact");
            String artifactName = VoteArtifactNames.getArtifactName(artifactID);
            if (!artifactName.isEmpty()) {
                return new TranslatableComponent("item.voteme.voter.with_artifact", artifactName);
            }
        }
        return new TranslatableComponent("item.voteme.voter");
    }

    public ItemStack copyFrom(int voterSize, ItemStack stack) {
        CompoundTag tag = stack.getTag(), newTag = new CompoundTag();
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            newTag.putUUID("CurrentArtifact", tag.getUUID("CurrentArtifact"));
        }
        ItemStack result = new ItemStack(this, voterSize);
        result.setTag(newTag);
        return result;
    }
}
