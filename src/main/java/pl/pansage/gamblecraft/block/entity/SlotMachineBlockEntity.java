package pl.pansage.gamblecraft.block.entity;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.casino.SlotMath;
import pl.pansage.gamblecraft.core.casino.SlotTiming;
import pl.pansage.gamblecraft.entity.CasinoNpc;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.JackpotData;
import pl.pansage.gamblecraft.game.PayoutMailbox;
import pl.pansage.gamblecraft.network.CasinoPayloads.Shake;
import pl.pansage.gamblecraft.registry.ModBlockEntities;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * A slot machine's state, on its lower half. The server decides the spin the moment the lever is pulled and
 * syncs it to everyone around; the clients only animate the reels towards the stops they were told.
 */
public class SlotMachineBlockEntity extends BlockEntity {
	public static final int[] BETS = {1, 5, 25, 100};

	private int bet = BETS[1];
	private boolean spinning;
	private long spinStart = -1000;
	private int[] stops = {0, 13, 26};
	private boolean suspense;
	private int lastWin;
	private boolean jackpotWon;
	private long jackpotShown = JackpotData.SEED;
	private UUID player;

	public SlotMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SLOT_MACHINE, pos, state);
	}

	public int bet() {
		return bet;
	}

	public boolean spinning() {
		return spinning;
	}

	public long spinStart() {
		return spinStart;
	}

	public int stop(int reel) {
		return stops[reel];
	}

	public boolean suspense() {
		return suspense;
	}

	public int lastWin() {
		return lastWin;
	}

	public boolean jackpotWon() {
		return jackpotWon;
	}

	public long jackpot() {
		return jackpotShown;
	}

	/** Pull the lever, or (sneaking) change the bet. */
	public void use(ServerPlayer user, boolean changeBet) {
		ServerLevel level = (ServerLevel) this.level;
		if (!AgeGate.require(user)) {
			return;
		}
		if (spinning) {
			user.sendOverlayMessage(Component.translatable("gamblecraft.slots.busy"));
			return;
		}
		if (changeBet) {
			int i = 0;
			while (i < BETS.length && BETS[i] != bet) {
				i++;
			}
			bet = BETS[(i + 1) % BETS.length];
			user.sendOverlayMessage(Component.translatable("gamblecraft.slots.bet", bet));
			level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
			sync();
			return;
		}
		if (!ChipCurrency.INSTANCE.withdraw(user, bet)) {
			user.sendOverlayMessage(Component.translatable("gamblecraft.slots.no_chips", bet));
			return;
		}
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).staked(user.getUUID(), bet);
		JackpotData pots = JackpotData.get(level.getServer());
		String key = JackpotData.keyAt(level, worldPosition);
		pots.feed(key, bet);
		SlotMath.Spin spin = SlotMath.spin(new java.util.SplittableRandom(level.getRandom().nextLong()));
		stops = spin.stops().clone();
		suspense = SlotTiming.suspense(spin);
		spinning = true;
		spinStart = level.getGameTime();
		player = user.getUUID();
		lastWin = 0;
		jackpotWon = false;
		jackpotShown = pots.chips(key);
		level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.7F);
		sync();
	}

	/** The machine's top half (it has a block entity too, but only the bottom one plays). */
	public boolean isUpper() {
		return getBlockState().getValue(pl.pansage.gamblecraft.block.TallMachineBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
	}

	/** A spin for a casino regular (the addicted gambler): no chips change hands and the jackpot is left alone. */
	public boolean npcSpin() {
		if (spinning || !(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		SlotMath.Spin spin = SlotMath.spin(new java.util.SplittableRandom(serverLevel.getRandom().nextLong()));
		stops = spin.stops().clone();
		suspense = SlotTiming.suspense(spin);
		spinning = true;
		spinStart = serverLevel.getGameTime();
		player = null;
		lastWin = 0;
		jackpotWon = false;
		serverLevel.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.7F);
		sync();
		return true;
	}

	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, SlotMachineBlockEntity machine) {
		if (!machine.spinning || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long t = level.getGameTime() - machine.spinStart;
		for (int reel = 0; reel < 3; reel++) {
			if (t == SlotTiming.stopTick(reel, machine.suspense)) {
				level.playSound(null, pos, pl.pansage.gamblecraft.registry.ModSounds.REEL_STOP, SoundSource.BLOCKS, 0.8F, 0.9F + reel * 0.12F);
			}
		}
		boolean creeping = machine.suspense && t > SlotTiming.stopTick(1, true) && t < SlotTiming.stopTick(2, true);
		if (t > SlotTiming.LEVER && t < SlotTiming.stopTick(2, machine.suspense) && t % (creeping ? 4 : 2) == 0) {
			level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, creeping ? 0.6F : 0.25F, creeping ? 1.0F : 1.8F);
		}
		if (t >= SlotTiming.doneTick(machine.suspense)) {
			machine.settle(serverLevel);
		}
	}

	/** The reels have stopped: pay the lines, maybe the jackpot, and make a show of it. */
	private void settle(ServerLevel level) {
		spinning = false;
		SlotMath.Spin spin = SlotMath.evaluate(stops);
		int won = (int) Math.round(bet * spin.multiplier());
		JackpotData pots = JackpotData.get(level.getServer());
		String key = JackpotData.keyAt(level, worldPosition);
		// only a player's spin can take the jackpot
		if (spin.jackpot() && player != null) {
			won += (int) pots.win(key);
			jackpotWon = true;
		}
		jackpotShown = pots.chips(key);
		ServerPlayer winner = player == null ? null : level.getServer().getPlayerList().getPlayer(player);
		won += (int) pl.pansage.gamblecraft.game.Luck.bonus(winner, won, level.getRandom());
		lastWin = won;
		if (player != null) {
			pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).won(player, won);
		}
		if (won > 0) {
			if (winner != null) {
				ChipCurrency.INSTANCE.deposit(winner, won);
				winner.sendOverlayMessage(Component.translatable(jackpotWon ? "gamblecraft.slots.jackpot" : "gamblecraft.slots.win", won));
			} else if (player != null) {
				PayoutMailbox.get(level.getServer()).post(player, ChipCurrency.INSTANCE.asStacks(won));
			}
			celebrate(level, won, winner);
		} else {
			level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5F, 0.6F);
		}
		sync();
	}

	private void celebrate(ServerLevel level, int won, ServerPlayer winner) {
		Vec3 top = Vec3.atCenterOf(worldPosition).add(0, 1.6, 0);
		boolean big = jackpotWon || won >= bet * 20;
		pl.pansage.gamblecraft.game.Celebrate.win(level, winner, won, bet, top, jackpotWon);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, top.x, top.y, top.z, big ? 30 : 8, 0.4, 0.3, 0.4, 0.05);
		if (!big) {
			return;
		}
		// a fountain of chips, fireworks over the machine, the room cheers and the floor shakes
		ItemParticleOption chip = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(new ItemStack(ModItems.CHIPS[4])));
		level.sendParticles(chip, top.x, top.y + 0.4, top.z, jackpotWon ? 120 : 50, 0.2, 0.3, 0.2, 0.35);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, jackpotWon ? 80 : 30, 0.5, 0.5, 0.5, 0.4);
		for (int i = 0; i < (jackpotWon ? 3 : 1); i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
					it.unimi.dsi.fastutil.ints.IntList.of(0xFFD700, 0xFF3030), it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFFF), true, true))));
			level.addFreshEntity(new FireworkRocketEntity(level, top.x + (i - 1) * 1.5, top.y + 1, top.z, rocket));
		}
		for (CasinoNpc npc : level.getEntitiesOfClass(CasinoNpc.class, new AABB(worldPosition).inflate(16))) {
			npc.gesture(CasinoNpc.GESTURE_CHEER);
		}
		if (jackpotWon) {
			Shake shake = new Shake(30, 0.9F);
			for (ServerPlayer near : PlayerLookup.around(level, top, 24)) {
				ServerPlayNetworking.send(near, shake);
			}
		}
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("bet", bet);
		output.putBoolean("spinning", spinning);
		output.putLong("spin_start", spinStart);
		output.putIntArray("stops", stops);
		output.putBoolean("suspense", suspense);
		output.putInt("last_win", lastWin);
		output.putBoolean("jackpot_won", jackpotWon);
		output.putLong("jackpot", jackpotShown);
		if (player != null) {
			output.store("player", UUIDUtil.CODEC, player);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bet = input.getIntOr("bet", BETS[1]);
		spinning = input.getBooleanOr("spinning", false);
		spinStart = input.getLongOr("spin_start", -1000);
		stops = input.getIntArray("stops").filter(a -> a.length == 3).orElse(new int[] {0, 13, 26});
		suspense = input.getBooleanOr("suspense", false);
		lastWin = input.getIntOr("last_win", 0);
		jackpotWon = input.getBooleanOr("jackpot_won", false);
		jackpotShown = input.getLongOr("jackpot", JackpotData.SEED);
		player = input.read("player", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
