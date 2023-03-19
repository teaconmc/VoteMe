package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteRoleHandler extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static SortedMap<ResourceLocation, VoteRole> roleMap = ImmutableSortedMap.of();

    public VoteRoleHandler() {
        super(GSON, "vote_roles");
    }

    public static Collection<? extends ResourceLocation> getRoles(ServerPlayer player) {
        ImmutableSet.Builder<ResourceLocation> builder = ImmutableSet.builder();
        for (Map.Entry<ResourceLocation, VoteRole> entry : roleMap.entrySet()) {
            try {
                EntitySelector selector = entry.getValue().selector;
                List<ServerPlayer> selected = selector.findPlayers(player.server.createCommandSourceStack());
                if (selected.contains(player)) {
                    builder.add(entry.getKey());
                }
            } catch (CommandSyntaxException ignored) {
                // continue
            }
        }
        return builder.build();
    }

    public static Optional<VoteRole> getRole(ResourceLocation id) {
        return Optional.ofNullable(roleMap.get(id));
    }

    public static Collection<? extends ResourceLocation> getIds() {
        return roleMap.keySet();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        roleMap = ImmutableSortedMap.copyOf(Maps.transformEntries(objects, VoteRole::fromJson), Comparator.naturalOrder());
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteRoleHandler());
    }

    public static MutableComponent getText(ResourceLocation id) {
        Optional<VoteRole> roleOptional = VoteRoleHandler.getRole(id);
        if (roleOptional.isPresent()) {
            MutableComponent base = wrapInSquareBrackets(Component.literal(id.toString()));
            Component hover = Component.empty().append(roleOptional.get().name).append("\n");
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return Component.empty();
    }
}
