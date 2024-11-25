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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
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
public class HttpServerPlugin extends Plugin {
	@Inject
	private Client client;
	@Inject
	private HttpServerConfig config;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ItemManager itemManager;
	@Inject
	private EventBus eventBus;

	private HttpServer server;
	private final List<BaseContextHandler> contextHandlers = new ArrayList<>();

	// Instantiate context handlers
	private StatsContextHandler statsContextHandler;
	private MiscStatsContextHandler miscStatsContextHandler;
	private EventsContextHandler eventsContextHandler;
	private QuestsContextHandler questsContextHandler;
	private InventoryContextHandler inventoryContextHandler;
	private EquipmentContextHandler equipmentContextHandler;
	private HerbSackContextHandler herbSackContextHandler;

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
	private HttpServerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(HttpServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress(config.portNum()), 0);

		// Initialize context handlers
		statsContextHandler = new StatsContextHandler(this);
		miscStatsContextHandler = new MiscStatsContextHandler(this);
		eventsContextHandler = new EventsContextHandler(this);
		questsContextHandler = new QuestsContextHandler(this);
		inventoryContextHandler = new InventoryContextHandler(this);
		equipmentContextHandler = new EquipmentContextHandler(this);
		herbSackContextHandler = new HerbSackContextHandler(this);



		contextHandlers.add(statsContextHandler);
		contextHandlers.add(miscStatsContextHandler);
		contextHandlers.add(eventsContextHandler);
		contextHandlers.add(questsContextHandler);
		contextHandlers.add(inventoryContextHandler);
		contextHandlers.add(equipmentContextHandler);
		contextHandlers.add(herbSackContextHandler);

		// Create HTTP contexts
		for (BaseContextHandler handler : contextHandlers) {
			server.createContext(handler.getContextPath(), handler.getHttpHandler());
		}

		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		// Register event subscriptions
		eventBus.register(this);
		eventBus.register(herbSackContextHandler);

		log.info("HTTP server started on port " + config.portNum());
	}

	@Override
	protected void shutDown() throws Exception {
		server.stop(1);

		// Unregister event subscriptions
		eventBus.unregister(this);
		eventBus.unregister(herbSackContextHandler);

		log.info("HTTP server stopped");
	}

	// Event handling methods
	@Subscribe
	public void onGameTick(GameTick tick) {
		statsContextHandler.onGameTick(tick);
		miscStatsContextHandler.onGameTick(tick);
		eventsContextHandler.onGameTick(tick);
		questsContextHandler.onGameTick(tick);
		inventoryContextHandler.onGameTick(tick);
		equipmentContextHandler.onGameTick(tick);
		herbSackContextHandler.onGameTick(tick);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		miscStatsContextHandler.onVarbitChanged(event);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		eventsContextHandler.onChatMessage(event);
	}
}
