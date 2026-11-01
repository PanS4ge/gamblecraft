"""Generates GambleCraft textures, item models and item definitions.

Card art comes from the blackjackmod resource pack (88x124 pixel-art cards):
  python tools/gen_assets.py --cards "C:/Users/patry/Desktop/blackjackmod/Blackjack_Poker_ResourcePack/assets/minecraft/optifine/cit/cards"

Outputs (under src/main/resources/assets/gamblecraft):
  textures/card/<code>.png        88x124 faces with transparent corners, drawn on tables and in GUIs
  textures/item/card/<code>.png   128x128 padded icons
  textures/item/card_deck_*.png   deck icons for four fill levels
  models/item/..., items/...      item models and item model definitions
"""
import argparse
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gamblecraft")
SUITS = ["hearts", "diamonds", "clubs", "spades"]
RANKS = ["2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king", "ace"]
CODES = [f"{s}_{r}" for s in SUITS for r in RANKS]


def out(*parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_json(data, *parts):
    with open(out(*parts), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def clear_corners(img):
    """Makes the background colour around the rounded corners transparent (flood fill from each corner)."""
    img = img.convert("RGBA")
    w, h = img.size
    marker = (255, 0, 255, 0)
    for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        if img.getpixel(corner)[3] != 0:
            ImageDraw.floodfill(img, corner, marker, thresh=10)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] == marker:
                px[x, y] = (0, 0, 0, 0)
    return img


def pad_square(img, size=128):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((size - img.width) // 2, (size - img.height) // 2))
    return canvas


def generated_model(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def cards(src):
    cases = []
    for code in CODES + ["card_back"]:
        face = clear_corners(Image.open(os.path.join(src, f"{code}.png")))
        name = "back" if code == "card_back" else code
        face.save(out("textures", "card", f"{name}.png"))
        pad_square(face).save(out("textures", "item", "card", f"{name}.png"))
        write_json(generated_model(f"gamblecraft:item/card/{name}"), "models", "item", "card", f"{name}.json")
        if code != "card_back":
            cases.append({"when": code, "model": {"type": "minecraft:model", "model": f"gamblecraft:item/card/{code}"}})
    write_json({
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": cases,
            "fallback": {"type": "minecraft:model", "model": "gamblecraft:item/card/back"},
        }
    }, "items", "card.json")


def deck(src):
    back = clear_corners(Image.open(os.path.join(src, "card_back.png")))
    levels = {"empty": 0, "low": 1, "half": 4, "full": 8}
    size = 144
    for name, layers in levels.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        top = 2
        if layers == 0:
            # An open, empty card box: just the outline of where cards would sit.
            box = Image.new("RGBA", back.size, (0, 0, 0, 0))
            d = ImageDraw.Draw(box)
            d.rounded_rectangle([0, 0, back.width - 1, back.height - 1], radius=6, outline=(120, 30, 30, 255), width=4)
            d.rounded_rectangle([8, 8, back.width - 9, back.height - 9], radius=4, outline=(70, 15, 15, 160), width=2)
            canvas.alpha_composite(box, ((size - back.width) // 2, top))
        else:
            edge = Image.new("RGBA", back.size, (0, 0, 0, 0))
            ImageDraw.Draw(edge).rounded_rectangle([0, 0, back.width - 1, back.height - 1], radius=6,
                                                    fill=(235, 235, 235, 255), outline=(60, 60, 60, 255))
            x = (size - back.width) // 2
            for i in range(layers, 0, -1):
                canvas.alpha_composite(edge, (x, top + i * 2))
            canvas.alpha_composite(back, (x, top))
        canvas.save(out("textures", "item", f"card_deck_{name}.png"))
        write_json(generated_model(f"gamblecraft:item/card_deck_{name}"), "models", "item", f"card_deck_{name}.json")
    write_json({
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": [
                {"threshold": 0.01, "model": {"type": "minecraft:model", "model": "gamblecraft:item/card_deck_low"}},
                {"threshold": 0.34, "model": {"type": "minecraft:model", "model": "gamblecraft:item/card_deck_half"}},
                {"threshold": 0.67, "model": {"type": "minecraft:model", "model": "gamblecraft:item/card_deck_full"}},
            ],
            "fallback": {"type": "minecraft:model", "model": "gamblecraft:item/card_deck_empty"},
        }
    }, "items", "card_deck.json")


EGG = [
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "...##########...",
    "..############..",
    "..############..",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    ".##############.",
    "..############..",
    "..############..",
    "...##########...",
    ".....######.....",
]


def spawn_egg(name, base, spot, seed):
    import random
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(EGG):
        for x, c in enumerate(row):
            if c == "#":
                shade = 1.0 if x < 9 and y < 10 else 0.85
                px[x, y] = tuple(int(v * shade) for v in base) + (255,)
    for _ in range(9):
        x, y = rng.randint(3, 12), rng.randint(2, 13)
        if EGG[y][x] == "#":
            px[x, y] = spot + (255,)
            if x + 1 < 16 and EGG[y][x + 1] == "#":
                px[x + 1, y] = spot + (255,)
    for y, row in enumerate(EGG):
        for x, c in enumerate(row):
            edge = c == "#" and (x == 0 or row[x - 1] == "." or x == 15 or row[x + 1] == "." or y == 15 or EGG[y + 1][x] == ".")
            if edge:
                px[x, y] = tuple(int(v * 0.6) for v in px[x, y][:3]) + (255,)
    img.save(out("textures", "item", f"{name}.png"))
    write_json(generated_model(f"gamblecraft:item/{name}"), "models", "item", f"{name}.json")
    write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:item/{name}"}}, "items", f"{name}.json")


def icon(src):
    ace = clear_corners(Image.open(os.path.join(src, "spades_ace.png")))
    heart = clear_corners(Image.open(os.path.join(src, "hearts_ace.png")))
    canvas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    canvas.alpha_composite(heart.rotate(12, expand=True, resample=Image.NEAREST), (8, 4))
    canvas.alpha_composite(ace.rotate(-10, expand=True, resample=Image.NEAREST), (30, 2))
    canvas.save(out("icon.png"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cards", required=True, help="folder with <suit>_<rank>.png, card_back.png")
    args = parser.parse_args()
    cards(args.cards)
    deck(args.cards)
    icon(args.cards)
    spawn_egg("street_dealer_spawn_egg", (20, 95, 50), (215, 175, 50), 1)
    print("assets written to", os.path.normpath(ROOT))
