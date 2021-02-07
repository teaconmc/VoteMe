package org.teacon.voteme.category;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import org.teacon.voteme.http.VoteMeHttpServer;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteCategory {
    public final ITextComponent name;
    public final ITextComponent description;
    public final boolean enabledDefault, enabledModifiable;

    public VoteCategory(ITextComponent name, ITextComponent description, boolean enabledDefault, boolean enabledModifiable) {
        this.name = name;
        this.description = description;
        this.enabledDefault = enabledDefault;
        this.enabledModifiable = enabledModifiable;
    }

    public static VoteCategory fromJson(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        JsonObject enabled = JSONUtils.getJsonObject(root, "enabled");
        boolean enabledDefault = JSONUtils.getBoolean(enabled, "default");
        boolean enabledModifiable = JSONUtils.getBoolean(enabled, "modifiable");
        ITextComponent name = ITextComponent.Serializer.getComponentFromJson(root.get("name"));
        ITextComponent desc = ITextComponent.Serializer.getComponentFromJson(root.get("description"));
        if (name == null || desc == null) {
            throw new JsonSyntaxException("Both name and description are expected");
        }
        return new VoteCategory(name, desc, enabledDefault, enabledModifiable);
    }

    public JsonElement toHTTPJson(ResourceLocation categoryID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", categoryID.toString());
        jsonObject.addProperty("name", this.name.getString());
        jsonObject.addProperty("description", this.description.getString());
        VoteListHandler voteListHandler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
        jsonObject.add("vote_lists", Util.make(new JsonArray(), array -> {
            for (UUID artifactID : voteListHandler.getArtifacts()) {
                int id = voteListHandler.getIdOrCreate(artifactID, categoryID);
                Optional<VoteListEntry> entryOptional = voteListHandler.getEntry(id);
                entryOptional.filter(e -> e.votes.isEnabled()).ifPresent(e -> array.add(id));
            }
        }));
        return jsonObject;
    }
}
