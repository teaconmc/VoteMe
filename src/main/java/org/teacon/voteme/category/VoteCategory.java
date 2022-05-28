package org.teacon.voteme.category;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.teacon.voteme.http.VoteMeHttpServer;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.UUID;

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

    public JsonElement toHTTPJson(ResourceLocation categoryID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", categoryID.toString());
        jsonObject.addProperty("name", this.name.getString());
        jsonObject.addProperty("description", this.description.getString());
        VoteDataStorage voteDataStorage = VoteDataStorage.get(VoteMeHttpServer.getMinecraftServer());
        jsonObject.add("vote_lists", Util.make(new JsonArray(), array -> {
            for (UUID artifactID : VoteArtifactNames.getArtifacts(false)) {
                int id = voteDataStorage.getIdOrCreate(artifactID, categoryID);
                Optional<VoteList> entryOptional = voteDataStorage.getVoteList(id);
                entryOptional.filter(e -> e.getEnabled().orElse(this.enabledDefault)).ifPresent(e -> array.add(id));
            }
        }));
        return jsonObject;
    }
}
