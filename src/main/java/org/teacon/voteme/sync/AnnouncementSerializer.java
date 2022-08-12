package org.teacon.voteme.sync;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.sync.VoteSynchronizer.*;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.primitives.ImmutableIntArray.copyOf;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class AnnouncementSerializer {
    public static final String ARTIFACT = "voteme:artifact"; // voteme:artifact:<artifact-id>
    public static final String COMMENTS = "voteme:comments"; // voteme:comments:<artifact-id>:<voter-id>
    public static final String VOTE = "voteme:vote"; // voteme:vote:<artifact-id>:<category-id>:<voter-id>
    public static final String VOTE_DISABLED = "voteme:vote_disabled"; // voteme:vote_disabled:<artifact-id>:<category-id>
    public static final String VOTE_STATS = "voteme:vote_stats"; // voteme:vote_stats:<artifact-id>:<category-id>:<role-id>

    private static final String KEY_ANNOUNCEMENT = "Announcement";
    private static final String KEY_ALIAS = "Alias";
    private static final String KEY_ARTIFACT_NAME = "ArtifactName";
    private static final String KEY_ARTIFACT = "ArtifactUUID";
    private static final String KEY_CATEGORY = "Category";
    private static final String KEY_COMMENTS = "Comments";
    private static final String KEY_DISABLED = "Disabled";
    private static final String KEY_LEVEL = "Level";
    private static final String KEY_LEVEL_COUNTS = "LevelCounts";
    private static final String KEY_VOTER = "VoterUUID";
    private static final String KEY_VOTE_ROLE = "VoteRole";
    private static final String KEY_VOTE_ROLES = "VoteRoles";
    private static final String KEY_VOTE_TIME = "VoteTime";

    public static Optional<CompoundTag> serialize(Announcement announcement) {
        try {
            CompoundTag nbt = new CompoundTag();
            if (announcement instanceof Artifact artifact) {
                nbt.putString(KEY_ANNOUNCEMENT, ARTIFACT);
                nbt.putUUID(KEY_ARTIFACT, artifact.key().artifactID());
                nbt.putString(KEY_ARTIFACT_NAME, artifact.name());
                artifact.alias().ifPresent(alias -> nbt.putString(KEY_ALIAS, alias));
                return Optional.of(nbt);
            }
            if (announcement instanceof Comments comments) {
                nbt.putString(KEY_ANNOUNCEMENT, COMMENTS);
                nbt.putUUID(KEY_ARTIFACT, comments.key().artifactID());
                nbt.putUUID(KEY_VOTER, comments.key().voterID());
                nbt.put(KEY_COMMENTS, Util.make(new ListTag(), tag -> comments
                        .comments().forEach(c -> tag.add(StringTag.valueOf(c)))));
                return Optional.of(nbt);
            }
            if (announcement instanceof Vote vote) {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE);
                nbt.putUUID(KEY_ARTIFACT, vote.key().artifactID());
                nbt.putString(KEY_CATEGORY, vote.key().categoryID().toString());
                nbt.putUUID(KEY_VOTER, vote.key().voterID());
                nbt.putInt(KEY_LEVEL, vote.level());
                nbt.put(KEY_VOTE_ROLES, Util.make(new ListTag(), tag -> vote
                        .roles().forEach(c -> tag.add(StringTag.valueOf(c.toString())))));
                nbt.putLong(KEY_VOTE_TIME, vote.time().toEpochMilli());
                return Optional.of(nbt);
            }
            if (announcement instanceof VoteDisabled voteDisabled) {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE_DISABLED);
                nbt.putUUID(KEY_ARTIFACT, voteDisabled.key().artifactID());
                nbt.putString(KEY_CATEGORY, voteDisabled.key().categoryID().toString());
                voteDisabled.disabled().ifPresent(disabled -> nbt.putBoolean(KEY_DISABLED, disabled));
                return Optional.of(nbt);
            }
            if (announcement instanceof VoteStats voteStats) {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE_STATS);
                nbt.putUUID(KEY_ARTIFACT, voteStats.key().artifactID());
                nbt.putString(KEY_CATEGORY, voteStats.key().categoryID().toString());
                nbt.putString(KEY_VOTE_ROLE, voteStats.key().roleID().toString());
                // noinspection UnstableApiUsage
                nbt.putIntArray(KEY_LEVEL_COUNTS, voteStats.counts().toArray());
                return Optional.of(nbt);
            }
            throw new IllegalArgumentException("unsupported announcement type: " + announcement.getClass());
        } catch (IllegalArgumentException e) {
            VoteMe.LOGGER.warn("Failed to serialize " + announcement + " to nbt", e);
            return Optional.empty();
        }
    }

    public static Optional<Announcement> deserialize(CompoundTag nbt) {
        try {
            String announceKey = nbt.getString(KEY_ANNOUNCEMENT);
            return Optional.of(switch (announceKey) {
                case ARTIFACT -> {
                    ArtifactKey key = new ArtifactKey(nbt.getUUID(KEY_ARTIFACT));
                    yield new Artifact(key, nbt.getString(KEY_ARTIFACT_NAME), nbt.contains(KEY_ALIAS,
                            Tag.TAG_STRING) ? Optional.of(nbt.getString(KEY_ALIAS)) : Optional.empty());
                }
                case COMMENTS -> {
                    CommentsKey key = new CommentsKey(nbt.getUUID(KEY_ARTIFACT), nbt.getUUID(KEY_VOTER));
                    yield new Comments(key, nbt.getList(KEY_COMMENTS, Tag.TAG_STRING)
                            .stream().map(Tag::getAsString).collect(toImmutableList()));
                }
                case VOTE -> {
                    VoteKey key = new VoteKey(nbt.getUUID(KEY_ARTIFACT),
                            new ResourceLocation(nbt.getString(KEY_CATEGORY)), nbt.getUUID(KEY_VOTER));
                    Instant time = nbt.contains(KEY_VOTE_TIME, Tag.TAG_LONG)
                            ? Instant.ofEpochMilli(nbt.getLong(KEY_VOTE_TIME)) : VoteList.DEFAULT_VOTE_TIME;
                    yield new Vote(key, nbt.getInt(KEY_LEVEL), nbt.getList(KEY_VOTE_ROLES, Tag.TAG_STRING)
                            .stream().map(t -> new ResourceLocation(t.getAsString())).collect(toImmutableSet()), time);
                }
                case VOTE_DISABLED -> {
                    VoteDisabledKey key = new VoteDisabledKey(nbt
                            .getUUID(KEY_ARTIFACT), new ResourceLocation(nbt.getString(KEY_CATEGORY)));
                    yield new VoteDisabled(key, nbt.contains(KEY_DISABLED,
                            Tag.TAG_BYTE) ? Optional.of(nbt.getBoolean(KEY_DISABLED)) : Optional.empty());
                }
                case VOTE_STATS -> {
                    VoteStatsKey key = new VoteStatsKey(nbt.getUUID(KEY_ARTIFACT), new ResourceLocation(nbt
                            .getString(KEY_CATEGORY)), new ResourceLocation(nbt.getString(KEY_VOTE_ROLE)));
                    // noinspection UnstableApiUsage
                    yield new VoteStats(key, copyOf(nbt.getIntArray(KEY_LEVEL_COUNTS)));
                }
                default -> throw new IllegalArgumentException("unsupported announce key: " + announceKey);
            });
        } catch (IllegalArgumentException e) {
            VoteMe.LOGGER.warn("Failed to serialize " + nbt + " to nbt", e);
            return Optional.empty();
        }
    }

    private AnnouncementSerializer() {
        throw new IllegalStateException();
    }
}
