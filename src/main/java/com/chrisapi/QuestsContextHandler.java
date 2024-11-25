package com.chrisapi;

import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class QuestsContextHandler extends BaseContextHandler {
    private JsonArray cachedQuests = new JsonArray(); // Removed 'final'

    public QuestsContextHandler(HttpServerPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheQuests();
    }

    private void collectAndCacheQuests() {
        clientThread.invoke(() -> {
            JsonArray jsonArray = new JsonArray();
            for (Quest quest : Quest.values()) {
                QuestState state = quest.getState(client);
                if (state != QuestState.NOT_STARTED) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("name", quest.getName());
                    jsonObject.addProperty("state", state.name());
                    jsonArray.add(jsonObject);
                }
            }
            synchronized (this) {
                cachedQuests = jsonArray; // Reassign with new data
            }
        });
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray questsCopy;
            synchronized (this) {
                questsCopy = cachedQuests.deepCopy();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                RuneLiteAPI.GSON.toJson(questsCopy, out);
            } catch (IOException e) {
                log.error("Error writing quests response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/quests";
    }
}
