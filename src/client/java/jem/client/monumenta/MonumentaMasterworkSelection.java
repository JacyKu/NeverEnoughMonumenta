package jem.client.monumenta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MonumentaMasterworkSelection {
	private static final Map<String, Integer> SELECTED_VARIANTS = new ConcurrentHashMap<>();

	private MonumentaMasterworkSelection() {
	}

	public static int selectedVariantIndex(MonumentaCatalogEntry entry) {
		if (!entry.isCycling()) {
			return 0;
		}
		return Math.floorMod(SELECTED_VARIANTS.getOrDefault(entry.logicalKey(), 0), entry.variantCount());
	}

	public static void cycle(MonumentaCatalogEntry entry, int steps) {
		if (!entry.isCycling() || steps == 0) {
			return;
		}
		SELECTED_VARIANTS.compute(entry.logicalKey(), (logicalKey, currentIndex) -> {
			int baseIndex = currentIndex == null ? 0 : currentIndex;
			return Math.floorMod(baseIndex + steps, entry.variantCount());
		});
	}
}