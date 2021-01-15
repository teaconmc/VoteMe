package org.teacon.voteme.vote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteListEntry {
    public final VoteList votes;
    public final String artifact;
    public final ResourceLocation category;

    public VoteListEntry(String artifact, ResourceLocation category, VoteList votes) {
        this.category = category;
        this.artifact = artifact;
        this.votes = votes;
    }

    public static VoteListEntry fromNBT(CompoundNBT compound, Runnable onChange) {
        VoteList votes = new VoteList(onChange);
        votes.deserializeNBT(compound);
        String artifact = compound.getString("Artifact");
        ResourceLocation category = new ResourceLocation(compound.getString("Category"));
        return new VoteListEntry(artifact, category, votes);
    }

    public CompoundNBT toNBT() {
        CompoundNBT nbt = this.votes.serializeNBT();
        nbt.putString("Artifact", Objects.requireNonNull(this.artifact));
        nbt.putString("Category", Objects.requireNonNull(this.category).toString());
        return nbt;
    }

    public void toJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        jsonObject.addProperty("artifact", this.artifact);
        jsonObject.addProperty("category", this.category.toString());
        SortedMap<ResourceLocation, VoteList.Stats> scores = this.votes.buildFinalScore(this.category);
        jsonObject.add("vote_counts", this.toVoteCountInfoJson(scores));
        jsonObject.addProperty("final_score", VoteList.Stats.combine(scores.values()).getFinalScore());
    }

    private JsonElement toVoteCountInfoJson(SortedMap<ResourceLocation, VoteList.Stats> scores) {
        JsonArray voteCountInfo = new JsonArray();
        for (Map.Entry<ResourceLocation, VoteList.Stats> entry : scores.entrySet()) {
            ResourceLocation role = entry.getKey();
            VoteList.Stats stats = entry.getValue();
            JsonObject childVoteCountInfo = new JsonObject();
            childVoteCountInfo.addProperty("role", role.toString());
            childVoteCountInfo.addProperty("1", stats.getVoteCount(1));
            childVoteCountInfo.addProperty("2", stats.getVoteCount(2));
            childVoteCountInfo.addProperty("3", stats.getVoteCount(3));
            childVoteCountInfo.addProperty("4", stats.getVoteCount(4));
            childVoteCountInfo.addProperty("5", stats.getVoteCount(5));
            childVoteCountInfo.addProperty("sum", stats.getVoteCount());
            childVoteCountInfo.addProperty("weight", stats.getWeight());
            childVoteCountInfo.addProperty("score", stats.getFinalScore());
            voteCountInfo.add(childVoteCountInfo);
        }
        return voteCountInfo;
    }
}
