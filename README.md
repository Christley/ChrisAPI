# Chris API
Exposes data to localhost on a configurable port. For building dashboards or pretty websites during tournaments and similar.
Spiritual successor to Morg HTTP Client.

### Example endpoints:

http://localhost:8081/stats

http://localhost:8081/events

http://localhost:8081/inventory

http://localhost:8081/equipment

http://localhost:8081/quests

# Stats:

- Shows login, account hash and username.
- Shows your combat level.
- Shows your current logged in world.
- Shows current skill level.
- Shows what your boosted skill level is.
- Shows the difference between your boosted level and normal level.
- Shows current experience in each skill.
- Shows XP gained during your login session.

# Events:

- Shows current animation ID and animation pose.
- Shows if you're idling or not.
- Shows the latest chat message.
- Shows your current run energy.
- Shows the current game tick.
- Shows your current health.
- Shows your current prayer points.
- Shows the name of the NPC and its health if you're in combat.
- Shows your location in the world.
- Shows your camera position.
- Shows where your mouse is located in the game.
- Max distance which affects what is displayed on objects/doors

# Inventory:

- Shows which items are in your inventory
- Shows ID of the item
- Shows the name of the item
- Shows the quantity of the item

# Quests

- Shows quest status, like FINISHED, NOT_STARTED or IN_PROGRESS for each quest
