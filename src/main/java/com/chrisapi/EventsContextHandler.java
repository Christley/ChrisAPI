package com.chrisapi;

import com.chrisapi.HttpServerPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class EventsContextHandler extends BaseContextHandler {
    private final JsonObject cachedEvents = new JsonObject();

    private String lastChatMessage = "";
    private String opponentName = "None";
    private int opponentHealthRatio = -1;
    private int opponentHealthScale = -1;

    public EventsContextHandler(HttpServerPlugin plugin) {
        super(plugin);
    }

    public void onChatMessage(ChatMessage event) {
        lastChatMessage = event.getMessage();
    }

    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheEvents();
    }

    private void collectAndCacheEvents() {
        clientThread.invoke(() -> {
            Player player = client.getLocalPlayer();
            if (player == null) return;

            JsonObject object = new JsonObject();
            JsonObject camera = new JsonObject();
            JsonObject worldPoint = new JsonObject();
            JsonObject mouse = new JsonObject();

            int energy = client.getEnergy();
            int processedEnergy = energy != 0 ? energy / 100 : 0;

            List<Integer> idlePoses = Arrays.asList(808, 813, 3418, 10075);
            boolean isIdle = player.getAnimation() == -1 && idlePoses.contains(player.getPoseAnimation());
            boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;

            if (player.getInteracting() instanceof NPC) {
                NPC opponent = (NPC) player.getInteracting();
                opponentName = opponent.getName();
                opponentHealthRatio = opponent.getHealthRatio();
                opponentHealthScale = opponent.getHealthScale();
            } else {
                opponentName = "None";
                opponentHealthRatio = -1;
                opponentHealthScale = -1;
            }

            object.addProperty("Animation ID", player.getAnimation());
            object.addProperty("Animation pose", player.getPoseAnimation());
            object.addProperty("Idling", isIdle);
            object.addProperty("Last chat message", lastChatMessage);
            object.addProperty("Run energy", processedEnergy);
            object.addProperty("Game tick", client.getGameCycle());
            object.addProperty("Logged in", loggedIn);
            object.addProperty("Current health", client.getBoostedSkillLevel(Skill.HITPOINTS));
            object.addProperty("Current prayer points", client.getBoostedSkillLevel(Skill.PRAYER));
            object.addProperty("Current weight", client.getWeight());
            object.addProperty("Interacting code", String.valueOf(player.getInteracting()));
            object.addProperty("NPC name", opponentName);
            object.addProperty("NPC health ratio", opponentHealthRatio);
            object.addProperty("NPC health scale", opponentHealthScale);

            mouse.addProperty("x", client.getMouseCanvasPosition().getX());
            mouse.addProperty("y", client.getMouseCanvasPosition().getY());

            WorldPoint wp = player.getWorldLocation();
            worldPoint.addProperty("x", wp.getX());
            worldPoint.addProperty("y", wp.getY());
            worldPoint.addProperty("plane", wp.getPlane());
            worldPoint.addProperty("regionID", wp.getRegionID());
            worldPoint.addProperty("regionX", wp.getRegionX());
            worldPoint.addProperty("regionY", wp.getRegionY());

            camera.addProperty("yaw", client.getCameraYaw());
            camera.addProperty("pitch", client.getCameraPitch());
            camera.addProperty("x", client.getCameraX());
            camera.addProperty("y", client.getCameraY());
            camera.addProperty("z", client.getCameraZ());

            object.add("worldPoint", worldPoint);
            object.add("camera", camera);
            object.add("mouse", mouse);

            synchronized (cachedEvents) {
                cachedEvents.entrySet().clear();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    cachedEvents.add(entry.getKey(), entry.getValue());
                }
            }
        });
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonObject eventsCopy;
            synchronized (cachedEvents) {
                eventsCopy = cachedEvents.deepCopy();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                RuneLiteAPI.GSON.toJson(eventsCopy, out);
            } catch (IOException e) {
                log.error("Error writing events response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/events";
    }
}