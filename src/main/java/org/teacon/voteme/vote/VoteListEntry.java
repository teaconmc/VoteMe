package org.teacon.voteme.vote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

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
        JsonObject voteCounts = new JsonObject();
        voteCounts.addProperty("1", this.votes.getVoteCount(1));
        voteCounts.addProperty("2", this.votes.getVoteCount(2));
        voteCounts.addProperty("3", this.votes.getVoteCount(3));
        voteCounts.addProperty("4", this.votes.getVoteCount(4));
        voteCounts.addProperty("5", this.votes.getVoteCount(5));
        voteCounts.addProperty("sum", this.votes.getVoteCount());
        jsonObject.add("vote_counts", voteCounts);
        int truncation = VoteCategoryHandler.getCategory(this.category).map(c -> c.truncation).orElse(0);
        jsonObject.addProperty("vote_score", this.votes.getFinalScore(truncation));
    }
}
