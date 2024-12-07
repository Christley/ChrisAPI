package com.chrisapi;

import com.google.inject.Provides;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
        name = "Chris API",
        description = "Actively logs the player status and events to localhost",
        tags = {"status", "stats", "api"},
        enabledByDefault = true
)
@Slf4j
public class ChrisAPIPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ChrisAPIConfig config;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ItemManager itemManager;
    @Inject
    private EventBus eventBus;

    private HttpServer server;
    private final List<BaseContextHandler> contextHandlers = new ArrayList<>();

    public Client getClient() {
        return client;
    }

    public ClientThread getClientThread() {
        return clientThread;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    @Provides
    private ChrisAPIConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChrisAPIConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(config.portNum()), 0);

        // Initialize and add context handlers
        contextHandlers.add(new StatsContextHandler(this));
        contextHandlers.add(new MiscStatsContextHandler(this));
        contextHandlers.add(new EventsContextHandler(this));
        contextHandlers.add(new QuestsContextHandler(this));
        contextHandlers.add(new InventoryContextHandler(this));
        contextHandlers.add(new EquipmentContextHandler(this));
        contextHandlers.add(new HerbSackContextHandler(this));
        contextHandlers.add(new BankContextHandler(this));
        contextHandlers.add(new AccountInfoContextHandler(this));
        contextHandlers.add(new GuardiansContextHandler(this));

        // Create HTTP contexts and register event subscriptions
        for (BaseContextHandler handler : contextHandlers) {
            server.createContext(handler.getContextPath(), handler.getHttpHandler());
            eventBus.register(handler);
        }

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        // Register event subscriptions for this plugin if needed
        eventBus.register(this);

        log.info("HTTP server started on port " + config.portNum());
    }

    @Override
    protected void shutDown() throws Exception {
        server.stop(1);

        // Unregister event subscriptions
        eventBus.unregister(this);
        for (BaseContextHandler handler : contextHandlers) {
            eventBus.unregister(handler);
        }

        log.info("HTTP server stopped");
    }
}
