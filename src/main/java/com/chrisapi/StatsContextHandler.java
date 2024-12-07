package com.chrisapi;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class StatsContextHandler extends BaseContextHandler {
    private JsonObject cachedStats = new JsonObject();

    public StatsContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Subscribe
    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheStats();
    }

    private void collectAndCacheStats() {
        // Since onGameTick is already on the client thread, we don't need clientThread.invoke()
        JsonObject jsonObject = new JsonObject();
        for (Skill skill : Skill.values()) {
            JsonObject skillObject = new JsonObject();
            int currentLevel = client.getRealSkillLevel(skill);
            int boostedLevel = client.getBoostedSkillLevel(skill);
            int experience = client.getSkillExperience(skill);
            skillObject.addProperty("currentLevel", currentLevel);
            skillObject.addProperty("boostedLevel", boostedLevel);
            skillObject.addProperty("experience", experience);
            jsonObject.add(skill.getName(), skillObject);
        }
        synchronized (this) {
            cachedStats = jsonObject;
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonObject statsCopy;
            synchronized (this) {
                statsCopy = cachedStats.deepCopy();
            }
            String response = RuneLiteAPI.GSON.toJson(statsCopy);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
            } catch (IOException e) {
                log.error("Error writing stats response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/stats";
    }
}
