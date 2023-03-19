package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteCategory {
    public final Component name;
    public final Component description;
    public final boolean enabledDefault, enabledModifiable;

    public VoteCategory(Component name, Component description, boolean enabledDefault, boolean enabledModifiable) {
        this.name = name;
        this.description = description;
        this.enabledDefault = enabledDefault;
        this.enabledModifiable = enabledModifiable;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        JsonObject enabled = GsonHelper.getAsJsonObject(root, "enabled");
        boolean enabledDefault = GsonHelper.getAsBoolean(enabled, "default");
        boolean enabledModifiable = GsonHelper.getAsBoolean(enabled, "modifiable");
        Component name = Component.Serializer.fromJson(root.get("name"));
        Component desc = Component.Serializer.fromJson(root.get("description"));
        if (name == null || desc == null) {
            throw new JsonSyntaxException("Both name and description are expected");
        }
        return new VoteCategory(name, desc, enabledDefault, enabledModifiable);
    }
}
