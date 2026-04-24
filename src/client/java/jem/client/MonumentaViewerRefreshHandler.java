package jem.client;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class MonumentaViewerRefreshHandler {
	private MonumentaViewerRefreshHandler() {
	}

	public static void refreshAll() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("jei")) {
			invokeStatic("jem.client.integration.jei.NeverEnoughMonumentaJeiPlugin", "refreshRuntime");
		}
		if (loader.isModLoaded("emi")) {
			invokeStatic("jem.client.integration.emi.NeverEnoughMonumentaEmiPlugin", "refreshRuntime");
		}
		if (loader.isModLoaded("roughlyenoughitems")) {
			invokeStatic("jem.client.integration.rei.NeverEnoughMonumentaReiPlugin", "refreshRuntime");
		}
	}

	private static void invokeStatic(String className, String methodName) {
		try {
			Class<?> type = Class.forName(className);
			Method method = type.getMethod(methodName);
			method.invoke(null);
		} catch (ReflectiveOperationException | LinkageError ignored) {
		}
	}
}