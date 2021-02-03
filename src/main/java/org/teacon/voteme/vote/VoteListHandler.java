package org.teacon.voteme.vote;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.time.Instant;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteListHandler extends WorldSavedData {
    private int nextIndex;

    private final Map<UUID, String> voteArtifactNames;
    private final Int2ObjectMap<VoteListEntry> voteEntries;
    private final Table<UUID, ResourceLocation, Integer> voteListIndices;

    public static VoteListHandler get(MinecraftServer server) {
        DimensionSavedDataManager manager = server.func_241755_D_().getSavedData();
        return manager.getOrCreate(() -> new VoteListHandler("vote_lists"), "vote_lists");
    }

    public VoteListHandler(String name) {
        super(name);
        this.nextIndex = 1;
        this.voteArtifactNames = new TreeMap<>();
        this.voteListIndices = TreeBasedTable.create();
        this.voteEntries = new Int2ObjectRBTreeMap<>();

        // initialize default values
        Instant voteTime = VoteList.DEFAULT_VOTE_TIME;
        ResourceLocation role = new ResourceLocation("voteme:00_general_players");
        UUID artifactID = UUID.fromString("8898dd9a-23cd-4f5f-80db-f66a32fd5e66");
        UUID userID = UUID.fromString("7c5faf44-24b0-4496-b91a-147fb781fae9"); // zzzz_ustc

        // create default entries
        this.voteArtifactNames.put(artifactID, "VoteMe");
        VoteCategoryHandler.getIds().stream()
                .mapToInt(category -> this.getIdOrCreate(artifactID, category))
                .forEach(id -> this.voteEntries.get(id).votes.set(userID, 5, role, voteTime));
    }

    public int getIdOrCreate(UUID artifactID, ResourceLocation category) {
        Integer oldId = this.voteListIndices.get(artifactID, category);
        if (oldId == null) {
            int id = this.nextIndex++;
            this.voteListIndices.put(artifactID, category, id);
            this.voteEntries.put(id, new VoteListEntry(artifactID, category, new VoteList(() -> this.onChange(id))));
            return id;
        }
        return oldId;
    }

    public Collection<? extends UUID> getArtifacts() {
        return Collections.unmodifiableSet(this.voteArtifactNames.keySet());
    }

    public String getArtifactName(UUID uuid) {
        return this.voteArtifactNames.getOrDefault(uuid, "");
    }

    public void putArtifactName(UUID uuid, String name) {
        if (name.isEmpty()) {
            this.voteArtifactNames.remove(uuid);
        } else {
            this.voteArtifactNames.put(uuid, name);
        }
    }

    public Optional<VoteListEntry> getEntry(int id) {
        return Optional.ofNullable(this.voteEntries.get(id));
    }

    public JsonObject toHTTPJson(UUID artifactID) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", artifactID.toString());
            result.addProperty("name", this.getArtifactName(artifactID));
            result.add("vote_lists", Util.make(new JsonArray(), array -> {
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    array.add(this.getIdOrCreate(artifactID, categoryID));
                }
            }));
        });
    }

    private void onChange(int id) {
        this.markDirty();
        // TODO: more things
    }

    @Override
    public void read(CompoundNBT nbt) {
        this.voteArtifactNames.clear();
        this.voteEntries.clear();
        this.voteListIndices.clear();
        this.nextIndex = nbt.getInt("VoteListNextIndex");
        ListNBT lists = nbt.getList("VoteLists", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = lists.size(); i < size; ++i) {
            CompoundNBT child = lists.getCompound(i);
            int id = child.getInt("VoteListIndex");
            if (id < this.nextIndex) {
                VoteListEntry entry = VoteListEntry.fromNBT(child, () -> this.onChange(id));
                this.voteListIndices.put(entry.artifactID, entry.category, id);
                this.voteEntries.put(id, entry);
            }
        }
        ListNBT names = nbt.getList("VoteArtifacts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = names.size(); i < size; ++i) {
            CompoundNBT child = names.getCompound(i);
            UUID id = child.getUniqueId("UUID");
            String name = child.getString("Name");
            if (!name.isEmpty()) {
                this.voteArtifactNames.put(id, name);
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
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
        for (Map.Entry<UUID, String> entry : this.voteArtifactNames.entrySet()) {
            CompoundNBT child = new CompoundNBT();
            child.putUniqueId("UUID", entry.getKey());
            child.putString("Name", entry.getValue());
            names.add(child);
        }
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("VoteListNextIndex", this.nextIndex);
        nbt.put("VoteArtifacts", names);
        nbt.put("VoteLists", lists);
        return nbt;
    }
}
