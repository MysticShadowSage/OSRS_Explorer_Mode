// ExplorerSaveData.java
package com.explorerMode;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;

import java.util.HashSet;
import java.util.Set;

public class ExplorerSaveData {
    @Getter @Setter
    private Set<SavedChunk> discoveredChunks = new HashSet<>();

    @Getter @Setter
    private Set<String> discoveredRegions = new HashSet<>();

    @Getter @Setter
    private Set<String> activeSubregions = new HashSet<>();

    @Getter @Setter
    private Set<String> activeChunkGroups = new HashSet<>();

    @Getter @Setter
    private int version = 2;
}