package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SubmitVotePacket {
    public final UUID artifactID;
    public final ImmutableMap<ResourceLocation, Integer> entries;

    private SubmitVotePacket(UUID artifactID, ImmutableMap<ResourceLocation, Integer> entries) {
        this.artifactID = artifactID;
        this.entries = entries;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            VoteListHandler handler = VoteListHandler.get(sender.server);
            for (Map.Entry<ResourceLocation, Integer> entry : this.entries.entrySet()) {
                ResourceLocation categoryID = entry.getKey();
                if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
                    int id = handler.getIdOrCreate(this.artifactID, categoryID);
                    handler.getEntry(id).ifPresent(e -> e.votes.set(sender, entry.getValue()));
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeUniqueId(this.artifactID);
        for (Map.Entry<ResourceLocation, Integer> entry : this.entries.entrySet()) {
            buffer.writeInt(entry.getValue());
            buffer.writeResourceLocation(entry.getKey());
        }
        buffer.writeInt(Integer.MIN_VALUE);
    }

    public static SubmitVotePacket read(PacketBuffer buffer) {
        UUID artifactID = buffer.readUniqueId();
        ImmutableMap.Builder<ResourceLocation, Integer> builder = ImmutableMap.builder();
        for (int level = buffer.readInt(); level != Integer.MIN_VALUE; level = buffer.readInt()) {
            builder.put(buffer.readResourceLocation(), level);
        }
        return new SubmitVotePacket(artifactID, builder.build());
    }

    public static SubmitVotePacket create(UUID artifactID, Map<ResourceLocation, Integer> entries) {
        return new SubmitVotePacket(artifactID, ImmutableMap.copyOf(entries));
    }
}
