package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.entity.DebtCollectorEntity;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * The loan shark's book: one loan per player, a quarter on top, three days to pay. After that his collectors come
 * calling every few minutes, wherever the player is (except inside a casino, which has its own security).
 */
public final class LoanData extends SavedData {
	public static final long[] AMOUNTS = {100, 500, 2000};
	public static final long TERM = GambleConfig.get().loanTermDays * 24000L;
	private static final long VISIT_EVERY = 6000;

	public record Loan(long owed, long due, long nextVisit) {
		static final Codec<Loan> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("owed").forGetter(Loan::owed),
				Codec.LONG.fieldOf("due").forGetter(Loan::due),
				Codec.LONG.fieldOf("next_visit").forGetter(Loan::nextVisit)
		).apply(i, Loan::new));
	}

	private static final Codec<LoanData> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Loan.CODEC)
			.xmap(map -> new LoanData(new HashMap<>(map)), data -> data.loans);
	public static final SavedDataType<LoanData> TYPE = new SavedDataType<>(GambleCraft.id("loans"), LoanData::new, CODEC, null);

	private final Map<UUID, Loan> loans;

	private LoanData() {
		this(new HashMap<>());
	}

	private LoanData(Map<UUID, Loan> loans) {
		this.loans = loans;
	}

	public static LoanData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level == level.getServer().overworld() && level.getGameTime() % 20 == 7) {
				get(level.getServer()).tick(level);
			}
		});
	}

	public static long repayment(long amount) {
		return amount + amount * GambleConfig.get().loanInterestPercent / 100;
	}

	public Loan loan(UUID player) {
		return loans.get(player);
	}

	public long owed(UUID player) {
		Loan loan = loans.get(player);
		return loan == null ? 0 : loan.owed();
	}

	/** Lends {@code amount} chips; returns false if the player still owes. */
	public boolean lend(ServerPlayer player, long amount) {
		if (loans.containsKey(player.getUUID())) {
			return false;
		}
		long now = player.level().getGameTime();
		loans.put(player.getUUID(), new Loan(repayment(amount), now + TERM, now + TERM));
		ChipCurrency.INSTANCE.deposit(player, amount);
		setDirty();
		return true;
	}

	/** Pays off as much as the player's chips cover; returns what is still owed. */
	public long repay(ServerPlayer player) {
		long owed = owed(player.getUUID());
		long pay = Math.min(owed, ChipCurrency.INSTANCE.balance(player));
		if (pay > 0 && ChipCurrency.INSTANCE.withdraw(player, pay)) {
			return collect(player.getUUID(), pay);
		}
		return owed;
	}

	/** Takes {@code amount} off the debt; returns what is still owed (and clears the loan at zero). */
	public long collect(UUID player, long amount) {
		Loan loan = loans.get(player);
		if (loan == null) {
			return 0;
		}
		long left = Math.max(0, loan.owed() - amount);
		if (left == 0) {
			loans.remove(player);
		} else {
			loans.put(player, new Loan(left, loan.due(), loan.nextVisit()));
		}
		setDirty();
		return left;
	}

	/** A collector was killed: a tenth more. Returns the new debt. */
	public long penalty(UUID player) {
		Loan loan = loans.get(player);
		if (loan == null) {
			return 0;
		}
		long owed = loan.owed() + Math.max(10, loan.owed() / 10);
		loans.put(player, new Loan(owed, loan.due(), loan.nextVisit()));
		setDirty();
		return owed;
	}

	/** Moves the due date (game tests and admins). */
	public void setDue(UUID player, long due) {
		Loan loan = loans.get(player);
		if (loan != null) {
			loans.put(player, new Loan(loan.owed(), due, due));
			setDirty();
		}
	}

	private void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (Map.Entry<UUID, Loan> entry : loans.entrySet()) {
			Loan loan = entry.getValue();
			if (now < loan.due() || now < loan.nextVisit()) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player == null || player.level() != level || !player.isAlive() || player.isCreative() || player.isSpectator()
					|| CasinoRegion.contains(level, player.blockPosition())) {
				continue;
			}
			boolean already = !level.getEntitiesOfClass(DebtCollectorEntity.class, new AABB(player.blockPosition()).inflate(64),
					c -> entry.getKey().equals(c.victim())).isEmpty();
			if (!already) {
				sendCollectors(level, player, loan.owed());
			}
			entry.setValue(new Loan(loan.owed(), loan.due(), now + VISIT_EVERY + level.getRandom().nextInt(3000)));
			setDirty();
		}
	}

	/** Two to four collectors, more for a bigger debt. */
	public static void sendCollectors(ServerLevel level, ServerPlayer player, long owed) {
		int count = 2 + (owed >= 600 ? 1 : 0) + (owed >= 2500 ? 1 : 0);
		for (DebtCollectorEntity collector : pl.pansage.gamblecraft.entity.ThugEntity.send(level, player, ModEntities.DEBT_COLLECTOR, count)) {
			collector.dress();
		}
		player.sendSystemMessage(Component.translatable("gamblecraft.collector.arrived", owed).withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, player.blockPosition(), SoundEvents.PILLAGER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F);
	}
}
