package org.teacon.voteme.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.teacon.voteme.vote.VoteDataStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class SubmitCommentPacket {

    /**
     * Maximum permitted length in bytes that a single page of comment may contain.
     * <p>
     * A CJK Unified Ideograph typically has 3 bytes; 1024 would means ~340 Chinese
     * characters.
     */
    private static final int MAX_LENGTH_PER_PAGE = 1024;
    /**
     * Maximum permitted number of pages that one may comment on a given artifact.
     */
    private static final int MAX_PAGE_NUMBER = 10;

    public final UUID artifactID;
    public final List<String> comments;
    private transient boolean problematic = false;

    public SubmitCommentPacket(UUID artifactID, List<String> comments) {
        this.artifactID = artifactID;
        this.comments = comments;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = Objects.requireNonNull(ctx.getSender());
            VoteDataStorage handler = VoteDataStorage.get(sender.server);
            if (!this.problematic) {
                VoteDataStorage.putCommentFor(handler, this.artifactID, sender.getUUID(), this.comments);
            }
        });
        ctx.setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.artifactID);
        buffer.writeVarInt(this.comments.size());
        for (String comment : this.comments) {
            buffer.writeUtf(comment, MAX_LENGTH_PER_PAGE);
        }
    }

    public static SubmitCommentPacket read(FriendlyByteBuf buffer) {
        UUID artifactID = buffer.readUUID();
        List<String> comments = new ArrayList<>(MAX_PAGE_NUMBER);
        int claimedSize = buffer.readVarInt();
        int sanitizedSize = Math.min(claimedSize, MAX_PAGE_NUMBER);
        // If the sanitizedSize is non-positive, the loop should exit immediately without trace.
        for (int i = 0; i < sanitizedSize; i++) {
            comments.add(buffer.readUtf(MAX_LENGTH_PER_PAGE));
        }
        SubmitCommentPacket pkt = new SubmitCommentPacket(artifactID, comments);
        if (sanitizedSize != claimedSize) {
            pkt.problematic = true;
        }
        return pkt;
    }

    public static SubmitCommentPacket create(UUID artifactID, List<String> comments) {
        return new SubmitCommentPacket(artifactID, comments);
    }


}
