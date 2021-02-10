package org.teacon.voteme.command;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteMeSelections {
    private static final Map<Entity, UUID> selections = new WeakHashMap<>(); // weak hash maps allow null keys

    public static boolean hasSelection(CommandSource source) {
        return selections.containsKey(source.getEntity());
    }

    public static UUID getSelection(CommandSource source) {
        return Objects.requireNonNull(selections.get(source.getEntity()));
    }

    public static void delSelection(CommandSource source) {
        Entity entity = source.getEntity();
        if (selections.remove(entity) != null && entity instanceof ServerPlayerEntity) {
            source.getServer().getCommandManager().send((ServerPlayerEntity) entity);
        }
    }

    public static void setSelection(CommandSource source, UUID uuid) {
        Entity entity = source.getEntity();
        if (!uuid.equals(selections.put(entity, uuid)) && entity instanceof ServerPlayerEntity) {
            source.getServer().getCommandManager().send((ServerPlayerEntity) entity);
        }
    }
}
