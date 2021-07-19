package org.teacon.voteme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import org.teacon.voteme.vote.VoteListHandler;

import java.util.Collection;
import java.util.Collections;

public final class AliasArgumentType implements ArgumentType<String> {

    public static AliasArgumentType alias() {
        return new AliasArgumentType();
    }

    public static String getAlias(CommandContext<CommandSource> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        String remaining = reader.getRemaining();
        int size = VoteListHandler.trimValidAlias(remaining);
        reader.setCursor(start + size);
        return remaining.substring(0, size);
    }

    @Override
    public Collection<String> getExamples() {
        return Collections.singleton("#alias");
    }
}
