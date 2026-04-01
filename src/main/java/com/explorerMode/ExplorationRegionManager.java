package com.explorerMode;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class ExplorationRegionManager
{
    private ExplorerModePlugin plugin;

    private final Map<String, ExplorationRegion> allRegions = new HashMap<>();
    private final Set<String> discoveredRegionIds = new HashSet<>();
    private final Set<Chunk> visitedChunks = new HashSet<>();

    // Track which regions are "active" for discovery notifications
    private final Set<String> activeSubregionIds = new HashSet<>();
    private final Set<String> activeChunkGroupIds = new HashSet<>();

    // Cache undiscovered chunks/regions
    private Set<Chunk> undiscoveredChunksCache = null;
    private Set<ExplorationRegion> undiscoveredRegionsCache = null;

    public void loadDefaultRegions()
    {
        clearAllData();

        Map<String, ExplorationRegion> loadedRegions = RegionDefinitionLoader.loadFromJson();
        allRegions.putAll(loadedRegions);

        for (ExplorationRegion region : allRegions.values())
        {
            if (region.getType() == ExplorationRegion.RegionType.SUBREGION)
            {
                populateChunksForSubregion(region);
            }
        }

        log.info("Loaded {} regions from JSON definitions", allRegions.size());
    }

    private void populateChunksForSubregion(ExplorationRegion subregion)
    {
        if (subregion.getType() != ExplorationRegion.RegionType.SUBREGION)
        {
            return;
        }

        RegionBounds bounds = subregion.getBoundingBox();
        if (bounds == null)
        {
            log.warn("Subregion {} has null bounding box", subregion.getName());
            return;
        }

        if (subregion.shouldExcludeChildren())
        {
            populateChunksExcludingChildren(subregion, bounds);
            return;
        }

        int minChunkX = bounds.getMinX() >> 3;
        int maxChunkX = bounds.getMaxX() >> 3;
        int minChunkY = bounds.getMinY() >> 3;
        int maxChunkY = bounds.getMaxY() >> 3;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
        {
            for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++)
            {
                Chunk chunk = new Chunk(chunkX, chunkY, bounds.getPlane());

                if (subregion.getShape().containsChunk(chunk))
                {
                    chunk.setParentRegion(subregion);
                    subregion.addChunk(chunk);
                }
            }
        }

        log.debug("Populated {} chunks for subregion {}", subregion.getChunks().size(), subregion.getName());
    }

    //If I get rid of Frontier regions, might be able to just remove this.
    // ToDo Look at later
    private void populateChunksExcludingChildren(ExplorationRegion frontier, RegionBounds bounds)
    {
        ExplorationRegion parent = frontier.getParent();
        if (parent == null)
        {
            log.warn("Frontier region {} has no parent", frontier.getName());
            return;
        }

        List<ExplorationRegion> siblingSubregions = parent.getChildren().stream()
                .filter(child -> child != frontier && child.getType() == ExplorationRegion.RegionType.SUBREGION)
                .collect(Collectors.toList());

        for (int chunkX = bounds.getMinX() >> 3; chunkX <= bounds.getMaxX() >> 3; chunkX++)
        {
            for (int chunkY = bounds.getMinY() >> 3; chunkY <= bounds.getMaxY() >> 3; chunkY++)
            {
                Chunk chunk = new Chunk(chunkX, chunkY, bounds.getPlane());

                if (!frontier.getShape().containsChunk(chunk))
                {
                    continue;
                }

                boolean inSiblingRegion = false;
                for (ExplorationRegion sibling : siblingSubregions)
                {
                    if (sibling.getShape().containsChunk(chunk))
                    {
                        inSiblingRegion = true;
                        break;
                    }
                }

                if (!inSiblingRegion)
                {
                    chunk.setParentRegion(frontier);
                    frontier.addChunk(chunk);
                }
            }
        }

        log.debug(
                "Frontier {} has {} chunks (excluding {} sibling regions)",
                frontier.getName(),
                frontier.getChunks().size(),
                siblingSubregions.size()
        );
    }

    public void onPlayerEnteredRegion(WorldPoint playerPos)
    {
        boolean changed = false;

        // Check kingdoms first
        for (ExplorationRegion kingdom : getAllRegionsOfType(ExplorationRegion.RegionType.KINGDOM))
        {
            if (!discoveredRegionIds.contains(kingdom.getId()) && kingdom.contains(playerPos))
            {
                discoveredRegionIds.add(kingdom.getId());
                plugin.onRegionDiscovered(kingdom);

                log.info("Discovered kingdom: {}", kingdom.getName());
                loadSubregionsForDiscovery(kingdom);

                changed = true;
                break;
            }
        }

        // Check active subregions
        for (String subregionId : activeSubregionIds)
        {
            ExplorationRegion subregion = allRegions.get(subregionId);
            if (subregion != null && !discoveredRegionIds.contains(subregionId) && subregion.contains(playerPos))
            {
                discoveredRegionIds.add(subregionId);
                plugin.onRegionDiscovered(subregion);

                log.info("Discovered subregion: {}", subregion.getName());
                loadChunksForDiscovery(subregion);

                // Check if it unlocks another subregion upon entering
                if (subregion.getRevealsRegionId() != null)
                {
                    ExplorationRegion linked = allRegions.get(subregion.getRevealsRegionId());
                    if (linked != null)
                    {
                        // Force all chunks of the placeholder as visited
                        for (Chunk chunk : linked.getChunks())
                        {
                            visitedChunks.add(chunk);
                            plugin.revealExplorerChunk(chunk);
                        }
                        discoveredRegionIds.add(linked.getId());
                        log.info("Auto-revealed linked region: {}", linked.getName());
                    }
                }

                // After discovering a subregion, check if parent kingdom needs discovering (Mainly for 'Other Regions' section)
                ExplorationRegion parent = subregion.getParent();
                if (parent != null
                        && parent.getType() == ExplorationRegion.RegionType.KINGDOM
                        && !discoveredRegionIds.contains(parent.getId()))
                {
                    discoveredRegionIds.add(parent.getId());
                    plugin.onRegionDiscovered(parent);
                    log.info("Discovered kingdom via subregion: {}", parent.getName());
                }

                changed = true;
                break;
            }
        }



        if (changed)
        {
            clearCaches();
        }
    }

    public double getExplorationPercentage(ExplorationRegion region) {
        Set<Chunk> totalChunks = region.getChunks();
        if (totalChunks.isEmpty()) {
            // If it has children but no direct chunks, aggregate children
            if (!region.getChildren().isEmpty()) {
                return region.getChildren().stream()
                        .mapToDouble(this::getExplorationPercentage)
                        .average()
                        .orElse(0.0);
            }
            return 0.0;
        }

        long visitedCount = totalChunks.stream()
                .filter(visitedChunks::contains)
                .count();

        return (double) visitedCount / totalChunks.size() * 100.0;
    }

    private void loadSubregionsForDiscovery(ExplorationRegion kingdom)
    {
        for (ExplorationRegion child : kingdom.getChildren())
        {
            if (child.getType() == ExplorationRegion.RegionType.SUBREGION)
            {
                activeSubregionIds.add(child.getId());
                log.debug("Now tracking subregion for discovery: {}", child.getName());
            }
        }
    }

    private void loadChunksForDiscovery(ExplorationRegion subregion)
    {
        activeChunkGroupIds.add(subregion.getId());
        log.debug("Now tracking chunks for subregion: {}", subregion.getName());
    }

    public void onChunkVisited(Chunk chunk)
    {
        if (visitedChunks.add(chunk))
        {
            log.debug("Discovered chunk: {},{}", chunk.getX(), chunk.getY());
            clearCaches();
        }
    }

    // ========== GETTER METHODS FOR OVERLAY ==========

    public Set<ExplorationRegion> getUndiscoveredRegions()
    {
        if (undiscoveredRegionsCache != null)
        {
            return undiscoveredRegionsCache;
        }

        Set<ExplorationRegion> undiscovered = new HashSet<>();

        // 1. Add undiscovered kingdoms
        for (ExplorationRegion kingdom : getAllRegionsOfType(ExplorationRegion.RegionType.KINGDOM))
        {
            if (!discoveredRegionIds.contains(kingdom.getId()))
            {
                undiscovered.add(kingdom);
            }
        }

        // 2. Add active but undiscovered subregions
        for (String subregionId : activeSubregionIds)
        {
            ExplorationRegion subregion = allRegions.get(subregionId);
            if (subregion != null && !discoveredRegionIds.contains(subregionId))
            {
                undiscovered.add(subregion);
            }
        }

        // 3. Add active but undiscovered chunk groups
        for (String chunkGroupId : activeChunkGroupIds)
        {
            ExplorationRegion chunkGroup = allRegions.get(chunkGroupId);
            if (chunkGroup != null && !discoveredRegionIds.contains(chunkGroupId))
            {
                undiscovered.add(chunkGroup);
            }
        }

        undiscoveredRegionsCache = undiscovered;
        return undiscovered;
    }

    public void setPlugin(ExplorerModePlugin plugin)
    {
        this.plugin = plugin;
    }

    //ToDo Still needed?
    public Set<Chunk> getUndiscoveredChunks()
    {
        if (undiscoveredChunksCache != null)
        {
            return undiscoveredChunksCache;
        }

        Set<Chunk> undiscovered = new HashSet<>();

        for (String subregionId : activeChunkGroupIds)
        {
            ExplorationRegion subregion = allRegions.get(subregionId);
            if (subregion != null)
            {
                for (Chunk chunk : subregion.getChunks())
                {
                    if (!visitedChunks.contains(chunk))
                    {
                        undiscovered.add(chunk);
                    }
                }
            }
        }

        undiscoveredChunksCache = undiscovered;
        return undiscovered;
    }

    // ========== HELPER METHODS ==========

    private List<ExplorationRegion> getAllRegionsOfType(ExplorationRegion.RegionType type)
    {
        return allRegions.values().stream()
                .filter(region -> region.getType() == type)
                .collect(Collectors.toList());
    }

    private void clearCaches()
    {
        undiscoveredChunksCache = null;
        undiscoveredRegionsCache = null;
    }

    private void clearAllData()
    {
        allRegions.clear();
        discoveredRegionIds.clear();
        visitedChunks.clear();
        activeSubregionIds.clear();
        activeChunkGroupIds.clear();
        clearCaches();

        log.debug("Cleared all region data");
    }

    public int getDiscoveredRegionCount()
    {
        return discoveredRegionIds.size();
    }

    public int getActiveSubregionCount()
    {
        return activeSubregionIds.size();
    }

    // ========== DATA PERSISTENCE ==========

    public PlayerProgressSaveData getPlayerProgressSaveData()
    {
        PlayerProgressSaveData data = new PlayerProgressSaveData();
        data.setDiscoveredRegions(new HashSet<>(discoveredRegionIds));
        data.setActiveSubregions(new HashSet<>(activeSubregionIds));
        data.setActiveChunkGroups(new HashSet<>(activeChunkGroupIds));

        Set<SavedChunk> saved = new HashSet<>();
        for (Chunk c : visitedChunks)
        {
            saved.add(new SavedChunk(c.getX(), c.getY(), c.getPlane()));
        }
        data.setDiscoveredChunks(saved);

        return data;
    }

    public void loadPlayerProgressSaveData(PlayerProgressSaveData data)
    {
        if (data == null)
        {
            return;
        }

        discoveredRegionIds.clear();
        activeSubregionIds.clear();
        activeChunkGroupIds.clear();
        visitedChunks.clear();

        if (data.getDiscoveredRegions() != null) discoveredRegionIds.addAll(data.getDiscoveredRegions());
        if (data.getActiveSubregions() != null) activeSubregionIds.addAll(data.getActiveSubregions());
        if (data.getActiveChunkGroups() != null) activeChunkGroupIds.addAll(data.getActiveChunkGroups());

        if (data.getDiscoveredChunks() != null)
        {
            for (SavedChunk sc : data.getDiscoveredChunks())
            {
                if (sc == null) continue;
                visitedChunks.add(new Chunk(sc.getX(), sc.getY(), sc.getPlane()));
            }
        }

        clearCaches();
    }


    // ========== DEBUG/UTILITY METHODS ==========

    public void debugPrintState()
    {
        log.info("=== Region Manager State ===");
        log.info("Total regions: {}", allRegions.size());
        log.info("Discovered regions: {}", discoveredRegionIds.size());
        log.info("Discovered chunks: {}", visitedChunks.size());
        log.info("Active subregions: {}", activeSubregionIds.size());
        log.info("Active chunk groups: {}", activeChunkGroupIds.size());

        for (ExplorationRegion region : allRegions.values())
        {
            String status = discoveredRegionIds.contains(region.getId()) ? "✓" : "✗";
            log.info("  {} {} ({}) - {} chunks", status, region.getName(), region.getType(), region.getChunks().size());
        }
    }

    private Map<Chunk, ExplorationRegion> chunkToRegionIndex = null;

    private void ensureChunkIndex()
    {
        if (chunkToRegionIndex != null)
        {
            return;
        }

        chunkToRegionIndex = new HashMap<>();
        for (ExplorationRegion region : allRegions.values())
        {
            for (Chunk c : region.getChunks())
            {
                chunkToRegionIndex.put(c, region); // relies on Chunk.equals/hashCode by coords
            }
        }
    }


    public void reset()
    {
        clearAllData();
        loadDefaultRegions();
        log.info("RegionManager information fully reset.");
    }

    // Getters for plugin
    public int getActiveChunkGroupCount()
    {
        return activeChunkGroupIds.size();
    }

    public boolean isRegionDiscovered(ExplorationRegion region)
    {
        return discoveredRegionIds.contains(region.getId());
    }


    public ExplorationRegion getRegion(String id)
    {
        return allRegions.get(id);
    }

    public Collection<ExplorationRegion> getAllRegions()
    {
        return allRegions.values();
    }
}
