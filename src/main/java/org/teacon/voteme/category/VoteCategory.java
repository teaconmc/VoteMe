package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

import javax.annotation.ParametersAreNonnullByDefault;

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

    public static VoteCategory fromJson(JsonElement json, HolderLookup.Provider registries) {
        JsonObject root = json.getAsJsonObject();
        JsonObject enabled = GsonHelper.getAsJsonObject(root, "enabled");
        boolean enabledDefault = GsonHelper.getAsBoolean(enabled, "default");
        boolean enabledModifiable = GsonHelper.getAsBoolean(enabled, "modifiable");
        Component name = Component.Serializer.fromJson(root.get("name"), registries);
        Component desc = Component.Serializer.fromJson(root.get("description"), registries);
        if (name == null || desc == null) {
            throw new JsonSyntaxException("Both name and description are expected");
        }
        return new VoteCategory(name, desc, enabledDefault, enabledModifiable);
    }
}
