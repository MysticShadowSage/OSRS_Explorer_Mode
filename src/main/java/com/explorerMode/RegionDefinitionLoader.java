package com.explorerMode;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.coords.WorldPoint;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RegionDefinitionLoader
{
    public static class JsonRegionDefinition
    {
        String id;
        String name;
        String type;
        int[] color;
        List<int[]> polygon;
        JsonObject bounds;
        List<String> children;
        String parent;
        boolean excludeChildren;
        boolean hiddenUntilDiscovered;
        String revealsRegionId;
    }

    public static class JsonRegionData
    {
        int version;
        List<JsonRegionDefinition> regions;
    }

    public static Map<String, ExplorationRegion> loadFromJson()
    {
        Map<String, ExplorationRegion> regions = new HashMap<>();

        try
        {
            InputStream is = RegionDefinitionLoader.class.getResourceAsStream("/ExplorerModeRegions.json");
            if (is == null)
            {
                log.error("Could not find regions.json in resources");
                return regions;
            }

            Gson gson = new Gson();
            JsonRegionData data = gson.fromJson(new InputStreamReader(is), JsonRegionData.class);

            // First: Create all regions
            for (JsonRegionDefinition def : data.regions)
            {
                ExplorationRegion region = createRegionFromDefinition(def);
                if (region != null)
                {
                    regions.put(def.id, region);
                }
            }

            // Second: Set up parent-child relationships
            for (JsonRegionDefinition def : data.regions)
            {
                if (def.parent != null)
                {
                    ExplorationRegion child = regions.get(def.id);
                    ExplorationRegion parent = regions.get(def.parent);

                    if (child != null && parent != null)
                    {
                        parent.addChild(child);
                    }
                }

                if (def.children != null)
                {
                    for (String childId : def.children)
                    {
                        ExplorationRegion child = regions.get(childId);
                        ExplorationRegion parent = regions.get(def.id);

                        if (child != null && parent != null)
                        {
                            parent.addChild(child);
                        }
                    }
                }
            }

            log.info("Loaded {} regions from JSON", regions.size());
            return regions;
        }
        catch (Exception e)
        {
            log.error("Failed to load region definitions from JSON", e);
            return regions;
        }
    }

    private static ExplorationRegion createRegionFromDefinition(JsonRegionDefinition def)
    {
        try
        {
            RegionShape shape;

            if (def.polygon != null && !def.polygon.isEmpty())
            {
                List<WorldPoint> points = new ArrayList<>();
                for (int[] coords : def.polygon)
                {
                    points.add(new WorldPoint(coords[0], coords[1], coords[2]));
                }
                shape = new RegionShape(points);
            }
            else if (def.bounds != null)
            {
                RegionBounds bounds = new RegionBounds(
                        def.bounds.get("minX").getAsInt(),
                        def.bounds.get("minY").getAsInt(),
                        def.bounds.get("maxX").getAsInt(),
                        def.bounds.get("maxY").getAsInt(),
                        def.bounds.get("plane").getAsInt()
                );
                shape = RegionShape.fromBounds(bounds);
            }
            else
            {
                log.warn("Region {} has no polygon or bounds", def.id);
                return null;
            }

            ExplorationRegion.RegionType type = ExplorationRegion.RegionType.valueOf(def.type);

            ExplorationRegion region = new ExplorationRegion(def.id, def.name, shape, type);

            if (def.color != null && def.color.length == 4)
            {
                Color color = new Color(def.color[0], def.color[1], def.color[2], def.color[3]);
                region.setRegionColor(color);
            }

            if (def.excludeChildren)
            {
                region.setExcludeChildren(true);
            }

            if (def.hiddenUntilDiscovered)
            {
                region.setHiddenUntilDiscovered(true);
            }

            if (def.revealsRegionId != null)
            {
                region.setRevealsRegionId(def.revealsRegionId);
            }

            return region;
        }
        catch (Exception e)
        {
            log.error("Failed to create region from definition: {}", def.id, e);
            return null;
        }
    }
}
