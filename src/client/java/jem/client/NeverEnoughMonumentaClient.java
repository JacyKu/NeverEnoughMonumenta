package jem.client;

import jem.NeverEnoughMonumenta;
import jem.client.monumenta.MonumentaItemRepository;
import net.fabricmc.api.ClientModInitializer;

public class NeverEnoughMonumentaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MonumentaItemRepository.prime();
		NeverEnoughMonumenta.LOGGER.info("Client bootstrap complete for {}", NeverEnoughMonumenta.MOD_ID);
	}
}