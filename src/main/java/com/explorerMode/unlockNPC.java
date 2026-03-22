package com.explorerMode;

import net.runelite.api.coords.WorldPoint;
import java.util.Set;
//For now, this class is dead code.
//Will be used when implementing NPC unlocks soon.

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