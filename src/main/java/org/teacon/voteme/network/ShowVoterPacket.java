package org.teacon.voteme.network;

import com.google.common.collect.ImmutableList;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.screen.VoterScreen;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ShowVoterPacket {
    public final String artifactName;
    public final ImmutableList<Info> infos;

    public ShowVoterPacket(String name, ImmutableList<Info> infos) {
        this.artifactName = name;
        this.infos = infos;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        if (!this.infos.isEmpty()) {
            // forge needs a separate class
            // noinspection Convert2Lambda
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new DistExecutor.SafeRunnable() {
                @Override
                public void run() {
                    ShowVoterPacket p = ShowVoterPacket.this;
                    VoterScreen gui = new VoterScreen(p.artifactName, p.infos);
                    supplier.get().enqueueWork(() -> Minecraft.getInstance().displayGuiScreen(gui));
                }
            });
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeString(this.artifactName);
        for (Info info : this.infos) {
            buffer.writeInt(info.level).writeInt(info.id);
            buffer.writeTextComponent(info.name).writeTextComponent(info.desc);
        }
        buffer.writeInt(Integer.MIN_VALUE);
    }

    public static ShowVoterPacket read(PacketBuffer buffer) {
        String artifactName = buffer.readString();
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        for (int level = buffer.readInt(); level != Integer.MIN_VALUE; level = buffer.readInt()) {
            int id = buffer.readInt();
            ITextComponent name = buffer.readTextComponent();
            ITextComponent desc = buffer.readTextComponent();
            builder.add(new Info(id, name, desc, level));
        }
        return new ShowVoterPacket(artifactName, builder.build());
    }

    public static Optional<ShowVoterPacket> create(UUID artifactID, ServerPlayerEntity player) {
        VoteListHandler handler = VoteListHandler.get(player.server);
        String artifactName = handler.getArtifactName(artifactID);
        ImmutableList.Builder<Info> builder = ImmutableList.builder();
        VoteCategoryHandler.getIds().forEach(location -> {
            int id = handler.getIdOrCreate(artifactID, location);
            VoteListEntry entry = handler.getEntry(id).orElseThrow(IllegalStateException::new);
            VoteCategory category = VoteCategoryHandler.getCategory(location).orElseThrow(IllegalStateException::new);
            if (entry.votes.getEnabled().orElse(category.enabledDefault)) {
                builder.add(new Info(id, category.name, category.description, entry.votes.get(player)));
            }
        });
        ImmutableList<Info> infos = builder.build();
        if (!infos.isEmpty()) {
            return Optional.of(new ShowVoterPacket(artifactName, infos));
        }
        return Optional.empty();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Info {
        public final int id;
        public final int level;
        public final ITextComponent name, desc;

        public Info(int id, ITextComponent name, ITextComponent desc, int level) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.level = level;
        }

        @Override
        public String toString() {
            return "ShowVoterPacket.Info{id=" + this.id + ", name='" + this.name + "', desc=" + this.desc + "', level=" + this.level + "}";
        }
    }
}
