package dev.rehan.passthrough.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The cursor inside a screen of minecraft's own, while the host (GTA) has the focus and the mouse.
 *
 * <p>The host cannot give its window away - Windows only hands the focus to the foreground process - so
 * instead the host steers this cursor: it turns off its own look controls while a screen is open and sends
 * the mouse it would have used as {@code {"t":"mouse","dx":..,"dy":..}}. Clicks arrive as the usual
 * {@code attack}/{@code use} keys and are routed to the screen at the cursor.
 */
public final class ScreenInput {
	private ScreenInput() {
	}

	private static double x, y;
	private static boolean inScreen;

	/** A cursor is only steered while a screen is open and it still wants pointer input. */
	public static boolean wantsCursor() {
		return Minecraft.getInstance().gui.screen() != null
			&& !(Minecraft.getInstance().gui.screen() instanceof ChatScreen);
	}

	/** The host's mouse, in GUI-scaled pixels, applied to whatever screen is open. */
	public static void move(final Minecraft minecraft, final double dx, final double dy) {
		final Screen screen = minecraft.gui.screen();
		if (screen == null) {
			return;
		}
		if (!inScreen) {
			// the screen just opened: start at its centre rather than wherever the last one left off
			x = screen.width / 2.0;
			y = screen.height / 2.0;
			inScreen = true;
		}
		x = Math.max(0.0, Math.min(screen.width, x + dx));
		y = Math.max(0.0, Math.min(screen.height, y + dy));
		screen.mouseMoved(x, y);
	}

	/** Called when a screen closes, so the cursor restarts centred next time. */
	public static void resetScreen() {
		inScreen = false;
	}

	/**
	 * The host's mouse button at the cursor: {@code 0} attack (left), {@code 1} use (right). Screens take
	 * pointer events directly, because they are driven by the cursor rather than by a keybinding.
	 */
	public static void mouse(final boolean down, final int button) {
		final Minecraft minecraft = Minecraft.getInstance();
		final Screen screen = minecraft.gui.screen();
		if (screen == null) {
			return;
		}
		final MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
		if (down) {
			move(minecraft, 0.0, 0.0);
			screen.mouseClicked(event, false);
		} else {
			screen.mouseReleased(event);
		}
	}
}
