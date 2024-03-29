package org.teacon.voteme.item;

import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.ShowCounterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;

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

    public static final ResourceLocation ID = new ResourceLocation("voteme:counter");

    public static final RegistryObject<CounterItem> INSTANCE = RegistryObject.create(ID, ForgeRegistries.ITEMS);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.ITEMS.getRegistryKey(), ID, () -> new CounterItem(new Properties().stacksTo(1)));
    }

    @SubscribeEvent
    public static void register(BuildCreativeModeTabContentsEvent event) {
        if (VoteMeItemGroup.ID.equals(CreativeModeTabRegistry.getName(event.getTab()))) {
            event.accept(INSTANCE, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private CounterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        tooltip.add(Component.empty());
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            UUID artifactID = tag.getUUID("CurrentArtifact");
            Optional<VoteArtifactNames> artifactNames = VoteArtifactNames.effective();
            if (artifactNames.isPresent() && !artifactNames.get().getName(artifactID).isEmpty()) {
                MutableComponent artifactText = artifactNames.get().toText(artifactID).withStyle(ChatFormatting.GREEN);
                tooltip.add(Component.translatable("gui.voteme.counter.current_artifact_hint", artifactText).withStyle(ChatFormatting.GRAY));
                ResourceLocation currentCategoryID = new ResourceLocation(tag.getString("CurrentCategory"));
                if (!VoteCategoryHandler.getIds().isEmpty()) {
                    tooltip.add(Component.empty());
                }
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                    if (categoryOptional.isPresent()) {
                        Component categoryName = categoryOptional.get().name;
                        ChatFormatting color = categoryID.equals(currentCategoryID) ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
                        MutableComponent categoryText = Component.empty().append(categoryName).withStyle(color);
                        tooltip.add(Component.translatable("gui.voteme.counter.category_hint", categoryText).withStyle(ChatFormatting.GRAY));
                    }
                }
            } else {
                tooltip.add(Component.translatable("gui.voteme.counter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("gui.voteme.counter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        CompoundTag tag = itemStack.getOrCreateTag();
        if (player instanceof ServerPlayer) {
            Optional<ShowCounterPacket> packet = Optional.empty();
            MinecraftServer server = Objects.requireNonNull(player.getServer());
            int inventoryId = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            if (tag.hasUUID("CurrentArtifact")) {
                ResourceLocation category = new ResourceLocation(tag.getString("CurrentCategory"));
                packet = ShowCounterPacket.create(inventoryId, tag.getUUID("CurrentArtifact"), category, server);
            }
            if (packet.isEmpty()) {
                packet = ShowCounterPacket.create(inventoryId, uuid -> tag.putUUID("CurrentArtifact", uuid));
            }
            if (packet.isPresent()) {
                PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> (ServerPlayer) player);
                VoteMePacketManager.CHANNEL.send(target, packet.get());
                return InteractionResultHolder.consume(itemStack);
            }
        } else {
            return InteractionResultHolder.success(itemStack);
        }
        return InteractionResultHolder.fail(itemStack);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            UUID artifactID = tag.getUUID("CurrentArtifact");
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
            VoteArtifactNames artifactNames = VoteDataStorage.get(sender.server).getArtifactNames();
            artifactNames.putName(sender.createCommandSourceStack(), artifactID, newArtifactName);
        } else {
            VoteMe.LOGGER.warn("Unmatched vote artifact {} submitted by {}.", artifactID, sender.getGameProfile());
        }
    }

    public void applyChanges(ServerPlayer sender, ItemStack stack, UUID artifactID, ResourceLocation currentCategory,
                             ImmutableList<ResourceLocation> enabledCategories, ImmutableList<ResourceLocation> disabledCategories) {
        VoteDataStorage handler = VoteDataStorage.get(Objects.requireNonNull(sender.getServer()));
        if (this.checkMatchedArtifact(stack, artifactID)) {
            stack.getOrCreateTag().putString("CurrentCategory", currentCategory.toString());
            stack.getOrCreateTag().putUUID("CurrentArtifact", artifactID);
            for (ResourceLocation category : enabledCategories) {
                if (VoteCategoryHandler.getCategory(category).filter(c -> c.enabledModifiable).isPresent()) {
                    int entryID = handler.getIdOrCreate(artifactID, category);
                    handler.getVoteList(entryID).ifPresent(entry -> entry.setEnabled(true));
                } else {
                    VoteMe.LOGGER.warn("Unmodifiable vote category {} submitted by {}.", category, sender.getGameProfile());
                }
            }
            for (ResourceLocation category : disabledCategories) {
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
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            return tag.getUUID("CurrentArtifact").equals(artifactID);
        }
        return false;
    }
}
