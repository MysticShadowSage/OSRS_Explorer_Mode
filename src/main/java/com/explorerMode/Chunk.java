package com.explorerMode;
import java.util.Objects;

public class Chunk {
    private final int x;
    private final int y;
    private final int plane;

    public Chunk(int x, int y, int plane) {
        this.x = x;
        this.y = y;
        this.plane = plane;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getPlane() { return plane; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chunk)) return false;
        Chunk chunk = (Chunk) o;
        return x == chunk.x && y == chunk.y && plane == chunk.plane;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, plane);
    }
}

