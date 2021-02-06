package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
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
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class EditCounterPacket {
    public final int invIndex;
    public final UUID artifactUUID;
    public final String artifactName;
    public final ResourceLocation category;
    public final ImmutableList<Info> infos;

    public EditCounterPacket(int invIndex, UUID uuid, String name, ResourceLocation category, ImmutableList<Info> infos) {
        this.invIndex = invIndex;
        this.artifactName = name;
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
                    CounterScreen gui = new CounterScreen(p.artifactUUID, p.artifactName, p.invIndex, p.category, p.infos);
                    supplier.get().enqueueWork(() -> Minecraft.getInstance().displayGuiScreen(gui));
                }
            });
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeInt(this.invIndex);
        buffer.writeString(this.artifactName);
        buffer.writeUniqueId(this.artifactUUID);
        buffer.writeResourceLocation(this.category);
        for (Info info : this.infos) {
            buffer.writeDouble(info.score);
            buffer.writeTextComponent(info.name);
            buffer.writeTextComponent(info.desc);
            buffer.writeResourceLocation(info.id);
        }
        buffer.writeDouble(Double.NaN);
    }

    public static EditCounterPacket read(PacketBuffer buffer) {
        int inventoryIndex = buffer.readInt();
        String artifactName = buffer.readString();
        UUID artifactUUID = buffer.readUniqueId();
        ResourceLocation category = buffer.readResourceLocation();
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        for (double score = buffer.readDouble(); !Double.isNaN(score); score = buffer.readDouble()) {
            ITextComponent name = buffer.readTextComponent();
            ITextComponent desc = buffer.readTextComponent();
            builder.add(new Info(buffer.readResourceLocation(), name, desc, score));
        }
        return new EditCounterPacket(inventoryIndex, artifactUUID, artifactName, category, builder.build());
    }

    public static Optional<EditCounterPacket> create(int id, int inventoryId, MinecraftServer server) {
        VoteListHandler handler = VoteListHandler.get(server);
        Optional<VoteListEntry> entryOptional = handler.getEntry(id);
        if (entryOptional.isPresent()) {
            UUID artifactID = entryOptional.get().artifactID;
            String artifactName = handler.getArtifactName(artifactID);
            ImmutableList.Builder<Info> builder = ImmutableList.builder();
            VoteCategoryHandler.getIds().forEach(location -> {
                // noinspection OptionalGetWithoutIsPresent
                VoteCategory category = VoteCategoryHandler.getCategory(location).get();
                // noinspection OptionalGetWithoutIsPresent
                VoteListEntry entry = handler.getEntry(handler.getIdOrCreate(artifactID, location)).get();
                Collection<VoteList.Stats> statsCollection = entry.votes.buildFinalScore(location).values();
                VoteList.Stats finalStats = VoteList.Stats.combine(statsCollection, VoteList.Stats::getWeight);
                builder.add(new Info(location, category.name, category.description, finalStats.getFinalScore(6.0F)));
            });
            ImmutableList<Info> infos = builder.build();
            if (!infos.isEmpty()) {
                ResourceLocation category = entryOptional.get().category;
                return Optional.of(new EditCounterPacket(inventoryId, artifactID, artifactName, category, infos));
            }
        }
        return Optional.empty();
    }

    public static Optional<EditCounterPacket> create(int inventoryId, MinecraftServer server) {
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        VoteCategoryHandler.getIds().forEach(location -> {
            // noinspection OptionalGetWithoutIsPresent
            VoteCategory category = VoteCategoryHandler.getCategory(location).get();
            builder.add(new Info(location, category.name, category.description, 6.0));
        });
        ImmutableList<Info> infos = builder.build();
        if (!infos.isEmpty()) {
            UUID newArtifactUUID = UUID.randomUUID();
            ResourceLocation category = infos.iterator().next().id;
            return Optional.of(new EditCounterPacket(inventoryId, newArtifactUUID, "", category, infos));
        }
        return Optional.empty();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {
        public final ITextComponent name;
        public final ITextComponent desc;
        public final double score;
        public final ResourceLocation id;

        public Info(ResourceLocation id, ITextComponent name, ITextComponent desc, double score) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.score = score;
        }

        @Override
        public String toString() {
            return "EditCounterPacker.Info{id='" + this.id + "', name='" + this.name + "', desc=" + this.desc + ", score=" + this.score + "}";
        }
    }
}
