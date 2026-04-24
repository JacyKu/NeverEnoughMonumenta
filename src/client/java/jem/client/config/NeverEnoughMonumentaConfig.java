package jem.client.config;

import jem.NeverEnoughMonumenta;
import me.sargunvohra.mcmods.autoconfig1u.ConfigData;
import me.sargunvohra.mcmods.autoconfig1u.annotation.Config;

@Config(name = NeverEnoughMonumenta.MOD_ID)
public class NeverEnoughMonumentaConfig implements ConfigData {
	public boolean hideVanillaItemStacks = true;
	public boolean hideCustomMonumentaEntries = false;
}