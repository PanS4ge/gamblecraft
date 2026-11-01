"""The casino: one big structure template (gamblecraft:casino/main) plus its worldgen JSON.

  python tools/gen_casino.py

Layout (template space, the street is at low z and the building at high z):
  plaza    z 0..10   red carpet to the door, lamp posts, fountain, the tailor's boutique (with a suit on show)
  facade   z 11      blackstone and gold, quartz columns, three casino doors, a glowing CASINO sign on the parapet
  lobby    z 12..19  cashier's booth behind bars (left), bar (right), chandelier
  hall     z 21..45  card tables with croupiers (left); slot machines and two roulette tables (right);
                     the arcade down the middle: dice tables, the crash pad under its launch shaft, plinko and the wheel
  security z 47..53  behind the hall (left): the guards' room with its screens; guards turn out from GUARD_POST on an alarm
  back     z 47..53  the loan shark's office (middle) and the VIP room (right: the boss's duel table, his two elite guards,
                     a high-roller blackjack table) behind its VIP door
People: the bartender behind the bar; markers that may put an addicted gambler at the slots, a pickpocket among the
roulette players and a waitress in the hall (gamblecraft:npc_marker, rolled once when the chunk first ticks).
Unlit redstone lamps in the ceilings light up red-orange while the alarm sounds (CasinoSecurity finds them).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from gen_structures import Template  # noqa: E402

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data")

W, H, D = 35, 16, 55
X0, X1, Z0, Z1 = 2, 32, 11, 46          # building walls
SEC_X1, SEC_Z1 = 14, 54                 # the security wing behind the hall: x X0..SEC_X1, z Z1..SEC_Z1
SEC_DOOR_X = (7, 8, 9)
GUARD_POST = (8, 1, 50)                 # must match CasinoRegion.GUARD_POST
CEILING = 8
DOOR_X = (16, 17, 18)
DIVIDER_Z = 20

WALL = "minecraft:polished_blackstone_bricks"
TRIM = "minecraft:gold_block"
FLOOR = "minecraft:dark_oak_planks"
NO_WATER = {"waterlogged": "false"}

# 3x5 (N is 4 wide) pixel letters, read left to right
FONT = {
    "C": ["###", "#..", "#..", "#..", "###"],
    "A": [".#.", "#.#", "###", "#.#", "#.#"],
    "S": ["###", "#..", "###", "..#", "###"],
    "I": ["###", ".#.", ".#.", ".#.", "###"],
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": ["###", "#.#", "#.#", "#.#", "###"],
}


def box(t, x0, y0, z0, x1, y1, z1, block, props=None):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, y, z, block, props)


def lantern(t, x, y, z, hanging=True):
    t.set(x, y, z, "minecraft:lantern", {"hanging": "true" if hanging else "false", **NO_WATER})


def chain(t, x, y, z):
    t.set(x, y, z, "minecraft:iron_chain", {"axis": "y", **NO_WATER})


def bars_along_z(t, x, y, z):
    t.set(x, y, z, "minecraft:iron_bars", {"north": "true", "south": "true", "east": "false", "west": "false", **NO_WATER})


def item(name, count=1):
    return {"id": name, "count": count}


def npc(t, x, z, entity_id, yaw, extra=None):
    data = {"id": entity_id, "Rotation": nbt.List([nbt.Float(float(yaw)), nbt.Float(0.0)]), "PersistenceRequired": nbt.Byte(1)}
    data.update(extra or {})
    t.entity(x, 1.0, z, data)


def plaza(t):
    for x in range(W):
        for z in range(0, Z0):
            t.set(x, 0, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks")
    for z in range(0, Z0):
        for x in DOOR_X:
            t.set(x, 1, z, "minecraft:red_carpet")
        for x in (DOOR_X[0] - 1, DOOR_X[-1] + 1):
            t.set(x, 1, z, "minecraft:yellow_carpet")
    for x, z in ((13, 2), (21, 2), (13, 7), (21, 7)):
        box(t, x, 1, z, x, 3, z, "minecraft:dark_oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", **NO_WATER})
        lantern(t, x, 4, z, hanging=False)
    # fountain
    for x in range(25, 32):
        for z in range(2, 9):
            edge = x in (25, 31) or z in (2, 8)
            t.set(x, 1, z, "minecraft:smooth_quartz" if edge else "minecraft:water", None if edge else {"level": "0"})
            t.set(x, 0, z, "minecraft:smooth_quartz")
    box(t, 28, 1, 5, 28, 2, 5, "minecraft:quartz_pillar", {"axis": "y"})
    t.set(28, 3, 5, "minecraft:sea_lantern")
    for x, z in ((24, 3), (24, 7), (32, 3), (32, 7)):
        t.set(x, 1, z, "minecraft:potted_red_tulip")


def boutique(t):
    """The tailor's shop on the plaza: a suit on a stand in the window, the tailor at his sewing table."""
    x0, x1, z0, z1 = 1, 9, 1, 9
    box(t, x0, 0, z0, x1, 0, z1, "minecraft:dark_oak_planks")
    for y in range(1, 5):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    t.set(x, y, z, "minecraft:stripped_dark_oak_log" if corner else "minecraft:white_terracotta", {"axis": "y"} if corner else None)
    box(t, x0, 5, z0, x1, 5, z1, "minecraft:dark_oak_slab", {"type": "bottom", **NO_WATER})
    # shop window and door on the street side (x1)
    for z in (2, 3, 7, 8):
        for y in (2, 3):
            t.set(x1, y, z, "minecraft:glass_pane", {"north": "true", "south": "true", "east": "false", "west": "false", **NO_WATER})
    for y in (1, 2):
        t.set(x1, y, 5, "minecraft:air")
    t.set(x1, 4, 5, "minecraft:purple_wool")
    t.set(x1, 4, 4, "minecraft:purple_wool")
    t.set(x1, 4, 6, "minecraft:purple_wool")
    box(t, x0 + 1, 1, z0 + 1, x1 - 1, 1, z1 - 1, "minecraft:purple_carpet")
    t.set(3, 1, 5, "gamblecraft:sewing_table", {"facing": "east"})
    t.set(2, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(2, 1, 8, "minecraft:white_wool")
    t.set(2, 2, 8, "minecraft:black_wool")
    lantern(t, 5, 4, 5)
    npc(t, 4.5, 3.5, "minecraft:villager", 270, {"VillagerData": {"type": "minecraft:plains", "profession": "gamblecraft:tailor", "level": 2}})
    t.entity(7.5, 1.0, 2.5, {"id": "minecraft:armor_stand", "Rotation": nbt.List([nbt.Float(270.0), nbt.Float(0.0)]),
                             "ShowArms": nbt.Byte(1),
                             "equipment": {"chest": item("gamblecraft:suit_jacket"), "legs": item("gamblecraft:suit_trousers"),
                                           "feet": item("gamblecraft:suit_shoes")}})


def shell(t):
    box(t, X0, 0, Z0, X1, 0, Z1, FLOOR)
    for y in range(1, CEILING):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x in (X0, X1) or z in (Z0, Z1):
                    t.set(x, y, z, WALL)
    box(t, X0, CEILING, Z0, X1, CEILING, Z1, "minecraft:dark_oak_planks")
    box(t, X0, CEILING + 1, Z0, X1, CEILING + 1, Z1, "minecraft:polished_blackstone")
    for x in range(X0, X1 + 1):
        for z in (Z0, Z1):
            t.set(x, CEILING, z, TRIM)
    for z in range(Z0, Z1 + 1):
        for x in (X0, X1):
            t.set(x, CEILING, z, TRIM)
    # ceiling lights, and alarm lamps between them
    for x in range(X0 + 3, X1, 4):
        for z in range(Z0 + 3, Z1, 4):
            t.set(x, CEILING, z, "minecraft:shroomlight")
    for x in range(X0 + 5, X1, 8):
        for z in range(Z0 + 5, Z1, 8):
            t.set(x, CEILING, z, "minecraft:redstone_lamp", {"lit": "false"})
    # side windows
    for z0 in range(Z0 + 4, Z1 - 3, 8):
        for x in (X0, X1):
            box(t, x, 3, z0, x, 5, z0 + 2, "minecraft:tinted_glass")
    # carpet, gold border round the edge
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            border = x in (X0 + 1, X1 - 1) or z in (Z0 + 1, Z1 - 1)
            t.set(x, 1, z, "minecraft:yellow_carpet" if border else "minecraft:red_carpet")


def facade(t):
    z = Z0
    for x in DOOR_X:
        t.set(x, 1, z, "gamblecraft:casino_door", {"facing": "south", "half": "lower", "hinge": "right" if x == DOOR_X[-1] else "left",
                                                   "open": "false", "powered": "false"})
        t.set(x, 2, z, "gamblecraft:casino_door", {"facing": "south", "half": "upper", "hinge": "right" if x == DOOR_X[-1] else "left",
                                                   "open": "false", "powered": "false"})
    box(t, DOOR_X[0] - 1, 3, z, DOOR_X[-1] + 1, 3, z, TRIM)
    for x in (DOOR_X[0] - 2, DOOR_X[-1] + 2):
        box(t, x, 1, z, x, CEILING - 1, z, "minecraft:quartz_pillar", {"axis": "y"})
        t.set(x, CEILING, z, "minecraft:chiseled_quartz_block")
        t.set(x, 1, z - 1, "minecraft:potted_red_tulip")
    for x0 in (5, 23):
        box(t, x0, 3, z, x0 + 6, 5, z, "minecraft:tinted_glass")
        box(t, x0, 2, z, x0 + 6, 2, z, TRIM)
    lantern(t, DOOR_X[0] - 1, 4, z - 1)
    lantern(t, DOOR_X[-1] + 1, 4, z - 1)
    # the sign: a black parapet with glowing letters framed in gold
    text = "CASINO"
    width = sum(len(FONT[c][0]) for c in text) + len(text) - 1
    left = (X0 + X1) // 2 + width // 2   # the street sees +x on its left
    sx0, sx1 = left - width - 1, left + 1
    for x in range(sx0, sx1 + 1):
        for y in range(CEILING + 1, CEILING + 8):
            frame = x in (sx0, sx1) or y in (CEILING + 1, CEILING + 7)
            t.set(x, y, z, TRIM if frame else "minecraft:black_concrete")
    col = 0
    for c in text:
        rows = FONT[c]
        for r, row in enumerate(rows):
            for k, ch in enumerate(row):
                if ch == "#":
                    t.set(left - col - k, CEILING + 6 - r, z, "minecraft:pearlescent_froglight", {"axis": "y"})
        col += len(rows[0]) + 1


def lobby(t):
    # cashier's booth: counter with bars, a window, the house's money behind
    booth_x = 9
    for z in range(Z0 + 1, DIVIDER_Z):
        t.set(booth_x, 1, z, WALL)
        if z in (15, 16):
            t.set(booth_x, 2, z, "minecraft:air")
            t.set(booth_x, 3, z, "minecraft:air")
        else:
            bars_along_z(t, booth_x, 2, z)
            bars_along_z(t, booth_x, 3, z)
        t.set(booth_x, 4, z, "minecraft:dark_oak_slab", {"type": "bottom", **NO_WATER})
    for x in range(X0 + 1, booth_x):
        box(t, x, 1, DIVIDER_Z - 1, x, CEILING - 1, DIVIDER_Z - 1, "minecraft:dark_oak_planks")
        t.set(x, 1, Z0 + 1, "minecraft:red_carpet")
    box(t, X0 + 1, 1, Z0 + 2, X0 + 1, 1, Z0 + 4, "minecraft:emerald_block")
    box(t, X0 + 1, 2, Z0 + 2, X0 + 1, 2, Z0 + 3, "minecraft:gold_block")
    t.set(X0 + 1, 1, Z0 + 6, "minecraft:chest", {"facing": "east", "type": "single", **NO_WATER})
    lantern(t, 6, 6, 15)
    chain(t, 6, 7, 15)
    npc(t, 7.5, 15.5, "gamblecraft:cashier", 270)
    # the scratch card kiosk by the cashier's window: cards for chips, prizes paid at the window
    t.set(booth_x + 1, 1, 18, "gamblecraft:scratch_kiosk", {"facing": "east"})

    # the bar: counter, stools, barrels and brewing stands behind
    bar_x = 25
    for z in range(Z0 + 2, DIVIDER_Z - 1):
        t.set(bar_x, 1, z, "minecraft:stripped_dark_oak_log", {"axis": "z"})
        t.set(bar_x, 2, z, "minecraft:dark_oak_slab", {"type": "bottom", **NO_WATER})
    for z in (Z0 + 3, Z0 + 5, Z0 + 7):
        t.set(bar_x - 2, 1, z, "minecraft:dark_oak_fence", {"north": "false", "south": "false", "east": "false", "west": "false", **NO_WATER})
    for z in range(Z0 + 2, DIVIDER_Z - 1):
        t.set(X1 - 1, 1, z, "minecraft:barrel", {"facing": "west", "open": "false"})
        if z % 2 == 0:
            t.set(X1 - 1, 2, z, "minecraft:brewing_stand", {"has_bottle_0": "true", "has_bottle_1": "true", "has_bottle_2": "false"})
    lantern(t, 28, 6, 15)
    chain(t, 28, 7, 15)

    # chandelier over the middle of the lobby
    cx, cz = (X0 + X1) // 2, 15
    chain(t, cx, 7, cz)
    t.set(cx, 6, cz, "minecraft:gold_block")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        lantern(t, cx + dx, 5, cz + dz)
        t.set(cx + dx, 6, cz + dz, "minecraft:iron_chain", {"axis": "y", **NO_WATER})

    # divider between lobby and hall, with a wide arch
    for x in range(booth_x + 1, X1):
        for y in range(1, CEILING):
            arch = DOOR_X[0] - 4 <= x <= DOOR_X[-1] + 4 and y <= 4
            if not arch:
                t.set(x, y, DIVIDER_Z, "minecraft:stripped_dark_oak_log" if x % 5 == 0 else "minecraft:dark_oak_planks",
                      {"axis": "y"} if x % 5 == 0 else None)
    box(t, DOOR_X[0] - 4, 5, DIVIDER_Z, DOOR_X[-1] + 4, 5, DIVIDER_Z, TRIM)


GAMES = ("blackjack", "holdem", "baccarat")


def hall(t):
    # card tables along the left wall, a croupier behind each felt
    for i, game in enumerate(GAMES):
        z = 25 + i * 7
        t.set(8, 1, z, "minecraft:green_wool")
        t.set(5, 1, z - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        t.set(5, 1, z + 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        for dz in (-2, 2):
            t.set(8, 1, z + dz, "minecraft:dark_oak_slab", {"type": "bottom", **NO_WATER})
        chain(t, 8, 7, z)
        lantern(t, 8, 6, z)
        npc(t, 7.5, z + 0.5, "gamblecraft:casino_dealer", 270, {"gamblecraft_game": game})
    # the slot machines: two rows facing each other across the purple carpet
    for x in range(21, X1 - 1):
        for z in range(23, 33):
            t.set(x, 1, z, "minecraft:purple_carpet")
        for z in range(35, Z1 - 1):
            t.set(x, 1, z, "minecraft:green_carpet")
    for z in range(24, 32, 2):
        for x, facing in ((22, "east"), (29, "west")):
            t.set(x, 1, z, "gamblecraft:slot_machine", {"facing": facing, "half": "lower"})
            t.set(x, 2, z, "gamblecraft:slot_machine", {"facing": facing, "half": "upper"})
    # two roulette tables on the green carpet, already put together; players stand on their south side
    for z in (37, 41):
        t.set(23, 1, z, "gamblecraft:roulette_wheel", {"facing": "east", "assembled": "true"})
        for part in range(1, 4):
            t.set(23 + part, 1, z, "gamblecraft:roulette_felt", {"facing": "east", "part": str(part)})
        chain(t, 25, 7, z)
        lantern(t, 25, 6, z)
    for x, z in ((20, 23), (20, 32), (20, 35), (20, 44)):
        box(t, x, 1, z, x, 1, z, "minecraft:polished_blackstone_wall",
            {"east": "none", "north": "none", "south": "none", "west": "none", "up": "true", **NO_WATER})
        lantern(t, x, 2, z, hanging=False)


def arcade(t):
    """The middle of the hall: two dice tables, the crash pad with a shaft through the roof, plinko boards either side of the wheel."""
    for x in range(11, 20):
        for z in range(26, Z1):
            t.set(x, 1, z, "minecraft:yellow_carpet" if x in (11, 19) else "minecraft:blue_carpet")
    for x in (13, 17):
        t.set(x, 1, 29, "gamblecraft:dice_table", {"facing": "north"})
        chain(t, x, 7, 29)
        lantern(t, x, 6, 29)
    # the rocket climbs out through a glass-lined shaft in the roof
    cx, cz = 15, 36
    t.set(cx, 1, cz, "gamblecraft:crash_pad", {"facing": "north"})
    for y in range(CEILING, H):
        t.set(cx, y, cz, "minecraft:air")
    for y in range(CEILING, CEILING + 4):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(cx + dx, y, cz + dz, "minecraft:glass")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        t.set(cx + dx, CEILING, cz + dz, TRIM)
    for x in (12, 18):
        lantern(t, x, 2, cz, hanging=False)
        t.set(x, 1, cz, "minecraft:polished_blackstone_wall",
              {"east": "none", "north": "none", "south": "none", "west": "none", "up": "true", **NO_WATER})
    # the wheel of fortune against the back wall, plinko boards either side
    t.set(15, 1, Z1 - 2, "gamblecraft:fortune_wheel", {"facing": "north"})
    for x in (11, 12, 18, 19):
        t.set(x, 1, Z1 - 2, "gamblecraft:plinko", {"facing": "north", "half": "lower"})
        t.set(x, 2, Z1 - 2, "gamblecraft:plinko", {"facing": "north", "half": "upper"})


def security(t):
    """The guards' room behind the hall: screens on the back wall, a desk, lockers, and the post the guards turn out from."""
    box(t, X0, 0, Z1, SEC_X1, 0, SEC_Z1, "minecraft:polished_andesite")
    for y in range(1, CEILING):
        for x in range(X0, SEC_X1 + 1):
            for z in range(Z1, SEC_Z1 + 1):
                if x in (X0, SEC_X1) or z in (Z1, SEC_Z1):
                    t.set(x, y, z, WALL)
                else:
                    t.set(x, y, z, "minecraft:air")
    box(t, X0, CEILING, Z1 + 1, SEC_X1, CEILING, SEC_Z1, "minecraft:dark_oak_planks")
    box(t, X0, CEILING + 1, Z1 + 1, SEC_X1, CEILING + 1, SEC_Z1, "minecraft:polished_blackstone")
    for x in range(X0 + 2, SEC_X1, 3):
        t.set(x, CEILING, Z1 + 4, "minecraft:sea_lantern")
    t.set(8, CEILING, Z1 + 2, "minecraft:redstone_lamp", {"lit": "false"})
    # a doorway wide and tall enough for a guard, an alarm lamp over it
    for x in SEC_DOOR_X:
        for y in range(1, 4):
            t.set(x, y, Z1, "minecraft:air")
    t.set(SEC_DOOR_X[1], 4, Z1, "minecraft:redstone_lamp", {"lit": "false"})
    for x in (SEC_DOOR_X[0] - 1, SEC_DOOR_X[-1] + 1):
        t.set(x, 4, Z1, TRIM)
    # the wall of screens (lit from behind) and the desk in front of it
    for x in range(X0 + 2, SEC_X1 - 1):
        t.set(x, 2, SEC_Z1 - 1, "minecraft:black_concrete")
        for y in (3, 4):
            t.set(x, y, SEC_Z1 - 1, "minecraft:sea_lantern")
            t.set(x, y, SEC_Z1 - 2, "minecraft:light_blue_stained_glass" if (x + y) % 3 else "minecraft:cyan_stained_glass")
        t.set(x, 5, SEC_Z1 - 1, "minecraft:black_concrete")
    for x in range(X0 + 3, SEC_X1 - 2):
        t.set(x, 1, SEC_Z1 - 2, "minecraft:polished_blackstone_slab", {"type": "top", **NO_WATER})
    t.set(X0 + 4, 2, SEC_Z1 - 2, "minecraft:lever", {"face": "floor", "facing": "south", "powered": "false"})
    t.set(SEC_X1 - 4, 2, SEC_Z1 - 2, "minecraft:stone_button", {"face": "floor", "facing": "south", "powered": "false"})
    # lockers along the side wall
    for z in range(Z1 + 2, SEC_Z1 - 2):
        t.set(X0 + 1, 1, z, "minecraft:iron_block")
        t.set(X0 + 1, 2, z, "minecraft:iron_trapdoor", {"facing": "east", "half": "bottom", "open": "true", "powered": "false", **NO_WATER})
    t.set(SEC_X1 - 1, 1, Z1 + 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(SEC_X1 - 1, 1, Z1 + 3, "minecraft:barrel", {"facing": "up", "open": "false"})
    gx, gy, gz = GUARD_POST
    t.set(gx, 0, gz, "minecraft:chiseled_polished_blackstone")


def marker(t, x, z, npc, chance, yaw, under):
    t.set(x, 1, z, "gamblecraft:npc_marker", {"npc": npc},
          {"id": "gamblecraft:npc_marker", "chance": chance, "yaw": nbt.Float(float(yaw)), "replace": under})


def people(t):
    # the bartender behind the lobby bar, facing the counter
    npc(t, 27.5, 15.5, "gamblecraft:bartender", 90)
    # regulars at the slots (they stand where a player would), a pickpocket by the roulette, a waitress doing the rounds
    for x, z, yaw in ((23, 24, 90), (28, 26, 270), (23, 28, 90), (28, 30, 270)):
        marker(t, x, z, "gambler", 60, yaw, "minecraft:purple_carpet")
    marker(t, 21, 39, "pickpocket", 60, 0, "minecraft:green_carpet")
    marker(t, 15, 23, "waitress", 85, 0, "minecraft:red_carpet")


def backrooms(t):
    """Behind the hall, right of the security room: the loan shark's office and the VIP room."""
    x0, x1, z0, z1 = SEC_X1, X1, Z1, SEC_Z1
    divider = 22
    for y in range(0, CEILING + 2):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if y == 0:
                    t.set(x, y, z, FLOOR)
                elif y == CEILING:
                    t.set(x, y, z, TRIM if x in (x0, x1) or z in (z0, z1) else "minecraft:dark_oak_planks")
                elif y == CEILING + 1:
                    t.set(x, y, z, "minecraft:polished_blackstone")
                elif x in (x0, x1, divider) or z in (z0, z1):
                    t.set(x, y, z, WALL)
                else:
                    t.set(x, y, z, "minecraft:air")
    # the loan shark's office: red carpet, a desk with a lamp, bookshelves, a safe
    for x in range(x0 + 1, divider):
        for z in range(z0 + 1, z1):
            t.set(x, 1, z, "minecraft:red_carpet")
    for x in range(17, 20):
        t.set(x, 1, 51, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    lantern(t, 17, 2, 51, hanging=False)
    for z in range(z0 + 1, z1):
        if z != 50:
            t.set(x0 + 1, 1, z, "minecraft:bookshelf")
            t.set(x0 + 1, 2, z, "minecraft:bookshelf")
    t.set(divider - 1, 1, z1 - 1, "minecraft:iron_block")
    t.set(divider - 1, 2, z1 - 1, "minecraft:iron_trapdoor", {"facing": "west", "half": "bottom", "open": "true", "powered": "false", **NO_WATER})
    t.set(18, CEILING, 49, "minecraft:shroomlight")
    t.set(20, CEILING, 52, "minecraft:redstone_lamp", {"lit": "false"})
    npc(t, 18.5, 52.5, "gamblecraft:loan_shark", 180)
    for y, half in ((1, "lower"), (2, "upper")):
        t.set(20, y, z0, "minecraft:dark_oak_door", {"facing": "south", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    # the VIP room: purple and gold, the boss behind his felt, his guards either side, a high-roller table
    for x in range(divider + 1, x1):
        for z in range(z0 + 1, z1):
            border = x in (divider + 1, x1 - 1) or z in (z0 + 1, z1 - 1)
            t.set(x, 1, z, "minecraft:yellow_carpet" if border else "minecraft:purple_carpet")
    for x, z in ((divider + 1, z0 + 1), (x1 - 1, z0 + 1), (divider + 1, z1 - 1), (x1 - 1, z1 - 1)):
        for y in range(1, CEILING):
            t.set(x, y, z, "minecraft:gold_block")
    t.set(27, 1, 51, "minecraft:green_wool")
    for x in (26, 28):
        t.set(x, 1, 51, "minecraft:dark_oak_slab", {"type": "top", **NO_WATER})
    npc(t, 27.5, 52.5, "gamblecraft:casino_boss", 180)
    # clear of the gold pillars and the table (a guard is 1.4 wide)
    for x in (25.0, 30.0):
        npc(t, x, 52.0, "gamblecraft:casino_guard", 180, {"gamblecraft_elite": True})
    t.set(25, 1, 48, "minecraft:green_wool")
    npc(t, 24.5, 48.5, "gamblecraft:casino_dealer", 270, {"gamblecraft_game": "blackjack", "gamblecraft_high_roller": True})
    for x in (29, 30):
        t.set(x, 1, 48, "minecraft:dark_oak_stairs", {"facing": "north", "half": "bottom", "shape": "straight", **NO_WATER})
    cx = 27
    chain(t, cx, CEILING - 1, 50)
    t.set(cx, CEILING - 2, 50, "minecraft:gold_block")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        lantern(t, cx + dx, CEILING - 3, 50 + dz)
        t.set(cx + dx, CEILING - 2, 50 + dz, "minecraft:iron_chain", {"axis": "y", **NO_WATER})
    t.set(30, CEILING, 49, "minecraft:redstone_lamp", {"lit": "false"})
    for y, half in ((1, "lower"), (2, "upper")):
        t.set(27, y, z0, "gamblecraft:vip_door", {"facing": "south", "half": half, "hinge": "left", "open": "false", "powered": "false"})
    t.set(27, 3, z0, TRIM)
    for x in (26, 28):
        t.set(x, 3, z0, "minecraft:purple_stained_glass")


def casino():
    t = Template(W, H, D)
    plaza(t)
    boutique(t)
    shell(t)
    facade(t)
    lobby(t)
    hall(t)
    arcade(t)
    security(t)
    backrooms(t)
    people(t)
    npc(t, DOOR_X[-1] + 1.5, Z0 - 0.5, "gamblecraft:bouncer", 180)
    t.save("casino", "main.nbt")


def data_json(content, *parts):
    path = os.path.join(DATA, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2)
        f.write("\n")


def worldgen():
    data_json({
        "type": "minecraft:jigsaw",
        "biomes": "#gamblecraft:has_structure/casino",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": "gamblecraft:casino/main",
        "step": "surface_structures",
        "terrain_adaptation": "beard_box",
        "use_expansion_hack": False,
    }, "gamblecraft", "worldgen", "structure", "casino.json")
    data_json({"elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": "gamblecraft:casino/main",
                                         "processors": "minecraft:empty", "projection": "rigid"}, "weight": 1}],
               "fallback": "minecraft:empty"}, "gamblecraft", "worldgen", "template_pool", "casino", "main.json")
    data_json({"placement": {"type": "minecraft:random_spread", "salt": 731942851, "separation": 16, "spacing": 40},
               "structures": [{"structure": "gamblecraft:casino", "weight": 1}]},
              "gamblecraft", "worldgen", "structure_set", "casino.json")
    data_json({"values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna", "minecraft:desert"]},
              "gamblecraft", "tags", "worldgen", "biome", "has_structure", "casino.json")
    data_json({"values": ["gamblecraft:casino"]}, "gamblecraft", "tags", "worldgen", "structure", "casino.json")


if __name__ == "__main__":
    casino()
    worldgen()
    print("casino written")
