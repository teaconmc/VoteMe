package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncArtifactNamePacket {
    public final ImmutableMap<UUID, String> artifactNames;
    public final ImmutableMap<UUID, String> artifactAliases;

    private SyncArtifactNamePacket(ImmutableMap<UUID, String> names, ImmutableMap<UUID, String> aliases) {
        this.artifactNames = names;
        this.artifactAliases = aliases;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        SyncArtifactNamePacket packet = SyncArtifactNamePacket.this;
        supplier.get().enqueueWork(() -> DistExecutor
                .safeCallWhenOn(Dist.CLIENT, () -> VoteArtifactNames::handleServerPacket).accept(packet));
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        for (Map.Entry<UUID, String> entry : this.artifactNames.entrySet()) {
            buffer.writeBoolean(true);
            buffer.writeUUID(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
        buffer.writeBoolean(false);
        for (Map.Entry<UUID, String> entry : this.artifactAliases.entrySet()) {
            buffer.writeBoolean(true);
            buffer.writeUUID(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
        buffer.writeBoolean(false);
    }

    public static SyncArtifactNamePacket read(FriendlyByteBuf buffer) {
        ImmutableMap.Builder<UUID, String> namesBuilder = ImmutableMap.builder();
        for (boolean b = buffer.readBoolean(); b; b = buffer.readBoolean()) {
            namesBuilder.put(buffer.readUUID(), buffer.readUtf(Short.MAX_VALUE));
        }
        ImmutableMap.Builder<UUID, String> aliasesBuilder = ImmutableMap.builder();
        for (boolean b = buffer.readBoolean(); b; b = buffer.readBoolean()) {
            aliasesBuilder.put(buffer.readUUID(), buffer.readUtf(Short.MAX_VALUE));
        }
        return new SyncArtifactNamePacket(namesBuilder.build(), aliasesBuilder.build());
    }

    public static SyncArtifactNamePacket create(Map<UUID, String> artifactNames, Map<UUID, String> artifactAliases) {
        return new SyncArtifactNamePacket(ImmutableMap.copyOf(artifactNames), ImmutableMap.copyOf(artifactAliases));
    }
}
