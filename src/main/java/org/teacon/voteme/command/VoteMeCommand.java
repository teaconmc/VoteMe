package org.teacon.voteme.command;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;
import org.teacon.voteme.item.component.ArtifactID;
import org.teacon.voteme.item.component.CategoryID;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;
import org.teacon.voteme.vote.VoteList;

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
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static org.teacon.voteme.command.AliasArgumentType.alias;
import static org.teacon.voteme.command.AliasArgumentType.getAlias;
import static org.teacon.voteme.command.ArtifactArgumentType.artifact;
import static org.teacon.voteme.command.ArtifactArgumentType.getArtifact;
import static org.teacon.voteme.command.VoteMePermissions.*;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoteMeCommand {
    public static final SimpleCommandExceptionType ALIAS_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.voteme.alias.invalid"));
    public static final SimpleCommandExceptionType ARTIFACT_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.voteme.artifact.invalid"));
    public static final DynamicCommandExceptionType ARTIFACT_NOT_FOUND = new DynamicCommandExceptionType(a -> Component.translatable("argument.voteme.artifact.notfound", a));
    public static final Dynamic2CommandExceptionType ARTIFACT_SAME_ALIAS = new Dynamic2CommandExceptionType((a, b) -> Component.translatable("argument.voteme.artifact.samealias", a, b));
    public static final DynamicCommandExceptionType ARTIFACT_SAME_NAME = new DynamicCommandExceptionType(a -> Component.translatable("argument.voteme.artifact.samename", a));
    public static final DynamicCommandExceptionType CATEGORY_NOT_FOUND = new DynamicCommandExceptionType(c -> Component.translatable("argument.voteme.category.notfound", c));
    public static final Dynamic2CommandExceptionType VOTE_DISABLED = new Dynamic2CommandExceptionType((c, a) -> Component.translatable("argument.voteme.vote_list.disabled", c, a));
    public static final Dynamic2CommandExceptionType VOTE_ENABLED = new Dynamic2CommandExceptionType((c, a) -> Component.translatable("argument.voteme.vote_list.enabled", c, a));
    public static final DynamicCommandExceptionType VOTE_UNMODIFIABLE = new DynamicCommandExceptionType(c -> Component.translatable("argument.voteme.vote_list.unmodifiable", c));

    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().map(Identifier::toString), b);
    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION_ENABLED = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(VoteDataStorage.get(c.getSource().getServer())::hasEnabled).map(Identifier::toString), b);
    public static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTION_MODIFIABLE = (c, b) -> SharedSuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(id -> VoteCategoryHandler.getCategory(id).filter(e -> e.enabledModifiable).isPresent()).map(Identifier::toString), b);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("voteme")
                .then(literal("admin")
                        .requires(permission(3, ADMIN, ADMIN_CREATE, ADMIN_REMOVE, ADMIN_MERGE, ADMIN_CLEAR, ADMIN_SWITCH))
                        .then(literal("create")
                                .requires(permission(3, ADMIN, ADMIN_CREATE))
                                .then(literal("alias")
                                        .then(argument("alias", alias())
                                                .then(literal("title")
                                                        .then(argument("title", greedyString())
                                                                .executes(VoteMeCommand::adminCreateArtifactWithAlias)))))
                                .then(literal("title")
                                        .then(argument("title", greedyString())
                                                .executes(VoteMeCommand::adminCreateArtifact))))
                        .then(literal("remove")
                                .requires(permission(3, ADMIN, ADMIN_REMOVE))
                                .then(argument("artifact", artifact())
                                        .executes(VoteMeCommand::adminRemoveArtifact)))
                        .then(literal("merge")
                                .requires(permission(3, ADMIN, ADMIN_MERGE))
                                .then(argument("artifact", artifact())
                                        .then(literal("from")
                                                .then(argument("artifact-from", artifact())
                                                        .executes(VoteMeCommand::adminMergeVotes)))))
                        .then(literal("clear")
                                .requires(permission(3, ADMIN, ADMIN_CLEAR))
                                .then(argument("artifact", artifact())
                                        .then(argument("category", id())
                                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                                .executes(VoteMeCommand::adminClearVotes))))
                        .then(literal("switch")
                                .requires(permission(3, ADMIN, ADMIN_SWITCH))
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
                        .requires(permission(2, SWITCH, ADMIN, ADMIN_SWITCH))
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
                        .requires(permission(2, MODIFY))
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
                        .requires(permission(2, QUERY))
                        .then(argument("target", gameProfile())
                                .then(argument("artifact", artifact())
                                        .then(argument("category", id())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .executes(VoteMeCommand::queryVoter)))
                                .then(argument("category", id())
                                        .suggests(CATEGORY_SUGGESTION)
                                        .executes(VoteMeCommand::queryVoterList))))
                .then(literal("open")
                        .requires(permission(2, OPEN))
                        .then(argument("targets", players())
                                .then(literal("voter")
                                        .then(argument("artifact", artifact())
                                                .executes(VoteMeCommand::openVoter)))))
                .then(literal("give")
                        .requires(permission(2, GIVE))
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
                        .requires(permission(2, LIST))
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
            if (source.getSourceProfile().isPresent()) {
                var id = source.getSourceProfile().get().id();
                return Arrays.stream(permissionNodes).anyMatch(n -> PermissionAPI.getOfflinePermission(id, n));
            }
            return hasPermissionLevel(source.permissions(), level);
        };
    }

    private static int listRoles(CommandContext<CommandSourceStack> context) {
        Collection<? extends Identifier> roles = VoteRoleHandler.getIds();
        int size = roles.size();
        if (size > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.list.roles.success",
                    size, ComponentUtils.formatList(roles, VoteMeCommand::toRoleText)), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.list.roles.none"), false);
        }
        return size;
    }

    private static int listCategories(CommandContext<CommandSourceStack> context) {
        Collection<? extends Identifier> categories = VoteCategoryHandler.getIds();
        int size = categories.size();
        if (size > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.list.categories.success",
                    size, ComponentUtils.formatList(categories, VoteMeCommand::toCategoryText)), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.list.categories.none"), false);
        }
        return size;
    }

    private static int listArtifacts(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        VoteArtifactNames artifactNames = VoteDataStorage.get(source.getServer()).getArtifactNames();
        Collection<? extends UUID> artifactIDs = artifactNames.getUUIDs();
        int size = artifactIDs.size();
        if (size > 0) {
            source.sendSuccess(() -> Component.translatable("commands.voteme.list.artifacts.success", size,
                    ComponentUtils.formatList(artifactIDs, uuid -> toArtifactText(artifactNames, uuid))), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.voteme.list.artifacts.none"), false);
        }
        return size;
    }

    private static int giveCounter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        Identifier location = getId(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<Identifier, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        for (ServerPlayer player : targets) {
            ItemStack item = CounterItem.INSTANCE.get().getDefaultInstance();
            item.set(CategoryID.INSTANCE, category.getKey());
            item.set(ArtifactID.INSTANCE, artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int giveVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            ItemStack item = VoterItem.INSTANCE.get().getDefaultInstance();
            item.set(ArtifactID.INSTANCE, artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int openVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayer> targets = getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            ItemStack fakeVoter = new ItemStack(VoterItem.INSTANCE.get());
            fakeVoter.set(ArtifactID.INSTANCE, artifactID);
            VoterItem.INSTANCE.get().open(player, fakeVoter);
        }
        return targets.size();
    }

    private static int queryVoter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        // noinspection DuplicatedCode
        Collection<NameAndId> profiles = getGameProfiles(context, "target");
        Identifier location = getId(context, "category");
        MinecraftServer server = context.getSource().getServer();
        VoteDataStorage handler = VoteDataStorage.get(server);
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            boolean enabledDefault = category.enabledDefault;
            int id = handler.getIdOrCreate(artifactID, location);
            Optional<VoteList> entryOptional = handler.getVoteList(id).filter(e -> e.getEnabled().orElse(enabledDefault));
            if (entryOptional.isPresent()) {
                int voted = 0;
                VoteList votes = entryOptional.get();
                for (NameAndId profile : profiles) {
                    int voteLevel = votes.get(profile.id());
                    Collection<? extends Identifier> roles = votes.getRoles(profile.id());
                    Instant time = votes.getTime(profile.id()).orElse(Instant.EPOCH).truncatedTo(ChronoUnit.SECONDS);
                    String timeString = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.atZone(ZoneId.systemDefault()));
                    if (Objects.equals(context.getSource().getEntity(), server.getPlayerList().getPlayer(profile.id()))) {
                        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.query.success." + voteLevel,
                                profile.name(), toCategoryText(location), toArtifactText(artifactNames, artifactID), timeString,
                                ComponentUtils.formatList(roles, VoteMeCommand::toRoleText)), false);
                    } else if (voteLevel > 0) {
                        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.query.success.voted",
                                profile.name(), toCategoryText(location), toArtifactText(artifactNames, artifactID)), false);
                    } else {
                        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.query.success.0",
                                profile.name(), toCategoryText(location), toArtifactText(artifactNames, artifactID)), false);
                    }
                    voted += voteLevel > 0 ? 1 : 0;
                }
                return voted;
            }
            throw VOTE_DISABLED.create(toCategoryText(location), toArtifactText(artifactNames, artifactID));
        }
        throw CATEGORY_NOT_FOUND.create(location);
    }

    private static int queryVoterList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // noinspection DuplicatedCode
        Collection<NameAndId> profiles = getGameProfiles(context, "target");
        Identifier location = getId(context, "category");
        MinecraftServer server = context.getSource().getServer();
        VoteDataStorage handler = VoteDataStorage.get(server);
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            boolean enabledDefault = category.enabledDefault;
            Set<UUID> disabledArtifacts = new LinkedHashSet<>();
            Map<NameAndId, ListMultimap<Integer, UUID>> totalVoted = new LinkedHashMap<>();
            for (NameAndId profile : profiles) {
                // noinspection RedundantTypeArguments
                totalVoted.put(profile, LinkedListMultimap.<Integer, UUID>create());
            }
            for (UUID artifactID : artifactNames.getUUIDs()) {
                int id = handler.getIdOrCreate(artifactID, location);
                Optional<VoteList> entryOptional = handler.getVoteList(id).filter(e -> e.getEnabled().orElse(enabledDefault));
                if (entryOptional.isPresent()) {
                    VoteList votes = entryOptional.get();
                    for (NameAndId profile : profiles) {
                        totalVoted.get(profile).put(votes.get(profile.id()), artifactID);
                    }
                    continue;
                }
                disabledArtifacts.add(artifactID);
            }
            int voted = 0;
            for (Map.Entry<NameAndId, ListMultimap<Integer, UUID>> entry : totalVoted.entrySet()) {
                NameAndId profile = entry.getKey();
                if (Objects.equals(context.getSource().getEntity(), server.getPlayerList().getPlayer(profile.id()))) {
                    for (int voteLevel = 5; voteLevel >= 0; --voteLevel) {
                        Collection<UUID> artifactIDs = entry.getValue().get(voteLevel);
                        if (!artifactIDs.isEmpty()) {
                            String voteLevelKey = "commands.voteme.query.list.success." + voteLevel;
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    voteLevelKey, profile.name(), toCategoryText(location),
                                    ComponentUtils.formatList(artifactIDs, uuid -> toArtifactText(artifactNames, uuid))), false);
                            voted += voteLevel > 0 ? artifactIDs.size() : 0;
                        }
                    }
                } else {
                    Collection<UUID> nonVotedArtifactIDs = entry.getValue().removeAll(0);
                    Collection<UUID> artifactIDs = entry.getValue().values();
                    if (!artifactIDs.isEmpty()) {
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.voteme.query.list.success.voted", profile.name(), toCategoryText(location),
                                ComponentUtils.formatList(artifactIDs, uuid -> toArtifactText(artifactNames, uuid))), false);
                        voted += artifactIDs.size();
                    }
                    if (!nonVotedArtifactIDs.isEmpty()) {
                        context.getSource().sendSuccess(() -> Component.translatable(
                                "commands.voteme.query.list.success.0", profile.name(), toCategoryText(location),
                                ComponentUtils.formatList(nonVotedArtifactIDs, uuid -> toArtifactText(artifactNames, uuid))), false);
                    }
                }
            }
            if (!disabledArtifacts.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.voteme.query.list.success.disabled", toCategoryText(location),
                        ComponentUtils.formatList(disabledArtifacts, uuid -> toArtifactText(artifactNames, uuid))), true);
            }
            return voted;
        }
        throw CATEGORY_NOT_FOUND.create(location);
    }

    private static int modifyArtifactTitle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String newName = getString(context, "title");
        UUID artifactID = getArtifact(context, "artifact");
        CommandSourceStack source = context.getSource();
        VoteArtifactNames artifactNames = VoteDataStorage.get(source.getServer()).getArtifactNames();
        String name = artifactNames.getName(artifactID);
        MutableComponent artifactText = toArtifactText(artifactNames, artifactID);
        if (!name.equals(newName)) {
            artifactNames.putName(source, artifactID, newName);
            MutableComponent artifactTextNew = toArtifactText(artifactNames, artifactID);
            source.sendSuccess(() -> Component.translatable("commands.voteme.modify.success",
                    artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_NAME.create(artifactText);
    }

    private static int modifyArtifactAlias(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String newAlias = getAlias(context, "alias");
        UUID artifactID = getArtifact(context, "artifact");
        CommandSourceStack source = context.getSource();
        VoteArtifactNames artifactNames = VoteDataStorage.get(source.getServer()).getArtifactNames();
        MutableComponent artifactText = toArtifactText(artifactNames, artifactID);
        Optional<UUID> conflict = artifactNames.getByAlias(newAlias);
        if (conflict.isEmpty()) {
            artifactNames.putAlias(source, artifactID, newAlias);
            MutableComponent artifactTextNew = toArtifactText(artifactNames, artifactID);
            source.sendSuccess(() -> Component.translatable("commands.voteme.modify.success",
                    artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(artifactNames, conflict.get()));
    }

    private static int modifyArtifactUnAlias(CommandContext<CommandSourceStack> context) {
        UUID artifactID = getArtifact(context, "artifact");
        CommandSourceStack source = context.getSource();
        VoteArtifactNames artifactNames = VoteDataStorage.get(source.getServer()).getArtifactNames();
        MutableComponent artifactText = toArtifactText(artifactNames, artifactID);
        artifactNames.putAlias(source, artifactID, "");
        MutableComponent artifactTextNew = toArtifactText(artifactNames, artifactID);
        source.sendSuccess(() -> Component.translatable("commands.voteme.modify.success",
                artifactText.withStyle(ChatFormatting.STRIKETHROUGH), artifactTextNew), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int switchOnVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchOffVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchUnsetVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int adminSwitchOnVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchOffVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchUnsetVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier location = getId(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminClearVotes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Identifier location = getId(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<Identifier, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        VoteDataStorage handler = VoteDataStorage.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        handler.getVoteList(id).orElseThrow(IllegalStateException::new).clear();
        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.clear.success",
                toCategoryText(category.getKey()), toArtifactText(handler.getArtifactNames(), artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminMergeVotes(CommandContext<CommandSourceStack> context) {
        UUID artifactIDFrom = getArtifact(context, "artifact-from"), artifactID = getArtifact(context, "artifact");
        VoteDataStorage handler = VoteDataStorage.get(context.getSource().getServer());
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        for (Identifier location : VoteCategoryHandler.getIds()) {
            VoteList entryFrom = handler.getVoteList(handler.getIdOrCreate(artifactIDFrom, location)).orElseThrow(IllegalStateException::new);
            VoteList entry = handler.getVoteList(handler.getIdOrCreate(artifactID, location)).orElseThrow(IllegalStateException::new);
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.merge.success", entry.merge(entryFrom),
                    toCategoryText(location), toArtifactText(artifactNames, artifactIDFrom), toArtifactText(artifactNames, artifactID)), true);
        }
        if (!Objects.equals(artifactIDFrom, artifactID)) {
            VoteDataStorage.getAllCommentsFor(handler, artifactIDFrom).forEach((uuid, commentsFrom) -> {
                ImmutableList<String> comments = VoteDataStorage.getCommentFor(handler, artifactID, uuid);
                VoteDataStorage.putCommentFor(handler, artifactID, uuid, ImmutableList.<String>builder().addAll(comments).addAll(commentsFrom).build());
            });
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.merge.success.comments",
                toArtifactText(artifactNames, artifactIDFrom), toArtifactText(artifactNames, artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminRemoveArtifact(CommandContext<CommandSourceStack> context) {
        UUID artifactID = getArtifact(context, "artifact");
        VoteArtifactNames artifactNames = VoteDataStorage.get(context.getSource().getServer()).getArtifactNames();
        MutableComponent artifactText = toArtifactText(artifactNames, artifactID);
        artifactNames.putName(context.getSource(), artifactID, "");
        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.admin.remove.success",
                artifactText.withStyle(ChatFormatting.STRIKETHROUGH)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifact(CommandContext<CommandSourceStack> context) {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        VoteArtifactNames artifactNames = VoteDataStorage.get(context.getSource().getServer()).getArtifactNames();
        artifactNames.putName(context.getSource(), newArtifactID, newName);
        MutableComponent artifactText = toArtifactText(artifactNames, newArtifactID);
        context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.admin.create.success", artifactText), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifactWithAlias(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        String newAlias = getAlias(context, "alias");
        VoteArtifactNames artifactNames = VoteDataStorage.get(context.getSource().getServer()).getArtifactNames();
        Optional<UUID> conflict = artifactNames.getByAlias(newAlias);
        if (conflict.isEmpty()) {
            artifactNames.putName(context.getSource(), newArtifactID, newName);
            artifactNames.putAlias(context.getSource(), newArtifactID, newAlias);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.voteme.admin.create.success", toArtifactText(artifactNames, newArtifactID)), true);

            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(artifactNames, conflict.get()));
    }

    private static int processSwitchOn(CommandContext<CommandSourceStack> context,
                                       Pair<Identifier, VoteCategory> category,
                                       UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteDataStorage handler = VoteDataStorage.get(context.getSource().getServer());
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteList> entryOptional = handler.getVoteList(id).filter(e -> !e.getEnabled().orElse(false));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().setEnabled(true);
                context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.switch.on",
                        toCategoryText(category.getKey()), toArtifactText(artifactNames, artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_ENABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactNames, artifactID));
    }

    private static int processSwitchOff(CommandContext<CommandSourceStack> context,
                                        Pair<Identifier, VoteCategory> category,
                                        UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteDataStorage handler = VoteDataStorage.get(context.getSource().getServer());
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteList> entryOptional = handler.getVoteList(id).filter(e -> e.getEnabled().orElse(true));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().setEnabled(false);
                context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.switch.off",
                        toCategoryText(category.getKey()), toArtifactText(artifactNames, artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_DISABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactNames, artifactID));
    }

    private static int processSwitchUnset(CommandContext<CommandSourceStack> context,
                                          Pair<Identifier, VoteCategory> category,
                                          UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteDataStorage handler = VoteDataStorage.get(context.getSource().getServer());
        VoteArtifactNames artifactNames = handler.getArtifactNames();
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        if (category.getValue().enabledModifiable || force) {
            handler.getVoteList(id).orElseThrow(NullPointerException::new).unsetEnabled();
            context.getSource().sendSuccess(() -> Component.translatable("commands.voteme.switch.unset",
                    toCategoryText(category.getKey()), toArtifactText(artifactNames, artifactID)), true);
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
                itemEntity.setTarget(player.getUUID());
            }
        }
    }

    private static MutableComponent toRoleText(Identifier input) {
        return VoteRoleHandler.getText(input).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static MutableComponent toCategoryText(Identifier input) {
        return VoteCategoryHandler.getText(input).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent toArtifactText(VoteArtifactNames artifactNames, UUID input) {
        return artifactNames.toText(input).withStyle(ChatFormatting.GREEN);
    }

    private static boolean hasPermissionLevel(PermissionSet permissions, int level) {
        if (permissions instanceof LevelBasedPermissionSet levelBased) {
            return levelBased.level().isEqualOrHigherThan(PermissionLevel.byId(level));
        }
        return false;
    }
}
