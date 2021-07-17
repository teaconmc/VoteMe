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
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.voteme.network.EditCounterPacket;
import org.teacon.voteme.network.ShowVoterPacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoterItem extends Item {

    public static final String ID = "voteme:voter";

    @ObjectHolder(ID)
    public static VoterItem INSTANCE;

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new VoterItem(new Properties().maxStackSize(1).group(ItemGroup.MISC)));
    }

    private VoterItem(Properties properties) {
        super(properties);
        this.setRegistryName(ID);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (player instanceof ServerPlayerEntity) {
            CompoundNBT tag = itemStack.getTag();
            Optional<ShowVoterPacket> packet = Optional.empty();
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            if (tag != null && tag.hasUniqueId("CurrentArtifact")) {
                packet = ShowVoterPacket.create(tag.getUniqueId("CurrentArtifact"), serverPlayer);
            }
            if (packet.isPresent()) {
                PacketDistributor.PacketTarget target = PacketDistributor.PLAYER.with(() -> serverPlayer);
                VoteMePacketManager.CHANNEL.send(target, packet.get());
            }
        }
        return ActionResult.func_233538_a_(itemStack, world.isRemote());
    }
}
