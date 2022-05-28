package org.teacon.voteme.network;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.*;

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
            boolean isCreating = VoteArtifactNames.getArtifactName(this.artifactUUID, false).isEmpty();
            Stream<PermissionNode<Boolean>> permissions = isCreating
                    ? Stream.of(CREATE_COUNTER, CREATE, ADMIN_CREATE, ADMIN) : Stream.of(MODIFY_COUNTER, MODIFY);
            if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
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
