package com.chrisapi;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("chrisapi")
public interface ChrisAPIConfig extends Config
{
	@ConfigItem(
			keyName = "PortNum",
			name = "Port number",
			description = "Specify the port to open the webserver towards (default 8081)"
	)
	@Range(min = 1, max = 20000)
	default int portNum()
	{
		return 8081;
	}
}
