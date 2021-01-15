package org.teacon.voteme.vote;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteListHandler extends WorldSavedData {
    private int nextIndex;
    private final List<VoteListEntry> voteEntries;
    private final Table<String, ResourceLocation, Integer> voteListIndices;

    public static VoteListHandler get(MinecraftServer server) {
        DimensionSavedDataManager manager = server.func_241755_D_().getSavedData();
        return manager.getOrCreate(() -> new VoteListHandler("vote_lists"), "vote_lists");
    }

    public VoteListHandler(String name) {
        super(name);
        this.nextIndex = 1;
        this.voteListIndices = TreeBasedTable.create();
        this.voteEntries = new ArrayList<>(Collections.singletonList(null));

        // create default entries
        ResourceLocation role = new ResourceLocation("voteme:00_general_players");
        UUID uuid = UUID.fromString("7c5faf44-24b0-4496-b91a-147fb781fae9"); // zzzz_ustc
        VoteCategoryHandler.getIds().forEach(c -> this.voteEntries.get(this.getIdOrCreate("VoteMe", c)).votes.set(uuid, 5, role));
    }

    public int getIdOrCreate(String artifact, ResourceLocation category) {
        Integer oldId = this.voteListIndices.get(artifact, category);
        if (oldId == null) {
            int id = this.nextIndex++;
            this.voteListIndices.put(artifact, category, id);
            this.voteEntries.add(id, new VoteListEntry(artifact, category, new VoteList(() -> this.onChange(id))));
            return id;
        }
        return oldId;
    }

    public Optional<VoteListEntry> getEntry(int id) {
        return Optional.ofNullable(this.voteEntries.get(id));
    }

    public IntStream getIds() {
        return IntStream.range(0, this.nextIndex).filter(id -> this.voteEntries.get(id) != null);
    }

    private void onChange(int id) {
        this.markDirty();
        // TODO: more things
    }

    @Override
    public void read(CompoundNBT nbt) {
        this.voteEntries.clear();
        this.voteListIndices.clear();
        this.nextIndex = nbt.getInt("VoteListNextIndex");
        ListNBT children = nbt.getList("VoteLists", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = children.size(); i < size; ++i) {
            CompoundNBT child = children.getCompound(i);
            int id = child.getInt("VoteListIndex");
            if (id < this.nextIndex) {
                VoteListEntry entry = VoteListEntry.fromNBT(child, () -> this.onChange(id));
                while (this.voteEntries.size() < id) this.voteEntries.add(null);
                this.voteListIndices.put(entry.artifact, entry.category, id);
                this.voteEntries.add(id, entry);
            }
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        ListNBT children = new ListNBT();
        for (int id = 0; id < this.nextIndex; ++id) {
            VoteListEntry entry = this.voteEntries.get(id);
            if (entry != null) {
                CompoundNBT child = entry.toNBT();
                child.putInt("VoteListIndex", id);
                children.add(child);
            }
        }
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("VoteListNextIndex", this.nextIndex);
        nbt.put("VoteLists", children);
        return nbt;
    }
}
