# Chris API
Exposes data to localhost on a configurable port. For building dashboards or pretty websites during tournaments and similar.

### Example endpoints:

http://localhost:8081/skills

http://localhost:8081/accountinfo

http://localhost:8081/events

http://localhost:8081/quests

http://localhost:8081/inventory

http://localhost:8081/equipment

http://localhost:8081/bank

http://localhost:8081/combat

# Skills:

- Shows the skill name.
- Shows current skill level.
- Shows what your boosted skill level is.
- Shows the difference between your boosted level and normal level.
- Shows current XP in each skill.

# Account info

- Shows your account hash.
- Shows your player name.
- Shows login state.
- Shows your combat level.
- Shows which world you're currently logged into.
- Shows current weight.

# Events:

- Shows current animation ID
- Shows current animation pose.
- Shows if you're idling or not.
- Shows the latest chat message.
- Shows the 5 latest chat messages
- Shows your current run energy.
- Shows your current special attack energy.
- Shows your location in the world.
- 
# Quests

- Shows quest status, like FINISHED, NOT_STARTED or IN_PROGRESS for each quest
- Shows your quest points and the max amount of quest points.

# Inventory:

- Shows which items are in your inventory.
- Shows ID of the item.
- Shows the name of the item.
- Shows the quantity of the item.
- Lists empty slots as well.

# Equipment

- Shows which items are equipped on your character.
- Shows slot name per item.
- Shows ID of the item.
- Shows the name of the item.
- Shows the quantity of the item.
- Lists empty slots as well.

# Bank

- Shows if your bank is open or not.
- If it's open, lists all items, their ids and the amount you have.

# Combat

- Shows if you're in combat or not.
- If you're in combat, states the name of the NPC you're in combat with.