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
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.ArgumentSerializer;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.commons.lang3.tuple.Pair;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.item.CounterItem;
import org.teacon.voteme.item.VoterItem;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteList;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;
import static net.minecraft.command.arguments.EntityArgument.getPlayers;
import static net.minecraft.command.arguments.EntityArgument.players;
import static net.minecraft.command.arguments.GameProfileArgument.gameProfile;
import static net.minecraft.command.arguments.GameProfileArgument.getGameProfiles;
import static net.minecraft.command.arguments.ResourceLocationArgument.getResourceLocation;
import static net.minecraft.command.arguments.ResourceLocationArgument.resourceLocation;
import static org.teacon.voteme.command.AliasArgumentType.alias;
import static org.teacon.voteme.command.AliasArgumentType.getAlias;
import static org.teacon.voteme.command.ArtifactArgumentType.artifact;
import static org.teacon.voteme.command.ArtifactArgumentType.getArtifact;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoteMeCommand {
    public static final SimpleCommandExceptionType ALIAS_INVALID = new SimpleCommandExceptionType(new TranslationTextComponent("argument.voteme.alias.invalid"));
    public static final SimpleCommandExceptionType ARTIFACT_INVALID = new SimpleCommandExceptionType(new TranslationTextComponent("argument.voteme.artifact.invalid"));
    public static final DynamicCommandExceptionType ARTIFACT_NOT_FOUND = new DynamicCommandExceptionType(a -> new TranslationTextComponent("argument.voteme.artifact.notfound", a));
    public static final Dynamic2CommandExceptionType ARTIFACT_SAME_ALIAS = new Dynamic2CommandExceptionType((a, b) -> new TranslationTextComponent("argument.voteme.artifact.samealias", a, b));
    public static final DynamicCommandExceptionType ARTIFACT_SAME_NAME = new DynamicCommandExceptionType(a -> new TranslationTextComponent("argument.voteme.artifact.samename", a));
    public static final DynamicCommandExceptionType CATEGORY_NOT_FOUND = new DynamicCommandExceptionType(c -> new TranslationTextComponent("argument.voteme.category.notfound", c));
    public static final Dynamic2CommandExceptionType VOTE_DISABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslationTextComponent("argument.voteme.vote_list.disabled", c, a));
    public static final Dynamic2CommandExceptionType VOTE_ENABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslationTextComponent("argument.voteme.vote_list.enabled", c, a));
    public static final DynamicCommandExceptionType VOTE_UNMODIFIABLE = new DynamicCommandExceptionType(c -> new TranslationTextComponent("argument.voteme.vote_list.unmodifiable", c));

    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION_ENABLED = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(VoteListHandler.get(c.getSource().getServer())::hasEnabled).map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION_MODIFIABLE = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(id -> VoteCategoryHandler.getCategory(id).filter(e -> e.enabledModifiable).isPresent()).map(ResourceLocation::toString), b);

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypes.register("voteme_alias", AliasArgumentType.class, new ArgumentSerializer<>(AliasArgumentType::alias));
            ArgumentTypes.register("voteme_artifact", ArtifactArgumentType.class, new ArgumentSerializer<>(ArtifactArgumentType::artifact));
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterCommandsEvent.class, VoteMeCommand::register);
    }

    private static void register(RegisterCommandsEvent event) {
        // register permission nodes
        PermissionAPI.registerNode("voteme.admin", DefaultPermissionLevel.NONE, "Admin operations");
        PermissionAPI.registerNode("voteme.switch", DefaultPermissionLevel.OP, "Switch on or switch off votes");
        PermissionAPI.registerNode("voteme.modify", DefaultPermissionLevel.OP, "Modify vote titles or aliases");
        PermissionAPI.registerNode("voteme.query", DefaultPermissionLevel.OP, "Get votes for different players");
        PermissionAPI.registerNode("voteme.open", DefaultPermissionLevel.OP, "Open vote related GUIs to players");
        PermissionAPI.registerNode("voteme.give", DefaultPermissionLevel.OP, "Give related items to players");
        PermissionAPI.registerNode("voteme.select", DefaultPermissionLevel.OP, "Select a particular artifact");
        PermissionAPI.registerNode("voteme.list", DefaultPermissionLevel.OP, "Listing thing related to votes");
        PermissionAPI.registerNode("voteme.open.voter", DefaultPermissionLevel.ALL, "Open vote related GUIs by voters");
        PermissionAPI.registerNode("voteme.create.counter", DefaultPermissionLevel.ALL, "Create vote titles by counters");
        PermissionAPI.registerNode("voteme.modify.counter", DefaultPermissionLevel.ALL, "Modify vote titles by counters");
        PermissionAPI.registerNode("voteme.switch.counter", DefaultPermissionLevel.ALL, "Switch on or switch off votes by counters");
        // register child commands
        event.getDispatcher().register(literal("voteme")
                .then(literal("admin")
                        .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.create", "voteme.admin.remove", "voteme.admin.merge", "voteme.admin.clear", "voteme.admin.switch"))
                        .then(literal("create")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.create"))
                                .then(literal("alias")
                                        .then(argument("alias", alias())
                                                .then(literal("title")
                                                        .then(argument("title", greedyString())
                                                                .executes(VoteMeCommand::adminCreateArtifactWithAlias)))))
                                .then(literal("title")
                                        .then(argument("title", greedyString())
                                                .executes(VoteMeCommand::adminCreateArtifact))))
                        .then(literal("remove")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.remove"))
                                .then(argument("artifact", artifact())
                                        .executes(VoteMeCommand::adminRemoveArtifact)))
                        .then(literal("merge")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.merge"))
                                .then(argument("artifact", artifact())
                                        .then(literal("from")
                                                .then(argument("artifact-from", artifact())
                                                        .executes(VoteMeCommand::adminMergeVotes)))))
                        .then(literal("clear")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.clear"))
                                .then(argument("artifact", artifact())
                                        .then(argument("category", resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                                .executes(VoteMeCommand::adminClearVotes))))
                        .then(literal("switch")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.switch"))
                                .then(argument("artifact", artifact())
                                        .then(argument("category", resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .then(literal("unset")
                                                        .executes(VoteMeCommand::adminSwitchUnsetVotes))
                                                .then(literal("off")
                                                        .executes(VoteMeCommand::adminSwitchOffVotes))
                                                .then(literal("on")
                                                        .executes(VoteMeCommand::adminSwitchOnVotes))))))
                .then(literal("switch")
                        .requires(permission(2, "voteme", "voteme.switch", "voteme.admin", "voteme.admin.switch"))
                        .then(argument("artifact", artifact())
                                .then(argument("category", resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                        .then(literal("unset")
                                                .executes(VoteMeCommand::switchUnsetVotes))
                                        .then(literal("off")
                                                .executes(VoteMeCommand::switchOffVotes))
                                        .then(literal("on")
                                                .executes(VoteMeCommand::switchOnVotes)))))
                .then(literal("modify")
                        .requires(permission(2, "voteme", "voteme.modify"))
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
                        .requires(permission(2, "voteme", "voteme.query"))
                        .then(argument("target", gameProfile())
                                .then(argument("artifact", artifact())
                                        .then(argument("category", resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .executes(VoteMeCommand::queryVoter)))
                                .then(argument("category", resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION)
                                        .executes(VoteMeCommand::queryVoterList))))
                .then(literal("open")
                        .requires(permission(2, "voteme", "voteme.open"))
                        .then(argument("targets", players())
                                .then(literal("voter")
                                        .then(argument("artifact", artifact())
                                                .executes(VoteMeCommand::openVoter)))))
                .then(literal("give")
                        .requires(permission(2, "voteme", "voteme.give"))
                        .then(argument("targets", players())
                                .then(literal("voter")
                                        .then(argument("artifact", artifact())
                                                .executes(VoteMeCommand::giveVoter)))
                                .then(literal("counter")
                                        .then(argument("artifact", artifact())
                                                .then(argument("category", resourceLocation())
                                                        .suggests(CATEGORY_SUGGESTION)
                                                        .executes(VoteMeCommand::giveCounter))))))
                .then(literal("list")
                        .requires(permission(2, "voteme", "voteme.list", "voteme.list.artifacts", "voteme.list.categories", "voteme.list.roles"))
                        .then(literal("artifacts")
                                .requires(permission(2, "voteme", "voteme.list", "voteme.list.artifacts"))
                                .executes(VoteMeCommand::listArtifacts))
                        .then(literal("categories")
                                .requires(permission(2, "voteme", "voteme.list", "voteme.list.categories"))
                                .executes(VoteMeCommand::listCategories))
                        .then(literal("roles")
                                .requires(permission(2, "voteme", "voteme.list", "voteme.list.roles"))
                                .executes(VoteMeCommand::listRoles))));
    }

    private static Predicate<CommandSource> permission(int level, String... permissionNodes) {
        Preconditions.checkArgument(permissionNodes.length > 0, "permission nodes should not be empty");
        return source -> {
            if (source.source instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) source.source;
                return Arrays.stream(permissionNodes).anyMatch(n -> PermissionAPI.hasPermission(player, n));
            }
            return source.hasPermissionLevel(level);
        };
    }

    private static int listRoles(CommandContext<CommandSource> context) {
        Collection<? extends ResourceLocation> roles = VoteRoleHandler.getIds();
        int size = roles.size();
        if (size > 0) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.roles.success",
                    size, TextComponentUtils.func_240649_b_(roles, VoteMeCommand::toRoleText)), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.roles.none"), false);
        }
        return size;
    }

    private static int listCategories(CommandContext<CommandSource> context) {
        Collection<? extends ResourceLocation> categories = VoteCategoryHandler.getIds();
        int size = categories.size();
        if (size > 0) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.categories.success",
                    size, TextComponentUtils.func_240649_b_(categories, VoteMeCommand::toCategoryText)), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.categories.none"), false);
        }
        return size;
    }

    private static int listArtifacts(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        Collection<? extends UUID> artifactIDs = VoteListHandler.getArtifacts();
        int size = artifactIDs.size();
        if (size > 0) {
            source.sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.success", size,
                    TextComponentUtils.func_240649_b_(artifactIDs, VoteMeCommand::toArtifactText)), false);
        } else {
            source.sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.none"), false);
        }
        return size;
    }

    private static int giveCounter(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayerEntity> targets = getPlayers(context, "targets");
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<ResourceLocation, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        for (ServerPlayerEntity player : targets) {
            ItemStack item = new ItemStack(CounterItem.INSTANCE);
            item.getOrCreateTag().putString("CurrentCategory", category.getKey().toString());
            item.getOrCreateTag().putUniqueId("CurrentArtifact", artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int giveVoter(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayerEntity> targets = getPlayers(context, "targets");
        for (ServerPlayerEntity player : targets) {
            ItemStack item = new ItemStack(VoterItem.INSTANCE);
            item.getOrCreateTag().putUniqueId("CurrentArtifact", artifactID);
            processGiveItemToPlayer(player, item);
        }
        return targets.size();
    }

    private static int openVoter(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<ServerPlayerEntity> targets = getPlayers(context, "targets");
        for (ServerPlayerEntity player : targets) {
            CompoundNBT tag = new CompoundNBT();
            tag.putUniqueId("CurrentArtifact", artifactID);
            VoterItem.INSTANCE.open(player, tag);
        }
        return targets.size();
    }

    private static int queryVoter(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        Collection<GameProfile> profiles = getGameProfiles(context, "target");
        ResourceLocation location = getResourceLocation(context, "category");
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
                    if (Objects.equals(context.getSource().source, server.getPlayerList().getPlayerByUUID(profile.getId()))) {
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.success." + voteLevel,
                                profile.getName(), toCategoryText(location), toArtifactText(artifactID), timeString,
                                TextComponentUtils.func_240649_b_(roles, VoteMeCommand::toRoleText)), false);
                    } else if (voteLevel > 0) {
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.success.voted",
                                profile.getName(), toCategoryText(location), toArtifactText(artifactID)), false);
                    } else {
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.success.0",
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

    private static int queryVoterList(CommandContext<CommandSource> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = getGameProfiles(context, "target");
        ResourceLocation location = getResourceLocation(context, "category");
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
                if (Objects.equals(context.getSource().source, server.getPlayerList().getPlayerByUUID(profile.getId()))) {
                    for (int voteLevel = 5; voteLevel >= 0; --voteLevel) {
                        Collection<UUID> artifactIDs = entry.getValue().get(voteLevel);
                        if (!artifactIDs.isEmpty()) {
                            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.list.success." + voteLevel,
                                    profile.getName(), toCategoryText(location), TextComponentUtils.func_240649_b_(artifactIDs, VoteMeCommand::toArtifactText)), false);
                            voted += voteLevel > 0 ? artifactIDs.size() : 0;
                        }
                    }
                } else {
                    Collection<UUID> nonVotedArtifactIDs = entry.getValue().removeAll(0);
                    Collection<UUID> artifactIDs = entry.getValue().values();
                    if (!artifactIDs.isEmpty()) {
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.list.success.voted",
                                profile.getName(), toCategoryText(location), TextComponentUtils.func_240649_b_(artifactIDs, VoteMeCommand::toArtifactText)), false);
                        voted += artifactIDs.size();
                    }
                    if (!nonVotedArtifactIDs.isEmpty()) {
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.list.success.0",
                                profile.getName(), toCategoryText(location), TextComponentUtils.func_240649_b_(nonVotedArtifactIDs, VoteMeCommand::toArtifactText)), false);
                    }
                }
            }
            if (!disabledArtifacts.isEmpty()) {
                context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.query.list.success.disabled",
                        toCategoryText(location), TextComponentUtils.func_240649_b_(disabledArtifacts, VoteMeCommand::toArtifactText)), true);
            }
            return voted;
        }
        throw CATEGORY_NOT_FOUND.create(location);
    }

    private static int modifyArtifactTitle(CommandContext<CommandSource> context) throws CommandSyntaxException {
        String newName = getString(context, "title");
        UUID artifactID = getArtifact(context, "artifact");
        String name = VoteListHandler.getArtifactName(artifactID);
        IFormattableTextComponent artifactText = toArtifactText(artifactID);
        if (!name.equals(newName)) {
            VoteListHandler.putArtifactName(context.getSource(), artifactID, newName);
            IFormattableTextComponent artifactTextNew = toArtifactText(artifactID);
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.modify.success",
                    artifactText.mergeStyle(TextFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_NAME.create(artifactText);
    }

    private static int modifyArtifactAlias(CommandContext<CommandSource> context) throws CommandSyntaxException {
        String newAlias = getAlias(context, "alias");
        UUID artifactID = getArtifact(context, "artifact");
        IFormattableTextComponent artifactText = toArtifactText(artifactID);
        Optional<UUID> conflict = VoteListHandler.getArtifactByAlias(newAlias);
        if (!conflict.isPresent()) {
            VoteListHandler.putArtifactAlias(context.getSource(), artifactID, newAlias);
            IFormattableTextComponent artifactTextNew = toArtifactText(artifactID);
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.modify.success",
                    artifactText.mergeStyle(TextFormatting.STRIKETHROUGH), artifactTextNew), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(conflict.get()));
    }

    private static int modifyArtifactUnAlias(CommandContext<CommandSource> context) {
        UUID artifactID = getArtifact(context, "artifact");
        IFormattableTextComponent artifactText = toArtifactText(artifactID);
        VoteListHandler.putArtifactAlias(context.getSource(), artifactID, "");
        IFormattableTextComponent artifactTextNew = toArtifactText(artifactID);
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.modify.success",
                artifactText.mergeStyle(TextFormatting.STRIKETHROUGH), artifactTextNew), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int switchOnVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchOffVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int switchUnsetVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), false);
    }

    private static int adminSwitchOnVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOn(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchOffVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchOff(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminSwitchUnsetVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(location);
        VoteCategory category = categoryOptional.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location));
        return processSwitchUnset(context, Pair.of(location, category), getArtifact(context, "artifact"), true);
    }

    private static int adminClearVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = getArtifact(context, "artifact");
        ResourceLocation location = getResourceLocation(context, "category");
        Optional<VoteCategory> result = VoteCategoryHandler.getCategory(location);
        Pair<ResourceLocation, VoteCategory> category = Pair.of(location, result.orElseThrow(() -> CATEGORY_NOT_FOUND.create(location)));
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        handler.getEntry(id).orElseThrow(IllegalStateException::new).votes.clear();
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.clear.success",
                toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminMergeVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactIDFrom = getArtifact(context, "artifact-from"), artifactID = getArtifact(context, "artifact");
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        for (ResourceLocation location : VoteCategoryHandler.getIds()) {
            VoteListEntry entryFrom = handler.getEntry(handler.getIdOrCreate(artifactIDFrom, location)).orElseThrow(IllegalStateException::new);
            VoteListEntry entry = handler.getEntry(handler.getIdOrCreate(artifactID, location)).orElseThrow(IllegalStateException::new);
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.merge.success",
                    entry.votes.merge(entryFrom.votes), toCategoryText(location), toArtifactText(artifactIDFrom), toArtifactText(artifactID)), true);
        }
        if (!Objects.equals(artifactIDFrom, artifactID)) {
            VoteListHandler.getAllCommentsFor(handler, artifactIDFrom).forEach((uuid, commentsFrom) -> {
                ImmutableList<String> comments = VoteListHandler.getCommentFor(handler, artifactID, uuid);
                VoteListHandler.putCommentFor(handler, artifactID, uuid, ImmutableList.<String>builder().addAll(comments).addAll(commentsFrom).build());
            });
        }
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.merge.success.comments",
                toArtifactText(artifactIDFrom), toArtifactText(artifactID)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminRemoveArtifact(CommandContext<CommandSource> context) {
        UUID artifactID = getArtifact(context, "artifact");
        IFormattableTextComponent artifactText = toArtifactText(artifactID);
        VoteListHandler.putArtifactName(context.getSource(), artifactID, "");
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.admin.remove.success",
                artifactText.mergeStyle(TextFormatting.STRIKETHROUGH)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifact(CommandContext<CommandSource> context) {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        VoteListHandler.putArtifactName(context.getSource(), newArtifactID, newName);
        IFormattableTextComponent artifactText = toArtifactText(newArtifactID);
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.admin.create.success", artifactText), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int adminCreateArtifactWithAlias(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID newArtifactID = UUID.randomUUID();
        String newName = getString(context, "title");
        String newAlias = getAlias(context, "alias");
        Optional<UUID> conflict = VoteListHandler.getArtifactByAlias(newAlias);
        if (!conflict.isPresent()) {
            VoteListHandler.putArtifactName(context.getSource(), newArtifactID, newName);
            VoteListHandler.putArtifactAlias(context.getSource(), newArtifactID, newAlias);
            context.getSource().sendFeedback(new TranslationTextComponent(
                    "commands.voteme.admin.create.success", toArtifactText(newArtifactID)), true);

            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_SAME_ALIAS.create(newAlias, toArtifactText(conflict.get()));
    }

    private static int processSwitchOn(CommandContext<CommandSource> context,
                                       Pair<ResourceLocation, VoteCategory> category,
                                       UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> !e.votes.getEnabled().orElse(false));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().votes.setEnabled(true);
                context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.switch.on",
                        toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_ENABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactID));
    }

    private static int processSwitchOff(CommandContext<CommandSource> context,
                                        Pair<ResourceLocation, VoteCategory> category,
                                        UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.getEnabled().orElse(true));
        if (entryOptional.isPresent()) {
            if (category.getValue().enabledModifiable || force) {
                entryOptional.get().votes.setEnabled(false);
                context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.switch.off",
                        toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
                return Command.SINGLE_SUCCESS;
            }
            throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
        }
        throw VOTE_DISABLED.create(toCategoryText(category.getKey()), toArtifactText(artifactID));
    }

    private static int processSwitchUnset(CommandContext<CommandSource> context,
                                          Pair<ResourceLocation, VoteCategory> category,
                                          UUID artifactID, boolean force) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        int id = handler.getIdOrCreate(artifactID, category.getKey());
        if (category.getValue().enabledModifiable || force) {
            handler.getEntry(id).orElseThrow(NullPointerException::new).votes.unsetEnabled();
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.switch.unset",
                    toCategoryText(category.getKey()), toArtifactText(artifactID)), true);
            return Command.SINGLE_SUCCESS;
        }
        throw VOTE_UNMODIFIABLE.create(toCategoryText(category.getKey()));
    }

    private static void processGiveItemToPlayer(ServerPlayerEntity player, ItemStack item) {
        boolean succeed = player.inventory.addItemStackToInventory(item);
        if (!succeed) {
            ItemEntity itemEntity = player.dropItem(item, false);
            if (itemEntity != null) {
                itemEntity.setNoPickupDelay();
                itemEntity.setOwnerId(player.getUniqueID());
            }
        }
    }

    private static IFormattableTextComponent toRoleText(ResourceLocation input) {
        return VoteRoleHandler.getText(input).mergeStyle(TextFormatting.LIGHT_PURPLE);
    }

    private static IFormattableTextComponent toCategoryText(ResourceLocation input) {
        return VoteCategoryHandler.getText(input).mergeStyle(TextFormatting.YELLOW);
    }

    private static IFormattableTextComponent toArtifactText(UUID input) {
        return VoteListHandler.getArtifactText(input).mergeStyle(TextFormatting.GREEN);
    }
}
