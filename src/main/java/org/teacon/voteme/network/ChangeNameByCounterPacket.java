package org.teacon.voteme.network;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ChangeNameByCounterPacket implements CustomPacketPayload {

    public static final Type<ChangeNameByCounterPacket> TYPE = new Type<>(ResourceLocation.parse("voteme:change_name_by_counter"));

    public static final StreamCodec<FriendlyByteBuf, ChangeNameByCounterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.inventoryIndex,
            UUIDUtil.STREAM_CODEC, p -> p.artifactUUID,
            ByteBufCodecs.STRING_UTF8, p -> p.newArtifactName,
            ChangeNameByCounterPacket::new
    );

    public final int inventoryIndex;
    public final UUID artifactUUID;
    public final String newArtifactName;

    private ChangeNameByCounterPacket(int inventoryIndex, UUID artifactUUID, String newArtifactName) {
        this.inventoryIndex = inventoryIndex;
        this.artifactUUID = artifactUUID;
        this.newArtifactName = newArtifactName;
    }

    @Override
    public Type<ChangeNameByCounterPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        ServerPlayer sender = (ServerPlayer) context.player();
        VoteArtifactNames artifactNames = VoteDataStorage.get(sender.server).getArtifactNames();
        boolean isCreating = artifactNames.getName(this.artifactUUID).isEmpty();
        Stream<PermissionNode<Boolean>> permissions = isCreating
                ? Stream.of(CREATE_COUNTER, CREATE, ADMIN_CREATE, ADMIN) : Stream.of(MODIFY_COUNTER, MODIFY);
        if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
            ItemStack stack = sender.getInventory().getItem(this.inventoryIndex);
            if (CounterItem.INSTANCE.get().equals(stack.getItem())) {
                CounterItem.INSTANCE.get().rename(sender, stack, this.artifactUUID, this.newArtifactName);
            }
        }
    }

    public static ChangeNameByCounterPacket create(int inventoryIndex, UUID artifactUUID, String newArtifactName) {
        return new ChangeNameByCounterPacket(inventoryIndex, artifactUUID, newArtifactName);
    }
}
