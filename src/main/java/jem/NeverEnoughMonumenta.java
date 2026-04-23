package jem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class NeverEnoughMonumenta implements ModInitializer {
	public static final String MOD_ID = "neverenoughmonumenta";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Loading {}", MOD_ID);
	}
}