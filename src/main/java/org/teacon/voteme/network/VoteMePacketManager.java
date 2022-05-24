package org.teacon.voteme.network;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMePacketManager {
    public static final String VERSION = "4"; // Last Update: Wed, 25 May 2022 02:00:00 +0800
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("voteme:network"), () -> VERSION, VERSION::equals, VERSION::equals);

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.registerMessage(0, ShowCounterPacket.class,
                ShowCounterPacket::write, ShowCounterPacket::read, ShowCounterPacket::handle);
        CHANNEL.registerMessage(1, ChangePropsByCounterPacket.class,
                ChangePropsByCounterPacket::write, ChangePropsByCounterPacket::read, ChangePropsByCounterPacket::handle);
        CHANNEL.registerMessage(2, ChangeNameByCounterPacket.class,
                ChangeNameByCounterPacket::write, ChangeNameByCounterPacket::read, ChangeNameByCounterPacket::handle);
        CHANNEL.registerMessage(3, ShowVoterPacket.class,
                ShowVoterPacket::write, ShowVoterPacket::read, ShowVoterPacket::handle);
        CHANNEL.registerMessage(4, SubmitVotePacket.class,
                SubmitVotePacket::write, SubmitVotePacket::read, SubmitVotePacket::handle);
        CHANNEL.registerMessage(5, SyncCategoryPacket.class,
                SyncCategoryPacket::write, SyncCategoryPacket::read, SyncCategoryPacket::handle);
        CHANNEL.registerMessage(6, SyncArtifactNamePacket.class,
                SyncArtifactNamePacket::write, SyncArtifactNamePacket::read, SyncArtifactNamePacket::handle);
        CHANNEL.registerMessage(7, SubmitCommentPacket.class,
                SubmitCommentPacket::write, SubmitCommentPacket::read, SubmitCommentPacket::handle);
    }
}
