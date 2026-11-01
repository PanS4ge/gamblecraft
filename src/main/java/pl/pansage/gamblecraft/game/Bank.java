package pl.pansage.gamblecraft.game;

/** Who pays the winners: a village dealer's daily purse or the casino's unlimited bank. */
public interface Bank {
	Bank UNLIMITED = new Bank() {
		@Override
		public double available() {
			return Double.MAX_VALUE;
		}

		@Override
		public void settle(double houseNet) {
		}
	};

	/** Emerald value the bank can still lose. */
	double available();

	/** Adds the house's result of a round (positive = house won), in emeralds. */
	void settle(double houseNet);
}
