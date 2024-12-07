package com.chrisapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;

@Slf4j
public class MiscStatsContextHandler extends BaseContextHandler {
    private final JsonObject cachedMiscStats = new JsonObject();

    private boolean cannonPlaced = false;
    private int cballsLeft = 0;
    private String slayerTaskName = "No task";
    private int slayerKillsLeft = 0;
    private String opponentName = "None";
    private int opponentHealthRatio = -1;
    private int opponentHealthScale = -1;

    public MiscStatsContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Subscribe
    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        int varpId = varbitChanged.getVarpId();

        if (varpId == VarPlayer.SLAYER_TASK_SIZE) {
            slayerKillsLeft = client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE);
        } else if (varpId == VarPlayer.SLAYER_TASK_CREATURE) {
            int taskId = client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE);
            if (taskId == 98) { // Special case for bosses
                int structId = client.getEnum(EnumID.SLAYER_TASK)
                        .getIntValue(client.getVarbitValue(Varbits.SLAYER_TASK_BOSS));
                slayerTaskName = client.getStructComposition(structId)
                        .getStringValue(ParamID.SLAYER_TASK_NAME);
            } else {
                slayerTaskName = client.getEnum(EnumID.SLAYER_TASK_CREATURE)
                        .getStringValue(taskId);
            }
        } else if (varpId == VarPlayer.CANNON_AMMO) {
            cballsLeft = varbitChanged.getValue();
        } else if (varpId == VarPlayer.CANNON_STATE) {
            cannonPlaced = varbitChanged.getValue() == 4; // Assuming 4 means the cannon is placed
        }
    }
    @Subscribe
    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheMiscStats();
    }

    private void collectAndCacheMiscStats() {
        clientThread.invoke(() -> {
            Player player = client.getLocalPlayer();
            if (player != null && player.getInteracting() instanceof NPC) {
                NPC opponent = (NPC) player.getInteracting();
                opponentName = opponent.getName();
                opponentHealthRatio = opponent.getHealthRatio();
                opponentHealthScale = opponent.getHealthScale();
            } else {
                opponentName = "None";
                opponentHealthRatio = -1;
                opponentHealthScale = -1;
            }

            JsonObject response = new JsonObject();
            response.addProperty("Cannon state", cannonPlaced ? "Operational" : "Broken or not placed");
            response.addProperty("Cannonballs loaded", cballsLeft);
            response.addProperty("Slayer task", slayerTaskName);
            response.addProperty("Slayer kills left", slayerKillsLeft);
            response.addProperty("Opponent name", opponentName);
            response.addProperty("Opponent health ratio", opponentHealthRatio);
            response.addProperty("Opponent health scale", opponentHealthScale);

            synchronized (cachedMiscStats) {
                cachedMiscStats.entrySet().clear();
                for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
                    cachedMiscStats.add(entry.getKey(), entry.getValue());
                }
            }
        });
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonObject miscStatsCopy;
            synchronized (cachedMiscStats) {
                miscStatsCopy = cachedMiscStats.deepCopy();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                RuneLiteAPI.GSON.toJson(miscStatsCopy, out);
            } catch (IOException e) {
                log.error("Error writing misc stats response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/miscstats";
    }
}
