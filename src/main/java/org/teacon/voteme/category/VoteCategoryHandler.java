package org.teacon.voteme.category;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.teacon.voteme.network.SyncCategoryPacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.util.text.TextComponentUtils.wrapWithSquareBrackets;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteCategoryHandler extends JsonReloadListener {
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
    protected void apply(Map<ResourceLocation, JsonElement> objects, IResourceManager manager, IProfiler profiler) {
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
        PlayerEntity player = event.getPlayer();
        if (player instanceof ServerPlayerEntity) {
            SyncCategoryPacket packet = SyncCategoryPacket.create(categoryMap);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), packet);
        }
    }

    public static IFormattableTextComponent getText(ResourceLocation id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
        if (categoryOptional.isPresent()) {
            ITextComponent desc = categoryOptional.get().description;
            ITextComponent hover = new StringTextComponent("[" + id + "]").append("\n\n").append(desc);
            IFormattableTextComponent base = new StringTextComponent("").append(categoryOptional.get().name);
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return new StringTextComponent("");
    }
}
