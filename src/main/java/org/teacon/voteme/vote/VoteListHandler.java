package org.teacon.voteme.vote;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
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
import net.minecraftforge.fml.network.PacketDistributor;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.network.SyncArtifactNamePacket;
import org.teacon.voteme.network.VoteMePacketManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteListHandler extends WorldSavedData {
    private static final Map<UUID, String> voteArtifactNames = new TreeMap<>();

    private int nextIndex;

    private final Int2ObjectMap<VoteListEntry> voteEntries;
    private final Table<UUID, ResourceLocation, Integer> voteListIndices;

    public static VoteListHandler get(MinecraftServer server) {
        DimensionSavedDataManager manager = server.func_241755_D_().getSavedData();
        return manager.getOrCreate(() -> new VoteListHandler("vote_lists"), "vote_lists");
    }

    public VoteListHandler(String name) {
        super(name);
        this.nextIndex = 1;
        this.voteListIndices = TreeBasedTable.create();
        this.voteEntries = new Int2ObjectRBTreeMap<>();
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
            this.voteEntries.put(id, new VoteListEntry(artifactID, category, new VoteList(this::markDirty)));
            this.markDirty();
            return id;
        }
        return oldId;
    }

    public Optional<VoteListEntry> getEntry(int id) {
        return Optional.ofNullable(this.voteEntries.get(id));
    }

    public static Collection<? extends UUID> getArtifacts() {
        return Collections.unmodifiableSet(voteArtifactNames.keySet());
    }

    public static String getArtifactName(UUID uuid) {
        return voteArtifactNames.getOrDefault(uuid, "");
    }

    public static void putArtifactName(VoteListHandler handler, UUID uuid, String name) {
        if (!name.isEmpty()) {
            handler.markDirty();
            voteArtifactNames.put(uuid, name);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(), SyncArtifactNamePacket.create(voteArtifactNames));
        } else if (voteArtifactNames.containsKey(uuid)) {
            handler.markDirty();
            voteArtifactNames.remove(uuid);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(), SyncArtifactNamePacket.create(voteArtifactNames));
        }
    }

    public static IFormattableTextComponent getArtifactText(UUID artifactID) {
        String uuidShort = artifactID.toString().substring(0, 4);
        ITextComponent hover = new StringTextComponent(artifactID.toString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        String base = String.format("%s (%s...)", getArtifactName(artifactID), uuidShort);
        return new StringTextComponent(base).modifyStyle(style -> style.setHoverEvent(hoverEvent));
    }

    public JsonObject toArtifactHTTPJson(UUID artifactID) {
        return Util.make(new JsonObject(), result -> {
            result.addProperty("id", artifactID.toString());
            result.addProperty("name", getArtifactName(artifactID));
            result.add("vote_lists", Util.make(new JsonArray(), array -> {
                for (ResourceLocation categoryID : VoteCategoryHandler.getIds()) {
                    int id = this.getIdOrCreate(artifactID, categoryID);
                    Optional<VoteListEntry> entryOptional = this.getEntry(id);
                    boolean enabledDefault = VoteCategoryHandler.getCategory(categoryID).filter(c -> c.enabledDefault).isPresent();
                    entryOptional.filter(entry -> entry.votes.getEnabled().orElse(enabledDefault)).ifPresent(entry -> array.add(id));
                }
            }));
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player instanceof ServerPlayerEntity) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(voteArtifactNames);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player), packet);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleServerPacket(SyncArtifactNamePacket packet) {
        voteArtifactNames.clear();
        voteArtifactNames.putAll(packet.artifactNames);
    }

    @Override
    public void read(CompoundNBT nbt) {
        voteArtifactNames.clear();
        this.voteEntries.clear();
        this.voteListIndices.clear();
        this.nextIndex = nbt.getInt("VoteListNextIndex");
        ListNBT lists = nbt.getList("VoteLists", Constants.NBT.TAG_COMPOUND);
        for (int i = 0, size = lists.size(); i < size; ++i) {
            CompoundNBT child = lists.getCompound(i);
            int id = child.getInt("VoteListIndex");
            if (id < this.nextIndex) {
                VoteListEntry entry = VoteListEntry.fromNBT(child, this::markDirty);
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
                voteArtifactNames.put(id, name);
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
        for (Map.Entry<UUID, String> entry : voteArtifactNames.entrySet()) {
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
