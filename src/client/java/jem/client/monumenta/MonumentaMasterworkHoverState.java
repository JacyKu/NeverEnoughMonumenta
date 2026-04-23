package jem.client.monumenta;

public final class MonumentaMasterworkHoverState {
	private static final long HOVER_TTL_NANOS = 1_000_000_000L;
	private static final double HOVER_RADIUS = 6.0D;

	private static volatile MonumentaCatalogEntry hoveredEntry;
	private static volatile long expiresAtNanos;
	private static volatile double hoveredMouseX;
	private static volatile double hoveredMouseY;

	private MonumentaMasterworkHoverState() {
	}

	public static void markHovered(MonumentaCatalogEntry entry, double mouseX, double mouseY) {
		hoveredEntry = entry;
		expiresAtNanos = System.nanoTime() + HOVER_TTL_NANOS;
		hoveredMouseX = mouseX;
		hoveredMouseY = mouseY;
	}

	public static MonumentaCatalogEntry currentHovered(double mouseX, double mouseY) {
		MonumentaCatalogEntry entry = hoveredEntry;
		if (entry == null) {
			return null;
		}
		if (System.nanoTime() > expiresAtNanos) {
			hoveredEntry = null;
			return null;
		}
		double deltaX = hoveredMouseX - mouseX;
		double deltaY = hoveredMouseY - mouseY;
		if ((deltaX * deltaX) + (deltaY * deltaY) > (HOVER_RADIUS * HOVER_RADIUS)) {
			return null;
		}
		return entry;
	}
}