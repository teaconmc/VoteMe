package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.arguments.EntitySelector;
import net.minecraft.command.arguments.EntitySelectorParser;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteRole {
    private static final String SUBGROUP_FALLBACK = "default";

    public final ITextComponent name;
    public final EntitySelector selector;
    public final ListMultimap<ResourceLocation, Participation> categories;

    public VoteRole(ITextComponent name, EntitySelector selector, Multimap<ResourceLocation, Participation> participations) {
        this.name = name;
        this.selector = selector;
        this.categories = ImmutableListMultimap.copyOf(participations);
    }

    public static VoteRole fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        ITextComponent name = parseName(jsonObject.get("name"));
        JsonArray participationsRaw = JSONUtils.getJsonArray(jsonObject, "participations");
        EntitySelector selector = parseSelector(JSONUtils.getString(jsonObject, "selector", "@a"));
        ListMultimap<ResourceLocation, Participation> participations = parseParticipations(participationsRaw);
        return new VoteRole(name, selector, participations);
    }

    private static ListMultimap<ResourceLocation, Participation> parseParticipations(JsonArray array) {
        ImmutableListMultimap.Builder<ResourceLocation, Participation> builder = ImmutableListMultimap.builder();
        for (JsonElement child : array) {
            JsonObject participationObject = JSONUtils.getJsonObject(child, "participations");
            ResourceLocation category = new ResourceLocation(JSONUtils.getString(participationObject, "category"));
            String subgroup = JSONUtils.getString(participationObject, "subgroup", SUBGROUP_FALLBACK);
            int truncation = JSONUtils.getInt(participationObject, "truncation", 0);
            float weight = JSONUtils.getFloat(participationObject, "weight", 1.0F);
            builder.put(category, new Participation(weight, truncation, subgroup));
        }
        return builder.build();
    }

    private static EntitySelector parseSelector(String str) {
        try {
            StringReader reader = new StringReader(str);
            return new EntitySelectorParser(reader).parse();
        } catch (CommandSyntaxException e) {
            String msg = "Expected selector to be an entity selector, was unknown string '" + str + "'";
            throw new JsonSyntaxException(msg);
        }
    }

    private static ITextComponent parseName(JsonElement elem) {
        ITextComponent name = ITextComponent.Serializer.getComponentFromJson(elem);
        if (name == null) {
            throw new JsonSyntaxException("The name is expected in a role for voting");
        }
        return name;
    }

    public JsonElement toHTTPJson(ResourceLocation id) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", id.toString());
            result.addProperty("name", name.getString());
        });
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Participation {
        public final float weight;
        public final int truncation;
        public final String subgroup;

        public Participation(float weight, int truncation, String subgroupString) {
            this.weight = weight;
            this.truncation = truncation;
            this.subgroup = subgroupString;
        }
    }
}
