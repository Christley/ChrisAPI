package com.chrisapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;

@Slf4j
public class BankContextHandler extends BaseContextHandler {
    private JsonObject cachedBankData = new JsonObject();

    public BankContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        JsonObject bankData = new JsonObject();

        Widget bankWidget = client.getWidget(WidgetInfo.BANK_TITLE_BAR);
        boolean bankOpen = bankWidget != null && !bankWidget.isHidden();

        bankData.addProperty("bank_open", bankOpen);

        if (bankOpen) {
            JsonArray bankItemsArray = new JsonArray();
            ItemContainer bank = client.getItemContainer(InventoryID.BANK);
            if (bank != null) {
                Item[] items = bank.getItems();
                for (Item item : items) {
                    if (item != null && item.getId() > 0) {
                        JsonObject itemObject = new JsonObject();
                        int itemId = item.getId();
                        itemObject.addProperty("item_id", itemId);

                        ItemComposition itemComposition = client.getItemDefinition(itemId);
                        String itemName = itemComposition != null ? itemComposition.getName() : "Unknown";
                        itemObject.addProperty("name", itemName);

                        itemObject.addProperty("quantity", item.getQuantity());
                        bankItemsArray.add(itemObject);
                    }
                }
            }
            bankData.add("items", bankItemsArray);
        }

        synchronized (this) {
            cachedBankData = bankData;
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            JsonObject bankDataCopy;
            synchronized (this) {
                bankDataCopy = cachedBankData.deepCopy();
            }

            String response = RuneLiteAPI.GSON.toJson(bankDataCopy);

            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                out.write(response);
            } catch (IOException e) {
                log.error("Error writing bank response", e);
            }
        };
    }

    @Override
    public String getContextPath() {
        return "/bank";
    }
}
