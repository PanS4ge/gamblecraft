"""Textures, models and data for F6: the casino door, the croupier and the doorman.

  python tools/gen_f6_assets.py
"""
import json
import os
import random
import sys
import zipfile
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model, spawn_egg  # noqa: E402
from gen_entity_textures import Painter, faces, villager_head, SKIN  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
CLIENT_JAR_GLOB = os.path.join(ROOT, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft")
LACQUER = (20, 18, 22)
GOLD = (222, 180, 60)
GOLD_DARK = (160, 120, 30)
TRANSPARENT = (0, 0, 0, 0)


def vanilla(path):
    """Reads a file from the Minecraft jars Loom unpacked (client assets or common data)."""
    for folder in os.listdir(CLIENT_JAR_GLOB):
        base = os.path.join(CLIENT_JAR_GLOB, folder)
        for version in os.listdir(base):
            for name in os.listdir(os.path.join(base, version)):
                if name.endswith(".jar"):
                    with zipfile.ZipFile(os.path.join(base, version, name)) as z:
                        if path in z.namelist():
                            return z.read(path).decode("utf-8")
    raise FileNotFoundError(path)


def door_textures():
    rng = random.Random(4)

    def wood(img):
        for x in range(16):
            for y in range(16):
                n = rng.randint(-3, 3)
                img.putpixel((x, y), (LACQUER[0] + n, LACQUER[1] + n, LACQUER[2] + n + (2 if x % 5 == 0 else 0), 255))

    top = Image.new("RGBA", (16, 16))
    wood(top)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.rectangle([3, 3, 12, 15], outline=GOLD_DARK + (255,))
    d.rectangle([4, 4, 11, 14], fill=(150, 165, 175, 255))
    for x in range(4, 12):
        for y in range(4, 15):
            if (x + y) % 3 == 0:
                top.putpixel((x, y), (175, 190, 200, 255))
    d.point([(7, 7), (8, 7), (7, 8), (8, 8)], fill=GOLD + (255,))
    top.save(out("textures", "block", "casino_door_top.png"))
    bottom = Image.new("RGBA", (16, 16))
    wood(bottom)
    d = ImageDraw.Draw(bottom)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.rectangle([3, 0, 12, 9], outline=GOLD_DARK + (255,))
    d.rectangle([1, 12, 14, 14], fill=GOLD + (255,))
    d.line([(1, 13), (14, 13)], fill=(250, 220, 120, 255))
    d.rectangle([12, 1, 13, 3], fill=GOLD + (255,))
    bottom.save(out("textures", "block", "casino_door_bottom.png"))
    icon = Image.new("RGBA", (16, 16), TRANSPARENT)
    icon.alpha_composite(top.resize((8, 8), Image.NEAREST), (4, 0))
    icon.alpha_composite(bottom.resize((8, 8), Image.NEAREST), (4, 8))
    icon.save(out("textures", "item", "casino_door.png"))


def door_models():
    states = json.loads(vanilla("assets/minecraft/blockstates/dark_oak_door.json"))
    text = json.dumps(states).replace("minecraft:block/dark_oak_door", "gamblecraft:block/casino_door")
    write_json(json.loads(text), "blockstates", "casino_door.json")
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        write_json({"parent": f"minecraft:block/door_{part}",
                    "textures": {"bottom": "gamblecraft:block/casino_door_bottom", "top": "gamblecraft:block/casino_door_top"}},
                   "models", "block", f"casino_door_{part}.json")
    write_json(generated_model("gamblecraft:item/casino_door"), "models", "item", "casino_door.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/casino_door"}}, "items", "casino_door.json")
    loot = vanilla("data/minecraft/loot_table/blocks/dark_oak_door.json")
    loot = loot.replace("minecraft:dark_oak_door", "gamblecraft:casino_door").replace("minecraft:blocks/dark_oak_door", "gamblecraft:blocks/casino_door")
    path = os.path.join(DATA, "gamblecraft", "loot_table", "blocks", "casino_door.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(loot)


def clear(p, rect):
    p.fill(rect, TRANSPARENT, 0)


def croupier():
    """Street-dealer layout: slicked black hair, no visor, black waistcoat with gold buttons, red bow tie."""
    p = Painter(41)
    villager_head(p, hair=(18, 16, 18), eyes=(60, 40, 30))
    head = faces(0, 0, 8, 10, 8)
    p.fill(head["top"], (14, 12, 14), 2)
    fx, fy, fw, fh = head["front"]
    p.fill((fx, fy, fw, 2), (18, 16, 18), 2)
    for rect in faces(32, 0, 8, 10, 8).values():
        clear(p, rect)
    for rect in faces(28, 38, 8, 1, 4).values():
        clear(p, rect)
    tie = p.cube(28, 44, 4, 2, 1, (190, 20, 30), 4)
    tx, ty, tw, th = tie["front"]
    p.dot(tx + 1, ty, (120, 10, 15))
    p.dot(tx + 2, ty + 1, (120, 10, 15))
    body = p.cube(16, 20, 8, 12, 6, (240, 240, 236), 3)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = body[name]
        p.fill((x, y + 10, w, 2), (16, 16, 18), 2)
    vest = p.cube(0, 38, 8, 12, 6, (22, 22, 26), 4)
    x, y, w, h = vest["front"]
    for i in range(y, y + 5):
        for j in range(x + 3, x + 5):
            p.dot(j, i, TRANSPARENT)
    for i in (y + 6, y + 8, y + 10):
        p.dot(x + 3, i, GOLD)
    clear(p, vest["top"])
    clear(p, vest["bottom"])
    arm = p.cube(44, 22, 4, 12, 4, (240, 240, 236), 3)
    for name in ("front", "left", "right", "back"):
        ax, ay, aw, ah = arm[name]
        p.fill((ax, ay + 7, aw, 1), GOLD, 2)
        p.fill((ax, ay + 8, aw, 4), SKIN, 4)
    p.fill(arm["bottom"], SKIN, 4)
    deck = p.cube(40, 44, 3, 1, 4, (235, 235, 235), 2)
    p.fill(deck["top"], (25, 60, 140), 5)
    p.fill(deck["bottom"], (25, 60, 140), 5)
    leg = p.cube(0, 22, 4, 12, 4, (16, 16, 18), 3)
    for name in ("front", "left", "right", "back"):
        lx, ly, lw, lh = leg[name]
        p.fill((lx, ly + 10, lw, 2), (6, 6, 8), 2)
    p.save("casino_dealer.png")
    spawn_egg("casino_dealer_spawn_egg", (22, 22, 26), (190, 20, 30), 4)


def doorman():
    """Black suit, white shirt, dark glasses and an earpiece; a shaved head."""
    p = Painter(43)
    villager_head(p, hair=(150, 108, 82), eyes=(30, 30, 30))
    hat = faces(32, 0, 8, 10, 8)
    for rect in hat.values():
        clear(p, rect)
    fx, fy, fw, fh = hat["front"]
    for i in range(fx, fx + fw):
        p.dot(i, fy + 4, (10, 10, 12))
    for i in (fx + 1, fx + 2, fx + 5, fx + 6):
        p.dot(i, fy + 5, (10, 10, 12))
    for side in ("left", "right"):
        sx, sy, sw, sh = hat[side]
        for i in range(sx, sx + sw):
            p.dot(i, sy + 4, (10, 10, 12))
    lx, ly, lw, lh = hat["left"]
    p.dot(lx + 2, ly + 5, (235, 235, 235))
    p.dot(lx + 2, ly + 6, (200, 200, 200))
    p.dot(lx + 2, ly + 7, (200, 200, 200))
    for rect in faces(28, 38, 8, 1, 4).values():
        clear(p, rect)
    p.cube(28, 44, 4, 2, 1, (12, 12, 14), 2)
    body = p.cube(16, 20, 8, 12, 6, (22, 22, 26), 3)
    bx, by, bw, bh = body["front"]
    for row in range(5):
        half = max(0, 2 - row // 2)
        for col in range(4 - half, 4 + half):
            p.dot(bx + col, by + row, (240, 240, 236))
    vest = p.cube(0, 38, 8, 12, 6, (22, 22, 26), 3)
    x, y, w, h = vest["front"]
    for i in range(y, y + 5):
        for j in range(x + 3, x + 5):
            p.dot(j, i, TRANSPARENT)
    clear(p, vest["top"])
    clear(p, vest["bottom"])
    arm = p.cube(44, 22, 4, 12, 4, (22, 22, 26), 3)
    for name in ("front", "left", "right", "back"):
        ax, ay, aw, ah = arm[name]
        p.fill((ax, ay + 9, aw, 1), (240, 240, 236), 2)
        p.fill((ax, ay + 10, aw, 2), (150, 108, 82), 3)
    p.fill(arm["bottom"], (150, 108, 82), 3)
    for rect in faces(40, 44, 3, 1, 4).values():
        clear(p, rect)
    leg = p.cube(0, 22, 4, 12, 4, (22, 22, 26), 3)
    for name in ("front", "left", "right", "back"):
        lx, ly, lw, lh = leg[name]
        p.fill((lx, ly + 10, lw, 2), (6, 6, 8), 2)
    p.save("bouncer.png")
    spawn_egg("bouncer_spawn_egg", (22, 22, 26), (240, 240, 236), 5)


if __name__ == "__main__":
    door_textures()
    door_models()
    croupier()
    doorman()
    print("F6 assets written")
