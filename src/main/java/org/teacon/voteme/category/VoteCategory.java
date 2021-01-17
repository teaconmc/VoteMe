package org.teacon.voteme.category;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteCategory {
    public final ITextComponent name;
    public final ITextComponent description;

    public VoteCategory(ITextComponent name, ITextComponent description) {
        this.name = name;
        this.description = description;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        ITextComponent name = ITextComponent.Serializer.getComponentFromJson(jsonObject.get("name"));
        ITextComponent desc = ITextComponent.Serializer.getComponentFromJson(jsonObject.get("description"));
        if (name == null || desc == null) {
            throw new JsonSyntaxException("Both name and description are expected");
        }
        return new VoteCategory(name, desc);
    }

    public void toHTTPJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        jsonObject.addProperty("name", this.name.getString());
        jsonObject.addProperty("description", this.description.getString());
    }
}
