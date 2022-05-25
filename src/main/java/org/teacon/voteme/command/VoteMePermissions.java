package org.teacon.voteme.command;

import com.google.common.collect.ImmutableList;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteMePermissions {
    public static final PermissionNode<Boolean> ADMIN = Util.make(new PermissionNode<>("voteme", "admin", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.name"), new TranslatableComponent("permission.voteme.admin.description")));
    public static final PermissionNode<Boolean> ADMIN_CREATE = Util.make(new PermissionNode<>("voteme", "admin.create", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.create.name"), new TranslatableComponent("permission.voteme.admin.create.description")));
    public static final PermissionNode<Boolean> ADMIN_REMOVE = Util.make(new PermissionNode<>("voteme", "admin.remove", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.remove.name"), new TranslatableComponent("permission.voteme.admin.remove.description")));
    public static final PermissionNode<Boolean> ADMIN_MERGE = Util.make(new PermissionNode<>("voteme", "admin.merge", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.merge.name"), new TranslatableComponent("permission.voteme.admin.merge.description")));
    public static final PermissionNode<Boolean> ADMIN_CLEAR = Util.make(new PermissionNode<>("voteme", "admin.clear", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.clear.name"), new TranslatableComponent("permission.voteme.admin.clear.description")));
    public static final PermissionNode<Boolean> ADMIN_SWITCH = Util.make(new PermissionNode<>("voteme", "admin.switch", PermissionTypes.BOOLEAN, VoteMePermissions::moderator),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.admin.switch.name"), new TranslatableComponent("permission.voteme.admin.switch.description")));
    public static final PermissionNode<Boolean> CREATE = Util.make(new PermissionNode<>("voteme", "create", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.create.name"), new TranslatableComponent("permission.voteme.create.description")));
    public static final PermissionNode<Boolean> SWITCH = Util.make(new PermissionNode<>("voteme", "switch", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.switch.name"), new TranslatableComponent("permission.voteme.switch.description")));
    public static final PermissionNode<Boolean> MODIFY = Util.make(new PermissionNode<>("voteme", "modify", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.modify.name"), new TranslatableComponent("permission.voteme.modify.description")));
    public static final PermissionNode<Boolean> QUERY = Util.make(new PermissionNode<>("voteme", "query", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.query.name"), new TranslatableComponent("permission.voteme.query.description")));
    public static final PermissionNode<Boolean> OPEN = Util.make(new PermissionNode<>("voteme", "open", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.open.name"), new TranslatableComponent("permission.voteme.open.description")));
    public static final PermissionNode<Boolean> GIVE = Util.make(new PermissionNode<>("voteme", "give", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.give.name"), new TranslatableComponent("permission.voteme.give.description")));
    public static final PermissionNode<Boolean> LIST = Util.make(new PermissionNode<>("voteme", "list", PermissionTypes.BOOLEAN, VoteMePermissions::function),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.list.name"), new TranslatableComponent("permission.voteme.list.description")));
    public static final PermissionNode<Boolean> OPEN_VOTER = Util.make(new PermissionNode<>("voteme", "open.voter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.open.voter.name"), new TranslatableComponent("permission.voteme.open.voter.description")));
    public static final PermissionNode<Boolean> CREATE_COUNTER = Util.make(new PermissionNode<>("voteme", "create.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.create.counter.name"), new TranslatableComponent("permission.voteme.create.counter.description")));
    public static final PermissionNode<Boolean> MODIFY_COUNTER = Util.make(new PermissionNode<>("voteme", "modify.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.modify.counter.name"), new TranslatableComponent("permission.voteme.modify.counter.description")));
    public static final PermissionNode<Boolean> SWITCH_COUNTER = Util.make(new PermissionNode<>("voteme", "switch.counter", PermissionTypes.BOOLEAN, VoteMePermissions::always),
            node -> node.setInformation(new TranslatableComponent("permission.voteme.switch.counter.name"), new TranslatableComponent("permission.voteme.switch.counter.description")));

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
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server.getProfileCache().get(uuid).map(server::getProfilePermissions).orElse(0) >= 3;
        }
        return player.hasPermissions(3);
    }

    private static boolean function(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        if (player == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server.getProfileCache().get(uuid).map(server::getProfilePermissions).orElse(0) >= 2;
        }
        return player.hasPermissions(2);
    }

    private static boolean always(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        return true;
    }
}
