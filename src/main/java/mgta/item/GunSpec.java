package mgta.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * How a MineGTA weapon behaves: pellets per shot, damage, cooldown, range, spread, recoil, and the
 * ammo item it eats. All of it is data, so the guns stay inert objects.
 *
 * @param pellets      projectiles per trigger pull
 * @param damage       damage per pellet
 * @param cooldownTicks ticks between shots
 * @param range        metres before the bullet stops existing
 * @param spread       0 for a laser, 0.3+ for a shotgun
 * @param recoil       vertical kick per shot, in degrees
 * @param explosive    hit or airburst explodes
 * @param ammo         the ammo item consumed per shot
 * @param ammoPerShot  ammo items consumed per shot
 * @param soundVolume  how loud the shot is
 * @param soundPitch   how high the shot sounds
 * @param shootSound   the shot's body (a rocketry launch for the RPG)
 */
public record GunSpec(int pellets, float damage, int cooldownTicks, double range, float spread, float recoil,
		boolean explosive, Item ammo, int ammoPerShot, float soundVolume, float soundPitch,
		SoundEvent shootSound) {
}
