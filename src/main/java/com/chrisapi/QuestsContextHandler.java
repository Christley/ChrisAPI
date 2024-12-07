package com.chrisapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class QuestsContextHandler extends BaseContextHandler {
    private JsonArray cachedQuests = new JsonArray();

    public QuestsContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Subscribe
    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheQuests();
    }

    private void collectAndCacheQuests() {
        // No need for clientThread.invoke(), already on client thread
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
            cachedQuests = jsonArray;
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray questsCopy;
            synchronized (this) {
                questsCopy = cachedQuests.deepCopy();
            }
            String response = RuneLiteAPI.GSON.toJson(questsCopy);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
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
