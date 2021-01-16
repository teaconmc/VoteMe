package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.command.arguments.EntitySelector;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteRoleHandler extends JsonReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private static SortedSet<ResourceLocation> roleIds = ImmutableSortedSet.of();
    private static SortedMap<ResourceLocation, VoteRole> reversedRoleMap = ImmutableSortedMap.of();

    public VoteRoleHandler() {
        super(GSON, "vote_roles");
    }

    public static Optional<ResourceLocation> getRole(ServerPlayerEntity player) {
        for (Map.Entry<ResourceLocation, VoteRole> entry : reversedRoleMap.entrySet()) {
            try {
                EntitySelector selector = entry.getValue().selector;
                List<ServerPlayerEntity> selected = selector.selectPlayers(player.server.getCommandSource());
                if (selected.contains(player)) {
                    return Optional.of(entry.getKey());
                }
            } catch (CommandSyntaxException ignored) {
                // continue
            }
        }
        return Optional.empty();
    }

    public static Optional<VoteRole> getRole(ResourceLocation id) {
        return Optional.ofNullable(reversedRoleMap.get(id));
    }

    public static Collection<? extends ResourceLocation> getIds() {
        return roleIds;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, IResourceManager manager, IProfiler profiler) {
        reversedRoleMap = ImmutableSortedMap.copyOf(Maps.transformValues(objects, VoteRole::fromJson), Comparator.reverseOrder());
        roleIds = ImmutableSortedSet.<ResourceLocation>naturalOrder().addAll(reversedRoleMap.keySet()).build();
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VoteRoleHandler());
    }
}
