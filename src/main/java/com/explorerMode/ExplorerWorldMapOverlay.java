package com.explorerMode;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.*;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;


@Slf4j
public class ExplorerWorldMapOverlay extends Overlay
{
    private final ExplorerModePlugin plugin;
    private final Client client;
    private final WorldMapOverlay worldMapOverlay;

    // ----- Map fog shape cache to help with processing -----
    private Shape cachedFogShape = null;
    private long cachedFogShapeAtMs = 0;
    private static final long FOG_SHAPE_CACHE_MS = 100; // How many ms between caching?


    // ---- Fog noise texture (Gonna cache) ----
    private static final int NOISE_SIZE = 256;
    private BufferedImage noiseTex;
    private final Random noiseRng = new Random(1337); // Stable look across runs

    // ----- Things I can tweak -----
    private static final float EDGE_WISP_ALPHA = 0.12f;   // Edge mist intensity - Initial 0.12f
    private static final float FOG_HAZE_ALPHA  = 0.18f;   // Fog interior haze intensity - Initial 0.8f

    private static final float EDGE_WISP_SPEED_X = 12f;   // Pixels per sec
    private static final float EDGE_WISP_SPEED_Y = 7f;

    private static final float FOG_HAZE_SPEED_X  = 4f;    // Pixels per sec
    private static final float FOG_HAZE_SPEED_Y  = 2f;

    // Edge band thickness in pixels (screen space)
    private static final float EDGE_BAND_WIDTH_PX = 9f;     // Initial width: 18f

    private static final Set<String> HIDE_LABELS = new java.util.HashSet<>(
            java.util.Arrays.asList("World")
    );  //Regions whose names shouldn't be displayed on the map, now just the World area.



    @Inject
    public ExplorerWorldMapOverlay(ExplorerModePlugin plugin, Client client, WorldMapOverlay worldMapOverlay)
    {
        this.plugin = plugin;
        this.client = client;
        this.worldMapOverlay = worldMapOverlay;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);

        if (worldMapOverlay == null)
        {
            log.warn("WorldMapOverlay is null - overlay may not work correctly");
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (graphics == null || client == null || worldMapOverlay == null)
        {
            return null;
        }   //If nothing's running, don't do anything.

        //Looks like it's the 8th child for the map overlay.
        Widget worldMapWidget = client.getWidget(InterfaceID.WORLDMAP,8);
        if (worldMapWidget == null || worldMapWidget.isHidden())
        {
            return null;
        }   //If map is closed, do nothing

        MapMode mode = plugin.getCurrentMode(); //Check what Mode the user's selected

        boolean drawFog = mode != MapMode.MAPPER && mode != MapMode.REGULAR; // Fog not shown in regular (ToDo - Remove Mapper)
        boolean drawRegions = plugin.showRegions(); //Check if user wants regions or not

        if (!drawFog && !drawRegions)   //No fog, no regions
        {
            return null;
        }

        Graphics2D g2d = (Graphics2D) graphics.create();
        try
        {
            Rectangle mapBounds = worldMapWidget.getBounds();
            g2d.setClip(mapBounds);

            long nowMs = System.currentTimeMillis();    //Log current time
            float pxPerTile = getPixelsPerTile();   //Roughly how many px per tile ont he map
            int zoomLevel = getZoomLevel0to4(pxPerTile);    //Approximate zoom level


            if (drawFog) {  //Will only be null if in Mapper (to be removed)
                // Draw fog of war
                Area fog = plugin.getActiveFog();   //Get the fog shape
                Shape fogShape;
                if (cachedFogShape != null && (nowMs - cachedFogShapeAtMs) < FOG_SHAPE_CACHE_MS)
                {
                    fogShape = cachedFogShape;
                }  //If there's already a stored fog shape, cache it.
                else
                {
                    fogShape = projectAreaToMap(fog);
                    cachedFogShape = fogShape;
                    cachedFogShapeAtMs = nowMs;
                }   //Otherwise, create a stored fog shape to display, rather than compute all the time


                if (fogShape != null)   //If not empty
                {
                    //Base colour fill
                    g2d.setColor(new Color(0, 0, 0, 215));  //Base alpha is 160, a bit too light though. 250 much too dark
                    g2d.fill(fogShape);

                    //Add a haze over the whole fog (helps distort what's beneath a bit)
                    drawFogHazeNoise(g2d, fogShape, mapBounds, nowMs);

                    //Wisp drift just near the boundary (Makes it seem foggy/cloudy on the edges)
                    drawFogEdgeWisp(g2d, fogShape, mapBounds, nowMs);

                    //Animate the edge (looks a lil nicer)
                    float pulse = (float) (Math.sin(nowMs / 250.0) * 1.5f);   //Tweak speed/size
                    float w = 3.5f + pulse; //Original size 3.5f

                    g2d.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.setColor(new Color(220, 220, 220, 130)); //Bit softer than base fill; original alpha 80
                    g2d.draw(fogShape);

                    //Second rim (Little bit of a softer outer glow)
                    g2d.setStroke(new BasicStroke(w + 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.setColor(new Color(95, 95, 95, 105)); //Might adjust; original alpha 35
                    g2d.draw(fogShape);
                }

                //So players can see where to close the map, unfortunately fog hides it without.
                //Creates an X in the top-right corner, same as default.
                int pad = 3;    //Might need to adjust to get it to match up a bit better.
                int safeSize = 28; //Can tweak if needed
                Rectangle safe = new Rectangle(
                        mapBounds.x + mapBounds.width - safeSize - pad,
                        mapBounds.y + pad,
                        safeSize,
                        safeSize
                );
                drawCloseHint(g2d, safe);

                drawProgressiveReveals(g2d, mapBounds, nowMs); //Makes the
            }
            List<PotentialLabel> potentialLabels = new ArrayList<>();

            // Draw regions if toggled on
            // Need to make sure I draw the Kingdoms first, was drawing in a random order before.
            if (drawRegions) {
                plugin.getAllRegions().stream()
                        .filter(region -> !region.getId().equals("world_fog") && !region.getId().equals("other_regions"))
                        .sorted((a, b) -> a.getType().compareTo(b.getType()))
                        .forEach(region -> {
                            Color color = getColorForRegion(region, zoomLevel);
                            if (color.getAlpha() > 5)
                            {
                                drawRegion(g2d, region, mapBounds, color, zoomLevel, potentialLabels);
                            }
                        });
                drawNonOverlappingLabels(g2d, potentialLabels, mapBounds);
            }

        }
        catch (Exception e)
        {
            log.debug("Error during drawing: {}", e.getMessage());
        }
        finally
        {
            g2d.dispose();
        }

        return null;
    }

    private Shape projectAreaToMap(Area worldArea)
    {
        PathIterator it = worldArea.getPathIterator(null);

        Path2D path = new Path2D.Float();
        float[] coords = new float[6];
        boolean penUp = true;

        while (!it.isDone())
        {
            int type = it.currentSegment(coords);

            WorldPoint wp = new WorldPoint((int) coords[0], (int) coords[1], 0);
            Point p = worldMapOverlay.mapWorldPointToGraphicsPoint(wp);

            if (p == null)
            {
                penUp = true;
                it.next();
                continue;
            }

            if (type == PathIterator.SEG_MOVETO || penUp)
            {
                path.moveTo(p.getX(), p.getY());
            }
            else if (type == PathIterator.SEG_LINETO)
            {
                path.lineTo(p.getX(), p.getY());
            }
            else if (type == PathIterator.SEG_CLOSE)
            {
                path.closePath();
            }

            penUp = false;
            it.next();
        }

        return path;
    }

    private Shape projectChunkToMap(Chunk c)
    {
        // chunk to world tile corners
        int minX = c.getX() * 8;
        int minY = c.getY() * 8;
        int maxX = minX + 8;
        int maxY = minY + 8;

        WorldPoint[] corners = new WorldPoint[]
                {
                        new WorldPoint(minX, minY, 0),
                        new WorldPoint(maxX, minY, 0),
                        new WorldPoint(maxX, maxY, 0),
                        new WorldPoint(minX, maxY, 0)
                };

        int[] xs = new int[4];
        int[] ys = new int[4];

        for (int i = 0; i < 4; i++)
        {
            Point p = worldMapOverlay.mapWorldPointToGraphicsPoint(corners[i]);
            if (p == null)
            {
                return null; // offscreen / unmapped
            }
            xs[i] = p.getX();
            ys[i] = p.getY();
        }

        return new Polygon(xs, ys, 4);
    }

    private void drawProgressiveReveals(Graphics2D g2d, Rectangle mapBounds, long nowMs)
    {
        Map<Chunk, Long> anim = plugin.getRevealAnimationsForCurrentMode();
        if (anim == null || anim.isEmpty())
        {
            return;
        }

        long now = nowMs;

        Composite oldComp = g2d.getComposite();
        try
        {
            for (Map.Entry<Chunk, Long> e : anim.entrySet())
            {
                Chunk c = e.getKey();
                long start = e.getValue();

                float t = (now - start) / 600f; // should match REVEAL_ANIM_MS (600)
                if (t <= 0f) t = 0f;
                if (t >= 1f) t = 1f;

                Shape hole = projectChunkToMap(c);
                if (hole == null)
                {
                    continue;
                }

                // Make it “open” from center: scale from 0.2 -> 1.0
                double scale = 0.2 + 0.8 * t;

                Rectangle r = hole.getBounds();
                double cx = r.getCenterX();
                double cy = r.getCenterY();

                AffineTransform at = new AffineTransform();
                at.translate(cx, cy);
                at.scale(scale, scale);
                at.translate(-cx, -cy);

                Shape scaledHole = at.createTransformedShape(hole);

                drawRevealGlow(g2d, scaledHole, t);

                // DST_OUT removes what was drawn before. Alpha controls “how much”.
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, t));
                g2d.fill(scaledHole);
            }
        }
        finally
        {
            g2d.setComposite(oldComp);
        }
    }


    private BufferedImage getOrCreateNoiseTexture()
    {
        if (noiseTex != null)
        {
            return noiseTex;
        }

        BufferedImage img = new BufferedImage(NOISE_SIZE, NOISE_SIZE, BufferedImage.TYPE_INT_ARGB);

        // Simple grayscale noise w/ alpha. (Cheap and good enough visually.)
        for (int y = 0; y < NOISE_SIZE; y++)
        {
            for (int x = 0; x < NOISE_SIZE; x++)
            {
                int v = noiseRng.nextInt(256);        // brightness
                int a = 60 + noiseRng.nextInt(90);    // alpha range 60..149
                int argb = (a << 24) | (v << 16) | (v << 8) | v;
                img.setRGB(x, y, argb);
            }
        }

        noiseTex = img;
        return noiseTex;
    }

    /**
     * Draw noise tiled across the mapBounds, with a translation offset.
     * Uses TexturePaint to keep it fast and simple.
     */
    private void drawTiledNoise(Graphics2D g, Rectangle mapBounds, float offsetX, float offsetY, float alpha)
    {
        BufferedImage tex = getOrCreateNoiseTexture();
        if (tex == null)
        {
            return;
        }

        Composite oldComp = g.getComposite();
        try
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(alpha)));

            // TexturePaint anchors at a rectangle; shifting that rectangle moves the tiling.
            Rectangle2D anchor = new Rectangle2D.Float(
                    mapBounds.x + offsetX,
                    mapBounds.y + offsetY,
                    tex.getWidth(),
                    tex.getHeight()
            );

            TexturePaint paint = new TexturePaint(tex, anchor);
            g.setPaint(paint);
            g.fillRect(mapBounds.x, mapBounds.y, mapBounds.width, mapBounds.height);
        }
        finally
        {
            g.setComposite(oldComp);
        }
    }

    private static float clamp01(float v)
    {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /**
     * Wisp drift that lives ONLY in a thin ring near the fog boundary.
     * This does NOT deform geometry; it just clips a moving noise texture to an edge band.
     */
    private void drawFogEdgeWisp(Graphics2D g2d, Shape fogShape, Rectangle mapBounds, long nowMs)
    {
        if (fogShape == null || mapBounds == null)
        {
            return;
        }

        Graphics2D g = (Graphics2D) g2d.create();
        Shape oldClip = g.getClip();

        try
        {
            // Create a stroked outline, then intersect it with the fog fill
            Stroke bandStroke = new BasicStroke(EDGE_BAND_WIDTH_PX, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            Shape edgeStrokeShape = bandStroke.createStrokedShape(fogShape);

            Area bandArea = new Area(edgeStrokeShape);
            bandArea.intersect(new Area(fogShape)); // ensures it only appears on the fog side

            g.setClip(bandArea);

            float t = nowMs / 1000f;
            float ox = (t * EDGE_WISP_SPEED_X) % NOISE_SIZE;
            float oy = (t * EDGE_WISP_SPEED_Y) % NOISE_SIZE;

            drawTiledNoise(g, mapBounds, ox, oy, EDGE_WISP_ALPHA);
        }
        finally
        {
            g.setClip(oldClip);
            g.dispose();
        }
    }

    /**
     * Haze noise over the WHOLE fog area. This gives “blur-like” obscuration without any real blur.
     */
    private void drawFogHazeNoise(Graphics2D g2d, Shape fogShape, Rectangle mapBounds, long nowMs)
    {
        if (fogShape == null || mapBounds == null)
        {
            return;
        }

        Graphics2D g = (Graphics2D) g2d.create();
        Shape oldClip = g.getClip();

        try
        {
            g.setClip(fogShape);

            float t = nowMs / 1000f;
            float ox = (t * FOG_HAZE_SPEED_X) % NOISE_SIZE;
            float oy = (t * FOG_HAZE_SPEED_Y) % NOISE_SIZE;

            drawTiledNoise(g, mapBounds, ox, oy, FOG_HAZE_ALPHA);
        }
        finally
        {
            g.setClip(oldClip);
            g.dispose();
        }
    }

    private void drawRevealGlow(Graphics2D g2d, Shape holeShape, float t)
    {
        // t: 0 -> 1
        // Rim is strongest early, fades out near end
        float glow = 1f - t;
        if (glow <= 0f)
        {
            return;
        }

        // Keep it subtle
        int alpha1 = (int) (120 * glow); // inner rim
        int alpha2 = (int) (55 * glow);  // outer softer rim

        // Outer "glow" stroke
        g2d.setColor(new Color(255, 255, 255, alpha2));
        g2d.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(holeShape);

        // Inner crisp rim
        g2d.setColor(new Color(255, 255, 255, alpha1));
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(holeShape);
    }


    private void drawRegion(Graphics2D graphics, ExplorationRegion region, Rectangle mapBounds, Color color, int zoomLevel, List<PotentialLabel> labels)
    {
        if (graphics == null || region == null || mapBounds == null)
        {
            return;
        }

        List<WorldPoint> worldPoints = region.getShape().getPolygonPoints();
        if (worldPoints == null || worldPoints.isEmpty())
        {
            return;
        }

        // Convert to screen coordinates
        List<Point> screenPoints = new ArrayList<>();
        for (WorldPoint worldPoint : worldPoints)
        {
            Point screenPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(worldPoint);
            if (screenPoint != null)
            {
                screenPoints.add(screenPoint);
            }
        }

        if (screenPoints.size() < 3)
        {
            return;
        }

        int[] xPoints = new int[screenPoints.size()];
        int[] yPoints = new int[screenPoints.size()];

        for (int i = 0; i < screenPoints.size(); i++)
        {
            Point p = screenPoints.get(i);
            xPoints[i] = p.getX();
            yPoints[i] = p.getY();
        }

        Polygon polygon = new Polygon(xPoints, yPoints, screenPoints.size());

        // Only draw if visible
        Rectangle polyBounds = polygon.getBounds();
        if (!polyBounds.intersects(mapBounds))
        {
            return;
        }

        // Fill
        graphics.setColor(color);
        graphics.fillPolygon(polygon);

        // Border
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawPolygon(polygon);

        // Labels - Now don't overlap, priority system and getting region bounds first help with the positioning
        if (shouldDrawLabel(region, zoomLevel))
        {
            if (polyBounds.width > 60 && polyBounds.height > 20) {  //Adjusted size, better for placement in smaller zones
                String name = region.getName();

                // No longer using Bounding box, struggled with regions like Taverly with weird shapes
                // Try finding a point that's actually inside the polygon:
                int cx = (int) Math.round(polyBounds.getCenterX());
                int cy = (int) Math.round(polyBounds.getCenterY());

                // Then walk inward until we find a point inside
                if (!polygon.contains(cx, cy))
                {
                    outer:
                    for (int dx = 0; dx <= polyBounds.width / 2; dx += 4)
                    {
                        for (int dy = 0; dy <= polyBounds.height / 2; dy += 4)
                        {
                            if (polygon.contains(cx + dx, cy + dy)) { cx = cx + dx; cy = cy + dy; break outer; }
                            if (polygon.contains(cx - dx, cy + dy)) { cx = cx - dx; cy = cy + dy; break outer; }
                            if (polygon.contains(cx + dx, cy - dy)) { cx = cx + dx; cy = cy - dy; break outer; }
                            if (polygon.contains(cx - dx, cy - dy)) { cx = cx - dx; cy = cy - dy; break outer; }
                        }
                    }
                }

                if (polygon.contains(cx, cy))
                {
                    int priority = getLabelPriority(region, polyBounds);
                    labels.add(new PotentialLabel(name, cx, cy, priority, polyBounds, Color.YELLOW));
                }
            }
        }
    }

    // Can't get the explicit zoom levels, so gotta approximate

    private static final float[] ZOOM_PX_PER_TILE = {
            1.48f, // level 0 (most zoomed out)
            2.00f, // level 1
            3.00f, // level 2
            4.00f, // level 3 (default-ish)
            8.00f  // level 4 (most zoomed in)
    };

    private float getPixelsPerTile()
    {
        try
        {
            WorldPoint p1 = new WorldPoint(3200, 3200, 0);
            WorldPoint p2 = new WorldPoint(3264, 3200, 0); // 64 tiles east

            Point s1 = worldMapOverlay.mapWorldPointToGraphicsPoint(p1);
            Point s2 = worldMapOverlay.mapWorldPointToGraphicsPoint(p2);

            if (s1 != null && s2 != null && s1.getX() != s2.getX())
            {
                int pixelDistance = Math.abs(s2.getX() - s1.getX());
                return pixelDistance / 64.0f;
            }
        }
        catch (Exception ignored) {}

        // fallback: last known-ish default
        return 4.0f;
    }

    private int getZoomLevel0to4(float pxPerTile)
    {
        int best = 0;
        float bestDiff = Float.MAX_VALUE;

        for (int i = 0; i < ZOOM_PX_PER_TILE.length; i++)
        {
            float diff = Math.abs(pxPerTile - ZOOM_PX_PER_TILE[i]);
            if (diff < bestDiff)
            {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private static final class PotentialLabel
    {
        final String text;
        final int cx, cy;               // desired center anchor (screen coords)
        final int priority;             // higher wins
        final Rectangle polyBounds;     // region bounds (for tie-breaks / sanity)
        final Color color;

        PotentialLabel(String text, int cx, int cy, int priority, Rectangle polyBounds, Color color)
        {
            this.text = text;
            this.cx = cx;
            this.cy = cy;
            this.priority = priority;
            this.polyBounds = polyBounds;
            this.color = color;
        }
    }

    private void drawNonOverlappingLabels(Graphics2D g, List<PotentialLabel> candidates, Rectangle mapBounds)
    {
        if (candidates == null || candidates.isEmpty())
        {
            return;
        }

        // Highest priority first
        candidates.sort((a, b) -> Integer.compare(b.priority, a.priority));

        var fm = g.getFontMetrics();

        // Keep track of drawn label rects to avoid overlap
        List<Rectangle> placed = new ArrayList<>();

        // Offsets to try (Expanded from before)
        final int[][] OFFSETS = new int[][]
        {
                { 0, 0 },
                { 0, -12 }, { 0, 12 },
                { -18, 0 }, { 18, 0 },
                { -18, -12 }, { 18, -12 },
                { -18, 12 }, { 18, 12 },
                { 0, -24 }, { 0, 24 },
                { -36, 0 }, { 36, 0 },
                { -36, -12 }, { 36, -12 },
                { -36, 12 }, { 36, 12 },
                { 0, -36 }, { 0, 36 },
                { -24, -24 }, { 24, -24 },
                { -24, 24 }, { 24, 24 }
        };

        for (PotentialLabel c : candidates)
        {
            String text = c.text;
            int textW = fm.stringWidth(text);
            int textH = fm.getAscent();   // good for baseline placement
            int boxH = fm.getHeight();    // for collision rectangle

            boolean placedThis = false;

            for (int[] off : OFFSETS)
            {
                int cx = c.cx + off[0];
                int cy = c.cy + off[1];

                // centered text bounds
                int drawX = cx - (textW / 2);
                int drawY = cy + (textH / 2); // baseline

                Rectangle labelRect = new Rectangle(drawX, drawY - textH, textW, boxH);

                // Must be within map bounds
                if (!mapBounds.contains(labelRect))
                {
                    continue;
                }

                // Collision test
                boolean collides = false;
                for (Rectangle r : placed)
                {
                    if (r.intersects(labelRect))
                    {
                        collides = true;
                        break;
                    }
                }
                if (collides)
                {
                    continue;
                }

                // Draw it
                g.setColor(c.color);
                g.drawString(text, drawX, drawY);

                placed.add(labelRect);
                placedThis = true;
                break;
            }

            // If not placed, we just skip it (lower priority ones will disappear)
            if (!placedThis)
            {
                // optional: do nothing
            }
        }
    }

    private void drawCloseHint(Graphics2D g2d, Rectangle r)
    {
        Graphics2D g = (Graphics2D) g2d.create();
        try
        {
           // g.setClip(null); // ensure it draws even if clip excludes it

            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 165, 0, 220)); // orange

            int inset =5;
            int x1 = r.x + inset;
            int y1 = r.y + inset;
            int x2 = r.x + r.width - inset;
            int y2 = r.y + r.height - inset;

            g.drawLine(x1, y1, x2, y2);
            g.drawLine(x1, y2, x2, y1);
        }
        finally
        {
            g.dispose();
        }
    }

    private boolean shouldDrawLabel(ExplorationRegion region, int level)
    {
        String name = region.getName();
        if(name != null && HIDE_LABELS.contains(name)){
            return false;
        }
        if (region.getType() == ExplorationRegion.RegionType.KINGDOM)
        {
            return level == 0;          // only at most zoomed out
        }
        if (region.getType() == ExplorationRegion.RegionType.SUBREGION)
        {
            return level >= 1 && level <= 3; // visible mid zooms
        }
        return false;
    }

    private int getLabelPriority(ExplorationRegion region, Rectangle polyBounds)
    {
        int base;
        switch (region.getType())
        {
            case KINGDOM:
                base = 2000;
                break;
            case SUBREGION:
                base = 1000;
                break;
            default:
                base = 0;
                break;
        }

        // Bigger regions win ties
        int area = polyBounds.width * polyBounds.height;
        int sizeBonus = Math.min(999, area / 200); // scale factor, tweak if desired

        return base + sizeBonus;
    }


    private static final float[] KINGDOM_ALPHA_BY_LEVEL = { 1.00f, 0.50f, 0.15f, 0.00f, 0.00f };
    private static final float[] SUBREGION_ALPHA_BY_LEVEL = { 0.75f, 1.00f, 0.50f, 0.15f, 0.00f };

    // keep "discovered makes fainter" behavior as an extra multiplier
    private static final float DISCOVERED_MULT = 0.35f;

    private Color getColorForRegion(ExplorationRegion region, int zoomLevel)
    {
        int level = zoomLevel;

        float baseAlpha;
        float zoomMult;

        switch (region.getType())
        {
            case KINGDOM:
                baseAlpha = 220f;
                zoomMult = KINGDOM_ALPHA_BY_LEVEL[level];
                break;

            case SUBREGION:
                baseAlpha = 180f;
                zoomMult = SUBREGION_ALPHA_BY_LEVEL[level];
                break;

            default:
                baseAlpha = 160f;
                zoomMult = 1.0f;
                break;
        }

        float alpha = baseAlpha * zoomMult;

        if (plugin.isRegionDiscovered(region))
        {
            alpha *= DISCOVERED_MULT;
        }

        // clamp
        int a = Math.max(0, Math.min(255, Math.round(alpha)));

        Color c = region.getDisplayColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
