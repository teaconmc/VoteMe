package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.function.Supplier;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SyncCategoryPacket {
    public final ImmutableMap<ResourceLocation, VoteCategory> categories;

    private SyncCategoryPacket(ImmutableMap<ResourceLocation, VoteCategory> categories) {
        this.categories = categories;
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        if (FMLEnvironment.dist.isClient()) {
            supplier.get().enqueueWork(() -> VoteCategoryHandler.setCategoriesFromServer(categories));
        }
        supplier.get().setPacketHandled(true);
    }

    public void write(FriendlyByteBuf buffer) {
        for (Map.Entry<ResourceLocation, VoteCategory> entry : this.categories.entrySet()) {
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(entry.getKey());
            VoteCategory category = entry.getValue();
            buffer.writeComponent(category.name);
            buffer.writeComponent(category.description);
            buffer.writeBoolean(category.enabledDefault);
            buffer.writeBoolean(category.enabledModifiable);
        }
        buffer.writeBoolean(false);
    }

    public static SyncCategoryPacket read(FriendlyByteBuf buffer) {
        ImmutableMap.Builder<ResourceLocation, VoteCategory> builder = ImmutableMap.builder();
        for (boolean b = buffer.readBoolean(); b; b = buffer.readBoolean()) {
            ResourceLocation id = buffer.readResourceLocation();
            Component name = buffer.readComponent();
            Component description = buffer.readComponent();
            boolean enabledDefault = buffer.readBoolean();
            boolean enabledModifiable = buffer.readBoolean();
            builder.put(id, new VoteCategory(name, description, enabledDefault, enabledModifiable));
        }
        return new SyncCategoryPacket(builder.build());
    }

    public static SyncCategoryPacket create(Map<ResourceLocation, VoteCategory> categories) {
        return new SyncCategoryPacket(ImmutableMap.copyOf(categories));
    }
}
