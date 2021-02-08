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
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jline.utils.Colors;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

import static net.minecraft.util.text.TextComponentUtils.wrapWithSquareBrackets;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteCategoryHandler extends JsonReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static SortedMap<ResourceLocation, VoteCategory> categoryMap = ImmutableSortedMap.of();

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
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteCategoryHandler());
    }

    public static IFormattableTextComponent getText(ResourceLocation id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
        if (categoryOptional.isPresent()) {
            ITextComponent name = categoryOptional.get().name, desc = categoryOptional.get().description;
            ITextComponent hover = new StringTextComponent("").append(name).appendString("\n").append(desc);
            IFormattableTextComponent base = wrapWithSquareBrackets(new StringTextComponent(id.toString()));
            return base.modifyStyle(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return new StringTextComponent("");
    }
}
