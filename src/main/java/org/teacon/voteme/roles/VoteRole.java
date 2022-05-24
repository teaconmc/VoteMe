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
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteRole {
    public final Component name;
    public final EntitySelector selector;
    public final ListMultimap<ResourceLocation, Participation> categories;

    public VoteRole(Component name, EntitySelector selector, Multimap<ResourceLocation, Participation> participations) {
        this.name = name;
        this.selector = selector;
        this.categories = ImmutableListMultimap.copyOf(participations);
    }

    public static VoteRole fromJson(ResourceLocation id, JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        Component name = parseName(jsonObject.get("name"));
        JsonArray participationsRaw = GsonHelper.getAsJsonArray(jsonObject, "participations");
        EntitySelector selector = parseSelector(GsonHelper.getAsString(jsonObject, "selector", "@a"));
        Multimap<ResourceLocation, Participation> participations = parseParticipations(id, participationsRaw);
        return new VoteRole(name, selector, participations);
    }

    private static Multimap<ResourceLocation, Participation> parseParticipations(ResourceLocation id, JsonArray array) {
        ImmutableListMultimap.Builder<ResourceLocation, Participation> builder = ImmutableListMultimap.builder();
        for (JsonElement child : array) {
            JsonObject participationObject = GsonHelper.convertToJsonObject(child, "participations");
            ResourceLocation category = new ResourceLocation(GsonHelper.getAsString(participationObject, "category"));
            String subgroup = GsonHelper.getAsString(participationObject, "subgroup", id.toString());
            int truncation = GsonHelper.getAsInt(participationObject, "truncation", 0);
            float weight = GsonHelper.getAsFloat(participationObject, "weight", 1.0F);
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

    private static Component parseName(JsonElement elem) {
        Component name = Component.Serializer.fromJson(elem);
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
