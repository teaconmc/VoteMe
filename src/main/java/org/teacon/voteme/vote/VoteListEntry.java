package org.teacon.voteme.vote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

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

    public static VoteListEntry fromNBT(CompoundTag compound, Runnable onChange) {
        VoteList votes = new VoteList(onChange);
        votes.deserializeNBT(compound);
        UUID artifactID = compound.getUUID("ArtifactUUID");
        ResourceLocation category = new ResourceLocation(compound.getString("Category"));
        return new VoteListEntry(artifactID, category, votes);
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = this.votes.serializeNBT();
        nbt.putUUID("ArtifactUUID", Objects.requireNonNull(this.artifactID));
        nbt.putString("Category", Objects.requireNonNull(this.category).toString());
        return nbt;
    }

    public JsonElement toHTTPJson(int voteListID) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", voteListID);
        jsonObject.addProperty("category", this.category.toString());
        jsonObject.addProperty("artifact", this.artifactID.toString());
        SortedMap<String, VoteList.Stats> scores = this.votes.buildFinalScore(this.category);
        VoteList.Stats combined = VoteList.Stats.combine(scores.values(), VoteList.Stats::getWeight);
        jsonObject.add("vote_stats", Util.make(this.toVoteStatsJson("", combined, combined.getFinalScore(6.0F)), e -> {
            e.getAsJsonObject().add("subgroups", this.toVoteStatsJson(scores, combined.getFinalScore(6.0F)));
            e.getAsJsonObject().remove("id");
        }));
        return jsonObject;
    }

    public float getFinalScore(float defaultScore) {
        SortedMap<String, VoteList.Stats> scores = this.votes.buildFinalScore(this.category);
        return VoteList.Stats.combine(scores.values(), VoteList.Stats::getWeight).getFinalScore(defaultScore);
    }

    private JsonElement toVoteStatsJson(SortedMap<String, VoteList.Stats> scores, float defaultScore) {
        JsonArray voteCountInfo = new JsonArray();
        for (Map.Entry<String, VoteList.Stats> entry : scores.entrySet()) {
            JsonElement child = this.toVoteStatsJson(entry.getKey(), entry.getValue(), defaultScore);
            voteCountInfo.add(child);
        }
        return voteCountInfo;
    }

    private JsonElement toVoteStatsJson(String subgroup, VoteList.Stats stats, float defaultScore) {
        return Util.make(new JsonObject(), child -> {
            child.addProperty("id", subgroup);
            child.addProperty("score", stats.getFinalScore(defaultScore));
            child.addProperty("weight", stats.getWeight());
            child.add("counts", Util.make(new JsonObject(), counts -> {
                counts.addProperty("1", stats.getVoteCount(1));
                counts.addProperty("2", stats.getVoteCount(2));
                counts.addProperty("3", stats.getVoteCount(3));
                counts.addProperty("4", stats.getVoteCount(4));
                counts.addProperty("5", stats.getVoteCount(5));
                counts.addProperty("sum", stats.getVoteCount());
                counts.addProperty("effective", stats.getEffectiveCount());
            }));
        });
    }
}
