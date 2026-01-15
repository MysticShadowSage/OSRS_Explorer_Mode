package com.explorerMode;

import net.runelite.api.coords.WorldPoint;
import java.util.Set;

public class unlockNPC
{
    private final int npcID;
    private final String displayName;
    private final Set<Chunk> unlockChunks;

    public unlockNPC(int npcID, String displayName, Set<Chunk> unlockChunks)
    {
        this.npcID = npcID;
        this.displayName = displayName;
        this.unlockChunks = unlockChunks;
    }

    public int getNpcID()
    { return npcID; }

    public String getDisplayName()
    { return displayName; }

    public Set<Chunk> getUnlockChunks() {
        return unlockChunks;
    }
}