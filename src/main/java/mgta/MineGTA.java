package mgta;

import mgta.item.ModItems;
import mgta.world.SantosWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineGTA: Grand Theft Auto inside Minecraft.
 *
 * <p>Two halves: the gameplay half lives here (weapons, the LSPD, pedestrians), the GTA HUD lives in
 * {@code mgta.client}.
 */
public class MineGTA implements ModInitializer {
	public static final String MOD_ID = "minegta";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		SantosWorld.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(ModItems.PISTOL);
			output.accept(ModItems.MICRO_SMG);
			output.accept(ModItems.PUMP_SHOTGUN);
			output.accept(ModItems.CARBINE_RIFLE);
			output.accept(ModItems.RPG);
			output.accept(ModItems.AMMO_PISTOL);
			output.accept(ModItems.AMMO_SMG);
			output.accept(ModItems.AMMO_SHELL);
			output.accept(ModItems.AMMO_RIFLE);
			output.accept(ModItems.AMMO_ROCKET);
		});

		LOGGER.info("MineGTA loaded: welcome to San Andreas, in cubes.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
