package jem.client.integration.rei;

import jem.NeverEnoughMonumenta;
import jem.client.MonumentaItemVisibility;
import jem.client.config.NeverEnoughMonumentaConfigManager;
import jem.client.monumenta.MonumentaCatalogEntry;
import jem.client.monumenta.MonumentaMasterworkHoverState;
import jem.client.monumenta.MonumentaItemRepository;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.TooltipContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.EntrySerializer;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.type.EntryDefinition;
import me.shedaniel.rei.api.common.entry.type.EntryType;
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class NeverEnoughMonumentaReiPlugin implements REIClientPlugin {
	private static final EntryType<MonumentaCatalogEntry> MASTERWORK_ENTRY_TYPE = EntryType.deferred(
		new ResourceLocation(NeverEnoughMonumenta.MOD_ID, "rei_masterwork")
	);
	private static final MasterworkEntryDefinition MASTERWORK_ENTRY_DEFINITION = new MasterworkEntryDefinition();
	private static final List<EntryStack<?>> removedVanillaEntries = new ArrayList<>();
	private static boolean customEntriesVisible;

	@Override
	public void registerEntryTypes(EntryTypeRegistry registry) {
		registry.register(MASTERWORK_ENTRY_TYPE, MASTERWORK_ENTRY_DEFINITION);
	}

	@Override
	public void registerEntries(EntryRegistry registry) {
		registry.addEntries(customEntries());
		customEntriesVisible = !MonumentaItemRepository.getCatalog().entries().isEmpty();
		refresh(registry);
	}

	public static void refreshRuntime() {
		refresh(EntryRegistry.getInstance());
	}

	public static MonumentaCatalogEntry getHoveredMasterworkEntry(Screen screen, double mouseX, double mouseY) {
		EntryStack<?> focusedStack = ScreenRegistry.getInstance().getFocusedStack(screen, new Point((int) mouseX, (int) mouseY));
		if (focusedStack == null || focusedStack.isEmpty()) {
			return null;
		}

		Object value = focusedStack.getValue();
		if (value instanceof MonumentaCatalogEntry entry) {
			return entry;
		}
		if (!(value instanceof ItemStack itemStack)) {
			return null;
		}
		return MonumentaItemRepository.findCyclingEntry(itemStack);
	}

	private static EntryStack<?> toEntryStack(MonumentaCatalogEntry entry) {
		if (!entry.isCycling()) {
			return EntryStacks.of(entry.copyRepresentativeStack());
		}
		return EntryStack.of(MASTERWORK_ENTRY_TYPE, entry);
	}

	private static void refresh(EntryRegistry registry) {
		if (registry == null) {
			return;
		}

		if (NeverEnoughMonumentaConfigManager.hideCustomMonumentaEntries()) {
			if (customEntriesVisible) {
				registry.removeEntryIf(NeverEnoughMonumentaReiPlugin::isCustomEntry);
				customEntriesVisible = false;
			}
		} else if (!customEntriesVisible) {
			List<EntryStack<?>> entries = customEntries();
			if (!entries.isEmpty()) {
				registry.addEntries(entries);
				customEntriesVisible = true;
			}
		}

		if (NeverEnoughMonumentaConfigManager.hideVanillaItemStacks()) {
			if (removedVanillaEntries.isEmpty()) {
				List<EntryStack<?>> entries = matchingEntries(registry, NeverEnoughMonumentaReiPlugin::isVanillaBaseEntry);
				if (!entries.isEmpty()) {
					removedVanillaEntries.addAll(entries);
					registry.removeEntryIf(NeverEnoughMonumentaReiPlugin::isVanillaBaseEntry);
				}
			}
		} else if (!removedVanillaEntries.isEmpty()) {
			registry.addEntries(removedVanillaEntries.stream().map(EntryStack::copy).toList());
			removedVanillaEntries.clear();
		}

		registry.refilter();
	}

	private static List<EntryStack<?>> customEntries() {
		return MonumentaItemRepository.getCatalog().entries().stream().map(NeverEnoughMonumentaReiPlugin::toEntryStack).toList();
	}

	private static List<EntryStack<?>> matchingEntries(EntryRegistry registry, java.util.function.Predicate<EntryStack<?>> predicate) {
		List<EntryStack<?>> entries = new ArrayList<>();
		registry.getEntryStacks()
			.filter(predicate)
			.forEach(entry -> entries.add(entry.copy()));
		return entries;
	}

	private static boolean isCustomEntry(EntryStack<?> entry) {
		Object value = entry.getValue();
		if (value instanceof MonumentaCatalogEntry monumentaEntry) {
			return MonumentaItemVisibility.shouldHideEntry(monumentaEntry);
		}
		return value instanceof ItemStack itemStack && MonumentaItemVisibility.isMonumentaStack(itemStack);
	}

	private static boolean isVanillaBaseEntry(EntryStack<?> entry) {
		Object value = entry.getValue();
		if (value instanceof ItemStack itemStack) {
			return MonumentaItemVisibility.isVanillaMinecraftStack(itemStack);
		}
		return "minecraft".equals(entry.getContainingNamespace()) && !isCustomEntry(entry);
	}

	private static final class MasterworkEntryDefinition implements EntryDefinition<MonumentaCatalogEntry> {
		private static final MasterworkEntryRenderer RENDERER = new MasterworkEntryRenderer();

		@Override
		public Class<MonumentaCatalogEntry> getValueType() {
			return MonumentaCatalogEntry.class;
		}

		@Override
		public EntryType<MonumentaCatalogEntry> getType() {
			return MASTERWORK_ENTRY_TYPE;
		}

		@Override
		public EntryRenderer<MonumentaCatalogEntry> getRenderer() {
			return RENDERER;
		}

		@Override
		public @Nullable ResourceLocation getIdentifier(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return new ResourceLocation(NeverEnoughMonumenta.MOD_ID, "masterwork/" + sanitize(value.logicalKey()));
		}

		@Override
		public boolean isEmpty(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value == null || value.variantCount() == 0;
		}

		@Override
		public MonumentaCatalogEntry copy(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value;
		}

		@Override
		public MonumentaCatalogEntry normalize(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value;
		}

		@Override
		public MonumentaCatalogEntry wildcard(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value;
		}

		@Override
		public @Nullable ItemStack cheatsAs(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value.copyCurrentStack();
		}

		@Override
		public long hash(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value, ComparisonContext context) {
			return value.logicalKey().hashCode();
		}

		@Override
		public boolean equals(MonumentaCatalogEntry o1, MonumentaCatalogEntry o2, ComparisonContext context) {
			return o1.logicalKey().equals(o2.logicalKey());
		}

		@Override
		public @Nullable EntrySerializer<MonumentaCatalogEntry> getSerializer() {
			return null;
		}

		@Override
		public Component asFormattedText(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return value.copyCurrentStack().getHoverName();
		}

		@Override
		public Stream<? extends TagKey<?>> getTagsFor(EntryStack<MonumentaCatalogEntry> entry, MonumentaCatalogEntry value) {
			return Stream.empty();
		}

		private static String sanitize(String value) {
			return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]+", "_");
		}
	}

	private static final class MasterworkEntryRenderer implements EntryRenderer<MonumentaCatalogEntry> {
		@Override
		public void render(EntryStack<MonumentaCatalogEntry> ignored, GuiGraphics graphics, Rectangle bounds, int mouseX, int mouseY, float delta) {
			MonumentaCatalogEntry entry = ignored.getValue();
			if (bounds.contains(mouseX, mouseY)) {
				MonumentaMasterworkHoverState.markHovered(entry, mouseX, mouseY);
			}
			EntryStack<ItemStack> current = EntryStacks.of(entry.copyCurrentStack());
			current.getRenderer().render(current, graphics, bounds, mouseX, mouseY, delta);
		}

		@Override
		public Tooltip getTooltip(EntryStack<MonumentaCatalogEntry> ignored, TooltipContext context) {
			MonumentaCatalogEntry entry = ignored.getValue();
			Point point = context.getPoint();
			MonumentaMasterworkHoverState.markHovered(entry, point.x, point.y);
			EntryStack<ItemStack> current = EntryStacks.of(entry.copyCurrentStack());
			return current.getRenderer().getTooltip(current, context);
		}
	}
}