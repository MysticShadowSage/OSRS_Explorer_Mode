package com.explorerMode;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

public class RegionShape
{
    private final List<WorldPoint> polygonPoints;
    private final RegionBounds boundingBox;
    private final int plane;

    public RegionShape(List<WorldPoint> polygonPoints)
    {
        if (polygonPoints == null || polygonPoints.isEmpty())
        {
            throw new IllegalArgumentException("Polygon points can't be empty!");
        }

        this.polygonPoints = new ArrayList<>(polygonPoints);
        this.plane = polygonPoints.get(0).getPlane();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (WorldPoint point : polygonPoints)
        {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }

        this.boundingBox = new RegionBounds(minX, minY, maxX, maxY, plane);
    }

    public static RegionShape fromBounds(RegionBounds bounds)
    {
        List<WorldPoint> points = new ArrayList<>();
        points.add(new WorldPoint(bounds.getMinX(), bounds.getMaxY(), bounds.getPlane()));
        points.add(new WorldPoint(bounds.getMaxX(), bounds.getMaxY(), bounds.getPlane()));
        points.add(new WorldPoint(bounds.getMaxX(), bounds.getMinY(), bounds.getPlane()));
        points.add(new WorldPoint(bounds.getMinX(), bounds.getMinY(), bounds.getPlane()));
        return new RegionShape(points);
    }

    public boolean contains(WorldPoint point)
    {
        if (point.getPlane() != plane || !boundingBox.contains(point))
        {
            return false;
        }

        return isPointInPolygon(point);
    }

    public boolean containsChunk(Chunk chunk)
    {
        int centerX = chunk.getX() * 8 + 4;
        int centerY = chunk.getY() * 8 + 4;
        WorldPoint center = new WorldPoint(centerX, centerY, chunk.getPlane());
        return contains(center);
    }

    private boolean isPointInPolygon(WorldPoint point)
    {
        int count = 0;
        int n = polygonPoints.size();

        for (int i = 0; i < n; i++)
        {
            WorldPoint p1 = polygonPoints.get(i);
            WorldPoint p2 = polygonPoints.get((i + 1) % n);

            boolean crossesY = (p1.getY() > point.getY()) != (p2.getY() > point.getY());
            if (crossesY)
            {
                double xAtY =
                        (double) (p2.getX() - p1.getX()) *
                                (point.getY() - p1.getY()) /
                                (double) (p2.getY() - p1.getY()) +
                                p1.getX();

                if (point.getX() < xAtY)
                {
                    count++;
                }
            }
        }

        return (count % 2) == 1;
    }

    public List<WorldPoint> getPolygonPoints()
    {
        return new ArrayList<>(polygonPoints);
    }

    public RegionBounds getBoundingBox()
    {
        return boundingBox;
    }

    public int getPlane()
    {
        return plane;
    }   //Not really used, assuming default plane for all of them
}
