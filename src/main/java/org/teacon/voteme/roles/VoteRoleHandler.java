package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoteRoleHandler extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Identifier RELOAD_LISTENER_ID = Identifier.parse("voteme:vote_roles");

    private static SortedMap<Identifier, VoteRole> roleMap = ImmutableSortedMap.of();

    public VoteRoleHandler() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("vote_roles"));
    }

    public static Collection<? extends Identifier> getRoles(ServerPlayer player) {
        ImmutableSet.Builder<Identifier> builder = ImmutableSet.builder();
        for (Map.Entry<Identifier, VoteRole> entry : roleMap.entrySet()) {
            try {
                EntitySelector selector = entry.getValue().selector;
                List<ServerPlayer> selected = selector.findPlayers(player.createCommandSourceStack());
                if (selected.contains(player)) {
                    builder.add(entry.getKey());
                }
            } catch (CommandSyntaxException ignored) {
                // continue
            }
        }
        return builder.build();
    }

    public static Optional<VoteRole> getRole(Identifier id) {
        return Optional.ofNullable(roleMap.get(id));
    }

    public static Collection<? extends Identifier> getIds() {
        return roleMap.keySet();
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        roleMap = ImmutableSortedMap.copyOf(Maps.transformEntries(objects, VoteRole::fromJson), Comparator.naturalOrder());
    }

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(RELOAD_LISTENER_ID, new VoteRoleHandler());
    }

    public static MutableComponent getText(Identifier id) {
        Optional<VoteRole> roleOptional = VoteRoleHandler.getRole(id);
        if (roleOptional.isPresent()) {
            MutableComponent base = wrapInSquareBrackets(Component.literal(id.toString()));
            Component hover = Component.empty().append(roleOptional.get().name).append("\n");
            return base.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
        }
        return Component.empty();
    }
}
