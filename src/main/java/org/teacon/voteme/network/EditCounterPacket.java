package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.screen.CounterScreen;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class EditCounterPacket {
    public final int invIndex;
    public final UUID artifactUUID;
    public final ResourceLocation category;
    public final ImmutableList<Info> infos;

    private EditCounterPacket(int invIndex, UUID uuid, ResourceLocation category, ImmutableList<Info> infos) {
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
                    EditCounterPacket p = EditCounterPacket.this;
                    String artifactName = VoteListHandler.getArtifactName(p.artifactUUID);
                    CounterScreen gui = new CounterScreen(p.artifactUUID, artifactName, p.invIndex, p.category, p.infos);
                    supplier.get().enqueueWork(() -> Minecraft.getInstance().displayGuiScreen(gui));
                }
            });
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeInt(this.invIndex);
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeResourceLocation(this.category);
        buffer.writeVarInt(this.infos.size());
        for (Info info : this.infos) {
            for (Map.Entry<String, VoteList.Stats> entry : info.scores.entrySet()) {
                VoteList.Stats stats = entry.getValue();
                buffer.writeFloat(stats.getWeight());
                buffer.writeFloat(stats.getFinalScore(Float.NaN));
                buffer.writeVarInt(stats.getEffectiveCount());
                buffer.writeVarIntArray(stats.getVoteCountArray());
                buffer.writeString(entry.getKey());
            }
            buffer.writeFloat(Float.NaN);
            buffer.writeResourceLocation(info.id);
            buffer.writeBoolean(info.enabledCurrently);
        }
    }

    public static EditCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readInt();
        UUID artifactUUID = buffer.readUniqueId();
        ResourceLocation category = buffer.readResourceLocation();
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        for (int i = 0, size = buffer.readVarInt(); i < size; ++i) {
            ImmutableMap.Builder<String, VoteList.Stats> scoresBuilder = ImmutableMap.builder();
            for (float weight = buffer.readFloat(); !Float.isNaN(weight); weight = buffer.readFloat()) {
                float finalScore = buffer.readFloat();
                int effectiveVoteCount = buffer.readVarInt();
                int[] countsByLevel = buffer.readVarIntArray(6);
                String subgroup = buffer.readString(Short.MAX_VALUE);
                scoresBuilder.put(subgroup, new VoteList.Stats(weight, finalScore, effectiveVoteCount, countsByLevel));
            }
            ResourceLocation id = buffer.readResourceLocation();
            boolean categoryEnabledCurrently = buffer.readBoolean();
            Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
            if (categoryOptional.isPresent()) {
                Info info = new Info(id, categoryOptional.get(), scoresBuilder.build(), categoryEnabledCurrently);
                builder.add(info);
            }
        }
        return new EditCounterPacket(inventoryIndex, artifactUUID, category, builder.build());
    }

    public static Optional<EditCounterPacket> create(int inventoryId, UUID artifactID, ResourceLocation categoryID, MinecraftServer server) {
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
                    Map<String, VoteList.Stats> scores = entry.votes.buildFinalScore(location);
                    builder.add(new Info(location, category, scores, enabledCurrently));
                }
            }
            ImmutableList<Info> infos = builder.build();
            if (!infos.isEmpty()) {
                if (!isValidCategoryID) {
                    categoryID = infos.iterator().next().id;
                }
                return Optional.of(new EditCounterPacket(inventoryId, artifactID, categoryID, infos));
            }
        }
        return Optional.empty();
    }

    public static Optional<EditCounterPacket> create(int inventoryId) {
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        VoteCategoryHandler.getIds().forEach(location -> {
            VoteCategory category = VoteCategoryHandler.getCategory(location).orElseThrow(IllegalStateException::new);
            if (category.enabledDefault || category.enabledModifiable) {
                builder.add(new Info(location, category, ImmutableSortedMap.of(), category.enabledDefault));
            }
        });
        ImmutableList<Info> infos = builder.build();
        if (!infos.isEmpty()) {
            UUID newArtifactUUID = UUID.randomUUID();
            ResourceLocation categoryID = infos.iterator().next().id;
            return Optional.of(new EditCounterPacket(inventoryId, newArtifactUUID, categoryID, infos));
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
        public final Map<String, VoteList.Stats> scores;

        public Info(ResourceLocation id, VoteCategory category, Map<String, VoteList.Stats> scores, boolean enabledCurrently) {
            this.id = id;
            this.scores = scores;
            this.category = category;
            this.enabledCurrently = enabledCurrently;
            this.finalStat = VoteList.Stats.combine(scores.values(), VoteList.Stats::getWeight);
        }

        @Override
        public String toString() {
            return "EditCounterPacker.Info{id='" + this.id + ", scores=" + this.scores + ", enabled=" + this.enabledCurrently + "}";
        }
    }
}
