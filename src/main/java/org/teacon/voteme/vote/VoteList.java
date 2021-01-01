package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Objects;
import java.util.SortedMap;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteList implements INBTSerializable<CompoundNBT> {

    private final int[] countsByLevel;
    private final Multiset<UUID> votes;
    private final Runnable onVoteChange;

    public VoteList(Runnable onChange) {
        this.countsByLevel = new int[1 + 5];
        this.votes = HashMultiset.create();
        this.onVoteChange = onChange;
    }

    public int get(UUID player) {
        return this.votes.count(player);
    }

    public void set(UUID player, int level) {
        Preconditions.checkArgument(level >= 0 && level <= 5);
        int oldLevel = this.votes.setCount(player, level);
        --this.countsByLevel[oldLevel];
        ++this.countsByLevel[level];
    }

    public int getVoteCount() {
        return -this.countsByLevel[0];
    }

    public int getVoteCount(int level) {
        Preconditions.checkArgument(level > 0 && level <= 5);
        return this.countsByLevel[level];
    }

    public double getFinalScore(int truncation) {
        int count = -this.countsByLevel[0];
        if (count > truncation * 2) {
            int[] counts = this.countsByLevel.clone();
            for (int i = 1, left = truncation; left > 0; ++i) {
                int diff = Math.min(left, counts[i]);
                counts[i] -= diff;
                left -= diff;
            }
            for (int i = 5, left = truncation; left > 0; --i) {
                int diff = Math.min(left, counts[i]);
                counts[i] -= diff;
                left -= diff;
            }
            double sum = 2.0 * counts[1] + 4.0 * counts[2] + 6.0 * counts[3] + 8.0 * counts[4] + 10.0 * counts[5];
            return sum / (count - truncation * 2);
        }
        return 6.0;
    }

    @Override
    public CompoundNBT serializeNBT() {
        ListNBT nbt = new ListNBT();
        for (Multiset.Entry<UUID> entry : this.votes.entrySet()) {
            CompoundNBT child = new CompoundNBT();
            child.putUniqueId("UUID", entry.getElement());
            child.putInt("Level", entry.getCount());
        }
        CompoundNBT result = new CompoundNBT();
        result.put("Votes", nbt);
        return result;
    }

    @Override
    public void deserializeNBT(CompoundNBT compound) {
        this.votes.clear();
        Arrays.fill(this.countsByLevel, 0);
        ListNBT nbt = compound.getList("Votes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = nbt.size(); i < size; ++i) {
            CompoundNBT child = nbt.getCompound(i);
            int level = MathHelper.clamp(child.getInt("Level"), 1, 5);
            this.votes.setCount(child.getUniqueId("UUID"), level);
            ++this.countsByLevel[level];
            --this.countsByLevel[0];
        }
        this.onVoteChange.run();
    }

    public static VoteList.Entry fromNBT(CompoundNBT compound, Runnable onChange) {
        VoteList votes = new VoteList(onChange);
        votes.deserializeNBT(compound);
        String artifact = compound.getString("Artifact");
        ResourceLocation category = new ResourceLocation(compound.getString("Category"));
        return new Entry(artifact, category, votes);
    }

    public static class Entry {
        private final VoteList votes;
        private final String artifact;
        private final ResourceLocation category;

        public Entry(String artifact, ResourceLocation category, VoteList votes) {
            this.category = category;
            this.artifact = artifact;
            this.votes = votes;
        }

        public VoteList getVotes() {
            return this.votes;
        }

        public String getArtifact() {
            return this.artifact;
        }

        public ResourceLocation getCategory() {
            return this.category;
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
            SortedMap<ResourceLocation, VoteCategory> categories = VoteCategoryHandler.getCategoryMap();
            int truncation = categories.containsKey(this.category) ? categories.get(this.category).truncation : 0;
            jsonObject.addProperty("vote_score", this.votes.getFinalScore(truncation));
        }
    }
}
