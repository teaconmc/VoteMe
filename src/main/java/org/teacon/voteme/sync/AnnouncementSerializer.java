package org.teacon.voteme.sync;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.sync.VoteSynchronizer.*;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.primitives.ImmutableIntArray.copyOf;

@FieldsAreNonnullByDefault
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
    private static final String KEY_REVISION = "Revision";
    private static final String KEY_VOTER = "VoterUUID";
    private static final String KEY_VOTE_ROLE = "VoteRole";
    private static final String KEY_VOTE_ROLES = "VoteRoles";
    private static final String KEY_VOTE_TIME = "VoteTime";

    public static Optional<CompoundTag> serialize(Announcement announcement) {
        CompoundTag nbt = new CompoundTag();
        switch (announcement) {
            case Artifact artifact -> {
                nbt.putString(KEY_ANNOUNCEMENT, ARTIFACT);
                nbt.store(KEY_ARTIFACT, UUIDUtil.CODEC, artifact.key().artifactID());
                nbt.putString(KEY_ARTIFACT_NAME, artifact.name());
                artifact.alias().ifPresent(alias -> nbt.putString(KEY_ALIAS, alias));
                return Optional.of(nbt);
            }
            case Comments comments -> {
                nbt.putString(KEY_ANNOUNCEMENT, COMMENTS);
                nbt.store(KEY_ARTIFACT, UUIDUtil.CODEC, comments.key().artifactID());
                nbt.store(KEY_VOTER, UUIDUtil.CODEC, comments.key().voterID());
                nbt.putInt(KEY_REVISION, comments.key().revision());
                nbt.put(KEY_COMMENTS, Util.make(new ListTag(), tag -> comments
                        .comments().forEach(c -> tag.add(StringTag.valueOf(c)))));
                return Optional.of(nbt);
            }
            case Vote vote -> {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE);
                nbt.store(KEY_ARTIFACT, UUIDUtil.CODEC, vote.key().artifactID());
                nbt.putString(KEY_CATEGORY, vote.key().categoryID().toString());
                nbt.store(KEY_VOTER, UUIDUtil.CODEC, vote.key().voterID());
                nbt.putInt(KEY_LEVEL, vote.level());
                nbt.put(KEY_VOTE_ROLES, Util.make(new ListTag(), tag -> vote
                        .roles().forEach(c -> tag.add(StringTag.valueOf(c.toString())))));
                nbt.putLong(KEY_VOTE_TIME, vote.time().toEpochMilli());
                return Optional.of(nbt);
            }
            case VoteDisabled voteDisabled -> {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE_DISABLED);
                nbt.store(KEY_ARTIFACT, UUIDUtil.CODEC, voteDisabled.key().artifactID());
                nbt.putString(KEY_CATEGORY, voteDisabled.key().categoryID().toString());
                voteDisabled.disabled().ifPresent(disabled -> nbt.putBoolean(KEY_DISABLED, disabled));
                return Optional.of(nbt);
            }
            case VoteStats voteStats -> {
                nbt.putString(KEY_ANNOUNCEMENT, VOTE_STATS);
                nbt.store(KEY_ARTIFACT, UUIDUtil.CODEC, voteStats.key().artifactID());
                nbt.putString(KEY_CATEGORY, voteStats.key().categoryID().toString());
                nbt.putString(KEY_VOTE_ROLE, voteStats.key().roleID().toString());
                nbt.putIntArray(KEY_LEVEL_COUNTS, voteStats.counts().toArray());
                return Optional.of(nbt);
            }
        }
    }

    public static Optional<Announcement> deserialize(CompoundTag nbt) {
        try {
            String announceKey = nbt.getString(KEY_ANNOUNCEMENT).orElseThrow();
            return Optional.of(switch (announceKey) {
                case ARTIFACT -> {
                    ArtifactKey key = new ArtifactKey(nbt.read(KEY_ARTIFACT, UUIDUtil.CODEC).orElseThrow());
                    yield new Artifact(key, nbt.getString(KEY_ARTIFACT_NAME).orElseThrow(),
                            nbt.contains(KEY_ALIAS) ? Optional.of(nbt.getString(KEY_ALIAS).orElseThrow()) : Optional.empty());
                }
                case COMMENTS -> {
                    CommentsKey key = new CommentsKey(
                            nbt.read(KEY_ARTIFACT, UUIDUtil.CODEC).orElseThrow(),
                            nbt.read(KEY_VOTER, UUIDUtil.CODEC).orElseThrow(),
                            nbt.getInt(KEY_REVISION).orElseThrow());
                    yield new Comments(key, nbt.getList(KEY_COMMENTS).orElseGet(ListTag::new)
                            .stream().map(tag -> tag.asString().orElseThrow()).collect(toImmutableList()));
                }
                case VOTE -> {
                    VoteKey key = new VoteKey(
                            nbt.read(KEY_ARTIFACT, UUIDUtil.CODEC).orElseThrow(),
                            Identifier.parse(nbt.getString(KEY_CATEGORY).orElseThrow()),
                            nbt.read(KEY_VOTER, UUIDUtil.CODEC).orElseThrow()
                    );
                    Instant time = nbt.contains(KEY_VOTE_TIME)
                            ? Instant.ofEpochMilli(nbt.getLong(KEY_VOTE_TIME).orElseThrow()) : VoteList.DEFAULT_VOTE_TIME;
                    yield new Vote(key, nbt.getInt(KEY_LEVEL).orElseThrow(), nbt.getList(KEY_VOTE_ROLES).orElseGet(ListTag::new)
                            .stream().map(tag -> Identifier.parse(tag.asString().orElseThrow())).collect(toImmutableSet()), time);
                }
                case VOTE_DISABLED -> {
                    VoteDisabledKey key = new VoteDisabledKey(
                            nbt.read(KEY_ARTIFACT, UUIDUtil.CODEC).orElseThrow(),
                            Identifier.parse(nbt.getString(KEY_CATEGORY).orElseThrow())
                    );
                    yield new VoteDisabled(key, nbt.contains(KEY_DISABLED)
                            ? Optional.of(nbt.getBoolean(KEY_DISABLED).orElseThrow()) : Optional.empty());
                }
                case VOTE_STATS -> {
                    VoteStatsKey key = new VoteStatsKey(
                            nbt.read(KEY_ARTIFACT, UUIDUtil.CODEC).orElseThrow(),
                            Identifier.parse(nbt.getString(KEY_CATEGORY).orElseThrow()),
                            Identifier.parse(nbt.getString(KEY_VOTE_ROLE).orElseThrow())
                    );
                    yield new VoteStats(key, copyOf(nbt.getIntArray(KEY_LEVEL_COUNTS).orElseThrow()));
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
