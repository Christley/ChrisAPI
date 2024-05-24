package com.chrisapi;

import net.runelite.api.events.ChatMessage;
import com.google.inject.Provides;
import net.runelite.api.events.GameTick;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.http.api.RuneLiteAPI;

@PluginDescriptor(
		name = "Chris API",
		description = "Actively logs the player status to localhost",
		tags = {"status", "stats"},
		enabledByDefault = true
)
@Slf4j
public class HttpServerPlugin extends Plugin
{
	private static final Duration WAIT = Duration.ofSeconds(5);
	@Inject
	public Client client;
	public Skill[] skillList;
	public XpTracker xpTracker;
	public Skill mostRecentSkillGained;
	public int tickCount = 0;
	public long startTime = 0;
	public long currentTime = 0;
	public int[] xp_gained_skills;
	@Inject
	public HttpServerConfig config;
	@Inject
	public ClientThread clientThread;
	public HttpServer server;
	public int MAX_DISTANCE = 1200;
	public String msg;
	@Provides
	private HttpServerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HttpServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		skillList = Skill.values();
		xpTracker = new XpTracker(this);
		server = HttpServer.create(new InetSocketAddress(config.portNum()), 0);
		server.createContext("/stats", this::handleStats);
		server.createContext("/inv", handlerForInv(InventoryID.INVENTORY));
		server.createContext("/equip", handlerForInv(InventoryID.EQUIPMENT));
		server.createContext("/events", this::handleEvents);
		server.setExecutor(Executors.newCachedThreadPool()); // Use multi-threaded executor
		startTime = System.currentTimeMillis();
		xp_gained_skills = new int[Skill.values().length];
		int skill_count = 0;
		server.start();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			xp_gained_skills[skill_count] = 0;
			skill_count++;
		}
		log.info("HTTP server started on port " + config.portNum());
	}

	@Override
	protected void shutDown() throws Exception
	{
		server.stop(1);
		log.info("HTTP server stopped");
	}
	public Client getClient() {
		return client;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		msg = event.getMessage();
		// System.out.println("onChatmsg:" + msg);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		currentTime = System.currentTimeMillis();
		xpTracker.update();
		int skill_count = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			int xp_gained = handleTracker(skill);
			xp_gained_skills[skill_count] = xp_gained;
			skill_count++;
		}
		tickCount++;
	}

	public int handleTracker(Skill skill)
	{
		int startingSkillXp = xpTracker.getXpData(skill, 0);
		int endingSkillXp = xpTracker.getXpData(skill, tickCount);
		int xpGained = endingSkillXp - startingSkillXp;
		return xpGained;
	}

	public void handleStats(HttpExchange exchange) throws IOException
	{
		synchronized (this) { // Ensure thread safety
			Player player = client.getLocalPlayer();
			JsonArray skills = new JsonArray();
			JsonObject headers = new JsonObject();
			headers.addProperty("username", client.getUsername());
			headers.addProperty("account hash", client.getAccountHash());
			headers.addProperty("player name", player.getName());
			int skill_count = 0;
			skills.add(headers);
			for (Skill skill : Skill.values())
			{
				if (skill == Skill.OVERALL)
				{
					continue;
				}
				int realLevel = client.getRealSkillLevel(skill);
				int boostedLevel = client.getBoostedSkillLevel(skill);
				int boostedVsLevel = boostedLevel - realLevel;
				JsonObject object = new JsonObject();
				object.addProperty("Skill", skill.getName());
				object.addProperty("Level", realLevel);
				object.addProperty("Boosted level", boostedLevel);
				object.addProperty("Boosted amount", boostedVsLevel);
				object.addProperty("XP", client.getSkillExperience(skill));
				object.addProperty("Xp gained", String.valueOf(xp_gained_skills[skill_count]));
				skills.add(object);
				skill_count++;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
			{
				RuneLiteAPI.GSON.toJson(skills, out);
			}
		}
	}

	public void handleEvents(HttpExchange exchange) throws IOException
	{
		synchronized (this) { // Ensure thread safety
			Player player = client.getLocalPlayer();
			Actor npc = player.getInteracting();
			String npcName;
			int npcHealth;
			int npcHealth2;
			int health;
			int minHealth = 0;
			int maxHealth = 0;
			if (npc != null)
			{
				npcName = npc.getName();
				npcHealth = npc.getHealthScale();
				npcHealth2 = npc.getHealthRatio();
				health = 0;
				if (npcHealth2 > 0)
				{
					minHealth = 1;
					if (npcHealth > 1)
					{
						if (npcHealth2 > 1)
						{
							// This doesn't apply if healthRatio = 1, because of the special case in the server calculation that
							// health = 0 forces healthRatio = 0 instead of the expected healthRatio = 1
							minHealth = (npcHealth * (npcHealth2 - 1) + npcHealth - 2) / (npcHealth - 1);
						}
						maxHealth = (npcHealth * npcHealth2 - 1) / (npcHealth - 1);
						if (maxHealth > npcHealth)
						{
							maxHealth = npcHealth;
						}
					}
					else
					{
						// If healthScale is 1, healthRatio will always be 1 unless health = 0
						// so we know nothing about the upper limit except that it can't be higher than maxHealth
						maxHealth = npcHealth;
					}
					// Take the average of min and max possible healths
					health = (minHealth + maxHealth + 1) / 2;
				}
			}
			else
			{
				npcName = "null";
				npcHealth = 0;
				npcHealth2 = 0;
				health = 0;
			}
			int energy = (int) client.getEnergy();
			int processedEnergy = energy != 0 ? energy / 100 : 0;
			JsonObject object = new JsonObject();
			JsonObject camera = new JsonObject();
			JsonObject worldPoint = new JsonObject();
			JsonObject mouse = new JsonObject();
			object.addProperty("Animation ID", player.getAnimation());
			object.addProperty("Animation pose", player.getPoseAnimation());
			object.addProperty("Last chat message", msg);
			object.addProperty("Run energy", processedEnergy);
			object.addProperty("Game tick", client.getGameCycle());
			object.addProperty("Current health", client.getBoostedSkillLevel(Skill.HITPOINTS));
			object.addProperty("Interacting code", String.valueOf(player.getInteracting()));
			object.addProperty("NPC name", npcName);
			object.addProperty("NPC health ", minHealth);
			object.addProperty("MAX_DISTANCE", MAX_DISTANCE);
			mouse.addProperty("x", client.getMouseCanvasPosition().getX());
			mouse.addProperty("y", client.getMouseCanvasPosition().getY());
			worldPoint.addProperty("x", player.getWorldLocation().getX());
			worldPoint.addProperty("y", player.getWorldLocation().getY());
			worldPoint.addProperty("plane", player.getWorldLocation().getPlane());
			worldPoint.addProperty("regionID", player.getWorldLocation().getRegionID());
			worldPoint.addProperty("regionX", player.getWorldLocation().getRegionX());
			worldPoint.addProperty("regionY", player.getWorldLocation().getRegionY());
			camera.addProperty("yaw", client.getCameraYaw());
			camera.addProperty("pitch", client.getCameraPitch());
			camera.addProperty("x", client.getCameraX());
			camera.addProperty("y", client.getCameraY());
			camera.addProperty("z", client.getCameraZ());
			object.add("worldPoint", worldPoint);
			object.add("camera", camera);
			object.add("mouse", mouse);
			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
			{
				RuneLiteAPI.GSON.toJson(object, out);
			}
		}
	}

	private HttpHandler handlerForInv(InventoryID inventoryID)
	{
		return exchange -> {
			Item[] items = invokeAndWait(() -> {
				ItemContainer itemContainer = client.getItemContainer(inventoryID);
				if (itemContainer != null)
				{
					return itemContainer.getItems();
				}
				return null;
			});

			if (items == null)
			{
				exchange.sendResponseHeaders(204, 0);
				return;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
			{
				RuneLiteAPI.GSON.toJson(items, out);
			}
		};
	}

	private <T> T invokeAndWait(Callable<T> r)
	{
		try
		{
			AtomicReference<T> ref = new AtomicReference<>();
			Semaphore semaphore = new Semaphore(0);
			clientThread.invokeLater(() -> {
				try
				{
					ref.set(r.call());
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
				finally
				{
					semaphore.release();
				}
			});
			semaphore.acquire();
			return ref.get();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
