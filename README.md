# Explorer Mode
A plugin to hide the world map fog-of-war style until a player has visited those locations in-game.
Created to help players regain a sense of exploration, or for those who want to add more immersion to their role play.

The plugin will check the players location each game tick and will track which tiles/chunks the player has been in.
The distance 'discovered' around the player can be edited to a certain degree.
If the radius is set to 1, then it will record a 3x3 grid of tiles around the player. (9 tiles)
If the radius is set to 2, then it will record a 4x4 grid of tiles (16 tiles discovered).
If the radius is set to 'max', then it will record each chunk a player visits, rather than a specific tile radius.

This way, each player can customise the level of exploration they want added to their journey.

### Future Goals:
* With time, I'm hoping to add a 'map' that will be filled in, rather than just a black overlay.

* I also want to try and add in, very optimistically, a 'Quest Explorer' mode which will tie in to quest completion to reveal areas of the map. In this way, players are given an incentive to do quests that are intrinsically tied to a location.
 E.g. Legends Quest with it's Jungle mapping segment, Monkey Madness for Ape Atoll, In Aid of the Myreque for Burgh De Rott and Barrows, etc...

* If possible, the use of 'cartographer' npcs to be added (like those in the now defunct 'Citizens' plugin) that can reveal areas of the map when spoken to. If this is not possible, then adding a trigger/prompt when a player speaks to existing NPCs such as Town Criers or the Wise Old Man.
