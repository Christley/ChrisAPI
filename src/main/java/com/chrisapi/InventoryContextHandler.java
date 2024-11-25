package com.chrisapi;

import com.chrisapi.HttpServerPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class InventoryContextHandler extends BaseContextHandler {
    private JsonArray cachedInventory = new JsonArray(); // Removed 'final'

    public InventoryContextHandler(HttpServerPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheInventory();
    }

    private void collectAndCacheInventory() {
        clientThread.invoke(() -> {
            ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
            if (itemContainer != null) {
                Item[] items = itemContainer.getItems();
                JsonArray jsonArray = new JsonArray();
                for (Item item : items) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("id", item.getId());
                    String itemName = item.getId() != -1
                            ? itemManager.getItemComposition(item.getId()).getName()
                            : "Unknown";
                    jsonObject.addProperty("name", itemName);
                    jsonObject.addProperty("quantity", item.getQuantity());
                    jsonArray.add(jsonObject);
                }
                synchronized (this) {
                    cachedInventory = jsonArray; // Reassign the cachedInventory
                }
            } else {
                synchronized (this) {
                    cachedInventory = new JsonArray(); // Empty JsonArray if inventory is null
                }
            }
        });
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray inventoryCopy;
            synchronized (this) {
                inventoryCopy = cachedInventory.deepCopy();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                RuneLiteAPI.GSON.toJson(inventoryCopy, out);
            } catch (IOException e) {
                log.error("Error writing inventory response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/inventory";
    }
}
