package com.chrisapi;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HerbSackContextHandler extends BaseContextHandler {
    private boolean gettingHerbs = false;
    private final List<String> herbsInChatMessage = new ArrayList<>();
    private final Map<Integer, Integer> herbCounts = new HashMap<>();
    private long lastUpdatedTimestamp = 0;
    private boolean hasHerbSackInInventory = false;

    public HerbSackContextHandler(ChrisAPIPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged) {

    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Check if the herb sack is in inventory
        boolean hasHerbSack = false;
        ItemContainer inventory = client.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            if (items != null) {
                for (Item item : items) {
                    if (item != null) {
                        int itemId = item.getId();
                        if (itemId == ItemID.HERB_SACK || itemId == ItemID.OPEN_HERB_SACK) {
                            hasHerbSack = true;
                            break;
                        }
                    }
                }
            }
        }
        hasHerbSackInInventory = hasHerbSack;

        if (gettingHerbs && !herbsInChatMessage.isEmpty()) {
            parseHerbChatMessages();
            gettingHerbs = false;
            herbsInChatMessage.clear();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!event.getMenuOption().equals("Check")
                || (event.getItemId() != ItemID.HERB_SACK && event.getItemId() != ItemID.OPEN_HERB_SACK)) {
            return;
        }
        synchronized (this) {
            gettingHerbs = true;
            herbsInChatMessage.clear();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (!gettingHerbs || chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String messageString = Text.removeTags(chatMessage.getMessage());
        if (messageString.contains(" x Grimy")) {
            synchronized (this) {
                herbsInChatMessage.add(messageString);
            }
        }

        if (messageString.equals("The herb sack is empty.")) {
            synchronized (this) {
                herbCounts.clear();
                lastUpdatedTimestamp = System.currentTimeMillis();
                gettingHerbs = false;
                herbsInChatMessage.clear();
            }
        }
    }

    private void parseHerbChatMessages() {
        Map<Integer, Integer> newHerbCounts = new HashMap<>();

        for (String message : herbsInChatMessage) {
            String[] fullHerbName = message.split(" x ");
            if (fullHerbName.length == 2) {
                String quantityString = fullHerbName[0].trim();
                String herbName = fullHerbName[1].trim();
                int quantity;
                try {
                    quantity = Integer.parseInt(quantityString);
                } catch (NumberFormatException e) {
                    continue;
                }
                int itemId = getHerbItemIdByName(herbName);
                if (itemId != -1) {
                    newHerbCounts.put(itemId, quantity);
                }
            }
        }

        herbCounts.clear();
        herbCounts.putAll(newHerbCounts);
        lastUpdatedTimestamp = System.currentTimeMillis();
    }

    private int getHerbItemIdByName(String herbName) {
        // Map herb names to item IDs
        switch (herbName.toLowerCase()) {
            case "grimy guam leaf":
                return ItemID.GRIMY_GUAM_LEAF;
            case "grimy marrentill":
                return ItemID.GRIMY_MARRENTILL;
            case "grimy tarromin":
                return ItemID.GRIMY_TARROMIN;
            case "grimy harralander":
                return ItemID.GRIMY_HARRALANDER;
            case "grimy ranarr weed":
                return ItemID.GRIMY_RANARR_WEED;
            case "grimy irit leaf":
                return ItemID.GRIMY_IRIT_LEAF;
            case "grimy avantoe":
                return ItemID.GRIMY_AVANTOE;
            case "grimy kwuarm":
                return ItemID.GRIMY_KWUARM;
            case "grimy cadantine":
                return ItemID.GRIMY_CADANTINE;
            case "grimy lantadyme":
                return ItemID.GRIMY_LANTADYME;
            case "grimy dwarf weed":
                return ItemID.GRIMY_DWARF_WEED;
            case "grimy torstol":
                return ItemID.GRIMY_TORSTOL;
            default:
                return -1;
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return exchange -> {
            try {
                JsonObject jsonObject = new JsonObject();
                Map<Integer, String> herbNames = getHerbNames();

                synchronized (this) {
                    jsonObject.addProperty("herbsack_in_inventory", hasHerbSackInInventory);

                    long secondsSinceLastUpdate;
                    if (lastUpdatedTimestamp > 0) {
                        long currentTime = System.currentTimeMillis();
                        secondsSinceLastUpdate = (currentTime - lastUpdatedTimestamp) / 1000;
                    } else {
                        secondsSinceLastUpdate = -1; // Indicates that there has been no update yet
                    }
                    jsonObject.addProperty("seconds_since_last_update", secondsSinceLastUpdate);

                    if (hasHerbSackInInventory && lastUpdatedTimestamp > 0) {
                        // Add herb counts to jsonObject
                        for (Map.Entry<Integer, String> entry : herbNames.entrySet()) {
                            int itemId = entry.getKey();
                            String name = entry.getValue();
                            int quantity = herbCounts.getOrDefault(itemId, 0);
                            jsonObject.addProperty(name, quantity);
                        }
                    }
                }

                String response = RuneLiteAPI.GSON.toJson(jsonObject);

                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                    out.write(response);
                }
            } catch (Exception e) {
                log.error("Exception in HerbSackContextHandler getHttpHandler", e);
                // Send back an error response
                String errorResponse = "Internal Server Error";
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
                    out.write(errorResponse);
                }
            }
        };
    }

    private Map<Integer, String> getHerbNames() {
        Map<Integer, String> herbNames = new HashMap<>();
        // Map item IDs to herb names
        herbNames.put(ItemID.GRIMY_GUAM_LEAF, "grimy guam");
        herbNames.put(ItemID.GRIMY_MARRENTILL, "grimy marrentill");
        herbNames.put(ItemID.GRIMY_TARROMIN, "grimy tarromin");
        herbNames.put(ItemID.GRIMY_HARRALANDER, "grimy harralander");
        herbNames.put(ItemID.GRIMY_RANARR_WEED, "grimy ranarr");
        herbNames.put(ItemID.GRIMY_IRIT_LEAF, "grimy irit");
        herbNames.put(ItemID.GRIMY_AVANTOE, "grimy avantoe");
        herbNames.put(ItemID.GRIMY_KWUARM, "grimy kwuarm");
        herbNames.put(ItemID.GRIMY_CADANTINE, "grimy cadantine");
        herbNames.put(ItemID.GRIMY_LANTADYME, "grimy lantadyme");
        herbNames.put(ItemID.GRIMY_DWARF_WEED, "grimy dwarf weed");
        herbNames.put(ItemID.GRIMY_TORSTOL, "grimy torstol");
        return herbNames;
    }

    @Override
    public String getContextPath() {
        return "/herbsack";
    }
}
