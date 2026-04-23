package jem.client.monumenta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record MonumentaItemDefinition(
	String key,
	String name,
	String baseItemName,
	String rawNbt,
	String type,
	String tier,
	String region,
	String location,
	String className,
	String releaseStatus,
	String masterwork,
	List<String> lore,
	List<String> mmLore,
	Map<String, MonumentaStat> stats,
	List<String> aliases
) {
	public static MonumentaItemDefinition fromJson(String key, JsonObject json) {
		String name = readString(json, "name");
		String baseItemName = readString(json, "base_item");
		String rawNbt = readString(json, "nbt");
		String type = readString(json, "type");
		String tier = readString(json, "tier");
		String region = readString(json, "region");
		String location = readString(json, "location");
		String className = readString(json, "class_name");
		String releaseStatus = readString(json, "release_status");
		String masterwork = readString(json, "masterwork");
		List<String> lore = readLore(json, "lore");
		List<String> mmLore = readStringList(json, "mmlore");
		Map<String, MonumentaStat> stats = readStats(json.get("stats"));
		List<String> loreAliases = mmLore.isEmpty() ? lore : mmLore;

		Set<String> aliases = new LinkedHashSet<>();
		addAlias(aliases, key);
		addAlias(aliases, name);
		addAlias(aliases, baseItemName);
		addAlias(aliases, type);
		addAlias(aliases, tier);
		addAlias(aliases, region);
		addAlias(aliases, location);
		addAlias(aliases, className);
		addAlias(aliases, releaseStatus);
		addAlias(aliases, masterwork);
		loreAliases.stream().limit(6).forEach(line -> addAlias(aliases, line));
		stats.keySet().stream().limit(12).forEach(statKey -> addAlias(aliases, formatStatAlias(statKey)));

		return new MonumentaItemDefinition(
			key,
			name.isEmpty() ? key : name,
			baseItemName,
			rawNbt,
			type,
			tier,
			region,
			location,
			className,
			releaseStatus,
			masterwork,
			List.copyOf(lore),
			List.copyOf(mmLore),
			Collections.unmodifiableMap(new LinkedHashMap<>(stats)),
			List.copyOf(aliases)
		);
	}

	private static String readString(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString().trim();
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			List<String> values = new ArrayList<>(array.size());
			for (JsonElement entry : array) {
				if (entry != null && entry.isJsonPrimitive()) {
					String value = entry.getAsString().trim();
					if (!value.isEmpty()) {
						values.add(value);
					}
				}
			}
			return String.join(", ", values);
		}
		return "";
	}

	private static List<String> readLore(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (element.isJsonPrimitive()) {
			String value = element.getAsString().trim();
			if (value.isEmpty()) {
				return List.of();
			}
			String[] lines = value.split("\\R");
			List<String> parsed = new ArrayList<>(lines.length);
			for (String line : lines) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					parsed.add(trimmed);
				}
			}
			return parsed;
		}
		return readStringList(json, key);
	}

	private static List<String> readStringList(JsonObject json, String key) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (element.isJsonPrimitive()) {
			String value = element.getAsString().trim();
			return value.isEmpty() ? List.of() : List.of(value);
		}
		if (!element.isJsonArray()) {
			return List.of();
		}

		List<String> values = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			if (entry != null && entry.isJsonPrimitive()) {
				String value = entry.getAsString().trim();
				if (!value.isEmpty()) {
					values.add(value);
				}
			}
		}
		return values;
	}

	private static Map<String, MonumentaStat> readStats(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonObject()) {
			return Map.of();
		}

		Map<String, MonumentaStat> stats = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			MonumentaStat stat = MonumentaStat.fromJson(entry.getValue());
			if (stat != null) {
				stats.put(entry.getKey(), stat);
			}
		}
		return stats;
	}

	private static String formatStatAlias(String statKey) {
		String[] parts = statKey.split("_");
		StringBuilder builder = new StringBuilder(statKey.length());
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				builder.append(part.substring(1));
			}
		}
		return builder.toString();
	}

	private static void addAlias(Set<String> aliases, String value) {
		if (value == null) {
			return;
		}
		String cleaned = value.trim();
		if (!cleaned.isEmpty()) {
			aliases.add(cleaned);
		}
	}
}