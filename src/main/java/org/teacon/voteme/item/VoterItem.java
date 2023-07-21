package org.teacon.voteme.item;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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

    public static final ResourceLocation ID = new ResourceLocation("voteme:voter");

    public static final RegistryObject<VoterItem> INSTANCE = RegistryObject.create(ID, ForgeRegistries.ITEMS);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.ITEMS.getRegistryKey(), ID, () -> new VoterItem(new Properties()));
    }

    @SubscribeEvent
    public static void register(BuildCreativeModeTabContentsEvent event) {
        if (VoteMeItemGroup.ID.equals(CreativeModeTabRegistry.getName(event.getTab()))) {
            event.accept(INSTANCE, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private VoterItem(Properties properties) {
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
                tooltip.add(Component.translatable("gui.voteme.voter.current_artifact_hint", artifactText).withStyle(ChatFormatting.GRAY));
                if (!VoteCategoryHandler.getIds().isEmpty()) {
                    tooltip.add(Component.empty());
                }
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                    if (categoryOptional.isPresent()) {
                        Component categoryName = categoryOptional.get().name;
                        MutableComponent categoryText = Component.empty().append(categoryName).withStyle(ChatFormatting.YELLOW);
                        tooltip.add(Component.translatable("gui.voteme.counter.category_hint", categoryText).withStyle(ChatFormatting.GRAY));
                    }
                }
            } else {
                tooltip.add(Component.translatable("gui.voteme.voter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("gui.voteme.voter.empty_artifact_hint").withStyle(ChatFormatting.GRAY));
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
        CompoundTag tag = stack.getTag(), newTag = new CompoundTag();
        if (tag != null && tag.hasUUID("CurrentArtifact")) {
            newTag.putUUID("CurrentArtifact", tag.getUUID("CurrentArtifact"));
        }
        ItemStack result = new ItemStack(this, voterSize);
        result.setTag(newTag);
        return result;
    }
}
