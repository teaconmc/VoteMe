package org.teacon.voteme.vote;

import com.google.common.collect.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.commons.lang3.text.StrSubstitutor;
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

import static org.teacon.voteme.sync.AnnouncementSerializer.deserialize;
import static org.teacon.voteme.sync.AnnouncementSerializer.serialize;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(modid = "voteme")
public final class VoteDataStorage extends SavedData implements Closeable {
    private static final SavedDataType<VoteDataStorage> TYPE = new SavedDataType<>(
            Identifier.parse("voteme:vote_lists"),
            VoteDataStorage::new,
            CompoundTag.CODEC.xmap(VoteDataStorage::fromTag, VoteDataStorage::toTag),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private int nextIndex;

    private final VoteArtifactNames artifactNames;
    private final Int2ObjectMap<VoteList> voteLists;
    private final Table<UUID, Identifier, Integer> voteListIDs;
    private final Table<UUID, UUID, CommentsEntry> voteComments;

    private final VoteSynchronizer sync;

    public static VoteDataStorage get(MinecraftServer server) {
        SavedDataStorage manager = server.overworld().getDataStorage();
        return manager.computeIfAbsent(TYPE);
    }

    public VoteDataStorage() {
        this.nextIndex = 1;
        this.artifactNames = new VoteArtifactNames();
        this.voteLists = new Int2ObjectRBTreeMap<>();
        this.voteListIDs = TreeBasedTable.create();
        this.voteComments = HashBasedTable.create();
        this.sync = this.loadSynchronizer();
    }

    @SuppressWarnings("deprecation")
    private VoteSynchronizer loadSynchronizer() {
        MinecraftServer server = Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer());
        String uri = StrSubstitutor.replace(VoteMe.CONFIG.REDIS_ATTACH_URI.get(), System.getenv());
        return uri.isBlank() ? new DetachedSynchronizer(server) : new RedisSynchronizer(server, uri.strip());
    }

    private void tick() {
        // upload announcements
        Collection<VoteSynchronizer.Announcement> toUpload = new ArrayList<>();
        this.voteLists.values().forEach(v -> v.dequeue(toUpload));
        this.artifactNames.dequeue(toUpload);
        if (!toUpload.isEmpty()) {
            this.sync.publish(toUpload);
            this.setDirty();
        }

        // download announcements
        Collection<? extends VoteSynchronizer.Announcement> toDownload = this.sync.dequeue();
        if (!toDownload.isEmpty()) {
            toDownload.forEach(this::handle);
            this.setDirty();
        }
    }

    private void handle(VoteSynchronizer.Announcement announcement) {
        switch (announcement) {
            case VoteSynchronizer.Artifact artifact -> this.artifactNames.publish(artifact);
            case VoteSynchronizer.Comments comments -> this.handleCommentsAnnouncement(comments);
            case VoteSynchronizer.VoteDisabled voteDisabled -> {
                int id = this.getIdOrCreate(voteDisabled.key().artifactID(), voteDisabled.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(voteDisabled));
            }
            case VoteSynchronizer.Vote vote -> {
                int id = this.getIdOrCreate(vote.key().artifactID(), vote.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(vote));
            }
            case VoteSynchronizer.VoteStats voteStats -> {
                int id = this.getIdOrCreate(voteStats.key().artifactID(), voteStats.key().categoryID());
                this.getVoteList(id).ifPresent(v -> v.publish(voteStats));
            }
        }
    }

    private void handleCommentsAnnouncement(VoteSynchronizer.Comments comments) {
        UUID artifactID = comments.key().artifactID(), voterID = comments.key().voterID();
        CommentsEntry oldEntry = this.voteComments.get(artifactID, voterID);
        int revision = comments.key().revision();
        if (oldEntry == null || oldEntry.revision() <= revision) {
            ImmutableList<String> list = comments.comments();
            // drop the empty string prepended to the list for empty lists whose revision is larger than 0
            if (revision > 0 && list.stream().allMatch(String::isEmpty) && !list.isEmpty()) {
                list = list.subList(1, list.size());
            }
            CommentsEntry newEntry = new CommentsEntry(revision, list);
            this.voteComments.put(artifactID, voterID, newEntry);
        }
    }

    private void emitCommentsAnnouncement(UUID artifactID, UUID voterID, int revision, ImmutableList<String> comments) {
        VoteSynchronizer.CommentsKey key = new VoteSynchronizer.CommentsKey(artifactID, voterID, revision);
        if (revision > 0 && comments.stream().allMatch(String::isEmpty)) {
            // prepend an additional empty string if the comments are empty and the revision is larger than 0
            comments = ImmutableList.<String>builder().add("").addAll(comments).build();
        }
        this.sync.publish(List.of(new VoteSynchronizer.Comments(key, comments)));
        this.setDirty();
    }

    public VoteArtifactNames getArtifactNames() {
        return this.artifactNames;
    }

    public boolean hasEnabled(Identifier category) {
        boolean enabledDefault = VoteCategoryHandler.getCategory(category).filter(c -> c.enabledDefault).isPresent();
        return this.artifactNames.getUUIDs().stream()
                .map(id -> this.voteLists.get(this.getIdOrCreate(id, category)))
                .anyMatch(votes -> votes.getEnabled().orElse(enabledDefault));
    }

    public int getIdOrCreate(UUID artifactID, Identifier category) {
        return getIdOrCreate(artifactID, category, this.nextIndex);
    }

    private int getIdOrCreate(UUID artifactID, Identifier category, int hint) {
        Integer oldId = this.voteListIDs.get(artifactID, category);
        if (oldId == null) {
            int id = this.voteLists.containsKey(hint) ? this.nextIndex : hint;
            this.nextIndex = Math.max(this.nextIndex, id + 1);
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
            return Objects.requireNonNull(handler.voteComments.get(artifactID, voterID)).comments();
        }
        return ImmutableList.of();
    }

    public static Map<UUID, ImmutableList<String>> getAllCommentsFor(VoteDataStorage handler, UUID artifactID) {
        Map<UUID, CommentsEntry> map = handler.voteComments.row(artifactID);
        return Collections.unmodifiableMap(Maps.transformValues(map, CommentsEntry::comments));
    }

    public static void putCommentFor(VoteDataStorage handler, UUID artifactID, UUID voterID, List<String> newComments) {
        CommentsEntry old = handler.voteComments.get(artifactID, voterID);
        if (old == null ? !newComments.isEmpty() : !newComments.equals(old.comments())) {
            int revision = old == null ? 0 : old.revision() + 1;
            ImmutableList<String> comments = ImmutableList.copyOf(newComments);
            handler.voteComments.put(artifactID, voterID, new CommentsEntry(revision, comments));
            handler.emitCommentsAnnouncement(artifactID, voterID, revision, comments);
        }
    }

    public JsonObject toArtifactHTTPJson(UUID artifactID) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", artifactID.toString());
            result.addProperty("name", this.artifactNames.getName(artifactID));
            Optional.of(this.artifactNames.getAlias(artifactID))
                    .filter(s -> !s.isEmpty()).ifPresent(s -> result.addProperty("alias", s));
            Map<Integer, VoteList> voteLists = new LinkedHashMap<>();
            for (Identifier categoryID : VoteCategoryHandler.getIds()) {
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
                                        for (Identifier roleID : voteList.getRoles(voterID)) {
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

    public JsonElement toVoteListHTTPJson(UUID artifactID, Identifier categoryID) {
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
    public static void onServerTick(ServerTickEvent.Pre event) {
        VoteDataStorage.get(event.getServer()).tick();
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

    private static VoteDataStorage fromTag(CompoundTag nbt) {
        VoteDataStorage storage = new VoteDataStorage();
        storage.load(nbt);
        return storage;
    }

    private CompoundTag toTag() {
        return this.save(new CompoundTag());
    }

    public void load(CompoundTag nbt) {
        VoteMe.LOGGER.info("Loading vote list data on server ...");

        // vote list next index and index hints
        this.nextIndex = Math.max(this.nextIndex, nbt.getInt("VoteListNextIndex").orElse(0));

        ListTag hintTags = nbt.getList("VoteListIndexHints").orElseGet(ListTag::new);
        for (Tag tag : hintTags) {
            CompoundTag child = tag.asCompound().orElseThrow();
            int hint = child.getInt("VoteListIndex").orElse(this.nextIndex);
            UUID artifactID = child.read("ArtifactUUID", UUIDUtil.CODEC).orElseThrow();
            Identifier categoryID = Identifier.parse(child.getString("Category").orElseThrow());
            this.getIdOrCreate(artifactID, categoryID, hint);
        }

        // announcements
        ListTag announcementTags = nbt.getList("VoteAnnouncements").orElseGet(ListTag::new);
        for (Tag tag : announcementTags) {
            tag.asCompound().flatMap(announcementTag -> deserialize(announcementTag)).ifPresent(announcement -> {
                this.handle(announcement);
                this.sync.publish(List.of(announcement));
            });
        }

        int size = 1 + hintTags.size() + announcementTags.size();
        VoteMe.LOGGER.info("Loaded {} data on server.", size);
    }

    public CompoundTag save(CompoundTag nbt) {
        VoteMe.LOGGER.info("Saving vote list data on server ...");

        // vote list next index and index hints
        nbt.putInt("VoteListNextIndex", this.nextIndex);

        ListTag hintTags = new ListTag();
        for (Int2ObjectMap.Entry<VoteList> entry : this.voteLists.int2ObjectEntrySet()) {
            CompoundTag child = new CompoundTag();
            child.putInt("VoteListIndex", entry.getIntKey());
            child.store("ArtifactUUID", UUIDUtil.CODEC, entry.getValue().getArtifactID());
            child.putString("Category", entry.getValue().getCategoryID().toString());
            hintTags.add(child);
        }
        nbt.put("VoteListIndexHints", hintTags);

        // announcements
        ListTag announcementTags = new ListTag();
        List<VoteSynchronizer.Announcement> announcements = new ArrayList<>();
        this.artifactNames.buildAnnouncements(announcements);
        for (Table.Cell<UUID, UUID, CommentsEntry> e : this.voteComments.cellSet()) {
            VoteSynchronizer.CommentsKey key = new VoteSynchronizer.CommentsKey(e.getRowKey(), e.getColumnKey(), e.getValue().revision());
            announcements.add(new VoteSynchronizer.Comments(key, e.getValue().comments()));
        }
        this.voteLists.values().forEach(v -> v.buildAnnouncements(announcements));
        announcements.forEach(announcement -> serialize(announcement).ifPresent(announcementTags::add));
        nbt.put("VoteAnnouncements", announcementTags);

        VoteMe.LOGGER.info("Saved {} data on server.", 1 + hintTags.size() + announcementTags.size());
        return nbt;
    }

    @Override
    public void close() throws IOException {
        this.sync.close();
    }

    public record CommentsEntry(int revision, ImmutableList<String> comments) {
        // nothing here
    }
}
