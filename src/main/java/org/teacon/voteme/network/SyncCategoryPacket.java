package org.teacon.voteme.network;

import com.google.common.collect.ImmutableMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
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
        // forge needs a separate class
        // noinspection Convert2Lambda
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> new DistExecutor.SafeRunnable() {
            @Override
            public void run() {
                SyncCategoryPacket p = SyncCategoryPacket.this;
                supplier.get().enqueueWork(() -> VoteCategoryHandler.handleServerPacket(p));
            }
        });
        supplier.get().setPacketHandled(true);
    }

    public void write(PacketBuffer buffer) {
        for (Map.Entry<ResourceLocation, VoteCategory> entry : this.categories.entrySet()) {
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(entry.getKey());
            VoteCategory category = entry.getValue();
            buffer.writeTextComponent(category.name);
            buffer.writeTextComponent(category.description);
            buffer.writeBoolean(category.enabledDefault);
            buffer.writeBoolean(category.enabledModifiable);
        }
        buffer.writeBoolean(false);
    }

    public static SyncCategoryPacket read(PacketBuffer buffer) {
        ImmutableMap.Builder<ResourceLocation, VoteCategory> builder = ImmutableMap.builder();
        for (boolean b = buffer.readBoolean(); b; b = buffer.readBoolean()) {
            ResourceLocation id = buffer.readResourceLocation();
            ITextComponent name = buffer.readTextComponent();
            ITextComponent description = buffer.readTextComponent();
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
