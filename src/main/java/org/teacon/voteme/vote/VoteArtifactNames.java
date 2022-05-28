package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
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
import net.minecraftforge.common.util.Lazy;
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
    private boolean needsSynchronizationToClient = false;
    private final Map<UUID, String> names = new TreeMap<>();
    private final BiMap<UUID, String> aliases = HashBiMap.create();
    private final Queue<VoteSynchronizer.Announcement> queued = new ArrayDeque<>();

    private static final Lazy<VoteArtifactNames> clientArtifactNames = Lazy.of(VoteArtifactNames::new);

    private static final Lazy<VoteArtifactNames> serverArtifactNames = Lazy.of(VoteArtifactNames::new);

    public static Collection<? extends UUID> getArtifacts(boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        return Collections.unmodifiableCollection(instance.names.keySet());
    }

    public static Collection<? extends String> getArtifactAliases(boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        return Collections.unmodifiableSet(instance.aliases.values());
    }

    public static String getArtifactName(UUID uuid, boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        return instance.names.getOrDefault(uuid, "");
    }

    public static String getArtifactAlias(UUID uuid, boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        return instance.aliases.getOrDefault(uuid, "");
    }

    public static Optional<UUID> getArtifactByAliasOrUUID(String ref, boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        if (instance.aliases.inverse().containsKey(ref)) {
            return Optional.of(instance.aliases.inverse().get(ref));
        }
        try {
            return Optional.of(UUID.fromString(ref)).filter(instance.names::containsKey);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> getArtifactByAlias(String alias, boolean isClient) {
        VoteArtifactNames instance = isClient ? clientArtifactNames.get() : serverArtifactNames.get();
        return Optional.ofNullable(instance.aliases.inverse().get(alias));
    }

    public static MutableComponent getArtifactText(UUID artifactID, boolean isClient) {
        String alias = getArtifactAlias(artifactID, isClient);
        Component hover = new TextComponent(artifactID.toString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        if (alias.isEmpty()) {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s...)", getArtifactName(artifactID, isClient), shortID);
            return new TextComponent(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        } else {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s, %s...)", getArtifactName(artifactID, isClient), alias, shortID);
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
        VoteArtifactNames instance = serverArtifactNames.get();
        if (!name.isEmpty()) {
            String oldName = instance.names.put(uuid, name);
            if (!name.equals(oldName)) {
                if (isNullOrEmpty(oldName)) {
                    VoteMe.LOGGER.info("{} ({}) has created artifact to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), name, uuid);
                } else {
                    VoteMe.LOGGER.info("{} ({}) has renamed artifact from {} to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), oldName, name, uuid);
                }
                instance.needsSynchronizationToClient = true;
                instance.emitArtifactAnnouncement(uuid);
            }
        } else if (instance.names.containsKey(uuid)) {
            instance.aliases.remove(uuid);
            String oldName = instance.names.remove(uuid);
            if (!isNullOrEmpty(oldName)) {
                VoteMe.LOGGER.info("{} ({}) has removed artifact {} ({})",
                        source.getTextName(), source.getDisplayName().getString(), oldName, uuid);
                instance.needsSynchronizationToClient = true;
                instance.emitArtifactAnnouncement(uuid);
            }
        }
    }

    public static void putArtifactAlias(CommandSourceStack source, UUID uuid, String alias) {
        VoteArtifactNames instance = serverArtifactNames.get();
        String sourceName = source.getTextName();
        String name = instance.names.get(uuid);
        if (!alias.isEmpty()) {
            Preconditions.checkArgument(!isNullOrEmpty(name));
            Preconditions.checkArgument(trimValidAlias(alias) == alias.length());
            UUID oldArtifactID = instance.aliases.inverse().remove(alias);
            String oldAlias = instance.aliases.put(uuid, alias);
            if (!isNullOrEmpty(oldAlias)) {
                VoteMe.LOGGER.info("{} ({}) has changed alias from {} to {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), oldAlias, alias, name, uuid);
                instance.needsSynchronizationToClient = true;
                instance.emitArtifactAnnouncement(uuid);
            } else if (oldArtifactID == null) {
                VoteMe.LOGGER.info("{} ({}) has created alias {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, name, uuid);
                instance.needsSynchronizationToClient = true;
                instance.emitArtifactAnnouncement(uuid);
            } else if (!uuid.equals(oldArtifactID)) {
                String oldArtifactName = instance.names.get(oldArtifactID);
                VoteMe.LOGGER.info("{} ({}) has adjusted the reference of alias {} from artifact {} ({}) to {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, oldArtifactName, oldAlias, name, uuid);
                instance.needsSynchronizationToClient = true;
                instance.emitArtifactAnnouncement(oldArtifactID);
                instance.emitArtifactAnnouncement(uuid);
            }
        } else if (instance.aliases.containsKey(uuid)) {
            String oldAlias = instance.aliases.remove(uuid);
            VoteMe.LOGGER.info("{} ({}) has removed alias {} for artifact {} ({})",
                    sourceName, source.getDisplayName(), oldAlias, name, uuid);
            instance.needsSynchronizationToClient = true;
            instance.emitArtifactAnnouncement(uuid);
        }
    }

    public static void handleServerPacket(SyncArtifactNamePacket packet) {
        Util.make(clientArtifactNames.get(), instance -> {
            instance.names.clear();
            instance.aliases.clear();
            instance.names.putAll(packet.artifactNames);
            instance.aliases.putAll(packet.artifactAliases);
        });
    }

    public static void publish(VoteSynchronizer.Announcement announcement) {
        if (announcement instanceof VoteSynchronizer.Artifact artifact) {
            VoteArtifactNames instance = serverArtifactNames.get();
            UUID artifactID = artifact.key().artifactID();
            if (artifact.name().isEmpty()) {
                String oldName = instance.names.remove(artifactID);
                instance.aliases.remove(artifactID);
                if (!isNullOrEmpty(oldName)) {
                    instance.needsSynchronizationToClient = true;
                }
            } else {
                String oldName = instance.names.put(artifactID, artifact.name());
                if (!artifact.name().equals(oldName)) {
                    instance.needsSynchronizationToClient = true;
                }
                if (artifact.alias().isPresent()) {
                    String aliasString = artifact.alias().get();
                    UUID oldArtifactID = instance.aliases.inverse().remove(aliasString);
                    String oldAlias = instance.aliases.put(artifactID, aliasString);
                    if (!isNullOrEmpty(oldAlias) || !artifactID.equals(oldArtifactID)) {
                        instance.needsSynchronizationToClient = true;
                    }
                } else if (instance.aliases.containsKey(artifactID)) {
                    instance.aliases.remove(artifactID);
                    instance.needsSynchronizationToClient = true;
                }
            }
            return;
        }
        throw new IllegalArgumentException("unsupported announcement type: " + announcement.getClass());
    }

    public static void dequeue(Collection<? super VoteSynchronizer.Announcement> drainTo) {
        VoteArtifactNames instance = serverArtifactNames.get();
        drainTo.addAll(instance.queued);
        instance.queued.clear();
    }

    private void emitArtifactAnnouncement(UUID uuid) {
        String name = this.names.getOrDefault(uuid, "");
        Optional<String> alias = Optional.ofNullable(this.aliases.get(uuid)).filter(a -> !a.isEmpty());
        this.queued.add(new VoteSynchronizer.Artifact(new VoteSynchronizer.ArtifactKey(uuid), name, alias));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getPlayer();
        VoteArtifactNames instance = serverArtifactNames.get();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(instance.names, instance.aliases);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        VoteArtifactNames instance = serverArtifactNames.get();
        if (instance.needsSynchronizationToClient && event.phase == TickEvent.Phase.START) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(instance.names, instance.aliases);
            VoteMePacketManager.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    public static int load(CompoundTag nbt) {
        VoteArtifactNames instance = serverArtifactNames.get();
        ListTag names = nbt.getList("VoteArtifacts", Tag.TAG_COMPOUND);
        for (int i = 0, size = names.size(); i < size; ++i) {
            CompoundTag child = names.getCompound(i);
            UUID uuid = child.getUUID("UUID");
            String name = child.getString("Name");
            String alias = child.getString("Alias");
            if (!name.isEmpty()) {
                String oldName = instance.names.put(uuid, name);
                if (!alias.isEmpty() && trimValidAlias(alias) == alias.length()) {
                    UUID oldArtifactID = instance.aliases.inverse().remove(alias);
                    String oldAlias = instance.aliases.put(uuid, alias);
                    if (!isNullOrEmpty(oldAlias)) {
                        instance.needsSynchronizationToClient = true;
                        instance.emitArtifactAnnouncement(uuid);
                    } else if (oldArtifactID == null) {
                        instance.needsSynchronizationToClient = true;
                        instance.emitArtifactAnnouncement(uuid);
                    } else if (!uuid.equals(oldArtifactID)) {
                        instance.needsSynchronizationToClient = true;
                        instance.emitArtifactAnnouncement(oldArtifactID);
                        instance.emitArtifactAnnouncement(uuid);
                    } else if (!name.equals(oldName)) {
                        instance.needsSynchronizationToClient = true;
                        instance.emitArtifactAnnouncement(uuid);
                    }
                } else if (instance.aliases.containsKey(uuid)) {
                    instance.aliases.remove(uuid);
                    instance.needsSynchronizationToClient = true;
                    instance.emitArtifactAnnouncement(uuid);
                } else if (!name.equals(oldName)) {
                    instance.needsSynchronizationToClient = true;
                    instance.emitArtifactAnnouncement(uuid);
                }
            }
        }
        return names.size();
    }

    public static int save(CompoundTag nbt) {
        ListTag names = new ListTag();
        VoteArtifactNames instance = serverArtifactNames.get();
        for (Map.Entry<UUID, String> entry : instance.names.entrySet()) {
            CompoundTag child = new CompoundTag();
            child.putUUID("UUID", entry.getKey());
            child.putString("Name", entry.getValue());
            Optional.ofNullable(instance.aliases.get(entry.getKey())).ifPresent(a -> child.putString("Alias", a));
            names.add(child);
        }
        nbt.put("VoteArtifacts", names);
        return names.size();
    }
}
