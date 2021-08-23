package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.item.CounterItem;

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
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            ItemStack stack = sender.inventory.getStackInSlot(this.inventoryIndex);
            if (CounterItem.INSTANCE.equals(stack.getItem())) {
                CounterItem.INSTANCE.rename(sender, stack, this.artifactUUID, this.newArtifactName);
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
