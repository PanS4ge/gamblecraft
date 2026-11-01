"""Textures, models and data for the village lottery (F4): scratch cards, litter, the kolektura counter, scratch symbols.

  python tools/gen_f4_assets.py
"""
import json
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model, spawn_egg  # noqa: E402

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data")

TIERS = {"bronze": (176, 110, 60), "silver": (170, 180, 190), "gold": (222, 180, 50)}
CREAM = (244, 235, 208)
FOIL = (190, 192, 200)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(c) + ((255,) if len(c) == 3 else ()))


def ticket(tier_color, scratched, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 3, 14, 12], fill=tier_color + (255,))
    d.rectangle([2, 4, 13, 11], fill=CREAM + (255,))
    for i in range(3):
        for j in range(2):
            x0, y0 = 3 + i * 4, 5 + j * 3
            if scratched:
                colors = [(60, 160, 60), (220, 60, 60), (230, 190, 40), (90, 200, 220)]
                d.rectangle([x0, y0, x0 + 2, y0 + 1], fill=(250, 250, 250, 255))
                px(img, x0 + 1, y0, rng.choice(colors))
                if rng.random() < 0.5:
                    px(img, x0 + 2, y0 + 1, FOIL)
            else:
                d.rectangle([x0, y0, x0 + 2, y0 + 1], fill=FOIL + (255,))
                px(img, x0 + rng.randint(0, 2), y0 + rng.randint(0, 1), (225, 228, 235))
    px(img, 13, 4, (255, 240, 150))
    return img


def used_ticket(seed):
    rng = random.Random(seed)
    img = ticket((150, 140, 120), True, seed)
    for _ in range(10):
        px(img, rng.randint(2, 13), rng.randint(4, 11), (200, 195, 180))
    for x in (1, 14):
        for y in range(3, 13, 3):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img


def items():
    for tier, color in TIERS.items():
        for scratched in (False, True):
            name = f"scratch_card_{tier}" + ("_scratched" if scratched else "")
            ticket(color, scratched, sum(map(ord, name))).save(out("textures", "item", f"{name}.png"))
            write_json(generated_model(f"gamblecraft:item/{name}"), "models", "item", f"{name}.json")
        write_json({"model": {
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "on_true": {"type": "minecraft:model", "model": f"gamblecraft:item/scratch_card_{tier}_scratched"},
            "on_false": {"type": "minecraft:model", "model": f"gamblecraft:item/scratch_card_{tier}"},
        }}, "items", f"scratch_card_{tier}.json")
    used_ticket(7).save(out("textures", "item", "used_scratch_card.png"))
    write_json(generated_model("gamblecraft:item/used_scratch_card"), "models", "item", "used_scratch_card.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/used_scratch_card"}}, "items", "used_scratch_card.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/used_scratch_card"}}, "items", "scratch_litter.json")
    spawn_egg("lottery_clerk_spawn_egg", (110, 40, 140), (240, 220, 120), 2)


def litter_and_counter():
    colors = [TIERS["bronze"], TIERS["silver"], (150, 140, 120)]
    for v, color in enumerate(colors):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        base = ticket(color, True, 100 + v).rotate(17 * (v - 1) + 8, resample=Image.NEAREST)
        img.alpha_composite(base)
        img.save(out("textures", "block", f"scratch_litter_{v}.png"))
        write_json({
            "ambientocclusion": False,
            "textures": {"particle": f"gamblecraft:block/scratch_litter_{v}", "ticket": f"gamblecraft:block/scratch_litter_{v}"},
            "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#ticket"},
                "down": {"uv": [0, 16, 16, 0], "texture": "#ticket"}}}],
        }, "models", "block", f"scratch_litter_{v}.json")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for v in range(3):
            entry = {"model": f"gamblecraft:block/scratch_litter_{v}"}
            if y:
                entry["y"] = y
            variants[f"facing={facing},variant={v}"] = entry
    write_json({"variants": variants}, "blockstates", "scratch_litter.json")

    sign = Image.new("RGBA", (16, 16), (150, 20, 30, 255))
    d = ImageDraw.Draw(sign)
    d.rectangle([0, 0, 15, 15], outline=(220, 180, 50, 255))
    d.ellipse([3, 3, 12, 12], fill=(220, 180, 50, 255), outline=(150, 110, 20, 255))
    for cx, cy in ((7, 5), (5, 7), (9, 7), (7, 9)):
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=(40, 140, 50, 255))
    px(sign, 7, 7, (30, 110, 40))
    sign.save(out("textures", "block", "kolektura_sign.png"))
    t = Image.new("RGBA", (16, 16), CREAM + (255,))
    ImageDraw.Draw(t).rectangle([0, 0, 15, 15], outline=TIERS["gold"] + (255,))
    for x in range(3, 13, 3):
        ImageDraw.Draw(t).rectangle([x, 5, x + 1, 10], fill=FOIL + (255,))
    t.save(out("textures", "block", "kolektura_ticket.png"))

    def side(uv=None):
        face = {"texture": "#side"}
        if uv:
            face["uv"] = uv
        return face

    write_json({
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/dark_oak_planks", "side": "minecraft:block/dark_oak_planks",
                     "top": "minecraft:block/green_wool", "trim": "minecraft:block/gold_block",
                     "sign": "gamblecraft:block/kolektura_sign", "ticket": "gamblecraft:block/kolektura_ticket"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 11, 16], "faces": {
                "north": side([0, 5, 16, 16]), "south": side([0, 5, 16, 16]), "east": side([0, 5, 16, 16]),
                "west": side([0, 5, 16, 16]), "up": side(), "down": side()}},
            {"from": [0, 11, 0], "to": [16, 12, 16], "faces": {
                "up": {"texture": "#top"}, "north": {"texture": "#trim", "uv": [0, 0, 16, 1]}, "south": {"texture": "#trim", "uv": [0, 0, 16, 1]},
                "east": {"texture": "#trim", "uv": [0, 0, 16, 1]}, "west": {"texture": "#trim", "uv": [0, 0, 16, 1]}, "down": side()}},
            {"from": [2, 12, 13], "to": [14, 22, 15], "faces": {
                "north": {"texture": "#sign", "uv": [0, 0, 16, 16]}, "south": side(), "east": side([0, 0, 2, 10]),
                "west": side([0, 0, 2, 10]), "up": {"texture": "#trim", "uv": [0, 0, 12, 2]}}},
            {"from": [3, 12, 3], "to": [8, 12.5, 8], "faces": {
                "up": {"texture": "#ticket"}, "north": {"texture": "#ticket", "uv": [0, 0, 5, 1]},
                "south": {"texture": "#ticket", "uv": [0, 0, 5, 1]}}},
            {"from": [9, 12, 4], "to": [13, 13, 9], "rotation": {"angle": 22.5, "axis": "y", "origin": [11, 12, 6]},
             "faces": {"up": {"texture": "#ticket"}, "north": {"texture": "#ticket", "uv": [0, 0, 4, 1]}}},
        ],
    }, "models", "block", "kolektura.json")
    states = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        states[f"facing={f}"] = {"model": "gamblecraft:block/kolektura", "y": y} if y else {"model": "gamblecraft:block/kolektura"}
    write_json({"variants": states}, "blockstates", "kolektura.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:block/kolektura"}}, "items", "kolektura.json")


def symbols():
    def canvas():
        return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    s = {}
    img = canvas()
    d = ImageDraw.Draw(img)
    for cx, cy in ((8, 4), (4, 8), (12, 8), (8, 12)):
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(50, 160, 60, 255), outline=(20, 90, 30, 255))
    d.line([8, 9, 11, 15], fill=(20, 90, 30, 255))
    s["clover"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.arc([2, 1, 14, 15], 160, 380, fill=(140, 140, 150, 255), width=3)
    for p in ((4, 8), (12, 8), (5, 4), (11, 4)):
        px(img, p[0], p[1], (60, 60, 70))
    s["horseshoe"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (13, 6), (14, 12), (2, 12), (3, 6)], fill=(230, 185, 40, 255), outline=(150, 110, 20, 255))
    d.rectangle([7, 12, 9, 14], fill=(150, 110, 20, 255))
    s["bell"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 14, 14], fill=(235, 190, 50, 255), outline=(150, 110, 20, 255))
    d.ellipse([4, 4, 11, 11], outline=(255, 230, 120, 255))
    s["coin"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(8, 0), (10, 6), (15, 6), (11, 10), (13, 15), (8, 12), (3, 15), (5, 10), (0, 6), (6, 6)],
              fill=(255, 220, 60, 255), outline=(190, 140, 20, 255))
    s["star"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(1, 5), (5, 9), (8, 3), (11, 9), (15, 5), (14, 13), (2, 13)], fill=(230, 185, 40, 255), outline=(150, 110, 20, 255))
    for p in ((5, 11), (8, 11), (11, 11)):
        px(img, p[0], p[1], (200, 30, 40))
    s["crown"] = img
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([(4, 2), (12, 2), (15, 6), (8, 15), (1, 6)], fill=(90, 220, 230, 255), outline=(30, 130, 150, 255))
    d.line([1, 6, 15, 6], fill=(170, 245, 250, 255))
    s["diamond"] = img
    for name, image in s.items():
        image.save(out("textures", "gui", "scratch", f"{name}.png"))


def white():
    """Plain white texture: tinted by vertex colour for the scratch card drawn on a block."""
    Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(out("textures", "misc", "white.png"))


def data_json(content, *parts):
    path = os.path.join(DATA, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2)
        f.write("\n")


def data():
    prices = {"bronze": (1, 12), "silver": (3, 8), "gold": (8, 4)}
    for tier, (price, uses) in prices.items():
        data_json({"wants": {"id": "minecraft:emerald", "count": price}, "gives": {"id": f"gamblecraft:scratch_card_{tier}"},
                   "max_uses": uses, "reputation_discount": 0.05, "xp": 2 + price}, "gamblecraft", "villager_trade", "scratch", f"{tier}.json")
    offers = {"farmer": (1, "bronze"), "fisherman": (1, "bronze"), "shepherd": (2, "bronze"), "mason": (2, "bronze"),
              "butcher": (2, "silver"), "fletcher": (2, "silver"), "cleric": (2, "silver"), "toolsmith": (3, "silver"),
              "cartographer": (3, "gold"), "weaponsmith": (4, "gold")}
    # librarian and armorer are left out: the trade_rebalance pack replaces their tags and would drop the cards
    for profession, (level, tier) in offers.items():
        data_json({"replace": False, "values": [f"gamblecraft:scratch/{tier}"]}, "minecraft", "tags", "villager_trade", profession, f"level_{level}.json")
    data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:kolektura"}],
                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
              "gamblecraft", "loot_table", "blocks", "kolektura.json")
    data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:used_scratch_card"}]}]},
              "gamblecraft", "loot_table", "blocks", "scratch_litter.json")
    data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["PEP", "WWW", "WWW"],
               "key": {"P": "minecraft:paper", "E": "minecraft:emerald", "W": "minecraft:dark_oak_planks"},
               "result": {"id": "gamblecraft:kolektura", "count": 1}}, "gamblecraft", "recipe", "kolektura.json")
    data_json({"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": ["gamblecraft:used_scratch_card", "gamblecraft:used_scratch_card", "gamblecraft:used_scratch_card"],
               "result": {"id": "minecraft:paper", "count": 1}}, "gamblecraft", "recipe", "paper_from_used_scratch_cards.json")


if __name__ == "__main__":
    items()
    litter_and_counter()
    symbols()
    white()
    data()
    print("F4 assets written")
