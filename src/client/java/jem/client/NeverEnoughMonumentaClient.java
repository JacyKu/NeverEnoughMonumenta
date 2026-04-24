package jem.client;

import jem.NeverEnoughMonumenta;
import jem.client.config.NeverEnoughMonumentaConfigManager;
import jem.client.monumenta.MonumentaItemRepository;
import net.fabricmc.api.ClientModInitializer;

public class NeverEnoughMonumentaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NeverEnoughMonumentaConfigManager.initialize();
		NeverEnoughMonumentaConfigManager.registerChangeListener(MonumentaViewerRefreshHandler::refreshAll);
		MonumentaItemRepository.prime();
		NeverEnoughMonumentaKeyBindings.initialize();
		NeverEnoughMonumenta.LOGGER.info("Client bootstrap complete for {}", NeverEnoughMonumenta.MOD_ID);
	}
}