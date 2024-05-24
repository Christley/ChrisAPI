package com.chrisapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

@Slf4j
@PluginDescriptor(
		name = "Chris API",
		description = "Actively logs the player status and events to localhost",
		tags = {"status", "stats", "api"},
		enabledByDefault = true
)
public class ChrisAPIPlugin extends Plugin {
	private HttpServer server;
	private final List<String> chatMessages = new LinkedList<>();

	@Inject
	private ChrisAPIConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Override
	protected void startUp() throws Exception {
		startWebServer(config.portNum());
	}

	@Override
	protected void shutDown() throws Exception {
		stopWebServer();
	}

	@Provides
	ChrisAPIConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ChrisAPIConfig.class);
	}

	private void startWebServer(int port) {
		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/skills", new SkillsHandler(client));
			server.createContext("/accountinfo", new AccountInfoHandler(client));
			server.createContext("/events", new EventsHandler(client, chatMessages));
			server.createContext("/quests", new QuestsHandler(client, clientThread));
			server.createContext("/inventory", new InventoryHandler(client, clientThread));
			server.createContext("/equipment", new EquipmentHandler(client, clientThread));
			server.createContext("/bank", new BankHandler(client, clientThread));
			server.createContext("/combat", new CombatHandler(client));
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			log.info("Web server started on port {}", port);
		} catch (IOException e) {
			log.error("Failed to start web server", e);
		}
	}

	private void stopWebServer() {
		if (server != null) {
			server.stop(0);
			log.info("Web server stopped");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		synchronized (chatMessages) {
			chatMessages.add(event.getMessage());
			if (chatMessages.size() > 5) {
				chatMessages.remove(0);
			}
		}
	}

	static class SkillsHandler implements HttpHandler {
		private final Client client;

		public SkillsHandler(Client client) {
			this.client = client;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			JsonArray skillsArray = new JsonArray();
			for (Skill skill : Skill.values()) {
				JsonObject skillObject = new JsonObject();
				skillObject.addProperty("Skill name", skill.getName());
				skillObject.addProperty("Level", client.getRealSkillLevel(skill));
				skillObject.addProperty("Boosted level", client.getBoostedSkillLevel(skill));
				skillObject.addProperty("Boosted amount", client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill));
				skillObject.addProperty("Current XP", client.getSkillExperience(skill));
				skillsArray.add(skillObject);
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(skillsArray, out);
			}
		}
	}

	static class AccountInfoHandler implements HttpHandler {
		private final Client client;

		public AccountInfoHandler(Client client) {
			this.client = client;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			JsonObject accountInfo = new JsonObject();
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null) {
				log.error("Local player is null");
				exchange.sendResponseHeaders(500, 0);
				exchange.close();
				return;
			}

			accountInfo.addProperty("Account hash", client.getAccountHash());
			accountInfo.addProperty("Player name", localPlayer.getName());
			accountInfo.addProperty("Logged in", client.getGameState().name().equals("LOGGED_IN"));
			accountInfo.addProperty("Combat level", localPlayer.getCombatLevel());
			accountInfo.addProperty("Current world", client.getWorld());
			accountInfo.addProperty("Current weight", client.getWeight());

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(accountInfo, out);
			}
		}
	}

	static class EventsHandler implements HttpHandler {
		private final Client client;
		private final List<String> chatMessages;
		private final List<Integer> idlePoses = Arrays.asList(808, 813, 3418, 10075);

		public EventsHandler(Client client, List<String> chatMessages) {
			this.client = client;
			this.chatMessages = chatMessages;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			JsonObject eventsData = new JsonObject();
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null) {
				log.error("Local player is null");
				exchange.sendResponseHeaders(500, 0);
				exchange.close();
				return;
			}

			eventsData.addProperty("Animation ID", localPlayer.getAnimation());
			eventsData.addProperty("Animation pose ID", localPlayer.getPoseAnimation());
			boolean isIdle = localPlayer.getAnimation() == -1 && idlePoses.contains(localPlayer.getPoseAnimation());
			eventsData.addProperty("Is idle", isIdle);

			synchronized (chatMessages) {
				if (!chatMessages.isEmpty()) {
					eventsData.addProperty("Last chat message", chatMessages.get(chatMessages.size() - 1));
					JsonArray last5ChatMessages = new JsonArray();
					for (String message : chatMessages) {
						last5ChatMessages.add(message);
					}
					eventsData.add("Last 5 chat messages", last5ChatMessages);
				} else {
					eventsData.addProperty("Last chat message", (String) null);
					eventsData.add("Last 5 chat messages", new JsonArray());
				}
			}

			int energy = client.getEnergy();
			int processedEnergy = energy != 0 ? energy / 100 : 0;
			eventsData.addProperty("Current run energy", processedEnergy);

			int specialAttack = client.getVarpValue(300) / 10;
			eventsData.addProperty("Current special attack energy", specialAttack);

			WorldPoint worldLocation = localPlayer.getWorldLocation();
			eventsData.addProperty("World location", String.format("X: %d, Y: %d, Plane: %d, RegionID: %d, RegionX: %d, RegionY: %d", worldLocation.getX(), worldLocation.getY(), worldLocation.getPlane(), worldLocation.getRegionID(), worldLocation.getRegionX(), worldLocation.getRegionY()));

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(eventsData, out);
			}
		}
	}

	static class QuestsHandler implements HttpHandler {
		private final Client client;
		private final ClientThread clientThread;

		public QuestsHandler(Client client, ClientThread clientThread) {
			this.client = client;
			this.clientThread = clientThread;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			clientThread.invokeLater(() -> {
				JsonArray questsArray = new JsonArray();
				for (Quest quest : Quest.values()) {
					JsonObject questObject = new JsonObject();
					questObject.addProperty("Name", quest.getName());
					QuestState state = quest.getState(client);
					questObject.addProperty("State", state.name());
					questsArray.add(questObject);
				}

				JsonObject questPoints = new JsonObject();
				questPoints.addProperty("Current quest points", client.getVarpValue(101) + "/308");
				questsArray.add(questPoints);

				try {
					exchange.sendResponseHeaders(200, 0);
					try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
						RuneLiteAPI.GSON.toJson(questsArray, out);
					}
				} catch (IOException e) {
					log.error("Error writing quests response", e);
				}
			});
		}
	}

	static class InventoryHandler implements HttpHandler {
		private final Client client;
		private final ClientThread clientThread;

		public InventoryHandler(Client client, ClientThread clientThread) {
			this.client = client;
			this.clientThread = clientThread;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			clientThread.invokeLater(() -> {
				JsonArray inventoryArray = new JsonArray();
				ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

				if (inventory != null) {
					Item[] items = inventory.getItems();
					for (int i = 0; i < 28; i++) {
						JsonObject itemObject = new JsonObject();
						if (i < items.length) {
							Item item = items[i];
							itemObject.addProperty("Item ID", item.getId());
							itemObject.addProperty("Name", client.getItemDefinition(item.getId()).getName());
							itemObject.addProperty("Quantity", item.getQuantity());
						} else {
							itemObject.addProperty("Item ID", -1);
							itemObject.addProperty("Name", "Empty");
							itemObject.addProperty("Quantity", 0);
						}
						inventoryArray.add(itemObject);
					}
				}

				JsonObject response = new JsonObject();
				response.add("Inventory", inventoryArray);

				try {
					exchange.sendResponseHeaders(200, 0);
					try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
						RuneLiteAPI.GSON.toJson(response, out);
					}
				} catch (IOException e) {
					log.error("Error writing inventory response", e);
				}
			});
		}
	}

	static class EquipmentHandler implements HttpHandler {
		private final Client client;
		private final ClientThread clientThread;

		public EquipmentHandler(Client client, ClientThread clientThread) {
			this.client = client;
			this.clientThread = clientThread;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			clientThread.invokeLater(() -> {
				JsonArray equipmentArray = new JsonArray();
				ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);

				if (equipment != null) {
					Item[] items = equipment.getItems();
					for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
						JsonObject itemObject = new JsonObject();
						int slotIdx = slot.getSlotIdx();
						if (slotIdx < items.length) {
							Item item = items[slotIdx];
							if (item.getId() != -1) {
								itemObject.addProperty("Slot name", slot.name());
								itemObject.addProperty("Item ID", item.getId());
								itemObject.addProperty("Name", client.getItemDefinition(item.getId()).getName());
								itemObject.addProperty("Quantity", item.getQuantity());
							} else {
								itemObject.addProperty("Slot name", slot.name());
								itemObject.addProperty("Item ID", -1);
								itemObject.addProperty("Name", "Empty");
								itemObject.addProperty("Quantity", 0);
							}
						} else {
							itemObject.addProperty("Slot name", slot.name());
							itemObject.addProperty("Item ID", -1);
							itemObject.addProperty("Name", "Empty");
							itemObject.addProperty("Quantity", 0);
						}
						equipmentArray.add(itemObject);
					}
				}

				try {
					exchange.sendResponseHeaders(200, 0);
					try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
						RuneLiteAPI.GSON.toJson(equipmentArray, out);
					}
				} catch (IOException e) {
					log.error("Error writing equipment response", e);
				}
			});
		}
	}

	static class BankHandler implements HttpHandler {
		private final Client client;
		private final ClientThread clientThread;

		public BankHandler(Client client, ClientThread clientThread) {
			this.client = client;
			this.clientThread = clientThread;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			clientThread.invokeLater(() -> {
				JsonObject bankData = new JsonObject();
				Widget bankWidget = client.getWidget(InterfaceID.BANK, 0); // Using InterfaceID instead of WidgetInfo
				boolean bankOpen = bankWidget != null && !bankWidget.isHidden();
				bankData.addProperty("Is bank open", bankOpen);

				if (bankOpen) {
					JsonArray bankItemsArray = new JsonArray();
					ItemContainer bank = client.getItemContainer(InventoryID.BANK);
					if (bank != null) {
						Item[] items = bank.getItems();
						for (int i = 0; i < items.length; i++) {
							Item item = items[i];
							JsonObject itemObject = new JsonObject();
							itemObject.addProperty("Item ID", item.getId());
							itemObject.addProperty("Name", client.getItemDefinition(item.getId()).getName());
							itemObject.addProperty("Quantity", item.getQuantity());
							bankItemsArray.add(itemObject);
						}
					}
					bankData.add("Items", bankItemsArray);
				}

				try {
					exchange.sendResponseHeaders(200, 0);
					try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
						RuneLiteAPI.GSON.toJson(bankData, out);
					}
				} catch (IOException e) {
					log.error("Error writing bank response", e);
				}
			});
		}
	}

	static class CombatHandler implements HttpHandler {
		private final Client client;

		public CombatHandler(Client client) {
			this.client = client;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			JsonObject combatData = new JsonObject();
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null) {
				log.error("Local player is null");
				exchange.sendResponseHeaders(500, 0);
				exchange.close();
				return;
			}

			Actor opponent = localPlayer.getInteracting();

			if (opponent != null && opponent.getHealthRatio() > 0) {
				combatData.addProperty("In combat", true);
				combatData.addProperty("NPC name", opponent.getName());
			} else {
				combatData.addProperty("In combat", false);
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(combatData, out);
			}
		}
	}
}
