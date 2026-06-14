package org.teacon.voteme.item;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.item.component.ArtifactID;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.OPEN;
import static org.teacon.voteme.command.VoteMePermissions.OPEN_VOTER;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoterItem extends Item {

    public static final Identifier ID = Identifier.parse("voteme:voter");

    public static final DeferredHolder<Item, VoterItem> INSTANCE = DeferredHolder.create(Registries.ITEM, ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.ITEM) {
            return;
        }
        event.register(Registries.ITEM, ID, () -> new VoterItem(ID));
    }

    @SubscribeEvent
    public static void register(BuildCreativeModeTabContentsEvent event) {
        if (VoteMeItemGroup.ID.equals(CreativeModeTabRegistry.getName(event.getTab()))) {
            event.accept(INSTANCE.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private VoterItem(Identifier identifier) {
        super(new Properties().setId(ResourceKey.create(Registries.ITEM, identifier)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        UUID artifactID = stack.get(ArtifactID.INSTANCE);
        Optional<VoteArtifactNames> artifactNames = VoteArtifactNames.effective();
        if (artifactNames.isPresent() && artifactID != null && !artifactNames.get().getName(artifactID).isEmpty()) {
            MutableComponent artifactText = artifactNames.get().toText(artifactID).withStyle(ChatFormatting.GREEN);
            tooltip.accept(Component.translatable("gui.voteme.voter.current_artifact_hint", artifactText).withStyle(ChatFormatting.GRAY));
            if (!VoteCategoryHandler.getIds().isEmpty()) {
                tooltip.accept(Component.empty());
            }
            for (Identifier categoryID : VoteCategoryHandler.getIds()) {
                Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                if (categoryOptional.isPresent()) {
                    Component categoryName = categoryOptional.get().name;
                    MutableComponent categoryText = Component.empty().append(categoryName).withStyle(ChatFormatting.YELLOW);
                    tooltip.accept(Component.translatable("gui.voteme.counter.category_hint", categoryText).withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            tooltip.accept(Component.translatable("gui.voteme.voter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (this.open(serverPlayer, itemStack)) {
                return InteractionResult.CONSUME;
            }
        } else if (itemStack.has(ArtifactID.INSTANCE)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public boolean open(ServerPlayer player, ItemStack itemStack) {
        Stream<PermissionNode<Boolean>> permissions = Stream.of(OPEN_VOTER, OPEN);
        if (permissions.anyMatch(p -> PermissionAPI.getPermission(player, p))) {
            Optional<ShowVoterPacket> packet = Optional.empty();
            if (itemStack.has(ArtifactID.INSTANCE)) {
                packet = ShowVoterPacket.create(Objects.requireNonNull(itemStack.get(ArtifactID.INSTANCE), "Artifact ID must not be null here"), player);
            }
            if (packet.isPresent()) {
                PacketDistributor.sendToPlayer(player, packet.get());
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        UUID artifactID = stack.get(ArtifactID.INSTANCE);
        if (artifactID != null) {
            Optional<VoteArtifactNames> artifactNames = VoteArtifactNames.effective();
            if (artifactNames.isPresent()) {
                String artifactName = artifactNames.get().getName(artifactID);
                if (!artifactName.isEmpty()) {
                    return Component.translatable("item.voteme.voter.with_artifact", artifactName);
                }
            }
        }
        return Component.translatable("item.voteme.voter");
    }

    public ItemStack copyFrom(int voterSize, ItemStack stack) {
        ItemStack result = new ItemStack(this, voterSize);
        result.set(ArtifactID.INSTANCE, stack.get(ArtifactID.INSTANCE));
        return result;
    }
}
