package org.teacon.voteme.network;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

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
            ServerPlayer sender = Objects.requireNonNull(supplier.get().getSender());
            boolean isCreating = VoteListHandler.getArtifactName(this.artifactUUID).isEmpty();
            /* TODO
            Stream<String> permissions = !isCreating
                    ? Stream.of("voteme.modify.counter", "voteme.modify", "voteme")
                    : Stream.of("voteme.create.counter", "voteme.create", "voteme.admin.create", "voteme.admin", "voteme");
            if (permissions.anyMatch(p -> PermissionAPI.hasPermission(sender, p)))*/
            {
                ItemStack stack = sender.getInventory().getItem(this.inventoryIndex);
                if (CounterItem.INSTANCE.equals(stack.getItem())) {
                    CounterItem.INSTANCE.rename(sender, stack, this.artifactUUID, this.newArtifactName);
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.inventoryIndex);
        buffer.writeUUID(this.artifactUUID);
        buffer.writeUtf(this.newArtifactName);
    }

    public static ChangeNameByCounterPacket read(FriendlyByteBuf buffer) {
        int inventoryIndex = buffer.readVarInt();
        UUID artifactUUID = buffer.readUUID();
        String artifactName = buffer.readUtf(Short.MAX_VALUE);
        return new ChangeNameByCounterPacket(inventoryIndex, artifactUUID, artifactName);
    }

    public static ChangeNameByCounterPacket create(int inventoryIndex, UUID artifactUUID, String newArtifactName) {
        return new ChangeNameByCounterPacket(inventoryIndex, artifactUUID, newArtifactName);
    }
}
