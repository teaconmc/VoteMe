package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.command.arguments.EntitySelector;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.util.text.TextComponentUtils.wrapWithSquareBrackets;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteRoleHandler extends JsonReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static SortedMap<ResourceLocation, VoteRole> roleMap = ImmutableSortedMap.of();

    public VoteRoleHandler() {
        super(GSON, "vote_roles");
    }

    public static Collection<? extends ResourceLocation> getRoles(ServerPlayerEntity player) {
        ImmutableSet.Builder<ResourceLocation> builder = ImmutableSet.builder();
        for (Map.Entry<ResourceLocation, VoteRole> entry : roleMap.entrySet()) {
            try {
                EntitySelector selector = entry.getValue().selector;
                List<ServerPlayerEntity> selected = selector.selectPlayers(player.server.getCommandSource());
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
    protected void apply(Map<ResourceLocation, JsonElement> objects, IResourceManager manager, IProfiler profiler) {
        roleMap = ImmutableSortedMap.copyOf(Maps.transformEntries(objects, VoteRole::fromJson), Comparator.naturalOrder());
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteRoleHandler());
    }

    public static IFormattableTextComponent getText(ResourceLocation id) {
        Optional<VoteRole> roleOptional = VoteRoleHandler.getRole(id);
        if (roleOptional.isPresent()) {
            IFormattableTextComponent base = wrapWithSquareBrackets(new StringTextComponent(id.toString()));
            ITextComponent hover = new StringTextComponent("").append(roleOptional.get().name).appendString("\n");
            return base.modifyStyle(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        }
        return new StringTextComponent("");
    }
}
