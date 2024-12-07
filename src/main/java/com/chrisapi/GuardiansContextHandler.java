package com.chrisapi;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.Renderable;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.http.api.RuneLiteAPI;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This handler uses the exact logic from the working plugin:
 * - Uses GuardianInfo.ALL to map spriteIds to guardians
 * - Uses parseRuneWidget to spawn/despawn guardians
 * - Checks animations to confirm active guardians
 */
@Slf4j
public class GuardiansContextHandler extends BaseContextHandler
{
    // Taken from the working plugin:
    private static final int GUARDIAN_ACTIVE_ANIM = 9363;
    private static final int ELEMENTAL_RUNE_WIDGET_ID = 48889879;
    private static final int CATALYTIC_RUNE_WIDGET_ID = 48889876;

    // Guardian game object IDs from GuardianInfo
    private static final Set<Integer> GUARDIAN_IDS = GuardianInfo.ALL.stream().mapToInt(x -> x.gameObjectId).boxed().collect(Collectors.toSet());

    // Track current guardians in the scene
    private final Set<GameObject> guardians = new HashSet<>();

    // Track the last elemental and catalytic sprites
    private int lastElementalRuneSprite = -1;
    private int lastCatalyticRuneSprite = -1;

    // Current plugin config placeholder
    private final GuardiansOfTheRiftHelperConfig gotrConfig = new GuardiansOfTheRiftHelperConfig();

    private JsonObject cachedGuardians = new JsonObject();

    public GuardiansContextHandler(ChrisAPIPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        // Not using varbits directly for this logic
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject obj = event.getGameObject();
        if (GUARDIAN_IDS.contains(obj.getId()))
        {
            synchronized (guardians)
            {
                guardians.add(obj);
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject obj = event.getGameObject();
        if (GUARDIAN_IDS.contains(obj.getId()))
        {
            synchronized (guardians)
            {
                guardians.remove(obj);
            }
        }
    }

    @Subscribe
    @Override
    public void onGameTick(GameTick tick)
    {
        collectAndCacheGuardians();
    }

    private void collectAndCacheGuardians()
    {
        // Determine active guardians by animation
        List<GameObject> activeGuardians = new ArrayList<>();
        synchronized (guardians)
        {
            for (GameObject g : guardians)
            {
                Renderable renderable = g.getRenderable();
                if (renderable instanceof DynamicObject)
                {
                    Animation anim = ((DynamicObject) renderable).getAnimation();
                    if (anim != null && anim.getId() == GUARDIAN_ACTIVE_ANIM)
                    {
                        activeGuardians.add(g);
                    }
                }
            }
        }

        // Get current rune widgets
        Widget elementalRuneWidget = client.getWidget(ELEMENTAL_RUNE_WIDGET_ID);
        Widget catalyticRuneWidget = client.getWidget(CATALYTIC_RUNE_WIDGET_ID);

        // Update the elemental and catalytic guardians based on widget sprites
        lastElementalRuneSprite = parseRuneWidget(elementalRuneWidget, lastElementalRuneSprite);
        lastCatalyticRuneSprite = parseRuneWidget(catalyticRuneWidget, lastCatalyticRuneSprite);

        // Find which guardians correspond to these spriteIds
        GuardianInfo elementalGuardianInfo = GuardianInfo.ALL.stream()
                .filter(g -> g.spriteId == lastElementalRuneSprite)
                .findFirst().orElse(null);

        GuardianInfo catalyticGuardianInfo = GuardianInfo.ALL.stream()
                .filter(g -> g.spriteId == lastCatalyticRuneSprite)
                .findFirst().orElse(null);

        String elementalGuardianName = (elementalGuardianInfo != null) ? elementalGuardianInfo.name : "Unknown";
        String catalyticGuardianName = (catalyticGuardianInfo != null) ? catalyticGuardianInfo.name : "Unknown";

        JsonObject json = new JsonObject();
        json.addProperty("elementalGuardian", elementalGuardianName);
        json.addProperty("catalyticGuardian", catalyticGuardianName);

        synchronized (this)
        {
            cachedGuardians = json;
        }
    }

    /**
     * Direct copy of the logic from the original plugin's parseRuneWidget method:
     * - If the sprite changes, despawn the old guardian and spawn the new one
     */
    private int parseRuneWidget(Widget runeWidget, int lastSpriteId)
    {
        if (runeWidget != null)
        {
            int spriteId = runeWidget.getSpriteId();
            if (spriteId != lastSpriteId)
            {
                // Despawn old guardian if there was one
                if (lastSpriteId > 0)
                {
                    GuardianInfo.ALL.stream()
                            .filter(g -> g.spriteId == lastSpriteId)
                            .findFirst()
                            .ifPresent(GuardianInfo::despawn);
                }

                // Spawn new guardian if recognized
                GuardianInfo.ALL.stream()
                        .filter(g -> g.spriteId == spriteId)
                        .findFirst()
                        .ifPresent(guardian -> {
                            guardian.spawn();
                            // The original plugin handles notifications here, but we can skip that.
                            // Just ensure we replicate logic enough to identify the guardian.
                        });

                return spriteId;
            }
            return spriteId;
        }
        return lastSpriteId;
    }

    @Override
    public HttpHandler getHttpHandler()
    {
        return exchange -> {
            JsonObject guardiansCopy;
            synchronized (this)
            {
                guardiansCopy = cachedGuardians.deepCopy();
            }
            String response = RuneLiteAPI.GSON.toJson(guardiansCopy);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody()))
            {
                out.write(response);
            }
            catch (IOException e)
            {
                log.error("Error writing guardians response", e);
            }
        };
    }

    @Override
    public String getContextPath()
    {
        return "/guardians";
    }
}