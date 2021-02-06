package org.teacon.voteme.vote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteListEntry {
    public final VoteList votes;
    public final UUID artifactID;
    public final ResourceLocation category;

    public VoteListEntry(UUID artifactID, ResourceLocation category, VoteList votes) {
        this.artifactID = artifactID;
        this.category = category;
        this.votes = votes;
    }

    public static VoteListEntry fromNBT(CompoundNBT compound, Runnable onChange) {
        VoteList votes = new VoteList(onChange);
        votes.deserializeNBT(compound);
        UUID artifactID = compound.getUniqueId("ArtifactUUID");
        ResourceLocation category = new ResourceLocation(compound.getString("Category"));
        return new VoteListEntry(artifactID, category, votes);
    }

    public CompoundNBT toNBT() {
        CompoundNBT nbt = this.votes.serializeNBT();
        nbt.putUniqueId("ArtifactUUID", Objects.requireNonNull(this.artifactID));
        nbt.putString("Category", Objects.requireNonNull(this.category).toString());
        return nbt;
    }

    public JsonElement toHTTPJson(int voteListID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", voteListID);
        jsonObject.addProperty("category", this.category.toString());
        jsonObject.addProperty("artifact", this.artifactID.toString());
        SortedMap<String, VoteList.Stats> scores = this.votes.buildFinalScore(this.category);
        VoteList.Stats combinedScore = VoteList.Stats.combine(scores.values(), VoteList.Stats::getWeight);
        JsonObject voteStatsJson = this.toVoteStatsJson(combinedScore, combinedScore.getFinalScore(6.0F));
        voteStatsJson.add("subgroups", this.toVoteStatsJson(scores, combinedScore.getFinalScore(6.0F)));
        jsonObject.add("vote_stats", voteStatsJson);
        return jsonObject;
    }

    private JsonElement toVoteStatsJson(SortedMap<String, VoteList.Stats> scores, float defaultScore) {
        JsonObject voteCountInfo = new JsonObject();
        for (Map.Entry<String, VoteList.Stats> entry : scores.entrySet()) {
            voteCountInfo.add(entry.getKey(), this.toVoteStatsJson(entry.getValue(), defaultScore));
        }
        return voteCountInfo;
    }

    private JsonObject toVoteStatsJson(VoteList.Stats stats, float defaultScore) {
        JsonObject childVoteCountInfo = new JsonObject();
        childVoteCountInfo.addProperty("1", stats.getVoteCount(1));
        childVoteCountInfo.addProperty("2", stats.getVoteCount(2));
        childVoteCountInfo.addProperty("3", stats.getVoteCount(3));
        childVoteCountInfo.addProperty("4", stats.getVoteCount(4));
        childVoteCountInfo.addProperty("5", stats.getVoteCount(5));
        childVoteCountInfo.addProperty("sum", stats.getVoteCount());
        childVoteCountInfo.addProperty("effective", stats.getEffectiveCount());
        childVoteCountInfo.addProperty("weight", stats.getWeight());
        childVoteCountInfo.addProperty("score", stats.getFinalScore(defaultScore));
        return childVoteCountInfo;
    }
}
