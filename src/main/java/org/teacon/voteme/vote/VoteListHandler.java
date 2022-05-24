package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.SyncArtifactNamePacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.util.StringUtils.isNullOrEmpty;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteListHandler extends WorldSavedData {
    private static final Map<UUID, String> voteArtifactNames = new TreeMap<>();
    private static final BiMap<UUID, String> voteArtifactAliases = HashBiMap.create();

    private int nextIndex;

    private final Int2ObjectMap<VoteListEntry> voteEntries;
    private final Table<UUID, ResourceLocation, Integer> voteListIndices;
    private final Table<UUID, UUID, ImmutableList<String>> comments;

    public static VoteListHandler get(MinecraftServer server) {
        DimensionSavedDataManager manager = server.overworld().getDataStorage();
        return manager.computeIfAbsent(() -> new VoteListHandler("vote_lists"), "vote_lists");
    }

    public VoteListHandler(String name) {
        super(name);
        this.nextIndex = 1;
        this.voteListIndices = TreeBasedTable.create();
        this.voteEntries = new Int2ObjectRBTreeMap<>();
        this.comments = HashBasedTable.create();
    }

    public boolean hasEnabled(ResourceLocation category) {
        boolean enabledDefault = VoteCategoryHandler.getCategory(category).filter(c -> c.enabledDefault).isPresent();
        return voteArtifactNames.keySet().stream()
                .map(id -> this.voteEntries.get(this.getIdOrCreate(id, category)))
                .anyMatch(entry -> entry.votes.getEnabled().orElse(enabledDefault));
    }

    public int getIdOrCreate(UUID artifactID, ResourceLocation category) {
        Integer oldId = this.voteListIndices.get(artifactID, category);
        if (oldId == null) {
            int id = this.nextIndex++;
            this.voteListIndices.put(artifactID, category, id);
            this.voteEntries.put(id, new VoteListEntry(artifactID, category, new VoteList(this::setDirty)));
            this.setDirty();
            return id;
        }
        return oldId;
    }

    public Optional<VoteListEntry> getEntry(int id) {
        return Optional.ofNullable(this.voteEntries.get(id));
    }
    
    public static ImmutableList<String> getCommentFor(VoteListHandler handler, UUID artifactID, UUID voterID) {
        if (handler.comments.contains(artifactID, voterID)) {
            return handler.comments.get(artifactID, voterID);
        }
        return ImmutableList.of();
    }
    
    public static Map<UUID, ImmutableList<String>> getAllCommentsFor(VoteListHandler handler, UUID artifactID) {
        return Collections.unmodifiableMap(handler.comments.row(artifactID));
    }
    
    public static void putCommentFor(VoteListHandler handler, UUID artifactID, UUID voterID, List<String> newComments) {
        if (newComments.isEmpty()) {
            handler.comments.remove(artifactID, voterID);
        }
        handler.comments.put(artifactID, voterID, ImmutableList.copyOf(newComments));
        handler.setDirty();
    }

    public static Collection<? extends UUID> getArtifacts() {
        return Collections.unmodifiableSet(voteArtifactNames.keySet());
    }

    public static Collection<? extends String> getArtifactAliases() {
        return Collections.unmodifiableSet(voteArtifactAliases.values());
    }

    public static String getArtifactName(UUID uuid) {
        return voteArtifactNames.getOrDefault(uuid, "");
    }

    public static String getArtifactAlias(UUID uuid) {
        return voteArtifactAliases.getOrDefault(uuid, "");
    }

    public static Optional<UUID> getArtifactByAliasOrUUID(String ref) {
        if (voteArtifactAliases.inverse().containsKey(ref)) {
            return Optional.of(voteArtifactAliases.inverse().get(ref));
        }
        try {
            return Optional.of(UUID.fromString(ref)).filter(voteArtifactNames::containsKey);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> getArtifactByAlias(String alias) {
        return Optional.ofNullable(voteArtifactAliases.inverse().get(alias));
    }

    public static void putArtifactName(CommandSource source, UUID uuid, String name) {
        VoteListHandler handler = get(source.getServer());
        if (!name.isEmpty()) {
            String oldName = voteArtifactNames.put(uuid, name);
            if (!name.equals(oldName)) {
                if (isNullOrEmpty(oldName)) {
                    VoteMe.LOGGER.info("{} ({}) has created artifact to {} ({})", source.getTextName(), source.getDisplayName().getString(), name, uuid);
                } else {
                    VoteMe.LOGGER.info("{} ({}) has renamed artifact from {} to {} ({})", source.getTextName(), source.getDisplayName().getString(), oldName, name, uuid);
                }
                SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases);
                VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(),packet);
                handler.setDirty();
            }
        } else if (voteArtifactNames.containsKey(uuid)) {
            handler.setDirty();
            voteArtifactAliases.remove(uuid);
            String oldName = voteArtifactNames.remove(uuid);
            VoteMe.LOGGER.info("{} ({}) has removed artifact {} ({})", source.getTextName(), source.getDisplayName().getString(), oldName, uuid);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(),SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases));
        }
    }

    public static void putArtifactAlias(CommandSource source, UUID uuid, String alias) {
        VoteListHandler handler = get(source.getServer());
        if (!alias.isEmpty()) {
            Preconditions.checkArgument(!isNullOrEmpty(voteArtifactNames.get(uuid)));
            Preconditions.checkArgument(trimValidAlias(alias) == alias.length());
            String oldAlias = voteArtifactAliases.put(uuid, alias);
            if (!alias.equals(oldAlias)) {
                handler.setDirty();
                if (isNullOrEmpty(oldAlias)) {
                    VoteMe.LOGGER.info("{} ({}) has created alias {} for artifact {} ({})", source.getTextName(),
                            source.getDisplayName().getString(), alias, voteArtifactNames.get(uuid), uuid);
                } else {
                    VoteMe.LOGGER.info("{} ({}) has changed alias from {} to {} for artifact {} ({})", source.getTextName(),
                            source.getDisplayName().getString(), oldAlias, alias, voteArtifactNames.get(uuid), uuid);
                }
                VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases));
            }
        } else if (voteArtifactAliases.containsKey(uuid)) {
            handler.setDirty();
            String oldAlias = voteArtifactAliases.remove(uuid);
            VoteMe.LOGGER.info("{} ({}) has removed alias {} for artifact {} ({})",
                    source.getTextName(), source.getDisplayName(), oldAlias, voteArtifactNames.get(uuid), uuid);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(),
                    SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases));
        }
    }

    public static IFormattableTextComponent getArtifactText(UUID artifactID) {
        String alias = getArtifactAlias(artifactID);
        ITextComponent hover = new StringTextComponent(artifactID.toString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        if (alias.isEmpty()) {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s...)", getArtifactName(artifactID), shortID);
            return new StringTextComponent(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        } else {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s, %s...)", getArtifactName(artifactID), alias, shortID);
            return new StringTextComponent(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        }
    }

    public static int trimValidAlias(String remaining) {
        int index = 0, size = remaining.length();
        if (remaining.charAt(0) == '#' && size > 1) {
            while (++index < size) {
                char current = remaining.charAt(index);
                if (!ResourceLocation.validPathChar(current)) {
                    break;
                }
            }
        }
        return index;
    }

    public JsonObject toArtifactHTTPJson(UUID artifactID) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", artifactID.toString());
            result.addProperty("name", getArtifactName(artifactID));
            Optional.of(getArtifactAlias(artifactID)).filter(s -> !s.isEmpty()).ifPresent(s -> result.addProperty("alias", s));
            Map<Integer, VoteListEntry> voteLists = new LinkedHashMap<>();
            for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                int id = this.getIdOrCreate(artifactID, categoryID);
                Optional<VoteListEntry> entryOptional = this.getEntry(id);
                boolean enabledDefault = VoteCategoryHandler.getCategory(categoryID).filter(c -> c.enabledDefault).isPresent();
                entryOptional.filter(entry -> entry.votes.getEnabled().orElse(enabledDefault)).ifPresent(entry -> voteLists.put(id, entry));
            }
            result.add("vote_lists", Util.make(new JsonArray(), array -> voteLists.keySet().forEach(array::add)));
            result.add("vote_comments", Util.make(new JsonArray(), array -> {
                for (Map.Entry<UUID, ImmutableList<String>> entry : getAllCommentsFor(this, artifactID).entrySet()) {
                    UUID voterID = entry.getKey();
                    ImmutableList<String> commentsForVoter = entry.getValue();
                    if (!commentsForVoter.isEmpty()) {
                        array.add(Util.make(new JsonObject(), child -> {
                            child.add("votes", Util.make(new JsonObject(), votes -> {
                                JsonArray[] arrays = {new JsonArray(), new JsonArray(), new JsonArray(), new JsonArray(), new JsonArray(), new JsonArray()};
                                for (VoteListEntry voteListEntry : voteLists.values()) {
                                    JsonObject object = new JsonObject();
                                    VoteList voteList = voteListEntry.votes;
                                    object.add("roles", Util.make(new JsonArray(), roles -> voteList.getRoles(voterID).forEach(r -> roles.add(r.toString()))));
                                    object.addProperty("category", voteListEntry.category.toString());
                                    arrays[voteList.get(voterID)].add(object);
                                }
                                for (int i = 1; i <= 5; ++i) {
                                    votes.add(Integer.toString(i), arrays[i]);
                                }
                            }));
                            child.add("texts", Util.make(new JsonArray(), texts -> commentsForVoter.forEach(texts::add)));
                        }));
                    }
                }
            }));
        });
    }

    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        VoteListHandler.get(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player instanceof ServerPlayerEntity) {
            VoteListHandler.get(Objects.requireNonNull(player.getServer()));
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), packet);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleServerPacket(SyncArtifactNamePacket packet) {
        voteArtifactNames.clear();
        voteArtifactAliases.clear();
        voteArtifactNames.putAll(packet.artifactNames);
        voteArtifactAliases.putAll(packet.artifactAliases);
    }

    @Override
    public void load(CompoundNBT nbt) {
        VoteMe.LOGGER.info("Loading vote list data on server ...");
        voteArtifactNames.clear();
        voteArtifactAliases.clear();
        this.voteEntries.clear();
        this.voteListIndices.clear();
        this.comments.clear();
        this.nextIndex = nbt.getInt("VoteListNextIndex");
        ListNBT lists = nbt.getList("VoteLists", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = lists.size(); i < size; ++i) {
            CompoundNBT child = lists.getCompound(i);
            int id = child.getInt("VoteListIndex");
            if (id < this.nextIndex) {
                VoteListEntry entry = VoteListEntry.fromNBT(child, this::setDirty);
                this.voteListIndices.put(entry.artifactID, entry.category, id);
                this.voteEntries.put(id, entry);
            }
        }
        ListNBT names = nbt.getList("VoteArtifacts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = names.size(); i < size; ++i) {
            CompoundNBT child = names.getCompound(i);
            UUID id = child.getUUID("UUID");
            String name = child.getString("Name");
            String alias = child.getString("Alias");
            if (!name.isEmpty()) {
                voteArtifactNames.put(id, name);
                if (!alias.isEmpty() && trimValidAlias(alias) == alias.length()) {
                    voteArtifactAliases.put(id, alias);
                }
            }
        }
        CompoundNBT commentsCollection = nbt.getCompound("VoteComments");
        for (String artifactID : commentsCollection.getAllKeys()) {
            CompoundNBT allComments = commentsCollection.getCompound(artifactID);
            for (String voterID : allComments.getAllKeys()) {
                ImmutableList<String> comments = allComments.getList(voterID, Constants.NBT.TAG_STRING)
                    .stream()
                    .filter(t -> t instanceof StringNBT)
                    .map(t -> ((StringNBT) t).getAsString())
                    .collect(ImmutableList.toImmutableList());
                if (!comments.isEmpty()) {
                    this.comments.put(UUID.fromString(artifactID), UUID.fromString(voterID), comments);
                }
            }
        }
        VoteMe.LOGGER.info("Loaded vote list data of {} artifact(s) on server.", voteArtifactNames.size());
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        VoteMe.LOGGER.info("Saving vote list data on server ...");
        ListNBT lists = new ListNBT();
        for (int id = 0; id < this.nextIndex; ++id) {
            VoteListEntry entry = this.voteEntries.get(id);
            if (entry != null) {
                CompoundNBT child = entry.toNBT();
                child.putInt("VoteListIndex", id);
                lists.add(child);
            }
        }
        ListNBT names = new ListNBT();
        for (Map.Entry<UUID, String> entry : voteArtifactNames.entrySet()) {
            CompoundNBT child = new CompoundNBT();
            child.putUUID("UUID", entry.getKey());
            child.putString("Name", entry.getValue());
            Optional.ofNullable(voteArtifactAliases.get(entry.getKey())).ifPresent(a -> child.putString("Alias", a));
            names.add(child);
        }
        CompoundNBT commentsCollection = new CompoundNBT();
        for (UUID artifactID : this.comments.rowKeySet()) {
            CompoundNBT artifactComments = new CompoundNBT();
            for (Map.Entry<UUID, ImmutableList<String>> commentsByVoter : this.comments.row(artifactID).entrySet()) {
                ListNBT comments = new ListNBT();
                for (String c : commentsByVoter.getValue()) {
                    comments.add(StringNBT.valueOf(c));
                }
                artifactComments.put(commentsByVoter.getKey().toString(), comments);
            }
            commentsCollection.put(artifactID.toString(), artifactComments);
        }
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("VoteListNextIndex", this.nextIndex);
        nbt.put("VoteArtifacts", names);
        nbt.put("VoteLists", lists);
        nbt.put("VoteComments", commentsCollection);
        VoteMe.LOGGER.info("Saved vote list data of {} artifact(s) on server.", names.size());
        return nbt;
    }
}
