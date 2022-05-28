package org.teacon.voteme.http;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.IntComparators;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteArtifactNames;
import org.teacon.voteme.vote.VoteDataStorage;
import org.teacon.voteme.vote.VoteList;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class VoteMeHttpServerHandlerImpl extends VoteMeHttpServerHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected HttpResponseStatus handle(QueryStringDecoder decoder, ByteBuf buf) {
        String path = decoder.path();
        Map<String, List<String>> parameters = decoder.parameters();
        if ("/v1/artifacts".equals(path) || "/v1/artifacts/".equals(path)) {
            return this.handleArtifacts(buf);
        }
        if (path.startsWith("/v1/artifacts/")) {
            return this.handleArtifact(buf, path.substring("/v1/artifacts/".length()));
        }
        if ("/v1/categories".equals(path) || "/v1/categories/".equals(path)) {
            return this.handleCategories(buf);
        }
        if (path.startsWith("/v1/categories/")) {
            return this.handleCategory(buf, path.substring("/v1/categories/".length()));
        }
        if ("/v1/vote_lists".equals(path) || "/v1/vote_lists/".equals(path)) {
            return this.handleVoteLists(buf, parameters);
        }
        if (path.startsWith("/v1/vote_lists/")) {
            return this.handleVoteList(buf, path.substring("/v1/vote_lists/".length()));
        }
        if ("/v1/roles".equals(path) || "/v1/roles/".equals(path)) {
            return this.handleRoles(buf);
        }
        if (path.startsWith("/v1/roles/")) {
            return this.handleRole(buf, path.substring("/v1/roles/".length()));
        }
        return this.handleBadRequest(buf);
    }

    private HttpResponseStatus handleArtifacts(ByteBuf buf) {
        VoteDataStorage handler = VoteDataStorage.get(VoteMeHttpServer.getMinecraftServer());
        Collection<? extends UUID> artifacts = VoteArtifactNames.getArtifacts(false);
        return this.handleOK(buf, Util.make(new JsonArray(), result -> {
            for (UUID artifactID : artifacts) {
                result.add(handler.toArtifactHTTPJson(artifactID));
            }
        }));
    }

    private HttpResponseStatus handleArtifact(ByteBuf buf, String ref) {
        VoteDataStorage handler = VoteDataStorage.get(VoteMeHttpServer.getMinecraftServer());
        Optional<UUID> artifactIDOptional = VoteArtifactNames.getArtifactByAliasOrUUID(ref, false);
        if (artifactIDOptional.isPresent()) {
            UUID artifactID = artifactIDOptional.get();
            return this.handleOK(buf, handler.toArtifactHTTPJson(artifactID));
        } else {
            return this.handleNotFound(buf);
        }
    }

    private HttpResponseStatus handleCategories(ByteBuf buf) {
        return this.handleOK(buf, Util.make(new JsonArray(), result -> VoteCategoryHandler.getIds().forEach(id -> {
            Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
            if (categoryOptional.isPresent()) {
                VoteCategory category = categoryOptional.get();
                JsonElement child = category.toHTTPJson(id);
                result.add(child);
            }
        })));
    }

    private HttpResponseStatus handleCategory(ByteBuf buf, String id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(new ResourceLocation(id));
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            return this.handleOK(buf, category.toHTTPJson(new ResourceLocation(id)));
        }
        return this.handleNotFound(buf);
    }

    private HttpResponseStatus handleVoteLists(ByteBuf buf, Map<String, List<String>> parameters) {
        VoteDataStorage handler = VoteDataStorage.get(VoteMeHttpServer.getMinecraftServer());
        List<String> sorts = parameters.getOrDefault("sort", Collections.emptyList())
                .stream().flatMap(s -> Arrays.stream(s.split(","))).collect(Collectors.toList());
        @Nullable List<ResourceLocation> filteredCategories = !parameters.containsKey("category") ? null : parameters
                .get("category").stream().map(ResourceLocation::tryParse).filter(Objects::nonNull).toList();
        @Nullable List<UUID> filteredArtifacts = !parameters.containsKey("artifact") ? null : parameters
                .get("artifact").stream().map(ref -> VoteArtifactNames.getArtifactByAliasOrUUID(ref, false)).flatMap(Optional::stream).toList();
        Comparator<Integer> comparator = Comparator.naturalOrder();
        for (String sort : Lists.reverse(sorts)) {
            comparator = switch (sort) {
                case "score-ascending" -> Comparator.comparingDouble((Integer id) -> handler.getVoteList(id)
                        .map(VoteList::getFinalScore).orElse(Float.NaN)).thenComparing(comparator);
                case "score-descending" -> Comparator.comparingDouble((Integer id) -> handler.getVoteList(id)
                        .map(VoteList::getFinalScore).orElse(Float.NaN)).reversed().thenComparing(comparator);
                case "artifact-ascending" -> Comparator.comparing((Integer id) -> handler.getVoteList(id)
                        .map(VoteList::getArtifactID).orElse(new UUID(0, 0))).thenComparing(comparator);
                case "artifact-descending" -> Comparator.comparing((Integer id) -> handler.getVoteList(id)
                        .map(VoteList::getArtifactID).orElse(new UUID(0, 0))).reversed().thenComparing(comparator);
                default -> comparator;
            };
        }
        Collection<? extends ResourceLocation> categories = VoteCategoryHandler.getIds();
        Int2ObjectMap<VoteList> ids = new Int2ObjectRBTreeMap<>(IntComparators.asIntComparator(comparator));
        for (UUID artifactID : VoteArtifactNames.getArtifacts(false)) {
            if (filteredArtifacts == null || filteredArtifacts.contains(artifactID)) {
                for (ResourceLocation categoryID : categories) {
                    if (filteredCategories == null || filteredCategories.contains(categoryID)) {
                        int id = handler.getIdOrCreate(artifactID, categoryID);
                        boolean enabledDefault = VoteCategoryHandler
                                .getCategory(categoryID).filter(c -> c.enabledDefault).isPresent();
                        handler.getVoteList(id).filter(entry -> entry
                                .getEnabled().orElse(enabledDefault)).ifPresent(entry -> ids.put(id, entry));
                    }
                }
            }
        }
        return this.handleOK(buf, Util.make(new JsonArray(), result -> ids.values().forEach(voteList -> {
            JsonElement json = handler.toVoteListHTTPJson(voteList.getArtifactID(), voteList.getCategoryID());
            result.add(json);
        })));
    }

    private HttpResponseStatus handleVoteList(ByteBuf buf, String idString) {
        // noinspection UnstableApiUsage
        Integer id = Ints.tryParse(idString);
        if (id != null) {
            VoteDataStorage handler = VoteDataStorage.get(VoteMeHttpServer.getMinecraftServer());
            Optional<VoteList> entryOptional = handler.getVoteList(id);
            if (entryOptional.isPresent()) {
                VoteList entry = entryOptional.get();
                ResourceLocation categoryID = entry.getCategoryID();
                Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(categoryID);
                if (entry.getEnabled().orElse(categoryOptional.filter(c -> c.enabledDefault).isPresent())) {
                    return this.handleOK(buf, handler.toVoteListHTTPJson(entry.getArtifactID(), categoryID));
                }
            }
        }
        return this.handleNotFound(buf);
    }

    private HttpResponseStatus handleRoles(ByteBuf buf) {
        return this.handleOK(buf, Util.make(new JsonArray(), result -> VoteRoleHandler.getIds().forEach(id -> {
            Optional<VoteRole> roleOptional = VoteRoleHandler.getRole(id);
            if (roleOptional.isPresent()) {
                VoteRole role = roleOptional.get();
                JsonElement child = role.toHTTPJson(id);
                result.add(child);
            }
        })));
    }

    private HttpResponseStatus handleRole(ByteBuf buf, String id) {
        Optional<VoteRole> roleOptional = VoteRoleHandler.getRole(new ResourceLocation(id));
        if (roleOptional.isPresent()) {
            VoteRole role = roleOptional.get();
            return this.handleOK(buf, role.toHTTPJson(new ResourceLocation(id)));
        }
        return this.handleNotFound(buf);
    }

    private HttpResponseStatus handleOK(ByteBuf buf, JsonElement result) {
        try (Writer writer = new OutputStreamWriter(new ByteBufOutputStream(buf), StandardCharsets.UTF_8)) {
            GSON.toJson(result, writer);
            return HttpResponseStatus.OK;
        } catch (IOException | JsonIOException e) {
            buf.clear();
            return this.handleNotFound(buf);
        }
    }

    private HttpResponseStatus handleNotFound(ByteBuf buf) {
        ByteBufUtil.writeUtf8(buf, "{\n  \"error\": \"Not Found\"\n}");
        return HttpResponseStatus.NOT_FOUND;
    }

    private HttpResponseStatus handleBadRequest(ByteBuf buf) {
        ByteBufUtil.writeUtf8(buf, "{\n  \"error\": \"Bad Request\"\n}");
        return HttpResponseStatus.BAD_REQUEST;
    }
}
