package com.chrisapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
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
public class EquipmentContextHandler extends BaseContextHandler {
    private JsonArray cachedEquipment = new JsonArray();

    public EquipmentContextHandler(ChrisAPIPlugin plugin) {
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
        if (event.getContainerId() != InventoryID.EQUIPMENT.getId()) {
            return;
        }
        collectAndCacheEquipment();
    }

    private void collectAndCacheEquipment() {
        // No need for clientThread.invoke(), already on client thread
        ItemContainer itemContainer = client.getItemContainer(InventoryID.EQUIPMENT);
        if (itemContainer != null) {
            Item[] items = itemContainer.getItems();
            JsonArray jsonArray = new JsonArray();
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
                int slotIdx = slot.getSlotIdx();
                if (slotIdx >= items.length) {
                    continue;
                }
                Item item = items[slotIdx];
                if (item != null && item.getId() > 0) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("id", item.getId());
                    String itemName = itemManager.getItemComposition(item.getId()).getName();
                    jsonObject.addProperty("name", itemName);
                    jsonObject.addProperty("quantity", item.getQuantity());
                    jsonObject.addProperty("slot", slot.name().toLowerCase());
                    jsonArray.add(jsonObject);
                }
            }
            synchronized (this) {
                cachedEquipment = jsonArray;
            }
        } else {
            synchronized (this) {
                cachedEquipment = new JsonArray();
            }
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonArray equipmentCopy;
            synchronized (this) {
                equipmentCopy = cachedEquipment.deepCopy();
            }
            String response = RuneLiteAPI.GSON.toJson(equipmentCopy);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
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
