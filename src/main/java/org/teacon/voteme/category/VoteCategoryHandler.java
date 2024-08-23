package org.teacon.voteme.category;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.teacon.voteme.network.SyncCategoryPacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class VoteCategoryHandler extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static ImmutableMap<ResourceLocation, VoteCategory> categoryMap = ImmutableMap.of();
    private final HolderLookup.Provider registries;

    public VoteCategoryHandler(HolderLookup.Provider registries) {
        super(GSON, "vote_categories");
        this.registries = registries;
    }

    public static Optional<VoteCategory> getCategory(ResourceLocation id) {
        return Optional.ofNullable(categoryMap.get(id));
    }

    public static Collection<? extends ResourceLocation> getIds() {
        return categoryMap.keySet();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        categoryMap = ImmutableSortedMap.copyOf(Maps.transformValues(objects, v -> VoteCategory.fromJson(v, this.registries)));
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    public static void setCategoriesFromServer(ImmutableMap<ResourceLocation, VoteCategory> categories) {
        categoryMap = categories;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteCategoryHandler(event.getRegistryAccess()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    public static MutableComponent getText(ResourceLocation id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
        if (categoryOptional.isPresent()) {
            Component desc = categoryOptional.get().description;
            Component hover = Component.literal("[" + id + "]").append("\n\n").append(desc);
            MutableComponent base = Component.empty().append(categoryOptional.get().name);
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return Component.empty();
    }
}
