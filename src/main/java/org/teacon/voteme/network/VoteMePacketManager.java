package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMePacketManager {
    public static final String VERSION = "1"; // Last Update: Sat, 9 Jan 2021 02:00:00 +0800
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("voteme:network"), () -> VERSION, VERSION::equals, VERSION::equals);

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.registerMessage(0, EditCounterPacket.class,
                EditCounterPacket::write, EditCounterPacket::read, EditCounterPacket::handle);
        CHANNEL.registerMessage(1, ApplyCounterPacket.class,
                ApplyCounterPacket::write, ApplyCounterPacket::read, ApplyCounterPacket::handle);
        CHANNEL.registerMessage(2, EditNamePacket.class,
                EditNamePacket::write, EditNamePacket::read, EditNamePacket::handle);
    }
}
