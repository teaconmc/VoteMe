package org.teacon.voteme.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.voteme.vote.VoteDataStorage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SubmitCommentPacket implements CustomPacketPayload {
    private static final int MAX_PAGE_NUMBER = 10;

    public static final Type<SubmitCommentPacket> TYPE = new Type<>(Identifier.parse("voteme:submit_comment"));

    public static final StreamCodec<FriendlyByteBuf, SubmitCommentPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, p -> p.artifactID,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_PAGE_NUMBER)), p -> p.comments,
            SubmitCommentPacket::create
    );

    public final UUID artifactID;
    public final List<String> comments;
    private transient boolean problematic = false;

    public SubmitCommentPacket(UUID artifactID, List<String> comments) {
        this.artifactID = artifactID;
        this.comments = comments;
    }

    @Override
    public Type<SubmitCommentPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        ServerPlayer sender = (ServerPlayer) context.player();
        VoteDataStorage handler = VoteDataStorage.get(Objects.requireNonNull(sender.level().getServer()));
        if (!this.problematic) {
            VoteDataStorage.putCommentFor(handler, this.artifactID, sender.getUUID(), this.comments);
        }
    }

    public static SubmitCommentPacket create(UUID artifactID, List<String> comments) {
        return new SubmitCommentPacket(artifactID, comments);
    }


}
