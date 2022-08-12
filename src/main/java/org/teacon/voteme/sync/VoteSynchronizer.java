package org.teacon.voteme.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.ImmutableIntArray;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface VoteSynchronizer extends Closeable {
    void publish(Collection<? extends Announcement> announcements);

    Collection<? extends Announcement> dequeue();

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    sealed interface AnnounceKey<T extends Announcement> permits ArtifactKey, CommentsKey, VoteDisabledKey, VoteKey, VoteStatsKey {
        T cast(Announcement announcement);
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    sealed interface Announcement permits Artifact, Comments, Vote, VoteDisabled, VoteStats {
        AnnounceKey<?> key();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record ArtifactKey(UUID artifactID) implements AnnounceKey<Artifact> {
        @Override
        public Artifact cast(Announcement announcement) {
            return (Artifact) announcement;
        }
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Artifact(ArtifactKey key, String name, Optional<String> alias) implements Announcement {
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record CommentsKey(UUID artifactID, UUID voterID) implements AnnounceKey<Comments> {
        @Override
        public Comments cast(Announcement announcement) {
            return (Comments) announcement;
        }
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Comments(CommentsKey key, ImmutableList<String> comments) implements Announcement {
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteKey(UUID artifactID, ResourceLocation categoryID, UUID voterID) implements AnnounceKey<Vote> {
        @Override
        public Vote cast(Announcement announcement) {
            return (Vote) announcement;
        }
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Vote(VoteKey key, int level, ImmutableSet<ResourceLocation> roles, Instant time) implements Announcement {
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteDisabledKey(UUID artifactID, ResourceLocation categoryID) implements AnnounceKey<VoteDisabled> {
        @Override
        public VoteDisabled cast(Announcement announcement) {
            return (VoteDisabled) announcement;
        }
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteDisabled(VoteDisabledKey key, Optional<Boolean> disabled) implements Announcement {
        // nothing here
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteStatsKey(UUID artifactID, ResourceLocation categoryID,
                        ResourceLocation roleID) implements AnnounceKey<VoteStats> {
        @Override
        public VoteStats cast(Announcement announcement) {
            return (VoteStats) announcement;
        }
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteStats(VoteStatsKey key, ImmutableIntArray counts) implements Announcement {
        // nothing here
    }
}
