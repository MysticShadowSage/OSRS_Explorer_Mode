package com.explorerMode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("explorermode")
public interface ExplorerModeConfig extends Config
{
	@ConfigItem(
			keyName = "mode",
			name = "Map Mode",
			description = "Select the map reveal mode you want."
	)
	default MapMode mode()
	{
		return MapMode.REGULAR;
	}

	@ConfigItem(
			keyName = "showRegions",
			name = "Show regions",
			description = "Show the regions of all the Kingdoms of Gielinor on the map."
	)
	default boolean showRegions() { return true; }
}

