package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteList implements INBTSerializable<CompoundNBT> {

    private final Multiset<UUID> votes;
    private final Runnable onChange;

    public VoteList(Runnable onChange) {
        this.votes = HashMultiset.create();
        this.onChange = onChange;
    }

    public int get(UUID player) {
        return this.votes.count(player);
    }

    public void set(UUID player, int level) {
        this.votes.setCount(player, MathHelper.clamp(level, 0, 5));
    }

    public int getVoteCount() {
        return this.votes.elementSet().size();
    }

    public float getFinalScore() {
        return this.votes.isEmpty() ? 6.0F : 2.0F * this.votes.size() / this.votes.elementSet().size();
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
        ListNBT nbt = compound.getList("Votes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = nbt.size(); i < size; ++i) {
            CompoundNBT child = nbt.getCompound(i);
            this.votes.setCount(child.getUniqueId("UUID"), MathHelper.clamp(child.getInt("Level"), 1, 5));
        }
    }

    public static VoteList.Entry fromNBT(CompoundNBT compound, Runnable onChange) {
        VoteList votes = new VoteList(onChange);
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
    }
}
