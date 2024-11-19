package org.teacon.voteme.vote;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.teacon.voteme.VoteMe;
import org.teacon.voteme.network.SyncArtifactNamePacket;
import org.teacon.voteme.sync.VoteSynchronizer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;

import static net.minecraft.util.StringUtil.isNullOrEmpty;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public final class VoteArtifactNames {
    private boolean needsSynchronizationToClient = false;
    private final Map<UUID, String> names = new TreeMap<>();
    private final BiMap<UUID, String> aliases = HashBiMap.create();
    private final Queue<VoteSynchronizer.Announcement> queued = new ArrayDeque<>();

    public Collection<? extends UUID> getUUIDs() {
        return Collections.unmodifiableCollection(this.names.keySet());
    }

    public Collection<? extends String> getAliases() {
        return Collections.unmodifiableSet(this.aliases.values());
    }

    public String getName(UUID uuid) {
        return this.names.getOrDefault(uuid, "");
    }

    public String getAlias(UUID uuid) {
        return this.aliases.getOrDefault(uuid, "");
    }

    public Optional<UUID> getByAliasOrUUID(String ref) {
        if (this.aliases.inverse().containsKey(ref)) {
            return Optional.of(this.aliases.inverse().get(ref));
        }
        try {
            return Optional.of(UUID.fromString(ref)).filter(this.names::containsKey);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> getByAlias(String alias) {
        return Optional.ofNullable(this.aliases.inverse().get(alias));
    }

    public MutableComponent toText(UUID artifactID) {
        String alias = this.getAlias(artifactID);
        Component hover = Component.literal(artifactID.toString());
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        if (alias.isEmpty()) {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s...)", this.getName(artifactID), shortID);
            return Component.literal(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        } else {
            String shortID = artifactID.toString().substring(0, 8);
            String base = String.format("%s (%s, %s...)", this.getName(artifactID), alias, shortID);
            return Component.literal(base).withStyle(style -> style.withHoverEvent(hoverEvent));
        }
    }

    public void putName(CommandSourceStack source, UUID uuid, String name) {
        if (!name.isEmpty()) {
            String oldName = this.names.put(uuid, name);
            if (!name.equals(oldName)) {
                if (isNullOrEmpty(oldName)) {
                    VoteMe.LOGGER.info("{} ({}) has created artifact to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), name, uuid);
                } else {
                    VoteMe.LOGGER.info("{} ({}) has renamed artifact from {} to {} ({})",
                            source.getTextName(), source.getDisplayName().getString(), oldName, name, uuid);
                }
                this.needsSynchronizationToClient = true;
                this.emitArtifactAnnouncement(uuid);
            }
        } else if (this.names.containsKey(uuid)) {
            this.aliases.remove(uuid);
            String oldName = this.names.remove(uuid);
            if (!isNullOrEmpty(oldName)) {
                VoteMe.LOGGER.info("{} ({}) has removed artifact {} ({})",
                        source.getTextName(), source.getDisplayName().getString(), oldName, uuid);
                this.needsSynchronizationToClient = true;
                this.emitArtifactAnnouncement(uuid);
            }
        }
    }

    public void putAlias(CommandSourceStack source, UUID uuid, String alias) {
        String sourceName = source.getTextName();
        String name = this.names.get(uuid);
        if (!alias.isEmpty()) {
            Preconditions.checkArgument(!isNullOrEmpty(name));
            Preconditions.checkArgument(trimValidAlias(alias) == alias.length());
            UUID oldArtifactID = this.aliases.inverse().remove(alias);
            String oldAlias = this.aliases.put(uuid, alias);
            if (!isNullOrEmpty(oldAlias)) {
                VoteMe.LOGGER.info("{} ({}) has changed alias from {} to {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), oldAlias, alias, name, uuid);
                this.needsSynchronizationToClient = true;
                this.emitArtifactAnnouncement(uuid);
            } else if (oldArtifactID == null) {
                VoteMe.LOGGER.info("{} ({}) has created alias {} for artifact {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, name, uuid);
                this.needsSynchronizationToClient = true;
                this.emitArtifactAnnouncement(uuid);
            } else if (!uuid.equals(oldArtifactID)) {
                String oldArtifactName = this.names.get(oldArtifactID);
                VoteMe.LOGGER.info("{} ({}) has adjusted the reference of alias {} from artifact {} ({}) to {} ({})",
                        sourceName, source.getDisplayName().getString(), alias, oldArtifactName, oldAlias, name, uuid);
                this.needsSynchronizationToClient = true;
                this.emitArtifactAnnouncement(oldArtifactID);
                this.emitArtifactAnnouncement(uuid);
            }
        } else if (this.aliases.containsKey(uuid)) {
            String oldAlias = this.aliases.remove(uuid);
            VoteMe.LOGGER.info("{} ({}) has removed alias {} for artifact {} ({})",
                    sourceName, source.getDisplayName(), oldAlias, name, uuid);
            this.needsSynchronizationToClient = true;
            this.emitArtifactAnnouncement(uuid);
        }
    }

    public void buildAnnouncements(Collection<? super VoteSynchronizer.Announcement> announcements) {
        for (Map.Entry<UUID, String> entry : this.names.entrySet()) {
            String name = entry.getValue();
            if (!name.isEmpty()) {
                VoteSynchronizer.ArtifactKey key = new VoteSynchronizer.ArtifactKey(entry.getKey());
                Optional<String> alias = Optional.ofNullable(this.aliases.get(entry.getKey()));
                announcements.add(new VoteSynchronizer.Artifact(key, name, alias));
            }
        }
    }

    public void publish(VoteSynchronizer.Announcement announcement) {
        if (announcement instanceof VoteSynchronizer.Artifact artifact) {
            UUID artifactID = artifact.key().artifactID();
            if (artifact.name().isEmpty()) {
                String oldName = this.names.remove(artifactID);
                this.aliases.remove(artifactID);
                if (!isNullOrEmpty(oldName)) {
                    this.needsSynchronizationToClient = true;
                }
            } else {
                String oldName = this.names.put(artifactID, artifact.name());
                if (!artifact.name().equals(oldName)) {
                    this.needsSynchronizationToClient = true;
                }
                if (artifact.alias().isPresent()) {
                    String aliasString = artifact.alias().get();
                    UUID oldArtifactID = this.aliases.inverse().remove(aliasString);
                    String oldAlias = this.aliases.put(artifactID, aliasString);
                    if (!isNullOrEmpty(oldAlias) || !artifactID.equals(oldArtifactID)) {
                        this.needsSynchronizationToClient = true;
                    }
                } else if (this.aliases.containsKey(artifactID)) {
                    this.aliases.remove(artifactID);
                    this.needsSynchronizationToClient = true;
                }
            }
            return;
        }
        throw new IllegalArgumentException("unsupported announcement type: " + announcement.getClass());
    }

    public void dequeue(Collection<? super VoteSynchronizer.Announcement> drainTo) {
        drainTo.addAll(this.queued);
        this.queued.clear();
    }

    private void emitArtifactAnnouncement(UUID uuid) {
        String name = this.names.getOrDefault(uuid, "");
        Optional<String> alias = Optional.ofNullable(this.aliases.get(uuid)).filter(a -> !a.isEmpty());
        this.queued.add(new VoteSynchronizer.Artifact(new VoteSynchronizer.ArtifactKey(uuid), name, alias));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        VoteArtifactNames instance = VoteDataStorage.get(Objects.requireNonNull(player.getServer())).getArtifactNames();
        if (player instanceof ServerPlayer serverPlayer) {
            SyncArtifactNamePacket packet = SyncArtifactNamePacket.create(instance.names, instance.aliases);
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Pre event) {
        // noinspection DataFlowIssue
        VoteArtifactNames instance = VoteDataStorage.get(ServerLifecycleHooks.getCurrentServer()).getArtifactNames();
        if (instance.needsSynchronizationToClient) {
            PacketDistributor.sendToAllPlayers(SyncArtifactNamePacket.create(instance.names, instance.aliases));
            instance.needsSynchronizationToClient = false;
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

    public static Consumer<SyncArtifactNamePacket> handleServerPacket() {
        return packet -> {
            Client.VOTE_ARTIFACT_NAMES.names.clear();
            Client.VOTE_ARTIFACT_NAMES.aliases.clear();
            Client.VOTE_ARTIFACT_NAMES.names.putAll(packet.artifactNames);
            Client.VOTE_ARTIFACT_NAMES.aliases.putAll(packet.artifactAliases);
        };
    }

    public static VoteArtifactNames client() {
        return Client.VOTE_ARTIFACT_NAMES;
    }

    public static Optional<VoteArtifactNames> effective() {
        return switch (EffectiveSide.get()) {
            case CLIENT -> Optional.of(VoteArtifactNames.client());
            case SERVER -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                yield server != null ? Optional.of(VoteDataStorage.get(server).getArtifactNames()) : Optional.empty();
            }
        };
    }

    private static final class Client {
        public static final VoteArtifactNames VOTE_ARTIFACT_NAMES = new VoteArtifactNames(); // lazy classloading
    }
}
