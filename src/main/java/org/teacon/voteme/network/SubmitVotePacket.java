package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteDataStorage;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.teacon.voteme.command.VoteMePermissions.OPEN;
import static org.teacon.voteme.command.VoteMePermissions.OPEN_VOTER;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SubmitVotePacket implements CustomPacketPayload {

    public static final Type<SubmitVotePacket> TYPE = new Type<>(Identifier.parse("voteme:submit_vote"));

    public static final StreamCodec<FriendlyByteBuf, SubmitVotePacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, p -> p.artifactID,
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT), p -> p.entries,
            SubmitVotePacket::new
    );
    public final UUID artifactID;
    public final ImmutableMap<Identifier, Integer> entries;

    private SubmitVotePacket(UUID artifactID, Map<Identifier, Integer> entries) {
        this.artifactID = artifactID;
        this.entries = ImmutableMap.copyOf(entries);
    }

    @Override
    public Type<SubmitVotePacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        ServerPlayer sender = (ServerPlayer) context.player();
        Stream<PermissionNode<Boolean>> permissions = Stream.of(OPEN_VOTER, OPEN);
        if (permissions.anyMatch(p -> PermissionAPI.getPermission(sender, p))) {
            VoteDataStorage handler = VoteDataStorage.get(Objects.requireNonNull(sender.level().getServer()));
            for (Map.Entry<Identifier, Integer> entry : this.entries.entrySet()) {
                Identifier categoryID = entry.getKey();
                if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
                    int id = handler.getIdOrCreate(this.artifactID, categoryID);
                    handler.getVoteList(id).ifPresent(e -> e.set(sender, entry.getValue()));
                }
            }
        }
    }

    public static SubmitVotePacket create(UUID artifactID, Map<Identifier, Integer> entries) {
        return new SubmitVotePacket(artifactID, ImmutableMap.copyOf(entries));
    }
}
