package mgta.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mgta.MineGTA;
import mgta.MineGTAText;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The city maintenance contract: keep San Andreas' pedestrians walking, keep the LSPD angry, and cool
 * a player's wanted level off over time.
 *
 * <p>State is one map of player UUIDs to stars, kept on the server and replayed to the HUD by counting
 * LSPD units near the player (see {@code mgta.client.GtaHud}).
 */
public final class SantosWorld {
	public static final String COP_NAME = "LSPD";

	public static final int MAX_STARS = 5;

	private static final int COP_RADIUS = 36;
	private static final int PED_RADIUS = 46;
	private static final int CITY_TICK = 20;
	private static final long COOL_DOWN_TICKS = 20L * 90L;
	private static final int MAX_COPS = 14;

	private static final Map<UUID, Integer> wanted = new HashMap<>();
	private static final Map<UUID, Long> lastCrime = new HashMap<>();

	private SantosWorld() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SantosWorld::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(SantosWorld::afterDeath);
	}

	/** Stars a player currently runs up against. */
	public static int stars(ServerPlayer player) {
		return wanted.getOrDefault(player.getUUID(), 0);
	}

	/** A crime happened: bump the player's heat, and tell them about it. */
	public static void commitCrime(Player player, int stars, String what) {
		if (!(player instanceof ServerPlayer server)) {
			return;
		}

		int now = wanted.merge(player.getUUID(), stars, Math::min);
		now = Math.min(MAX_STARS, now);
		wanted.put(player.getUUID(), now);
		lastCrime.put(player.getUUID(), server.level().getGameTime());

		server.displayClientMessage(MineGTAText.wanted(now, what), true);
		server.level().playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	private static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getGameTime() % CITY_TICK != 0L) {
				continue;
			}

			for (ServerPlayer player : level.players()) {
				runCity(level, player);
			}
		}
	}

	private static void runCity(ServerLevel level, ServerPlayer player) {
		UUID id = player.getUUID();
		long gameTime = level.getGameTime();
		int stars = wanted.getOrDefault(id, 0);

		if (stars > 0 && gameTime - lastCrime.getOrDefault(id, gameTime) > COOL_DOWN_TICKS) {
			stars = Math.max(0, stars - 1);
			wanted.put(id, stars);
			lastCrime.put(id, gameTime);
		}

		int cops = countCops(level, player);

		if (stars > 0 && cops < Math.min(MAX_COPS, stars * 2)) {
			spawnCop(level, player);
		}

		if (countCivilians(level, player) < 7) {
			spawnCivilian(level, player);
		}
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof Player player) || !(player instanceof ServerPlayer server)) {
			return;
		}

		if (isCop(victim)) {
			commitCrime(server, 2, "Officer down");
			spawnReinforcements(server.level(), server, victim.blockPosition());
			return;
		}

		if (victim instanceof Villager) {
			commitCrime(server, 1, "Civilian killed");
			return;
		}

		if (victim instanceof AgeableMob) {
			commitCrime(server, 1, "Animal killed");
		}
	}

	static boolean isCop(LivingEntity entity) {
		return entity.hasCustomName() && COP_NAME.equals(entity.getCustomName().getString());
	}

	private static int countCops(ServerLevel level, ServerPlayer player) {
		int count = 0;

		for (Villager villager : level.getEntitiesOfClass(Villager.class, ring(player, COP_RADIUS))) {
			if (isCop(villager)) {
				count++;
			}
		}

		return count;
	}

	private static int countCivilians(ServerLevel level, ServerPlayer player) {
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, ring(player, PED_RADIUS));
		int count = 0;

		for (Villager villager : villagers) {
			if (!isCop(villager)) {
				count++;
			}
		}

		return count;
	}

	private static void spawnCop(ServerLevel level, ServerPlayer player) {
		BlockPos at = randomGround(level, player, COP_RADIUS);
		if (at == null) {
			return;
		}

		Villager cop = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (cop == null) {
			return;
		}

		cop.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		cop.setCustomName(Component.literal(COP_NAME));
		cop.setCustomNameVisible(true);
		cop.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		cop.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cop, Player.class, 24, true, false, null));
		cop.setPersistenceRequired();

		level.addFreshEntity(cop);
	}

	/** Kill a cop and more show up, GTA style. */
	private static void spawnReinforcements(ServerLevel level, ServerPlayer player, BlockPos around) {
		for (int i = 0; i < 2; i++) {
			BlockPos at = randomGround(level, player, COP_RADIUS);
			if (at == null) {
				continue;
			}

			Villager cop = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (cop == null) {
				continue;
			}

			cop.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			cop.setCustomName(Component.literal(COP_NAME));
			cop.setCustomNameVisible(true);
			cop.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			cop.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cop, Player.class, 32, true, false, null));
			cop.setPersistenceRequired();

			level.addFreshEntity(cop);
			level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.0);
		}
	}

	private static void spawnCivilian(ServerLevel level, ServerPlayer player) {
		BlockPos at = randomGround(level, player, PED_RADIUS - 8);
		if (at == null) {
			return;
		}

		Villager ped = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (ped == null) {
			return;
		}

		ped.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		ped.setPersistenceRequired();

		level.addFreshEntity(ped);
	}

	private static AABB ring(ServerPlayer player, double radius) {
		return new AABB(
				player.getX() - radius, player.getY() - radius, player.getZ() - radius,
				player.getX() + radius, player.getY() + radius, player.getZ() + radius);
	}

	private static BlockPos randomGround(ServerLevel level, ServerPlayer player, double radius) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 8.0 + level.getRandom().nextDouble() * (radius - 8.0);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);

			if (at.getY() > level.getMinY() && Math.abs(at.getY() - player.getY()) < 24.0) {
				return at.above();
			}
		}

		return null;
	}
}
