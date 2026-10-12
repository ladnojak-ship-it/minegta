package mgta.item;

import mgta.MineGTA;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

/** Every item MineGTA adds, in one place. */
public final class ModItems {
	public static final Item AMMO_PISTOL = item("ammo_pistol");
	public static final Item AMMO_SMG = item("ammo_smg");
	public static final Item AMMO_SHELL = item("ammo_shell");
	public static final Item AMMO_RIFLE = item("ammo_rifle");
	public static final Item AMMO_ROCKET = item("ammo_rocket");

	public static final GunItem PISTOL = gun("pistol", new GunSpec(
			1, 6.0F, 8, 64.0, 0.0F, 2.5F,
			false, AMMO_PISTOL, 1, 1.5F, 0.75F, SoundEvents.FIREWORK_ROCKET_BLAST));

	public static final GunItem MICRO_SMG = gun("micro_smg", new GunSpec(
			1, 5.0F, 3, 48.0, 0.10F, 1.0F,
			false, AMMO_SMG, 1, 1.1F, 1.45F, SoundEvents.FIREWORK_ROCKET_BLAST));

	public static final GunItem PUMP_SHOTGUN = gun("pump_shotgun", new GunSpec(
			8, 4.0F, 20, 24.0, 0.32F, 6.0F,
			false, AMMO_SHELL, 1, 1.9F, 0.65F, SoundEvents.FIREWORK_ROCKET_BLAST));

	public static final GunItem CARBINE_RIFLE = gun("carbine_rifle", new GunSpec(
			1, 9.0F, 4, 96.0, 0.03F, 3.0F,
			false, AMMO_RIFLE, 1, 1.6F, 0.95F, SoundEvents.FIREWORK_ROCKET_BLAST));

	public static final GunItem RPG = gun("rpg", new GunSpec(
			1, 0.0F, 40, 64.0, 0.0F, 5.0F,
			true, AMMO_ROCKET, 1, 1.0F, 1.0F, SoundEvents.FIREWORK_ROCKET_LAUNCH));

	private ModItems() {
	}

	private static Item item(String name) {
		return Registry.register(BuiltInRegistries.ITEM, MineGTA.id(name), new Item(new Item.Properties()));
	}

	private static GunItem gun(String name, GunSpec spec) {
		return Registry.register(
				BuiltInRegistries.ITEM,
				MineGTA.id(name),
				new GunItem(new Item.Properties().stacksTo(1), spec));
	}

	/** Called from the mod initializer so the static fields are registered in order. */
	public static void init() {
		// Registration happens in the static initialisers; this just triggers them.
	}
}
