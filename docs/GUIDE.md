# GambleCraft — guide

A casino mod for Minecraft 26.3 (Fabric). Everything is played for in-game items; there is no real money anywhere.
The first start shows an 18+ notice: accept it to play, decline and the game closes. A server refuses every game
to a client that has not accepted it.

## Villages

- **Wannabe dealer** (a stall in villages, or a spawn egg): pick a game, then click any block within reach; he deals
  on it. You bet whatever the village trades in (emeralds, wheat, paper…) and are paid in the same item. His purse is
  limited per day: break him and he is out until tomorrow.
- **Card games:** blackjack, oczko, Texas Hold'em (players against each other, or heads-up against the dealer), five
  card draw, three card poker, baccarat, war, higher/lower. Playing cards and the card deck (a pouch for up to 52
  cards) drop and trade.
- **Texas Hold'em with friends:** one player opens a Hold'em table (a village dealer, or a casino card table); the
  others click the table to sit down (up to three at a village table, five in the casino) and buy in. Two cards each,
  then the flop, the turn and the river, with fold, check, call, raise and all-in between. Each player's cards go to
  that player only: the server never sends anyone else's hole cards, so not even a modified client can peek. They are
  shown at the showdown, except folded hands, which stay hidden for good. Someone sitting alone plays heads-up
  against the dealer.
- **Scratch cards:** bought from many villager professions or lifted from pockets (sneak-use a villager from behind).
  Lay one on a block and scratch it with your own hand. Winnings are paid only by the lottery clerk in the exchange
  centre of the village that issued the card.

## The suit and the casino

- The **tailor** (sewing table) sells the suit; it can also be crafted. The casino door opens only for a full suit,
  and the doorman turns everyone else away.
- Find a casino with `/locate structure gamblecraft:casino` (plains, meadow, savanna, desert). Journeyman cartographers
  sell a Casino Map (16 emeralds and a compass).
- The **cashier** changes minerals into chips (1, 5, 25, 100, 500, 1000) and back (10% house cut). The cashier's VIP
  desk sells the VIP card and redeems pawn tickets.

## Casino games

| Game | How |
|---|---|
| Slot machines | Use to spin, sneak-use to change the bet. A shared progressive jackpot. |
| Roulette | A wheel and three felts put together assemble the table. Click the felt with a chip to bet. |
| Plinko | Use to drop a ball; sneak-use the top for the risk, the bottom for the bet. |
| Wheel of Fortune | Bet on a symbol (1 … 23, Joker) in the 15 s window. |
| Crash | Bet, then cash out before the rocket explodes. |
| Dice | Under 7, exactly 7, over 7, doubles. |
| Card tables | The croupiers play the card games for chips; a VIP high-roller table takes 100–5000. |
| Scratch kiosk | Casino scratch cards for chips; the cashier pays the winners. |

Champagne at the bar and the lucky coin in the off hand give Luck: one per cent more on every casino win.

## Security

Breaking or placing a block inside the casino, or hurting the staff, sets off the alarm: sirens, red lamps, the
doors sealed for a minute, guards out of the security room (two, plus one per further offence, six at most) and a
day's ban for the offender. Anyone out of reach gets the guards' shockwave, which knocks out the blocks they built
under themselves. When the alarm ends, every broken block comes back and built ones vanish. Guards drop their cap
(a diamond helmet's worth, Protection III) and the shockwave pistol. The pickpocket is the exception: catching him
sets off nothing.

## The casino's people

- **Addicted gambler** at the slots: hand him a mineral and he follows you; walk him 48 blocks out and he may quit
  for good and thank you (chips, a lucky coin, a gold watch, a pawn ticket, or a map to his buried savings).
- **Loan shark** (back room): 100, 500 or 2000 chips, a quarter on top, three days. Late, and his debt collectors
  come for it wherever you are; each blow takes chips (or emeralds) off the debt.
- **Bartender and waitress:** champagne, espresso, whisky, mojito.
- **Pickpocket:** stand still too long and your chips get lighter. Hit him to get them back, with a reward.
- **The boss** (VIP room, behind the VIP door): once a day a heads-up Hold'em duel against his 5000 chips. Break him
  for the Golden Signet. Raise a hand to him and his elite guards answer.
- **Muggers:** walk out a big winner and you may be followed. The gang is half again as fast as you, wears your
  armour and carries your weapon, and takes a slice of the winnings with every blow.

## Server settings

`config/gamblecraft.json` (written with the defaults on first start):

| Key | Default | Meaning |
|---|---|---|
| `muggingThreshold` | 1000 | Winnings that may draw muggers |
| `muggingMaxChance` | 0.8 | Highest chance of being followed |
| `muggerSpeed` | 1.5 | Mugger speed, times a zombie's |
| `loanInterestPercent` / `loanTermDays` | 25 / 3 | The loan shark's terms |
| `vipCardPrice` / `vipFreeTurnover` | 5000 / 20000 | VIP card price, and the stakes that earn it free |
| `pickpocketStillSeconds` | 10 | How long standing still invites the pickpocket |
| `alarmLockdownSeconds` / `banDays` / `maxGuards` | 60 / 1 / 6 | The alarm |
| `bossBank` / `bossSmallBlind` | 5000 / 50 | The boss's duel |
| `luckBonusPercent` | 1.0 | Extra on wins under Luck |

The guards' drops are a loot table (`gamblecraft:entities/casino_guard`), so a data pack can change them.

## Sportsbook add-on (gamblecraft-sportsbook)

A separate jar next to GambleCraft (it needs it). It brings **betting terminals** with real fixtures and results:
Ekstraklasa, Premier League, LaLiga, Bundesliga, Serie A, the Champions League, NBA, NHL, ATP and WTA tennis and UFC.

- **Where:** a betting shop in villages (every village type), or craft one (iron, a glass pane, redstone, paper).
  The screen above the counter scrolls the games on now and the next ones with their prices.
- **Betting:** use the terminal, pick a league, click a price to put it on the coupon (again to take it off). One pick
  is a single bet, more make an accumulator (up to 12). Pay with chips, or with minerals at the cashier's rates
  (change comes back in chips). Bets close a minute before the start.
- **The coupon:** its description lists every pick with a mark that updates while you carry it: a clock (waiting), a
  ring (game on, with the score and minute), a tick (won), a cross (lost), a dash (void). One lost pick loses the
  coupon. When every pick has won, the coupon turns into a **green-named pouch of winnings**; use it to collect the
  chips. Postponed or called-off games count at odds 1; a coupon with nothing left pays the stake back. The book is
  kept by the world, so a coupon left in a chest still settles, and a copied pouch pays only once.
- **Prices:** DraftKings' football prices where ESPN shows them; everything else is priced by GambleCraft from the
  teams' form (season records and every result the feeds report, kept by the world, so the Ekstraklasa's prices
  sharpen as the season's results come in).
- **Data sources:** the terminal's **Data sources** button opens tick boxes for each feed and each league, with the
  feed every league is read from right now. Operators (or the owner of a single-player world) can change them:

| Feed | What it gives |
|---|---|
| ESPN (public scoreboards) | Fixtures, live scores and results for every league but the Ekstraklasa; DraftKings' football prices |
| NHL.com (official API) | NHL, as the backup for ESPN |
| OpenLigaDB (community) | Bundesliga, as the backup for ESPN |
| TheSportsDB (free tier, public key) | The Ekstraklasa, and the backup for football, NBA and NHL |

All four are public JSON feeds with no account. A league is read from the first feed that is on and answers.
Switching a feed off never stops coupons already bought from it being settled.

`/sportsbook status` lists where each league comes from; operators also have `/sportsbook refresh` and
`/sportsbook switch <source:espn|league:nba|odds:draftkings> <true|false>`.

`config/gamblecraft-sportsbook.json`:

| Key | Default | Meaning |
|---|---|---|
| `sources`, `leagues`, `draftKingsOdds` | all on | The tick boxes |
| `margin` | 0.07 | The house's cut on its own prices (a coin flip pays 1.86) |
| `minStake` / `maxStake` / `maxPicks` / `maxPayout` | 10 / 10000 / 12 / 1000000 | Coupon limits |
| `refreshMinutes` / `liveRefreshSeconds` | 20 / 90 | How often the feeds are read (live games more often) |
