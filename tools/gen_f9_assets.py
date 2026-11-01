"""Textures, data and model specs for F9: casino security (guards, their cap and shockwave pistol).

  python tools/gen_f9_assets.py          textures, equipment, items, loot, tags, Blockbench specs
  python tools/gen_f9_assets.py finish   after Blockbench exported the item models: texture ids and hand/GUI poses

The guard is built in Blockbench by blockbench/casino_guard.js (python tools/bb_entity.py casino_guard), then
python tools/bbmodel_to_java.py blockbench/casino_guard.bbmodel CasinoGuardModelData. The cap and pistol models
come from blockbench/<name>.json specs (python tools/bb_build.py security_cap shockwave_pistol).
"""
import json
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, spawn_egg  # noqa: E402
from gen_f7_assets import noise, data_json  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
BB = os.path.join(ROOT, "blockbench")
T = (0, 0, 0, 0)
SUIT = (28, 28, 34)
SUIT_HI = (44, 44, 52)
SHIRT = (236, 236, 240)
SKIN = (196, 140, 104)
SKIN_DARK = (164, 112, 82)
HAIR = (34, 26, 22)
TIE = (150, 20, 30)
GOLD = (222, 180, 60)
NAVY = (26, 30, 52)


def faces(u, v, w, h, d):
    """Box-UV rectangles (x0, y0, x1, y1 inclusive) of a cube with its texture at (u, v)."""
    return {
        "top": (u + d, v, u + d + w - 1, v + d - 1),
        "bottom": (u + d + w, v, u + d + 2 * w - 1, v + d - 1),
        "right": (u, v + d, u + d - 1, v + d + h - 1),
        "front": (u + d, v + d, u + d + w - 1, v + d + h - 1),
        "left": (u + d + w, v + d, u + 2 * d + w - 1, v + d + h - 1),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w - 1, v + d + h - 1),
    }


def fill(img, rect, color, seed=0, amount=4):
    rng = random.Random(seed)
    x0, y0, x1, y1 = rect
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            n = rng.randint(-amount, amount)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in color) + (255,))


def guard_texture():
    """128x128 box-UV texture for blockbench/casino_guard.js; the UV offsets there must match these."""
    img = Image.new("RGBA", (128, 128), T)
    d = ImageDraw.Draw(img)
    # head 8x10x8 at (0, 0): skin, short dark hair, a stern mouth
    head = faces(0, 0, 8, 10, 8)
    for i, r in enumerate(head.values()):
        fill(img, r, SKIN, 10 + i, 5)
    fill(img, head["top"], HAIR, 20, 4)
    for side in ("right", "left", "back", "front"):
        x0, y0, x1, y1 = head[side]
        fill(img, (x0, y0, x1, y0 + (1 if side == "front" else 3)), HAIR, 21, 4)
    fx0, fy0, fx1, fy1 = head["front"]
    d.line([(fx0 + 2, fy0 + 7), (fx1 - 2, fy0 + 7)], fill=(110, 60, 50, 255))
    d.line([(fx0 + 1, fy0 + 3), (fx1 - 1, fy0 + 3)], fill=(60, 40, 30, 255))
    # nose 2x4x2 at (32, 0)
    for i, r in enumerate(faces(32, 0, 2, 4, 2).values()):
        fill(img, r, SKIN_DARK, 30 + i, 4)
    # sunglasses 9x2x1 at (40, 0): black with a glint
    glasses = faces(40, 0, 9, 2, 1)
    for r in glasses.values():
        fill(img, r, (12, 12, 16), 40, 2)
    gx0, gy0, _, _ = glasses["front"]
    img.putpixel((gx0 + 1, gy0), (150, 170, 200, 255))
    img.putpixel((gx0 + 6, gy0), (150, 170, 200, 255))
    img.putpixel((gx0 + 4, gy0 + 1), (40, 40, 44, 255))
    # earpiece 1x2x2 at (60, 0) and its coiled wire 1x5x1 at (66, 0)
    for r in faces(60, 0, 1, 2, 2).values():
        fill(img, r, (20, 20, 24), 50, 2)
    for r in faces(66, 0, 1, 5, 1).values():
        fill(img, r, (200, 205, 210), 51, 6)
    # torso 18x12x11 at (0, 40): black jacket, white shirt, lapels, a gold badge
    torso = faces(0, 40, 18, 12, 11)
    for i, r in enumerate(torso.values()):
        fill(img, r, SUIT, 60 + i, 4)
    tx0, ty0, tx1, ty1 = torso["front"]
    cx = (tx0 + tx1) // 2
    for y in range(ty0, ty0 + 8):
        half = max(0, 3 - (y - ty0) // 3)
        for x in range(cx - half, cx + half + 2):
            img.putpixel((x, y), SHIRT + (255,))
    for y in range(ty0, ty0 + 9):
        img.putpixel((cx - 4 + (y - ty0) // 3, y), SUIT_HI + (255,))
        img.putpixel((cx + 5 - (y - ty0) // 3, y), SUIT_HI + (255,))
    for y in (ty0 + 9, ty0 + 11):
        img.putpixel((cx, y), (90, 90, 96, 255))
    d.rectangle([tx0 + 3, ty0 + 3, tx0 + 5, ty0 + 5], fill=GOLD + (255,))
    img.putpixel((tx0 + 4, ty0 + 4), (255, 240, 170, 255))
    d.rectangle([tx1 - 4, ty0 + 3, tx1 - 2, ty0 + 3], fill=SHIRT + (255,))
    bx0, by0, bx1, by1 = torso["back"]
    d.line([((bx0 + bx1) // 2, by0 + 2), ((bx0 + bx1) // 2, by1)], fill=(20, 20, 24, 255))
    # tie 2x9x1 at (60, 40)
    for r in faces(60, 40, 2, 9, 1).values():
        fill(img, r, TIE, 70, 6)
    # waist 9x5x6 at (0, 70): trousers with a belt and buckle
    waist = faces(0, 70, 9, 5, 6)
    for i, r in enumerate(waist.values()):
        fill(img, r, SUIT, 80 + i, 4)
    for side in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = waist[side]
        d.line([(x0, y0 + 1), (x1, y0 + 1)], fill=(50, 34, 24, 255))
    wx0, wy0, wx1, _ = waist["front"]
    img.putpixel(((wx0 + wx1) // 2, wy0 + 1), (200, 200, 210, 255))
    # arm 4x30x6 at (60, 58): sleeve, white cuff, hand
    arm = faces(60, 58, 4, 30, 6)
    for i, r in enumerate(arm.values()):
        fill(img, r, SUIT, 90 + i, 4)
    for side in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = arm[side]
        fill(img, (x0, y1 - 5, x1, y1), SKIN, 95, 5)
        d.line([(x0, y1 - 7), (x1, y1 - 7)], fill=SHIRT + (255,))
        d.line([(x0, y1 - 6), (x1, y1 - 6)], fill=SHIRT + (255,))
    fill(img, arm["bottom"], SKIN, 96, 5)
    # leg 6x16x5 at (90, 0): trousers with a crease, polished shoes
    leg = faces(90, 0, 6, 16, 5)
    for i, r in enumerate(leg.values()):
        fill(img, r, SUIT, 100 + i, 4)
    for side in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = leg[side]
        fill(img, (x0, y1 - 2, x1, y1), (10, 10, 12), 105, 2)
    lx0, ly0, lx1, ly1 = leg["front"]
    d.line([((lx0 + lx1) // 2, ly0), ((lx0 + lx1) // 2, ly1 - 3)], fill=SUIT_HI + (255,))
    img.putpixel((lx0 + 1, ly1 - 2), (90, 90, 100, 255))
    fill(img, leg["bottom"], (10, 10, 12), 106, 2)
    img.save(out("textures", "entity", "casino_guard.png"))


def cap_textures():
    # worn: the humanoid helmet layer (64x32, the head box at (0, 0) inflated)
    worn = Image.new("RGBA", (64, 32), T)
    d = ImageDraw.Draw(worn)
    head = faces(0, 0, 8, 8, 8)
    fill(worn, head["top"], NAVY, 1, 4)
    for side in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = head[side]
        fill(worn, (x0, y0, x1, y0 + 2), NAVY, 2, 4)
        d.line([(x0, y0 + 3), (x1, y0 + 3)], fill=GOLD + (255,))
    fx0, fy0, fx1, _ = head["front"]
    d.rectangle([fx0 + 3, fy0, fx0 + 4, fy0 + 1], fill=GOLD + (255,))
    d.line([(fx0, fy0 + 4), (fx1, fy0 + 4)], fill=(14, 14, 18, 255))
    worn.save(out("textures", "entity", "equipment", "humanoid", "security_cap.png"))
    noise(NAVY, 31, 4).save(out("textures", "item", "security_cap_cloth.png"))
    band = noise(GOLD, 32, 8)
    band.save(out("textures", "item", "security_cap_band.png"))
    badge = Image.new("RGBA", (16, 16), GOLD + (255,))
    bd = ImageDraw.Draw(badge)
    bd.polygon([(8, 1), (14, 4), (13, 11), (8, 15), (3, 11), (2, 4)], fill=(250, 214, 90, 255), outline=(150, 110, 20, 255))
    bd.rectangle([6, 6, 9, 9], fill=(26, 30, 52, 255))
    badge.save(out("textures", "item", "security_cap_badge.png"))
    noise((14, 14, 18), 33, 3).save(out("textures", "item", "security_cap_brim.png"))


def pistol_textures():
    noise((40, 42, 50), 41, 5).save(out("textures", "item", "shockwave_pistol_body.png"))
    grip = noise((26, 24, 24), 42, 4)
    gd = ImageDraw.Draw(grip)
    for y in range(1, 16, 3):
        gd.line([(0, y), (15, y)], fill=(14, 12, 12, 255))
    grip.save(out("textures", "item", "shockwave_pistol_grip.png"))
    coil = noise((40, 200, 210), 43, 12)
    cd = ImageDraw.Draw(coil)
    for x in range(0, 16, 2):
        cd.line([(x, 0), (x, 15)], fill=(150, 250, 255, 255))
    coil.save(out("textures", "item", "shockwave_pistol_coil.png"))
    noise((20, 60, 80), 44, 6).save(out("textures", "item", "shockwave_pistol_muzzle.png"))


TEXTURES = {
    "cloth": "gamblecraft:item/security_cap_cloth", "band": "gamblecraft:item/security_cap_band",
    "badge": "gamblecraft:item/security_cap_badge", "brim": "gamblecraft:item/security_cap_brim",
    "body": "gamblecraft:item/shockwave_pistol_body", "grip": "gamblecraft:item/shockwave_pistol_grip",
    "coil": "gamblecraft:item/shockwave_pistol_coil", "muzzle": "gamblecraft:item/shockwave_pistol_muzzle",
}
PARTICLES = {"security_cap": "gamblecraft:item/security_cap_cloth", "shockwave_pistol": "gamblecraft:item/shockwave_pistol_body"}
DISPLAY = {
    "security_cap": {
        "gui": {"rotation": [25, 210, 0], "translation": [0, 1, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8.5, 0], "scale": [1.15, 1.15, 1.15]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    },
    "shockwave_pistol": {
        "gui": {"rotation": [0, 0, -20], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        # chosen in Blockbench's display preview: barrel forward, grip down
        "thirdperson_righthand": {"rotation": [0, 90, 90], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, -90, -90], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, 90, 0], "translation": [1, 3, -2], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand": {"rotation": [0, -90, 0], "translation": [1, 3, -2], "scale": [0.55, 0.55, 0.55]},
    },
}


def cube(name, frm, to, tex, faces_=None):
    return {"name": name, "from": frm, "to": to, "tex": tex, "faces": faces_ or {}}


def specs():
    cap = [
        cube("crown", [4.5, 6, 4], [11.5, 10.5, 11.5], "cloth"),
        cube("top", [5, 10.5, 4.5], [11, 11, 11], "cloth"),
        cube("band", [4.4, 6.5, 3.9], [11.6, 7.6, 11.6], "band"),
        cube("brim", [4.5, 6, 0.5], [11.5, 6.8, 4], "brim"),
        cube("badge", [7, 8, 3.8], [9, 9.8, 4], "badge"),
    ]
    pistol = [
        cube("barrel", [3, 9, 7], [13.5, 11, 9], "body"),
        cube("slide", [3, 11, 6.8], [12, 12.2, 9.2], "body"),
        cube("grip", [3.2, 4, 7.1], [5.8, 9, 8.9], "grip"),
        cube("trigger_guard", [5.8, 7.6, 7.6], [8, 8.2, 8.4], "body"),
        cube("trigger", [6.4, 8.2, 7.8], [6.9, 9, 8.2], "body"),
        cube("coil_a", [8, 8.6, 6.6], [9, 11.4, 9.4], "coil"),
        cube("coil_b", [10, 8.6, 6.6], [11, 11.4, 9.4], "coil"),
        cube("emitter", [13.5, 8.6, 6.6], [15, 11.4, 9.4], "muzzle", {"east": "coil"}),
    ]
    for name, cubes in (("security_cap", cap), ("shockwave_pistol", pistol)):
        with open(os.path.join(BB, f"{name}.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"name": name, "textures": TEXTURES, "cubes": cubes,
                       "export": f"src/main/resources/assets/gamblecraft/models/item/{name}.json"}, f, indent=2)
            f.write("\n")


def items_and_data():
    write_json({"layers": {"humanoid": [{"texture": "gamblecraft:security_cap"}]}}, "equipment", "security_cap.json")
    for name in ("security_cap", "shockwave_pistol"):
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:item/{name}"}}, "items", f"{name}.json")
    spawn_egg("casino_guard_spawn_egg", (28, 28, 34), (230, 230, 236), 9)
    data_json({"values": ["minecraft:leather", "minecraft:black_wool"]}, "gamblecraft", "tags", "item", "repairs_security_cap.json")
    # the cap takes helmet enchantments, both take Unbreaking and Mending
    for tag in ("head_armor", "enchantable/head_armor", "enchantable/armor", "enchantable/equippable"):
        data_json({"values": ["gamblecraft:security_cap"]}, "minecraft", "tags", "item", *f"{tag}.json".split("/"))
    data_json({"values": ["gamblecraft:security_cap", "gamblecraft:shockwave_pistol"]}, "minecraft", "tags", "item", "enchantable", "durability.json")
    data_json({"type": "minecraft:entity", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:security_cap",
                                  "modifier": [{"type": "minecraft:set_enchantments", "enchantments": {"minecraft:protection": 3}}]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:shockwave_pistol"}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:iron_ingot", "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]}]},
    ], "random_sequence": "gamblecraft:entities/casino_guard"}, "gamblecraft", "loot_table", "entities", "casino_guard.json")


def finish():
    """Blockbench names textures after their files: map them back to ids, add the particle and the poses."""
    by_file = {v.split("/")[-1]: v for v in TEXTURES.values()}
    for name, particle in PARTICLES.items():
        path = os.path.join(RES, "assets", "gamblecraft", "models", "item", f"{name}.json")
        with open(path, encoding="utf-8") as f:
            model = json.load(f)
        textures = {k: by_file.get(v.split("/")[-1].split(":")[-1], v) for k, v in model["textures"].items()}
        textures["particle"] = particle
        tidy = {"textures": textures, "elements": model["elements"], "display": DISPLAY[name]}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(tidy, f, indent=2)
            f.write("\n")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "finish":
        finish()
        print("F9 models finished")
    else:
        guard_texture()
        cap_textures()
        pistol_textures()
        specs()
        items_and_data()
        print("F9 assets written")
