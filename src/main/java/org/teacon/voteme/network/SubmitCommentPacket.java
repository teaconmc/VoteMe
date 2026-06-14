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

    /**
     * Maximum permitted length in bytes that a single page of comment may contain.
     * <p>
     * A CJK Unified Ideograph typically has 3 bytes; 1024 would mean ~340 Chinese
     * characters.
     */
    private static final int MAX_LENGTH_PER_PAGE = 1024;
    /**
     * Maximum permitted number of pages that one may comment on a given artifact.
     */
    private static final int MAX_PAGE_NUMBER = 10;

    public static final Type<SubmitCommentPacket> TYPE = new Type<>(Identifier.parse("voteme:submit_comment"));

    public static final StreamCodec<FriendlyByteBuf, SubmitCommentPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, p -> p.artifactID,
            ByteBufCodecs.stringUtf8(MAX_LENGTH_PER_PAGE).apply(ByteBufCodecs.list(MAX_PAGE_NUMBER)), p -> p.comments,
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
