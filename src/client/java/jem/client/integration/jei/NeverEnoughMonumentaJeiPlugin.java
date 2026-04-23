package jem.client.integration.jei;

import jem.NeverEnoughMonumenta;
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
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@JeiPlugin
public class NeverEnoughMonumentaJeiPlugin implements IModPlugin {
	private static final ResourceLocation PLUGIN_ID = new ResourceLocation(NeverEnoughMonumenta.MOD_ID, "jei");
	private static final IIngredientType<MonumentaCatalogEntry> CYCLING_MASTERWORK_TYPE = () -> MonumentaCatalogEntry.class;
	private static final MonumentaEntryHelper CYCLING_MASTERWORK_HELPER = new MonumentaEntryHelper();
	private static final MonumentaEntryRenderer CYCLING_MASTERWORK_RENDERER = new MonumentaEntryRenderer();
	private static volatile IJeiRuntime runtime;

	@Override
	public ResourceLocation getPluginUid() {
		return PLUGIN_ID;
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		MonumentaCatalog catalog = MonumentaItemRepository.getCatalog();
		if (!catalog.cyclingEntries().isEmpty()) {
			registration.register(CYCLING_MASTERWORK_TYPE, catalog.cyclingEntries(), CYCLING_MASTERWORK_HELPER, CYCLING_MASTERWORK_RENDERER);
		}
	}

	@Override
	public void registerItemSubtypes(ISubtypeRegistration registration) {
		for (Item item : MonumentaItemRepository.getCatalog().baseItems()) {
			registration.registerSubtypeInterpreter(item, (stack, context) -> MonumentaStackFactory.getSubtypeKey(stack));
		}
	}

	@Override
	public void registerModInfo(IModInfoRegistration registration) {
		registration.addModAliases(NeverEnoughMonumenta.MOD_ID, List.of("Monumenta", "Monumenta Items"));
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		MonumentaCatalog catalog = MonumentaItemRepository.getCatalog();
		if (!catalog.singleVariantEntries().isEmpty()) {
			jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, catalog.copySingleVariantStacks());
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