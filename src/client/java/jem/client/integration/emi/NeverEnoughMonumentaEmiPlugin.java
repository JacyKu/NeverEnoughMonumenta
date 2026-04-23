package jem.client.integration.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import jem.NeverEnoughMonumenta;
import jem.client.monumenta.MonumentaCatalogEntry;
import jem.client.monumenta.MonumentaItemRepository;
import jem.client.monumenta.MonumentaStackFactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public class NeverEnoughMonumentaEmiPlugin implements EmiPlugin {
	@Override
	public void register(EmiRegistry registry) {
		for (Item item : MonumentaItemRepository.getCatalog().baseItems()) {
			registry.setDefaultComparison(item, Comparison.compareData(stack -> {
				ItemStack itemStack = stack.getItemStack();
				String monumentaKey = MonumentaStackFactory.getSubtypeKey(itemStack);
				if (monumentaKey != null) {
					return monumentaKey;
				}
				return itemStack.getTag();
			}));
		}

		for (MonumentaCatalogEntry entry : MonumentaItemRepository.getCatalog().entries()) {
			EmiStack emiStack = entry.isCycling()
				? new CyclingMonumentaEmiStack(entry)
				: EmiStack.of(entry.copyRepresentativeStack());
			registry.addEmiStack(emiStack);
			for (String alias : entry.aliases()) {
				registry.addAlias(emiStack, Component.literal(alias));
			}
		}
	}

	public static MonumentaCatalogEntry getHoveredMasterworkEntry() {
		EmiStackInteraction hovered = EmiApi.getHoveredStack(false);
		if (hovered.isEmpty()) {
			return null;
		}

		EmiIngredient stack = hovered.getStack();
		if (stack instanceof CyclingMonumentaEmiStack cyclingStack) {
			return cyclingStack.entry;
		}
		return null;
	}

	private static final class CyclingMonumentaEmiStack extends EmiStack {
		private final MonumentaCatalogEntry entry;
		private final ResourceLocation id;

		private CyclingMonumentaEmiStack(MonumentaCatalogEntry entry) {
			this.entry = entry;
			this.id = new ResourceLocation(NeverEnoughMonumenta.MOD_ID, "masterwork/" + sanitize(entry.logicalKey()));
		}

		@Override
		public EmiStack copy() {
			CyclingMonumentaEmiStack stack = new CyclingMonumentaEmiStack(entry);
			stack.setAmount(getAmount());
			stack.setChance(getChance());
			stack.setRemainder(getRemainder().copy());
			stack.comparison = comparison;
			return stack;
		}

		@Override
		public boolean isEmpty() {
			return entry.variantCount() == 0;
		}

		@Override
		public CompoundTag getNbt() {
			return current().getNbt();
		}

		@Override
		public Object getKey() {
			return entry.logicalKey();
		}

		@Override
		public ResourceLocation getId() {
			return id;
		}

		@Override
		public ItemStack getItemStack() {
			ItemStack stack = entry.copyCurrentStack();
			stack.setCount((int) Math.max(1L, Math.min(Integer.MAX_VALUE, getAmount())));
			return stack;
		}

		@Override
		public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
			current().copy().setAmount(getAmount()).setChance(getChance()).render(draw, x, y, delta, flags);
		}

		@Override
		public List<Component> getTooltipText() {
			return current().getTooltipText();
		}

		@Override
		public List<ClientTooltipComponent> getTooltip() {
			return current().copy().setAmount(getAmount()).setChance(getChance()).getTooltip();
		}

		@Override
		public Component getName() {
			return current().getName();
		}

		private EmiStack current() {
			return EmiStack.of(entry.copyCurrentStack());
		}

		private static String sanitize(String value) {
			return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]+", "_");
		}
	}
}