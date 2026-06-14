package org.teacon.voteme.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.ImmutableIntArray;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.resources.Identifier;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface VoteSynchronizer extends Closeable {
    void publish(Collection<? extends Announcement> announcements);

    Collection<? extends Announcement> dequeue();

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    sealed interface AnnounceKey<T extends Announcement> permits ArtifactKey, CommentsKey, VoteDisabledKey, VoteKey, VoteStatsKey {
        T cast(Announcement announcement);
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    sealed interface Announcement permits Artifact, Comments, Vote, VoteDisabled, VoteStats {
        AnnounceKey<?> key();
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record ArtifactKey(UUID artifactID) implements AnnounceKey<Artifact> {
        @Override
        public Artifact cast(Announcement announcement) {
            return (Artifact) announcement;
        }
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Artifact(ArtifactKey key, String name, Optional<String> alias) implements Announcement {
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record CommentsKey(UUID artifactID, UUID voterID, int revision) implements AnnounceKey<Comments> {
        @Override
        public Comments cast(Announcement announcement) {
            return (Comments) announcement;
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Comments(CommentsKey key, ImmutableList<String> comments) implements Announcement {
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteKey(UUID artifactID, Identifier categoryID, UUID voterID) implements AnnounceKey<Vote> {
        @Override
        public Vote cast(Announcement announcement) {
            return (Vote) announcement;
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Vote(VoteKey key, int level, ImmutableSet<Identifier> roles, Instant time) implements Announcement {
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteDisabledKey(UUID artifactID, Identifier categoryID) implements AnnounceKey<VoteDisabled> {
        @Override
        public VoteDisabled cast(Announcement announcement) {
            return (VoteDisabled) announcement;
        }
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteDisabled(VoteDisabledKey key, Optional<Boolean> disabled) implements Announcement {
        // nothing here
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteStatsKey(UUID artifactID, Identifier categoryID,
                        Identifier roleID) implements AnnounceKey<VoteStats> {
        @Override
        public VoteStats cast(Announcement announcement) {
            return (VoteStats) announcement;
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record VoteStats(VoteStatsKey key, ImmutableIntArray counts) implements Announcement {
        // nothing here
    }
}
