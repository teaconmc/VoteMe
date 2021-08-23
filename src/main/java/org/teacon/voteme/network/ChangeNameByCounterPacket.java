package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ChangeNameByCounterPacket {
    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final String newArtifactName;

    private ChangeNameByCounterPacket(int inventoryIndex, UUID artifactUUID, String newArtifactName) {
        this.inventoryIndex = inventoryIndex;
        this.artifactUUID = artifactUUID;
        this.newArtifactName = newArtifactName;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            boolean isCreating = VoteListHandler.getArtifactName(this.artifactUUID).isEmpty();
            Stream<String> permissions = !isCreating
                    ? Stream.of("voteme.modify.counter", "voteme.modify", "voteme")
                    : Stream.of("voteme.create.counter", "voteme.create", "voteme.admin.create", "voteme.admin", "voteme");
            if (permissions.anyMatch(p -> PermissionAPI.hasPermission(sender, p))) {
                ItemStack stack = sender.inventory.getStackInSlot(this.inventoryIndex);
                if (CounterItem.INSTANCE.equals(stack.getItem())) {
                    CounterItem.INSTANCE.rename(sender, stack, this.artifactUUID, this.newArtifactName);
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(this.inventoryIndex);
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeString(this.newArtifactName);
    }

    public static ChangeNameByCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readVarInt();
        UUID artifactUUID = buffer.readUniqueId();
        String artifactName = buffer.readString(Short.MAX_VALUE);
        return new ChangeNameByCounterPacket(inventoryIndex, artifactUUID, artifactName);
    }

    public static ChangeNameByCounterPacket create(int inventoryIndex, UUID artifactUUID, String newArtifactName) {
        return new ChangeNameByCounterPacket(inventoryIndex, artifactUUID, newArtifactName);
    }
}
