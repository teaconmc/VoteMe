package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteCategory {

    public static final StreamCodec<RegistryFriendlyByteBuf, VoteCategory> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, c -> c.name,
            ComponentSerialization.STREAM_CODEC, c -> c.description,
            ByteBufCodecs.BOOL, c -> c.enabledDefault,
            ByteBufCodecs.BOOL, c -> c.enabledModifiable,
            VoteCategory::new
    );

    public final Component name;
    public final Component description;
    public final boolean enabledDefault, enabledModifiable;

    public VoteCategory(Component name, Component description, boolean enabledDefault, boolean enabledModifiable) {
        this.name = name;
        this.description = description;
        this.enabledDefault = enabledDefault;
        this.enabledModifiable = enabledModifiable;
    }

    public static VoteCategory fromJson(@Nullable JsonElement json) {
        JsonObject root = Objects.requireNonNullElse(json, JsonNull.INSTANCE).getAsJsonObject();
        JsonObject enabled = GsonHelper.getAsJsonObject(root, "enabled");
        boolean enabledDefault = GsonHelper.getAsBoolean(enabled, "default");
        boolean enabledModifiable = GsonHelper.getAsBoolean(enabled, "modifiable");
        Codec<Component> componentCodec = ComponentSerialization.CODEC;
        Optional<Component> name = componentCodec.parse(JsonOps.INSTANCE, root.get("name")).result();
        Optional<Component> desc = componentCodec.parse(JsonOps.INSTANCE, root.get("description")).result();
        if (name.isEmpty() || desc.isEmpty()) {
            throw new JsonSyntaxException("Both name and description are expected");
        }
        return new VoteCategory(name.get(), desc.get(), enabledDefault, enabledModifiable);
    }
}
