package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JSONUtils;

public final class VoteCategory {
    public final String name;
    public final String description;
    public final int truncation;

    public VoteCategory(String name, String description, int truncation) {
        this.name = name;
        this.description = description;
        this.truncation = truncation;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        String name = JSONUtils.getString(jsonObject, "name");
        String description = JSONUtils.getString(jsonObject, "description", name);
        int truncation = JSONUtils.getInt(jsonObject, "truncation", 0);
        return new VoteCategory(name, description, truncation);
    }

    public void toJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        jsonObject.addProperty("name", this.name);
        jsonObject.addProperty("description", this.description);
        jsonObject.addProperty("truncation", this.truncation);
    }
}
