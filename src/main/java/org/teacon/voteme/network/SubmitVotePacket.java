package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SubmitVotePacket {
    public final ImmutableMap<Integer, Integer> entries;

    public SubmitVotePacket(ImmutableMap<Integer, Integer> entries) {
        this.entries = entries;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            VoteListHandler handler = VoteListHandler.get(sender.server);
            for (Map.Entry<Integer, Integer> entry : this.entries.entrySet()) {
                handler.getEntry(entry.getKey()).ifPresent(e -> e.votes.set(sender, entry.getValue()));
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        for (Map.Entry<Integer, Integer> entry : this.entries.entrySet()) {
            buffer.writeInt(entry.getValue());
            buffer.writeInt(entry.getKey());
        }
        buffer.writeInt(Integer.MIN_VALUE);
    }

    public static SubmitVotePacket read(PacketBuffer buffer) {
        ImmutableMap.Builder<Integer, Integer> builder = ImmutableMap.builder();
        for (int level = buffer.readInt(); level != Integer.MIN_VALUE; level = buffer.readInt()) {
            builder.put(buffer.readInt(), level);
        }
        return new SubmitVotePacket(builder.build());
    }

    public static SubmitVotePacket create(Map<Integer, Integer> entries) {
        return new SubmitVotePacket(ImmutableMap.copyOf(entries));
    }
}
