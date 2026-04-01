package com.explorerMode;

import net.runelite.api.coords.WorldPoint;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public class ExplorationRegion
{
    public enum RegionType
    {
        KINGDOM,
        SUBREGION,
        CHUNK_GROUP
    }

    private final String id;
    private final String name;
    private final RegionShape shape;
    private final RegionType type;

    private final Set<ExplorationRegion> children = new HashSet<>();
    private final Set<Chunk> chunks = new HashSet<>();

    private ExplorationRegion parent = null;
    private boolean excludeChildren = false;

    private boolean hiddenUntilDiscovered = false;
    public boolean isHiddenUntilDiscovered() { return hiddenUntilDiscovered; }
    public void setHiddenUntilDiscovered(boolean hidden) { this.hiddenUntilDiscovered  = hidden; }

    private  String revealsRegionId = null;
    public String getRevealsRegionId() { return revealsRegionId; }
    public void setRevealsRegionId(String id) { this.revealsRegionId = id; }

    // Custom color for each region
    private Color regionColor = null;

    // Default based on type - Might remove
  //  private Color defaultColor = null;

    public ExplorationRegion(String id, String name, RegionShape shape, RegionType type)
    {
        this.id = id;
        this.name = name;
        this.shape = shape;
        this.type = type;
    }

    public ExplorationRegion(String id, String name, RegionBounds bounds, RegionType type)
    {
        this(id, name, RegionShape.fromBounds(bounds), type);
    }   //Was using with older versions of the code, might replace. ToDo

    public boolean contains(WorldPoint point)
    {
        return shape.contains(point);
    }

    public RegionBounds getBoundingBox()
    {
        return shape.getBoundingBox();
    }

    public void addChild(ExplorationRegion region)
    {
        children.add(region);
        region.setParent(this);
    }

    public void addChunk(Chunk chunk)
    {
        chunks.add(chunk);
    }

    public Color getDisplayColor()
    {
        // Custom color override
        if (regionColor != null)
        {
            return regionColor;
        }

/*        // Explicit default override
        //ToDO Still needed, or unnecessary? Look at in morning.
        if (defaultColor != null)
        {
            return defaultColor;
        }   */

        // Fallback based on type
        switch (type)
        {
            case KINGDOM:
                return new Color(0, 0, 0, 220);
            case SUBREGION:
                return new Color(40, 40, 40, 180);
            case CHUNK_GROUP:
                return new Color(80, 80, 80, 140);
            default:
                return new Color(0, 0, 0, 200);
        }
    }
    //ToDo could probably replace most of my getters/setters with LOMBOKs

    // Setters
    public void setParent(ExplorationRegion parent)
    {
        this.parent = parent;
    }

    public void setRegionColor(Color color)
    {
        this.regionColor = color;
    }

/*    public void setDefaultColor(Color color)
    {
        this.defaultColor = color;
    }   */
    //Should default based on type, probably will remove soon

    public void setExcludeChildren(boolean excludeChildren)
    {
        this.excludeChildren = excludeChildren;
    }

    // Getters
    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public RegionBounds getBounds()
    {
        return shape.getBoundingBox();
    }

    public RegionType getType()
    {
        return type;
    }

    public Set<ExplorationRegion> getChildren()
    {
        return children;
    }

    public Set<Chunk> getChunks()
    {
        return chunks;
    }

    public ExplorationRegion getParent()
    {
        return parent;
    }

    public RegionShape getShape()
    {
        return shape;
    }

    public boolean shouldExcludeChildren()
    {
        return excludeChildren;
    }
}
