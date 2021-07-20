package org.teacon.voteme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import org.teacon.voteme.vote.VoteListHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ArtifactArgumentType implements ArgumentType<UUID> {
    private static final Pattern UUID_PATTERN = Pattern.compile("^([-0-9a-fA-F]+)");

    public static ArtifactArgumentType artifact() {
        return new ArtifactArgumentType();
    }

    public static UUID getArtifact(CommandContext<CommandSource> context, String name) {
        return context.getArgument(name, UUID.class);
    }

    @Override
    public UUID parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String remaining = reader.getRemaining();
        int size = VoteListHandler.trimValidAlias(remaining);
        if (size > 0) {
            reader.setCursor(start + size);
            String alias = remaining.substring(0, size);
            Optional<UUID> result = VoteListHandler.getArtifactByAlias(alias);
            return result.orElseThrow(() -> VoteMeCommand.ARTIFACT_NOT_FOUND.create(alias));
        }
        Matcher matcher = UUID_PATTERN.matcher(remaining);
        if (matcher.find()) {
            String uuidString = matcher.group(1);
            reader.setCursor(start + uuidString.length());
            UUID uuid = parse(uuidString);
            if (VoteListHandler.getArtifactName(uuid).isEmpty()) {
                throw VoteMeCommand.ARTIFACT_NOT_FOUND.create(uuid);
            }
            return uuid;
        }
        throw VoteMeCommand.ARTIFACT_INVALID.create();
    }

    private UUID parse(String uuidString) throws CommandSyntaxException {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw VoteMeCommand.ARTIFACT_INVALID.create();
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ISuggestionProvider.suggest(Stream.concat(
                VoteListHandler.getArtifactAliases().stream(),
                VoteListHandler.getArtifacts().stream().map(UUID::toString)), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return Collections.singleton("#alias");
    }
}