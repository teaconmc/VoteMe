package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.JSONUtils;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteCategory {
    public final String name;
    public final String description;

    public VoteCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        String name = JSONUtils.getString(jsonObject, "name");
        String description = JSONUtils.getString(jsonObject, "description", name);
        return new VoteCategory(name, description);
    }

    public void toJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        jsonObject.addProperty("name", this.name);
        jsonObject.addProperty("description", this.description);
    }
}
