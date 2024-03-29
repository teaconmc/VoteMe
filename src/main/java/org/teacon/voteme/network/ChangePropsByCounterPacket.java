package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.item.CounterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.*;

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
            ServerPlayer sender = Objects.requireNonNull(supplier.get().getSender());
            Stream<PermissionNode<Boolean>> permissions = Stream.of(SWITCH_COUNTER, SWITCH, ADMIN_SWITCH, ADMIN);
            if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
                ItemStack stack = sender.getInventory().getItem(this.inventoryIndex);
                if (CounterItem.INSTANCE.get().equals(stack.getItem())) {
                    CounterItem.INSTANCE.get().applyChanges(sender, stack,
                            this.artifactUUID, this.categoryID, this.enabled, this.disabled);
                }
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.inventoryIndex);
        buffer.writeUUID(this.artifactUUID);
        buffer.writeResourceLocation(this.categoryID);
        buffer.writeInt(this.enabled.size());
        buffer.writeInt(this.disabled.size());
        this.enabled.forEach(buffer::writeResourceLocation);
        this.disabled.forEach(buffer::writeResourceLocation);
    }

    public static ChangePropsByCounterPacket read(FriendlyByteBuf buffer) {
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
