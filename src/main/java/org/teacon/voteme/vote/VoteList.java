package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
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

    private final Runnable onVoteChange;
    private final Map<ResourceLocation, int[]> countMap;
    private final Map<UUID, Triple<Integer, ResourceLocation, Instant>> votes;

    public VoteList(Runnable onChange) {
        this.votes = new HashMap<>();
        this.countMap = new HashMap<>();
        this.onVoteChange = onChange;
    }

    public int get(ServerPlayerEntity player) {
        return this.get(player.getUniqueID());
    }

    public int get(UUID uuid) {
        return this.votes.containsKey(uuid) ? this.votes.get(uuid).getLeft() : 0;
    }

    public void set(ServerPlayerEntity player, int level) {
        Preconditions.checkArgument(level >= 0 && level <= 5);
        VoteRoleHandler.getRole(player).ifPresent(role -> this.set(player.getUniqueID(), level, role, Instant.now()));
    }

    public void set(UUID uuid, int level, ResourceLocation role, Instant voteTime) {
        if (level > 0) {
            Triple<Integer, ResourceLocation, Instant> old = this.votes.put(uuid, Triple.of(level, role, voteTime));
            int[] newCountsByLevel = this.countMap.computeIfAbsent(role, k -> new int[1 + 5]);
            ++newCountsByLevel[level];
            --newCountsByLevel[0];
            if (old != null) {
                int[] oldCountsByLevel = Objects.requireNonNull(this.countMap.get(old.getMiddle()));
                --oldCountsByLevel[old.getLeft()];
                ++oldCountsByLevel[0];
            }
            this.onVoteChange.run();
        } else {
            Triple<Integer, ResourceLocation, Instant> old = this.votes.remove(uuid);
            if (old != null) {
                int[] oldCountsByLevel = Objects.requireNonNull(this.countMap.get(old.getMiddle()));
                --oldCountsByLevel[old.getLeft()];
                ++oldCountsByLevel[0];
                this.onVoteChange.run();
            }
        }
    }

    public SortedMap<ResourceLocation, Stats> buildFinalScore(ResourceLocation category) {
        ImmutableSortedMap.Builder<ResourceLocation, Stats> resultBuilder = ImmutableSortedMap.naturalOrder();
        VoteRoleHandler.getIds().forEach(location -> {
            VoteRole role = VoteRoleHandler.getRole(location).orElseThrow(NullPointerException::new);
            VoteRole.Category roleCategory = role.categories.get(category);
            if (roleCategory != null) {
                int[] countsByLevel = this.countMap.computeIfAbsent(location, k -> new int[1 + 5]);
                float weight = roleCategory.weight, finalScore = 6F;
                int count = -countsByLevel[0], truncation = roleCategory.truncation;
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
                resultBuilder.put(location, new Stats(weight, finalScore, effectiveCount, countsByLevel));
            }
        });
        return resultBuilder.build();
    }

    @Override
    public CompoundNBT serializeNBT() {
        ListNBT nbt = new ListNBT();
        for (Map.Entry<UUID, Triple<Integer, ResourceLocation, Instant>> entry : this.votes.entrySet()) {
            CompoundNBT child = new CompoundNBT();
            child.putUniqueId("UUID", entry.getKey());
            child.putInt("Level", entry.getValue().getLeft());
            child.putString("VoteRole", entry.getValue().getMiddle().toString());
            child.putLong("VoteTime", entry.getValue().getRight().toEpochMilli());
            nbt.add(child);
        }
        CompoundNBT result = new CompoundNBT();
        result.put("Votes", nbt);
        return result;
    }

    @Override
    public void deserializeNBT(CompoundNBT compound) {
        this.votes.clear();
        ListNBT nbt = compound.getList("Votes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = nbt.size(); i < size; ++i) {
            CompoundNBT child = nbt.getCompound(i);
            int level = MathHelper.clamp(child.getInt("Level"), 1, 5);
            ResourceLocation role = new ResourceLocation(child.getString("VoteRole"));
            Instant voteTime = child.contains("VoteTime", Constants.NBT.TAG_LONG) ? Instant.ofEpochMilli(child.getLong("VoteTime")) : DEFAULT_VOTE_TIME;
            int[] countsByLevel = this.countMap.computeIfAbsent(role, k -> new int[1 + 5]);
            this.votes.put(child.getUniqueId("UUID"), Triple.of(level, role, voteTime));
            ++countsByLevel[level];
            --countsByLevel[0];
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

        public float getFinalScore() {
            return this.finalScore;
        }

        public static Stats combine(Iterable<Stats> iterable) {
            int effectiveCount = 0;
            float weightedCount = 0F;
            float weightedFinalScore = 0F;
            int[] countsByLevel = new int[1 + 5];
            for (Stats stats : iterable) {
                effectiveCount += stats.effectiveCount;
                countsByLevel[0] += stats.countsByLevel[0];
                countsByLevel[1] += stats.countsByLevel[1];
                countsByLevel[2] += stats.countsByLevel[2];
                countsByLevel[3] += stats.countsByLevel[3];
                countsByLevel[4] += stats.countsByLevel[4];
                countsByLevel[5] += stats.countsByLevel[5];
                weightedCount += stats.weight * stats.effectiveCount;
                weightedFinalScore += stats.weight * stats.effectiveCount * stats.finalScore;
            }
            float finalScore = weightedCount > 0F ? weightedFinalScore / weightedCount : 6F;
            float weight = effectiveCount > 0 ? weightedCount / effectiveCount : 1F;
            return new Stats(weight, finalScore, effectiveCount, countsByLevel);
        }
    }
}
