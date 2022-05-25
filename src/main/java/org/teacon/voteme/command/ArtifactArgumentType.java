package org.teacon.voteme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.EmptyArgumentSerializer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArtifactArgumentType implements ArgumentType<UUID> {
    private static final Pattern UUID_PATTERN = Pattern.compile("^([-0-9a-fA-F]+)");

    public static ArtifactArgumentType artifact() {
        return new ArtifactArgumentType();
    }

    public static UUID getArtifact(CommandContext<CommandSourceStack> context, String name) {
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
        return SharedSuggestionProvider.suggest(Stream.concat(
                VoteListHandler.getArtifactAliases().stream(),
                VoteListHandler.getArtifacts().stream().map(UUID::toString)), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return Collections.singleton("#alias");
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        ArgumentSerializer<ArtifactArgumentType> serializer = new EmptyArgumentSerializer<>(ArtifactArgumentType::artifact);
        event.enqueueWork(() -> ArgumentTypes.register("voteme_artifact", ArtifactArgumentType.class, serializer));
    }
}
