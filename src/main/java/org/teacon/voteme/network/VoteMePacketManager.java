package org.teacon.voteme.network;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme", bus = EventBusSubscriber.Bus.MOD)
public final class VoteMePacketManager {
    public static final String VERSION = "5"; // Last Update: Thu Aug 22 19:34:54 PDT 2024

    @SubscribeEvent
    public static void setup(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("voteme")
                .versioned("0.8.4")
                .versioned(VERSION);
        registrar.playToClient(ShowCounterPacket.TYPE, ShowCounterPacket.STREAM_CODEC, ShowCounterPacket::handle);
        registrar.playToServer(ChangePropsByCounterPacket.TYPE, ChangePropsByCounterPacket.STREAM_CODEC, ChangePropsByCounterPacket::handle);
        registrar.playToServer(ChangeNameByCounterPacket.TYPE, ChangeNameByCounterPacket.STREAM_CODEC, ChangeNameByCounterPacket::handle);
        registrar.playToClient(ShowVoterPacket.TYPE, ShowVoterPacket.STREAM_CODEC, ShowVoterPacket::handle);
        registrar.playToServer(SubmitVotePacket.TYPE,  SubmitVotePacket.STREAM_CODEC, SubmitVotePacket::handle);
        registrar.playToClient(SyncCategoryPacket.TYPE, SyncCategoryPacket.STREAM_CODEC, SyncCategoryPacket::handle);
        registrar.playToClient(SyncArtifactNamePacket.TYPE, SyncArtifactNamePacket.STREAM_CODEC, SyncArtifactNamePacket::handle);
        registrar.playToServer(SubmitCommentPacket.TYPE, SubmitCommentPacket.STREAM_CODEC, SubmitCommentPacket::handle);
    }
}
