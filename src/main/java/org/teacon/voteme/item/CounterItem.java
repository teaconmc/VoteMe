package org.teacon.voteme.item;

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
import org.teacon.voteme.network.EditCounterPacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
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
            if (tag != null && tag.contains("CurrentVoteId", Constants.NBT.TAG_INT)) {
                packet = EditCounterPacket.create(tag.getInt("CurrentVoteId"), inventoryId, server);
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

    public void modifyVoteId(ServerPlayerEntity sender, ItemStack stack, UUID artifactID, ResourceLocation category) {
        boolean matchArtifact = true;
        CompoundNBT tag = stack.getTag();
        VoteListHandler voteListHandler = VoteListHandler.get(Objects.requireNonNull(sender.getServer()));
        if (tag != null && tag.contains("CurrentVoteId", Constants.NBT.TAG_INT)) {
            Optional<VoteListEntry> entryOptional = voteListHandler.getEntry(tag.getInt("CurrentVoteId"));
            if (entryOptional.isPresent()) {
                matchArtifact = entryOptional.get().artifactID.equals(artifactID);
            }
        }
        if (matchArtifact) {
            int id = voteListHandler.getIdOrCreate(artifactID, category);
            stack.getOrCreateTag().putInt("CurrentVoteId", id);
        }
    }
}
