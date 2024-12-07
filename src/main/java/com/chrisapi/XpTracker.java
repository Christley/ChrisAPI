package com.chrisapi;

import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

public class XpTracker {

    private final Map<Skill, ArrayList<Integer>> skillXpMap = new EnumMap<>(Skill.class);
    private final ChrisAPIPlugin plugin;

    public XpTracker(ChrisAPIPlugin plugin) {
        this.plugin = plugin;

        // Initialize the skillXpMap with all skills
        for (Skill skill : Skill.values()) {
            skillXpMap.put(skill, new ArrayList<>());
        }
    }

    public void update() {
        for (Skill skill : Skill.values()) {
            ArrayList<Integer> xpListToUpdate = skillXpMap.get(skill);
            int xpValueToAdd = plugin.getClient().getSkillExperience(skill);
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
