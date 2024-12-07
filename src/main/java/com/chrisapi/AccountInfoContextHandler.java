package com.chrisapi;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class AccountInfoContextHandler extends BaseContextHandler {
    private JsonObject cachedAccountInfo = new JsonObject();

    public AccountInfoContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        JsonObject accountInfo = new JsonObject();
        Player localPlayer = client.getLocalPlayer();

        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;

        accountInfo.addProperty("logged_in", loggedIn);

        if (loggedIn && localPlayer != null) {
            accountInfo.addProperty("account_hash", client.getAccountHash());
            accountInfo.addProperty("player_name", localPlayer.getName());
            accountInfo.addProperty("combat_level", localPlayer.getCombatLevel());
            accountInfo.addProperty("current_world", client.getWorld());
            accountInfo.addProperty("current_weight", client.getWeight());
        }

        synchronized (this) {
            cachedAccountInfo = accountInfo;
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonObject accountInfoCopy;
            synchronized (this) {
                accountInfoCopy = cachedAccountInfo.deepCopy();
            }

            String response = RuneLiteAPI.GSON.toJson(accountInfoCopy);

            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
            } catch (IOException e) {
                log.error("Error writing account info response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/accountinfo";
    }
}
