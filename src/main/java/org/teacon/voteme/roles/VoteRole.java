package org.teacon.voteme.roles;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteRole {
    public final Component name;
    public final EntitySelector selector;
    public final ListMultimap<Identifier, Participation> categories;

    public VoteRole(Component name, EntitySelector selector, Multimap<Identifier, Participation> participations) {
        this.name = name;
        this.selector = selector;
        this.categories = ImmutableListMultimap.copyOf(participations);
    }

    public static VoteRole fromJson(@Nullable Identifier id, @Nullable JsonElement json) {
        JsonObject jsonObject = Objects.requireNonNullElse(json, JsonNull.INSTANCE).getAsJsonObject();
        Codec<Component> componentCodec = ComponentSerialization.CODEC;
        Optional<Component> name = componentCodec.parse(JsonOps.INSTANCE, jsonObject.get("name")).result();
        if (name.isEmpty()) {
            throw new JsonSyntaxException("The name is expected in a role for voting");
        }
        JsonArray participations = GsonHelper.getAsJsonArray(jsonObject, "participations");
        EntitySelector selector = parseSelector(GsonHelper.getAsString(jsonObject, "selector", "@a"));
        return new VoteRole(name.get(), selector, parseParticipations(Objects.requireNonNull(id), participations));
    }

    private static Multimap<Identifier, Participation> parseParticipations(Identifier id, JsonArray array) {
        ImmutableListMultimap.Builder<Identifier, Participation> builder = ImmutableListMultimap.builder();
        for (JsonElement child : array) {
            JsonObject participationObject = GsonHelper.convertToJsonObject(child, "participations");
            Identifier category = Identifier.parse(GsonHelper.getAsString(participationObject, "category"));
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
            return new EntitySelectorParser(reader, true).parse();
        } catch (CommandSyntaxException e) {
            String msg = "Expected selector to be an entity selector, was unknown string '" + str + "'";
            throw new JsonSyntaxException(msg);
        }
    }

    public JsonElement toHTTPJson(Identifier id) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", id.toString());
            result.addProperty("name", name.getString());
        });
    }

    @FieldsAreNonnullByDefault
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
