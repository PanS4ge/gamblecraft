"""Generates GambleCraft structure templates (26.3 format: DataVersion 5023, palette entries {id, properties}).

  python tools/gen_structures.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402

DATA_VERSION = 5023
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "gamblecraft", "structure")


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.entities = []

    def set(self, x, y, z, block, props=None, block_nbt=None):
        self.blocks[(x, y, z)] = (block, tuple(sorted((props or {}).items())), block_nbt)

    def entity(self, x, y, z, nbt_data):
        self.entities.append({
            "blockPos": nbt.List([int(x), int(y), int(z)]),
            "pos": nbt.List([float(x), float(y), float(z)]),
            "nbt": nbt_data,
        })

    def save(self, *path):
        palette = []
        index = {}
        blocks = []
        sx, sy, sz = self.size
        for x in range(sx):
            for y in range(sy):
                for z in range(sz):
                    block, props, block_nbt = self.blocks.get((x, y, z), ("minecraft:air", (), None))
                    key = (block, props)
                    if key not in index:
                        index[key] = len(palette)
                        entry = {"id": block}
                        if props:
                            entry["properties"] = dict(props)
                        palette.append(entry)
                    b = {"pos": nbt.List([x, y, z]), "state": index[key]}
                    if block_nbt:
                        b["nbt"] = block_nbt
                    blocks.append(b)
        root = {
            "size": nbt.List([sx, sy, sz]),
            "entities": nbt.List(self.entities, 10),
            "blocks": nbt.List(blocks),
            "palette": nbt.List(palette),
            "DataVersion": DATA_VERSION,
        }
        target = os.path.join(OUT, *path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        nbt.write(target, root)


VILLAGE_STYLES = {
    "plains": dict(floor="minecraft:cobblestone", fence="minecraft:oak_fence", stripes=("minecraft:red_wool", "minecraft:white_wool"),
                   wall="minecraft:oak_planks", post="minecraft:oak_log", roof="minecraft:spruce_planks"),
    "desert": dict(floor="minecraft:smooth_sandstone", fence="minecraft:birch_fence", stripes=("minecraft:orange_wool", "minecraft:white_wool"),
                   wall="minecraft:cut_sandstone", post="minecraft:chiseled_sandstone", roof="minecraft:smooth_sandstone"),
    "savanna": dict(floor="minecraft:acacia_planks", fence="minecraft:acacia_fence", stripes=("minecraft:yellow_wool", "minecraft:red_wool"),
                    wall="minecraft:acacia_planks", post="minecraft:acacia_log", roof="minecraft:terracotta"),
    "snowy": dict(floor="minecraft:spruce_planks", fence="minecraft:spruce_fence", stripes=("minecraft:blue_wool", "minecraft:white_wool"),
                  wall="minecraft:white_terracotta", post="minecraft:stripped_spruce_log", roof="minecraft:spruce_planks"),
    "taiga": dict(floor="minecraft:spruce_planks", fence="minecraft:spruce_fence", stripes=("minecraft:brown_wool", "minecraft:white_wool"),
                  wall="minecraft:spruce_planks", post="minecraft:spruce_log", roof="minecraft:dark_oak_planks"),
}

PANE_NS = {"north": "true", "south": "true", "east": "false", "west": "false", "waterlogged": "false"}
PANE_EW = {"north": "false", "south": "false", "east": "true", "west": "true", "waterlogged": "false"}


def post_props(block):
    return {"axis": "y"} if block.endswith("_log") else None


def entrance(t, x, z, village, style):
    t.set(x, 0, z, "minecraft:jigsaw", {"orientation": "west_up"}, {
        "id": "minecraft:jigsaw",
        "name": "minecraft:building_entrance",
        "target": "minecraft:building_entrance",
        "pool": f"minecraft:village/{village}/streets",
        "joint": "aligned",
        "final_state": style["floor"],
    })


def dealer_stall(village, style):
    """5x4x5 market stall: striped canopy on four posts, a green felt table, the dealer behind it facing the street (west)."""
    t = Template(5, 5, 5)
    for x in range(5):
        for z in range(5):
            t.set(x, 0, z, style["floor"])
            t.set(x, 4, z, style["stripes"][z % 2])
    for x in (0, 4):
        for z in (0, 4):
            for y in (1, 2, 3):
                t.set(x, y, z, style["fence"])
    t.set(2, 1, 2, "minecraft:green_wool")
    t.set(1, 1, 1, "minecraft:spruce_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set(1, 1, 3, "minecraft:spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set(4, 1, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(2, 3, 0, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(2, 3, 4, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    entrance(t, 0, 2, village, style)
    t.entity(3.5, 1.0, 2.5, {"id": "gamblecraft:street_dealer", "Rotation": nbt.List([nbt.Float(90.0), nbt.Float(0.0)]),
                             "PersistenceRequired": nbt.Byte(1)})
    t.save("village", f"dealer_stall_{village}.nbt")


def exchange_centre(village, style):
    """7x6x7 exchange office: door to the street (west), the lottery counter facing it, the clerk behind, tickets on the floor."""
    t = Template(7, 6, 7)
    for x in range(7):
        for z in range(7):
            t.set(x, 0, z, style["floor"])
            t.set(x, 4, z, style["roof"])
    for y in (1, 2, 3):
        for i in range(7):
            for x, z in ((i, 0), (i, 6), (0, i), (6, i)):
                corner = x in (0, 6) and z in (0, 6)
                t.set(x, y, z, style["post"] if corner else style["wall"], post_props(style["post"]) if corner else None)
    for y in (1, 2):
        t.set(0, y, 3, "minecraft:air")
    for z in (1, 5):
        t.set(0, 2, z, "minecraft:glass_pane", PANE_NS)
    for x in (2, 4):
        t.set(x, 2, 0, "minecraft:glass_pane", PANE_EW)
        t.set(x, 2, 6, "minecraft:glass_pane", PANE_EW)
    # striped fascia over the door
    for z in range(7):
        t.set(0, 5, z, style["stripes"][z % 2])
    t.set(0, 3, 3, style["stripes"][0])
    # the counter row: kolektura in the middle, plain counters beside it
    t.set(4, 1, 3, "gamblecraft:kolektura", {"facing": "west"})
    for z in (2, 4):
        t.set(4, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
        t.set(4, 2, z, "minecraft:spruce_trapdoor", {"facing": "west", "half": "bottom", "open": "false", "powered": "false",
                                                    "waterlogged": "false"})
    for z in (1, 5):
        t.set(4, 1, z, style["wall"])
    t.set(5, 1, 1, "minecraft:bookshelf")
    t.set(5, 1, 5, "minecraft:lectern", {"facing": "west", "has_book": "false", "powered": "false"})
    t.set(3, 3, 3, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(1, 1, 1, "minecraft:spruce_stairs", {"facing": "south", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set(1, 1, 5, "minecraft:spruce_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    # what the last customers left behind
    for (x, z, facing, variant) in ((2, 1, "east", 2), (3, 5, "north", 0), (2, 4, "west", 2)):
        t.set(x, 1, z, "gamblecraft:scratch_litter", {"facing": facing, "variant": str(variant)})
    entrance(t, 0, 3, village, style)
    t.entity(5.5, 1.0, 3.5, {"id": "gamblecraft:lottery_clerk", "Rotation": nbt.List([nbt.Float(90.0), nbt.Float(0.0)]),
                             "PersistenceRequired": nbt.Byte(1)})
    t.save("village", f"exchange_{village}.nbt")


def tailor_shop(village, style):
    """7x6x7 tailor's workshop: door to the street (west), a sewing table, the tailor, and a suit on a stand."""
    t = Template(7, 6, 7)
    for x in range(7):
        for z in range(7):
            t.set(x, 0, z, style["floor"])
            t.set(x, 4, z, style["roof"])
    for y in (1, 2, 3):
        for i in range(7):
            for x, z in ((i, 0), (i, 6), (0, i), (6, i)):
                corner = x in (0, 6) and z in (0, 6)
                t.set(x, y, z, style["post"] if corner else style["wall"], post_props(style["post"]) if corner else None)
    for y in (1, 2):
        t.set(0, y, 3, "minecraft:air")
    for z in (1, 2, 4, 5):
        t.set(0, 2, z, "minecraft:glass_pane", PANE_NS)
    for x in range(1, 6):
        for z in range(1, 6):
            t.set(x, 1, z, "minecraft:purple_carpet")
    t.set(4, 1, 3, "gamblecraft:sewing_table", {"facing": "west"})
    t.set(5, 1, 1, "minecraft:white_wool")
    t.set(5, 2, 1, "minecraft:black_wool")
    t.set(5, 1, 5, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(3, 3, 3, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(0, 3, 3, "minecraft:purple_wool")
    entrance(t, 0, 3, village, style)
    villager_type = {"plains": "plains", "desert": "desert", "savanna": "savanna", "snowy": "snow", "taiga": "taiga"}[village]
    t.entity(5.5, 1.0, 3.5, {"id": "minecraft:villager", "Rotation": nbt.List([nbt.Float(90.0), nbt.Float(0.0)]),
                             "VillagerData": {"type": f"minecraft:{villager_type}", "profession": "gamblecraft:tailor", "level": 1}})
    t.entity(1.5, 1.0, 1.5, {"id": "minecraft:armor_stand", "Rotation": nbt.List([nbt.Float(270.0), nbt.Float(0.0)]),
                             "equipment": {"chest": {"id": "gamblecraft:suit_jacket", "count": 1},
                                           "legs": {"id": "gamblecraft:suit_trousers", "count": 1},
                                           "feet": {"id": "gamblecraft:suit_shoes", "count": 1}}})
    t.save("village", f"tailor_{village}.nbt")


if __name__ == "__main__":
    for village, style in VILLAGE_STYLES.items():
        dealer_stall(village, style)
        exchange_centre(village, style)
        tailor_shop(village, style)
    print("structures written to", os.path.normpath(OUT))
