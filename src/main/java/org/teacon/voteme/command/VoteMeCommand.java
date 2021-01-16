package org.teacon.voteme.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.command.Commands.literal;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteMeCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("voteme")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("list")
                        .then(literal("roles").executes(VoteMeCommand::listRoles))
                        .then(literal("categories").executes(VoteMeCommand::listCategories))));
    }

    private static int listRoles(CommandContext<CommandSource> context) throws CommandSyntaxException {
        List<ResourceLocation> roles = VoteRoleHandler.getIds().collect(Collectors.toList());
        if (roles.isEmpty()) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.roles.none"), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.roles.success", roles.size(), TextComponentUtils.func_240649_b_(roles, elem -> {
                VoteRole role = VoteRoleHandler.getRole(elem).orElseThrow(NullPointerException::new);
                ITextComponent hover = new StringTextComponent(role.name).appendString("\n");
                return TextComponentUtils.wrapWithSquareBrackets(new StringTextComponent(elem.toString()))
                        .modifyStyle(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
            })), false);
        }
        return roles.size();
    }

    private static int listCategories(CommandContext<CommandSource> context) throws CommandSyntaxException {
        List<ResourceLocation> categories = VoteCategoryHandler.getIds().collect(Collectors.toList());
        if (categories.isEmpty()) {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.categories.none"), false);
        } else {
            context.getSource().sendFeedback(new TranslationTextComponent("commands.voteme.list.categories.success", categories.size(), TextComponentUtils.func_240649_b_(categories, elem -> {
                VoteCategory category = VoteCategoryHandler.getCategory(elem).orElseThrow(NullPointerException::new);
                ITextComponent hover = new StringTextComponent(category.name).appendString("\n").appendString(category.description);
                return TextComponentUtils.wrapWithSquareBrackets(new StringTextComponent(elem.toString()))
                        .modifyStyle(style -> style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
            })), false);
        }
        return categories.size();
    }
}
