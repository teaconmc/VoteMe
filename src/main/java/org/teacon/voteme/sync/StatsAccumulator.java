package org.teacon.voteme.sync;

import com.google.common.primitives.ImmutableIntArray;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.teacon.voteme.sync.VoteSynchronizer.Vote;
import org.teacon.voteme.sync.VoteSynchronizer.VoteKey;
import org.teacon.voteme.sync.VoteSynchronizer.VoteStatsKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class StatsAccumulator {
    private static final int MAX_LEVEL = 5;

    private final Map<VoteKey, Vote> subtractedVotes = new HashMap<>();
    private final Map<VoteKey, Vote> accumulatedVotes = new HashMap<>();
    private final List<Object2IntMap<VoteStatsKey>> countMaps = IntStream.range(0, MAX_LEVEL + 1)
            .<Object2IntMap<VoteStatsKey>>mapToObj(level -> new Object2IntOpenHashMap<>()).toList();

    private void increase(Vote vote) {
        for (ResourceLocation roleID : vote.roles()) {
            VoteStatsKey key = new VoteStatsKey(vote.key().artifactID(), vote.key().categoryID(), roleID);
            this.countMaps.get(vote.level()).mergeInt(key, 1, Integer::sum);
            this.countMaps.get(0).mergeInt(key, -1, Integer::sum);
        }
    }

    private void decrease(Vote vote) {
        for (ResourceLocation roleID : vote.roles()) {
            VoteStatsKey key = new VoteStatsKey(vote.key().artifactID(), vote.key().categoryID(), roleID);
            this.countMaps.get(vote.level()).mergeInt(key, -1, Integer::sum);
            this.countMaps.get(0).mergeInt(key, 1, Integer::sum);
        }
    }

    public void accumulate(Vote vote) {
        Vote prevSubtractedVote = this.subtractedVotes.get(vote.key());
        if (prevSubtractedVote == null || prevSubtractedVote.time().isBefore(vote.time())) {
            Vote prevAccumulatedVote = this.accumulatedVotes.get(vote.key());
            if (prevAccumulatedVote == null || prevAccumulatedVote.time().isBefore(vote.time())) {
                if (prevAccumulatedVote != null) {
                    this.decrease(prevAccumulatedVote);
                }
                this.accumulatedVotes.put(vote.key(), vote);
                this.increase(vote);
            }
        }
    }

    public void subtract(Vote vote) {
        Vote prevSubtractedVote = this.subtractedVotes.get(vote.key());
        if (prevSubtractedVote == null || prevSubtractedVote.time().isBefore(vote.time())) {
            Vote prevAccumulatedVote = this.accumulatedVotes.get(vote.key());
            if (prevAccumulatedVote != null && !vote.time().isBefore(prevAccumulatedVote.time())) {
                this.accumulatedVotes.remove(vote.key());
                this.decrease(prevAccumulatedVote);
            }
            this.subtractedVotes.put(vote.key(), vote);
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
