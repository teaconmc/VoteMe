package org.teacon.voteme.sync;

import com.google.common.base.Preconditions;
import com.google.common.primitives.ImmutableIntArray;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.teacon.voteme.sync.VoteSynchronizer.Vote;
import org.teacon.voteme.sync.VoteSynchronizer.VoteKey;
import org.teacon.voteme.sync.VoteSynchronizer.VoteStatsKey;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

public final class StatsAccumulator {
    private static final int MAX_LEVEL = 5;

    private final Map<VoteKey, Vote> subtractedVotes = new HashMap<>();
    private final Map<VoteKey, Vote> accumulatedVotes = new HashMap<>();
    private final List<Object2IntMap<VoteStatsKey>> countMaps = IntStream.range(0, MAX_LEVEL + 1)
            .<Object2IntMap<VoteStatsKey>>mapToObj(level -> new Object2IntOpenHashMap<>()).toList();

    private void increase(@Nullable Vote vote) {
        if (vote != null) {
            for (ResourceLocation roleID : vote.roles()) {
                VoteStatsKey key = new VoteStatsKey(vote.key().artifactID(), vote.key().categoryID(), roleID);
                this.countMaps.get(vote.level()).mergeInt(key, 1, Integer::sum);
                this.countMaps.get(0).mergeInt(key, -1, Integer::sum);
            }
        }
    }

    private void decrease(@Nullable Vote vote) {
        if (vote != null) {
            for (ResourceLocation roleID : vote.roles()) {
                VoteStatsKey key = new VoteStatsKey(vote.key().artifactID(), vote.key().categoryID(), roleID);
                this.countMaps.get(vote.level()).mergeInt(key, -1, Integer::sum);
                this.countMaps.get(0).mergeInt(key, 1, Integer::sum);
            }
        }
    }

    private void forceAccumulate(VoteKey voteKey, @Nullable Vote prev, @Nullable Vote vote) {
        @Nullable Vote subtracted = this.subtractedVotes.get(voteKey);
        if (prev != null) {
            Preconditions.checkArgument(Objects.equals(this.accumulatedVotes.remove(voteKey), prev));
            this.increase(subtracted);
            this.decrease(prev);
        }
        if (vote != null) {
            Preconditions.checkArgument(Objects.isNull(this.accumulatedVotes.put(voteKey, vote)));
            this.decrease(subtracted);
            this.increase(vote);
        }
    }

    private void forceSubtract(VoteKey voteKey, @Nullable Vote prev, @Nullable Vote vote) {
        @Nullable Vote accumulated = this.accumulatedVotes.get(voteKey);
        if (prev != null) {
            Preconditions.checkArgument(Objects.equals(this.subtractedVotes.remove(voteKey), prev));
        }
        if (vote != null) {
            Preconditions.checkArgument(Objects.isNull(this.subtractedVotes.put(voteKey, vote)));
        }
        if (accumulated != null) {
            this.increase(prev);
            this.decrease(vote);
        }
    }

    public void accumulate(Vote vote) {
        VoteKey voteKey = vote.key();
        @Nullable Vote prevSubtracted = this.subtractedVotes.get(voteKey);
        if (prevSubtracted == null || prevSubtracted.time().isBefore(vote.time())) {
            @Nullable Vote prevAccumulated = this.accumulatedVotes.get(voteKey);
            if (prevAccumulated == null || prevAccumulated.time().isBefore(vote.time())) {
                this.forceAccumulate(voteKey, prevAccumulated, vote);
            }
        }
    }

    public void subtract(Vote vote) {
        VoteKey voteKey = vote.key();
        @Nullable Vote prevSubtracted = this.subtractedVotes.get(voteKey);
        if (prevSubtracted == null || prevSubtracted.time().isBefore(vote.time())) {
            @Nullable Vote prevAccumulated = this.accumulatedVotes.get(voteKey);
            if (prevAccumulated != null && !vote.time().isBefore(prevAccumulated.time())) {
                this.forceAccumulate(voteKey, prevAccumulated, null);
            }
            this.forceSubtract(voteKey, prevSubtracted, vote);
        }
    }

    public void buildAffectedVotes(List<? super Vote> votes) {
        votes.addAll(this.accumulatedVotes.values());
    }

    public void buildStatsMap(Map<? super VoteStatsKey, ? super ImmutableIntArray> map) {
        Map<VoteStatsKey, int[]> result = new HashMap<>();
        for (int i = 0; i < MAX_LEVEL + 1; ++i) {
            Object2IntMap<VoteStatsKey> countMap = this.countMaps.get(i);
            for (Object2IntMap.Entry<VoteStatsKey> entry : countMap.object2IntEntrySet()) {
                int count = entry.getIntValue();
                if (count != 0) {
                    result.computeIfAbsent(entry.getKey(), k -> new int[MAX_LEVEL + 1])[i] += count;
                }
            }
        }
        for (Map.Entry<VoteStatsKey, int[]> entry : result.entrySet()) {
            map.put(entry.getKey(), ImmutableIntArray.copyOf(entry.getValue()));
        }
    }
}
