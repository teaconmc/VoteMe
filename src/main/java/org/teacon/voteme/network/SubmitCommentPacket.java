package org.teacon.voteme.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.vote.VoteListHandler;

public final class SubmitCommentPacket {
    
    /** 
     * Maximum permitted length in bytes that a single page of comment may contain. 
     * 
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
    private transient int readSize = -1;
    
    public SubmitCommentPacket(UUID artifactID, List<String> comments) {
        this.artifactID = artifactID;
        this.comments = comments;
    }
    
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sender = Objects.requireNonNull(ctx.getSender());
            VoteListHandler handler = VoteListHandler.get(sender.server);
            if (!this.problematic) {
                VoteListHandler.putCommentFor(handler, this.artifactID, sender.getUniqueID(), this.comments);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    public void write(PacketBuffer buffer) {
        buffer.writeUniqueId(this.artifactID);
        buffer.writeVarInt(this.comments.size());
        for (int i = 0; i < this.comments.size(); i++) {
            buffer.writeString(this.comments.get(i), MAX_LENGTH_PER_PAGE);
        }
    }
    
    public static SubmitCommentPacket read(PacketBuffer buffer) {
        UUID artifactID = buffer.readUniqueId();
        List<String> comments = new ArrayList<>(MAX_PAGE_NUMBER);
        int claimedSize = buffer.readVarInt();
        int sanitizedSize = Math.min(claimedSize, MAX_PAGE_NUMBER);
        // If the sanitizedSize is non-positive, the loop should exit immediately without trace.
        for (int i = 0; i < sanitizedSize; i++) {
            comments.add(buffer.readString(MAX_LENGTH_PER_PAGE));
        }
        SubmitCommentPacket pkt = new SubmitCommentPacket(artifactID, comments);
        if (sanitizedSize != claimedSize) {
            pkt.problematic = true;
            pkt.readSize = claimedSize;
        }
        return pkt;
    }
    
    public static SubmitCommentPacket create(UUID artifactID, List<String> comments) {
        return new SubmitCommentPacket(artifactID, comments);
    }
    
    
    
}
