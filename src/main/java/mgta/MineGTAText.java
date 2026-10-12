package mgta;

import net.minecraft.network.chat.Component;

/** Tiny text helpers, so the HUD and the city speak the same language. */
public final class MineGTAText {
	private MineGTAText() {
	}

	/** What the player is told when their wanted level goes up. */
	public static Component wanted(int stars, String what) {
		return Component.literal("WANTED " + "★".repeat(stars) + " — " + what);
	}

	public static Component cash(long amount) {
		return Component.literal("$" + String.format("%,d", amount).replace(',', ' '));
	}
}
