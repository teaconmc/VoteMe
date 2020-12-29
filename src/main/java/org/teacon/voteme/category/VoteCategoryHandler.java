package org.teacon.voteme.category;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.SortedMap;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteCategoryHandler extends JsonReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static SortedMap<ResourceLocation, VoteCategory> categoryMap = ImmutableSortedMap.of();

    public VoteCategoryHandler() {
        super(GSON, "vote_categories");
    }

    public static SortedMap<ResourceLocation, VoteCategory> getCategoryMap() {
        return categoryMap;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, IResourceManager manager, IProfiler profiler) {
        categoryMap = ImmutableSortedMap.copyOf(Maps.transformValues(objects, VoteCategory::fromJson));
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteCategoryHandler());
    }
}
