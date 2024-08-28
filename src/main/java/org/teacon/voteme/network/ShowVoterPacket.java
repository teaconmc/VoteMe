package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.screen.VoterScreen;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ShowVoterPacket implements CustomPacketPayload {

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

    public static final Type<ShowVoterPacket> TYPE = new Type<>(ResourceLocation.parse("voteme:show_voter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowVoterPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, p -> p.artifactID,
            Info.STREAM_CODEC.apply(ByteBufCodecs.list()), p -> p.infos,
            ByteBufCodecs.stringUtf8(MAX_LENGTH_PER_PAGE).apply(ByteBufCodecs.list(MAX_PAGE_NUMBER)), p -> p.comments,
            ShowVoterPacket::new
    );

    public final UUID artifactID;
    public final ImmutableList<Info> infos;
    public final List<String> comments;

    private ShowVoterPacket(UUID artifactID, List<Info> infos, List<String> comments) {
        this.artifactID = artifactID;
        this.infos = ImmutableList.copyOf(infos);
        this.comments = comments;
    }

    @Override
    public Type<ShowVoterPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        Handler.handle(this, context);
    }

    public static Optional<ShowVoterPacket> create(UUID artifactID, ServerPlayer player) {
        VoteArtifactNames artifactNames = VoteDataStorage.get(player.server).getArtifactNames();
        if (!artifactNames.getName(artifactID).isEmpty()) {
            VoteDataStorage handler = VoteDataStorage.get(player.server);
            ImmutableList.Builder<Info> builder = ImmutableList.builder();
            Set<ResourceLocation> categoryIDs = new LinkedHashSet<>();
            for (ResourceLocation roleID : VoteRoleHandler.getRoles(player)) {
                VoteRole role = VoteRoleHandler.getRole(roleID).orElseThrow(IllegalStateException::new);
                categoryIDs.addAll(role.categories.keySet());
            }
            for (ResourceLocation categoryID : categoryIDs) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                VoteList entry = handler.getVoteList(id).orElseThrow(IllegalStateException::new);
                VoteCategory category = VoteCategoryHandler.getCategory(categoryID).orElseThrow(IllegalStateException::new);
                if (entry.getEnabled().orElse(category.enabledDefault)) {
                    builder.add(new Info(categoryID, category, entry.get(player)));
                }
            }
            List<String> comments = VoteDataStorage.getCommentFor(handler, artifactID, player.getUUID());
            return Optional.of(new ShowVoterPacket(artifactID, builder.build(), comments));
        }
        return Optional.empty();
    }

    static final class Handler {
        static void handle(ShowVoterPacket packet, IPayloadContext context) {
            // forge needs a separate class
            if (FMLEnvironment.dist == Dist.CLIENT) {
                String artifactName = VoteArtifactNames.client().getName(packet.artifactID);
                if (!artifactName.isEmpty()) {
                    VoterScreen gui = new VoterScreen(packet.artifactID, artifactName, packet.infos, packet.comments);
                    context.enqueueWork(() -> Minecraft.getInstance().setScreen(gui));
                }
            };
        }
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {

        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, info -> info.id,
                VoteCategory.STREAM_CODEC, info -> info.category,
                ByteBufCodecs.VAR_INT, info -> info.level,
                Info::new
        );

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
