// RegionBounds.java
package com.explorerMode;

import net.runelite.api.coords.WorldPoint;
import java.util.Objects;

public class RegionBounds {
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final int plane;

    public RegionBounds(int minX, int minY, int maxX, int maxY, int plane) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.plane = plane;
    }

    public boolean contains(WorldPoint point) {
        if (point.getPlane() != plane) return false;
        return point.getX() >= minX &&
                point.getX() <= maxX &&
                point.getY() >= minY &&
                point.getY() <= maxY;
    }


//ToDo Still needed?
    public boolean containsChunk(Chunk chunk) {
        int chunkMinX = chunk.getX() * 8;
        int chunkMaxX = chunkMinX + 7;
        int chunkMinY = chunk.getY() * 8;
        int chunkMaxY = chunkMinY + 7;

        return chunkMinX >= minX && chunkMaxX <= maxX &&
                chunkMinY >= minY && chunkMaxY <= maxY &&
                chunk.getPlane() == plane;
    }

    // Getters
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getPlane() { return plane; }
    public int getWidth() { return maxX - minX + 1; }   //Don't really have a use for anymore
    public int getHeight() { return maxY - minY + 1; }  //Same with this

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionBounds)) return false;
        RegionBounds that = (RegionBounds) o;
        return minX == that.minX && minY == that.minY &&
                maxX == that.maxX && maxY == that.maxY && plane == that.plane;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, maxX, maxY, plane);
    }
}