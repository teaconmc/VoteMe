package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.item.CounterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ApplyCounterPacket {
    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final ResourceLocation categoryID;
    public final ImmutableList<ResourceLocation> enabled;
    public final ImmutableList<ResourceLocation> disabled;

    private ApplyCounterPacket(int inventoryIndex, UUID artifactUUID, ResourceLocation category,
                               ImmutableList<ResourceLocation> enabled, ImmutableList<ResourceLocation> disabled) {
        this.inventoryIndex = inventoryIndex;
        this.artifactUUID = artifactUUID;
        this.categoryID = category;
        this.disabled = disabled;
        this.enabled = enabled;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
            ItemStack stack = sender.inventory.getStackInSlot(this.inventoryIndex);
            if (CounterItem.INSTANCE.equals(stack.getItem())) {
                CounterItem.INSTANCE.applyChanges(sender, stack, this.artifactUUID, this.categoryID, this.enabled, this.disabled);
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeInt(this.inventoryIndex);
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeResourceLocation(this.categoryID);
        buffer.writeInt(this.enabled.size());
        buffer.writeInt(this.disabled.size());
        this.enabled.forEach(buffer::writeResourceLocation);
        this.disabled.forEach(buffer::writeResourceLocation);
    }

    public static ApplyCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readInt();
        UUID artifactUUID = buffer.readUniqueId();
        ResourceLocation category = buffer.readResourceLocation();
        int enabledSize = buffer.readInt(), disabledSize = buffer.readInt();
        ImmutableList.Builder<ResourceLocation> enabledBuilder = ImmutableList.builder();
        ImmutableList.Builder<ResourceLocation> disabledBuilder = ImmutableList.builder();
        for (int i = 0; i < enabledSize; ++i) {
            enabledBuilder.add(buffer.readResourceLocation());
        }
        for (int i = 0; i < disabledSize; ++i) {
            disabledBuilder.add(buffer.readResourceLocation());
        }
        return new ApplyCounterPacket(inventoryIndex, artifactUUID, category, enabledBuilder.build(), disabledBuilder.build());
    }

    public static ApplyCounterPacket create(int inventoryIndex, UUID artifactUUID, ResourceLocation category,
                                            Iterable<? extends ResourceLocation> enabled, Iterable<? extends ResourceLocation> disabled) {
        ImmutableList<ResourceLocation> wrappedEnabled = ImmutableList.copyOf(enabled);
        ImmutableList<ResourceLocation> wrappedDisabled = ImmutableList.copyOf(disabled);
        if (wrappedEnabled.size() + wrappedDisabled.size() > 0) {
            VoteMe.LOGGER.info("Request for enabling {} and disabling {}.", wrappedEnabled, wrappedDisabled);
        }
        return new ApplyCounterPacket(inventoryIndex, artifactUUID, category, wrappedEnabled, wrappedDisabled);
    }
}
