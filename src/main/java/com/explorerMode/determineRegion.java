package com.explorerMode;

import net.runelite.api.coords.WorldPoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.util.Arrays;
import java.util.List;


public class determineRegion {
    public static void main(String[] args) throws Exception {
        List<WorldPoint> varrockPolygon = Arrays.asList(
                new WorldPoint(3180, 3410, 0),
                new WorldPoint(3300, 3410, 0),
                new WorldPoint(3310, 3440, 0),
                new WorldPoint(3300, 3500, 0),
                new WorldPoint(3180, 3500, 0),
                new WorldPoint(3160, 3480, 0)
        );

        Set<Chunk> varrockChunks = generateChunkRegionFromPolygon(varrockPolygon);

        // Save varrockChunks to JSON file
        saveChunksToJson(varrockChunks, "KING_ROALD.json");
    }

    public static Set<Chunk> generateChunkRegionFromPolygon(List<WorldPoint> polygon) {
        Set<Chunk> chunks = new HashSet<>();
        // Compute bounding box
        int minX = polygon.stream().mapToInt(WorldPoint::getX).min().orElse(0);
        int maxX = polygon.stream().mapToInt(WorldPoint::getX).max().orElse(0);
        int minY = polygon.stream().mapToInt(WorldPoint::getY).min().orElse(0);
        int maxY = polygon.stream().mapToInt(WorldPoint::getY).max().orElse(0);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                WorldPoint p = new WorldPoint(x, y, 0); // assuming plane 0
                if (pointInPolygon(p, polygon)) {
                    chunks.add(new Chunk(x >> 3, y >> 3, 0));
                }
            }
        }
        return chunks;
    }

    private static boolean pointInPolygon(WorldPoint p, List<WorldPoint> polygon) {
        int crossings = 0;
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            WorldPoint a = polygon.get(i);
            WorldPoint b = polygon.get((i + 1) % count);
            if (((a.getY() > p.getY()) != (b.getY() > p.getY())) &&
                    (p.getX() < (b.getX() - a.getX()) * (p.getY() - a.getY()) / (b.getY() - a.getY()) + a.getX())) {
                crossings++;
            }
        }
        return (crossings % 2) == 1;
    }
    // saveChunksToJson() — serialize Set<Chunk> to JSO



    public static void saveChunksToJson(Set<Chunk> chunks, String filePath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(filePath)) {
                gson.toJson(chunks, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
}