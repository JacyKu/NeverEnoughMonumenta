package jem.client;

import com.mojang.blaze3d.platform.InputConstants;
import jem.NeverEnoughMonumenta;
import jem.client.config.NeverEnoughMonumentaConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.Map;

public final class NeverEnoughMonumentaKeyBindings {
	private static final String CATEGORY = "category.neverenoughmonumenta";
	private static final Map<KeyMapping, Boolean> PREVIOUS_KEY_STATES = new IdentityHashMap<>();
	private static final KeyMapping TOGGLE_HIDE_VANILLA_ITEM_STACKS = KeyBindingHelper.registerKeyBinding(
		new KeyMapping(
			"key.neverenoughmonumenta.toggle_hide_vanilla_item_stacks",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			CATEGORY
		)
	);
	private static final KeyMapping TOGGLE_HIDE_CUSTOM_MONUMENTA_ENTRIES = KeyBindingHelper.registerKeyBinding(
		new KeyMapping(
			"key.neverenoughmonumenta.toggle_hide_custom_monumenta_entries",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			CATEGORY
		)
	);
	private static boolean initialized;

	private NeverEnoughMonumentaKeyBindings() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (consumeGlobalClick(client, TOGGLE_HIDE_VANILLA_ITEM_STACKS)) {
				NeverEnoughMonumentaConfigManager.toggleHideVanillaItemStacks();
				displayStatus(client,
					NeverEnoughMonumentaConfigManager.hideVanillaItemStacks()
						? Component.translatable("neverenoughmonumenta.message.hide_vanilla_item_stacks.enabled")
						: Component.translatable("neverenoughmonumenta.message.hide_vanilla_item_stacks.disabled")
				);
			}

			while (consumeGlobalClick(client, TOGGLE_HIDE_CUSTOM_MONUMENTA_ENTRIES)) {
				NeverEnoughMonumentaConfigManager.toggleHideCustomMonumentaEntries();
				displayStatus(client,
					NeverEnoughMonumentaConfigManager.hideCustomMonumentaEntries()
						? Component.translatable("neverenoughmonumenta.message.hide_custom_monumenta_entries.enabled")
						: Component.translatable("neverenoughmonumenta.message.hide_custom_monumenta_entries.disabled")
				);
			}
		});
	}

	private static boolean consumeGlobalClick(Minecraft client, KeyMapping keyMapping) {
		boolean isDown = isBindingDown(client, keyMapping);
		boolean wasDown = PREVIOUS_KEY_STATES.getOrDefault(keyMapping, false);
		PREVIOUS_KEY_STATES.put(keyMapping, isDown);
		return isDown && !wasDown;
	}

	private static boolean isBindingDown(Minecraft client, KeyMapping keyMapping) {
		if (!client.isWindowActive()) {
			return false;
		}
		if (keyMapping.isUnbound()) {
			return false;
		}

		InputConstants.Key key = InputConstants.getKey(keyMapping.saveString());
		if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
			return false;
		}

		long window = client.getWindow().getWindow();
		return switch (key.getType()) {
			case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
			case KEYSYM, SCANCODE -> GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
		};
	}

	private static void displayStatus(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.displayClientMessage(message, true);
			return;
		}
		NeverEnoughMonumenta.LOGGER.info(message.getString());
	}
}