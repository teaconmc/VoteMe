package org.teacon.voteme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import org.teacon.voteme.vote.VoteListHandler;

import java.util.Collection;
import java.util.Collections;

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
        int size = VoteListHandler.trimValidAlias(remaining);
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
}
