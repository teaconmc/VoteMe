package org.teacon.voteme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Collections;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public final class AliasArgumentType implements ArgumentType<String> {

    public static AliasArgumentType alias() {
        return new AliasArgumentType();
    }

    public static String getAlias(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String remaining = reader.getRemaining();
        int size = VoteArtifactNames.trimValidAlias(remaining);
        if (size > 0) {
            reader.setCursor(start + size);
            return remaining.substring(0, size);
        }
        throw VoteMeCommand.ALIAS_INVALID.create();
    }

    @Override
    public Collection<String> getExamples() {
        return Collections.singleton("#alias");
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.COMMAND_ARGUMENT_TYPE, ResourceLocation.parse("voteme:alias"), () ->
                ArgumentTypeInfos.registerByClass(AliasArgumentType.class, SingletonArgumentInfo.contextFree(AliasArgumentType::alias)));
    }
}
