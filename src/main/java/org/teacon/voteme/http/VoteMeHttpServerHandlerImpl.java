package org.teacon.voteme.http;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.roles.VoteRole;
import org.teacon.voteme.roles.VoteRoleHandler;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class VoteMeHttpServerHandlerImpl extends VoteMeHttpServerHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected HttpResponseStatus handle(QueryStringDecoder decoder, ByteBuf buf) {
        String path = decoder.path();
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
            return this.handleVoteLists(buf);
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
        VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
        Collection<? extends UUID> artifacts = handler.getArtifacts();
        return this.handleOK(buf, Util.make(new JsonArray(), result -> {
            for (UUID artifactID : artifacts) {
                result.add(handler.toHTTPJson(artifactID));
            }
        }));
    }

    private HttpResponseStatus handleArtifact(ByteBuf buf, String uuid) {
        try {
            UUID artifactID = UUID.fromString(uuid);
            VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
            Collection<? extends UUID> artifacts = handler.getArtifacts();
            Preconditions.checkArgument(artifacts.contains(artifactID));
            return this.handleOK(buf, handler.toHTTPJson(artifactID));
        } catch (IllegalArgumentException ignored) {
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

    private HttpResponseStatus handleVoteLists(ByteBuf buf) {
        VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
        Collection<? extends ResourceLocation> categories = VoteCategoryHandler.getIds();
        IntCollection ids = new IntRBTreeSet();
        handler.getArtifacts().forEach(artifactID -> {
            for (ResourceLocation categoryID : categories) {
                ids.add(handler.getIdOrCreate(artifactID, categoryID));
            }
        });
        return this.handleOK(buf, Util.make(new JsonArray(), result -> ids.forEach((int id) -> {
            Optional<VoteListEntry> entryOptional = handler.getEntry(id);
            entryOptional.ifPresent(entry -> result.add(entry.toHTTPJson(id)));
        })));
    }

    private HttpResponseStatus handleVoteList(ByteBuf buf, String idString) {
        // noinspection UnstableApiUsage
        Integer id = Ints.tryParse(idString);
        if (id != null) {
            VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
            Optional<VoteListEntry> entryOptional = handler.getEntry(id);
            if (entryOptional.isPresent()) {
                return this.handleOK(buf, entryOptional.get().toHTTPJson(id));
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
