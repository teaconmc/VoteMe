package org.teacon.voteme.network;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteDataStorage;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.OPEN;
import static org.teacon.voteme.command.VoteMePermissions.OPEN_VOTER;

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
            ServerPlayer sender = Objects.requireNonNull(supplier.get().getSender());
            Stream<PermissionNode<Boolean>> permissions = Stream.of(OPEN_VOTER, OPEN);
            if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
                VoteDataStorage handler = VoteDataStorage.get(sender.server);
                for (Map.Entry<ResourceLocation, Integer> entry : this.entries.entrySet()) {
                    ResourceLocation categoryID = entry.getKey();
                    if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
                        int id = handler.getIdOrCreate(this.artifactID, categoryID);
                        handler.getVoteList(id).ifPresent(e -> e.set(sender, entry.getValue()));
                    }
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.artifactID);
        for (Map.Entry<ResourceLocation, Integer> entry : this.entries.entrySet()) {
            buffer.writeInt(entry.getValue());
            buffer.writeResourceLocation(entry.getKey());
        }
        buffer.writeInt(Integer.MIN_VALUE);
    }

    public static SubmitVotePacket read(FriendlyByteBuf buffer) {
        UUID artifactID = buffer.readUUID();
        ImmutableMap.Builder<ResourceLocation, Integer> builder = ImmutableMap.builder();
        for (int level = buffer.readInt(); level != Integer.MIN_VALUE; level = buffer.readInt()) {
            Preconditions.checkArgument(level >= 0 && level <= 5);
            builder.put(buffer.readResourceLocation(), level);
        }
        return new SubmitVotePacket(artifactID, builder.build());
    }

    public static SubmitVotePacket create(UUID artifactID, Map<ResourceLocation, Integer> entries) {
        return new SubmitVotePacket(artifactID, ImmutableMap.copyOf(entries));
    }
}
