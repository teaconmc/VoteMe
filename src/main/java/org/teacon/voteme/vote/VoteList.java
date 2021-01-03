package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
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
        this.onVoteChange.run();
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
}
