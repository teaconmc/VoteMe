package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.ImmutableIntArray;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.tuple.Triple;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.sync.VoteSynchronizer.Announcement;
import org.teacon.voteme.sync.VoteSynchronizer.Vote;
import org.teacon.voteme.sync.VoteSynchronizer.VoteDisabled;
import org.teacon.voteme.sync.VoteSynchronizer.VoteDisabledKey;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.teacon.voteme.sync.VoteSynchronizer.VoteKey;
import static org.teacon.voteme.sync.VoteSynchronizer.VoteStats;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteList implements INBTSerializable<CompoundTag> {
    public static final Instant DEFAULT_VOTE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME.parse("Sat, 9 Jan 2021 02:00:00 +0800", Instant::from);

    private @Nullable Boolean enabled;

    private final VoteDisabledKey key;
    private final Queue<Announcement> queuedAnnouncements;
    private final SortedMap<String, Stats> delayedCachedScores;
    private final Map<ResourceLocation, ImmutableIntArray> delayedStatsMap;
    private final Map<UUID, Triple<Integer, ImmutableSet<ResourceLocation>, Instant>> votes;

    public VoteList(UUID artifactID, ResourceLocation categoryID) {
        this.enabled = null;
        this.votes = new HashMap<>();
        this.delayedStatsMap = new HashMap<>();
        this.delayedCachedScores = new TreeMap<>();
        this.queuedAnnouncements = new ArrayDeque<>();
        this.key = new VoteDisabledKey(artifactID, categoryID);
    }

    public void publish(Announcement announcement) {
        if (announcement instanceof VoteStats voteStats) {
            this.handleVoteStatsAnnouncement(voteStats);
            return;
        }
        if (announcement instanceof Vote vote) {
            this.handleVoteAnnouncement(vote);
            return;
        }
        if (announcement instanceof VoteDisabled voteDisabled) {
            this.handleVoteDisabledAnnouncement(voteDisabled);
            return;
        }
        throw new IllegalArgumentException("unsupported announcement type: " + announcement.getClass());
    }

    public void dequeue(Collection<? super Announcement> drainTo) {
        drainTo.addAll(this.queuedAnnouncements);
        this.queuedAnnouncements.clear();
    }

    private void handleVoteStatsAnnouncement(VoteStats voteStats) {
        Preconditions.checkArgument(this.key.artifactID().equals(voteStats.key().artifactID()), "wrong artifact id");
        Preconditions.checkArgument(this.key.categoryID().equals(voteStats.key().categoryID()), "wrong category id");
        // noinspection UnstableApiUsage
        ImmutableIntArray oldCounts = this.delayedStatsMap.put(voteStats.key().roleID(), voteStats.counts());
        // noinspection UnstableApiUsage
        if (!voteStats.counts().equals(oldCounts)) {
            this.delayedCachedScores.clear();
        }
    }

    private void handleVoteAnnouncement(Vote vote) {
        Preconditions.checkArgument(this.key.artifactID().equals(vote.key().artifactID()), "wrong artifact id");
        Preconditions.checkArgument(this.key.categoryID().equals(vote.key().categoryID()), "wrong category id");
        UUID voterID = vote.key().voterID();
        if (!this.votes.containsKey(voterID) || this.votes.get(voterID).getRight().isBefore(vote.time())) {
            if (vote.level() != 0) {
                this.votes.put(voterID, Triple.of(vote.level(), vote.roles(), vote.time()));
            } else {
                this.votes.remove(voterID);
            }
        }
    }

    private void handleVoteDisabledAnnouncement(VoteDisabled voteDisabled) {
        Preconditions.checkArgument(this.key.artifactID().equals(voteDisabled.key().artifactID()), "wrong artifact id");
        Preconditions.checkArgument(this.key.categoryID().equals(voteDisabled.key().categoryID()), "wrong category id");
        this.enabled = voteDisabled.disabled().map(a -> !a).orElse(null);
    }

    private void emitVoteAnnouncement(UUID uuid, Triple<Integer, ImmutableSet<ResourceLocation>, Instant> triple) {
        VoteKey key = new VoteKey(this.key.artifactID(), this.key.categoryID(), uuid);
        this.queuedAnnouncements.offer(new Vote(key, triple.getLeft(), triple.getMiddle(), triple.getRight()));
    }

    private void emitVoteDisabledAnnouncement(@Nullable Boolean enabled) {
        Optional<Boolean> disabled = enabled == null ? Optional.empty() : Optional.of(!enabled);
        this.queuedAnnouncements.offer(new VoteDisabled(this.key, disabled));
    }

    public UUID getArtifactID() {
        return this.key.artifactID();
    }

    public ResourceLocation getCategoryID() {
        return this.key.categoryID();
    }

    public Optional<Boolean> getEnabled() {
        return Optional.ofNullable(this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != Boolean.valueOf(enabled)) {
            this.enabled = enabled;
            this.emitVoteDisabledAnnouncement(enabled);
        }
    }

    public void unsetEnabled() {
        if (this.enabled != null) {
            this.enabled = null;
            this.emitVoteDisabledAnnouncement(null);
        }
    }

    public int merge(VoteList fromList) {
        int[] countArray = new int[1];
        if (fromList != this) {
            fromList.votes.forEach((uuid, from) -> {
                Triple<Integer, ImmutableSet<ResourceLocation>, Instant> triple = this.votes.get(uuid);
                if (triple == null || triple.getRight().isBefore(from.getRight())) {
                    countArray[0] += 1;
                    this.votes.put(uuid, from);
                    this.emitVoteAnnouncement(uuid, from);
                }
            });
        }
        return countArray[0];
    }

    public int get(ServerPlayer player) {
        return this.get(player.getUUID());
    }

    public int get(UUID uuid) {
        return this.votes.containsKey(uuid) ? this.votes.get(uuid).getLeft() : 0;
    }

    public Optional<Instant> getTime(UUID uuid) {
        return this.votes.containsKey(uuid) ? Optional.of(this.votes.get(uuid).getRight()) : Optional.empty();
    }

    public Collection<? extends ResourceLocation> getRoles(UUID uuid) {
        return this.votes.containsKey(uuid) ? this.votes.get(uuid).getMiddle() : ImmutableSet.of();
    }

    public void set(ServerPlayer player, int level) {
        this.set(player.getUUID(), level, VoteRoleHandler.getRoles(player), Instant.now());
    }

    public void set(UUID uuid, int level, Collection<? extends ResourceLocation> roles, Instant voteTime) {
        Preconditions.checkArgument(level >= 0 && level <= 5, "level out of range from 0 to 5");

        ImmutableSet<ResourceLocation> roleSet = ImmutableSet.copyOf(roles);
        Triple<Integer, ImmutableSet<ResourceLocation>, Instant> triple = Triple.of(level, roleSet, voteTime);

        if (level > 0) {
            this.votes.put(uuid, triple);
            this.emitVoteAnnouncement(uuid, triple);
        } else {
            this.votes.remove(uuid);
            this.emitVoteAnnouncement(uuid, triple);
        }
    }

    public void clear() {
        Map<UUID, Triple<Integer, ImmutableSet<ResourceLocation>, Instant>> votes = Map.copyOf(this.votes);
        this.votes.clear();
        votes.forEach(this::emitVoteAnnouncement);
    }

    @Override
    public CompoundTag serializeNBT() {
        ListTag nbt = new ListTag();
        for (Map.Entry<UUID, Triple<Integer, ImmutableSet<ResourceLocation>, Instant>> entry : this.votes.entrySet()) {
            CompoundTag child = new CompoundTag();
            child.putUUID("UUID", entry.getKey());
            child.putInt("Level", entry.getValue().getLeft());
            child.put("VoteRoles", Util.make(new ListTag(), roles -> {
                for (ResourceLocation r : entry.getValue().getMiddle()) {
                    roles.add(StringTag.valueOf(r.toString()));
                }
            }));
            child.putLong("VoteTime", entry.getValue().getRight().toEpochMilli());
            nbt.add(child);
        }
        CompoundTag result = new CompoundTag();
        result.put("Votes", nbt);
        if (this.enabled != null) {
            result.putBoolean("Disabled", !this.enabled);
        }
        result.putUUID("ArtifactUUID", this.key.artifactID());
        result.putString("Category", this.key.categoryID().toString());
        return result;
    }

    @Override
    public void deserializeNBT(CompoundTag source) {
        Preconditions.checkArgument(deserializeKey(source).equals(this.key), "invalid artifact or category");
        Boolean enabled = source.contains("Disabled", Tag.TAG_ANY_NUMERIC) ? !source.getBoolean("Disabled") : null;
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.emitVoteDisabledAnnouncement(this.enabled);
        }
        this.votes.clear();
        ListTag nbt = source.getList("Votes", Tag.TAG_COMPOUND);
        for (int i = 0, size = nbt.size(); i < size; ++i) {
            CompoundTag child = nbt.getCompound(i);
            int level = Mth.clamp(child.getInt("Level"), 1, 5);
            ImmutableSet.Builder<ResourceLocation> roleBuilder = ImmutableSet.builder();
            for (Tag roleNBT : child.getList("VoteRoles", Tag.TAG_STRING)) {
                roleBuilder.add(new ResourceLocation(roleNBT.getAsString()));
            }
            ImmutableSet<ResourceLocation> roles = roleBuilder.build();
            Instant voteTime = DEFAULT_VOTE_TIME;
            if (child.contains("VoteTime", Tag.TAG_LONG)) {
                voteTime = Instant.ofEpochMilli(child.getLong("VoteTime"));
            }
            UUID uuid = child.getUUID("UUID");
            if (!this.votes.containsKey(uuid) || this.votes.get(uuid).getRight().isBefore(voteTime)) {
                this.votes.put(uuid, Triple.of(level, roles, voteTime));
                this.emitVoteAnnouncement(uuid, Triple.of(level, roles, voteTime));
            }
        }
    }

    public SortedMap<String, Stats> buildStatsMap() {
        if (this.delayedCachedScores.isEmpty()) {
            // noinspection UnstableApiUsage
            ImmutableIntArray zeros = ImmutableIntArray.of(0, 0, 0, 0, 0, 0);
            ListMultimap<String, Stats> results = ArrayListMultimap.create();
            for (ResourceLocation location : VoteRoleHandler.getIds()) {
                VoteRole role = VoteRoleHandler.getRole(location).orElseThrow(NullPointerException::new);
                // noinspection UnstableApiUsage
                ImmutableIntArray countsByLevel = this.delayedStatsMap.computeIfAbsent(location, k -> zeros);
                for (VoteRole.Participation participation : role.categories.get(this.key.categoryID())) {
                    float weight = participation.weight, finalScore = Float.NaN;
                    // noinspection UnstableApiUsage
                    int count = -countsByLevel.get(0), truncation = participation.truncation;
                    int effectiveCount = Math.max(0, count - truncation * 2);
                    if (effectiveCount > 0) {
                        // noinspection UnstableApiUsage
                        int[] counts = countsByLevel.toArray();
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
                        float sum = 2F * counts[1] + 4F * counts[2] + 6F * counts[3] + 8F * counts[4] + 10F * counts[5];
                        finalScore = sum / effectiveCount;
                    }
                    results.put(participation.subgroup, new Stats(weight, finalScore, effectiveCount, countsByLevel));
                }
            }
            for (Map.Entry<String, Collection<Stats>> entry : results.asMap().entrySet()) {
                Stats stats = Stats.combine(entry.getValue(), s -> s.getEffectiveCount() * s.getWeight());
                this.delayedCachedScores.put(entry.getKey(), stats);
            }
        }
        return this.delayedCachedScores;
    }

    public float getFinalScore() {
        Collection<Stats> scores = this.buildStatsMap().values();
        return VoteList.Stats.combine(scores, VoteList.Stats::getWeight).getFinalScore(6.0F);
    }

    public static VoteDisabledKey deserializeKey(CompoundTag source) {
        return new VoteDisabledKey(source.getUUID("ArtifactUUID"), new ResourceLocation(source.getString("Category")));
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Stats {
        private final float weight;
        private final float finalScore;
        private final int effectiveCount;
        private final ImmutableIntArray voteCountsByLevel;

        public Stats(float weight, float finalScore, int effectiveCount, ImmutableIntArray voteCountsByLevel) {
            this.weight = weight;
            this.finalScore = finalScore;
            this.effectiveCount = effectiveCount;
            this.voteCountsByLevel = voteCountsByLevel;
            // noinspection UnstableApiUsage
            Preconditions.checkArgument(voteCountsByLevel.length() == 1 + 5);
            // noinspection UnstableApiUsage
            Preconditions.checkArgument(voteCountsByLevel.stream().sum() == 0);
            Preconditions.checkArgument(effectiveCount > 0 || Float.isNaN(finalScore));
        }

        public int getEffectiveCount() {
            return this.effectiveCount;
        }

        public int getVoteCount() {
            // noinspection UnstableApiUsage
            return -this.voteCountsByLevel.get(0);
        }

        public int getVoteCount(int level) {
            Preconditions.checkArgument(level > 0 && level <= 5);
            // noinspection UnstableApiUsage
            return this.voteCountsByLevel.get(level);
        }

        public int[] getVoteCountArray() {
            // noinspection UnstableApiUsage
            return this.voteCountsByLevel.toArray();
        }

        public float getWeight() {
            return this.weight;
        }

        public float getFinalScore(float defaultScore) {
            return Float.isNaN(this.finalScore) ? defaultScore : this.finalScore;
        }

        public static Stats combine(Iterable<? extends Stats> iterable, ScoreWeightFunction scoreWeightFunction) {
            int effectiveCountSum = 0;
            int[] countsByLevel = new int[1 + 5];
            float weightSum = 0F, scoreDivisor = 0F, scoreSum = 0F;
            for (Stats stats : iterable) {
                weightSum += stats.weight;
                effectiveCountSum += stats.effectiveCount;
                // noinspection UnstableApiUsage
                countsByLevel[0] += stats.voteCountsByLevel.get(0);
                // noinspection UnstableApiUsage
                countsByLevel[1] += stats.voteCountsByLevel.get(1);
                // noinspection UnstableApiUsage
                countsByLevel[2] += stats.voteCountsByLevel.get(2);
                // noinspection UnstableApiUsage
                countsByLevel[3] += stats.voteCountsByLevel.get(3);
                // noinspection UnstableApiUsage
                countsByLevel[4] += stats.voteCountsByLevel.get(4);
                // noinspection UnstableApiUsage
                countsByLevel[5] += stats.voteCountsByLevel.get(5);
                if (!Float.isNaN(stats.finalScore)) {
                    scoreDivisor += scoreWeightFunction.calculateWeight(stats);
                    scoreSum += scoreWeightFunction.calculateWeight(stats) * stats.finalScore;
                }
            }
            float finalScore = scoreDivisor > 0F && effectiveCountSum > 0F ? scoreSum / scoreDivisor : Float.NaN;
            // noinspection UnstableApiUsage
            return new Stats(weightSum, finalScore, effectiveCountSum, ImmutableIntArray.copyOf(countsByLevel));
        }

        @Override
        public String toString() {
            return "Stats{weight=" + this.weight + ", final=" + this.finalScore +
                    ", effective=" + this.effectiveCount + ", counts=" + this.voteCountsByLevel + "}";
        }

        @FunctionalInterface
        @MethodsReturnNonnullByDefault
        @ParametersAreNonnullByDefault
        public interface ScoreWeightFunction {
            float calculateWeight(Stats stats);
        }
    }
}
