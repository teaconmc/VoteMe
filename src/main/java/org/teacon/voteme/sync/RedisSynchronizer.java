package org.teacon.voteme.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.ImmutableIntArray;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.vote.VoteArtifactNames;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.teacon.voteme.sync.AnnouncementSerializer.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class RedisSynchronizer implements VoteSynchronizer {
    private static final String SYNC = "voteme:sync";

    private boolean notFirstTime = false;
    private List<Announcement> queued = new ArrayList<>();
    private final StatefulRedisPubSubConnection<String, String> connectionPubSub;
    private final StatefulRedisConnection<String, String> connection;
    private final MinecraftServer server;
    private final RedisClient client;

    private final Deque<Vote> queuedVotes = new ConcurrentLinkedDeque<>();

    public RedisSynchronizer(MinecraftServer server, String uri) {
        this.server = server;
        this.server.addTickable(this::tickVoteUpdates);

        this.client = RedisClient.create(uri);

        this.connectionPubSub = this.client.connectPubSub();
        this.connectionPubSub.addListener(new PubSubListener());
        this.connectionPubSub.sync().subscribe(SYNC);

        this.connection = this.client.connect();
    }

    private void tickVoteUpdates() {
        StatsAccumulator accumulator = new StatsAccumulator();
        for (int i = 0, maximum = 1000; i < maximum; ++i) {
            Vote vote = this.queuedVotes.poll();
            if (vote == null) {
                break; // the queue is empty
            }
            accumulator.accumulate(vote);
        }
        List<Vote> watchedVotes = new ArrayList<>();
        accumulator.buildAffectedVotes(watchedVotes);
        if (watchedVotes.size() > 0) {
            String[] watchedKeys = watchedVotes.stream().map(v -> toRedisKey(v.key())).toArray(String[]::new);
            VoteMe.LOGGER.info("Watch {} key(s) for updating vote related data in redis.", watchedKeys.length);
            List<Vote> affectedVotes = new ArrayList<>();
            // noinspection UnstableApiUsage
            Map<VoteStatsKey, ImmutableIntArray> affectedStatsMap = new HashMap<>();
            RedisAsyncCommands<String, String> async = this.connection.async();
            AtomicBoolean succeed = new AtomicBoolean();
            async.watch(watchedKeys)
                    .thenComposeAsync(ok -> {
                        List<CompletableFuture<?>> futures = new ArrayList<>(watchedVotes.size());
                        // get original votes in redis server
                        for (Vote vote : watchedVotes) {
                            CompletableFuture<Vote> future = this.dispatch(vote.key());
                            futures.add(future.thenAcceptAsync(accumulator::subtract, this.server));
                        }
                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    }, this.server)
                    .thenRunAsync(() -> {
                        // calculate affected votes and stats map
                        accumulator.buildAffectedVotes(affectedVotes);
                        accumulator.buildStatsMap(affectedStatsMap);
                    }, this.server)
                    .thenComposeAsync(v -> {
                        int capacity = 2 + 2 * affectedVotes.size() + 6 * affectedStatsMap.size();
                        List<CompletableFuture<?>> futures = new ArrayList<>(capacity);
                        futures.add(async.multi().toCompletableFuture());
                        // submit affected votes to redis server
                        for (Vote vote : watchedVotes) {
                            String key = toRedisKey(vote.key());
                            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
                            builder.put("level", Integer.toString(vote.level()));
                            Iterator<ResourceLocation> roleIterator = vote.roles().iterator();
                            for (int i = 0; roleIterator.hasNext(); ++i) {
                                builder.put("role:" + i, roleIterator.next().toString());
                            }
                            builder.put("time", vote.time().truncatedTo(ChronoUnit.MILLIS).toString());
                            futures.add(async.del(key).toCompletableFuture());
                            futures.add(async.hset(key, builder.build()).toCompletableFuture());
                        }
                        // submit affected stats
                        // noinspection UnstableApiUsage
                        for (Map.Entry<VoteStatsKey, ImmutableIntArray> entry : affectedStatsMap.entrySet()) {
                            String key = toRedisKey(entry.getKey());
                            // noinspection UnstableApiUsage
                            ImmutableIntArray counts = entry.getValue();
                            // noinspection UnstableApiUsage
                            int bound = counts.length();
                            for (int i = 0; i < bound; ++i) {
                                // noinspection UnstableApiUsage
                                futures.add(async.hincrby(key, "level:" + i, counts.get(i)).toCompletableFuture());
                            }
                        }
                        futures.add(async.exec().thenAccept(r -> succeed.set(!r.wasDiscarded())).toCompletableFuture());
                        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    }, this.server)
                    .thenRunAsync(() -> {
                        if (succeed.getAcquire()) {
                            // the transaction succeeded, broadcast vote changes and stat changes
                            for (Vote affectedVote : affectedVotes) {
                                this.connection.async().publish(SYNC, serialize(affectedVote).orElseThrow().toString());
                            }
                            // noinspection UnstableApiUsage
                            for (Map.Entry<VoteStatsKey, ImmutableIntArray> entry : affectedStatsMap.entrySet()) {
                                VoteStats voteStats = new VoteStats(entry.getKey(), entry.getValue());
                                this.connection.async().publish(SYNC, serialize(voteStats).orElseThrow().toString());
                            }
                        } else {
                            // the transaction failed because the watched keys changed, try again in the next tick
                            for (int i = watchedVotes.size() - 1; i >= 0; --i) {
                                this.queuedVotes.offerFirst(watchedVotes.get(i));
                            }
                        }
                    }, this.server);
        }
    }

    private static String toRedisKey(AnnounceKey<?> announceKey) {
        if (announceKey instanceof ArtifactKey key) {
            return ARTIFACT + ":" + key.artifactID();
        }
        if (announceKey instanceof CommentsKey key) {
            return COMMENTS + ":" + key.artifactID() + ":" + key.voterID();
        }
        if (announceKey instanceof VoteKey key) {
            return VOTE + ":" + key.artifactID() + ":" + key.categoryID() + ":" + key.voterID();
        }
        if (announceKey instanceof VoteDisabledKey key) {
            return VOTE_DISABLED + ":" + key.artifactID() + ":" + key.categoryID();
        }
        if (announceKey instanceof VoteStatsKey key) {
            return VOTE_STATS + ":" + key.artifactID() + ":" + key.categoryID() + ":" + key.roleID();
        }
        throw new IllegalArgumentException("unsupported announce key");
    }

    private static AnnounceKey<?> fromRedisKey(String redisKey) {
        String[] redisKeyParts = redisKey.split(":", 3);
        checkArgument(redisKeyParts.length == 3, "redis key needs at least two colons");
        String announceKey = redisKeyParts[0] + ":" + redisKeyParts[1];
        switch (announceKey) {
            case ARTIFACT -> {
                UUID artifactID = UUID.fromString(redisKeyParts[2]);
                return new ArtifactKey(artifactID);
            }
            case COMMENTS -> {
                String[] parts = redisKeyParts[2].split(":");
                checkArgument(parts.length == 2, "invalid message");
                UUID artifactID = UUID.fromString(parts[0]);
                UUID voterID = UUID.fromString(parts[1]);
                return new CommentsKey(artifactID, voterID);
            }
            case VOTE -> {
                String[] parts = redisKeyParts[2].split(":");
                checkArgument(parts.length == 4, "invalid message");
                UUID artifactID = UUID.fromString(parts[0]);
                ResourceLocation categoryID = new ResourceLocation(parts[1], parts[2]);
                UUID voterID = UUID.fromString(parts[3]);
                return new VoteKey(artifactID, categoryID, voterID);
            }
            case VOTE_DISABLED -> {
                String[] parts = redisKeyParts[2].split(":");
                checkArgument(parts.length == 3, "invalid message");
                UUID artifactID = UUID.fromString(parts[0]);
                ResourceLocation categoryID = new ResourceLocation(parts[1], parts[2]);
                return new VoteDisabledKey(artifactID, categoryID);
            }
            case VOTE_STATS -> {
                String[] parts = redisKeyParts[2].split(":");
                checkArgument(parts.length == 5, "invalid message");
                UUID artifactID = UUID.fromString(parts[0]);
                ResourceLocation categoryID = new ResourceLocation(parts[1], parts[2]);
                ResourceLocation roleID = new ResourceLocation(parts[3], parts[4]);
                return new VoteStatsKey(artifactID, categoryID, roleID);
            }
            default -> throw new IllegalArgumentException("unsupported announce key: " + announceKey);
        }
    }

    private <T extends Announcement> CompletableFuture<T> dispatch(AnnounceKey<T> announceKey) {
        CompletionStage<T> stage = CompletableFuture.failedStage(new IllegalArgumentException("unsupported announce key"));
        if (announceKey instanceof ArtifactKey key) {
            RedisAsyncCommands<String, String> async = this.connection.async();
            stage = async.hgetall(toRedisKey(key)).thenApplyAsync(map -> {
                String name = map.getOrDefault("name", "");
                Optional<String> optional = Optional.empty();
                if (map.containsKey("alias")) {
                    String alias = map.get("alias");
                    checkArgument(!alias.isEmpty(), "invalid alias format");
                    checkArgument(VoteArtifactNames.trimValidAlias(alias) == alias.length(), "invalid alias format");
                    optional = Optional.of(alias);
                }
                return announceKey.cast(new Artifact(key, name, optional));
            });
        }
        if (announceKey instanceof CommentsKey key) {
            RedisAsyncCommands<String, String> async = RedisSynchronizer.this.connection.async();
            stage = async.lrange(toRedisKey(key), 0, Integer.MAX_VALUE).thenApplyAsync(list -> {
                ImmutableList<String> comments = ImmutableList.copyOf(list);
                return announceKey.cast(new Comments(key, comments));
            });
        }
        if (announceKey instanceof VoteKey key) {
            RedisAsyncCommands<String, String> async = RedisSynchronizer.this.connection.async();
            stage = async.hgetall(toRedisKey(key)).thenApplyAsync(map -> {
                int level = Integer.parseInt(map.getOrDefault("level", "0"));
                checkArgument(level >= 0 && level <= 5, "level out of range from 1 to 5");
                int expectedSize = Math.max(0, map.size() - 2);
                // noinspection UnstableApiUsage
                ImmutableSet.Builder<ResourceLocation> roles = ImmutableSet.builderWithExpectedSize(expectedSize);
                for (int i = 0; map.containsKey("role:" + i); ++i) {
                    ResourceLocation role = new ResourceLocation(map.get("role:" + i));
                    roles.add(role);
                }
                Instant time = Instant.parse(checkNotNull(map.getOrDefault("time", Instant.EPOCH.toString())));
                return announceKey.cast(new Vote(key, level, roles.build(), time));
            });
        }
        if (announceKey instanceof VoteDisabledKey key) {
            RedisAsyncCommands<String, String> async = RedisSynchronizer.this.connection.async();
            stage = async.get(toRedisKey(key)).thenApplyAsync(string -> {
                Optional<Boolean> disabled = switch (String.valueOf(string)) {
                    case "null" -> Optional.empty();
                    case "true" -> Optional.of(Boolean.TRUE);
                    case "false" -> Optional.of(Boolean.FALSE);
                    default -> throw new IllegalArgumentException("unexpected value: " + string);
                };
                return announceKey.cast(new VoteDisabled(key, disabled));
            });
        }
        if (announceKey instanceof VoteStatsKey key) {
            RedisAsyncCommands<String, String> async = RedisSynchronizer.this.connection.async();
            stage = async.hgetall(toRedisKey(key)).thenApplyAsync(map -> {
                int count0 = Integer.parseInt(map.getOrDefault("level:0", "0"));
                int count1 = Integer.parseInt(map.getOrDefault("level:1", "0"));
                int count2 = Integer.parseInt(map.getOrDefault("level:2", "0"));
                int count3 = Integer.parseInt(map.getOrDefault("level:3", "0"));
                int count4 = Integer.parseInt(map.getOrDefault("level:4", "0"));
                int count5 = Integer.parseInt(map.getOrDefault("level:5", "0"));
                // noinspection UnstableApiUsage
                ImmutableIntArray counts = ImmutableIntArray.of(count0, count1, count2, count3, count4, count5);
                return announceKey.cast(new VoteStats(key, counts));
            });
        }
        return stage.toCompletableFuture();
    }

    @Override
    public void publish(Collection<? extends Announcement> announcements) {
        checkArgument(this.server.isSameThread(), "server thread");
        VoteMe.LOGGER.info("Publishing {} announcement(s) to redis.", announcements.size());
        for (Announcement announcement : announcements) {
            if (announcement instanceof Artifact artifact) {
                RedisAsyncCommands<String, String> async = this.connection.async();
                if (artifact.name().isEmpty()) {
                    async.del(toRedisKey(artifact.key()))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(artifact).orElseThrow().toString()));
                } else {
                    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
                    builder.put("name", artifact.name());
                    artifact.alias().ifPresent(alias -> builder.put("alias", alias));
                    String key = toRedisKey(artifact.key());
                    CompletableFuture.allOf(Stream
                                    .of(async.multi(), async.del(key), async.hset(key, builder.build()), async.exec())
                                    .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(artifact).orElseThrow().toString()));
                }
                continue;
            }
            if (announcement instanceof Comments comments) {
                RedisAsyncCommands<String, String> async = this.connection.async();
                if (comments.comments().isEmpty()) {
                    async.del(toRedisKey(comments.key()))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(comments).orElseThrow().toString()));
                } else {
                    String key = toRedisKey(comments.key());
                    String[] list = comments.comments().toArray(new String[0]);
                    CompletableFuture.allOf(Stream
                                    .of(async.multi(), async.del(key), async.lpush(key, list), async.exec())
                                    .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(comments).orElseThrow().toString()));
                }
                continue;
            }
            if (announcement instanceof Vote vote) {
                this.queuedVotes.add(vote);
                continue;
            }
            if (announcement instanceof VoteDisabled voteDisabled) {
                RedisAsyncCommands<String, String> async = this.connection.async();
                if (voteDisabled.disabled().isEmpty()) {
                    async.del(toRedisKey(voteDisabled.key()))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(voteDisabled).orElseThrow().toString()));
                } else {
                    String key = toRedisKey(voteDisabled.key());
                    async.set(key, Boolean.toString(voteDisabled.disabled().get()))
                            .thenComposeAsync(res -> this.connection.async()
                                    .publish(SYNC, serialize(voteDisabled).orElseThrow().toString()));
                }
                continue;
            }
            throw new IllegalArgumentException("unsupported outbound announcement: " + announcement.key());
        }
    }

    private void scan(ScanCursor cursor, ScanArgs args) {
        RedisAsyncCommands<String, String> async = this.connection.async();
        async.scan(cursor, args).thenAcceptAsync(newCursor -> {
            for (String key : newCursor.getKeys()) {
                this.dispatch(fromRedisKey(key)).thenAcceptAsync(this.queued::add, this.server);
            }
            if (!newCursor.isFinished()) {
                this.scan(newCursor, args);
            }
        }, this.server);
    }

    @Override
    public Collection<? extends Announcement> dequeue() {
        checkArgument(this.server.isSameThread(), "server thread");
        if (!this.notFirstTime) {
            int maximum = 1000;
            this.notFirstTime = true;
            this.scan(ScanCursor.of("0"), new ScanArgs().match(ARTIFACT + ":*").limit(maximum));
            this.scan(ScanCursor.of("0"), new ScanArgs().match(COMMENTS + ":*").limit(maximum));
            this.scan(ScanCursor.of("0"), new ScanArgs().match(VOTE + ":*").limit(maximum));
            this.scan(ScanCursor.of("0"), new ScanArgs().match(VOTE_DISABLED + ":*").limit(maximum));
            this.scan(ScanCursor.of("0"), new ScanArgs().match(VOTE_STATS + ":*").limit(maximum));
        }
        if (this.queued.size() > 0) {
            VoteMe.LOGGER.info("Retrieving {} announcement(s) from redis.", this.queued.size());
            Collection<? extends Announcement> result = this.queued;
            this.queued = new ArrayList<>();
            return result;
        }
        return new ArrayList<>();
    }

    @Override
    public void close() {
        checkArgument(this.server.isSameThread(), "server thread");
        this.connectionPubSub.close();
        this.connection.close();
        this.client.close();
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    private class PubSubListener extends RedisPubSubAdapter<String, String> {
        private static void tryParse(String message, Consumer<? super CompoundTag> consumer) {
            try {
                consumer.accept(TagParser.parseTag(message));
            } catch (CommandSyntaxException e) {
                VoteMe.LOGGER.warn("Failed to parse " + message + " as a tag from redis channel", e);
            }
        }

        @Override
        public void message(String channel, String message) {
            if (SYNC.equals(channel)) {
                tryParse(message, nbt -> deserialize(nbt).ifPresent(RedisSynchronizer.this.queued::add));
            } else {
                VoteMe.LOGGER.warn("Unrecognized message from redis channel {}", channel);
            }
        }
    }
}
