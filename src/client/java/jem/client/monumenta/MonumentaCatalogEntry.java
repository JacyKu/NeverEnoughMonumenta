package jem.client.monumenta;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;

public record MonumentaCatalogEntry(
	String logicalKey,
	MonumentaItemDefinition definition,
	List<MonumentaItemDefinition> variants,
	List<ItemStack> variantStacks
) {
	public MonumentaCatalogEntry {
		variants = List.copyOf(variants);
		variantStacks = variantStacks.stream().map(ItemStack::copy).toList();
		if (variants.isEmpty() || variants.size() != variantStacks.size()) {
			throw new IllegalArgumentException("Monumenta catalog entries must have matching variant definitions and stacks");
		}
	}

	public boolean isCycling() {
		return variantStacks.size() > 1;
	}

	public int variantCount() {
		return variantStacks.size();
	}

	public MonumentaItemDefinition currentDefinition() {
		return variants.get(MonumentaMasterworkSelection.selectedVariantIndex(this));
	}

	public ItemStack copyCurrentStack() {
		return variantStacks.get(MonumentaMasterworkSelection.selectedVariantIndex(this)).copy();
	}

	public ItemStack copyRepresentativeStack() {
		return variantStacks.get(0).copy();
	}

	public List<ItemStack> copyVariantStacks() {
		return variantStacks.stream().map(ItemStack::copy).toList();
	}

	public List<String> aliases() {
		LinkedHashSet<String> aliases = new LinkedHashSet<>();
		for (MonumentaItemDefinition variant : variants) {
			aliases.addAll(variant.aliases());
		}
		return List.copyOf(aliases);
	}

	public boolean matchesVariantSubtype(String subtypeKey) {
		if (subtypeKey == null || subtypeKey.isBlank()) {
			return false;
		}
		for (ItemStack variantStack : variantStacks) {
			if (subtypeKey.equals(MonumentaStackFactory.getSubtypeKey(variantStack))) {
				return true;
			}
		}
		return false;
	}
}