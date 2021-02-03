package org.teacon.voteme.category;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import org.teacon.voteme.http.VoteMeHttpServer;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

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

    public JsonElement toHTTPJson(ResourceLocation categoryID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", categoryID.toString());
        jsonObject.addProperty("name", this.name.getString());
        jsonObject.addProperty("description", this.description.getString());
        VoteListHandler voteListHandler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
        jsonObject.add("vote_lists", Util.make(new JsonArray(), array -> {
            for (UUID artifactID : voteListHandler.getArtifacts()) {
                array.add(voteListHandler.getIdOrCreate(artifactID, categoryID));
            }
        }));
        return jsonObject;
    }
}
