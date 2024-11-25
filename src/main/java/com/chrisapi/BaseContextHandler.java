package com.chrisapi;

import com.chrisapi.HttpServerPlugin;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

public abstract class BaseContextHandler {
    protected final HttpServerPlugin plugin;
    protected final Client client;
    protected final ClientThread clientThread;
    protected final ItemManager itemManager;

    protected BaseContextHandler(HttpServerPlugin plugin) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.clientThread = plugin.getClientThread();
        this.itemManager = plugin.getItemManager();
    }

    public abstract void onGameTick(net.runelite.api.events.GameTick tick);

    public abstract com.sun.net.httpserver.HttpHandler getHttpHandler();

    public abstract String getContextPath();
}
