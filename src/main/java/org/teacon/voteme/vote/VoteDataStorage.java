package org.teacon.voteme.vote;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.sync.DetachedSynchronizer;
import org.teacon.voteme.sync.RedisSynchronizer;
import org.teacon.voteme.sync.VoteSynchronizer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteDataStorage extends SavedData implements Closeable {
    private int nextIndex;

    private final Int2ObjectMap<VoteList> voteLists;
    private final Table<UUID, ResourceLocation, Integer> voteListIDs;
    private final Table<UUID, UUID, ImmutableList<String>> voteComments;

    private final VoteSynchronizer sync;

    public static VoteDataStorage get(MinecraftServer server) {
        DimensionDataStorage manager = server.overworld().getDataStorage();
        return manager.computeIfAbsent(VoteDataStorage::new, VoteDataStorage::new, "vote_lists");
    }

    public VoteDataStorage() {
        this.nextIndex = 1;
        this.voteLists = new Int2ObjectRBTreeMap<>();
        this.voteListIDs = TreeBasedTable.create();
        this.voteComments = HashBasedTable.create();
        this.sync = this.loadSynchronizer();
    }

    public VoteDataStorage(CompoundTag nbt) {
        this.nextIndex = 1;
        this.voteLists = new Int2ObjectRBTreeMap<>();
        this.voteListIDs = TreeBasedTable.create();
        this.voteComments = HashBasedTable.create();
        this.sync = this.loadSynchronizer();
        this.load(nbt);
    }

    private VoteSynchronizer loadSynchronizer() {
        String uri = VoteMe.CONFIG.REDIS_ATTACH_URI.get();
        if (uri.length() > 0) {
            return new RedisSynchronizer(ServerLifecycleHooks.getCurrentServer(), uri);
        }
        return new DetachedSynchronizer(ServerLifecycleHooks.getCurrentServer());
    }

    private void tick() {
        // upload announcements
        Collection<VoteSynchronizer.Announcement> toUpload = new ArrayList<>();
        this.voteLists.values().forEach(v -> v.dequeue(toUpload));
        VoteArtifactNames.dequeue(toUpload);
        if (!toUpload.isEmpty()) {
            toUpload.forEach(this.sync::publish);
            this.setDirty();
        }

        // download announcements
        Collection<VoteSynchronizer.Announcement> toDownload = new ArrayList<>();
        this.sync.dequeue(toDownload);
        toDownload.forEach(elem -> {
            if (elem instanceof VoteSynchronizer.Artifact artifact) {
                VoteArtifactNames.publish(artifact);
                this.setDirty();
                return;
            }
            if (elem instanceof VoteSynchronizer.Comments comments) {
                this.handleCommentsAnnouncement(comments);
                this.setDirty();
                return;
            }
            if (elem instanceof VoteSynchronizer.VoteDisabled voteDisabled) {
                int id = this.getIdOrCreate(voteDisabled.key().artifactID(), voteDisabled.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(voteDisabled));
                this.setDirty();
                return;
            }
            if (elem instanceof VoteSynchronizer.Vote vote) {
                int id = this.getIdOrCreate(vote.key().artifactID(), vote.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(vote));
                this.setDirty();
                return;
            }
            if (elem instanceof VoteSynchronizer.VoteStats voteStats) {
                int id = this.getIdOrCreate(voteStats.key().artifactID(), voteStats.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(voteStats));
                this.setDirty();
                return;
            }
            throw new IllegalArgumentException("unsupported announcement type: " + elem.getClass());
        });
    }

    private void handleCommentsAnnouncement(VoteSynchronizer.Comments comments) {
        this.voteComments.put(comments.key().artifactID(), comments.key().voterID(), comments.comments());
    }

    private void emitCommentsAnnouncement(UUID artifactID, UUID voterID, ImmutableList<String> comments) {
        VoteSynchronizer.CommentsKey key = new VoteSynchronizer.CommentsKey(artifactID, voterID);
        this.sync.publish(new VoteSynchronizer.Comments(key, comments));
        this.setDirty();
    }

    public boolean hasEnabled(ResourceLocation category) {
        boolean enabledDefault = VoteCategoryHandler.getCategory(category).filter(c -> c.enabledDefault).isPresent();
        return VoteArtifactNames.getArtifacts().stream()
                .map(id -> this.voteLists.get(this.getIdOrCreate(id, category)))
                .anyMatch(votes -> votes.getEnabled().orElse(enabledDefault));
    }

    public int getIdOrCreate(UUID artifactID, ResourceLocation category) {
        Integer oldId = this.voteListIDs.get(artifactID, category);
        if (oldId == null) {
            int id = this.nextIndex++;
            this.voteListIDs.put(artifactID, category, id);
            this.voteLists.put(id, new VoteList(artifactID, category));
            this.setDirty();
            return id;
        }
        return oldId;
    }

    public Optional<VoteList> getVoteList(int id) {
        return Optional.ofNullable(this.voteLists.get(id));
    }

    public static ImmutableList<String> getCommentFor(VoteDataStorage handler, UUID artifactID, UUID voterID) {
        if (handler.voteComments.contains(artifactID, voterID)) {
            return Objects.requireNonNull(handler.voteComments.get(artifactID, voterID));
        }
        return ImmutableList.of();
    }

    public static Map<UUID, ImmutableList<String>> getAllCommentsFor(VoteDataStorage handler, UUID artifactID) {
        return Collections.unmodifiableMap(handler.voteComments.row(artifactID));
    }

    public static void putCommentFor(VoteDataStorage handler, UUID artifactID, UUID voterID, List<String> newComments) {
        if (newComments.isEmpty()) {
            ImmutableList<String> oldComments = handler.voteComments.remove(artifactID, voterID);
            if (!Objects.requireNonNullElse(oldComments, ImmutableList.of()).isEmpty()) {
                handler.emitCommentsAnnouncement(artifactID, voterID, ImmutableList.of());
            }
        } else {
            ImmutableList<String> comments = ImmutableList.copyOf(newComments);
            ImmutableList<String> oldComments = handler.voteComments.put(artifactID, voterID, comments);
            if (!comments.equals(Objects.requireNonNullElse(oldComments, ImmutableList.of()))) {
                handler.emitCommentsAnnouncement(artifactID, voterID, comments);
            }
        }
    }

    public JsonObject toArtifactHTTPJson(UUID artifactID) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", artifactID.toString());
            result.addProperty("name", VoteArtifactNames.getArtifactName(artifactID));
            Optional.of(VoteArtifactNames.getArtifactAlias(artifactID))
                    .filter(s -> !s.isEmpty()).ifPresent(s -> result.addProperty("alias", s));
            Map<Integer, VoteList> voteLists = new LinkedHashMap<>();
            for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                int id = this.getIdOrCreate(artifactID, categoryID);
                Optional<VoteList> entryOptional = this.getVoteList(id);
                boolean enabledDefault = VoteCategoryHandler
                        .getCategory(categoryID).filter(c -> c.enabledDefault).isPresent();
                entryOptional.filter(entry -> entry
                        .getEnabled().orElse(enabledDefault)).ifPresent(entry -> voteLists.put(id, entry));
            }
            result.add("vote_lists", Util.make(new JsonArray(), array -> voteLists.keySet().forEach(array::add)));
            result.add("vote_comments", Util.make(new JsonArray(), array -> {
                for (Map.Entry<UUID, ImmutableList<String>> entry : getAllCommentsFor(this, artifactID).entrySet()) {
                    UUID voterID = entry.getKey();
                    ImmutableList<String> commentsForVoter = entry.getValue();
                    if (!commentsForVoter.isEmpty()) {
                        array.add(Util.make(new JsonObject(), child -> {
                            child.add("votes", Util.make(new JsonObject(), votes -> {
                                List<JsonArray> arrays = IntStream.range(0, 6).mapToObj(i -> new JsonArray()).toList();
                                for (VoteList voteList : voteLists.values()) {
                                    JsonObject object = new JsonObject();
                                    object.add("roles", Util.make(new JsonArray(), roles -> {
                                        for (ResourceLocation roleID : voteList.getRoles(voterID)) {
                                            roles.add(roleID.toString());
                                        }
                                    }));
                                    object.addProperty("category", voteList.getCategoryID().toString());
                                    arrays.get(voteList.get(voterID)).add(object);
                                }
                                for (int i = 1; i <= 5; ++i) {
                                    votes.add(Integer.toString(i), arrays.get(i));
                                }
                            }));
                            child.add("texts", Util.make(new JsonArray(), texts -> commentsForVoter.forEach(texts::add)));
                        }));
                    }
                }
            }));
        });
    }

    public JsonElement toVoteListHTTPJson(UUID artifactID, ResourceLocation categoryID) {
        int id = this.getIdOrCreate(artifactID, categoryID);
        VoteList voteList = this.getVoteList(id).orElseThrow();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", id);
        jsonObject.addProperty("category", categoryID.toString());
        jsonObject.addProperty("artifact", artifactID.toString());

        SortedMap<String, VoteList.Stats> scores = voteList.buildStatsMap();
        VoteList.Stats combined = VoteList.Stats.combine(scores.values(), VoteList.Stats::getWeight);
        jsonObject.add("vote_stats", Util.make(toVoteStatsJson("", combined, combined.getFinalScore(6.0F)), e -> {
            e.getAsJsonObject().add("subgroups", toVoteStatsJson(scores, combined.getFinalScore(6.0F)));
            e.getAsJsonObject().remove("id");
        }));

        return jsonObject;
    }

    private static JsonElement toVoteStatsJson(String subgroup, VoteList.Stats stats, float defaultScore) {
        return Util.make(new JsonObject(), child -> {
            child.addProperty("id", subgroup);
            child.addProperty("score", stats.getFinalScore(defaultScore));
            child.addProperty("weight", stats.getWeight());
            child.add("counts", Util.make(new JsonObject(), counts -> {
                counts.addProperty("1", stats.getVoteCount(1));
                counts.addProperty("2", stats.getVoteCount(2));
                counts.addProperty("3", stats.getVoteCount(3));
                counts.addProperty("4", stats.getVoteCount(4));
                counts.addProperty("5", stats.getVoteCount(5));
                counts.addProperty("sum", stats.getVoteCount());
                counts.addProperty("effective", stats.getEffectiveCount());
            }));
        });
    }

    private static JsonElement toVoteStatsJson(SortedMap<String, VoteList.Stats> scores, float defaultScore) {
        JsonArray voteCountInfo = new JsonArray();
        for (Map.Entry<String, VoteList.Stats> entry : scores.entrySet()) {
            JsonElement child = toVoteStatsJson(entry.getKey(), entry.getValue(), defaultScore);
            voteCountInfo.add(child);
        }
        return voteCountInfo;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
            VoteDataStorage.get(currentServer).tick();
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        VoteDataStorage.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            VoteDataStorage.get(event.getServer()).close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(CompoundTag nbt) {
        VoteMe.LOGGER.info("Loading vote list data on server ...");

        // vote lists
        this.nextIndex = nbt.getInt("VoteListNextIndex");
        ListTag lists = nbt.getList("VoteLists", Tag.TAG_COMPOUND);
        for (int i = 0, size = lists.size(); i < size; ++i) {
            CompoundTag child = lists.getCompound(i);
            VoteSynchronizer.VoteDisabledKey key = VoteList.deserializeKey(child);
            Integer id = this.voteListIDs.get(key.artifactID(), key.categoryID());
            if (id == null) {
                id = child.getInt("VoteListIndex");
                if (id >= this.nextIndex) {
                    this.nextIndex = id + 1; // increase next index
                } else if (!this.voteLists.containsKey((int) id)) {
                    id = this.nextIndex++; // regenerate id
                }
                VoteList voteList = new VoteList(key.artifactID(), key.categoryID());
                this.voteListIDs.put(key.artifactID(), key.categoryID(), id);
                this.voteLists.put((int) id, voteList);
            }
            this.voteLists.get((int) id).deserializeNBT(child);
        }

        // artifacts
        int loadedArtifactSize = VoteArtifactNames.load(nbt);

        // comments
        CompoundTag commentsCollection = nbt.getCompound("VoteComments");
        for (String artifactID : commentsCollection.getAllKeys()) {
            CompoundTag allComments = commentsCollection.getCompound(artifactID);
            for (String voterID : allComments.getAllKeys()) {
                ImmutableList<String> comments = allComments.getList(voterID, Tag.TAG_STRING)
                        .stream().map(Tag::getAsString).collect(ImmutableList.toImmutableList());
                if (comments.isEmpty()) {
                    this.voteComments.put(UUID.fromString(artifactID), UUID.fromString(voterID), comments);
                } else {
                    this.voteComments.remove(UUID.fromString(artifactID), UUID.fromString(voterID));
                }
                this.emitCommentsAnnouncement(UUID.fromString(artifactID), UUID.fromString(voterID), comments);
            }
        }

        VoteMe.LOGGER.info("Loaded {} vote list data of {} artifact(s) on server.", lists.size(), loadedArtifactSize);
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        VoteMe.LOGGER.info("Saving vote list data on server ...");

        // vote lists
        ListTag lists = new ListTag();
        for (int id = 0; id < this.nextIndex; ++id) {
            VoteList voteList = this.voteLists.get(id);
            if (voteList != null) {
                CompoundTag child = voteList.serializeNBT();
                child.putInt("VoteListIndex", id);
                lists.add(child);
            }
        }
        nbt.putInt("VoteListNextIndex", this.nextIndex);
        nbt.put("VoteLists", lists);

        // artifacts
        int savedArtifactSize = VoteArtifactNames.save(nbt);

        // comments
        CompoundTag commentsCollection = new CompoundTag();
        for (UUID artifactID : this.voteComments.rowKeySet()) {
            CompoundTag artifactComments = new CompoundTag();
            for (Map.Entry<UUID, ImmutableList<String>> entry : this.voteComments.row(artifactID).entrySet()) {
                ListTag comments = new ListTag();
                for (String comment : entry.getValue()) {
                    comments.add(StringTag.valueOf(comment));
                }
                artifactComments.put(entry.getKey().toString(), comments);
            }
            commentsCollection.put(artifactID.toString(), artifactComments);
        }
        nbt.put("VoteComments", commentsCollection);

        VoteMe.LOGGER.info("Saved {} vote list data of {} artifact(s) on server.", lists.size(), savedArtifactSize);
        return nbt;
    }

    @Override
    public void close() throws IOException {
        this.sync.close();
    }
}
