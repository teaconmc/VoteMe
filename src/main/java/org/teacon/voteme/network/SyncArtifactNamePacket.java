package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncArtifactNamePacket {
    public final ImmutableMap<UUID, String> artifactNames;

    private SyncArtifactNamePacket(ImmutableMap<UUID, String> artifactNames) {
        this.artifactNames = artifactNames;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        // forge needs a separate class
        // noinspection Convert2Lambda
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                SyncArtifactNamePacket p = SyncArtifactNamePacket.this;
                supplier.get().enqueueWork(() -> VoteListHandler.handleServerPacket(p));
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        for (Map.Entry<UUID, String> entry : this.artifactNames.entrySet()) {
            buffer.writeBoolean(true);
            buffer.writeUniqueId(entry.getKey());
            buffer.writeString(entry.getValue());
        }
        buffer.writeBoolean(false);
    }

    public static SyncArtifactNamePacket read(PacketBuffer buffer) {
        ImmutableMap.Builder<UUID, String> builder = ImmutableMap.builder();
        for (boolean b = buffer.readBoolean(); b; b = buffer.readBoolean()) {
            builder.put(buffer.readUniqueId(), buffer.readString());
        }
        return new SyncArtifactNamePacket(builder.build());
    }

    public static SyncArtifactNamePacket create(Map<UUID, String> artifactNames) {
        return new SyncArtifactNamePacket(ImmutableMap.copyOf(artifactNames));
    }
}
