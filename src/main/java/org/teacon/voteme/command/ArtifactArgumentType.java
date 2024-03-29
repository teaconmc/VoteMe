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
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.teacon.voteme.vote.VoteArtifactNames;

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
        int size = VoteArtifactNames.trimValidAlias(remaining);
        if (size > 0) {
            reader.setCursor(start + size);
            String alias = remaining.substring(0, size);
            Optional<UUID> result = VoteArtifactNames.effective().flatMap(a -> a.getByAlias(alias));
            return result.orElseThrow(() -> VoteMeCommand.ARTIFACT_NOT_FOUND.create(alias));
        }
        Matcher matcher = UUID_PATTERN.matcher(remaining);
        if (matcher.find()) {
            String uuidString = matcher.group(1);
            reader.setCursor(start + uuidString.length());
            UUID uuid = parse(uuidString);
            if (VoteArtifactNames.effective().filter(a -> !a.getName(uuid).isEmpty()).isEmpty()) {
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
        return SharedSuggestionProvider.suggest(VoteArtifactNames.effective().stream().flatMap(a -> Stream
                .concat(a.getAliases().stream(), a.getUUIDs().stream().map(UUID::toString))), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return Collections.singleton("#alias");
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.COMMAND_ARGUMENT_TYPES.getRegistryKey(), new ResourceLocation("voteme:artifact"), () ->
                ArgumentTypeInfos.registerByClass(ArtifactArgumentType.class, SingletonArgumentInfo.contextFree(ArtifactArgumentType::artifact)));
    }
}
