# Crispy API
Exposes data to localhost on a configurable port. For building dashboards or pretty websites during tournaments and similar.

Example endpoints:
http://localhost:8081/stats

http://localhost:8081/events

http://localhost:8081/inv

http://localhost:8081/equip

# http://localhost:8081/stats

- Shows login, account hash and username.
- Shows current skill level.
- Shows what your boosted skill level is.
- Shows the difference between your boosted level and normal level.
- Shows current experience in each skill.
- Shows XP gained during your login session.

# http://localhost:8080/events
## Logs:
- total health and health remaining
- run energy
- the animation id useful tracking if interacting with objects
- if in combat the name of the npc and its health
- logs world location, local location, camera position and where the mouse is located
- Max distance which affects what is displayed on objects/doors
