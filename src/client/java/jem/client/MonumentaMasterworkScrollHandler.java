package jem.client;

import java.lang.reflect.Method;

import jem.client.monumenta.MonumentaCatalogEntry;
import jem.client.monumenta.MonumentaMasterworkHoverState;
import jem.client.monumenta.MonumentaMasterworkSelection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

public final class MonumentaMasterworkScrollHandler {
	private MonumentaMasterworkScrollHandler() {
	}

	public static boolean handleMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (!Screen.hasShiftDown()) {
			return false;
		}

		int steps = scrollSteps(horizontalAmount, verticalAmount);
		if (steps == 0) {
			return false;
		}

		MonumentaCatalogEntry entry = hoveredEntry(screen, mouseX, mouseY);
		if (entry != null && entry.isCycling()) {
			MonumentaMasterworkSelection.cycle(entry, steps);
		}
		return true;
	}

	private static int scrollSteps(double horizontalAmount, double verticalAmount) {
		double amount = verticalAmount != 0 ? verticalAmount : horizontalAmount;
		return (int) Math.signum(amount);
	}

	private static MonumentaCatalogEntry hoveredEntry(Screen screen, double mouseX, double mouseY) {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("jei")) {
			MonumentaCatalogEntry entry = invokeStatic(
				"jem.client.integration.jei.NeverEnoughMonumentaJeiPlugin",
				"getHoveredMasterworkEntry"
			);
			if (entry != null) {
				return entry;
			}
		}

		if (loader.isModLoaded("emi")) {
			MonumentaCatalogEntry entry = invokeStatic(
				"jem.client.integration.emi.NeverEnoughMonumentaEmiPlugin",
				"getHoveredMasterworkEntry"
			);
			if (entry != null) {
				return entry;
			}
		}

		if (loader.isModLoaded("roughlyenoughitems")) {
			MonumentaCatalogEntry entry = invokeStatic(
				"jem.client.integration.rei.NeverEnoughMonumentaReiPlugin",
				"getHoveredMasterworkEntry",
				new Class<?>[]{Screen.class, double.class, double.class},
				screen,
				mouseX,
				mouseY
			);
			if (entry != null) {
				return entry;
			}
		}

		return MonumentaMasterworkHoverState.currentHovered(mouseX, mouseY);
	}

	private static MonumentaCatalogEntry invokeStatic(String className, String methodName) {
		return invokeStatic(className, methodName, new Class<?>[0]);
	}

	private static MonumentaCatalogEntry invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			Class<?> type = Class.forName(className);
			Method method = type.getMethod(methodName, parameterTypes);
			Object result = method.invoke(null, args);
			return result instanceof MonumentaCatalogEntry entry ? entry : null;
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return null;
		}
	}
}