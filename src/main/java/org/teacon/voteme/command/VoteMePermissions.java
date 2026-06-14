package org.teacon.voteme.command;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Util;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoteMePermissions {
    public static final PermissionNode<Boolean> ADMIN = Util.make(new PermissionNode<>("voteme", "admin", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.name"), Component.translatable("permission.voteme.admin.description")));
    public static final PermissionNode<Boolean> ADMIN_CREATE = Util.make(new PermissionNode<>("voteme", "admin.create", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.create.name"), Component.translatable("permission.voteme.admin.create.description")));
    public static final PermissionNode<Boolean> ADMIN_REMOVE = Util.make(new PermissionNode<>("voteme", "admin.remove", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.remove.name"), Component.translatable("permission.voteme.admin.remove.description")));
    public static final PermissionNode<Boolean> ADMIN_MERGE = Util.make(new PermissionNode<>("voteme", "admin.merge", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.merge.name"), Component.translatable("permission.voteme.admin.merge.description")));
    public static final PermissionNode<Boolean> ADMIN_CLEAR = Util.make(new PermissionNode<>("voteme", "admin.clear", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.clear.name"), Component.translatable("permission.voteme.admin.clear.description")));
    public static final PermissionNode<Boolean> ADMIN_SWITCH = Util.make(new PermissionNode<>("voteme", "admin.switch", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(Component.translatable("permission.voteme.admin.switch.name"), Component.translatable("permission.voteme.admin.switch.description")));
    public static final PermissionNode<Boolean> CREATE = Util.make(new PermissionNode<>("voteme", "create", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.create.name"), Component.translatable("permission.voteme.create.description")));
    public static final PermissionNode<Boolean> SWITCH = Util.make(new PermissionNode<>("voteme", "switch", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.switch.name"), Component.translatable("permission.voteme.switch.description")));
    public static final PermissionNode<Boolean> MODIFY = Util.make(new PermissionNode<>("voteme", "modify", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.modify.name"), Component.translatable("permission.voteme.modify.description")));
    public static final PermissionNode<Boolean> QUERY = Util.make(new PermissionNode<>("voteme", "query", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.query.name"), Component.translatable("permission.voteme.query.description")));
    public static final PermissionNode<Boolean> OPEN = Util.make(new PermissionNode<>("voteme", "open", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.open.name"), Component.translatable("permission.voteme.open.description")));
    public static final PermissionNode<Boolean> GIVE = Util.make(new PermissionNode<>("voteme", "give", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.give.name"), Component.translatable("permission.voteme.give.description")));
    public static final PermissionNode<Boolean> LIST = Util.make(new PermissionNode<>("voteme", "list", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(Component.translatable("permission.voteme.list.name"), Component.translatable("permission.voteme.list.description")));
    public static final PermissionNode<Boolean> OPEN_VOTER = Util.make(new PermissionNode<>("voteme", "open.voter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(Component.translatable("permission.voteme.open.voter.name"), Component.translatable("permission.voteme.open.voter.description")));
    public static final PermissionNode<Boolean> CREATE_COUNTER = Util.make(new PermissionNode<>("voteme", "create.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(Component.translatable("permission.voteme.create.counter.name"), Component.translatable("permission.voteme.create.counter.description")));
    public static final PermissionNode<Boolean> MODIFY_COUNTER = Util.make(new PermissionNode<>("voteme", "modify.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(Component.translatable("permission.voteme.modify.counter.name"), Component.translatable("permission.voteme.modify.counter.description")));
    public static final PermissionNode<Boolean> SWITCH_COUNTER = Util.make(new PermissionNode<>("voteme", "switch.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(Component.translatable("permission.voteme.switch.counter.name"), Component.translatable("permission.voteme.switch.counter.description")));

    @SubscribeEvent
    public static void register(PermissionGatherEvent.Nodes event) {
        ImmutableList.Builder<PermissionNode<?>> builder = ImmutableList.builder();

        builder.add(VoteMePermissions.ADMIN);
        builder.add(VoteMePermissions.ADMIN_CREATE);
        builder.add(VoteMePermissions.ADMIN_REMOVE);
        builder.add(VoteMePermissions.ADMIN_MERGE);
        builder.add(VoteMePermissions.ADMIN_CLEAR);
        builder.add(VoteMePermissions.ADMIN_SWITCH);
        builder.add(VoteMePermissions.CREATE);
        builder.add(VoteMePermissions.SWITCH);
        builder.add(VoteMePermissions.MODIFY);
        builder.add(VoteMePermissions.QUERY);
        builder.add(VoteMePermissions.OPEN);
        builder.add(VoteMePermissions.GIVE);
        builder.add(VoteMePermissions.LIST);
        builder.add(VoteMePermissions.OPEN_VOTER);
        builder.add(VoteMePermissions.CREATE_COUNTER);
        builder.add(VoteMePermissions.MODIFY_COUNTER);
        builder.add(VoteMePermissions.SWITCH_COUNTER);

        event.addNodes(builder.build());
    }

    private static boolean moderator(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        if (player == null) {
            MinecraftServer server = Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer());
            return hasPermissionLevel(server.getProfilePermissions(new NameAndId(uuid, "")), 3);
        }
        return hasPermissionLevel(player.permissions(), 3);
    }

    private static boolean function(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        if (player == null) {
            MinecraftServer server = Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer());
            return hasPermissionLevel(server.getProfilePermissions(new NameAndId(uuid, "")), 2);
        }
        return hasPermissionLevel(player.permissions(), 2);
    }

    private static boolean always(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        return true;
    }

    private static boolean hasPermissionLevel(PermissionSet permissions, int level) {
        if (permissions instanceof LevelBasedPermissionSet levelBased) {
            return levelBased.level().isEqualOrHigherThan(PermissionLevel.byId(level));
        }
        return false;
    }
}
