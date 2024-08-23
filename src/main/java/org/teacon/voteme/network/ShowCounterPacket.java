package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.screen.CounterScreen;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ShowCounterPacket implements CustomPacketPayload {

    public static final Type<ShowCounterPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("voteme", "show_counter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowCounterPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.invIndex,
            UUIDUtil.STREAM_CODEC, p -> p.artifactUUID,
            ResourceLocation.STREAM_CODEC, p -> p.category,
            Info.STREAM_CODEC.apply(ByteBufCodecs.list()), p -> p.infos,
            ShowCounterPacket::new
    );

    public final int invIndex;
    public final UUID artifactUUID;
    public final ResourceLocation category;
    public final ImmutableList<Info> infos;

    private ShowCounterPacket(int invIndex, UUID uuid, ResourceLocation category, List<Info> infos) {
        this.invIndex = invIndex;
        this.artifactUUID = uuid;
        this.category = category;
        this.infos = ImmutableList.copyOf(infos);
    }

    @Override
    public Type<ShowCounterPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (!this.infos.isEmpty()) {
            // neoforge claims this is sufficient
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ShowCounterPacket p = ShowCounterPacket.this;
                String artifactName = VoteArtifactNames.client().getName(p.artifactUUID);
                CounterScreen gui = new CounterScreen(p.artifactUUID, artifactName, p.invIndex, p.category, p.infos);
                context.enqueueWork(() -> Minecraft.getInstance().setScreen(gui));
            }
        }
    }

    public static Optional<ShowCounterPacket> create(int inventoryId, UUID artifactID, ResourceLocation categoryID, MinecraftServer server) {
        VoteArtifactNames artifactNames = VoteDataStorage.get(server).getArtifactNames();
        if (!artifactNames.getName(artifactID).isEmpty()) {
            boolean isValidCategoryID = false;
            VoteDataStorage handler = VoteDataStorage.get(server);
            ImmutableList.Builder<Info> builder = ImmutableList.builder();
            for (ResourceLocation location : VoteCategoryHandler.getIds()) {
                isValidCategoryID = isValidCategoryID || location.equals(categoryID);
                VoteCategory category = VoteCategoryHandler.getCategory(location).orElseThrow(IllegalStateException::new);
                VoteList entry = handler.getVoteList(handler.getIdOrCreate(artifactID, location)).orElseThrow(IllegalStateException::new);
                boolean enabledCurrently = entry.getEnabled().orElse(category.enabledDefault);
                if (category.enabledDefault || category.enabledModifiable || enabledCurrently) {
                    ImmutableList.Builder<Pair<Component, VoteList.Stats>> scoresBuilder = ImmutableList.builder();
                    entry.buildStatsMap().forEach((subgroup, scores) -> scoresBuilder.add(Pair.of(Optional
                            .ofNullable(ResourceLocation.tryParse(subgroup)).flatMap(VoteRoleHandler::getRole)
                            .map(role -> role.name).orElse(Component.literal(subgroup)), scores)));
                    builder.add(new Info(location, category, scoresBuilder.build(), enabledCurrently));
                }
            }
            ImmutableList<Info> infos = builder.build();
            if (!infos.isEmpty()) {
                if (!isValidCategoryID) {
                    categoryID = infos.iterator().next().id;
                }
                return Optional.of(new ShowCounterPacket(inventoryId, artifactID, categoryID, infos));
            }
        }
        return Optional.empty();
    }

    public static Optional<ShowCounterPacket> create(int inventoryId, Consumer<UUID> artifactUUIDConsumer) {
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        VoteCategoryHandler.getIds().forEach(location -> {
            VoteCategory category = VoteCategoryHandler.getCategory(location).orElseThrow(IllegalStateException::new);
            if (category.enabledDefault || category.enabledModifiable) {
                builder.add(new Info(location, category, ImmutableList.of(), category.enabledDefault));
            }
        });
        ImmutableList<Info> infos = builder.build();
        if (!infos.isEmpty()) {
            UUID newArtifactUUID = UUID.randomUUID();
            artifactUUIDConsumer.accept(newArtifactUUID);
            ResourceLocation categoryID = infos.iterator().next().id;
            return Optional.of(new ShowCounterPacket(inventoryId, newArtifactUUID, categoryID, infos));
        }
        return Optional.empty();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {
        public final ResourceLocation id;
        public final VoteCategory category;
        public final boolean enabledCurrently;
        public final VoteList.Stats finalStat;
        public final List<Pair<Component, VoteList.Stats>> scores;

        public Info(ResourceLocation id, VoteCategory category, List<Pair<Component, VoteList.Stats>> scores, boolean enabledCurrently) {
            this.id = id;
            this.scores = scores;
            this.category = category;
            this.enabledCurrently = enabledCurrently;
            this.finalStat = VoteList.Stats.combine(() -> scores.stream().map(Pair::getValue).iterator(), VoteList.Stats::getWeight);
        }

        @Override
        public String toString() {
            return "EditCounterPacker.Info{id='" + this.id + ", scores=" + this.scores + ", enabled=" + this.enabledCurrently + "}";
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, info -> info.id,
                VoteCategory.STREAM_CODEC, info -> info.category,
                VoteMeStreamUtils.pair(ComponentSerialization.STREAM_CODEC, VoteList.Stats.STREAM_CODEC).apply(ByteBufCodecs.list()), info -> info.scores,
                ByteBufCodecs.BOOL, info -> info.enabledCurrently,
                Info::new
        );
    }
}
