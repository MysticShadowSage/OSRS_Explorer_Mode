package com.explorerMode;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;

import com.explorerMode.MapMode;
import com.explorerMode.unlockNPC;

@Slf4j
@PluginDescriptor(
	name = "Explorer Mode"
)

/*
	Currently, this will cover the whole game with the black boxes, in a
	fog-of-war type effect. Might be good, might not be worth it.
	Will see.
	Need to convert it into only the world map.

 */
public class ExplorerModePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ExplorerModeConfig config;

	@Inject
	private OverlayManager overlayManager;

	//Create a hashset to store visited areas
	private final Set<WorldPoint> explorerDiscovered = new HashSet<>();
	private final Set<Chunk> questDiscovered = new HashSet<>();

	//Map NPCs to chunks for region unlocks
	private final Map<Integer, unlockNPC> npcUnlocks = new HashMap<>();

	@Inject
	private ExplorerMapOverlay mapOverlay;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(mapOverlay);
		loadNpcUnlockTable();
		loadExplorerDiscovered();
		loadQuestsDiscovered();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(mapOverlay);
		saveExplorerDiscovered();
		saveQuestsDiscovered();
		//ToDo Save tile sets visited
	}

	@Subscribe
	public void OnGameTick(GameTick event)
	{
		//Do I want the tiles to be unlocked when moving even when not on explorer/adventurer mode?
		if(client.getLocalPlayer()== null)
			return;

		if(config.mode() == MapMode.EXPLORER || config.mode() == MapMode.ADVENTURER)
		{
			WorldPoint p = client.getLocalPlayer().getWorldLocation();
			unlockRadius(explorerDiscovered, p, config.revealRadius());
		}

		//Will save the 'explorer mode' tiles we discovered every 60 seconds.
		long now = System.currentTimeMillis();
		if (now - lastSaveTime > 60000) {
			saveExplorerDiscovered();
			lastSaveTime = now;
		}
	}

	private long lastSaveTime = System.currentTimeMillis();

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if(!e.getMenuOption().equals("Talk-to"))
			return;

		int npcID = e.getMenuEntry().getNpc() != null
				? e.getMenuEntry().getNpc().getId()
				: -1;

		if (!npcUnlocks.containsKey(npcID))
			return;

		showMapRevealDialogue(npcID);
	}

	private void showMapRevealDialogue(int npcID)
	{
		unlockNPC unlock = npcUnlocks.get(npcID);
		//Open a chatbox with the Npc name (Or a different header if wanted)
		chatboxPanelManager.openTextMenuInput(unlock.getDisplayName())
				.option("This npc can reveal the current region on the map! Unlock it?", () -> {})
				.option("Yes, reveal it", () ->
				{
					questDiscovered.addAll(unlock.getUnlockChunks());
					saveQuestsDiscovered();
				})
				.option("No, not yet", () -> {})
				.build();
	}

	private void saveQuestsDiscovered() {
		Gson gson = new Gson();
		Set<Chunk> combined = new HashSet<>();

		// Load existing unlocked chunks, if any
		try (FileReader reader = new FileReader("unlockedQuests.json")) {
			Type setType = new TypeToken<Set<Chunk>>() {}.getType();
			Set<Chunk> existing = gson.fromJson(reader, setType);
			if (existing != null) {
				combined.addAll(existing);
			}
		} catch (IOException e) {
			// File doesn't exist yet, that's fine
			log.info("No existing unlocked quests JSON, creating new.");
		}

		// Add the newly unlocked chunks
		combined.addAll(questDiscovered);

		// Write the combined set back to the JSON file
		try (FileWriter writer = new FileWriter("unlockedQuests.json")) {
			gson.toJson(combined, writer);
		} catch (IOException e) {
			log.error("Failed to save quest-unlocked chunks.", e);
		}
	}

	private void loadQuestsDiscovered() {
		Gson gson = new Gson();
		try (FileReader reader = new FileReader("unlockedQuests.json")) {
			Type setType = new TypeToken<Set<Chunk>>() {}.getType();
			Set<Chunk> loaded = gson.fromJson(reader, setType);
			if (loaded != null) {
				questDiscovered.addAll(loaded);
			}
		} catch (IOException e) {
			log.info("No saved quest-unlocked chunks found. Starting fresh.");
		}
	}

	private void saveExplorerDiscovered() {
		Gson gson = new Gson();
		try (FileWriter writer = new FileWriter("explorerTiles.json")) {
			gson.toJson(explorerDiscovered, writer);
		} catch (IOException e) {
			log.error("Failed to save explorer tiles.", e);
		}
	}

	private void loadExplorerDiscovered() {
		Gson gson = new Gson();
		try (FileReader reader = new FileReader("explorerTiles.json")) {
			Type setType = new TypeToken<Set<WorldPoint>>(){}.getType();
			Set<WorldPoint> loaded = gson.fromJson(reader, setType);
			if (loaded != null) {
				explorerDiscovered.addAll(loaded);
			}
		} catch (IOException e) {
			log.info("No saved explorer tiles found. Starting fresh.");
		}
	}


	public void unlockTile(WorldPoint wp)
	{
		explorerDiscovered.add(wp);
	}

	private void unlockRadius(Set<WorldPoint> target, WorldPoint center, int radius)
	{
		for(int x = -radius; x <= radius; x++)
		{
			for(int y = -radius; y<= radius; y++)
			{
				target.add(new WorldPoint(
						center.getX() + x,
						center.getY() + y,
						center.getPlane()
				));
			}
		}
	}

	private Set<WorldPoint> generateRegion(WorldPoint center, int radius)
	{
		Set<WorldPoint> s = new HashSet<>();
		unlockRadius(s, center, radius);
		return s;
	}

	public boolean isTileVisible(WorldPoint wp)
	{
		switch (config.mode())
		{
			case REGULAR:
				return true;

			case EXPLORER:
				return explorerDiscovered.contains(wp);

			case QUESTER:
				Chunk chunk = new Chunk(wp.getX() >> 3, wp.getY() >> 3, wp.getPlane());
				return questDiscovered.contains(chunk);

			case ADVENTURER:
				Chunk tileChunk = new Chunk(wp.getX() >> 3, wp.getY() >> 3, wp.getPlane());
				return explorerDiscovered.contains(wp) || questDiscovered.contains(tileChunk);

			default:
				return false;
		}
	}

	public class ExplorerMapOverlay extends Overlay
	{
		@Inject private ExplorerModePlugin plugin;
		@Inject private Client client;

		public ExplorerMapOverlay()
		{
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			// Iterate visible map tiles
			// For each tile:
			//   if (!plugin.isTileVisible(tilePoint))
			//       draw black rectangle

			if (client.getPlane() < 0 ) return null;

			// Iterate over tiles on current scene
			for (int x = 0; x < 104; x++)
			{
				for (int y = 0; y< 104; y++)
				{
					WorldPoint wp = new WorldPoint(x + client.getBaseX(), y + client.getBaseY(), client.getPlane());
					if (!plugin.isTileVisible(wp)) {
						// Convert world tile to canvas/screen coordinates
						Point canvasPt = Perspective.localToCanvas(client, wp.getX(), wp.getY(), client.getPlane());
						if (canvasPt != null) {
							g.setColor(new Color(0, 0, 0, 180)); // semi-transparent black
							g.fillRect(canvasPt.getX(), canvasPt.getY(), 5, 5); // adjust 5x5 depending on zoom/tile size
						}
					}
				}
			}

			return null;
		}
	}
/*
	@Subscribe	//For this one, probably will change it to have the quests unlock specific regions.
	public void onQuestCompletion(QuestCompleted e)
	{
		questDiscovered.addAll(questRegionMap.get(e.getQuest()));
	}
*/

	@Provides
	ExplorerModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExplorerModeConfig.class);
	}

	private void loadNpcUnlockTable()	//Use a hybrid approach of polygonal edges to map chunks to region unlocks.
	{
		//NPCs and the associated regions they unlock.

		Set<Chunk> kingRoaldChunks = loadChunksFromJson("resources/KING_ROALD.json");
		npcUnlocks.put(
				NpcID.KING_ROALD,
				new unlockNPC(NpcID.KING_ROALD, "King Roald", kingRoaldChunks)
		);
	}

	public static Set<Chunk> loadChunksFromJson(String filePath) {
		Gson gson = new Gson();
		try (FileReader reader = new FileReader(filePath)) {
			Type setType = new TypeToken<Set<Chunk>>(){}.getType();
			return gson.fromJson(reader, setType);
		} catch (IOException e) {
			e.printStackTrace();
			return new HashSet<>();
		}
	}
}

