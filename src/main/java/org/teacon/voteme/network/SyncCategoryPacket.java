package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncCategoryPacket implements CustomPacketPayload {

    public static final Type<SyncCategoryPacket> TYPE = new Type<>(Identifier.parse("voteme:sync_category"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCategoryPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, VoteCategory.STREAM_CODEC), p -> p.categories,
            SyncCategoryPacket::create
    );

    public final ImmutableMap<Identifier, VoteCategory> categories;

    private SyncCategoryPacket(ImmutableMap<Identifier, VoteCategory> categories) {
        this.categories = categories;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        if (FMLEnvironment.getDist().isClient()) {
            context.enqueueWork(() -> VoteCategoryHandler.setCategoriesFromServer(categories));
        }
    }

    public static SyncCategoryPacket create(Map<Identifier, VoteCategory> categories) {
        return new SyncCategoryPacket(ImmutableMap.copyOf(categories));
    }
}
