package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncArtifactNamePacket implements CustomPacketPayload {

    public static final Type<SyncArtifactNamePacket> TYPE = new Type<>(Identifier.parse("voteme:sync_artifact_name"));

    public static final StreamCodec<FriendlyByteBuf, SyncArtifactNamePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.stringUtf8(Short.MAX_VALUE)), p -> p.artifactNames,
            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.stringUtf8(Short.MAX_VALUE)), p -> p.artifactAliases,
            SyncArtifactNamePacket::create
    );

    public final ImmutableMap<UUID, String> artifactNames;
    public final ImmutableMap<UUID, String> artifactAliases;

    private SyncArtifactNamePacket(ImmutableMap<UUID, String> names, ImmutableMap<UUID, String> aliases) {
        this.artifactNames = names;
        this.artifactAliases = aliases;
    }

    @Override
    public Type<SyncArtifactNamePacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        SyncArtifactNamePacket packet = SyncArtifactNamePacket.this;
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            VoteArtifactNames.handleServerPacket().accept(packet);
        }
    }

    public static SyncArtifactNamePacket create(Map<UUID, String> artifactNames, Map<UUID, String> artifactAliases) {
        return new SyncArtifactNamePacket(ImmutableMap.copyOf(artifactNames), ImmutableMap.copyOf(artifactAliases));
    }
}
