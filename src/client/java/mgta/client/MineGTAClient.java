package mgta.client;

import mgta.world.SantosWorld;
import net.fabricmc.api.ClientModInitializer;

/** Client half of MineGTA: registers the GTA HUD. */
public class MineGTAClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GtaHud.register();

		mgta.MineGTA.LOGGER.info("MineGTA HUD: radar, wanted stars, health and armor arcs, cash, ammo.");
		// Keep the cop name in one place: the server marks police with it, the HUD counts it.
		SantosWorld.COP_NAME.length();
	}
}
