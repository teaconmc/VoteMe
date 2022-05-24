package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.item.CounterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ChangePropsByCounterPacket {
    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final ResourceLocation categoryID;
    public final ImmutableList<ResourceLocation> enabled;
    public final ImmutableList<ResourceLocation> disabled;

    private ChangePropsByCounterPacket(int inventoryIndex, UUID artifactUUID, ResourceLocation category,
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
            Stream<String> permissions = Stream.of("voteme.switch.counter", "voteme.switch", "voteme.admin.switch", "voteme.admin", "voteme");
            if (permissions.anyMatch(p -> PermissionAPI.hasPermission(sender, p))) {
                ItemStack stack = sender.inventory.getItem(this.inventoryIndex);
                if (CounterItem.INSTANCE.equals(stack.getItem())) {
                    CounterItem.INSTANCE.applyChanges(sender, stack, this.artifactUUID, this.categoryID, this.enabled, this.disabled);
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeVarInt(this.inventoryIndex);
        buffer.writeUUID(this.artifactUUID);
        buffer.writeResourceLocation(this.categoryID);
        buffer.writeInt(this.enabled.size());
        buffer.writeInt(this.disabled.size());
        this.enabled.forEach(buffer::writeResourceLocation);
        this.disabled.forEach(buffer::writeResourceLocation);
    }

    public static ChangePropsByCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readVarInt();
        UUID artifactUUID = buffer.readUUID();
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
        return new ChangePropsByCounterPacket(inventoryIndex, artifactUUID, category, enabledBuilder.build(), disabledBuilder.build());
    }

    public static ChangePropsByCounterPacket create(int inventoryIndex, UUID artifactUUID, ResourceLocation category,
                                                    Iterable<? extends ResourceLocation> enabled, Iterable<? extends ResourceLocation> disabled) {
        ImmutableList<ResourceLocation> wrappedEnabled = ImmutableList.copyOf(enabled);
        ImmutableList<ResourceLocation> wrappedDisabled = ImmutableList.copyOf(disabled);
        if (wrappedEnabled.size() + wrappedDisabled.size() > 0) {
            VoteMe.LOGGER.info("Request for enabling {} and disabling {}.", wrappedEnabled, wrappedDisabled);
        }
        return new ChangePropsByCounterPacket(inventoryIndex, artifactUUID, category, wrappedEnabled, wrappedDisabled);
    }
}
