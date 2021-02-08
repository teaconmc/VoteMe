package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.tuple.Triple;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteList implements INBTSerializable<CompoundNBT> {
    public static final Instant DEFAULT_VOTE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME.parse("Sat, 9 Jan 2021 02:00:00 +0800", Instant::from);

    private boolean disabled;
    private final Runnable onVoteChange;
    private final Map<ResourceLocation, int[]> countMap;
    private final Map<UUID, Triple<Integer, ImmutableSet<ResourceLocation>, Instant>> votes;

    public VoteList(Runnable onChange) {
        this.disabled = false;
        this.votes = new HashMap<>();
        this.countMap = new HashMap<>();
        this.onVoteChange = onChange;
    }

    public boolean isEnabled() {
        return !this.disabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.disabled == enabled) {
            this.disabled = !enabled;
            this.onVoteChange.run();
        }
    }

    public int get(ServerPlayerEntity player) {
        return this.get(player.getUniqueID());
    }

    public int get(UUID uuid) {
        return this.votes.containsKey(uuid) ? this.votes.get(uuid).getLeft() : 0;
    }

    public void set(ServerPlayerEntity player, int level) {
        Preconditions.checkArgument(level >= 0 && level <= 5);
        this.set(player.getUniqueID(), level, VoteRoleHandler.getRole(player), Instant.now());
    }

    public void set(UUID uuid, int level, Collection<? extends ResourceLocation> roles, Instant voteTime) {
        if (level > 0) {
            Triple<Integer, ImmutableSet<ResourceLocation>, Instant> old = this.votes.put(uuid, Triple.of(level, ImmutableSet.copyOf(roles), voteTime));
            for (ResourceLocation role : roles) {
                int[] newCountsByLevel = this.countMap.computeIfAbsent(role, k -> new int[1 + 5]);
                ++newCountsByLevel[level];
                --newCountsByLevel[0];
            }
            if (old != null) {
                for (ResourceLocation role : old.getMiddle()) {
                    int[] oldCountsByLevel = Objects.requireNonNull(this.countMap.get(role));
                    --oldCountsByLevel[old.getLeft()];
                    ++oldCountsByLevel[0];
                }
            }
            this.onVoteChange.run();
        } else {
            Triple<Integer, ImmutableSet<ResourceLocation>, Instant> old = this.votes.remove(uuid);
            if (old != null) {
                for (ResourceLocation role : old.getMiddle()) {
                    int[] oldCountsByLevel = Objects.requireNonNull(this.countMap.get(role));
                    --oldCountsByLevel[old.getLeft()];
                    ++oldCountsByLevel[0];
                    this.onVoteChange.run();
                }
            }
        }
    }

    public void clear() {
        this.votes.clear();
        this.countMap.clear();
        this.onVoteChange.run();
    }

    public SortedMap<String, Stats> buildFinalScore(ResourceLocation category) {
        ListMultimap<String, Stats> results = ArrayListMultimap.create();
        VoteRoleHandler.getIds().forEach(location -> {
            VoteRole role = VoteRoleHandler.getRole(location).orElseThrow(NullPointerException::new);
            int[] countsByLevel = this.countMap.computeIfAbsent(location, k -> new int[1 + 5]);
            for (VoteRole.Participation participation : role.categories.get(category)) {
                float weight = participation.weight, finalScore = Float.NaN;
                int count = -countsByLevel[0], truncation = participation.truncation;
                int effectiveCount = Math.max(0, count - truncation * 2);
                if (effectiveCount > 0) {
                    int[] counts = countsByLevel.clone();
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
        });
        ImmutableSortedMap.Builder<String, Stats> result = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Collection<Stats>> entry : results.asMap().entrySet()) {
            Stats stats = Stats.combine(entry.getValue(), s -> s.getEffectiveCount() * s.getWeight());
            result.put(entry.getKey(), stats);
        }
        return result.build();
    }

    @Override
    public CompoundNBT serializeNBT() {
        ListNBT nbt = new ListNBT();
        for (Map.Entry<UUID, Triple<Integer, ImmutableSet<ResourceLocation>, Instant>> entry : this.votes.entrySet()) {
            CompoundNBT child = new CompoundNBT();
            child.putUniqueId("UUID", entry.getKey());
            child.putInt("Level", entry.getValue().getLeft());
            child.put("VoteRoles", Util.make(new ListNBT(), roles -> {
                for (ResourceLocation r : entry.getValue().getMiddle()) {
                    roles.add(StringNBT.valueOf(r.toString()));
                }
            }));
            child.putLong("VoteTime", entry.getValue().getRight().toEpochMilli());
            nbt.add(child);
        }
        CompoundNBT result = new CompoundNBT();
        result.putBoolean("Disabled", this.disabled);
        result.put("Votes", nbt);
        return result;
    }

    @Override
    public void deserializeNBT(CompoundNBT compound) {
        this.votes.clear();
        this.countMap.clear();
        this.disabled = compound.getBoolean("Disabled");
        ListNBT nbt = compound.getList("Votes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = nbt.size(); i < size; ++i) {
            CompoundNBT child = nbt.getCompound(i);
            int level = MathHelper.clamp(child.getInt("Level"), 1, 5);
            ImmutableSet.Builder<ResourceLocation> roleBuilder = ImmutableSet.builder();
            for (INBT roleNBT : child.getList("VoteRoles", Constants.NBT.TAG_STRING)) {
                roleBuilder.add(new ResourceLocation(roleNBT.getString()));
            }
            ImmutableSet<ResourceLocation> roles = roleBuilder.build();
            for (ResourceLocation role : roles) {
                int[] countsByLevel = this.countMap.computeIfAbsent(role, k -> new int[1 + 5]);
                ++countsByLevel[level];
                --countsByLevel[0];
            }
            Instant voteTime = DEFAULT_VOTE_TIME;
            if (child.contains("VoteTime", Constants.NBT.TAG_LONG)) {
                voteTime = Instant.ofEpochMilli(child.getLong("VoteTime"));
            }
            this.votes.put(child.getUniqueId("UUID"), Triple.of(level, roles, voteTime));
        }
        this.onVoteChange.run();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public static final class Stats {
        private final float weight;
        private final float finalScore;
        private final int effectiveCount;
        private final int[] countsByLevel;

        public Stats(float weight, float finalScore, int effectiveCount, int[] countsByLevel) {
            this.weight = weight;
            this.finalScore = finalScore;
            this.effectiveCount = effectiveCount;
            this.countsByLevel = countsByLevel.clone();
            Preconditions.checkArgument(countsByLevel.length == 1 + 5);
            Preconditions.checkArgument(IntStream.of(countsByLevel).sum() == 0);
            Preconditions.checkArgument(effectiveCount > 0 || Float.isNaN(finalScore));
        }

        public int getEffectiveCount() {
            return this.effectiveCount;
        }

        public int getVoteCount() {
            return -this.countsByLevel[0];
        }

        public int getVoteCount(int level) {
            Preconditions.checkArgument(level > 0 && level <= 5);
            return this.countsByLevel[level];
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
                countsByLevel[0] += stats.countsByLevel[0];
                countsByLevel[1] += stats.countsByLevel[1];
                countsByLevel[2] += stats.countsByLevel[2];
                countsByLevel[3] += stats.countsByLevel[3];
                countsByLevel[4] += stats.countsByLevel[4];
                countsByLevel[5] += stats.countsByLevel[5];
                if (!Float.isNaN(stats.finalScore)) {
                    scoreDivisor += scoreWeightFunction.calculateWeight(stats);
                    scoreSum += scoreWeightFunction.calculateWeight(stats) * stats.finalScore;
                }
            }
            float finalScore = scoreDivisor > 0F && effectiveCountSum > 0F ? scoreSum / scoreDivisor : Float.NaN;
            return new Stats(weightSum, finalScore, effectiveCountSum, countsByLevel);
        }

        @FunctionalInterface
        @MethodsReturnNonnullByDefault
        @ParametersAreNonnullByDefault
        public interface ScoreWeightFunction {
            float calculateWeight(Stats stats);
        }
    }
}
