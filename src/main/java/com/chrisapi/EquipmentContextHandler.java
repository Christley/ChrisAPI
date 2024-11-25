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
public class EquipmentContextHandler extends BaseContextHandler {
    private JsonArray cachedEquipment = new JsonArray(); // Removed 'final'

    public EquipmentContextHandler(HttpServerPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onGameTick(GameTick tick) {
        collectAndCacheEquipment();
    }

    private void collectAndCacheEquipment() {
        clientThread.invoke(() -> {
            ItemContainer itemContainer = client.getItemContainer(InventoryID.EQUIPMENT);
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
                    cachedEquipment = jsonArray; // Reassign with new data
                }
            } else {
                synchronized (this) {
                    cachedEquipment = new JsonArray(); // Empty JsonArray if equipment is null
                }
            }
        });
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray equipmentCopy;
            synchronized (this) {
                equipmentCopy = cachedEquipment.deepCopy();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                RuneLiteAPI.GSON.toJson(equipmentCopy, out);
            } catch (IOException e) {
                log.error("Error writing equipment response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/equipment";
    }
}
