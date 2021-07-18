package org.teacon.voteme.item;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.EditCounterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
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
        event.getRegistry().register(new CounterItem(new Properties().maxStackSize(1).group(ItemGroup.MISC)));
    }

    private CounterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (player instanceof ServerPlayerEntity) {
            CompoundNBT tag = itemStack.getTag();
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
            }
        }
        return ActionResult.func_233538_a_(itemStack, world.isRemote());
    }

    public void applyChanges(ServerPlayerEntity sender, ItemStack stack, UUID artifactID, ResourceLocation currentCategory,
                             ImmutableList<ResourceLocation> enabledCategories, ImmutableList<ResourceLocation> disabledCategories) {
        boolean matchArtifact = true;
        CompoundNBT tag = stack.getTag();
        VoteListHandler handler = VoteListHandler.get(Objects.requireNonNull(sender.getServer()));
        if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
            Collection<? extends UUID> artifacts = handler.getArtifacts();
            matchArtifact = artifacts.contains(artifactID) && tag.getUniqueId("CurrentArtifact").equals(artifactID);
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
