package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JSONUtils;

public final class VoteCategory {
    public final String name;
    public final String description;
    public final float weight;

    public VoteCategory(String name, String description, float weight) {
        this.name = name;
        this.description = description;
        this.weight = weight;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        String name = JSONUtils.getString(jsonObject, "name");
        String description = JSONUtils.getString(jsonObject, "description");
        float weight = JSONUtils.getFloat(jsonObject, "weight");
        return new VoteCategory(name, description, weight);
    }

    public void toJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        jsonObject.addProperty("name", this.name);
        jsonObject.addProperty("description", this.description);
        jsonObject.addProperty("weight", this.weight);
    }
}
