package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableSortedMap;
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
import java.util.Map;
import java.util.SortedMap;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteRole {
    public final ITextComponent name;
    public final EntitySelector selector;
    public final SortedMap<ResourceLocation, Category> categories;

    public VoteRole(ITextComponent name, EntitySelector selector, SortedMap<ResourceLocation, Category> categories) {
        this.name = name;
        this.selector = selector;
        this.categories = ImmutableSortedMap.copyOf(categories);
    }

    public static VoteRole fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        ITextComponent name = ITextComponent.Serializer.getComponentFromJson(jsonObject.get("name"));
        if (name == null) {
            throw new JsonSyntaxException("The name is expected in a role for voting");
        }
        ImmutableSortedMap.Builder<ResourceLocation, Category> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, JsonElement> category : JSONUtils.getJsonObject(jsonObject, "categories").entrySet()) {
            ResourceLocation resourceLocation = new ResourceLocation(category.getKey());
            JsonObject categoryObject = category.getValue().getAsJsonObject();
            int truncation = JSONUtils.getInt(categoryObject, "truncation", 0);
            float weight = JSONUtils.getFloat(categoryObject, "weight", 1.0F);
            builder.put(resourceLocation, new Category(weight, truncation));
        }
        EntitySelector selector = parseSelector(JSONUtils.getString(jsonObject, "selector", "@a"));
        return new VoteRole(name, selector, builder.build());
    }

    private static EntitySelector parseSelector(String s) {
        try {
            StringReader reader = new StringReader(s);
            return new EntitySelectorParser(reader).parse();
        } catch (CommandSyntaxException e) {
            String msg = "Expected selector to be an entity selector, was unknown string '" + s + "'";
            throw new JsonSyntaxException(msg);
        }
    }

    public JsonElement toHTTPJson(ResourceLocation id) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", id.toString());
            result.addProperty("name", name.getString());
        });
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Category {
        public final float weight;
        public final int truncation;

        public Category(float weight, int truncation) {
            this.weight = weight;
            this.truncation = truncation;
        }
    }
}
