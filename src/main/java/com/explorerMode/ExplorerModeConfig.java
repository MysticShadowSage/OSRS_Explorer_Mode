package com.explorerMode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import com.explorerMode.MapMode;

import java.nio.channels.FileChannel;

@ConfigGroup("example")
public interface ExplorerModeConfig extends Config
{
	@ConfigItem(
			keyName = "mode",
			name = "Map Mode",
			description = "Select map reveal mode"
	)
	default MapMode mode()
	{
		return MapMode.EXPLORER;
	}

	@ConfigItem(
			keyName = "revealRadius",
			name = "Reveal Radius",
			description = "Radius of tiles revealed around player."
	)
	default int revealRadius()
	{
		return 3;
	}
}

