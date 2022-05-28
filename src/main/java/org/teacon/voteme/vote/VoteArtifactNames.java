package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.network.SyncArtifactNamePacket;
import org.teacon.voteme.network.VoteMePacketManager;
import org.teacon.voteme.sync.VoteSynchronizer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.minecraft.util.StringUtil.isNullOrEmpty;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoteArtifactNames {
    private static boolean needsSynchronizationToClient = false;
    private static final Map<UUID, String> voteArtifactNames = new TreeMap<>();
    private static final BiMap<UUID, String> voteArtifactAliases = HashBiMap.create();
    private static final Queue<VoteSynchronizer.Announcement> queuedAnnouncements = new ArrayDeque<>();

    public static Collection<? extends UUID> getArtifacts() {
        return Collections.unmodifiableCollection(voteArtifactNames.keySet());
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

    public static MutableComponent getArtifactText(UUID artifactID) {
        String alias = getArtifactAlias(artifactID);
        Component hover = new TextComponent(artifactID.toString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        if (alias.isEmpty()) {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s...)", getArtifactName(artifactID), shortID);
            return new TextComponent(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        } else {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s, %s...)", getArtifactName(artifactID), alias, shortID);
            return new TextComponent(base).withStyle(style -> style.withHoverEvent(hoverEvent));
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

    public static void putArtifactName(CommandSourceStack source, UUID uuid, String name) {
        if (!name.isEmpty()) {
            String oldName = voteArtifactNames.put(uuid, name);
            if (!name.equals(oldName)) {
                if (isNullOrEmpty(oldName)) {
                    VoteMe.LOGGER.info("{} ({}) has created artifact to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), name, uuid);
                } else {
                    VoteMe.LOGGER.info("{} ({}) has renamed artifact from {} to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), oldName, name, uuid);
                }
                needsSynchronizationToClient = true;
                emitArtifactAnnouncement(uuid);
            }
        } else if (voteArtifactNames.containsKey(uuid)) {
            voteArtifactAliases.remove(uuid);
            String oldName = voteArtifactNames.remove(uuid);
            if (!isNullOrEmpty(oldName)) {
                VoteMe.LOGGER.info("{} ({}) has removed artifact {} ({})",
                        source.getTextName(), source.getDisplayName().getString(), oldName, uuid);
                needsSynchronizationToClient = true;
                emitArtifactAnnouncement(uuid);
            }
        }
    }

    public static void putArtifactAlias(CommandSourceStack source, UUID uuid, String alias) {
        String name = voteArtifactNames.get(uuid);
        String sourceName = source.getTextName();
        if (!alias.isEmpty()) {
            Preconditions.checkArgument(!isNullOrEmpty(name));
            Preconditions.checkArgument(trimValidAlias(alias) == alias.length());
            UUID oldArtifactID = voteArtifactAliases.inverse().remove(alias);
            String oldAlias = voteArtifactAliases.put(uuid, alias);
            if (!isNullOrEmpty(oldAlias)) {
                VoteMe.LOGGER.info("{} ({}) has changed alias from {} to {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), oldAlias, alias, name, uuid);
                needsSynchronizationToClient = true;
                emitArtifactAnnouncement(uuid);
            } else if (oldArtifactID == null) {
                VoteMe.LOGGER.info("{} ({}) has created alias {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, name, uuid);
                needsSynchronizationToClient = true;
                emitArtifactAnnouncement(uuid);
            } else if (!uuid.equals(oldArtifactID)) {
                String oldArtifactName = voteArtifactNames.get(oldArtifactID);
                VoteMe.LOGGER.info("{} ({}) has adjusted the reference of alias {} from artifact {} ({}) to {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, oldArtifactName, oldAlias, name, uuid);
                needsSynchronizationToClient = true;
                emitArtifactAnnouncement(oldArtifactID);
                emitArtifactAnnouncement(uuid);
            }
        } else if (voteArtifactAliases.containsKey(uuid)) {
            String oldAlias = voteArtifactAliases.remove(uuid);
            VoteMe.LOGGER.info("{} ({}) has removed alias {} for artifact {} ({})",
                    sourceName, source.getDisplayName(), oldAlias, name, uuid);
            needsSynchronizationToClient = true;
            emitArtifactAnnouncement(uuid);
        }
    }

    public static void handleServerPacket(SyncArtifactNamePacket packet) {
        voteArtifactNames.clear();
        voteArtifactAliases.clear();
        voteArtifactNames.putAll(packet.artifactNames);
        voteArtifactAliases.putAll(packet.artifactAliases);
    }

    public static void publish(VoteSynchronizer.Announcement announcement) {
        if (announcement instanceof VoteSynchronizer.Artifact artifact) {
            UUID artifactID = artifact.key().artifactID();
            if (artifact.name().isEmpty()) {
                String oldName = voteArtifactNames.remove(artifactID);
                voteArtifactAliases.remove(artifactID);
                if (!isNullOrEmpty(oldName)) {
                    needsSynchronizationToClient = true;
                }
            } else {
                String oldName = voteArtifactNames.put(artifactID, artifact.name());
                if (!artifact.name().equals(oldName)) {
                    needsSynchronizationToClient = true;
                }
                if (artifact.alias().isPresent()) {
                    String aliasString = artifact.alias().get();
                    UUID oldArtifactID = voteArtifactAliases.inverse().remove(aliasString);
                    String oldAlias = voteArtifactAliases.put(artifactID, aliasString);
                    if (!isNullOrEmpty(oldAlias) || !artifactID.equals(oldArtifactID)) {
                        needsSynchronizationToClient = true;
                    }
                } else if (voteArtifactAliases.containsKey(artifactID)) {
                    voteArtifactAliases.remove(artifactID);
                    needsSynchronizationToClient = true;
                }
            }
            return;
        }
        throw new IllegalArgumentException("unsupported announcement type: " + announcement.getClass());
    }

    public static void dequeue(Collection<? super VoteSynchronizer.Announcement> drainTo) {
        drainTo.addAll(queuedAnnouncements);
        queuedAnnouncements.clear();
    }

    private static void emitArtifactAnnouncement(UUID uuid) {
        String name = voteArtifactNames.getOrDefault(uuid, "");
        Optional<String> alias = Optional.ofNullable(voteArtifactAliases.get(uuid)).filter(a -> !a.isEmpty());
        queuedAnnouncements.add(new VoteSynchronizer.Artifact(new VoteSynchronizer.ArtifactKey(uuid), name, alias));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (needsSynchronizationToClient && event.phase == TickEvent.Phase.START) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(voteArtifactNames, voteArtifactAliases);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    public static int load(CompoundTag nbt) {
        ListTag names = nbt.getList("VoteArtifacts", Tag.TAG_COMPOUND);
        for (int i = 0, size = names.size(); i < size; ++i) {
            CompoundTag child = names.getCompound(i);
            UUID uuid = child.getUUID("UUID");
            String name = child.getString("Name");
            String alias = child.getString("Alias");
            if (!name.isEmpty()) {
                String oldName = voteArtifactNames.put(uuid, name);
                if (!alias.isEmpty() && trimValidAlias(alias) == alias.length()) {
                    UUID oldArtifactID = voteArtifactAliases.inverse().remove(alias);
                    String oldAlias = voteArtifactAliases.put(uuid, alias);
                    if (!isNullOrEmpty(oldAlias)) {
                        needsSynchronizationToClient = true;
                        emitArtifactAnnouncement(uuid);
                    } else if (oldArtifactID == null) {
                        needsSynchronizationToClient = true;
                        emitArtifactAnnouncement(uuid);
                    } else if (!uuid.equals(oldArtifactID)) {
                        needsSynchronizationToClient = true;
                        emitArtifactAnnouncement(oldArtifactID);
                        emitArtifactAnnouncement(uuid);
                    } else if (!name.equals(oldName)) {
                        needsSynchronizationToClient = true;
                        emitArtifactAnnouncement(uuid);
                    }
                } else if (voteArtifactAliases.containsKey(uuid)) {
                    voteArtifactAliases.remove(uuid);
                    needsSynchronizationToClient = true;
                    emitArtifactAnnouncement(uuid);
                } else if (!name.equals(oldName)) {
                    needsSynchronizationToClient = true;
                    emitArtifactAnnouncement(uuid);
                }
            }
        }
        return names.size();
    }

    public static int save(CompoundTag nbt) {
        ListTag names = new ListTag();
        for (Map.Entry<UUID, String> entry : voteArtifactNames.entrySet()) {
            CompoundTag child = new CompoundTag();
            child.putUUID("UUID", entry.getKey());
            child.putString("Name", entry.getValue());
            Optional.ofNullable(voteArtifactAliases.get(entry.getKey())).ifPresent(a -> child.putString("Alias", a));
            names.add(child);
        }
        nbt.put("VoteArtifacts", names);
        return names.size();
    }
}
