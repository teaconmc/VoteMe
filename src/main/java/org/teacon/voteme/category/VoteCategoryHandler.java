package org.teacon.voteme.category;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.teacon.voteme.network.SyncCategoryPacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteCategoryHandler extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static ImmutableMap<ResourceLocation, VoteCategory> categoryMap = ImmutableMap.of();

    public VoteCategoryHandler() {
        super(GSON, "vote_categories");
    }

    public static Optional<VoteCategory> getCategory(ResourceLocation id) {
        return Optional.ofNullable(categoryMap.get(id));
    }

    public static Collection<? extends ResourceLocation> getIds() {
        return categoryMap.keySet();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        categoryMap = ImmutableSortedMap.copyOf(Maps.transformValues(objects, VoteCategory::fromJson));
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleServerPacket(SyncCategoryPacket packet) {
        categoryMap = packet.categories;
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteCategoryHandler());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }

    public static MutableComponent getText(ResourceLocation id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
        if (categoryOptional.isPresent()) {
            Component desc = categoryOptional.get().description;
            Component hover = new TextComponent("[" + id + "]").append("\n\n").append(desc);
            MutableComponent base = new TextComponent("").append(categoryOptional.get().name);
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return new TextComponent("");
    }
}
