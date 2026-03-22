# Explorer Mode
A plugin to hide the world map fog-of-war style until a player has visited those locations in-game.
Created to help players regain a sense of exploration, or for those who want to add more immersion to their role play.

The plugin will check the players location each game tick and will track which tiles/chunks the player has been in.
The world map is hidden in a 3 tiered system, so that players can still have an idea of what is happening around the world.

When the player enters a kingdom, the sub-regions that comprise it will be revealed.
When the player enters the subregion, they will start to clear 8*8 chunks of the map as they travel there.

For now, there is no 'radius' feature anymore due to the rendering detail it would take to cover the entire map in individual tiles.

### Future Goals:
* ~~With time, I'm hoping to add a 'map' that will be filled in, rather than just a black overlay.~~ **This has been accomplished.**

* I also want to try and add in, very optimistically, a 'Quest Explorer' mode which will tie in to quest completion to reveal areas of the map. In this way, players are given an incentive to do quests that are intrinsically tied to a location.
 E.g. Legends Quest with its Jungle mapping segment, Monkey Madness for Ape Atoll, In Aid of the Myreque for Burgh De Rott and Barrows, etc...

* If possible, the use of 'cartographer' npcs to be added (like those in the now defunct 'Citizens' plugin) that can reveal areas of the map when spoken to. If this is not possible, then adding a trigger/prompt when a player speaks to existing NPCs such as Town Criers or the Wise Old Man.
