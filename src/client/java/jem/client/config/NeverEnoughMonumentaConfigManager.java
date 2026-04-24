package jem.client.config;

import me.sargunvohra.mcmods.autoconfig1u.AutoConfig;
import me.sargunvohra.mcmods.autoconfig1u.ConfigHolder;
import me.sargunvohra.mcmods.autoconfig1u.serializer.Toml4jConfigSerializer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NeverEnoughMonumentaConfigManager {
	private static final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private static ConfigHolder<NeverEnoughMonumentaConfig> holder;

	private NeverEnoughMonumentaConfigManager() {
	}

	public static void initialize() {
		if (holder != null) {
			return;
		}
		AutoConfig.register(NeverEnoughMonumentaConfig.class, Toml4jConfigSerializer::new);
		holder = AutoConfig.getConfigHolder(NeverEnoughMonumentaConfig.class);
	}

	public static NeverEnoughMonumentaConfig config() {
		return holder.getConfig();
	}

	public static void registerChangeListener(Runnable listener) {
		changeListeners.add(listener);
	}

	public static boolean hideVanillaItemStacks() {
		return config().hideVanillaItemStacks;
	}

	public static boolean hideCustomMonumentaEntries() {
		return config().hideCustomMonumentaEntries;
	}

	public static void save() {
		holder.save();
		for (Runnable listener : changeListeners) {
			listener.run();
		}
	}

	public static void toggleHideVanillaItemStacks() {
		config().hideVanillaItemStacks = !config().hideVanillaItemStacks;
		save();
	}

	public static void toggleHideCustomMonumentaEntries() {
		config().hideCustomMonumentaEntries = !config().hideCustomMonumentaEntries;
		save();
	}
}