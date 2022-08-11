package org.teacon.voteme.sync;

import com.google.common.base.Preconditions;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.teacon.voteme.VoteMe;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class DetachedSynchronizer implements VoteSynchronizer {
    private final MinecraftServer server;
    private final Map<VoteKey, Vote> votes = new HashMap<>();
    private List<Announcement> queued = new ArrayList<>();

    public DetachedSynchronizer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void publish(Announcement announcement) {
        Preconditions.checkArgument(this.server.isSameThread(), "server thread");
        VoteMe.LOGGER.info("Publishing announcement locally: {}", announcement.key());
        if (announcement instanceof Artifact artifact) {
            this.queued.add(artifact);
            return;
        }
        if (announcement instanceof Comments comments) {
            this.queued.add(comments);
            return;
        }
        if (announcement instanceof Vote vote) {
            StatsAccumulator accumulator = new StatsAccumulator();
            if (this.votes.containsKey(vote.key())) {
                accumulator.subtract(this.votes.get(vote.key()));
            }
            accumulator.accumulate(vote);
            // it should loop at most once
            Util.make(new ArrayList<>(), accumulator::buildAffectedVotes).forEach(affectedVote -> {
                this.votes.put(affectedVote.key(), affectedVote);
                this.queued.add(affectedVote);
            });
            Util.make(new HashMap<>(), accumulator::buildStatsMap).forEach((key, counts) -> {
                VoteStats affectedStats = new VoteStats(key, counts);
                this.queued.add(affectedStats);
            });
            return;
        }
        if (announcement instanceof VoteDisabled voteDisabled) {
            this.queued.add(voteDisabled);
            return;
        }
        throw new IllegalArgumentException("unsupported outbound announcement");
    }

    @Override
    public Collection<? extends Announcement> dequeue() {
        Preconditions.checkArgument(this.server.isSameThread(), "server thread");
        if (this.queued.size() > 0) {
            VoteMe.LOGGER.info("Retrieving {} announcement locally.", this.queued.size());
            Collection<? extends Announcement> result = this.queued;
            this.queued = new ArrayList<>();
            return result;
        }
        return new ArrayList<>();
    }

    @Override
    public void close() {
        Preconditions.checkArgument(this.server.isSameThread(), "server thread");
        this.queued.clear();
    }
}
