package com.chrisapi;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

public abstract class BaseContextHandler {
    protected final ChrisAPIPlugin plugin;
    protected final Client client;
    protected final ClientThread clientThread;
    protected final ItemManager itemManager;

    protected BaseContextHandler(ChrisAPIPlugin plugin) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.clientThread = plugin.getClientThread();
        this.itemManager = plugin.getItemManager();
    }

    @Subscribe
    public abstract void onVarbitChanged(VarbitChanged varbitChanged);

    @Subscribe
    public abstract void onGameTick(GameTick tick);

    public abstract com.sun.net.httpserver.HttpHandler getHttpHandler();

    public abstract String getContextPath();
}
