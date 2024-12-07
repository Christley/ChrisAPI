package com.chrisapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class InventoryContextHandler extends BaseContextHandler {
    private JsonArray cachedInventory = new JsonArray();

    public InventoryContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Override
    public void onGameTick(GameTick tick) {

    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }
        collectAndCacheInventory();
    }

    private void collectAndCacheInventory() {
        // No need for clientThread.invoke(), already on client thread
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
                cachedInventory = jsonArray;
            }
        } else {
            synchronized (this) {
                cachedInventory = new JsonArray();
            }
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray inventoryCopy;
            synchronized (this) {
                inventoryCopy = cachedInventory.deepCopy();
            }
            String response = RuneLiteAPI.GSON.toJson(inventoryCopy);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
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
