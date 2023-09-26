package com.ywcode.resourceareanotifier;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ResourceAreaNotifierTest {
	public static void main(String[] args) throws Exception	{
		ExternalPluginManager.loadBuiltin(ResourceAreaNotifierPlugin.class);
		RuneLite.main(args);
	}
}