package jem.client.monumenta;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jem.NeverEnoughMonumenta;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MonumentaStackFactory {
	private static final String INTERNAL_TAG = NeverEnoughMonumenta.MOD_ID;
	private static final String INTERNAL_KEY = "key";
	private static final String INTERNAL_BASE_ITEM = "base_item";
	private static final String MONUMENTA_TAG = "Monumenta";
	private static final int DEFAULT_HIDE_FLAGS = 3;
	private static final int POTION_HIDE_FLAGS = 33;
	private static final int LOCATION_COLOR = 0xC2C4C4;
	private static final int SPECIAL_KEEP_LOCATION_COLOR = 0xC4BBA5;
	private static final int DEFAULT_POTION_NAME_COLOR = 0xFFFF55;
	private static final int DEFAULT_MAINHAND_NAME_COLOR = 0xEEE6D6;
	private static final int POSITIVE_ADD_COLOR = 0x33CCFF;
	private static final int POSITIVE_MULTIPLY_COLOR = 0x5555FF;
	private static final int NEGATIVE_COLOR = 0xFF5555;
	private static final Map<String, String> LOCATION_TAG_OVERRIDES = createLocationTagOverrides();
	private static final Map<String, String> LOCATION_DISPLAY_OVERRIDES = createLocationDisplayOverrides();
	private static final Map<String, String> SPECIAL_CASE_PATHS = createSpecialCasePaths();

	private MonumentaStackFactory() {
	}

	public static ItemStack createStack(MonumentaItemDefinition definition) {
		Item item = resolveBaseItem(definition.baseItemName());
		ItemStack exactStack = createExactStack(definition, item);
		if (exactStack != null) {
			return exactStack;
		}

		ItemStack stack = new ItemStack(item);
		String slot = resolveSlot(definition, item);
		TierStyle tierStyle = getTierStyle(definition.tier());
		int nameColor = resolveNameColor(item, slot, tierStyle);
		List<MonumentaAttribute> attributes = collectAttributes(definition, slot);
		List<MonumentaEnchantment> enchantments = collectEnchantments(definition);

		writeInternalIdentity(stack, definition);
		writeBaseItemData(stack, item);
		stack.getOrCreateTag().putInt("HideFlags", resolveHideFlags(item));
		writeVanillaEnchantments(stack, item, enchantments);
		writeVanillaAttributes(stack, definition, attributes);
		writeMonumentaData(stack, definition, item, nameColor, attributes, enchantments, slot);
		writeDisplayData(stack, definition, item, slot, nameColor, attributes, enchantments);
		return stack;
	}

	private static ItemStack createExactStack(MonumentaItemDefinition definition, Item item) {
		if (definition.rawNbt().isBlank()) {
			return null;
		}

		try {
			CompoundTag exactTag = TagParser.parseTag(definition.rawNbt());
			ItemStack stack = new ItemStack(item);
			stack.setTag(exactTag);
			return stack;
		} catch (CommandSyntaxException exception) {
			NeverEnoughMonumenta.LOGGER.warn("Failed to parse Monumenta SNBT for '{}', falling back to synthesized stack", definition.key(), exception);
			return null;
		}
	}

	public static String getSubtypeKey(ItemStack stack) {
		CompoundTag internalTag = getTagElement(stack, INTERNAL_TAG);
		if (internalTag == null) {
			CompoundTag tag = stack.getTag();
			return tag == null ? null : tag.toString();
		}
		String key = internalTag.getString(INTERNAL_KEY);
		return key.isBlank() ? null : key;
	}

	private static CompoundTag getTagElement(ItemStack stack, String tagName) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(tagName, Tag.TAG_COMPOUND)) {
			return null;
		}
		return tag.getCompound(tagName);
	}

	private static void writeInternalIdentity(ItemStack stack, MonumentaItemDefinition definition) {
		CompoundTag internalTag = stack.getOrCreateTagElement(INTERNAL_TAG);
		internalTag.putString(INTERNAL_KEY, definition.key());
		internalTag.putString(INTERNAL_BASE_ITEM, definition.baseItemName());
	}

	private static void writeBaseItemData(ItemStack stack, Item item) {
		if (isPotionItem(item)) {
			CompoundTag tag = stack.getOrCreateTag();
			tag.putString("Potion", "minecraft:awkward");
			tag.putInt("CustomPotionColor", 0xFFFFFF);
		}
	}

	private static void writeVanillaEnchantments(ItemStack stack, Item item, List<MonumentaEnchantment> enchantments) {
		ListTag enchantmentList = new ListTag();
		if (isPotionItem(item) && !enchantments.isEmpty()) {
			CompoundTag enchantmentTag = new CompoundTag();
			enchantmentTag.putString("id", "minecraft:power");
			enchantmentTag.putShort("lvl", (short) 1);
			enchantmentList.add(enchantmentTag);
		}

		for (MonumentaEnchantment enchantment : enchantments) {
			if (isPotionItem(item)) {
				continue;
			}
			if (enchantment.vanillaEnchantmentId() == null || !enchantment.stat().isWholeNumber()) {
				continue;
			}

			CompoundTag enchantmentTag = new CompoundTag();
			enchantmentTag.putString("id", enchantment.vanillaEnchantmentId());
			enchantmentTag.putShort("lvl", (short) enchantment.stat().intValue());
			enchantmentList.add(enchantmentTag);
		}

		if (!enchantmentList.isEmpty()) {
			stack.getOrCreateTag().put("Enchantments", enchantmentList);
		}
	}

	private static void writeVanillaAttributes(ItemStack stack, MonumentaItemDefinition definition, List<MonumentaAttribute> attributes) {
		ListTag modifierList = new ListTag();
		for (MonumentaAttribute attribute : attributes) {
			if (!attribute.writeVanillaModifier()) {
				continue;
			}

			CompoundTag modifierTag = new CompoundTag();
			modifierTag.putDouble("Amount", attribute.vanillaAmount());
			modifierTag.putString("AttributeName", attribute.vanillaAttributeId());
			modifierTag.putString("Name", "Modifier");
			modifierTag.putInt("Operation", attribute.vanillaOperation());
			if (attribute.slot() != null) {
				modifierTag.putString("Slot", attribute.slot());
			}

			UUID uuid = UUID.nameUUIDFromBytes((definition.key() + ":" + attribute.statKey()).getBytes(StandardCharsets.UTF_8));
			modifierTag.putUUID("UUID", uuid);
			modifierList.add(modifierTag);
		}

		if (!modifierList.isEmpty()) {
			stack.getOrCreateTag().put("AttributeModifiers", modifierList);
		}
	}

	private static void writeMonumentaData(ItemStack stack, MonumentaItemDefinition definition, Item item, int nameColor, List<MonumentaAttribute> attributes, List<MonumentaEnchantment> enchantments, String slot) {
		CompoundTag monumentaTag = stack.getOrCreateTagElement(MONUMENTA_TAG);
		putNormalized(monumentaTag, "Tier", definition.tier());
		putNormalized(monumentaTag, "Region", definition.region());
		putNormalized(monumentaTag, "Location", definition.location());
		putDirect(monumentaTag, "Masterwork", definition.masterwork());
		putDirect(monumentaTag, "MMName", buildMmName(definition, item, nameColor));

		List<String> mmLore = definition.mmLore().isEmpty() ? definition.lore() : definition.mmLore();
		if (!mmLore.isEmpty()) {
			monumentaTag.put("MMLore", toStringList(mmLore));
		}

		CompoundTag stockTag = new CompoundTag();
		if (!attributes.isEmpty()) {
			ListTag stockAttributes = new ListTag();
			for (MonumentaAttribute attribute : attributes) {
				CompoundTag attributeTag = new CompoundTag();
				attributeTag.putDouble("Amount", attribute.stockAmount());
				attributeTag.putString("AttributeName", attribute.stockName());
				attributeTag.putString("Operation", attribute.stockOperation());
				if (attribute.slot() != null) {
					attributeTag.putString("Slot", attribute.slot());
				}
				stockAttributes.add(attributeTag);
			}
			stockTag.put("Attributes", stockAttributes);
		}

		if (!enchantments.isEmpty()) {
			CompoundTag stockEnchantments = new CompoundTag();
			if (isMainhandLike(slot) && !isPotionItem(item)) {
				CompoundTag disableTag = new CompoundTag();
				disableTag.putInt("Level", 1);
				stockEnchantments.put("MainhandOffhandDisable", disableTag);
			}

			for (MonumentaEnchantment enchantment : sortStockEnchantments(enchantments)) {
				CompoundTag levelTag = new CompoundTag();
				if (enchantment.stat().isWholeNumber()) {
					levelTag.putInt("Level", enchantment.stat().intValue());
				} else {
					levelTag.putDouble("Level", enchantment.stat().value());
				}
				if (enchantment.stat().locked()) {
					levelTag.putBoolean("Locked", true);
				}
				stockEnchantments.put(enchantment.stockName(), levelTag);
			}
			stockTag.put("Enchantments", stockEnchantments);
		}

		if (!stockTag.isEmpty()) {
			monumentaTag.put("Stock", stockTag);
		}
	}

	private static void writeDisplayData(ItemStack stack, MonumentaItemDefinition definition, Item item, String slot, int nameColor, List<MonumentaAttribute> attributes, List<MonumentaEnchantment> enchantments) {
		Component displayName = buildDisplayName(definition, item, nameColor);
		List<LoreLine> loreLines = buildDisplayLore(definition, slot, attributes, enchantments);

		CompoundTag displayTag = stack.getOrCreateTagElement("display");
		displayTag.putString("Name", Component.Serializer.toJson(displayName));
		if (!loreLines.isEmpty()) {
			displayTag.put("Lore", toDisplayLore(loreLines));
		}
		if (item instanceof DyeableLeatherItem) {
			displayTag.putInt("color", nameColor);
		}

		CompoundTag plainTag = stack.getOrCreateTagElement("plain");
		CompoundTag plainDisplayTag = new CompoundTag();
		plainDisplayTag.putString("Name", definition.name());
		if (!loreLines.isEmpty()) {
			plainDisplayTag.put("Lore", toPlainLore(loreLines));
		}
		plainTag.put("display", plainDisplayTag);
	}

	private static List<LoreLine> buildDisplayLore(MonumentaItemDefinition definition, String slot, List<MonumentaAttribute> attributes, List<MonumentaEnchantment> enchantments) {
		List<LoreLine> loreLines = new ArrayList<>();

		for (MonumentaEnchantment enchantment : sortDisplayEnchantments(enchantments)) {
			if (!enchantment.showInTooltip()) {
				continue;
			}
			loreLines.add(buildEnchantmentLore(enchantment));
		}

		String regionTier = buildRegionTierText(definition);
		if (!regionTier.isBlank()) {
			MutableComponent line = plain(regionTier + " : ", ChatFormatting.DARK_GRAY)
				.append(plain(blankToValue(definition.tier(), "Unknown"), getTierStyle(definition.tier()).accentColor()));
			loreLines.add(new LoreLine(line, regionTier + " : " + blankToValue(definition.tier(), "Unknown")));
		}

		if (!definition.masterwork().isBlank()) {
			String stars = buildMasterworkStars(definition.masterwork());
			MutableComponent line = plain("Masterwork : ", ChatFormatting.DARK_GRAY)
				.append(plain(stars, 0xFFB43E));
			loreLines.add(new LoreLine(line, "Masterwork :"));
		}

		String locationLine = buildLocationLine(definition);
		if (!locationLine.isBlank()) {
			loreLines.add(buildLocationLore(definition, locationLine));
		}

		List<String> description = definition.mmLore().isEmpty() ? definition.lore() : definition.mmLore();
		for (String line : description) {
			loreLines.add(wrappedLore(line, ChatFormatting.DARK_GRAY));
		}

		if (!attributes.isEmpty()) {
			loreLines.add(blankLore());
			if (slot != null) {
				loreLines.add(simpleLore(formatEquipLine(slot), ChatFormatting.GRAY));
			}
			for (MonumentaAttribute attribute : sortDisplayAttributes(attributes)) {
				loreLines.add(new LoreLine(buildAttributeLore(attribute), buildAttributePlainText(attribute)));
			}
		}

		return loreLines;
	}

	private static MutableComponent buildAttributeLore(MonumentaAttribute attribute) {
		if (attribute.useAbsoluteTooltipValue()) {
			return plain(" " + buildAttributePlainText(attribute), ChatFormatting.DARK_GREEN);
		}

		int color = attribute.vanillaAmount() < 0 ? NEGATIVE_COLOR : attribute.vanillaOperation() == 1 ? POSITIVE_MULTIPLY_COLOR : POSITIVE_ADD_COLOR;
		return plain(buildAttributePlainText(attribute), color);
	}

	private static String buildAttributePlainText(MonumentaAttribute attribute) {
		double rawValue = attribute.useAbsoluteTooltipValue() ? attribute.displayAmount() : attribute.stockAmount();
		if (attribute.useAbsoluteTooltipValue()) {
			return formatNumber(Math.abs(rawValue)) + " " + attribute.displayName();
		}

		String sign = rawValue >= 0 ? "+" : "-";
		double magnitude = Math.abs(rawValue);
		if (attribute.vanillaOperation() == 1) {
			return sign + formatNumber(magnitude) + "% " + attribute.displayName();
		}
		return sign + formatNumber(magnitude) + " " + attribute.displayName();
	}

	private static LoreLine buildEnchantmentLore(MonumentaEnchantment enchantment) {
		if ("alchemical_utensil".equals(enchantment.statKey())) {
			return simpleLore("* " + enchantment.displayName() + " *", ChatFormatting.DARK_GRAY);
		}
		return simpleLore(formatEnchantmentText(enchantment), ChatFormatting.GRAY);
	}

	private static LoreLine buildLocationLore(MonumentaItemDefinition definition, String locationLine) {
		if ("yellow".equals(normalizeTagValue(definition.location()))) {
			return simpleLore(locationLine, ChatFormatting.YELLOW);
		}
		return simpleLore(locationLine, resolveLocationColor(definition.location()));
	}

	private static String formatEnchantmentText(MonumentaEnchantment enchantment) {
		String displayName = enchantment.displayName();
		MonumentaStat stat = enchantment.stat();
		if (stat.isWholeNumber()) {
			int level = stat.intValue();
			if (level == 1 && omitLevelOneMarker(enchantment.statKey())) {
				return displayName;
			}
			if (level >= 1) {
				return displayName + " " + toRoman(level);
			}
		}

		if (enchantment.statKey().endsWith("_percent")) {
			return displayName + " " + formatSigned(stat.value()) + "%";
		}

		return displayName + " " + formatSigned(stat.value());
	}

	private static String buildRegionTierText(MonumentaItemDefinition definition) {
		String regionDisplay = getRegionDisplay(definition.region());
		if (!regionDisplay.isBlank()) {
			return regionDisplay;
		}
		return blankToValue(definition.location(), "");
	}

	private static String buildLocationLine(MonumentaItemDefinition definition) {
		String location = normalizeLocationDisplay(definition.location());
		if (location.isBlank()) {
			return getRegionDisplay(definition.region());
		}

		String regionDisplay = getRegionDisplay(definition.region());
		if (location.equals("Overworld") && !regionDisplay.isBlank()) {
			return regionDisplay + " " + location;
		}
		return LOCATION_DISPLAY_OVERRIDES.getOrDefault(location, location);
	}

	private static Component buildDisplayName(MonumentaItemDefinition definition, Item item, int nameColor) {
		if (isPotionItem(item)) {
			return Component.literal(definition.name()).withStyle(style -> style
				.withItalic(false)
				.withUnderlined(false)
				.withBold(true)
				.withColor(ChatFormatting.YELLOW));
		}

		return Component.literal(definition.name()).withStyle(style -> style
			.withItalic(false)
			.withUnderlined(false)
			.withBold(true)
			.withColor(TextColor.fromRgb(nameColor)));
	}

	private static String buildMmName(MonumentaItemDefinition definition, Item item, int nameColor) {
		if (isPotionItem(item)) {
			return "<!italic><!underlined><bold><yellow>" + definition.name();
		}
		return "<!italic><!underlined><bold><#" + toHexColor(nameColor) + ">" + definition.name();
	}

	private static List<MonumentaAttribute> collectAttributes(MonumentaItemDefinition definition, String slot) {
		List<MonumentaAttribute> attributes = new ArrayList<>();
		for (Map.Entry<String, MonumentaStat> entry : definition.stats().entrySet()) {
			MonumentaAttribute attribute = mapAttribute(entry.getKey(), entry.getValue(), slot);
			if (attribute != null) {
				attributes.add(attribute);
			}
		}
		return attributes;
	}

	private static List<MonumentaEnchantment> collectEnchantments(MonumentaItemDefinition definition) {
		List<MonumentaEnchantment> enchantments = new ArrayList<>();
		for (Map.Entry<String, MonumentaStat> entry : definition.stats().entrySet()) {
			if (mapAttribute(entry.getKey(), entry.getValue(), null) != null) {
				continue;
			}

			String vanillaEnchantmentId = null;
			ResourceLocation enchantmentId = new ResourceLocation("minecraft", entry.getKey());
			if (BuiltInRegistries.ENCHANTMENT.containsKey(enchantmentId)) {
				vanillaEnchantmentId = enchantmentId.toString();
			}

			enchantments.add(new MonumentaEnchantment(
				entry.getKey(),
				toTitleCase(entry.getKey()),
				toTitleCase(entry.getKey()),
				entry.getValue(),
				vanillaEnchantmentId,
				true
			));
		}
		return enchantments;
	}

	private static MonumentaAttribute mapAttribute(String statKey, MonumentaStat stat, String slot) {
		double value = stat.value();
		return switch (statKey) {
			case "speed_percent" -> new MonumentaAttribute(statKey, "Speed", "Speed", "minecraft:generic.movement_speed", 1, "multiply", value / 100.0D, value, value, slot, true, false, 50);
			case "attack_speed_percent" -> new MonumentaAttribute(statKey, "Attack Speed", "Attack Speed", "minecraft:generic.attack_speed", 1, "multiply", value / 100.0D, value, value, slot == null ? "mainhand" : slot, true, false, 95);
			case "attack_speed_base" -> new MonumentaAttribute(statKey, "Attack Speed", "Attack Speed", "minecraft:generic.attack_speed", 0, "add", value - 4.0D, value - 4.0D, value, slot == null ? "mainhand" : slot, true, true, 95);
			case "attack_damage_percent" -> new MonumentaAttribute(statKey, "Attack Damage", "Attack Damage", "minecraft:generic.attack_damage", 1, "multiply", value / 100.0D, value, value, slot == null ? "mainhand" : slot, true, false, 90);
			case "attack_damage_base" -> new MonumentaAttribute(statKey, "Attack Damage Add", "Attack Damage", "minecraft:generic.attack_damage", 0, "add", value - 1.0D, value - 1.0D, value, slot == null ? "mainhand" : slot, false, true, 90);
			case "potion_damage_flat" -> new MonumentaAttribute(statKey, "Potion Damage", "Potion Damage", null, 0, "add", 0.0D, value, value, slot == null ? "mainhand" : slot, false, true, 10);
			case "potion_radius_flat" -> new MonumentaAttribute(statKey, "Potion Radius", "Potion Radius", null, 0, "add", 0.0D, value, value, slot == null ? "mainhand" : slot, false, true, 20);
			case "potion_recharge_rate_percent" -> new MonumentaAttribute(statKey, "Potion Recharge Rate", "Potion Recharge Rate", null, 0, "multiply", 0.0D, value / 100.0D, value / 100.0D, slot == null ? "mainhand" : slot, false, true, 30);
			case "projectile_speed_base" -> new MonumentaAttribute(statKey, "Projectile Speed", "Projectile Speed", null, 0, "multiply", 0.0D, value, value, slot == null ? "mainhand" : slot, false, true, 40);
			case "armor" -> new MonumentaAttribute(statKey, "Armor", "Armor", "minecraft:generic.armor", 0, "add", value, value, value, slot, true, false, 50);
			case "armor_percent" -> new MonumentaAttribute(statKey, "Armor", "Armor", "minecraft:generic.armor", 1, "multiply", value / 100.0D, value, value, slot, true, false, 50);
			case "max_health_base" -> new MonumentaAttribute(statKey, "Max Health", "Max Health", "minecraft:generic.max_health", 0, "add", value, value, value, slot, true, false, 50);
			case "max_health_percent" -> new MonumentaAttribute(statKey, "Max Health", "Max Health", "minecraft:generic.max_health", 1, "multiply", value / 100.0D, value, value, slot, true, false, 50);
			case "knockback_resistance_percent" -> new MonumentaAttribute(statKey, "Knockback Resistance", "Knockback Resistance", "minecraft:generic.knockback_resistance", 1, "multiply", value / 100.0D, value, value, slot, true, false, 50);
			default -> null;
		};
	}

	private static String resolveSlot(MonumentaItemDefinition definition, Item item) {
		String normalizedType = normalizeTagValue(definition.type());
		if (normalizedType.contains("mainhand")) {
			return "mainhand";
		}
		if (normalizedType.contains("offhand")) {
			return "offhand";
		}

		String combined = (definition.type() + " " + definition.baseItemName() + " " + BuiltInRegistries.ITEM.getKey(item).getPath()).toLowerCase(Locale.ROOT);
		if (combined.contains("chestplate") || combined.contains("elytra") || combined.contains("chest")) {
			return "chest";
		}
		if (combined.contains("leggings") || combined.contains("pants") || combined.contains("legs")) {
			return "legs";
		}
		if (combined.contains("boots") || combined.contains("shoes") || combined.contains("feet")) {
			return "feet";
		}
		if (combined.contains("helmet") || combined.contains("head") || combined.contains("hat") || combined.contains("cap") || combined.contains("skull")) {
			return "head";
		}
		if (combined.contains("shield") || combined.contains("offhand")) {
			return "offhand";
		}
		if (combined.contains("sword") || combined.contains("axe") || combined.contains("bow") || combined.contains("crossbow")
			|| combined.contains("trident") || combined.contains("mace") || combined.contains("pickaxe") || combined.contains("shovel")
			|| combined.contains("hoe") || combined.contains("wand") || combined.contains("staff") || combined.contains("dagger")) {
			return "mainhand";
		}
		return null;
	}

	private static ListTag toDisplayLore(List<LoreLine> loreLines) {
		ListTag loreTag = new ListTag();
		for (LoreLine line : loreLines) {
			String json = line.plainText().isEmpty() ? "\"\"" : Component.Serializer.toJson(line.component());
			loreTag.add(StringTag.valueOf(json));
		}
		return loreTag;
	}

	private static ListTag toPlainLore(List<LoreLine> loreLines) {
		ListTag loreTag = new ListTag();
		for (LoreLine line : loreLines) {
			loreTag.add(StringTag.valueOf(line.plainText()));
		}
		return loreTag;
	}

	private static ListTag toStringList(List<String> values) {
		ListTag listTag = new ListTag();
		for (String value : values) {
			listTag.add(StringTag.valueOf(value));
		}
		return listTag;
	}

	private static void putNormalized(CompoundTag tag, String key, String value) {
		if (!value.isBlank()) {
			tag.putString(key, normalizeTagValueForTag(key, value));
		}
	}

	private static String normalizeTagValueForTag(String key, String value) {
		String normalized = normalizeTagValue(value);
		if ("Location".equals(key)) {
			return LOCATION_TAG_OVERRIDES.getOrDefault(normalized, normalized);
		}
		return normalized;
	}

	private static void putDirect(CompoundTag tag, String key, String value) {
		if (!value.isBlank()) {
			tag.putString(key, value);
		}
	}

	private static TierStyle getTierStyle(String tier) {
		return switch (normalizeTagValue(tier)) {
			case "artifact" -> new TierStyle(0xE3545D, 0xD02E28);
			case "rare" -> new TierStyle(0x33CCFF, 0x4AC2E5);
			case "epic" -> new TierStyle(0xC86CFF, 0xB14EFF);
			case "uncommon" -> new TierStyle(0x6FD77B, 0x54C763);
			case "unique" -> new TierStyle(0xFFD447, 0xFFB43E);
			case "currency", "event_currency" -> new TierStyle(0xFFB43E, 0xFFB43E);
			case "fish" -> new TierStyle(0x5BC0EB, 0x3FA7D6);
			case "legacy" -> new TierStyle(0xA0A0A0, 0x8A8A8A);
			case "trophy" -> new TierStyle(0xFFD447, 0xFFB43E);
			case "event" -> new TierStyle(0x6ED38E, 0x6ED38E);
			case "key" -> new TierStyle(0xEBCB8B, 0xD9B66C);
			case "obfuscated" -> new TierStyle(0xB8A3FF, 0xA382F7);
			default -> new TierStyle(0xF0F0F0, 0xF0F0F0);
		};
	}

	private static String getRegionDisplay(String region) {
		return switch (normalizeTagValue(region)) {
			case "ring" -> "Architect's Ring";
			case "valley" -> "King's Valley";
			case "isles" -> "Celsian Isles";
			default -> blankToValue(region, "");
		};
	}

	private static String normalizeLocationDisplay(String location) {
		if (location == null || location.isBlank()) {
			return "";
		}
		if (location.equalsIgnoreCase("Overworld3") || location.equalsIgnoreCase("Isles Overworld")) {
			return "Overworld";
		}
		return location;
	}

	private static int resolveLocationColor(String location) {
		String normalized = normalizeTagValue(location);
		if ("pelias_keep".equals(normalized)) {
			return SPECIAL_KEEP_LOCATION_COLOR;
		}
		return LOCATION_COLOR;
	}

	private static int resolveNameColor(Item item, String slot, TierStyle tierStyle) {
		if (isPotionItem(item)) {
			return DEFAULT_POTION_NAME_COLOR;
		}
		if (isMainhandLike(slot)) {
			return DEFAULT_MAINHAND_NAME_COLOR;
		}
		return tierStyle.nameColor();
	}

	private static int resolveHideFlags(Item item) {
		return isPotionItem(item) ? POTION_HIDE_FLAGS : DEFAULT_HIDE_FLAGS;
	}

	private static String blankToValue(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String normalizeTagValue(String value) {
		return normalizeBaseItemName(value);
	}

	private static String toTitleCase(String value) {
		String[] parts = value.split("_");
		StringBuilder builder = new StringBuilder(value.length());
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

	private static String buildMasterworkStars(String masterwork) {
		try {
			int level = Integer.parseInt(masterwork.trim());
			if (level <= 0) {
				return masterwork;
			}
			return "\u2605".repeat(Math.min(level, 7));
		} catch (NumberFormatException ignored) {
			return masterwork;
		}
	}

	private static String toRoman(int value) {
		int[] arabic = {10, 9, 5, 4, 1};
		String[] roman = {"X", "IX", "V", "IV", "I"};
		StringBuilder builder = new StringBuilder();
		int remaining = value;
		for (int index = 0; index < arabic.length; index++) {
			while (remaining >= arabic[index]) {
				builder.append(roman[index]);
				remaining -= arabic[index];
			}
		}
		return builder.toString();
	}

	private static String formatSigned(double value) {
		return (value > 0 ? "+" : "") + formatNumber(value);
	}

	private static String formatNumber(double value) {
		double rounded = Math.rint(value);
		if (rounded == value) {
			return Integer.toString((int) rounded);
		}
		String text = String.format(Locale.ROOT, "%.2f", value);
		text = text.replaceAll("0+$", "");
		text = text.replaceAll("\\.$", "");
		return text;
	}

	private static String capitalize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String formatEquipLine(String slot) {
		return switch (slot) {
			case "mainhand" -> "When in Main Hand:";
			case "offhand" -> "When in Off Hand:";
			default -> "When on " + capitalize(slot) + ":";
		};
	}

	private static boolean isMainhandLike(String slot) {
		return "mainhand".equals(slot) || "offhand".equals(slot);
	}

	private static boolean isPotionItem(Item item) {
		return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
	}

	private static boolean omitLevelOneMarker(String statKey) {
		return switch (statKey) {
			case "mending", "adaptability", "alchemical_utensil" -> true;
			default -> false;
		};
	}

	private static String toHexColor(int color) {
		return String.format(Locale.ROOT, "%06x", color);
	}

	private static LoreLine simpleLore(String text, int color) {
		return new LoreLine(plain(text, color), text);
	}

	private static LoreLine simpleLore(String text, ChatFormatting formatting) {
		return new LoreLine(plain(text, formatting), text);
	}

	private static LoreLine wrappedLore(String text, ChatFormatting formatting) {
		MutableComponent line = Component.literal("").withStyle(style -> style.withItalic(false).withColor(formatting)).append(Component.literal(text));
		return new LoreLine(line, text);
	}

	private static LoreLine blankLore() {
		return new LoreLine(Component.empty(), "");
	}

	private static MutableComponent plain(String text, int color) {
		return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(TextColor.fromRgb(color)));
	}

	private static MutableComponent plain(String text, ChatFormatting formatting) {
		return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(formatting));
	}

	private static Item resolveBaseItem(String baseItemName) {
		ResourceLocation itemId = resolveBaseItemId(baseItemName);
		if (itemId != null) {
			Item resolved = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
			if (resolved != null && resolved != Items.AIR) {
				return resolved;
			}
		}

		if (!baseItemName.isBlank()) {
			NeverEnoughMonumenta.LOGGER.warn("Unable to resolve Monumenta base item '{}', falling back to paper", baseItemName);
		}
		return Items.PAPER;
	}

	private static ResourceLocation resolveBaseItemId(String baseItemName) {
		if (baseItemName == null || baseItemName.isBlank()) {
			return null;
		}

		ResourceLocation direct = ResourceLocation.tryParse(baseItemName);
		if (direct != null) {
			return direct;
		}

		String normalized = normalizeBaseItemName(baseItemName);
		String specialPath = SPECIAL_CASE_PATHS.get(normalized);
		if (specialPath != null) {
			return new ResourceLocation("minecraft", specialPath);
		}

		return normalized.isBlank() ? null : new ResourceLocation("minecraft", normalized);
	}

	private static String normalizeBaseItemName(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		normalized = normalized.replaceAll("\\p{M}+", "");
		normalized = normalized.toLowerCase(Locale.ROOT);
		normalized = normalized.replaceAll("[^a-z0-9]+", "_");
		normalized = normalized.replaceAll("_+", "_");
		normalized = normalized.replaceAll("^_", "");
		normalized = normalized.replaceAll("_$", "");
		return normalized;
	}

	private static Map<String, String> createSpecialCasePaths() {
		Map<String, String> specialPaths = new LinkedHashMap<>();
		specialPaths.put("nether_quartz", "quartz");
		specialPaths.put("redstone_dust", "redstone");
		specialPaths.put("bottle_o_enchanting", "experience_bottle");
		return specialPaths;
	}

	private static Map<String, String> createLocationTagOverrides() {
		Map<String, String> overrides = new LinkedHashMap<>();
		overrides.put("pelias_keep", "keep");
		return overrides;
	}

	private static Map<String, String> createLocationDisplayOverrides() {
		Map<String, String> overrides = new LinkedHashMap<>();
		overrides.put("Yellow", "Vernal Nightmare");
		return overrides;
	}

	private static List<MonumentaAttribute> sortDisplayAttributes(List<MonumentaAttribute> attributes) {
		return attributes.stream()
			.sorted(Comparator.comparingInt(MonumentaAttribute::displayPriority))
			.toList();
	}

	private static List<MonumentaEnchantment> sortDisplayEnchantments(List<MonumentaEnchantment> enchantments) {
		return enchantments.stream()
			.sorted(Comparator
				.comparingInt(MonumentaStackFactory::displayEnchantmentPriority)
				.thenComparing(MonumentaEnchantment::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static List<MonumentaEnchantment> sortStockEnchantments(List<MonumentaEnchantment> enchantments) {
		return enchantments.stream()
			.sorted(Comparator
				.comparingInt(MonumentaStackFactory::stockEnchantmentPriority)
				.thenComparing(MonumentaEnchantment::stockName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static int displayEnchantmentPriority(MonumentaEnchantment enchantment) {
		return switch (enchantment.statKey()) {
			case "alchemical_utensil" -> 80;
			case "unbreaking" -> 90;
			case "mending" -> 100;
			default -> 10;
		};
	}

	private static int stockEnchantmentPriority(MonumentaEnchantment enchantment) {
		return switch (enchantment.statKey()) {
			case "mending" -> 20;
			case "unbreaking" -> 50;
			default -> 30;
		};
	}

	private record TierStyle(int nameColor, int accentColor) {
	}

	private record MonumentaAttribute(
		String statKey,
		String stockName,
		String displayName,
		String vanillaAttributeId,
		int vanillaOperation,
		String stockOperation,
		double vanillaAmount,
		double stockAmount,
		double displayAmount,
		String slot,
		boolean writeVanillaModifier,
		boolean useAbsoluteTooltipValue,
		int displayPriority
	) {
	}

	private record MonumentaEnchantment(String statKey, String displayName, String stockName, MonumentaStat stat, String vanillaEnchantmentId, boolean showInTooltip) {
	}

	private record LoreLine(Component component, String plainText) {
	}
}