package com.chrisapi;

import net.runelite.api.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XpTracker {

    private Map<Skill, ArrayList<Integer>> skillXpMap = new HashMap<>();

    private HttpServerPlugin httpPlugin;

    public XpTracker(HttpServerPlugin httpPlugin) {
        this.httpPlugin = httpPlugin;

        // Initialize the skillXpMap with all skills
        for (Skill skill : Skill.values()) {
            ArrayList<Integer> newXpList = new ArrayList<>();
            skillXpMap.put(skill, newXpList);
        }
    }

    public void update() {
        for (Skill skill : Skill.values()) {
            ArrayList<Integer> xpListToUpdate = skillXpMap.get(skill);
            int xpValueToAdd = httpPlugin.getClient().getSkillExperience(skill);
            xpListToUpdate.add(xpValueToAdd);
        }
    }

    public int getXpData(Skill skillToGet, int tickNum) {
        ArrayList<Integer> xpListToGet = skillXpMap.get(skillToGet);
        if (tickNum >= 0 && tickNum < xpListToGet.size()) {
            return xpListToGet.get(tickNum);
        } else {
            // Handle the case where tickNum is out of bounds
            return 0; // Or throw an exception, depending on your requirements
        }
    }

    public int getMostRecentXp(Skill skillToGet) {
        ArrayList<Integer> xpListToGet = skillXpMap.get(skillToGet);
        if (!xpListToGet.isEmpty()) {
            return xpListToGet.get(xpListToGet.size() - 1);
        } else {
            return 0;
        }
    }
}
