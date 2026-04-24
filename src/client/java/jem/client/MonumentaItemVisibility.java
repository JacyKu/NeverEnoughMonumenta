package jem.client;

import jem.NeverEnoughMonumenta;
import jem.client.config.NeverEnoughMonumentaConfigManager;
import jem.client.monumenta.MonumentaCatalogEntry;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class MonumentaItemVisibility {
	private MonumentaItemVisibility() {
	}

	public static boolean isMonumentaStack(ItemStack stack) {
		return !stack.isEmpty()
			&& stack.hasTag()
			&& stack.getTag().contains(NeverEnoughMonumenta.MOD_ID, Tag.TAG_COMPOUND);
	}

	public static boolean isVanillaMinecraftStack(ItemStack stack) {
		return !stack.isEmpty()
			&& !isMonumentaStack(stack)
			&& "minecraft".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
	}

	public static boolean shouldHideStack(ItemStack stack) {
		return (NeverEnoughMonumentaConfigManager.hideCustomMonumentaEntries() && isMonumentaStack(stack))
			|| (NeverEnoughMonumentaConfigManager.hideVanillaItemStacks() && isVanillaMinecraftStack(stack));
	}

	public static boolean shouldHideEntry(MonumentaCatalogEntry entry) {
		return NeverEnoughMonumentaConfigManager.hideCustomMonumentaEntries() && entry != null;
	}
}