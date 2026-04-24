package jem.client.integration.jei;

import jem.NeverEnoughMonumenta;
import jem.client.MonumentaItemVisibility;
import jem.client.config.NeverEnoughMonumentaConfigManager;
import jem.client.monumenta.MonumentaCatalog;
import jem.client.monumenta.MonumentaCatalogEntry;
import jem.client.monumenta.MonumentaItemRepository;
import jem.client.monumenta.MonumentaStackFactory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModInfoRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JeiPlugin
public class NeverEnoughMonumentaJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_ID = new ResourceLocation(NeverEnoughMonumenta.MOD_ID, "jei");
	private static final IIngredientType<MonumentaCatalogEntry> CYCLING_MASTERWORK_TYPE = () -> MonumentaCatalogEntry.class;
	private static final MonumentaEntryHelper CYCLING_MASTERWORK_HELPER = new MonumentaEntryHelper();
	private static final MonumentaEntryRenderer CYCLING_MASTERWORK_RENDERER = new MonumentaEntryRenderer();
	private static volatile IJeiRuntime runtime;
	private static volatile List<ItemStack> removedVanillaStacks = List.of();
	private static volatile Map<IIngredientType<?>, List<Object>> removedVanillaIngredients = Map.of();
	private static volatile boolean singleVariantStacksVisible;
	private static volatile boolean cyclingEntriesVisible;

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_ID;
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		MonumentaCatalog catalog = MonumentaItemRepository.getCatalog();
		List<MonumentaCatalogEntry> customEntries = customIngredientEntries(catalog);
		if (!customEntries.isEmpty()) {
			registration.register(CYCLING_MASTERWORK_TYPE, customEntries, CYCLING_MASTERWORK_HELPER, CYCLING_MASTERWORK_RENDERER);
		}
	}

	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		for (Item item : MonumentaItemRepository.getCatalog().baseItems()) {
			if (hasBuiltInSubtypeInterpreter(item)) {
				continue;
			}
			registration.registerSubtypeInterpreter(item, (stack, context) -> MonumentaStackFactory.getSubtypeKey(stack));
		}
	}

	private static List<MonumentaCatalogEntry> customIngredientEntries(MonumentaCatalog catalog) {
		return catalog.entries().stream()
			.filter(entry -> entry.isCycling() || hasBuiltInSubtypeInterpreter(entry.copyRepresentativeStack().getItem()))
			.toList();
	}

	private static List<ItemStack> runtimeAddedItemStacks(MonumentaCatalog catalog) {
		return catalog.singleVariantEntries().stream()
			.filter(entry -> !hasBuiltInSubtypeInterpreter(entry.copyRepresentativeStack().getItem()))
			.map(MonumentaCatalogEntry::copyRepresentativeStack)
			.toList();
	}

	private static boolean hasBuiltInSubtypeInterpreter(Item item) {
		return item == Items.ENCHANTED_BOOK
			|| item == Items.POTION
			|| item == Items.SPLASH_POTION
			|| item == Items.LINGERING_POTION
			|| item == Items.TIPPED_ARROW;
	}

	@Override
	public void registerModInfo(IModInfoRegistration registration) {
		registration.addModAliases(NeverEnoughMonumenta.MOD_ID, List.of("Monumenta", "Monumenta Items"));
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		MonumentaCatalog catalog = MonumentaItemRepository.getCatalog();
		List<MonumentaCatalogEntry> customEntries = customIngredientEntries(catalog);
		List<ItemStack> itemStacks = runtimeAddedItemStacks(catalog);
		removedVanillaStacks = List.of();
		removedVanillaIngredients = Map.of();
		singleVariantStacksVisible = false;
		cyclingEntriesVisible = !customEntries.isEmpty();
		if (!itemStacks.isEmpty()) {
			jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, itemStacks);
			singleVariantStacksVisible = true;
		}
		refreshRuntime();
	}

	public static void refreshRuntime() {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return;
		}

		MonumentaCatalog catalog = MonumentaItemRepository.getCatalog();
		List<MonumentaCatalogEntry> customEntries = customIngredientEntries(catalog);
		List<ItemStack> itemStacks = runtimeAddedItemStacks(catalog);
		var ingredientManager = jeiRuntime.getIngredientManager();
		if (NeverEnoughMonumentaConfigManager.hideCustomMonumentaEntries()) {
			if (singleVariantStacksVisible && !itemStacks.isEmpty()) {
				ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, itemStacks);
				singleVariantStacksVisible = false;
			}
			if (cyclingEntriesVisible && !customEntries.isEmpty()) {
				ingredientManager.removeIngredientsAtRuntime(CYCLING_MASTERWORK_TYPE, customEntries);
				cyclingEntriesVisible = false;
			}
		} else {
			if (!singleVariantStacksVisible && !itemStacks.isEmpty()) {
				ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, itemStacks);
				singleVariantStacksVisible = true;
			}
			if (!cyclingEntriesVisible && !customEntries.isEmpty()) {
				ingredientManager.addIngredientsAtRuntime(CYCLING_MASTERWORK_TYPE, customEntries);
				cyclingEntriesVisible = true;
			}
		}

		if (NeverEnoughMonumentaConfigManager.hideVanillaItemStacks()) {
			if (removedVanillaStacks.isEmpty()) {
				List<ItemStack> stacksToRemove = ingredientManager.getAllItemStacks()
					.stream()
					.filter(MonumentaItemVisibility::isVanillaMinecraftStack)
					.map(ItemStack::copy)
					.toList();
				if (!stacksToRemove.isEmpty()) {
					removedVanillaStacks = stacksToRemove;
					ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacksToRemove);
				}
			}
			if (removedVanillaIngredients.isEmpty()) {
				Map<IIngredientType<?>, List<Object>> ingredientsToRemove = collectVanillaNonItemIngredients(ingredientManager);
				if (!ingredientsToRemove.isEmpty()) {
					removedVanillaIngredients = ingredientsToRemove;
					removeIngredientsAtRuntime(ingredientManager, ingredientsToRemove);
				}
			}
		} else if (!removedVanillaStacks.isEmpty()) {
			ingredientManager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removedVanillaStacks);
			removedVanillaStacks = List.of();
			if (!removedVanillaIngredients.isEmpty()) {
				addIngredientsAtRuntime(ingredientManager, removedVanillaIngredients);
				removedVanillaIngredients = Map.of();
			}
		} else if (!removedVanillaIngredients.isEmpty()) {
			addIngredientsAtRuntime(ingredientManager, removedVanillaIngredients);
			removedVanillaIngredients = Map.of();
		}

		refreshVisibleIngredientList(jeiRuntime);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Map<IIngredientType<?>, List<Object>> collectVanillaNonItemIngredients(mezz.jei.api.runtime.IIngredientManager ingredientManager) {
		Map<IIngredientType<?>, List<Object>> ingredientsByType = new LinkedHashMap<>();
		for (IIngredientType<?> ingredientType : ingredientManager.getRegisteredIngredientTypes()) {
			if (ingredientType == VanillaTypes.ITEM_STACK || ingredientType == CYCLING_MASTERWORK_TYPE) {
				continue;
			}

			IIngredientHelper helper = ingredientManager.getIngredientHelper((IIngredientType) ingredientType);
			List<Object> ingredients = ((Collection<?>) ingredientManager.getAllIngredients((IIngredientType) ingredientType))
				.stream()
				.filter(ingredient -> isVanillaNonItemIngredient(helper, ingredient))
				.map(Object.class::cast)
				.toList();
			if (!ingredients.isEmpty()) {
				ingredientsByType.put(ingredientType, ingredients);
			}
		}
		return ingredientsByType;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void removeIngredientsAtRuntime(mezz.jei.api.runtime.IIngredientManager ingredientManager, Map<IIngredientType<?>, List<Object>> ingredientsByType) {
		for (Map.Entry<IIngredientType<?>, List<Object>> entry : ingredientsByType.entrySet()) {
			ingredientManager.removeIngredientsAtRuntime((IIngredientType) entry.getKey(), (Collection) entry.getValue());
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addIngredientsAtRuntime(mezz.jei.api.runtime.IIngredientManager ingredientManager, Map<IIngredientType<?>, List<Object>> ingredientsByType) {
		for (Map.Entry<IIngredientType<?>, List<Object>> entry : ingredientsByType.entrySet()) {
			ingredientManager.addIngredientsAtRuntime((IIngredientType) entry.getKey(), (Collection) entry.getValue());
		}
	}

	@SuppressWarnings("rawtypes")
	private static boolean isVanillaNonItemIngredient(IIngredientHelper helper, Object ingredient) {
		if (ingredient == null) {
			return false;
		}

		ResourceLocation resourceLocation = helper.getResourceLocation(ingredient);
		if (resourceLocation == null || !"minecraft".equals(resourceLocation.getNamespace())) {
			return false;
		}

		String displayModId = helper.getDisplayModId(ingredient);
		return displayModId == null || "minecraft".equalsIgnoreCase(displayModId);
	}

	private static void refreshVisibleIngredientList(IJeiRuntime jeiRuntime) {
		Object ingredientFilter = unwrapField(jeiRuntime.getIngredientFilter(), "ingredientFilter");
		invokeMethod(ingredientFilter, "rebuildItemFilter");
		invokeMethod(ingredientFilter, "notifyListenersOfChange");
	}

	private static Object unwrapField(Object target, String fieldName) {
		if (target == null) {
			return null;
		}

		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(target);
			if (value != null) {
				return value;
			}
		} catch (ReflectiveOperationException | LinkageError ignored) {
		}

		return target;
	}

	private static void invokeMethod(Object target, String methodName) {
		if (target == null) {
			return;
		}

		try {
			java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName);
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException | LinkageError ignored) {
		}
	}

	public static MonumentaCatalogEntry getHoveredMasterworkEntry() {
		IJeiRuntime jeiRuntime = runtime;
		if (jeiRuntime == null) {
			return null;
		}
		return jeiRuntime.getIngredientListOverlay().getIngredientUnderMouse(CYCLING_MASTERWORK_TYPE);
	}

	private static final class MonumentaEntryHelper implements IIngredientHelper<MonumentaCatalogEntry> {
		@Override
		public IIngredientType<MonumentaCatalogEntry> getIngredientType() {
			return CYCLING_MASTERWORK_TYPE;
		}

		@Override
		public String getDisplayName(MonumentaCatalogEntry ingredient) {
			return ingredient.currentDefinition().name();
		}

		@Override
		public String getUniqueId(MonumentaCatalogEntry ingredient, mezz.jei.api.ingredients.subtypes.UidContext context) {
			return ingredient.logicalKey();
		}

		@Override
		public String getDisplayModId(MonumentaCatalogEntry ingredient) {
			return NeverEnoughMonumenta.MOD_ID;
		}

		@Override
		public Iterable<Integer> getColors(MonumentaCatalogEntry ingredient) {
			return Collections.emptyList();
		}

		@Override
		public ResourceLocation getResourceLocation(MonumentaCatalogEntry ingredient) {
			return BuiltInRegistries.ITEM.getKey(ingredient.copyRepresentativeStack().getItem());
		}

		@Override
		public ItemStack getCheatItemStack(MonumentaCatalogEntry ingredient) {
			return ingredient.copyCurrentStack();
		}

		@Override
		public MonumentaCatalogEntry copyIngredient(MonumentaCatalogEntry ingredient) {
			return ingredient;
		}

		@Override
		public boolean isValidIngredient(MonumentaCatalogEntry ingredient) {
			return ingredient != null && ingredient.variantCount() > 0;
		}

		@Override
		public String getErrorInfo(@Nullable MonumentaCatalogEntry ingredient) {
			if (ingredient == null) {
				return "null Monumenta masterwork entry";
			}
			return ingredient.logicalKey();
		}
	}

	private static final class MonumentaEntryRenderer implements IIngredientRenderer<MonumentaCatalogEntry> {
		@Override
		public void render(GuiGraphics guiGraphics, MonumentaCatalogEntry ingredient) {
			ItemStack stack = ingredient.copyCurrentStack();
			guiGraphics.renderItem(stack, 0, 0);
			guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
		}

		@Override
		public List<Component> getTooltip(MonumentaCatalogEntry ingredient, TooltipFlag tooltipFlag) {
			return ingredient.copyCurrentStack().getTooltipLines(Minecraft.getInstance().player, tooltipFlag);
		}

		@Override
		public Font getFontRenderer(Minecraft minecraft, MonumentaCatalogEntry ingredient) {
			return minecraft.font;
		}
	}
}