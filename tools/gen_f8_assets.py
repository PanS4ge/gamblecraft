"""Textures, data and Blockbench model specs for F8: plinko, wheel of fortune, crash, dice and the scratch kiosk.

  python tools/gen_f8_assets.py          textures, blockstates, items, loot, recipes, Blockbench specs
  python tools/gen_f8_assets.py finish   after blockbench/build_block.js exported the models: fix their texture ids

The block models are built in Blockbench from blockbench/<model>.json by blockbench/build_block.js
(python tools/bb_build.py plinko fortune_wheel dice_table crash_pad scratch_kiosk).
"""
import json
import math
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model  # noqa: E402
from gen_f7_assets import noise, data_json  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
BB = os.path.join(ROOT, "blockbench")
T = (0, 0, 0, 0)
GOLD = (222, 180, 60)
WHEEL = [(58, 110, 200), (47, 160, 82), (232, 160, 32), (208, 48, 48), (138, 58, 192), (240, 208, 64)]


def block_textures():
    front = noise((150, 22, 30), 11, 5)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    for k in range(12):
        a0 = k * 30
        d.pieslice([3, 3, 12, 12], a0, a0 + 30, fill=WHEEL[k % len(WHEEL)] + (255,))
    d.ellipse([6, 6, 9, 9], fill=GOLD + (255,))
    front.save(out("textures", "block", "fortune_front.png"))

    side = noise((52, 54, 60), 12, 4)
    d = ImageDraw.Draw(side)
    for x in range(-16, 16, 4):
        d.polygon([(x, 11), (x + 2, 11), (x + 7, 4), (x + 5, 4)], fill=(240, 200, 30, 255))
    d.line([(0, 3), (15, 3)], fill=(30, 30, 34, 255))
    d.line([(0, 12), (15, 12)], fill=(30, 30, 34, 255))
    for y in (4, 11):
        for x in range(16):
            if side.getpixel((x, y))[0] < 100:
                side.putpixel((x, y), (20, 20, 22, 255))
    side.save(out("textures", "block", "crash_side.png"))
    top = noise((120, 122, 126), 13, 6)
    top.save(out("textures", "block", "crash_top.png"))
    target = noise((34, 34, 38), 14, 3)
    d = ImageDraw.Draw(target)
    d.ellipse([1, 1, 14, 14], outline=(240, 200, 30, 255))
    d.ellipse([4, 4, 11, 11], outline=(240, 200, 30, 255))
    d.rectangle([6, 5, 6, 10], fill=(240, 240, 240, 255))
    d.rectangle([9, 5, 9, 10], fill=(240, 240, 240, 255))
    d.rectangle([7, 7, 8, 8], fill=(240, 240, 240, 255))
    target.save(out("textures", "block", "crash_target.png"))

    purple = noise((58, 24, 92), 15, 5)
    d = ImageDraw.Draw(purple)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    for y in range(2, 15, 3):
        purple.putpixel((7, y), (250, 220, 120, 255))
        purple.putpixel((8, y), (250, 220, 120, 255))
    purple.save(out("textures", "block", "plinko_side.png"))
    tray = noise((24, 24, 30), 16, 3)
    d = ImageDraw.Draw(tray)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    tray.save(out("textures", "block", "plinko_tray.png"))
    board = noise((20, 28, 52), 17, 2)
    board.save(out("textures", "block", "plinko_board.png"))

    kiosk = noise((26, 52, 112), 18, 5)
    d = ImageDraw.Draw(kiosk)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.rectangle([2, 2, 13, 8], fill=(14, 18, 30, 255))
    for x, c in ((3, (196, 120, 60)), (7, (200, 200, 210)), (11, (240, 200, 50))):
        d.rectangle([x, 3, x + 2, 7], fill=c + (255,))
        kiosk.putpixel((x + 1, 5), (150, 150, 150, 255))
    d.rectangle([5, 11, 10, 12], fill=(10, 10, 12, 255))
    d.line([(6, 11), (9, 11)], fill=GOLD + (255,))
    kiosk.save(out("textures", "block", "kiosk_front.png"))
    kside = noise((26, 52, 112), 19, 5)
    d = ImageDraw.Draw(kside)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.line([(0, 8), (15, 8)], fill=GOLD + (255,))
    kside.save(out("textures", "block", "kiosk_side.png"))
    sign = noise((120, 20, 40), 20, 4)
    d = ImageDraw.Draw(sign)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    for x in range(1, 15, 2):
        sign.putpixel((x, 1), (255, 240, 160, 255))
        sign.putpixel((x, 14), (255, 240, 160, 255))
    d.polygon([(8, 3), (9, 7), (13, 7), (10, 9), (11, 13), (8, 10), (5, 13), (6, 9), (3, 7), (7, 7)], fill=(250, 220, 90, 255))
    sign.save(out("textures", "block", "kiosk_sign.png"))


def item_textures():
    wheel = Image.new("RGBA", (16, 16), T)
    d = ImageDraw.Draw(wheel)
    d.rectangle([6, 12, 9, 15], fill=(90, 40, 22, 255))
    d.rectangle([4, 14, 11, 15], fill=(70, 28, 14, 255))
    d.ellipse([1, 0, 14, 13], fill=GOLD + (255,))
    for k in range(10):
        a0 = k * 36
        d.pieslice([2, 1, 13, 12], a0, a0 + 36, fill=WHEEL[k % len(WHEEL)] + (255,))
    d.ellipse([6, 5, 9, 8], fill=GOLD + (255,))
    d.polygon([(7, 0), (9, 0), (8, 2)], fill=(230, 30, 30, 255))
    wheel.save(out("textures", "item", "fortune_wheel.png"))

    plinko = Image.new("RGBA", (16, 16), T)
    d = ImageDraw.Draw(plinko)
    d.rectangle([2, 0, 13, 15], fill=(20, 28, 52, 255), outline=GOLD + (255,))
    d.rectangle([2, 0, 13, 1], fill=(150, 22, 30, 255))
    for row in range(5):
        for j in range(row + 2):
            x = 8 - (row + 1) // 2 * 2 + j * 2 - (1 if row % 2 == 0 else 0)
            if 3 <= x <= 12:
                plinko.putpixel((x, 3 + row * 2), (232, 236, 244, 255))
    for k, c in enumerate([(224, 40, 60), (240, 138, 40), (240, 192, 48), (240, 138, 40), (224, 40, 60)]):
        d.rectangle([3 + k * 2, 13, 4 + k * 2, 14], fill=c + (255,))
    plinko.putpixel((7, 6), (255, 74, 106, 255))
    plinko.putpixel((8, 6), (255, 74, 106, 255))
    plinko.save(out("textures", "item", "plinko.png"))


TEXTURES = {
    "wood": "gamblecraft:block/roulette_wood", "felt": "gamblecraft:block/roulette_felt", "brass": "gamblecraft:block/roulette_brass",
    "fortune_front": "gamblecraft:block/fortune_front",
    "crash_side": "gamblecraft:block/crash_side", "crash_top": "gamblecraft:block/crash_top", "crash_target": "gamblecraft:block/crash_target",
    "iron": "minecraft:block/iron_block", "black": "minecraft:block/black_concrete", "gold": "minecraft:block/gold_block",
    "plinko_side": "gamblecraft:block/plinko_side", "plinko_tray": "gamblecraft:block/plinko_tray", "plinko_board": "gamblecraft:block/plinko_board",
    "marquee": "gamblecraft:block/slot_marquee",
    "kiosk_front": "gamblecraft:block/kiosk_front", "kiosk_side": "gamblecraft:block/kiosk_side", "kiosk_sign": "gamblecraft:block/kiosk_sign",
}
PARTICLES = {
    "plinko": "gamblecraft:block/plinko_side", "fortune_wheel": "gamblecraft:block/roulette_wood", "dice_table": "gamblecraft:block/roulette_felt",
    "crash_pad": "gamblecraft:block/crash_side", "scratch_kiosk": "gamblecraft:block/kiosk_side",
}


def cube(name, frm, to, tex, faces=None, uv=None):
    c = {"name": name, "from": frm, "to": to, "tex": tex, "faces": faces or {}}
    if uv:
        c["uv"] = uv
    return c


def specs():
    plinko = [
        cube("cabinet", [1, 0, 2], [15, 28.5, 15], "plinko_side", {"north": "plinko_board"}),
        cube("tray", [1, 0, 0], [15, 3.5, 2], "plinko_tray"),
        cube("rail_left", [14, 3.5, 0.5], [15, 28.5, 2], "gold"),
        cube("rail_right", [1, 3.5, 0.5], [2, 28.5, 2], "gold"),
        cube("marquee", [1, 28.5, 0], [15, 32, 15], "marquee", {}, {"north": [1, 0, 15, 3.5], "south": [1, 0, 15, 3.5]}),
        cube("foot_left", [1, 0, 13], [3, 1, 15], "black"),
    ]
    wheel = [
        cube("plinth", [2, 0, 2], [14, 4, 14], "wood", {"north": "fortune_front"}),
        cube("trim", [1.5, 4, 1.5], [14.5, 5, 14.5], "brass"),
        cube("column", [5.5, 5, 7], [10.5, 16, 11], "wood"),
        cube("collar", [5, 14.5, 6.5], [11, 16, 11.5], "brass"),
    ]
    dice = [
        cube("top", [0, 12, 0], [16, 14, 16], "wood", {"up": "felt"}),
        cube("apron", [1, 9, 1], [15, 12, 15], "wood"),
        cube("leg_a", [1, 0, 1], [3, 9, 3], "wood"),
        cube("leg_b", [13, 0, 1], [15, 9, 3], "wood"),
        cube("leg_c", [1, 0, 13], [3, 9, 15], "wood"),
        cube("leg_d", [13, 0, 13], [15, 9, 15], "wood"),
        cube("back_rail", [0, 14, 15], [16, 15.5, 16], "brass"),
        cube("rail_left", [15, 14, 8], [16, 15.5, 15], "brass"),
        cube("rail_right", [0, 14, 8], [1, 15.5, 15], "brass"),
        cube("edge", [0, 13.9, 0], [16, 14.1, 0.6], "brass"),
    ]
    crash = [
        cube("base", [0, 0, 0], [16, 4, 16], "crash_side", {"up": "crash_top", "down": "crash_top"}),
        cube("pad", [2, 4, 2], [14, 6, 14], "crash_side", {"up": "crash_target"}),
        cube("pylon_a", [0, 4, 0], [2, 9, 2], "iron"),
        cube("pylon_b", [14, 4, 0], [16, 9, 2], "iron"),
        cube("pylon_c", [0, 4, 14], [2, 9, 16], "iron"),
        cube("pylon_d", [14, 4, 14], [16, 9, 16], "iron"),
    ]
    kiosk = [
        cube("body", [1, 0, 3], [15, 13, 15], "kiosk_side", {"north": "kiosk_front"}),
        cube("counter", [1, 6.5, 1], [15, 7.5, 3], "brass"),
        cube("sign", [1, 13, 1], [15, 16, 15], "kiosk_side", {"north": "kiosk_sign", "south": "kiosk_sign"}),
        cube("plinth", [0.5, 0, 2.5], [15.5, 1, 15.5], "black"),
    ]
    for name, cubes in (("plinko", plinko), ("fortune_wheel", wheel), ("dice_table", dice), ("crash_pad", crash), ("scratch_kiosk", kiosk)):
        path = os.path.join(BB, f"{name}.json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"name": name, "textures": TEXTURES, "cubes": cubes,
                       "export": f"src/main/resources/assets/gamblecraft/models/block/{name}.json"}, f, indent=2)
            f.write("\n")


def states_and_items():
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def variant(model, facing):
        return {"model": model, "y": rot[facing]} if rot[facing] else {"model": model}

    plinko = {}
    for facing in rot:
        plinko[f"facing={facing},half=lower"] = variant("gamblecraft:block/plinko", facing)
        plinko[f"facing={facing},half=upper"] = {"model": "gamblecraft:block/plinko_top"}
    write_json({"variants": plinko}, "blockstates", "plinko.json")
    write_json({"textures": {"particle": PARTICLES["plinko"]}}, "models", "block", "plinko_top.json")
    for name in ("fortune_wheel", "dice_table", "crash_pad", "scratch_kiosk"):
        write_json({"variants": {f"facing={facing}": variant(f"gamblecraft:block/{name}", facing) for facing in rot}}, "blockstates", f"{name}.json")
    for name in ("plinko", "fortune_wheel"):
        write_json(generated_model(f"gamblecraft:item/{name}"), "models", "item", f"{name}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:item/{name}"}}, "items", f"{name}.json")
    for name in ("dice_table", "crash_pad", "scratch_kiosk"):
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:block/{name}"}}, "items", f"{name}.json")


def data():
    data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:plinko",
               "condition": {"type": "minecraft:match_block", "blocks": "gamblecraft:plinko", "state": {"half": "lower"}}}],
               "condition": {"type": "minecraft:survives_explosion"}}]}, "gamblecraft", "loot_table", "blocks", "plinko.json")
    for name in ("fortune_wheel", "dice_table", "crash_pad", "scratch_kiosk"):
        data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"gamblecraft:{name}"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]}, "gamblecraft", "loot_table", "blocks", f"{name}.json")
    recipes = {
        "plinko": (["IGI", "INI", "IRI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass", "N": "minecraft:gold_nugget", "R": "minecraft:redstone"}),
        "fortune_wheel": (["PGP", "GCG", "PIP"], {"P": "#minecraft:planks", "G": "minecraft:gold_ingot", "C": "minecraft:clock", "I": "minecraft:iron_ingot"}),
        "dice_table": (["WWW", "PBP", "P P"], {"W": "minecraft:green_wool", "P": "#minecraft:planks", "B": "minecraft:bone"}),
        "crash_pad": (["RFR", "III"], {"R": "minecraft:redstone", "F": "minecraft:firework_rocket", "I": "minecraft:iron_ingot"}),
        "scratch_kiosk": (["IPI", "IGI", "IRI"], {"I": "minecraft:iron_ingot", "P": "minecraft:paper", "G": "minecraft:glass", "R": "minecraft:redstone"}),
    }
    for name, (pattern, key) in recipes.items():
        data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                   "result": {"id": f"gamblecraft:{name}", "count": 1}}, "gamblecraft", "recipe", f"{name}.json")


def finish():
    """Blockbench names textures after their files; map them back to ids and add the particle texture."""
    by_file = {v.split("/")[-1]: v for v in TEXTURES.values()}
    for name, particle in PARTICLES.items():
        path = os.path.join(RES, "assets", "gamblecraft", "models", "block", f"{name}.json")
        with open(path, encoding="utf-8") as f:
            model = json.load(f)
        textures = {k: by_file.get(v.split("/")[-1].split(":")[-1], v) for k, v in model["textures"].items()}
        textures["particle"] = particle
        tidy = {"parent": "minecraft:block/block", "textures": textures, "elements": model["elements"]}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(tidy, f, indent=2)
            f.write("\n")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "finish":
        finish()
        print("F8 models finished")
    else:
        block_textures()
        item_textures()
        specs()
        states_and_items()
        data()
        print("F8 assets written")
