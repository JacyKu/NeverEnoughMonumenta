package jem.client.monumenta;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public record MonumentaCatalog(List<MonumentaCatalogEntry> entries, Set<Item> baseItems) {
	public static final MonumentaCatalog EMPTY = new MonumentaCatalog(List.of(), Set.of());

	public MonumentaCatalog {
		entries = List.copyOf(entries);
		baseItems = Set.copyOf(baseItems);
	}

	public List<MonumentaCatalogEntry> singleVariantEntries() {
		return entries.stream().filter(entry -> !entry.isCycling()).toList();
	}

	public List<MonumentaCatalogEntry> cyclingEntries() {
		return entries.stream().filter(MonumentaCatalogEntry::isCycling).toList();
	}

	public List<ItemStack> copySingleVariantStacks() {
		return singleVariantEntries().stream().map(MonumentaCatalogEntry::copyRepresentativeStack).toList();
	}

	public List<ItemStack> copyStacks() {
		return entries.stream().map(MonumentaCatalogEntry::copyCurrentStack).toList();
	}
}