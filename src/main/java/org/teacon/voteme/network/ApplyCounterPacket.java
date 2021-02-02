package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.item.CounterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ApplyCounterPacket {
    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final ResourceLocation categoryID;

    private ApplyCounterPacket(int inventoryIndex, UUID artifactUUID, ResourceLocation category) {
        this.inventoryIndex = inventoryIndex;
        this.artifactUUID = artifactUUID;
        this.categoryID = category;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            ItemStack stack = sender.inventory.getStackInSlot(this.inventoryIndex);
            if (CounterItem.INSTANCE.equals(stack.getItem())) {
                CounterItem.INSTANCE.modifyVoteId(sender, stack, this.artifactUUID, this.categoryID);
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeInt(this.inventoryIndex);
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeResourceLocation(this.categoryID);
    }

    public static ApplyCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readInt();
        UUID artifactUUID = buffer.readUniqueId();
        ResourceLocation category = buffer.readResourceLocation();
        return new ApplyCounterPacket(inventoryIndex, artifactUUID, category);
    }

    public static ApplyCounterPacket create(int inventoryIndex, UUID artifactUUID, ResourceLocation category) {
        return new ApplyCounterPacket(inventoryIndex, artifactUUID, category);
    }
}
