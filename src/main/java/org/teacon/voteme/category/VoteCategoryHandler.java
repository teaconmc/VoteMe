package org.teacon.voteme.category;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.teacon.voteme.network.SyncCategoryPacket;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoteCategoryHandler extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Identifier RELOAD_LISTENER_ID = Identifier.parse("voteme:vote_categories");

    private static ImmutableMap<Identifier, VoteCategory> categoryMap = ImmutableMap.of();

    public VoteCategoryHandler() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("vote_categories"));
    }

    public static Optional<VoteCategory> getCategory(Identifier id) {
        return Optional.ofNullable(categoryMap.get(id));
    }

    public static Collection<? extends Identifier> getIds() {
        return categoryMap.keySet();
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        categoryMap = ImmutableSortedMap.copyOf(Maps.transformValues(objects, VoteCategory::fromJson));
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    public static void setCategoriesFromServer(ImmutableMap<Identifier, VoteCategory> categories) {
        categoryMap = categories;
    }

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(RELOAD_LISTENER_ID, new VoteCategoryHandler());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    public static MutableComponent getText(Identifier id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
        if (categoryOptional.isPresent()) {
            Component desc = categoryOptional.get().description;
            Component hover = Component.literal("[" + id + "]").append("\n\n").append(desc);
            MutableComponent base = Component.empty().append(categoryOptional.get().name);
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
        }
        return Component.empty();
    }
}
