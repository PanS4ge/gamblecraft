"""Textures, models and data for F5: the suit, casino chips, the tailor and the cashier.

  python tools/gen_f5_assets.py

The sewing table and the 3D chip models are made in Blockbench (blockbench/*.js); this script paints the
textures they use.
"""
import json
import math
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model, spawn_egg  # noqa: E402
from gen_entity_textures import Painter, faces, villager_head, SKIN  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")

BLACK = (24, 24, 30)
BLACK_HI = (44, 44, 54)
WHITE = (236, 236, 232)
POPPY = (200, 30, 35)
CHARCOAL = (38, 38, 44)
TRANSPARENT = (0, 0, 0, 0)


class Armor:
    """A 64x32 armour layer painted face by face, like Painter."""

    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 32), TRANSPARENT)
        self.px = self.img.load()
        self.rng = random.Random(seed)

    def fill(self, rect, color, noise=4):
        x, y, w, h = rect
        for i in range(x, x + w):
            for j in range(y, y + h):
                if len(color) > 3 and color[3] == 0:
                    self.px[i, j] = TRANSPARENT
                    continue
                n = self.rng.randint(-noise, noise)
                self.px[i, j] = tuple(max(0, min(255, c + n)) for c in color[:3]) + (255,)

    def dot(self, x, y, color):
        self.px[x, y] = tuple(color[:3]) + (255,)

    def cube(self, u, v, w, h, d, color, noise=4):
        f = faces(u, v, w, h, d)
        for rect in f.values():
            self.fill(rect, color, noise)
        return f


def suit_layers():
    # humanoid: the jacket (body and arms) and the patent shoes (bottom of the legs)
    a = Armor(5)
    body = a.cube(16, 16, 8, 12, 4, BLACK, 3)
    x, y, w, h = body["front"]
    # white shirt in a V, black bow tie, poppy in the left buttonhole, two buttons
    for row in range(6):
        half = max(0, 3 - row // 2)
        for col in range(4 - half, 4 + half):
            a.dot(x + col, y + row, WHITE)
    a.dot(x + 3, y + 1, (10, 10, 12))
    a.dot(x + 4, y + 1, (10, 10, 12))
    a.dot(x + 2, y + 1, (10, 10, 12))
    a.dot(x + 5, y + 1, (10, 10, 12))
    a.dot(x + 6, y + 3, POPPY)
    a.dot(x + 6, y + 2, (230, 60, 60))
    a.dot(x + 5, y + 3, (150, 20, 25))
    a.dot(x + 6, y + 4, (40, 110, 40))
    for row in (7, 9):
        a.dot(x + 3, y + row, (70, 70, 80))
    # lapels a shade lighter
    for row in range(6, 9):
        a.dot(x + 2, y + row - 4, BLACK_HI)
        a.dot(x + 5, y + row - 4, BLACK_HI)
    bx, by, bw, bh = body["back"]
    for row in range(8, 12):
        a.dot(bx + 4, by + row, (12, 12, 16))
    tx, ty, tw, th = body["top"]
    a.fill((tx + 3, ty + 1, 2, 2), WHITE, 0)
    a.fill(body["bottom"], TRANSPARENT, 0)
    arm = a.cube(40, 16, 4, 12, 4, BLACK, 3)
    for name in ("front", "left", "right", "back"):
        ax, ay, aw, ah = arm[name]
        a.fill((ax, ay + 10, aw, 1), WHITE, 2)
        a.fill((ax, ay + 11, aw, 1), TRANSPARENT, 0)
    a.fill(arm["bottom"], TRANSPARENT, 0)
    leg = faces(0, 16, 4, 12, 4)
    for name in ("front", "left", "right", "back"):
        lx, ly, lw, lh = leg[name]
        a.fill((lx, ly + 9, lw, 3), (12, 12, 14), 2)
        a.dot(lx + 1, ly + 9, (90, 90, 100))
    a.fill(leg["bottom"], (8, 8, 10), 1)
    a.img.save(out("textures", "entity", "equipment", "humanoid", "suit.png"))

    # humanoid_leggings: trousers with a crease, and the waistband
    t = Armor(6)
    leg = t.cube(0, 16, 4, 12, 4, CHARCOAL, 3)
    lx, ly, lw, lh = leg["front"]
    for row in range(0, 10):
        t.dot(lx + 1, ly + row, (52, 52, 60))
    t.fill(leg["top"], TRANSPARENT, 0)
    body = faces(16, 16, 8, 12, 4)
    for name in ("front", "left", "right", "back"):
        bx, by, bw, bh = body[name]
        t.fill((bx, by + 10, bw, 2), CHARCOAL, 3)
    bx, by, bw, bh = body["front"]
    t.fill((bx, by + 10, bw, 1), (14, 14, 16), 1)
    t.dot(bx + 4, by + 10, (190, 160, 70))
    t.img.save(out("textures", "entity", "equipment", "humanoid_leggings", "suit.png"))

    path = os.path.join(ROOT, "assets", "gamblecraft", "equipment", "suit.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"layers": {"humanoid": [{"texture": "gamblecraft:suit"}],
                              "humanoid_leggings": [{"texture": "gamblecraft:suit"}]}}, f, indent=2)
        f.write("\n")


def suit_icons():
    j = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(j)
    d.polygon([(4, 1), (11, 1), (15, 4), (14, 14), (11, 14), (11, 6), (4, 6), (4, 14), (1, 14), (0, 4)], fill=BLACK + (255,))
    d.rectangle([4, 2, 11, 14], fill=BLACK + (255,))
    d.polygon([(6, 1), (9, 1), (8, 7), (7, 7)], fill=WHITE + (255,))
    d.point([(7, 2), (8, 2)], fill=(10, 10, 12, 255))
    d.point([(10, 4)], fill=POPPY + (255,))
    d.point([(10, 3)], fill=(230, 60, 60, 255))
    d.point([(7, 9), (7, 11)], fill=(80, 80, 90, 255))
    d.line([(1, 13), (3, 13)], fill=WHITE + (255,))
    d.line([(12, 13), (14, 13)], fill=WHITE + (255,))
    j.save(out("textures", "item", "suit_jacket.png"))
    t = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(t)
    d.rectangle([3, 1, 12, 3], fill=CHARCOAL + (255,))
    d.polygon([(3, 3), (7, 3), (7, 15), (3, 15)], fill=CHARCOAL + (255,))
    d.polygon([(8, 3), (12, 3), (12, 15), (8, 15)], fill=CHARCOAL + (255,))
    d.line([(5, 4), (5, 14)], fill=(60, 60, 68, 255))
    d.line([(10, 4), (10, 14)], fill=(60, 60, 68, 255))
    d.line([(3, 1), (12, 1)], fill=(14, 14, 16, 255))
    d.point([(7, 1)], fill=(190, 160, 70, 255))
    t.save(out("textures", "item", "suit_trousers.png"))
    s = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(s)
    # two patent shoes seen from the side, one behind the other
    for x0, y0, shade in ((5, 5, 30), (1, 9, 14)):
        body = (shade, shade, shade + 4, 255)
        d.rectangle([x0, y0, x0 + 3, y0 + 4], fill=body)
        d.rectangle([x0, y0 + 2, x0 + 9, y0 + 4], fill=body)
        d.point([(x0 + 10, y0 + 3), (x0 + 10, y0 + 4)], fill=body)
        d.line([(x0, y0 + 5), (x0 + 10, y0 + 5)], fill=(60, 40, 30, 255))
        d.line([(x0 + 5, y0 + 2), (x0 + 8, y0 + 2)], fill=(200, 200, 215, 255))
        d.point([(x0 + 1, y0)], fill=(120, 120, 135, 255))
    s.save(out("textures", "item", "suit_shoes.png"))
    for name in ("suit_jacket", "suit_trousers", "suit_shoes"):
        write_json(generated_model(f"gamblecraft:item/{name}"), "models", "item", f"{name}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:item/{name}"}}, "items", f"{name}.json")


CHIP_COLORS = {1: ((236, 236, 236), (40, 90, 200)), 5: ((200, 30, 35), (245, 245, 245)), 25: ((30, 140, 60), (245, 245, 245)),
               100: ((25, 25, 30), (245, 245, 245)), 500: ((110, 40, 150), (245, 210, 80)), 1000: ((235, 150, 30), (40, 40, 45))}


def chip_face(value, size=16):
    base, stripe = CHIP_COLORS[value]
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, size - 2, size - 2], fill=base + (255,), outline=tuple(max(0, c - 60) for c in base) + (255,))
    c = (size - 1) / 2
    for k in range(8):
        a = k * math.pi / 4
        for r in (5.5, 6.2):
            x, y = c + r * math.cos(a), c + r * math.sin(a)
            img.putpixel((int(round(x)), int(round(y))), stripe + (255,))
    d.ellipse([4, 4, size - 5, size - 5], outline=stripe + (255,))
    inner = tuple(min(255, ch + 25) for ch in base) if value != 100 else (60, 60, 70)
    d.ellipse([5, 5, size - 6, size - 6], fill=inner + (255,))
    return img


def chips():
    for value in CHIP_COLORS:
        chip_face(value).save(out("textures", "item", f"chip_{value}.png"))
        write_json(generated_model(f"gamblecraft:item/chip_{value}"), "models", "item", f"chip_{value}.json")
        # flat icon in inventories and in your own hand, the Blockbench disc in the world and other players' hands
        write_json({"model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "fixed", "firstperson_righthand", "firstperson_lefthand"],
                       "model": {"type": "minecraft:model", "model": f"gamblecraft:item/chip_{value}"}}],
            "fallback": {"type": "minecraft:model", "model": f"gamblecraft:item/chip_{value}_3d"},
        }}, "items", f"chip_{value}.json")
        base, stripe = CHIP_COLORS[value]
        edge = Image.new("RGBA", (16, 16), base + (255,))
        for x in range(0, 16, 4):
            for y in range(16):
                edge.putpixel((x + 1, y), stripe + (255,))
        edge.save(out("textures", "item", f"chip_{value}_edge.png"))


def tailor():
    p = Painter(21)
    plum = (88, 32, 70)
    tape = (232, 196, 60)
    robe = faces(0, 38, 8, 20, 6)
    # a plum waistcoat over the upper robe, a yellow tape measure round the neck
    for name in ("front", "left", "right", "back"):
        x, y, w, h = robe[name]
        p.fill((x, y, w, 9), plum, 5)
    x, y, w, h = robe["front"]
    for row in range(0, 12):
        for col in (2, 5):
            p.dot(x + col, y + row, tape)
        if row % 2 == 0:
            p.dot(x + 2, y + row, (40, 30, 10))
            p.dot(x + 5, y + row, (40, 30, 10))
    for row in (3, 5, 7):
        p.dot(x + 3, y + row, (230, 220, 200))
    tx, ty, tw, th = robe["top"]
    p.fill((tx, ty + 2, tw, 2), tape, 3)
    # crossed arms: plum sleeves, a red pin cushion with pins on the forearms
    arms = faces(40, 38, 8, 4, 4)
    for rect in arms.values():
        p.fill(rect, plum, 4)
    ax, ay, aw, ah = arms["front"]
    p.fill((ax + 5, ay + 1, 2, 2), (200, 40, 50), 4)
    p.dot(ax + 5, ay, (220, 220, 230))
    p.dot(ax + 6, ay, (240, 210, 80))
    side = faces(44, 22, 4, 8, 4)
    for rect in side.values():
        p.fill(rect, plum, 4)
    for name in ("profession", "zombie_profession"):
        folder = ("textures", "entity", "villager", "profession") if name == "profession" else ("textures", "entity", "zombie_villager", "profession")
        p.img.save(out(*folder, "tailor.png"))
        meta = out(*folder, "tailor.png.mcmeta")
        with open(meta, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"villager": {"hat": "none"}}, f, indent=4)
            f.write("\n")


def cashier():
    """Same layout as the street dealer: green eyeshade, burgundy waistcoat, black bow tie, sleeve garters."""
    p = Painter(31)
    villager_head(p, hair=(90, 90, 95), eyes=(60, 90, 60))
    hat = faces(32, 0, 8, 10, 8)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = hat[name]
        p.fill((x, y + 1, w, 2), (20, 110, 60), 4)
    brim = faces(28, 38, 8, 1, 4)
    for name, rect in brim.items():
        p.fill(rect, (40, 170, 90) if name != "bottom" else (20, 100, 55), 4)
    tie = p.cube(28, 44, 4, 2, 1, (16, 16, 18), 3)
    body = p.cube(16, 20, 8, 12, 6, (236, 236, 232), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = body[name]
        p.fill((x, y + 10, w, 2), (70, 70, 78), 4)
    vest = p.cube(0, 38, 8, 12, 6, (110, 20, 35), 5)
    x, y, w, h = vest["front"]
    for i in range(y, y + 6):
        for j in range(x + 3, x + 5):
            p.dot(j, i, TRANSPARENT)
    for i in (y + 6, y + 8, y + 10):
        p.dot(x + 3, i, (240, 200, 80))
    p.dot(x + 1, y + 7, (240, 200, 80))
    p.dot(x + 2, y + 8, (240, 200, 80))
    p.fill(vest["top"], TRANSPARENT, 0)
    p.fill(vest["bottom"], TRANSPARENT, 0)
    arm = p.cube(44, 22, 4, 12, 4, (236, 236, 232), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = arm[name]
        p.fill((x, y + 3, w, 1), (30, 140, 70), 3)
        p.fill((x, y + 9, w, 3), SKIN, 4)
    p.fill(arm["bottom"], SKIN, 4)
    for rect in faces(40, 44, 3, 1, 4).values():
        p.fill(rect, TRANSPARENT, 0)
    leg = p.cube(0, 22, 4, 12, 4, (70, 70, 78), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = leg[name]
        p.fill((x, y + 10, w, 2), (18, 16, 16), 3)
    p.fill(leg["bottom"], (18, 16, 16), 3)
    p.save("cashier.png")
    spawn_egg("cashier_spawn_egg", (110, 20, 35), (40, 170, 90), 3)


def sewing_textures():
    """Textures for blockbench/sewing_table: the machine body, its gold trim and a spool of red thread."""
    m = Image.new("RGBA", (16, 16), (22, 22, 26, 255))
    d = ImageDraw.Draw(m)
    rng = random.Random(9)
    for x in range(16):
        for y in range(16):
            n = rng.randint(-4, 4)
            m.putpixel((x, y), (22 + n, 22 + n, 28 + n, 255))
    d.line([(0, 2), (15, 2)], fill=(214, 176, 70, 255))
    d.line([(0, 13), (15, 13)], fill=(214, 176, 70, 255))
    for x in range(2, 14, 3):
        m.putpixel((x, 7), (230, 200, 110, 255))
        m.putpixel((x + 1, 8), (180, 140, 50, 255))
    m.save(out("textures", "block", "sewing_machine.png"))
    s = Image.new("RGBA", (16, 16), (180, 30, 40, 255))
    for y in range(16):
        for x in range(16):
            shade = 160 + (x * 7 + y * 3) % 40 if y % 2 == 0 else 190
            s.putpixel((x, y), (shade, 28, 38, 255))
    ImageDraw.Draw(s).rectangle([0, 0, 15, 1], fill=(150, 110, 70, 255))
    ImageDraw.Draw(s).rectangle([0, 14, 15, 15], fill=(150, 110, 70, 255))
    s.save(out("textures", "block", "thread_spool.png"))
    c = Image.new("RGBA", (16, 16), (60, 110, 70, 255))
    for x in range(16):
        for y in range(16):
            if (x + y) % 4 == 0:
                c.putpixel((x, y), (75, 130, 85, 255))
    ImageDraw.Draw(c).rectangle([0, 0, 15, 15], outline=(220, 200, 150, 255))
    c.save(out("textures", "block", "sewing_cloth.png"))


BB_TEXTURES = {
    "spruce_planks": "minecraft:block/spruce_planks", "stripped_spruce_log": "minecraft:block/stripped_spruce_log",
    "iron_block": "minecraft:block/iron_block", "sewing_machine": "gamblecraft:block/sewing_machine",
    "thread_spool": "gamblecraft:block/thread_spool", "sewing_cloth": "gamblecraft:block/sewing_cloth",
    "chip_100": "face", "chip_100_edge": "edge",
}


def finish_models():
    """Tidies the models Blockbench exported (blockbench/*.js): real texture ids, display transforms, variants."""
    models = os.path.join(ROOT, "assets", "gamblecraft", "models")
    table_path = os.path.join(models, "block", "sewing_table.json")
    with open(table_path, encoding="utf-8") as f:
        table = json.load(f)
    table = {"parent": "minecraft:block/block",
             "textures": {**{k: BB_TEXTURES.get(v, v) for k, v in table["textures"].items()}, "particle": "minecraft:block/spruce_planks"},
             "elements": table["elements"]}
    with open(table_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(table, f, indent=2)
        f.write("\n")
    states = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        states[f"facing={facing}"] = {"model": "gamblecraft:block/sewing_table", "y": y} if y else {"model": "gamblecraft:block/sewing_table"}
    write_json({"variants": states}, "blockstates", "sewing_table.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:block/sewing_table"}}, "items", "sewing_table.json")

    chip_path = os.path.join(models, "item", "chip_3d.json")
    with open(chip_path, encoding="utf-8") as f:
        chip = json.load(f)
    rename = {k: BB_TEXTURES.get(v, v) for k, v in chip["textures"].items()}
    for element in chip["elements"]:
        for face in element["faces"].values():
            name = face["texture"][1:]
            face["texture"] = "#" + rename.get(name, name)
    # display transforms come from Blockbench (blockbench/chip_3d.js, tuned in Display mode)
    chip = {"textures": {"particle": "#face"}, "elements": chip["elements"], "display": chip.get("display", {})}
    with open(chip_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(chip, f, indent=2)
        f.write("\n")
    for value in CHIP_COLORS:
        write_json({"parent": "gamblecraft:item/chip_3d", "textures": {"face": f"gamblecraft:item/chip_{value}",
                    "edge": f"gamblecraft:item/chip_{value}_edge"}}, "models", "item", f"chip_{value}_3d.json")


def data_json(content, *parts):
    path = os.path.join(DATA, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2)
        f.write("\n")


def trade(wants, gives, uses, xp, *parts, wants_count=1, gives_count=1):
    data_json({"wants": {"id": wants, "count": wants_count}, "gives": {"id": gives, "count": gives_count}, "max_uses": uses,
               "reputation_discount": 0.05, "xp": xp}, "gamblecraft", "villager_trade", *parts)


def data():
    # suit recipes (L leather, B black dye, M poppy)
    shaped = {
        "suit_jacket": ["LML", "LBL", "LLL"],
        "suit_trousers": ["LLL", "LBL", "LML"],
        "suit_shoes": ["LBL", "LML"],
    }
    for name, pattern in shaped.items():
        data_json({"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                   "key": {"L": "minecraft:leather", "B": "minecraft:black_dye", "M": "minecraft:poppy"},
                   "result": {"id": f"gamblecraft:{name}", "count": 1}}, "gamblecraft", "recipe", f"{name}.json")
    data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["SIS", "PPP", "P P"],
               "key": {"S": "minecraft:string", "I": "minecraft:iron_ingot", "P": "#minecraft:planks"},
               "result": {"id": "gamblecraft:sewing_table", "count": 1}}, "gamblecraft", "recipe", "sewing_table.json")
    data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:sewing_table"}],
                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
              "gamblecraft", "loot_table", "blocks", "sewing_table.json")
    data_json({"replace": False, "values": ["gamblecraft:sewing_table"]}, "minecraft", "tags", "point_of_interest_type", "acquirable_job_site.json")

    # the tailor: buys cloth and leather, sells the suit piece by piece, dearly
    trade("minecraft:string", "minecraft:emerald", 16, 2, "tailor", "1", "string_emerald.json", wants_count=12)
    trade("minecraft:leather", "minecraft:emerald", 16, 2, "tailor", "1", "leather_emerald.json", wants_count=6)
    trade("minecraft:emerald", "minecraft:bundle", 8, 1, "tailor", "1", "emerald_bundle.json", wants_count=3)
    trade("minecraft:white_wool", "minecraft:emerald", 16, 10, "tailor", "2", "white_wool_emerald.json", wants_count=10)
    trade("minecraft:emerald", "gamblecraft:suit_shoes", 3, 10, "tailor", "2", "emerald_suit_shoes.json", wants_count=24)
    trade("minecraft:poppy", "minecraft:emerald", 16, 20, "tailor", "3", "poppy_emerald.json", wants_count=8)
    trade("minecraft:emerald", "gamblecraft:suit_trousers", 3, 15, "tailor", "3", "emerald_suit_trousers.json", wants_count=32)
    trade("minecraft:black_wool", "minecraft:emerald", 16, 30, "tailor", "4", "black_wool_emerald.json", wants_count=6)
    trade("minecraft:emerald", "gamblecraft:suit_jacket", 3, 20, "tailor", "4", "emerald_suit_jacket.json", wants_count=48)
    trade("minecraft:rabbit_hide", "minecraft:emerald", 12, 30, "tailor", "5", "rabbit_hide_emerald.json", wants_count=4)
    trade("minecraft:emerald", "minecraft:black_dye", 12, 30, "tailor", "5", "emerald_black_dye.json", gives_count=4)
    levels = {1: ["string_emerald", "leather_emerald", "emerald_bundle"], 2: ["white_wool_emerald", "emerald_suit_shoes"],
              3: ["poppy_emerald", "emerald_suit_trousers"], 4: ["black_wool_emerald", "emerald_suit_jacket"],
              5: ["rabbit_hide_emerald", "emerald_black_dye"]}
    for level, names in levels.items():
        data_json({"values": [f"gamblecraft:tailor/{level}/{n}" for n in names]}, "gamblecraft", "tags", "villager_trade", "tailor", f"level_{level}.json")
        # two offers per level; from level 2 on there are only two, so the suit piece is always among them
        data_json({"amount": 2, "random_sequence": f"gamblecraft:trade_set/tailor/level_{level}", "trades": f"#gamblecraft:tailor/level_{level}"},
                  "gamblecraft", "trade_set", "tailor", f"level_{level}.json")


if __name__ == "__main__":
    suit_layers()
    suit_icons()
    chips()
    tailor()
    cashier()
    sewing_textures()
    finish_models()
    data()
    print("F5 assets written")
