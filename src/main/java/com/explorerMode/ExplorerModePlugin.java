package com.explorerMode;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.runelite.client.RuneLite;
import net.runelite.client.util.ImageUtil;


@Slf4j
@PluginDescriptor(name = "Explorer Mode")
public class ExplorerModePlugin extends Plugin
{
	/* ===== CONSTANTS ===== */

	@Inject private Client client;
	@Inject private ExplorerModeConfig config;
	@Inject private ExplorerWorldMapOverlay worldMapOverlay;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatboxPanelManager chatboxPanelManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ExplorationRegionManager regionManager;

	private ExplorationPanel panel;
	private NavigationButton navButton;

	// Data storage
	Set<Chunk> explorerChunks = new HashSet<>();
	Set<Chunk> questChunks = new HashSet<>();

	private File saveDir;
	private File explorerChunksFile;
	private File questChunksFile;
	private File playerProgressFile;

	// Area for the map fogs
	Area explorerFog = new Area();
	Area questFog = new Area();

	//private final ExplorationRegionManager regionManager = new ExplorationRegionManager();

	// ToDo NPC unlocks
	private final Map<Integer, unlockNPC> npcUnlocks = new HashMap<>();

	// Timing for map reveals
	private long lastSaveTime = System.currentTimeMillis();
	private long lastUnlockTime = 0;
	private static final long UNLOCK_COOLDOWN_MS = 1000;

	// Reveal animations
	private static final long REVEAL_ANIM_MS = 600;
		// discover chunk -> startTimeMs
	private final Map<Chunk, Long> explorerRevealAnim = new HashMap<>();
	private final Map<Chunk, Long> questRevealAnim = new HashMap<>();

	/* ======= Startup/Shutdown ======= */

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(worldMapOverlay);

		regionManager.setPlugin(this);
		regionManager.loadDefaultRegions();

		initSaveFiles();

		loadPlayerProgress();
		loadExplorerChunks();
		loadQuestChunks();

		rebuildExplorerFog();
		rebuildQuestFog();

		panel = injector.getInstance(ExplorationPanel.class);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/explorerMode/ExplorerMode_icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Explorer Mode")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);

		panel.rebuild();

		log.info("Explorer Mode plugin started");
	}


	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);

		overlayManager.remove(worldMapOverlay);

		saveAll();

		log.info("Explorer Mode plugin stopped");
	}


	/* ========== GAMEPLAY ========== */

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
		Chunk current = getChunkFromWorldPoint(playerPos);
		regionManager.onChunkVisited(current);


		regionManager.onPlayerEnteredRegion(playerPos);
		finishRevealAnimations(System.currentTimeMillis());

		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.refreshHeader();
			}
		});

		long now = System.currentTimeMillis();

		// Unlock new tiles around player
	//	if (now - lastUnlockTime > UNLOCK_COOLDOWN_MS)
	//	{
			unlockTile(playerPos);
	//		if (config.mode() == MapMode.EXPLORER || config.mode() == MapMode.ADVENTURER)
	//		{
	//			lastUnlockTime = now;
	//		}
	//	}
		//ToDo I think I can remove the time gated unlocks, it's not really an issue anymore.

		// Auto-save
		if (now - lastSaveTime > 60000)
		{
			saveAll();
			lastSaveTime = now;
		}
	}

	public void onRegionDiscovered(ExplorationRegion region)
	{
		// Send message in chat when new Kingdom discovered
		client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				"Map Discovery: " + region.getName() + " revealed!",
				null
		);

		//Update panel
		SwingUtilities.invokeLater(() -> {
			if (panel != null) {
				panel.rebuild();
			}
		});

		log.info("Discovered region: {}", region.getName());
	}

	/* ========== TILES & CHUNK MANAGEMENT ========== */

	private Area buildChunkAreaWorld(Chunk chunk)
	{
		int minX = chunk.getX() * 8;	//ToDO if I wanna change the 'chunk' size, gonna need to change this eventually.
		int minY = chunk.getY() * 8;
		int maxX = minX + 8;
		int maxY = minY + 8;

		Polygon p = new Polygon();
		p.addPoint(minX, minY);
		p.addPoint(maxX, minY);
		p.addPoint(maxX, maxY);
		p.addPoint(minX, maxY);

		return new Area(p);
	}

	// For the world fog
	public Area getActiveFog()
	{
		switch (config.mode())
		{
			case EXPLORER:
				return explorerFog;

			case QUESTER:
				return questFog;

			case ADVENTURER:
				Area combined = new Area(explorerFog);
				combined.intersect(questFog);
				return combined;

			case REGULAR:
			default:
				return new Area(); // no fog
		}
	}

	public void rebuildExplorerFog()
	{
		// Start with one big Area (from JSON)
		explorerFog = buildFogSeedFromRegion("world_fog");

		// Subtract every discovered chunk from that Area
		for (Chunk c : explorerChunks)
		{
			if (!explorerRevealAnim.containsKey(c)) // So it doesn't rebuild fog that player currently in.
			{
				explorerFog.subtract(buildChunkAreaWorld(c));
			}
		}

		log.debug("Explorer fog rebuilt. Bounds={}", explorerFog.getBounds());
	}

	public void rebuildQuestFog()
	{
		questFog = buildFogSeedFromRegion("world_fog");

		for (Chunk c : questChunks)
		{
			if (!questRevealAnim.containsKey(c))
			{
				questFog.subtract(buildChunkAreaWorld(c));
			}
		}

		log.debug("Quest fog rebuilt. Bounds={}", questFog.getBounds());
	}

	public Map<Chunk, Long> getRevealAnimationsForCurrentMode()
	{
		switch (config.mode())
		{
			case EXPLORER:
				return explorerRevealAnim;

			case QUESTER:
				return questRevealAnim;

			case ADVENTURER:
				Map<Chunk, Long> combined = new HashMap<>();
				combined.putAll(explorerRevealAnim);
				combined.putAll(questRevealAnim);
				return combined;

			default:
				return java.util.Collections.emptyMap();
		}
	}

	private void finishRevealAnimations(long nowMs)
	{
		// Explorer reveal animation -> remove from Area when done
		explorerRevealAnim.entrySet().removeIf(e ->
		{
			long start = e.getValue();
			if (nowMs - start >= REVEAL_ANIM_MS)
			{
				Chunk c = e.getKey();
				revealExplorerChunk(c); //Once the animation is finished
				return true;
			}
			return false;
		});

		// Quest animations -> permanently subtract when done
		// Still need to look into opening map like a reward cutscene when quest completed.
		// Or better, when closing the quest reward popup
		questRevealAnim.entrySet().removeIf(e ->
		{
			long start = e.getValue();
			if (nowMs - start >= REVEAL_ANIM_MS)
			{
				Chunk c = e.getKey();
				revealQuestChunk(c);	//Will need to adjust here to deal with the above considerations
				return true;
			}
			return false;
		});
	}

	public void revealExplorerChunk(Chunk c)
	{
		explorerFog.subtract(buildChunkAreaWorld(c));
	}

	public void revealQuestChunk(Chunk c)
	{
		questFog.subtract(buildChunkAreaWorld(c));
	}

	private Chunk getChunkFromWorldPoint(WorldPoint wp)
	{
		return new Chunk(wp.getX() >> 3, wp.getY() >> 3, wp.getPlane());
		//ToDo Same as above, if I wanna change chunk size need to change the division here.
	}

	private Area buildFogSeedFromRegion(String regionId)
	{
		ExplorationRegion region = regionManager.getRegion(regionId);
		if (region == null)
		{
			log.warn("Fog seed region '{}' not found (did JSON load?)", regionId);
			return new Area(); // If empty -> No fog
		}

		java.util.List<WorldPoint> pts = region.getShape().getPolygonPoints();
		if (pts == null || pts.size() < 3)
		{
			log.warn("Fog seed region '{}' has invalid polygon points", regionId);
			return new Area();
		}	//Might be unnecessary safeguard, could remove maybe?

		Polygon poly = new Polygon();
		for (WorldPoint wp : pts)
		{
			poly.addPoint(wp.getX(), wp.getY());
		}

		return new Area(poly);
	}

	public void unlockTile(WorldPoint wp)
	{
		Chunk chunk = getChunkFromWorldPoint(wp);

		if (config.mode() == MapMode.EXPLORER || config.mode() == MapMode.ADVENTURER)
		{
			if (explorerChunks.add(chunk))
			{
				explorerRevealAnim.put(chunk, System.currentTimeMillis());
			}
		}
	}

	/* ========== DATA HANDLING (Save & Load) ========== */

	private void saveAll()
	{
		saveExplorerChunks();
		saveQuestChunks();
		savePlayerProgress();
	}

	private void saveExplorerChunks()
	{
		ExplorerSaveData data = new ExplorerSaveData();
		data.setDiscoveredChunks(toSavedChunks(explorerChunks));
		saveToFile(explorerChunksFile, data);
	}

	private void saveQuestChunks()
	{
		ExplorerSaveData data = new ExplorerSaveData();
		data.setDiscoveredChunks(toSavedChunks(questChunks));
		saveToFile(questChunksFile, data);
	}

	private void savePlayerProgress()
	{
		PlayerProgressSaveData state = regionManager.getPlayerProgressSaveData();
		saveToFile(playerProgressFile, state);
	}

	private void loadExplorerChunks()
	{
		ExplorerSaveData data = loadFromFile(explorerChunksFile, ExplorerSaveData.class);
		explorerChunks.clear();
		if (data != null && data.getDiscoveredChunks() != null)
		{
			explorerChunks.addAll(fromSavedChunks(data.getDiscoveredChunks()));
		}
		log.info("Loaded {} explorer chunks", explorerChunks.size());
	}

	private void loadQuestChunks()
	{
		ExplorerSaveData data = loadFromFile(questChunksFile, ExplorerSaveData.class);
		questChunks.clear();
		if (data != null && data.getDiscoveredChunks() != null)
		{
			questChunks.addAll(fromSavedChunks(data.getDiscoveredChunks()));
		}
		log.info("Loaded {} quest chunks", questChunks.size());
	}

	private void loadPlayerProgress()
	{
		PlayerProgressSaveData state = loadFromFile(playerProgressFile, PlayerProgressSaveData.class);
		if (state == null)
		{
			log.info("No player progress file found yet (starting fresh).");
			return;
		}
		regionManager.loadPlayerProgressSaveData(state);
		if (state.getVersion() != 1)
		{
			log.warn("Player progress version mismatch (got {}, expected 1). Ignoring file.", state.getVersion());
			return;	//ToDo This will always need to change when updating
		}
		log.info("Loaded player progress: discoveredRegions={}, activeSubregions={}, activeChunkGroups={}, visitedChunks={}",
				state.getDiscoveredRegions().size(),
				state.getActiveSubregions().size(),
				state.getActiveChunkGroups().size(),
				state.getDiscoveredChunks().size());
	}


	private void initSaveFiles()
	{
		saveDir = new File(RuneLite.RUNELITE_DIR, "explorer-mode");
		if (!saveDir.exists() && !saveDir.mkdirs())
		{
			log.warn("Failed to create save directory: {}", saveDir.getAbsolutePath());
		}

		explorerChunksFile = new File(saveDir, "explorer_chunks.json");
		questChunksFile = new File(saveDir, "quest_chunks.json");
		playerProgressFile = new File(saveDir, "playerProgress.json");
	}

	//Convert between the saved chunk data and runtime chunk data
	//Savinig with parent region data etc causing problems.
	private static Set<SavedChunk> toSavedChunks(Set<Chunk> chunks)
	{
		Set<SavedChunk> saved = new HashSet<>();
		if (chunks == null)
		{
			return saved;
		}

		for (Chunk c : chunks)
		{
			if (c == null) continue;
			saved.add(new SavedChunk(c.getX(), c.getY(), c.getPlane()));
		}
		return saved;
	}

	private static Set<Chunk> fromSavedChunks(Set<SavedChunk> saved)
	{
		Set<Chunk> chunks = new HashSet<>();
		if (saved == null)
		{
			return chunks;
		}

		for (SavedChunk sc : saved)
		{
			if (sc == null) continue;
			chunks.add(new Chunk(sc.getX(), sc.getY(), sc.getPlane()));
		}
		return chunks;
	}


	private void saveToFile(File file, Object data)
	{
		if (file == null)
		{
			return;
		}

		Gson gson = new Gson();
		try (FileWriter writer = new FileWriter(file))
		{
			gson.toJson(data, writer);
		}
		catch (IOException e)
		{
			log.error("Failed saving {}", file.getName(), e);
		}
	}

	private <T> T loadFromFile(File file, Class<T> clazz)
	{
		if (file == null || !file.exists())
		{
			return null;
		}

		Gson gson = new Gson();
		try (FileReader reader = new FileReader(file))
		{
			return gson.fromJson(reader, clazz);
		}
		catch (Exception e)
		{
			log.warn("Failed loading {} (treating as empty): {}", file.getName(), e.getMessage());
			return null;
		}
	}


	// ========== GETTERS & CONFIG ==========

	public MapMode getCurrentMode()
	{
		return config.mode();
	}

	public boolean showRegions() { return config.showRegions(); }

	public Collection<ExplorationRegion> getAllRegions()
	{
		return regionManager.getAllRegions();
	}

	public boolean isRegionDiscovered(ExplorationRegion region)
	{
		return regionManager.isRegionDiscovered(region);
	}


	@Provides
	ExplorerModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExplorerModeConfig.class);
	}

	// ========== NPC UNLOCK METHODS (wip)==========

	private void loadNpcUnlockTable()
	{
		// For now, not used.
		// Keeping the method in, just to ensure it's being run and debugged properly
		log.debug("NPC unlock table loaded (empty for now)");
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		// For when clicking on specific NPCs (and possibly objects?) in the world.
		// Will be tied to subregion unlocks for exploration mode.
	}

	private void showMapRevealDialogue(int npcID)
	{
		// Add in dialogue for NPCs that will unlock map regions when spoken to.
		// If possible, see if a Citizen's style npc creation is still possible.
		// If not, then just the chatbox, probably an emote.
		// Maybe see if I can open the map manually and reveal the whole area to the player, would be cool
	}


	/* 	============ CHAT COMMANDS ===============	*/

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Debug and test commands.
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.PUBLICCHAT)
		{
			String initMessage = event.getMessage();
			String message = initMessage.toLowerCase();

			if (message.equals("!mapdebug"))	//ToDo Might remove when done, could be good to adjust for player progress
			{
				StringBuilder debug = new StringBuilder();
				debug.append("Explorer Mode Debug:\n");
				debug.append("Discovered Regions: ").append(regionManager.getDiscoveredRegionCount()).append("\n");
				debug.append("Active Subregions: ").append(regionManager.getActiveSubregionCount()).append("\n");

				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", debug.toString(), null);
			}

			if (message.equals("!mapsave"))	//Force a save
			{
				saveAll();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Explorer data saved!", null);
			}

			if (message.equals("!mapload"))	//Rebuild from last save
			{
				loadPlayerProgress();
				loadExplorerChunks();
				loadQuestChunks();
				rebuildExplorerFog();
				rebuildQuestFog();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Explorer data loaded!", null);
			}

			if (message.equals("!addpoint"))	//ToDo For boundary mapping, can remove when done.
			{
				WorldPoint pos = client.getLocalPlayer().getWorldLocation();
				String point = "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getPlane() + "]";
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Point: " + point, null);
				log.info("MAP POINT: {}", point);
			}

			//Wipe all save data
			if (message.equals("!mapreset"))
			{
				explorerChunks.clear();
				questChunks.clear();
				explorerRevealAnim.clear();
				questRevealAnim.clear();

				regionManager.reset();

				rebuildExplorerFog();
				rebuildQuestFog();

				SwingUtilities.invokeLater(() -> {
					if (panel != null) {
						panel.rebuild();
					}
				});

				saveAll();

				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Explorer data fully reset!", null);
			}
		}
	}
}
