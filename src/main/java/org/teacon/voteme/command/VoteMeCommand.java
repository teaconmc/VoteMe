package org.teacon.voteme.command;

import com.google.common.base.Preconditions;
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
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
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
import java.util.function.Function;
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
        PermissionAPI.registerNode("voteme.list", DefaultPermissionLevel.OP, "Listing thing related to votes");
        // register child commands
        event.getDispatcher().register(literal("voteme")
                .then(literal("admin")
                        .then(literal("remove")
                                .requires(permission("voteme", "voteme.admin", "voteme.admin.remove"))
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .executes(VoteMeCommand::adminRemoveArtifact)))
                        .then(literal("rename")
                                .requires(permission("voteme", "voteme.admin", "voteme.admin.rename"))
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(VoteMeCommand::adminRenameArtifact))))
                        .then(literal("disable")
                                .requires(permission("voteme", "voteme.admin", "voteme.admin.disable"))
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .executes(VoteMeCommand::adminDisableVotes))))
                        .then(literal("enable")
                                .requires(permission("voteme", "voteme.admin", "voteme.admin.enable"))
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION)
                                                .executes(VoteMeCommand::adminEnableVotes)))))
                .then(literal("disable")
                        .requires(permission("voteme", "voteme.disable", "voteme.admin", "voteme.admin.disable"))
                        .then(argument("artifact", UUIDArgument.func_239194_a_())
                                .suggests(ARTIFACT_SUGGESTION)
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                        .executes(VoteMeCommand::disableVotes))))
                .then(literal("enable")
                        .requires(permission("voteme", "voteme.enable", "voteme.admin", "voteme.admin.enable"))
                        .then(argument("artifact", UUIDArgument.func_239194_a_())
                                .suggests(ARTIFACT_SUGGESTION)
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_MODIFIABLE)
                                        .executes(VoteMeCommand::enableVotes))))
                .then(literal("set")
                        .requires(permission("voteme", "voteme.set"))
                        .then(argument("targets", EntityArgument.players())
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                                .then(argument("stars", IntegerArgumentType.integer(1, 5))
                                                        .suggests(STAR_SUGGESTION)
                                                        .executes(VoteMeCommand::setVotes))))))
                .then(literal("unset")
                        .requires(permission("voteme", "voteme.unset"))
                        .then(argument("targets", EntityArgument.players())
                                .then(argument("artifact", UUIDArgument.func_239194_a_())
                                        .suggests(ARTIFACT_SUGGESTION)
                                        .then(argument("category", ResourceLocationArgument.resourceLocation())
                                                .suggests(CATEGORY_SUGGESTION_ENABLED)
                                                .executes(VoteMeCommand::unsetVotes)))))
                .then(literal("clear")
                        .requires(permission("voteme", "voteme.clear"))
                        .then(argument("artifact", UUIDArgument.func_239194_a_())
                                .suggests(ARTIFACT_SUGGESTION)
                                .then(argument("category", ResourceLocationArgument.resourceLocation())
                                        .suggests(CATEGORY_SUGGESTION_ENABLED)
                                        .executes(VoteMeCommand::clearVotes))))
                .then(literal("list")
                        .then(literal("artifacts")
                                .requires(permission("voteme", "voteme.list", "voteme.list.artifacts"))
                                .executes(VoteMeCommand::listArtifacts))
                        .then(literal("categories")
                                .requires(permission("voteme", "voteme.list", "voteme.list.categories"))
                                .executes(VoteMeCommand::listCategories))
                        .then(literal("roles")
                                .requires(permission("voteme", "voteme.list", "voteme.list.roles"))
                                .executes(VoteMeCommand::listRoles))));
    }

    private static Predicate<CommandSource> permission(String... permissionNodes) {
        Preconditions.checkArgument(permissionNodes.length > 0, "permission nodes should not be empty");
        return source -> {
            if (source.getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
                return Arrays.stream(permissionNodes).anyMatch(n -> PermissionAPI.hasPermission(player, n));
            }
            return source.hasPermissionLevel(3);
        };
    }

    private static int listRoles(CommandContext<CommandSource> context) throws CommandSyntaxException {
        Collection<? extends ResourceLocation> roles = VoteRoleHandler.getIds();
        int size = roles.size();
        if (size > 0) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.roles.success",
                    size, TextComponentUtils.func_240649_b_(roles, wrapGreen(VoteRoleHandler::getText))), false);
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
                    size, TextComponentUtils.func_240649_b_(categories, wrapGreen(VoteCategoryHandler::getText))), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.categories.none"), false);
        }
        return size;
    }

    private static int listArtifacts(CommandContext<CommandSource> context) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        Collection<? extends UUID> artifacts = handler.getArtifacts();
        int size = artifacts.size();
        if (size > 0) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.success",
                    size, TextComponentUtils.func_240649_b_(artifacts, wrapGreen(handler::getArtifactText))), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.artifacts.none"), false);
        }
        return size;
    }

    private static int clearVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = UUIDArgument.func_239195_a_(context, "artifact");
        ResourceLocation categoryID = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
                IFormattableTextComponent categoryText = VoteCategoryHandler.getText(categoryID);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    entryOptional.get().votes.clear();
                    context.getSource().sendFeedback(new TranslationTextComponent(
                            "commands.voteme.clear.success", categoryText, artifactText), true);
                    return 1;
                }
                throw VOTE_DISABLED.create(categoryText, artifactText);
            }
            throw ARTIFACT_NOT_FOUND.create(artifactID);
        }
        throw CATEGORY_NOT_FOUND.create(categoryID);
    }

    private static int unsetVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        UUID artifactID = UUIDArgument.func_239195_a_(context, "artifact");
        Collection<ServerPlayerEntity> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation categoryID = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(categoryID).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
                IFormattableTextComponent categoryText = VoteCategoryHandler.getText(categoryID);
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
        UUID artifact = UUIDArgument.func_239195_a_(context, "artifact");
        Collection<ServerPlayerEntity> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        if (VoteCategoryHandler.getCategory(category).isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifact).isEmpty()) {
                int id = handler.getIdOrCreate(artifact, category);
                IFormattableTextComponent artifactText = handler.getArtifactText(artifact);
                IFormattableTextComponent categoryText = VoteCategoryHandler.getText(category);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    int result = targets.size();
                    targets.forEach(player -> entryOptional.get().votes.set(player, stars));
                    context.getSource().sendFeedback(new TranslationTextComponent(
                            "commands.voteme.set.success", result, stars, categoryText, artifactText), true);
                    return result;
                }
                throw VOTE_DISABLED.create(VoteCategoryHandler.getText(category), handler.getArtifactText(artifact));
            }
            throw ARTIFACT_NOT_FOUND.create(artifact);
        }
        throw CATEGORY_NOT_FOUND.create(category);
    }

    private static int enableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = UUIDArgument.func_239195_a_(context, "artifact");
        return processEnabled(context, category, artifact, false, true);
    }

    private static int disableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = UUIDArgument.func_239195_a_(context, "artifact");
        return processEnabled(context, category, artifact, false, false);
    }

    private static int adminEnableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = UUIDArgument.func_239195_a_(context, "artifact");
        return processEnabled(context, category, artifact, true, true);
    }

    private static int adminDisableVotes(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ResourceLocation category = ResourceLocationArgument.getResourceLocation(context, "category");
        UUID artifact = UUIDArgument.func_239195_a_(context, "artifact");
        return processEnabled(context, category, artifact, true, false);
    }

    private static int adminRenameArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        UUID artifactID = UUIDArgument.func_239195_a_(context, "artifact");
        String newName = StringArgumentType.getString(context, "name");
        String oldName = handler.getArtifactName(artifactID);
        if (!oldName.isEmpty()) {
            if (!oldName.equals(newName)) {
                handler.putArtifactName(artifactID, newName);
                IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
                context.getSource().sendFeedback(new TranslationTextComponent(
                        "commands.voteme.admin.rename.success", oldName, artifactText), true);
                return 1;
            }
            IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
            throw ARTIFACT_SAME_NAME.create(artifactText);
        }
        throw ARTIFACT_NOT_FOUND.create(artifactID);
    }

    private static int adminRemoveArtifact(CommandContext<CommandSource> context) throws CommandSyntaxException {
        VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
        UUID artifactID = UUIDArgument.func_239195_a_(context, "artifact");
        String oldName = handler.getArtifactName(artifactID);
        if (!oldName.isEmpty()) {
            IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
            handler.putArtifactName(artifactID, "");
            context.getSource().sendFeedback(new TranslationTextComponent(
                    "commands.voteme.admin.remove.success", artifactText), true);
            return 1;
        }
        throw ARTIFACT_NOT_FOUND.create(artifactID);
    }

    private static int processEnabled(CommandContext<CommandSource> context, ResourceLocation categoryID,
                                      UUID artifactID, boolean force, boolean enabled) throws CommandSyntaxException {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
        if (categoryOptional.isPresent()) {
            VoteListHandler handler = VoteListHandler.get(context.getSource().getServer());
            if (!handler.getArtifactName(artifactID).isEmpty()) {
                int id = handler.getIdOrCreate(artifactID, categoryID);
                IFormattableTextComponent artifactText = handler.getArtifactText(artifactID);
                IFormattableTextComponent categoryText = VoteCategoryHandler.getText(categoryID);
                Optional<VoteListEntry> entryOptional = handler.getEntry(id).filter(e -> enabled != e.votes.isEnabled());
                if (entryOptional.isPresent()) {
                    if (categoryOptional.get().enabledModifiable || force) {
                        entryOptional.get().votes.setEnabled(enabled);
                        context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme." +
                                (enabled ? "enable.success" : "disable.success"), categoryText, artifactText), true);
                        return 1;
                    }
                    throw VOTE_UNMODIFIABLE.create(categoryText);
                }
                throw (enabled ? VOTE_ENABLED : VOTE_DISABLED).create(categoryText, artifactText);
            }
            throw ARTIFACT_NOT_FOUND.create(artifactID);
        }
        throw CATEGORY_NOT_FOUND.create(categoryID);
    }

    private static <S> Function<S, ITextComponent> wrapGreen(Function<S, IFormattableTextComponent> function) {
        return input -> function.apply(input).mergeStyle(TextFormatting.GREEN);
    }
}
