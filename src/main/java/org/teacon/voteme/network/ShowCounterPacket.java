package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.screen.CounterScreen;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ShowCounterPacket {
    public final int invIndex;
    public final UUID artifactUUID;
    public final ResourceLocation category;
    public final ImmutableList<Info> infos;

    private ShowCounterPacket(int invIndex, UUID uuid, ResourceLocation category, ImmutableList<Info> infos) {
        this.invIndex = invIndex;
        this.artifactUUID = uuid;
        this.category = category;
        this.infos = infos;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        if (!this.infos.isEmpty()) {
            // forge needs a separate class
            // noinspection Convert2Lambda
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new DistExecutor.SafeRunnable() {
                @Override
                public void run() {
                    ShowCounterPacket p = ShowCounterPacket.this;
                    String artifactName = VoteListHandler.getArtifactName(p.artifactUUID);
                    CounterScreen gui = new CounterScreen(p.artifactUUID, artifactName, p.invIndex, p.category, p.infos);
                    supplier.get().enqueueWork(() -> Minecraft.getInstance().setScreen(gui));
                }
            });
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.invIndex);
        buffer.writeUUID(this.artifactUUID);
        buffer.writeResourceLocation(this.category);
        buffer.writeVarInt(this.infos.size());
        for (Info info : this.infos) {
            for (Pair<Component, VoteList.Stats> entry : info.scores) {
                VoteList.Stats stats = entry.getValue();
                buffer.writeFloat(stats.getWeight());
                buffer.writeFloat(stats.getFinalScore(Float.NaN));
                buffer.writeVarInt(stats.getEffectiveCount());
                buffer.writeVarIntArray(stats.getVoteCountArray());
                buffer.writeComponent(entry.getKey());
            }
            buffer.writeFloat(Float.NaN);
            buffer.writeResourceLocation(info.id);
            buffer.writeBoolean(info.enabledCurrently);
        }
    }

    public static ShowCounterPacket read(FriendlyByteBuf buffer) {
        int inventoryIndex = buffer.readInt();
        UUID artifactUUID = buffer.readUUID();
        ResourceLocation category = buffer.readResourceLocation();
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        for (int i = 0, size = buffer.readVarInt(); i < size; ++i) {
            ImmutableList.Builder<Pair<Component, VoteList.Stats>> scoresBuilder = ImmutableList.builder();
            for (float weight = buffer.readFloat(); !Float.isNaN(weight); weight = buffer.readFloat()) {
                float finalScore = buffer.readFloat();
                int effectiveVoteCount = buffer.readVarInt();
                int[] countsByLevel = buffer.readVarIntArray(6);
                Component subgroup = buffer.readComponent();
                scoresBuilder.add(Pair.of(subgroup, new VoteList.Stats(weight, finalScore, effectiveVoteCount, countsByLevel)));
            }
            ResourceLocation id = buffer.readResourceLocation();
            boolean categoryEnabledCurrently = buffer.readBoolean();
            Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
            if (categoryOptional.isPresent()) {
                Info info = new Info(id, categoryOptional.get(), scoresBuilder.build(), categoryEnabledCurrently);
                builder.add(info);
            }
        }
        return new ShowCounterPacket(inventoryIndex, artifactUUID, category, builder.build());
    }

    public static Optional<ShowCounterPacket> create(int inventoryId, UUID artifactID, ResourceLocation categoryID, MinecraftServer server) {
        if (!VoteListHandler.getArtifactName(artifactID).isEmpty()) {
            boolean isValidCategoryID = false;
            VoteListHandler handler = VoteListHandler.get(server);
            ImmutableList.Builder<Info> builder = ImmutableList.builder();
            for (ResourceLocation location : VoteCategoryHandler.getIds()) {
                isValidCategoryID = isValidCategoryID || location.equals(categoryID);
                VoteCategory category = VoteCategoryHandler.getCategory(location).orElseThrow(IllegalStateException::new);
                VoteListEntry entry = handler.getEntry(handler.getIdOrCreate(artifactID, location)).orElseThrow(IllegalStateException::new);
                boolean enabledCurrently = entry.votes.getEnabled().orElse(category.enabledDefault);
                if (category.enabledDefault || category.enabledModifiable || enabledCurrently) {
                    ImmutableList.Builder<Pair<Component, VoteList.Stats>> scoresBuilder = ImmutableList.builder();
                    entry.votes.buildFinalScore(location).forEach((subgroup, scores) -> scoresBuilder.add(Pair.of(Optional
                            .ofNullable(ResourceLocation.tryParse(subgroup)).flatMap(VoteRoleHandler::getRole)
                            .map(role -> role.name).orElse(new TextComponent(subgroup)), scores)));
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
    }
}
