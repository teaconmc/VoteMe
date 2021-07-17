package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class EditNamePacket {
    public final UUID artifactUUID;
    public final String newArtifactName;

    private EditNamePacket(UUID artifactUUID, String newArtifactName) {
        this.artifactUUID = artifactUUID;
        this.newArtifactName = newArtifactName;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            VoteListHandler voteListHandler = VoteListHandler.get(Objects.requireNonNull(sender.getServer()));
            voteListHandler.putArtifactName(this.artifactUUID, this.newArtifactName);
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeString(this.newArtifactName);
    }

    public static EditNamePacket read(PacketBuffer buffer) {
        UUID artifactUUID = buffer.readUniqueId();
        String artifactName = buffer.readString();
        return new EditNamePacket(artifactUUID, artifactName);
    }

    public static EditNamePacket create(UUID artifactUUID, String newArtifactName) {
        return new EditNamePacket(artifactUUID, newArtifactName);
    }
}
