package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.screen.VoterScreen;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ShowVoterPacket {
    
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
    public final ImmutableList<Info> infos;
    public final List<String> comments;

    private ShowVoterPacket(UUID artifactID, ImmutableList<Info> infos, List<String> comments) {
        this.artifactID = artifactID;
        this.infos = infos;
        this.comments = comments;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        if (!this.infos.isEmpty()) {
            // forge needs a separate class
            // noinspection Convert2Lambda
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new DistExecutor.SafeRunnable() {
                @Override
                public void run() {
                    ShowVoterPacket p = ShowVoterPacket.this;
                    String artifactName = VoteListHandler.getArtifactName(p.artifactID);
                    if (!artifactName.isEmpty()) {
                        VoterScreen gui = new VoterScreen(p.artifactID, artifactName, p.infos, p.comments);
                        supplier.get().enqueueWork(() -> Minecraft.getInstance().setScreen(gui));
                    }
                }
            });
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeUUID(this.artifactID);
        for (Info info : this.infos) {
            buffer.writeInt(info.level);
            buffer.writeResourceLocation(info.id);
        }
        buffer.writeInt(Integer.MIN_VALUE);
        buffer.writeVarInt(this.comments.size());
        for (int i = 0; i < this.comments.size(); i++) {
            buffer.writeUtf(this.comments.get(i), MAX_LENGTH_PER_PAGE);
        }
    }

    public static ShowVoterPacket read(PacketBuffer buffer) {
        UUID artifactID = buffer.readUUID();
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        for (int level = buffer.readInt(); level != Integer.MIN_VALUE; level = buffer.readInt()) {
            ResourceLocation id = buffer.readResourceLocation();
            Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
            if (categoryOptional.isPresent()) {
                builder.add(new Info(id, categoryOptional.get(), level));
            }
        }
        List<String> comments = new ArrayList<>(MAX_PAGE_NUMBER);
        int pageNum = Math.min(buffer.readVarInt(), MAX_PAGE_NUMBER);
        for (int i = 0; i < pageNum; i++) {
            comments.add(buffer.readUtf(MAX_LENGTH_PER_PAGE));
        }
        return new ShowVoterPacket(artifactID, builder.build(), comments);
    }

    public static Optional<ShowVoterPacket> create(UUID artifactID, ServerPlayerEntity player) {
        if (!VoteListHandler.getArtifactName(artifactID).isEmpty()) {
            VoteListHandler handler = VoteListHandler.get(player.server);
            ImmutableList.Builder<Info> builder = ImmutableList.builder();
            Set<ResourceLocation> categoryIDs = new LinkedHashSet<>();
            for (ResourceLocation roleID : VoteRoleHandler.getRoles(player)) {
                VoteRole role = VoteRoleHandler.getRole(roleID).orElseThrow(IllegalStateException::new);
                categoryIDs.addAll(role.categories.keySet());
            }
            for (ResourceLocation categoryID : categoryIDs) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                VoteListEntry entry = handler.getEntry(id).orElseThrow(IllegalStateException::new);
                VoteCategory category = VoteCategoryHandler.getCategory(categoryID).orElseThrow(IllegalStateException::new);
                if (entry.votes.getEnabled().orElse(category.enabledDefault)) {
                    builder.add(new Info(categoryID, category, entry.votes.get(player)));
                }
            }
            ImmutableList<Info> infos = builder.build();
            List<String> comments = VoteListHandler.getCommentFor(handler, artifactID, player.getUUID());
            if (!infos.isEmpty() || !comments.isEmpty()) {
                return Optional.of(new ShowVoterPacket(artifactID, infos, comments));
            }
        }
        return Optional.empty();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {
        public final int level;
        public final ResourceLocation id;
        public final VoteCategory category;

        public Info(ResourceLocation id, VoteCategory category, int level) {
            this.id = id;
            this.level = level;
            this.category = category;
        }

        @Override
        public String toString() {
            return "ShowVoterPacket.Info{id=" + this.id + "', level=" + this.level + "}";
        }
    }
}
