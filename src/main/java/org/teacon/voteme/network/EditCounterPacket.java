package org.teacon.voteme.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class EditCounterPacket {
    public final String currentArtifact;
    public final ResourceLocation current;
    public final List<Info> infoCollection;

    public EditCounterPacket(String currentArtifact, ResourceLocation currentVoteCategory, List<Info> infos) {
        this.currentArtifact = currentArtifact;
        this.current = currentVoteCategory;
        this.infoCollection = infos;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        VoteMe.LOGGER.info("EditCounterPacket: {} (of {})", this.current, this.currentArtifact);
        for (Info info : this.infoCollection) {
            VoteMe.LOGGER.info("- [{}] {} ({}): {}", info.score, info.id, info.name, info.desc);
        }
        // TODO
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeString(this.currentArtifact);
        buffer.writeResourceLocation(this.current);
        buffer.writeInt(this.infoCollection.size());
        for (Info info : this.infoCollection) {
            buffer.writeString(info.name);
            buffer.writeString(info.desc);
            buffer.writeDouble(info.score);
            buffer.writeResourceLocation(info.id);
        }
        buffer.writeInt(-1);
    }

    public static EditCounterPacket read(PacketBuffer buffer) {
        String artifact = buffer.readString();
        ResourceLocation category = buffer.readResourceLocation();
        List<Info> infos = new ArrayList<>();
        for (int size = buffer.readInt(), i = 0; i < size; ++i) {
            String name = buffer.readString();
            String desc = buffer.readString();
            double score = buffer.readDouble();
            infos.add(new Info(buffer.readResourceLocation(), name, desc, score));
        }
        return new EditCounterPacket(artifact, category, Collections.unmodifiableList(infos));
    }

    public static Optional<EditCounterPacket> create(int id, MinecraftServer server) {
        VoteListHandler voteListHandler = VoteListHandler.get(server);
        Optional<VoteListEntry> entryOptional = voteListHandler.getEntry(id);
        if (entryOptional.isPresent()) {
            List<Info> infos = new ArrayList<>();
            String artifact = entryOptional.get().artifact;
            VoteCategoryHandler.getIds().forEach(location -> {
                // noinspection OptionalGetWithoutIsPresent
                VoteCategory category = VoteCategoryHandler.getCategory(location).get();
                // noinspection OptionalGetWithoutIsPresent
                VoteListEntry entry = voteListHandler.getEntry(voteListHandler.getIdOrCreate(artifact, location)).get();
                VoteList.Stats stats = VoteList.Stats.combine(entry.votes.buildFinalScore(location).values());
                infos.add(new Info(location, category.name, category.description, stats.getFinalScore()));
            });
            if (!infos.isEmpty()) {
                ResourceLocation category = entryOptional.get().category;
                return Optional.of(new EditCounterPacket(artifact, category, Collections.unmodifiableList(infos)));
            }
        }
        return Optional.empty();
    }

    public static Optional<EditCounterPacket> create(MinecraftServer server) {
        List<Info> infos = new ArrayList<>();
        VoteCategoryHandler.getIds().forEach(location -> {
            // noinspection OptionalGetWithoutIsPresent
            VoteCategory category = VoteCategoryHandler.getCategory(location).get();
            infos.add(new Info(location, category.name, category.description, 6.0));
        });
        if (!infos.isEmpty()) {
            ResourceLocation category = infos.iterator().next().id;
            return Optional.of(new EditCounterPacket("", category, Collections.unmodifiableList(infos)));
        }
        return Optional.empty();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {
        public final String name;
        public final String desc;
        public final double score;
        public final ResourceLocation id;

        public Info(ResourceLocation id, String name, String desc, double score) {
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
