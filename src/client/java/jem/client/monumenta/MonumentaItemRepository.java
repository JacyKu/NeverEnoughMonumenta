package jem.client.monumenta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jem.NeverEnoughMonumenta;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MonumentaItemRepository {
	private static final URI ITEMS_ENDPOINT = URI.create("https://api.playmonumenta.com/itemswithnbt");
	private static final Path CACHE_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve(NeverEnoughMonumenta.MOD_ID)
		.resolve("monumenta-items-cache.json");

	private static volatile MonumentaCatalog catalog;

	private MonumentaItemRepository() {
	}

	public static void prime() {
		getCatalog();
	}

	public static MonumentaCatalog getCatalog() {
		MonumentaCatalog loaded = catalog;
		if (loaded != null) {
			return loaded;
		}

		synchronized (MonumentaItemRepository.class) {
			if (catalog == null) {
				catalog = loadCatalog();
			}
			return catalog;
		}
	}

	public static MonumentaCatalogEntry findCyclingEntry(ItemStack stack) {
		String subtypeKey = MonumentaStackFactory.getSubtypeKey(stack);
		if (subtypeKey == null || subtypeKey.isBlank()) {
			return null;
		}

		for (MonumentaCatalogEntry entry : getCatalog().cyclingEntries()) {
			if (entry.matchesVariantSubtype(subtypeKey)) {
				return entry;
			}
		}
		return null;
	}

	private static MonumentaCatalog loadCatalog() {
		try {
			String response = fetchRemoteJson();
			writeCache(response);
			MonumentaCatalog loaded = parseCatalog(response);
			NeverEnoughMonumenta.LOGGER.info("Loaded {} Monumenta items from API", loaded.entries().size());
			return loaded;
		} catch (IOException | RuntimeException fetchFailure) {
			NeverEnoughMonumenta.LOGGER.warn("Failed to fetch Monumenta items from API, trying cache", fetchFailure);
		}

		try {
			if (Files.exists(CACHE_PATH)) {
				String cachedJson = Files.readString(CACHE_PATH, StandardCharsets.UTF_8);
				MonumentaCatalog loaded = parseCatalog(cachedJson);
				NeverEnoughMonumenta.LOGGER.info("Loaded {} Monumenta items from cache", loaded.entries().size());
				return loaded;
			}
		} catch (IOException | RuntimeException cacheFailure) {
			NeverEnoughMonumenta.LOGGER.error("Failed to read Monumenta cache", cacheFailure);
		}

		NeverEnoughMonumenta.LOGGER.error("No Monumenta data available, recipe viewers will stay empty");
		return MonumentaCatalog.EMPTY;
	}

	private static String fetchRemoteJson() throws IOException {
		HttpURLConnection connection = (HttpURLConnection) ITEMS_ENDPOINT.toURL().openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(20_000);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("User-Agent", NeverEnoughMonumenta.MOD_ID + "/1.0.0");

		int responseCode = connection.getResponseCode();
		if (responseCode < 200 || responseCode >= 300) {
			throw new IOException("Unexpected Monumenta API response: " + responseCode);
		}

		try (InputStream stream = connection.getInputStream(); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[8_192];
			int read;
			while ((read = reader.read(buffer)) != -1) {
				builder.append(buffer, 0, read);
			}
			return builder.toString();
		} finally {
			connection.disconnect();
		}
	}

	private static void writeCache(String json) {
		try {
			Files.createDirectories(CACHE_PATH.getParent());
			Files.writeString(CACHE_PATH, json, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			NeverEnoughMonumenta.LOGGER.warn("Failed to write Monumenta cache", exception);
		}
	}

	private static MonumentaCatalog parseCatalog(String json) {
		JsonElement rootElement = JsonParser.parseString(json);
		if (!rootElement.isJsonObject()) {
			throw new IllegalStateException("Monumenta items response is not a JSON object");
		}

		JsonObject root = rootElement.getAsJsonObject();
		Map<String, GroupedEntryBuilder> groupedEntries = new LinkedHashMap<>(root.size());
		Set<Item> baseItems = new LinkedHashSet<>();

		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonObject()) {
				continue;
			}

			try {
				MonumentaItemDefinition definition = MonumentaItemDefinition.fromJson(entry.getKey(), value.getAsJsonObject());
				ItemStack stack = MonumentaStackFactory.createStack(definition);
				baseItems.add(stack.getItem());
				String logicalKey = buildLogicalKey(definition);
				groupedEntries.computeIfAbsent(logicalKey, GroupedEntryBuilder::new).add(definition, stack);
			} catch (Exception exception) {
				NeverEnoughMonumenta.LOGGER.warn("Skipping Monumenta item '{}' due to parse error", entry.getKey(), exception);
			}
		}

		List<MonumentaCatalogEntry> entries = groupedEntries.values().stream().map(GroupedEntryBuilder::build).toList();
		return new MonumentaCatalog(entries, baseItems);
	}

	private static String buildLogicalKey(MonumentaItemDefinition definition) {
		if (definition.masterwork().isBlank()) {
			return "single|" + definition.key();
		}
		return "masterwork|"
			+ definition.name() + '|'
			+ definition.baseItemName() + '|'
			+ definition.type() + '|'
			+ definition.location() + '|'
			+ definition.region() + '|'
			+ definition.releaseStatus() + '|'
			+ definition.className();
	}

	private static int parseMasterworkRank(String value) {
		if (value == null || value.isBlank()) {
			return Integer.MIN_VALUE;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return Integer.MIN_VALUE;
		}
	}

	private record GroupedVariant(MonumentaItemDefinition definition, ItemStack stack) {
	}

	private static final class GroupedEntryBuilder {
		private final String logicalKey;
		private final List<GroupedVariant> variants = new ArrayList<>();

		private GroupedEntryBuilder(String logicalKey) {
			this.logicalKey = logicalKey;
		}

		private void add(MonumentaItemDefinition definition, ItemStack stack) {
			variants.add(new GroupedVariant(definition, stack));
		}

		private MonumentaCatalogEntry build() {
			List<GroupedVariant> ordered = variants.stream()
				.sorted(
					Comparator.comparingInt((GroupedVariant variant) -> parseMasterworkRank(variant.definition().masterwork()))
						.thenComparing(variant -> variant.definition().key())
				)
				.toList();
			List<MonumentaItemDefinition> definitions = ordered.stream().map(GroupedVariant::definition).toList();
			List<ItemStack> stacks = ordered.stream().map(GroupedVariant::stack).toList();
			return new MonumentaCatalogEntry(logicalKey, definitions.get(0), definitions, stacks);
		}
	}
}