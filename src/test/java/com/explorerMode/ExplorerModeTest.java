package com.explorerMode;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExplorerModeTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ExplorerModePlugin.class);
		RuneLite.main(args);
	}
}