package org.teacon.voteme.command;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.getPlayers;
import static net.minecraft.commands.arguments.EntityArgument.players;
import static net.minecraft.commands.arguments.GameProfileArgument.gameProfile;
import static net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles;
import static net.minecraft.commands.arguments.ResourceLocationArgument.getId;
import static net.minecraft.commands.arguments.ResourceLocationArgument.id;
import static org.teacon.voteme.command.AliasArgumentType.alias;
import static org.teacon.voteme.command.AliasArgumentType.getAlias;
import static org.teacon.voteme.command.ArtifactArgumentType.artifact;
import static org.teacon.voteme.command.ArtifactArgumentType.getArtifact;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMeCommand {
    public static final SimpleCommandExceptionType ALIAS_INVALID = new SimpleCommandExceptionType(new TranslatableComponent("argument.voteme.alias.invalid"));
    public static final SimpleCommandExceptionType ARTIFACT_INVALID = new SimpleCommandExceptionType(new TranslatableComponent("argument.voteme.artifact.invalid"));
    public static final DynamicCommandExceptionType ARTIFACT_NOT_FOUND = new DynamicCommandExceptionType(a -> new TranslatableComponent("argument.voteme.artifact.notfound", a));
    public static final Dynamic2CommandExceptionType ARTIFACT_SAME_ALIAS = new Dynamic2CommandExceptionType((a, b) -> new TranslatableComponent("argument.voteme.artifact.samealias", a, b));
    public static final DynamicCommandExceptionType ARTIFACT_SAME_NAME = new DynamicCommandExceptionType(a -> new TranslatableComponent("argument.voteme.artifact.samename", a));
    public static final DynamicCommandExceptionType CATEGORY_NOT_FOUND = new DynamicCommandExceptionType(c -> new TranslatableComponent("argument.voteme.category.notfound", c));
    public static final Dynamic2CommandExceptionType VOTE_DISABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslatableComponent("argument.voteme.vote_list.disabled", c, a));
    public static final Dynamic2CommandExceptionType VOTE_ENABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslatableComponent("argument.voteme.vote_list.enabled", c, a));
    public static final DynamicCommandExceptionType VOTE_UNMODIFIABLE = new DynamicCommandExceptionType(c -> new TranslatableComponent("argument.voteme.vote_list.unmodifiable", c));

    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION_ENABLED = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(VoteListHandler.get(c.getSource().getServer())::hasEnabled).map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION_MODIFIABLE = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(id -> VoteCategoryHandler.getCategory(id).filter(e -> e.enabledModifiable).isPresent()).map(ResourceLocation::toString), b);

    public static final PermissionNode<Boolean> PERMISSION_ADMIN = Util.make(new PermissionNode<>("voteme", "admin", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.name"), new TranslatableComponent("permission.voteme.admin.description")));
    public static final PermissionNode<Boolean> PERMISSION_ADMIN_CREATE = Util.make(new PermissionNode<>("voteme", "admin.create", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.create.name"), new TranslatableComponent("permission.voteme.admin.create.description")));
    public static final PermissionNode<Boolean> PERMISSION_ADMIN_REMOVE = Util.make(new PermissionNode<>("voteme", "admin.remove", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.remove.name"), new TranslatableComponent("permission.voteme.admin.remove.description")));
    public static final PermissionNode<Boolean> PERMISSION_ADMIN_MERGE = Util.make(new PermissionNode<>("voteme", "admin.merge", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.merge.name"), new TranslatableComponent("permission.voteme.admin.merge.description")));
    public static final PermissionNode<Boolean> PERMISSION_ADMIN_CLEAR = Util.make(new PermissionNode<>("voteme", "admin.clear", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.clear.name"), new TranslatableComponent("permission.voteme.admin.clear.description")));
    public static final PermissionNode<Boolean> PERMISSION_ADMIN_SWITCH = Util.make(new PermissionNode<>("voteme", "admin.switch", PermissionTypes.BOOLEAN, VoteMeCommand::moderator), p -> p.setInformation(new TranslatableComponent("permission.voteme.admin.switch.name"), new TranslatableComponent("permission.voteme.admin.switch.description")));
    public static final PermissionNode<Boolean> PERMISSION_CREATE = Util.make(new PermissionNode<>("voteme", "create", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.create.name"), new TranslatableComponent("permission.voteme.create.description")));
    public static final PermissionNode<Boolean> PERMISSION_SWITCH = Util.make(new PermissionNode<>("voteme", "switch", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.switch.name"), new TranslatableComponent("permission.voteme.switch.description")));
    public static final PermissionNode<Boolean> PERMISSION_MODIFY = Util.make(new PermissionNode<>("voteme", "modify", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.modify.name"), new TranslatableComponent("permission.voteme.modify.description")));
    public static final PermissionNode<Boolean> PERMISSION_QUERY = Util.make(new PermissionNode<>("voteme", "query", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.query.name"), new TranslatableComponent("permission.voteme.query.description")));
    public static final PermissionNode<Boolean> PERMISSION_OPEN = Util.make(new PermissionNode<>("voteme", "open", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.open.name"), new TranslatableComponent("permission.voteme.open.description")));
    public static final PermissionNode<Boolean> PERMISSION_GIVE = Util.make(new PermissionNode<>("voteme", "give", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.give.name"), new TranslatableComponent("permission.voteme.give.description")));
    public static final PermissionNode<Boolean> PERMISSION_SELECT = Util.make(new PermissionNode<>("voteme", "select", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.select.name"), new TranslatableComponent("permission.voteme.select.description")));
    public static final PermissionNode<Boolean> PERMISSION_LIST = Util.make(new PermissionNode<>("voteme", "list", PermissionTypes.BOOLEAN, VoteMeCommand::function), p -> p.setInformation(new TranslatableComponent("permission.voteme.list.name"), new TranslatableComponent("permission.voteme.list.description")));
    public static final PermissionNode<Boolean> PERMISSION_OPEN_VOTER = Util.make(new PermissionNode<>("voteme", "open.voter", PermissionTypes.BOOLEAN, VoteMeCommand::always), p -> p.setInformation(new TranslatableComponent("permission.voteme.open.voter.name"), new TranslatableComponent("permission.voteme.open.voter.description")));
    public static final PermissionNode<Boolean> PERMISSION_CREATE_COUNTER = Util.make(new PermissionNode<>("voteme", "create.counter", PermissionTypes.BOOLEAN, VoteMeCommand::always), p -> p.setInformation(new TranslatableComponent("permission.voteme.create.counter.name"), new TranslatableComponent("permission.voteme.create.counter.description")));
    public static final PermissionNode<Boolean> PERMISSION_MODIFY_COUNTER = Util.make(new PermissionNode<>("voteme", "modify.counter", PermissionTypes.BOOLEAN, VoteMeCommand::always), p -> p.setInformation(new TranslatableComponent("permission.voteme.modify.counter.name"), new TranslatableComponent("permission.voteme.modify.counter.description")));
    public static final PermissionNode<Boolean> PERMISSION_SWITCH_COUNTER = Util.make(new PermissionNode<>("voteme", "switch.counter", PermissionTypes.BOOLEAN, VoteMeCommand::always), p -> p.setInformation(new TranslatableComponent("permission.voteme.switch.counter.name"), new TranslatableComponent("permission.voteme.switch.counter.description")));

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypes.register("voteme_alias", AliasArgumentType.class, new EmptyArgumentSerializer<>(AliasArgumentType::alias));
            ArgumentTypes.register("voteme_artifact", ArtifactArgumentType.class, new EmptyArgumentSerializer<>(ArtifactArgumentType::artifact));
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class, VoteMeCommand::register);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PermissionGatherEvent.Nodes.class, VoteMeCommand::register);
    }

    private static void register(PermissionGatherEvent.Nodes event) {
        ImmutableList.Builder<PermissionNode<?>> builder = ImmutableList.builder();

        builder.add(PERMISSION_ADMIN);
        builder.add(PERMISSION_ADMIN_CREATE);
        builder.add(PERMISSION_ADMIN_REMOVE);
        builder.add(PERMISSION_ADMIN_MERGE);
        builder.add(PERMISSION_ADMIN_CLEAR);
        builder.add(PERMISSION_ADMIN_SWITCH);
        builder.add(PERMISSION_CREATE);
        builder.add(PERMISSION_SWITCH);
        builder.add(PERMISSION_MODIFY);
        builder.add(PERMISSION_QUERY);
        builder.add(PERMISSION_OPEN);
        builder.add(PERMISSION_GIVE);
        builder.add(PERMISSION_SELECT);
        builder.add(PERMISSION_LIST);
        builder.add(PERMISSION_OPEN_VOTER);
        builder.add(PERMISSION_CREATE_COUNTER);
        builder.add(PERMISSION_MODIFY_COUNTER);
        builder.add(PERMISSION_SWITCH_COUNTER);

        event.addNodes(builder.build());
    }

    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("voteme")
                .then(literal("admin")
                        .requires(permission(3, PERMISSION_ADMIN,
                                PERMISSION_ADMIN_CREATE, PERMISSION_ADMIN_REMOVE,
                                PERMISSION_ADMIN_MERGE, PERMISSION_ADMIN_CLEAR, PERMISSION_ADMIN_SWITCH))
                        .then(literal("create")
                                .requires(permission(3, PERMISSION_ADMIN, PERMISSION_ADMIN_CREATE))
                                .then(literal("alias")
                                        .then(argument("alias", alias())
                                                .then(literal("title")
                                                        .then(argument("title", greedyString())
                                                                .executes(VoteMeCommand::adminCreateArtifactWithAlias)))))
                                .then(literal("title")
                                        .then(argument("title", greedyString())
                                                .executes(VoteMeCommand::adminCreateArtifact))))
                        .then(literal("remove")
                                .requires(permission(3, PERMISSION_ADMIN, PERMISSION_ADMIN_REMOVE))
                                .then(argument("artifact", artifact())
                                        .executes(VoteMeCommand::adminRemoveArtifact)))
                        .then(literal("merge")
                                .requires(permission(3, PERMISSION_ADMIN, PERMISSION_ADMIN_MERGE))
                                .then(argument("artifact", artifact())
                                        .then(literal("from")
                                                .then(argument("artifact-from", artifact())
                                                        .executes(VoteMeCommand::adminMergeVotes)))))
                        .then(literal("clear")
                                .requires(permission(3, PERMISSION_ADMIN, PERMISSION_ADMIN_CLEAR))
                                .then(argument("artifact", artifact())
                                        .then(argument("category", id())
                                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                                .executes(VoteMeCommand::adminClearVotes))))
                        .then(literal("switch")
                                .requires(permission(3, PERMISSION_ADMIN, PERMISSION_ADMIN_SWITCH))
                                .then(argument("artifact", artifact())
                                        .then(argument("category", id())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .then(literal("unset")
                                                        .executes(VoteMeCommand::adminSwitchUnsetVotes))
                                                .then(literal("off")
                                                        .executes(VoteMeCommand::adminSwitchOffVotes))
                                                .then(literal("on")
                                                        .executes(VoteMeCommand::adminSwitchOnVotes))))))
                .then(literal("switch")
                        .requires(permission(2, PERMISSION_SWITCH, PERMISSION_ADMIN, PERMISSION_ADMIN_SWITCH))
                        .then(argument("artifact", artifact())
                                .then(argument("category", id())
                                        .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                        .then(literal("unset")
                                                .executes(VoteMeCommand::switchUnsetVotes))
                                        .then(literal("off")
                                                .executes(VoteMeCommand::switchOffVotes))
                                        .then(literal("on")
                                                .executes(VoteMeCommand::switchOnVotes)))))
                .then(literal("modify")
                        .requires(permission(2, PERMISSION_MODIFY))
                        .then(argument("artifact", artifact())
                                .then(literal("unalias")
                                        .executes(VoteMeCommand::modifyArtifactUnAlias))
                                .then(literal("alias")
                                        .then(argument("alias", alias())
                                                .executes(VoteMeCommand::modifyArtifactAlias)))
                                .then(literal("title")
                                        .then(argument("title", greedyString())
                                                .executes(VoteMeCommand::modifyArtifactTitle)))))
                .then(literal("query")
                        .requires(permission(2, PERMISSION_QUERY))
                        .then(argument("target", gameProfile())
                                .then(argument("artifact", artifact())
                                        .then(argument("category", id())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .executes(VoteMeCommand::queryVoter)))
                                .then(argument("category", id())
                                        .suggests(CATEGORY_SUGGESTION)
                                        .executes(VoteMeCommand::queryVoterList))))
                .then(literal("open")
                        .requires(permission(2, PERMISSION_OPEN))
                        .then(argument("targets", players())
                                .then(literal("voter")
                                        .then(argument("artifact", artifact())
                                                .executes(VoteMeCommand::openVoter)))))
                .then(literal("give")
                        .requires(permission(2, PERMISSION_GIVE))
                        .then(argument("targets", players())
                                .then(literal("voter")
                                        .then(argument("artifact", artifact())
                                                .executes(VoteMeCommand::giveVoter)))
                                .then(literal("counter")
                                        .then(argument("artifact", artifact())
                                                .then(argument("category", id())
                                                        .suggests(CATEGORY_SUGGESTION)
                                                        .executes(VoteMeCommand::giveCounter))))))
                .then(literal("list")
                        .requires(permission(2, PERMISSION_LIST))
                        .then(literal("artifacts")
                                .executes(VoteMeCommand::listArtifacts))
                        .then(literal("categories")
                                .executes(VoteMeCommand::listCategories))
                        .then(literal("roles")
                                .executes(VoteMeCommand::listRoles))));
    }

    @SafeVarargs
    private static Predicate<CommandSourceStack> permission(int level, PermissionNode<Boolean>... permissionNodes) {
        Preconditions.checkArgument(permissionNodes.length > 0, "permission nodes should not be empty");
        return source -> {
            if (source.source instanceof ServerPlayer player) {
                return Arrays.stream(permissionNodes).anyMatch(n -> PermissionAPI.getPermission(player, n));
            }
            return source.hasPermission(level);
        };
    }

    private static int listRoles(CommandContext<CommandSourceStack> context) {
        Collection<? extends ResourceLocation> roles = VoteRoleHandler.getIds();
        int size = roles.size();
        if (size > 0) {
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.list.roles.success",
                    size, ComponentUtils.formatList(roles, VoteMeCommand::toRoleText)), false);
        } else {
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.list.roles.none"), false);
        }
        return size;
    }

    private static int listCategories(CommandContext<CommandSourceStack> context) {
        Collection<? extends ResourceLocation> categories = VoteCategoryHandler.getIds();
        int size = categories.size();
        if (size > 0) {
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.list.categories.success",
                    size, ComponentUtils.formatList(categories, VoteMeCommand::toCategoryText)), false);
        } else {
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.list.categories.none"), false);
        }
        return size;
    }

    private static int listArtifacts(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<? extends UUID> artifactIDs = VoteListHandler.getArtifacts();
        int size = artifactIDs.size();
        if (size > 0) {
            source.sendSuccess(new TranslatableComponent("commands.voteme.list.artifacts.success", size,
                    ComponentUtils.formatList(artifactIDs, VoteMeCommand::toArtifactText)), false);
        } else {
            source.sendSuccess(new TranslatableComponent("commands.voteme.list.artifacts.none"), false);
        }
        return size;
    }

    private static int giveCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<ResourceLocation, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        for (ServerPlayer player : targets) {
            ItemStack item = new ItemStack(CounterItem.INSTANCE);
            item.getOrCreateTag().putString("CurrentCategory", category.getKey().toString());
            item.getOrCreateTag().putUUID("CurrentArtifact", artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int giveVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            ItemStack item = new ItemStack(VoterItem.INSTANCE);
            item.getOrCreateTag().putUUID("CurrentArtifact", artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int openVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("CurrentArtifact", artifactID);
            VoterItem.INSTANCE.open(player, tag);
        }
        return targets.size();
    }

    private static int queryVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<GameProfile> profiles = getGameProfiles(context, "target");
        ResourceLocation location = getId(context, "category");
        MinecraftServer server = context.getSource().getServer();
        VoteListHandler handler = VoteListHandler.get(server);
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            boolean enabledDefault = category.enabledDefault;
            int id = handler.getIdOrCreate(artifactID, location);
            Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.getEnabled().orElse(enabledDefault));
            if (entryOptional.isPresent()) {
                int voted = 0;
                VoteList votes = entryOptional.get().votes;
                for (GameProfile profile : profiles) {
                    int voteLevel = votes.get(profile.getId());
                    Collection<? extends ResourceLocation> roles = votes.getRoles(profile.getId());
                    Instant time = votes.getTime(profile.getId()).orElse(Instant.EPOCH).truncatedTo(ChronoUnit.SECONDS);
                    String timeString = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.atZone(ZoneId.systemDefault()));
                    if (Objects.equals(context.getSource().source, server.getPlayerList().getPlayer(profile.getId()))) {
                        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.success." + voteLevel,
                                profile.getName(), toCategoryText(location), toArtifactText(artifactID), timeString,
                                ComponentUtils.formatList(roles, VoteMeCommand::toRoleText)), false);
                    } else if (voteLevel > 0) {
                        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.success.voted",
                                profile.getName(), toCategoryText(location), toArtifactText(artifactID)), false);
                    } else {
                        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.success.0",
                                profile.getName(), toCategoryText(location), toArtifactText(artifactID)), false);
                    }
                    voted += voteLevel > 0 ? 1 : 0;
                }
                return voted;
            }
            throw VOTE_DISABLED.create(toCategoryText(location), toArtifactText(artifactID));
        }
        throw CATEGORY_NOT_FOUND.create(location);
    }

    private static int queryVoterList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = getGameProfiles(context, "target");
        ResourceLocation location = getId(context, "category");
        MinecraftServer server = context.getSource().getServer();
        VoteListHandler handler = VoteListHandler.get(server);
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            boolean enabledDefault = category.enabledDefault;
            Set<UUID> disabledArtifacts = new LinkedHashSet<>();
            Map<GameProfile, ListMultimap<Integer, UUID>> totalVoted = new LinkedHashMap<>();
            for (GameProfile profile : profiles) {
                // noinspection RedundantTypeArguments
                totalVoted.put(profile, LinkedListMultimap.<Integer, UUID>create());
            }
            for (UUID artifactID : VoteListHandler.getArtifacts()) {
                int id = handler.getIdOrCreate(artifactID, location);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.getEnabled().orElse(enabledDefault));
                if (entryOptional.isPresent()) {
                    VoteList votes = entryOptional.get().votes;
                    for (GameProfile profile : profiles) {
                        totalVoted.get(profile).put(votes.get(profile.getId()), artifactID);
                    }
                    continue;
                }
                disabledArtifacts.add(artifactID);
            }
            int voted = 0;
            for (Map.Entry<GameProfile, ListMultimap<Integer, UUID>> entry : totalVoted.entrySet()) {
                GameProfile profile = entry.getKey();
                if (Objects.equals(context.getSource().source, server.getPlayerList().getPlayer(profile.getId()))) {
                    for (int voteLevel = 5; voteLevel >= 0; --voteLevel) {
                        Collection<UUID> artifactIDs = entry.getValue().get(voteLevel);
                        if (!artifactIDs.isEmpty()) {
                            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.list.success." + voteLevel,
                                    profile.getName(), toCategoryText(location), ComponentUtils.formatList(artifactIDs, VoteMeCommand::toArtifactText)), false);
                            voted += voteLevel > 0 ? artifactIDs.size() : 0;
                        }
                    }
                } else {
                    Collection<UUID> nonVotedArtifactIDs = entry.getValue().removeAll(0);
                    Collection<UUID> artifactIDs = entry.getValue().values();
                    if (!artifactIDs.isEmpty()) {
                        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.list.success.voted",
                                profile.getName(), toCategoryText(location), ComponentUtils.formatList(artifactIDs, VoteMeCommand::toArtifactText)), false);
                        voted += artifactIDs.size();
                    }
                    if (!nonVotedArtifactIDs.isEmpty()) {
                        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.list.success.0",
                                profile.getName(), toCategoryText(location), ComponentUtils.formatList(nonVotedArtifactIDs, VoteMeCommand::toArtifactText)), false);
                    }
                }
            }
            if (!disabledArtifacts.isEmpty()) {
                context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.query.list.success.disabled",
                        toCategoryText(location), ComponentUtils.formatList(disabledArtifacts, VoteMeCommand::toArtifactText)), true);
            }
            return voted;
        }
        throw CATEGORY_NOT_FOUND.create(location);
    }

    private static int modifyArtifactTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String newName = getString(context, "title");
        UUID artifactID = getArtifact(context, "artifact");
        String name = VoteListHandler.getArtifactName(artifactID);
        MutableComponent artifactText = toArtifactText(artifactID);
        if (!name.equals(newName)) {
            VoteListHandler.putArtifactName(context.getSource(), artifactID, newName);
            MutableComponent artifactTextNew = toArtifactText(artifactID);
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.modify.success",
                    artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_NAME.create(artifactText);
    }

    private static int modifyArtifactAlias(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String newAlias = getAlias(context, "alias");
        UUID artifactID = getArtifact(context, "artifact");
        MutableComponent artifactText = toArtifactText(artifactID);
        Optional<UUID> conflict = VoteListHandler.getArtifactByAlias(newAlias);
        if (conflict.isEmpty()) {
            VoteListHandler.putArtifactAlias(context.getSource(), artifactID, newAlias);
            MutableComponent artifactTextNew = toArtifactText(artifactID);
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.modify.success",
                    artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(conflict.get()));
    }

    private static int modifyArtifactUnAlias(CommandContext<CommandSourceStack> context) {
        UUID artifactID = getArtifact(context, "artifact");
        MutableComponent artifactText = toArtifactText(artifactID);
        VoteListHandler.putArtifactAlias(context.getSource(), artifactID, "");
        MutableComponent artifactTextNew = toArtifactText(artifactID);
        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.modify.success",
                artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int switchOnVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchOffVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchUnsetVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int adminSwitchOnVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchOffVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchUnsetVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminClearVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        ResourceLocation location = getId(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<ResourceLocation, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        handler.getEntry(id).orElseThrow(IllegalStateException::new).votes.clear();
        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.clear.success",
                toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminMergeVotes(CommandContext<CommandSourceStack> context) {
        UUID artifactIDFrom = getArtifact(context, "artifact-from"), artifactID = getArtifact(context, "artifact");
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        for (ResourceLocation location : VoteCategoryHandler.getIds()) {
            VoteListEntry entryFrom = handler.getEntry(handler.getIdOrCreate(artifactIDFrom, location)).orElseThrow(IllegalStateException::new);
            VoteListEntry entry = handler.getEntry(handler.getIdOrCreate(artifactID, location)).orElseThrow(IllegalStateException::new);
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.merge.success",
                    entry.votes.merge(entryFrom.votes), toCategoryText(location), toArtifactText(artifactIDFrom), toArtifactText(artifactID)), true);
        }
        if (!Objects.equals(artifactIDFrom, artifactID)) {
            VoteListHandler.getAllCommentsFor(handler, artifactIDFrom).forEach((uuid, commentsFrom) -> {
                ImmutableList<String> comments = VoteListHandler.getCommentFor(handler, artifactID, uuid);
                VoteListHandler.putCommentFor(handler, artifactID, uuid, ImmutableList.<String>builder().addAll(comments).addAll(commentsFrom).build());
            });
        }
        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.merge.success.comments",
                toArtifactText(artifactIDFrom), toArtifactText(artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminRemoveArtifact(CommandContext<CommandSourceStack> context) {
        UUID artifactID = getArtifact(context, "artifact");
        MutableComponent artifactText = toArtifactText(artifactID);
        VoteListHandler.putArtifactName(context.getSource(), artifactID, "");
        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.admin.remove.success",
                artifactText.withStyle(ChatFormatting.STRIKETHROUGH)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifact(CommandContext<CommandSourceStack> context) {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        VoteListHandler.putArtifactName(context.getSource(), newArtifactID, newName);
        MutableComponent artifactText = toArtifactText(newArtifactID);
        context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.admin.create.success", artifactText), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifactWithAlias(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        String newAlias = getAlias(context, "alias");
        Optional<UUID> conflict = VoteListHandler.getArtifactByAlias(newAlias);
        if (conflict.isEmpty()) {
            VoteListHandler.putArtifactName(context.getSource(), newArtifactID, newName);
            VoteListHandler.putArtifactAlias(context.getSource(), newArtifactID, newAlias);
            context.getSource().sendSuccess(new TranslatableComponent(
                    "commands.voteme.admin.create.success", toArtifactText(newArtifactID)), true);

            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(conflict.get()));
    }

    private static int processSwitchOn(CommandContext<CommandSourceStack> context,
                                       Pair<ResourceLocation, VoteCategory> category,
                                       UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> !e.votes.getEnabled().orElse(false));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().votes.setEnabled(true);
                context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.switch.on",
                        toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_ENABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactID));
    }

    private static int processSwitchOff(CommandContext<CommandSourceStack> context,
                                        Pair<ResourceLocation, VoteCategory> category,
                                        UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.getEnabled().orElse(true));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().votes.setEnabled(false);
                context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.switch.off",
                        toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_DISABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactID));
    }

    private static int processSwitchUnset(CommandContext<CommandSourceStack> context,
                                          Pair<ResourceLocation, VoteCategory> category,
                                          UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        if (category.getValue().enabledModifiable || force) {
            handler.getEntry(id).orElseThrow(NullPointerException::new).votes.unsetEnabled();
            context.getSource().sendSuccess(new TranslatableComponent("commands.voteme.switch.unset",
                    toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
            return Command.SINGLE_SUCCESS;
        }
        throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
    }

    private static void processGiveItemToPlayer(ServerPlayer player, ItemStack item) {
        boolean succeed = player.getInventory().add(item);
        if (!succeed) {
            ItemEntity itemEntity = player.drop(item, false);
            if (itemEntity != null) {
                itemEntity.setNoPickUpDelay();
                itemEntity.setOwner(player.getUUID());
            }
        }
    }

    private static MutableComponent toRoleText(ResourceLocation input) {
        return VoteRoleHandler.getText(input).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static MutableComponent toCategoryText(ResourceLocation input) {
        return VoteCategoryHandler.getText(input).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent toArtifactText(UUID input) {
        return VoteListHandler.getArtifactText(input).withStyle(ChatFormatting.GREEN);
    }

    private static boolean always(@Nullable ServerPlayer player, UUID uuid, PermissionDynamicContext<?>... contexts) {
        return true;
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
}
