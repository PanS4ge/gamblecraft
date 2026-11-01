package pl.pansage.gamblecraft.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.entity.CasinoNpc;
import pl.pansage.gamblecraft.game.CasinoRegion.Casino;
import pl.pansage.gamblecraft.network.CasinoPayloads.AlarmPulse;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * The casino's security desk. Breaking or placing a block inside, or hurting the staff, sets off the alarm: sirens
 * and red lamps, the doors locked for a minute, guards turning out of the security room (two, then one more for every
 * further offence, six at most) after everyone who offended, and a day's ban for them. When the offenders are gone the
 * alarm ends: every block that was broken comes back and everything that was built disappears.
 */
public final class CasinoSecurity extends SavedData {
	public static final int MAX_GUARDS = GambleConfig.get().maxGuards;
	public static final long LOCKDOWN_TICKS = GambleConfig.get().alarmLockdownSeconds * 20L;
	public static final long BAN_TICKS = GambleConfig.get().banDays * 24000L;
	private static final long MAX_ALARM_TICKS = 6000;
	/** Offences by one player closer together than this count once (a burst of broken blocks is one offence). */
	private static final long OFFENCE_COOLDOWN = 60;
	/** Offenders further than this outside the casino are let go. */
	private static final int CHASE_MARGIN = 24;

	/** A block as it was before an offender touched it, with its block entity's data. */
	public record Snapshot(BlockState state, Optional<CompoundTag> data) {
		static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockState.CODEC.fieldOf("state").forGetter(Snapshot::state),
				CompoundTag.CODEC.optionalFieldOf("data").forGetter(Snapshot::data)
		).apply(i, Snapshot::new));

		static Snapshot of(ServerLevel level, BlockPos pos) {
			BlockEntity entity = level.getBlockEntity(pos);
			return new Snapshot(level.getBlockState(pos), Optional.ofNullable(entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
		}
	}

	private record SavedBlock(BlockPos pos, Snapshot snapshot) {
		static final Codec<SavedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(SavedBlock::pos),
				Snapshot.CODEC.fieldOf("was").forGetter(SavedBlock::snapshot)
		).apply(i, SavedBlock::new));
	}

	/** One casino's alarm. */
	public static final class Alarm {
		static final Codec<Alarm> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("casino").forGetter(a -> a.casino),
				BoundingBox.CODEC.fieldOf("box").forGetter(a -> a.box),
				BlockPos.CODEC.optionalFieldOf("post").forGetter(a -> Optional.ofNullable(a.post)),
				Codec.LONG.fieldOf("started").forGetter(a -> a.started),
				Codec.LONG.fieldOf("last_offence").forGetter(a -> a.lastOffence),
				Codec.INT.fieldOf("offences").forGetter(a -> a.offences),
				UUIDUtil.CODEC.listOf().fieldOf("offenders").forGetter(a -> List.copyOf(a.offenders)),
				UUIDUtil.CODEC.listOf().fieldOf("guards").forGetter(a -> List.copyOf(a.guards)),
				SavedBlock.CODEC.listOf().fieldOf("snapshots").forGetter(a -> a.snapshots.entrySet().stream()
						.map(e -> new SavedBlock(e.getKey(), e.getValue())).toList()),
				BlockPos.CODEC.listOf().fieldOf("placed").forGetter(a -> List.copyOf(a.placed)),
				BlockPos.CODEC.listOf().fieldOf("lamps").forGetter(a -> List.copyOf(a.lamps))
		).apply(i, Alarm::new));

		private final String casino;
		private final BoundingBox box;
		private final BlockPos post;
		private final long started;
		private long lastOffence;
		private int offences;
		private final Set<UUID> offenders;
		private final List<UUID> guards;
		private final Map<BlockPos, Snapshot> snapshots = new HashMap<>();
		private final Set<BlockPos> placed;
		private final List<BlockPos> lamps;

		Alarm(Casino casino, long now) {
			this(casino.key(), casino.box(), Optional.ofNullable(casino.guardPost()), now, now, 0, List.of(), List.of(), List.of(), List.of(), List.of());
		}

		private Alarm(String casino, BoundingBox box, Optional<BlockPos> post, long started, long lastOffence, int offences, List<UUID> offenders,
				List<UUID> guards, List<SavedBlock> snapshots, List<BlockPos> placed, List<BlockPos> lamps) {
			this.casino = casino;
			this.box = box;
			this.post = post.orElse(null);
			this.started = started;
			this.lastOffence = lastOffence;
			this.offences = offences;
			this.offenders = new HashSet<>(offenders);
			this.guards = new ArrayList<>(guards);
			for (SavedBlock saved : snapshots) {
				this.snapshots.put(saved.pos(), saved.snapshot());
			}
			this.placed = new HashSet<>(placed);
			this.lamps = new ArrayList<>(lamps);
		}

		public String casino() {
			return casino;
		}

		public int offences() {
			return offences;
		}

		public Set<UUID> offenders() {
			return offenders;
		}

		public List<UUID> guards() {
			return guards;
		}

		public Map<BlockPos, Snapshot> snapshots() {
			return snapshots;
		}

		public Set<BlockPos> placed() {
			return placed;
		}

		public BlockPos post() {
			return post;
		}

		public long started() {
			return started;
		}
	}

	private static final Codec<CasinoSecurity> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Alarm.CODEC).fieldOf("alarms").forGetter(s -> s.alarms),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("bans").forGetter(s -> s.bans)
	).apply(i, CasinoSecurity::new));
	public static final SavedDataType<CasinoSecurity> TYPE = new SavedDataType<>(GambleCraft.id("casino_security"), CasinoSecurity::new, CODEC, null);

	private final Map<String, Alarm> alarms;
	private final Map<UUID, Long> bans;
	/** When each player last offended (not saved: only spaces offences out). */
	private final Map<UUID, Long> lastOffenceBy = new HashMap<>();
	private static final ThreadLocal<PendingPlace> PENDING = new ThreadLocal<>();

	private record PendingPlace(BlockPos pos, Snapshot here, Snapshot above) {
	}

	private CasinoSecurity() {
		this(Map.of(), Map.of());
	}

	private CasinoSecurity(Map<String, Alarm> alarms, Map<UUID, Long> bans) {
		this.alarms = new HashMap<>(alarms);
		this.bans = new HashMap<>(bans);
	}

	public static CasinoSecurity get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || exempt(serverPlayer)) {
				return true;
			}
			Optional<Casino> casino = CasinoRegion.find(serverLevel, pos);
			if (casino.isEmpty()) {
				return true;
			}
			get(serverLevel.getServer()).breakIn(serverLevel, casino.get(), serverPlayer, pos, state);
			return false;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player) || exempt(player)
					|| !(entity instanceof CasinoNpc || entity instanceof CasinoGuardEntity) || entity instanceof pl.pansage.gamblecraft.entity.PickpocketEntity) {
				return;
			}
			CasinoRegion.find(level, entity.blockPosition()).ifPresent(casino -> get(level.getServer()).offence(level, casino, player, "assault"));
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level == level.getServer().overworld() && level.getGameTime() % 10 == 0) {
				get(level.getServer()).tick(level);
			}
		});
	}

	/** Creative and spectating players build and break freely (that is how the casino gets maintained). */
	public static boolean exempt(Player player) {
		return player.isCreative() || player.isSpectator();
	}

	public Optional<Alarm> alarm(String casino) {
		return Optional.ofNullable(alarms.get(casino));
	}

	public boolean isOffender(String casino, Player player) {
		Alarm alarm = alarms.get(casino);
		return alarm != null && alarm.offenders.contains(player.getUUID());
	}

	/** Lifts a player's ban. */
	public void pardon(UUID player) {
		if (bans.remove(player) != null) {
			setDirty();
		}
	}

	public boolean banned(ServerLevel level, Player player) {
		Long until = bans.get(player.getUUID());
		return until != null && until > level.getGameTime();
	}

	/** The doors stay shut for the first minute of an alarm. */
	public boolean lockedDown(ServerLevel level, BlockPos pos) {
		return CasinoRegion.find(level, pos).map(casino -> alarms.get(casino.key()))
				.filter(alarm -> alarm.offences > 0 && level.getGameTime() - alarm.started < LOCKDOWN_TICKS).isPresent();
	}

	private Alarm ensure(ServerLevel level, Casino casino) {
		return alarms.computeIfAbsent(casino.key(), key -> new Alarm(casino, level.getGameTime()));
	}

	/** A block broken inside: kept for later and taken away without drops, and the alarm goes off. */
	private void breakIn(ServerLevel level, Casino casino, ServerPlayer player, BlockPos pos, BlockState state) {
		Alarm alarm = ensure(level, casino);
		// the neighbours too: carpets, lanterns and the other half of a door go with the block
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			alarm.snapshots.computeIfAbsent(near.immutable(), p -> Snapshot.of(level, p));
		}
		level.levelEvent(2001, pos, Block.getId(state));
		level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
		offence(level, casino, player, "break");
	}

	/** Called by the BlockItem mixin before a block is placed. */
	public static void beforePlace(BlockPlaceContext context) {
		PENDING.remove();
		if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player && !exempt(player)) {
			BlockPos pos = context.getClickedPos();
			if (CasinoRegion.contains(level, pos)) {
				PENDING.set(new PendingPlace(pos.immutable(), Snapshot.of(level, pos), Snapshot.of(level, pos.above())));
			}
		}
	}

	/** Called by the BlockItem mixin after placing: a block built inside will vanish when the alarm is over. */
	public static void afterPlace(BlockPlaceContext context, InteractionResult result) {
		PendingPlace pending = PENDING.get();
		PENDING.remove();
		if (pending == null || !result.consumesAction() || !(context.getLevel() instanceof ServerLevel level)
				|| !(context.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		CasinoRegion.find(level, pending.pos()).ifPresent(casino -> {
			CasinoSecurity security = get(level.getServer());
			Alarm alarm = security.ensure(level, casino);
			alarm.snapshots.putIfAbsent(pending.pos(), pending.here());
			alarm.placed.add(pending.pos());
			if (level.getBlockState(pending.pos().above()) != pending.above().state()) {
				alarm.snapshots.putIfAbsent(pending.pos().above(), pending.above());
				alarm.placed.add(pending.pos().above());
			}
			security.offence(level, casino, player, "build");
		});
	}

	/** Raises (or raises further) the casino's alarm against {@code player}. */
	public void offence(ServerLevel level, Casino casino, ServerPlayer player, String what) {
		if (exempt(player)) {
			return;
		}
		long now = level.getGameTime();
		Alarm alarm = ensure(level, casino);
		Long last = lastOffenceBy.put(player.getUUID(), now);
		alarm.lastOffence = now;
		alarm.offenders.add(player.getUUID());
		bans.put(player.getUUID(), now + BAN_TICKS);
		if (last == null || now - last >= OFFENCE_COOLDOWN || alarm.offences == 0) {
			alarm.offences++;
			player.sendSystemMessage(Component.translatable("gamblecraft.security.offence." + what).withStyle(ChatFormatting.RED));
			if (alarm.offences == 1) {
				sound(level, alarm);
			}
			staff(level, alarm);
		}
		setDirty();
	}

	/** First moments of an alarm: the bell, the lamps on, everyone inside told. */
	private void sound(ServerLevel level, Alarm alarm) {
		BoundingBox box = alarm.box;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.REDSTONE_LAMP)) {
				alarm.lamps.add(pos.immutable());
				level.setBlock(pos, level.getBlockState(pos).setValue(RedstoneLampBlock.LIT, true), Block.UPDATE_CLIENTS);
			}
		}
		BlockPos centre = box.getCenter();
		level.playSound(null, centre, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 0.7F);
		for (ServerPlayer player : playersInside(level, alarm)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.security.alarm").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
	}

	/** Brings the guards on duty up to two plus one per further offence. */
	private void staff(ServerLevel level, Alarm alarm) {
		int alive = 0;
		for (UUID id : alarm.guards) {
			Entity guard = level.getEntity(id);
			if (guard != null && guard.isAlive()) {
				alive++;
			}
		}
		int wanted = Math.min(MAX_GUARDS, alarm.offences + 1);
		for (int i = alive; i < wanted; i++) {
			CasinoGuardEntity guard = ModEntities.CASINO_GUARD.create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				return;
			}
			Vec3 at = alarm.post != null ? Vec3.atBottomCenterOf(alarm.post).add(i % 3 - 1, 0, i / 3 % 2) : nearOffender(level, alarm, i);
			guard.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
			guard.onDuty(alarm.casino, alarm.post);
			level.addFreshEntity(guard);
			alarm.guards.add(guard.getUUID());
			level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.2, at.z, 12, 0.4, 0.8, 0.4, 0.02);
			level.playSound(null, BlockPos.containing(at), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.0F, 0.8F);
		}
	}

	/** Without a security room (a declared casino) guards walk in from a few blocks away from an offender. */
	private static Vec3 nearOffender(ServerLevel level, Alarm alarm, int index) {
		for (UUID id : alarm.offenders) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			Entity offender = player != null ? player : level.getEntity(id);
			if (offender != null) {
				double angle = index * 1.3 + level.getRandom().nextDouble();
				return offender.position().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
			}
		}
		return Vec3.atBottomCenterOf(alarm.box.getCenter());
	}

	private List<ServerPlayer> playersInside(ServerLevel level, Alarm alarm) {
		List<ServerPlayer> inside = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (alarm.box.isInside(player.blockPosition())) {
				inside.add(player);
			}
		}
		return inside;
	}

	private void tick(ServerLevel level) {
		long now = level.getGameTime();
		boolean changed = false;
		for (Iterator<Alarm> it = alarms.values().iterator(); it.hasNext(); ) {
			Alarm alarm = it.next();
			changed |= alarm.guards.removeIf(id -> {
				Entity guard = level.getEntity(id);
				return guard == null || !guard.isAlive();
			});
			BoundingBox chase = alarm.box.inflatedBy(CHASE_MARGIN);
			changed |= alarm.offenders.removeIf(id -> {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				return player == null || !player.isAlive() || exempt(player) || player.level() != level || !chase.isInside(player.blockPosition());
			});
			boolean calm = alarm.offenders.isEmpty() && now - alarm.lastOffence >= LOCKDOWN_TICKS;
			if (alarm.offences == 0 ? now - alarm.started > 200 : calm || now - alarm.started >= MAX_ALARM_TICKS) {
				end(level, alarm);
				it.remove();
				changed = true;
			} else if (alarm.offences > 0) {
				sirens(level, alarm, now);
			}
		}
		if (now % 1200 == 0) {
			changed |= bans.values().removeIf(until -> until <= now);
		}
		if (changed) {
			setDirty();
		}
	}

	private void sirens(ServerLevel level, Alarm alarm, long now) {
		boolean high = now / 10 % 2 == 0;
		if (high) {
			level.playSound(null, alarm.box.getCenter(), pl.pansage.gamblecraft.registry.ModSounds.ALARM, SoundSource.BLOCKS, 3.0F, 1.0F);
		}
		DustParticleOptions red = new DustParticleOptions(0xFF2020, 2.0F);
		for (BlockPos lamp : alarm.lamps) {
			level.sendParticles(red, lamp.getX() + 0.5, lamp.getY() - 0.3, lamp.getZ() + 0.5, 4, 0.5, 0.15, 0.5, 0);
		}
		if (high) {
			for (ServerPlayer player : playersInside(level, alarm)) {
				ServerPlayNetworking.send(player, new AlarmPulse(true));
			}
		}
	}

	/** Ends the casino's alarm now: blocks back, lamps off, guards back to their room. */
	public void end(ServerLevel level, String casino) {
		Alarm alarm = alarms.remove(casino);
		if (alarm != null) {
			end(level, alarm);
			setDirty();
		}
	}

	private void end(ServerLevel level, Alarm alarm) {
		restore(level, alarm);
		for (BlockPos lamp : alarm.lamps) {
			BlockState state = level.getBlockState(lamp);
			if (state.is(Blocks.REDSTONE_LAMP)) {
				level.setBlock(lamp, state.setValue(RedstoneLampBlock.LIT, false), Block.UPDATE_CLIENTS);
			}
		}
		for (UUID id : alarm.guards) {
			if (level.getEntity(id) instanceof CasinoGuardEntity guard) {
				guard.standDown();
			}
		}
		if (alarm.offences > 0) {
			for (ServerPlayer player : playersInside(level, alarm)) {
				player.sendOverlayMessage(Component.translatable("gamblecraft.security.all_clear").withStyle(ChatFormatting.GREEN));
				ServerPlayNetworking.send(player, new AlarmPulse(false));
			}
		}
	}

	/** Puts every block an offender touched back as it was, lowest first so everything has its support. */
	private static void restore(ServerLevel level, Alarm alarm) {
		List<Map.Entry<BlockPos, Snapshot>> blocks = new ArrayList<>(alarm.snapshots.entrySet());
		blocks.sort(Comparator.comparingInt(e -> e.getKey().getY()));
		for (Map.Entry<BlockPos, Snapshot> entry : blocks) {
			BlockPos pos = entry.getKey();
			Snapshot was = entry.getValue();
			if (level.getBlockState(pos) == was.state()) {
				continue;
			}
			level.setBlock(pos, was.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			was.data().ifPresent(tag -> {
				BlockEntity entity = BlockEntity.loadStatic(pos, was.state(), tag, level.registryAccess());
				if (entity != null) {
					level.setBlockEntity(entity);
				}
			});
			level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
		}
	}

	/** Knocks out every block an offender built under {@code target} (a guard's shockwave). Returns how many. */
	public int crumbleUnder(ServerLevel level, String casino, Entity target) {
		Alarm alarm = alarms.get(casino);
		if (alarm == null) {
			return 0;
		}
		int crumbled = 0;
		BlockPos feet = target.blockPosition();
		for (int down = 0; down < 32; down++) {
			BlockPos pos = feet.below(down);
			if (alarm.placed.contains(pos)) {
				level.destroyBlock(pos, false);
				crumbled++;
			} else if (down > 0 && !level.getBlockState(pos).isAir()) {
				break;
			}
		}
		return crumbled;
	}

	/** Records a block as built by an offender (as the BlockItem mixin does). */
	public void recordPlaced(ServerLevel level, Casino casino, BlockPos pos, BlockState was) {
		Alarm alarm = ensure(level, casino);
		alarm.snapshots.putIfAbsent(pos.immutable(), new Snapshot(was, Optional.empty()));
		alarm.placed.add(pos.immutable());
		setDirty();
	}
}
