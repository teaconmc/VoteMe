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
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.stream.IntStream;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class VoteListHandler extends WorldSavedData {

    private int maxIndex;

    private final List<VoteList.Entry> voteEntries;

    private final Table<String, ResourceLocation, Integer> voteListIndices;

    public static VoteListHandler get(MinecraftServer server) {
        DimensionSavedDataManager manager = server.func_241755_D_().getSavedData();
        return manager.getOrCreate(() -> new VoteListHandler("vote_lists"), "vote_lists");
    }

    public VoteListHandler(String name) {
        super(name);
        this.maxIndex = 0;
        this.voteListIndices = TreeBasedTable.create();
        this.voteEntries = new ArrayList<>(Collections.singletonList(null));

        // create default entries
        UUID uuid = UUID.fromString("7c5faf44-24b0-4496-b91a-147fb781fae9"); // zzzz_ustc
        for (ResourceLocation category : VoteCategoryHandler.getCategoryMap().keySet()) {
            this.voteEntries.get(this.getIdOrCreate("VoteMe", category)).getVotes().set(uuid, 5);
        }
    }

    public int getIdOrCreate(String artifact, ResourceLocation category) {
        Integer oldId = this.voteListIndices.get(artifact, category);
        if (oldId == null) {
            int id = ++this.maxIndex;
            this.voteListIndices.put(artifact, category, id);
            this.voteEntries.add(id, new VoteList.Entry(artifact, category, new VoteList(() -> this.onChange(id))));
            return id;
        }
        return oldId;
    }

    public Optional<VoteList.Entry> getEntry(int id) {
        return Optional.ofNullable(this.voteEntries.get(id));
    }

    public IntStream getIds() {
        return IntStream.rangeClosed(0, this.maxIndex).filter(id -> this.voteEntries.get(id) != null);
    }

    private void onChange(int id) {
        // TODO
    }

    @Override
    public void read(CompoundNBT nbt) {
        this.voteEntries.clear();
        this.voteListIndices.clear();
        this.maxIndex = nbt.getInt("VoteListMaxIndex");
        ListNBT children = nbt.getList("VoteLists", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = children.size(); i < size; ++i) {
            CompoundNBT child = children.getCompound(i);
            int id = child.getInt("VoteListIndex");
            VoteList.Entry entry = VoteList.fromNBT(child, () -> this.onChange(id));
            this.voteListIndices.put(entry.getArtifact(), entry.getCategory(), id);
            while (this.maxIndex < id) this.voteEntries.add(null);
            this.voteEntries.add(id, entry);
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        ListNBT children = new ListNBT();
        for (int id = 0; id <= this.maxIndex; ++id) {
            VoteList.Entry entry = this.voteEntries.get(id);
            if (entry != null) {
                CompoundNBT child = entry.toNBT();
                child.putInt("VoteListIndex", id);
                children.add(child);
            }
        }
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("VoteListMaxIndex", this.maxIndex);
        nbt.put("VoteLists", children);
        return nbt;
    }
}
