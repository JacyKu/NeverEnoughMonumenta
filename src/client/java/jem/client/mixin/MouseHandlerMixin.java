package jem.client.mixin;

import jem.client.MonumentaMasterworkScrollHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void NeverEnoughMonumenta$handleMasterworkScroll(long windowPointer, double horizontalAmount, double verticalAmount, CallbackInfo callbackInfo) {
		if (windowPointer != this.minecraft.getWindow().getWindow()) {
			return;
		}
		if (this.minecraft.getOverlay() != null) {
			return;
		}

		Screen screen = this.minecraft.screen;
		if (screen == null) {
			return;
		}

		double mouseX = this.xpos * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
		double mouseY = this.ypos * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();
		if (!MonumentaMasterworkScrollHandler.handleMouseScroll(screen, mouseX, mouseY, horizontalAmount, verticalAmount)) {
			return;
		}

		screen.afterMouseAction();
		callbackInfo.cancel();
	}
}