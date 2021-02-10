package org.teacon.voteme.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.command.arguments.UUIDArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteMeCommand {
    public static final DynamicCommandExceptionType ARTIFACT_NOT_FOUND = new DynamicCommandExceptionType(a -> new TranslationTextComponent("argument.voteme.artifact.notfound", a));
    public static final DynamicCommandExceptionType ARTIFACT_SAME_NAME = new DynamicCommandExceptionType(a -> new TranslationTextComponent("argument.voteme.artifact.samename", a));
    public static final DynamicCommandExceptionType CATEGORY_NOT_FOUND = new DynamicCommandExceptionType(c -> new TranslationTextComponent("argument.voteme.category.notfound", c));
    public static final Dynamic2CommandExceptionType VOTE_DISABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslationTextComponent("argument.voteme.vote_list.disabled", c, a));
    public static final Dynamic2CommandExceptionType VOTE_ENABLED = new Dynamic2CommandExceptionType((c, a) -> new TranslationTextComponent("argument.voteme.vote_list.enabled", c, a));
    public static final DynamicCommandExceptionType VOTE_UNMODIFIABLE = new DynamicCommandExceptionType(c -> new TranslationTextComponent("argument.voteme.vote_list.unmodifiable", c));

    public static final SuggestionProvider<CommandSource> STAR_SUGGESTION = (c, b) -> ISuggestionProvider.suggest(IntStream.rangeClosed(1, 5).mapToObj(Integer::toString), b);
    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION_ENABLED = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(VoteListHandler.get(c.getSource().getServer())::hasEnabled).map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSource> CATEGORY_SUGGESTION_MODIFIABLE = (c, b) -> ISuggestionProvider.suggest(VoteCategoryHandler.getIds().stream().filter(id -> VoteCategoryHandler.getCategory(id).filter(e -> e.enabledModifiable).isPresent()).map(ResourceLocation::toString), b);
    public static final SuggestionProvider<CommandSource> ARTIFACT_SUGGESTION = (c, b) -> ISuggestionProvider.suggest(VoteListHandler.get(c.getSource().getServer()).getArtifacts().stream().map(UUID::toString), b);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // register permission nodes
        PermissionAPI.registerNode("voteme.admin", DefaultPermissionLevel.NONE, "Admin operations");
        PermissionAPI.registerNode("voteme.disable", DefaultPermissionLevel.OP, "Disabling votes");
        PermissionAPI.registerNode("voteme.enable", DefaultPermissionLevel.OP, "Enabling votes");
        PermissionAPI.registerNode("voteme.set", DefaultPermissionLevel.OP, "Setting votes for artifacts");
        PermissionAPI.registerNode("voteme.unset", DefaultPermissionLevel.OP, "Unsetting votes for artifacts");
        PermissionAPI.registerNode("voteme.clear", DefaultPermissionLevel.OP, "Listing thing related to votes");
        PermissionAPI.registerNode("voteme.select", DefaultPermissionLevel.OP, "Select a particular artifact");
        PermissionAPI.registerNode("voteme.list", DefaultPermissionLevel.OP, "Listing thing related to votes");
        // register child commands
        event.getDispatcher().register(literal("voteme")
                .then(literal("admin")
                        .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.create", "voteme.admin.remove", "voteme.admin.rename", "voteme.admin.disable", "voteme.admin.enable"))
                        .then(literal("create")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.create"))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(VoteMeCommand::adminCreateArtifact)))
                        .then(literal("remove")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.remove").and(VoteMeSelections::hasSelection))
                                .executes(VoteMeCommand::adminRemoveArtifact))
                        .then(literal("rename")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.rename").and(VoteMeSelections::hasSelection))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(VoteMeCommand::adminRenameArtifact)))
                        .then(literal("disable")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.disable").and(VoteMeSelections::hasSelection))
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION)
                                        .executes(VoteMeCommand::adminDisableVotes)))
                        .then(literal("enable")
                                .requires(permission(3, "voteme", "voteme.admin", "voteme.admin.enable").and(VoteMeSelections::hasSelection))
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION)
                                        .executes(VoteMeCommand::adminEnableVotes))))
                .then(literal("disable")
                        .requires(permission(3, "voteme", "voteme.disable", "voteme.admin", "voteme.admin.disable").and(VoteMeSelections::hasSelection))
                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                .executes(VoteMeCommand::disableVotes)))
                .then(literal("enable")
                        .requires(permission(3, "voteme", "voteme.enable", "voteme.admin", "voteme.admin.enable").and(VoteMeSelections::hasSelection))
                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                .executes(VoteMeCommand::enableVotes)))
                .then(literal("set")
                        .requires(permission(3, "voteme", "voteme.set").and(VoteMeSelections::hasSelection))
                        .then(argument("targets", EntityArgument.players())
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_ENABLED)
                                        .then(argument("stars", IntegerArgumentType.integer(1, 5))
                                                .suggests(STAR_SUGGESTION)
                                                .executes(VoteMeCommand::setVotes)))))
                .then(literal("unset")
                        .requires(permission(3, "voteme", "voteme.unset").and(VoteMeSelections::hasSelection))
                        .then(argument("targets", EntityArgument.players())
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_ENABLED)
                                        .executes(VoteMeCommand::unsetVotes))))
                .then(literal("clear")
                        .requires(permission(3, "voteme", "voteme.clear").and(VoteMeSelections::hasSelection))
                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                .executes(VoteMeCommand::clearVotes)))
                .then(literal("select")
                        .requires(permission(2, "voteme", "voteme.select"))
                        .then(argument("artifact", UUIDArgument.func_239194_a_())
                                .suggests(ARTIFACT_SUGGESTION)
                                .executes(VoteMeCommand::selectArtifact)))
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

    private static int listRoles(CommandContext<CommandSource> context) throws CommandSyntaxException {
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

    private static int listCategories(CommandContext<CommandSource> context) throws CommandSyntaxException {
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

    private static int listArtifacts(CommandContext<CommandSource> context) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        VoteListHandler handler = VoteListHandler.get(source.getServer());
        Collection<? extends UUID> artifactIDs = handler.getArtifacts();
        int size = artifactIDs.size();
        if (size > 0) {
            source.sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.success", size,
                    TextComponentUtils.func_240649_b_(artifactIDs, id -> toArtifactText(id, handler, source))), false);
        } else {
            source.sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.none"), false);
        }
        return size;
    }

    private static int selectArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = UUIDArgument.func_239195_a_(context, "artifact");
        MinecraftServer server = context.getSource().getServer();
        VoteListHandler handler = VoteListHandler.get(server);
        if (!handler.getArtifactName(artifactID).isEmpty()) {
            VoteMeSelections.setSelection(context.getSource(), artifactID);
            context.getSource().sendFeedback(new TranslationTextComponent(
                    "commands.voteme.select.success", toArtifactText(artifactID, handler)), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_NOT_FOUND.create(artifactID);
    }

    private static int clearVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = VoteMeSelections.getSelection(context.getSource());
        ResourceLocation categoryID = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = toArtifactText(artifactID, handler);
                IFormattableTextComponent categoryText = toCategoryText(categoryID);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    entryOptional.get().votes.clear();
                    context.getSource().sendFeedback(new TranslationTextComponent(
                            "commands.voteme.clear.success", categoryText, artifactText), true);
                    return Command.SINGLE_SUCCESS;
                }
                throw VOTE_DISABLED.create(categoryText, artifactText);
            }
            throw ARTIFACT_NOT_FOUND.create(artifactID);
        }
        throw CATEGORY_NOT_FOUND.create(categoryID);
    }

    private static int unsetVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = VoteMeSelections.getSelection(context.getSource());
        Collection<ServerPlayerEntity> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation categoryID = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = toArtifactText(artifactID, handler);
                IFormattableTextComponent categoryText = toCategoryText(categoryID);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    int result = targets.size();
                    targets.forEach(player -> entryOptional.get().votes.set(player, 0));
                    context.getSource().sendFeedback(new TranslationTextComponent(
                            "commands.voteme.unset.success", result, categoryText, artifactText), true);
                    return result;
                }
                throw VOTE_DISABLED.create(categoryText, artifactText);
            }
            throw ARTIFACT_NOT_FOUND.create(artifactID);
        }
        throw CATEGORY_NOT_FOUND.create(categoryID);
    }

    private static int setVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        int stars = IntegerArgumentType.getInteger(context, "stars");
        UUID artifact = VoteMeSelections.getSelection(context.getSource());
        Collection<ServerPlayerEntity> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(category).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifact).isEmpty()) {
                int id = handler.getIdOrCreate(artifact, category);
                IFormattableTextComponent artifactText = toArtifactText(artifact, handler);
                IFormattableTextComponent categoryText = toCategoryText(category);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    int result = targets.size();
                    targets.forEach(player -> entryOptional.get().votes.set(player, stars));
                    context.getSource().sendFeedback(new TranslationTextComponent(
                            "commands.voteme.set.success", result, stars, categoryText, artifactText), true);
                    return result;
                }
                throw VOTE_DISABLED.create(toCategoryText(category), toArtifactText(artifact, handler));
            }
            throw ARTIFACT_NOT_FOUND.create(artifact);
        }
        throw CATEGORY_NOT_FOUND.create(category);
    }

    private static int enableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = VoteMeSelections.getSelection(context.getSource());
        return processEnabled(context, category, artifact, false, true);
    }

    private static int disableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = VoteMeSelections.getSelection(context.getSource());
        return processEnabled(context, category, artifact, false, false);
    }

    private static int adminEnableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = VoteMeSelections.getSelection(context.getSource());
        return processEnabled(context, category, artifact, true, true);
    }

    private static int adminDisableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = VoteMeSelections.getSelection(context.getSource());
        return processEnabled(context, category, artifact, true, false);
    }

    private static int adminRenameArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = VoteMeSelections.getSelection(context.getSource());
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        String newName = StringArgumentType.getString(context, "name");
        String name = handler.getArtifactName(artifactID);
        if (!name.isEmpty()) {
            IFormattableTextComponent artifactText = toArtifactText(artifactID, handler);
            if (!name.equals(newName)) {
                handler.putArtifactName(artifactID, newName);
                IFormattableTextComponent artifactTextNew = toArtifactText(artifactID, handler);
                context.getSource().sendFeedback(new TranslationTextComponent(
                        "commands.voteme.admin.rename.success", artifactText.mergeStyle(TextFormatting.STRIKETHROUGH), artifactTextNew), true);
                return Command.SINGLE_SUCCESS;
            }
            throw ARTIFACT_SAME_NAME.create(artifactText);
        }
        throw ARTIFACT_NOT_FOUND.create(artifactID);
    }

    private static int adminRemoveArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = VoteMeSelections.getSelection(context.getSource());
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        String oldName = handler.getArtifactName(artifactID);
        if (!oldName.isEmpty()) {
            IFormattableTextComponent artifactText = toArtifactText(artifactID, handler).mergeStyle(TextFormatting.STRIKETHROUGH);
            handler.putArtifactName(artifactID, "");
            VoteMeSelections.delSelection(context.getSource());
            context.getSource().sendFeedback(new TranslationTextComponent(
                    "commands.voteme.admin.remove.success", artifactText), true);
            return Command.SINGLE_SUCCESS;
        }
        throw ARTIFACT_NOT_FOUND.create(artifactID);
    }

    private static int adminCreateArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID newArtifactID = UUID.randomUUID();
        String newName = StringArgumentType.getString(context, "name");
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());

        handler.putArtifactName(newArtifactID, newName);
        VoteMeSelections.setSelection(context.getSource(), newArtifactID);
        IFormattableTextComponent artifactText = toArtifactText(newArtifactID, handler);
        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.admin.create.success", artifactText), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int processEnabled(CommandContext<CommandSource> context, ResourceLocation categoryID,
                                      UUID artifactID, boolean force, boolean enabled) throws CommandSyntaxException {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
        if (categoryOptional.isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = toArtifactText(artifactID, handler);
                IFormattableTextComponent categoryText = toCategoryText(categoryID);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> enabled != e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    if (categoryOptional.get().enabledModifiable || force) {
                        entryOptional.get().votes.setEnabled(enabled);
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme." +
                                (enabled ? "enable.success" : "disable.success"), categoryText, artifactText), true);
                        return Command.SINGLE_SUCCESS;
                    }
                    throw VOTE_UNMODIFIABLE.create(categoryText);
                }
                throw (enabled ? VOTE_ENABLED : VOTE_DISABLED).create(categoryText, artifactText);
            }
            throw ARTIFACT_NOT_FOUND.create(artifactID);
        }
        throw CATEGORY_NOT_FOUND.create(categoryID);
    }

    private static IFormattableTextComponent toRoleText(ResourceLocation input) {
        return VoteRoleHandler.getText(input).mergeStyle(TextFormatting.LIGHT_PURPLE);
    }

    private static IFormattableTextComponent toCategoryText(ResourceLocation input) {
        return VoteCategoryHandler.getText(input).mergeStyle(TextFormatting.YELLOW);
    }

    private static IFormattableTextComponent toArtifactText(UUID input, VoteListHandler handler) {
        return handler.getArtifactText(input).mergeStyle(TextFormatting.GREEN);
    }

    private static IFormattableTextComponent toArtifactText(UUID input, VoteListHandler handler, CommandSource source) {
        if (VoteMeSelections.hasSelection(source) && input.equals(VoteMeSelections.getSelection(source))) {
            return new TranslationTextComponent("commands.voteme.list.artifacts.selection", toArtifactText(input, handler));
        } else {
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/voteme select " + input);
            return toArtifactText(input, handler).modifyStyle(style -> style.setClickEvent(clickEvent));
        }
    }
}
