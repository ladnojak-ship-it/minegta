package mgta.client;

import java.util.HashSet;
import java.util.Set;

import mgta.MineGTA;
import mgta.MineGTAText;
import mgta.item.GunItem;
import mgta.world.SantosWorld;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The GTA heads-up display: radar with blips, wanted stars, the health and armor arcs wrapped around
 * the radar, cash, the weapon you are holding and its ammo, and WASTED when it is over.
 *
 * <p>Wanted stars are derived by counting LSPD units around the player, which is what the player can
 * actually see, so no networking is needed.
 */
public final class GtaHud {
	private static final Identifier STAR_ON = MineGTA.id("textures/gui/star_on.png");
	private static final Identifier STAR_OFF = MineGTA.id("textures/gui/star_off.png");

	private static final int STAR_SIZE = 14;
	private static final int STAR_GAP = 3;
	private static final int RADAR_X = 62;
	private static final int RADAR_Y = 62;
	private static final int RADAR_RADIUS = 44;
	private static final int RADAR_RANGE = 48;
	private static final int HEALTH_COLOR = 0xFF3FBF3F;
	private static final int HEALTH_TRACK = 0x66FFFFFF;
	private static final int ARMOR_COLOR = 0xFF4FA3E8;
	private static final int ARMOR_TRACK = 0x66FFFFFF;
	private static final int BLIP_COP = 0xFFE23B3B;
	private static final int BLIP_PED = 0xFFF0F0F0;
	private static final int BLIP_ENEMY = 0xFFFFB13B;

	private static final Set<Integer> aliveCops = new HashSet<>();
	private static final Set<Integer> aliveMobs = new HashSet<>();
	private static long money = 0L;
	private static int stars = 0;

	private GtaHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MineGTA.id("gta_hud"), GtaHud::render);
	}

	static int stars(Set<Integer> cops) {
		return Mth.clamp(1 + cops.size() / 3, 1, SantosWorld.MAX_STARS);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.options.hideGui) {
			return;
		}

		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		updateState(player);

		drawRadar(graphics, player);
		drawWanted(graphics, width);
		drawCash(graphics, font, width);
		drawArcs(graphics, player);
		drawWeapon(graphics, font, player, width, height);

		if (player.getHealth() <= 0.0F) {
			drawWasted(graphics, font, width, height);
		}
	}

	/** Watch the LSPD: a cop that stops being around is a cop that got paid for. */
	private static void updateState(LocalPlayer player) {
		Set<Integer> cops = new HashSet<>();
		Set<Integer> hostiles = new HashSet<>();

		for (Entity entity : player.level().entitiesForRendering()) {
			if (player.distanceToSqr(entity) > RADAR_RANGE * RADAR_RANGE) {
				continue;
			}

			if (!(entity instanceof net.minecraft.world.entity.LivingEntity body) || body == player) {
				continue;
			}

			if (SantosWorld.isCop(body)) {
				cops.add(entity.getId());
			} else if (body instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive()) {
				hostiles.add(entity.getId());
			}
		}

		for (int id : aliveCops) {
			if (!cops.contains(id)) {
				money += 250L;
			}
		}

		for (int id : aliveMobs) {
			if (!hostiles.contains(id)) {
				money += 50L;
			}
		}

		stars = cops.isEmpty() ? 0 : stars(cops);
		aliveCops.clear();
		aliveCops.addAll(cops);
		aliveMobs.clear();
		aliveMobs.addAll(hostiles);
	}

	private static void drawRadar(GuiGraphicsExtractor graphics, LocalPlayer player) {
		float yaw = player.getYRot() * (float) Math.PI / 180.0F;
		float forwardX = -Mth.sin(yaw);
		float forwardZ = Mth.cos(yaw);
		float rightX = Mth.cos(yaw);
		float rightZ = Mth.sin(yaw);

		for (int dy = -RADAR_RADIUS; dy <= RADAR_RADIUS; dy += 2) {
			int half = (int) Math.sqrt(Math.max(0.0, RADAR_RADIUS * (double) RADAR_RADIUS - (double) dy * dy));
			graphics.fill(RADAR_X - half, RADAR_Y + dy, RADAR_X + half, RADAR_Y + dy + 2, 0x99101418);
		}

		drawArc(graphics, RADAR_X, RADAR_Y, RADAR_RADIUS, 2, 0xFF000000, 0, 360);

		for (Entity entity : player.level().entitiesForRendering()) {
			if (entity == player || player.distanceToSqr(entity) > (double) RADAR_RANGE * RADAR_RANGE) {
				continue;
			}

			double dx = entity.getX() - player.getX();
			double dz = entity.getZ() - player.getZ();
			double radarX = dx * rightX + dz * rightZ;
			double radarY = dx * forwardX + dz * forwardZ;

			if (Math.abs(radarX) > RADAR_RANGE || Math.abs(radarY) > RADAR_RANGE) {
				continue;
			}

			int blipX = RADAR_X + (int) Math.round(radarX / RADAR_RANGE * (RADAR_RADIUS - 3.0));
			int blipY = RADAR_Y - (int) Math.round(radarY / RADAR_RANGE * (RADAR_RADIUS - 3.0));

			int color = entity instanceof net.minecraft.world.entity.player.Player
					? BLIP_ENEMY
					: BLIP_PED;

			if (entity instanceof net.minecraft.world.entity.Mob mob && SantosWorld.isCop(mob)) {
				color = BLIP_COP;
			} else if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive()) {
				color = BLIP_ENEMY;
			}

			graphics.fill(blipX - 2, blipY - 2, blipX + 3, blipY + 3, color);
		}

		graphics.fill(RADAR_X - 2, RADAR_Y - 2, RADAR_X + 3, RADAR_Y + 3, 0xFFFFFFFF);
	}

	private static void drawWanted(GuiGraphicsExtractor graphics, int width) {
		for (int i = 0; i < SantosWorld.MAX_STARS; i++) {
			int x = width - 14 - (SantosWorld.MAX_STARS - i) * (STAR_SIZE + STAR_GAP);
			Identifier sprite = i < stars ? STAR_ON : STAR_OFF;
			graphics.blit(RenderPipelines.GUI_TEXTURED, sprite, x, 24, 0, 0, STAR_SIZE, STAR_SIZE, STAR_SIZE, STAR_SIZE);
		}
	}

	private static void drawCash(GuiGraphicsExtractor graphics, Font font, int width) {
		String cash = MineGTAText.cash(money).getString();
		graphics.text(font, cash, width - 8 - font.width(cash), 8, 0xFFF2F2F2, true);
	}

	/** GTA V's health and armor arcs: two thin rings sitting on the radar's chin. */
	private static void drawArcs(GuiGraphicsExtractor graphics, LocalPlayer player) {
		float health = player.getHealth();
		float maxHealth = player.getMaxHealth();
		float armor = player.getArmorValue();
		float maxArmor = 20.0F;

		drawArc(graphics, RADAR_X, RADAR_Y, RADAR_RADIUS + 4, 4, HEALTH_TRACK, 28, 152);
		drawArc(graphics, RADAR_X, RADAR_Y, RADAR_RADIUS + 4, 4, HEALTH_COLOR, 28, 28 + (152 - 28) * Mth.clamp(health / maxHealth, 0, 1));

		drawArc(graphics, RADAR_X, RADAR_Y, RADAR_RADIUS + 10, 3, ARMOR_TRACK, 28, 152);
		drawArc(graphics, RADAR_X, RADAR_Y, RADAR_RADIUS + 10, 3, ARMOR_COLOR, 28, 28 + (152 - 28) * Mth.clamp(armor / maxArmor, 0, 1));
	}

	private static void drawWeapon(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int width, int height) {
		ItemStack held = player.getMainHandItem();
		Item item = held.getItem();

		String name = held.isEmpty() ? "UNARMED" : held.getHoverName().getString();
		graphics.text(font, name, width - 12 - font.width(name), height - 64, 0xFFF2F2F2, true);

		if (item instanceof GunItem gun) {
			int ammo = countAmmo(player, gun.spec().ammo());
			String ammoText = String.valueOf(ammo);

			int x = width - 12 - font.width(ammoText);
			int color = ammo > 0 ? 0xFFE8E8E8 : 0xFFE0504C;

			graphics.text(font, ammoText, x, height - 52, color, true);
		}
	}

	private static int countAmmo(LocalPlayer player, Item ammo) {
		int total = 0;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(ammo)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static void drawWasted(GuiGraphicsExtractor graphics, Font font, int width, int height) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0F, height / 2.0F);
		graphics.pose().scale(3.0F, 3.0F);
		graphics.text(font, "WASTED", -font.width("WASTED") / 2, -4, 0xFFE02020, true);
		graphics.pose().popMatrix();
	}

	/** Thick arc: a string of small fills along a circle, cheap and pipeline-free. */
	private static void drawArc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int thickness, int color, double fromDegrees, double toDegrees) {
		for (double angle = fromDegrees; angle <= toDegrees; angle += 1.5) {
			double radians = Math.toRadians(angle);
			int x = centerX + (int) Math.round(Math.cos(radians) * radius);
			int y = centerY + (int) Math.round(Math.sin(radians) * radius);
			int half = Math.max(1, thickness / 2);

			graphics.fill(x - half, y - half, x + half, y + half, color);
		}
	}
}
