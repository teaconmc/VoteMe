package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.item.CounterItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.*;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ChangePropsByCounterPacket implements CustomPacketPayload {

    public static final Type<ChangePropsByCounterPacket> TYPE = new Type<>(Identifier.parse("voteme:change_props_by_counter"));

    public static final StreamCodec<FriendlyByteBuf, ChangePropsByCounterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.inventoryIndex,
            UUIDUtil.STREAM_CODEC, p -> p.artifactUUID,
            Identifier.STREAM_CODEC, p -> p.categoryID,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), p -> p.enabled,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), p -> p.disabled,
            ChangePropsByCounterPacket::new
    );

    @Override
    public Type<ChangePropsByCounterPacket> type() {
        return TYPE;
    }

    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final Identifier categoryID;
    public final ImmutableList<Identifier> enabled;
    public final ImmutableList<Identifier> disabled;

    private ChangePropsByCounterPacket(int inventoryIndex, UUID artifactUUID, Identifier category,
                                       List<Identifier> enabled, List<Identifier> disabled) {
        this.inventoryIndex = inventoryIndex;
        this.artifactUUID = artifactUUID;
        this.categoryID = category;
        this.disabled = ImmutableList.copyOf(disabled);
        this.enabled = ImmutableList.copyOf(enabled);
    }

    public void handle(IPayloadContext context) {
        ServerPlayer sender = (ServerPlayer) context.player();
        Stream<PermissionNode<Boolean>> permissions = Stream.of(SWITCH_COUNTER, SWITCH, ADMIN_SWITCH, ADMIN);
        if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
            ItemStack stack = sender.getInventory().getItem(this.inventoryIndex);
            if (CounterItem.INSTANCE.get().equals(stack.getItem())) {
                CounterItem.INSTANCE.get().applyChanges(sender, stack,
                        this.artifactUUID, this.categoryID, this.enabled, this.disabled);
            }
        }
    }

    public static ChangePropsByCounterPacket create(int inventoryIndex, UUID artifactUUID, Identifier category,
                                                    Iterable<? extends Identifier> enabled, Iterable<? extends Identifier> disabled) {
        ImmutableList<Identifier> wrappedEnabled = ImmutableList.copyOf(enabled);
        ImmutableList<Identifier> wrappedDisabled = ImmutableList.copyOf(disabled);
        if (wrappedEnabled.size() + wrappedDisabled.size() > 0) {
            VoteMe.LOGGER.info("Request for enabling {} and disabling {}.", wrappedEnabled, wrappedDisabled);
        }
        return new ChangePropsByCounterPacket(inventoryIndex, artifactUUID, category, wrappedEnabled, wrappedDisabled);
    }
}
