package org.teacon.voteme.item;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.item.component.ArtifactID;
import org.teacon.voteme.item.component.CategoryID;
import org.teacon.voteme.network.ShowCounterPacket;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class CounterItem extends Item {
    public static final Identifier ID = Identifier.parse("voteme:counter");
    public static final DeferredHolder<Item, CounterItem> INSTANCE = DeferredHolder.create(Registries.ITEM, ID);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.ITEM) {
            return;
        }
        event.register(Registries.ITEM, ID, () -> new CounterItem(ID));
    }

    @SubscribeEvent
    public static void register(BuildCreativeModeTabContentsEvent event) {
        if (VoteMeItemGroup.ID.equals(CreativeModeTabRegistry.getName(event.getTab()))) {
            event.accept(INSTANCE.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private CounterItem(Identifier identifier) {
        super(new Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, identifier)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        UUID artifactID = stack.get(ArtifactID.INSTANCE);
        Optional<VoteArtifactNames> artifactNames = VoteArtifactNames.effective();
        if (artifactNames.isPresent() && artifactID != null && !artifactNames.get().getName(artifactID).isEmpty()) {
            MutableComponent artifactText = artifactNames.get().toText(artifactID).withStyle(ChatFormatting.GREEN);
            tooltip.accept(Component.translatable("gui.voteme.counter.current_artifact_hint", artifactText).withStyle(ChatFormatting.GRAY));
            Identifier currentCategoryID = stack.get(CategoryID.INSTANCE);
            if (!VoteCategoryHandler.getIds().isEmpty()) {
                tooltip.accept(Component.empty());
            }
            for (Identifier categoryID : VoteCategoryHandler.getIds()) {
                Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                if (categoryOptional.isPresent()) {
                    Component categoryName = categoryOptional.get().name;
                    ChatFormatting color = categoryID.equals(currentCategoryID) ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
                    MutableComponent categoryText = Component.empty().append(categoryName).withStyle(color);
                    tooltip.accept(Component.translatable("gui.voteme.counter.category_hint", categoryText).withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            tooltip.accept(Component.translatable("gui.voteme.counter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer) {
            Optional<ShowCounterPacket> packet = Optional.empty();
            MinecraftServer server = Objects.requireNonNull(player.level().getServer());
            int inventoryId = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
            UUID artifactID = itemStack.get(ArtifactID.INSTANCE);
            Identifier category = itemStack.get(CategoryID.INSTANCE);
            if (artifactID != null && category != null) {
                packet = ShowCounterPacket.create(inventoryId, artifactID, category, server);
            }
            if (packet.isEmpty()) {
                packet = ShowCounterPacket.create(inventoryId, uuid -> itemStack.set(ArtifactID.INSTANCE, uuid));
            }
            if (packet.isPresent()) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, packet.get());
                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public Component getName(ItemStack stack) {
        UUID artifactID = stack.get(ArtifactID.INSTANCE);
        if (artifactID != null) {
            Optional<VoteArtifactNames> artifactNames = VoteArtifactNames.effective();
            if (artifactNames.isPresent()) {
                String artifactName = artifactNames.get().getName(artifactID);
                if (!artifactName.isEmpty()) {
                    return Component.translatable("item.voteme.counter.with_artifact", artifactName);
                }
            }
        }
        return Component.translatable("item.voteme.counter");
    }

    public void rename(ServerPlayer sender, ItemStack stack, UUID artifactID, String newArtifactName) {
        if (this.checkMatchedArtifact(stack, artifactID)) {
            VoteArtifactNames artifactNames = VoteDataStorage.get(Objects.requireNonNull(sender.level().getServer())).getArtifactNames();
            artifactNames.putName(sender.createCommandSourceStack(), artifactID, newArtifactName);
        } else {
            VoteMe.LOGGER.warn("Unmatched vote artifact {} submitted by {}.", artifactID, sender.getGameProfile());
        }
    }

    public void applyChanges(ServerPlayer sender, ItemStack stack, UUID artifactID, Identifier currentCategory,
                             ImmutableList<Identifier> enabledCategories, ImmutableList<Identifier> disabledCategories) {
        VoteDataStorage handler = VoteDataStorage.get(Objects.requireNonNull(sender.level().getServer()));
        if (this.checkMatchedArtifact(stack, artifactID)) {
            stack.set(CategoryID.INSTANCE, currentCategory);
            stack.set(ArtifactID.INSTANCE, artifactID);
            for (Identifier category : enabledCategories) {
                if (VoteCategoryHandler.getCategory(category).filter(c -> c.enabledModifiable).isPresent()) {
                    int entryID = handler.getIdOrCreate(artifactID, category);
                    handler.getVoteList(entryID).ifPresent(entry -> entry.setEnabled(true));
                } else {
                    VoteMe.LOGGER.warn("Unmodifiable vote category {} submitted by {}.", category, sender.getGameProfile());
                }
            }
            for (Identifier category : disabledCategories) {
                if (VoteCategoryHandler.getCategory(category).filter(c -> c.enabledModifiable).isPresent()) {
                    int entryID = handler.getIdOrCreate(artifactID, category);
                    handler.getVoteList(entryID).ifPresent(entry -> entry.setEnabled(false));
                } else {
                    VoteMe.LOGGER.warn("Unmodifiable vote category {} submitted by {}.", category, sender.getGameProfile());
                }
            }
        } else {
            VoteMe.LOGGER.warn("Unmatched vote artifact {} submitted by {}.", artifactID, sender.getGameProfile());
        }
    }

    private boolean checkMatchedArtifact(ItemStack stack, UUID artifactID) {
        UUID inStack = stack.get(ArtifactID.INSTANCE);
        if (inStack != null) {
            return inStack.equals(artifactID);
        }
        return false;
    }
}
