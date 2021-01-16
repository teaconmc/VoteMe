package org.teacon.voteme.http;

import com.google.common.primitives.Ints;
import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import org.teacon.voteme.category.VoteCategory;
import org.teacon.voteme.category.VoteCategoryHandler;
import org.teacon.voteme.vote.VoteListEntry;
import org.teacon.voteme.vote.VoteListHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class VoteMeHttpServerHandlerImpl extends VoteMeHttpServerHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected HttpResponseStatus handle(QueryStringDecoder decoder, ByteBuf buf) {
        String path = decoder.path();
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
        return this.handleBadRequest(buf);
    }

    private HttpResponseStatus handleCategories(ByteBuf buf) {
        return this.handleOK(buf, Util.make(new JsonArray(), result -> VoteCategoryHandler.getIds().forEach(id -> {
            Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(id);
            if (categoryOptional.isPresent()) {
                VoteCategory category = categoryOptional.get();
                JsonObject child = new JsonObject();
                child.addProperty("id", id.toString());
                category.toJson(child);
                result.add(child);
            }
        })));
    }

    private HttpResponseStatus handleCategory(ByteBuf buf, String id) {
        Optional<VoteCategory> categoryOptional = VoteCategoryHandler.getCategory(new ResourceLocation(id));
        if (categoryOptional.isPresent()) {
            VoteCategory category = categoryOptional.get();
            return this.handleOK(buf, Util.make(new JsonObject(), result -> {
                result.addProperty("id", id);
                category.toJson(result);
            }));
        }
        return this.handleNotFound(buf);
    }

    private HttpResponseStatus handleVoteLists(ByteBuf buf) {
        VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
        return this.handleOK(buf, Util.make(new JsonArray(), result -> handler.getIds().forEach((int id) -> {
            Optional<VoteListEntry> entryOptional = handler.getEntry(id);
            entryOptional.ifPresent(entry -> {
                JsonObject child = new JsonObject();
                child.addProperty("id", id);
                entry.toJson(child);
                result.add(child);
            });
        })));
    }

    private HttpResponseStatus handleVoteList(ByteBuf buf, String id) {
        // noinspection UnstableApiUsage
        Integer idInt = Ints.tryParse(id);
        if (idInt != null) {
            VoteListHandler handler = VoteListHandler.get(VoteMeHttpServer.getMinecraftServer());
            Optional<VoteListEntry> entryOptional = handler.getEntry(idInt);
            if (entryOptional.isPresent()) {
                return this.handleOK(buf, Util.make(new JsonObject(), result -> {
                    result.addProperty("id", idInt);
                    entryOptional.get().toJson(result);
                }));
            }
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
