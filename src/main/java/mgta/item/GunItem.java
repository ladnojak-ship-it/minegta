package mgta.item;

import java.util.Optional;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A GTA gun: right click to shoot, spends ammo out of your inventory, hits what you look at within
 * range, and produces tracer, recoil and noise. Almost all of it lives in {@link GunSpec}.
 */
public class GunItem extends Item {
	private static final double BULLET_GRACE = 0.35;

	private final GunSpec spec;

	public GunItem(Item.Properties properties, GunSpec spec) {
		super(properties);
		this.spec = spec;
	}

	public GunSpec spec() {
		return spec;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		if (!hasAmmo(player)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 0.35F, 1.8F);
			return InteractionResult.FAIL;
		}

		spendAmmo(player);

		if (!level.isClientSide()) {
			shoot(level, player);
		}

		player.getCooldowns().addCooldown(stack, spec.cooldownTicks());
		return InteractionResult.SUCCESS;
	}

	/** Raycast every pellet, damage whatever is in the way, leave a tracer and a bang behind. */
	private void shoot(Level level, Player shooter) {
		Vec3 eye = shooter.getEyePosition(1.0F);
		Vec3 forward = shooter.getViewVector(1.0F).normalize();

		for (int pellet = 0; pellet < spec.pellets(); pellet++) {
			Vec3 direction = forward;

			if (spec.spread() > 0.0F) {
				direction = direction.add(
						(level.getRandom().nextDouble() - 0.5) * spec.spread() * 2.0,
						(level.getRandom().nextDouble() - 0.5) * spec.spread() * 2.0,
						(level.getRandom().nextDouble() - 0.5) * spec.spread() * 2.0).normalize();
			}

			Vec3 end = eye.add(direction.scale(spec.range()));
			AABB sweep = shooter.getBoundingBox().expandTowards(direction.scale(spec.range())).inflate(1.0);

			double best = spec.range();
			Entity target = null;
			Vec3 contact = end;

			for (Entity candidate : level.getEntities(shooter, sweep)) {
				if (candidate == shooter || !candidate.isAlive()) {
					continue;
				}

				Optional<Vec3> hit = candidate.getBoundingBox().inflate(BULLET_GRACE).clip(eye, end);
				if (hit.isEmpty()) {
					continue;
				}

				double distance = eye.distanceToSqr(hit.get());
				if (distance < best * best) {
					best = Math.sqrt(distance);
					target = candidate;
					contact = hit.get();
				}
			}

			for (double t = 0.1; t < 1.0; t += 0.2) {
				Vec3 step = eye.add(direction.scale(best * t));
				level.addParticle(ParticleTypes.END_ROD, step.x, step.y, step.z, 0.0, 0.0, 0.0);
			}

			if (target != null) {
				target.hurt(level.damageSources().playerAttack(shooter), spec.damage());

				if (target instanceof LivingEntity body) {
					body.push(direction.x * 0.35, 0.32, direction.z * 0.35);
					// vanilla's hit cooldown is plenty here
				}
			}

			if (spec.explosive()) {
				detonate(level, shooter, contact);
			}
		}

		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), spec.shootSound(), SoundSource.PLAYERS, spec.soundVolume(), spec.soundPitch());
		recoil(shooter);
	}

	/** GTA rockets do not wait for vanilla's explosion code: cut a hole in the mobs around the impact. */
	private void detonate(Level level, Player shooter, Vec3 at) {
		AABB blast = new AABB(at.x - 3.5, at.y - 3.5, at.z - 3.5, at.x + 3.5, at.y + 3.5, at.z + 3.5);

		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, blast)) {
			body.hurt(level.damageSources().playerAttack(shooter), 18.0F);
			body.push(shooter.getX() - body.getX(), 0.6, shooter.getZ() - body.getZ());
		}

		for (int i = 0; i < 28; i++) {
			double salsa = level.getRandom().nextDouble() * Math.PI * 2.0;
			double lift = level.getRandom().nextDouble() * 0.9;
			level.addParticle(ParticleTypes.FLAME,
					at.x, at.y + 0.2, at.z,
					Math.cos(salsa) * 0.6, lift, Math.sin(salsa) * 0.6);
			level.addParticle(ParticleTypes.SMOKE,
					at.x, at.y + 0.2, at.z,
					Math.cos(salsa) * 0.4, lift * 0.8, Math.sin(salsa) * 0.4);
		}

		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 3.0F, 0.7F);
	}

	private void recoil(Player shooter) {
		float pitch = shooter.getXRot() - spec.recoil();
		shooter.setXRot(Math.max(-90.0F, pitch));
	}

	private boolean hasAmmo(Player player) {
		return countAmmo(player) >= spec.ammoPerShot();
	}

	private int countAmmo(Player player) {
		int total = 0;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(spec.ammo())) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private void spendAmmo(Player player) {
		int left = spec.ammoPerShot();

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (left <= 0) {
				break;
			}

			if (stack.is(spec.ammo())) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
	}
}
