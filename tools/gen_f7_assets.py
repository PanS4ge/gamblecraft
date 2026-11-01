"""Textures, data and Blockbench model specs for F7: slot machines and roulette.

  python tools/gen_f7_assets.py          textures, blockstates, items, loot, recipes, Blockbench specs
  python tools/gen_f7_assets.py finish   after blockbench/build_block.js exported the models: fix their texture ids

The block models are built in Blockbench from blockbench/<model>.json by blockbench/build_block.js.
"""
import json
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data")
BB = os.path.join(ROOT, "blockbench")
T = (0, 0, 0, 0)
GOLD = (222, 180, 60)


def img():
    return Image.new("RGBA", (16, 16), T)


def symbols():
    s = {}
    i = img()
    d = ImageDraw.Draw(i)
    d.line([(8, 2), (5, 9)], fill=(60, 120, 40, 255), width=1)
    d.line([(8, 2), (11, 8)], fill=(60, 120, 40, 255), width=1)
    d.polygon([(8, 2), (12, 1), (13, 3), (9, 3)], fill=(70, 160, 50, 255))
    d.ellipse([1, 8, 8, 15], fill=(200, 20, 40, 255), outline=(120, 0, 20, 255))
    d.ellipse([8, 7, 15, 14], fill=(220, 30, 50, 255), outline=(120, 0, 20, 255))
    d.point([(3, 10), (10, 9)], fill=(255, 200, 200, 255))
    s["cherry"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.ellipse([1, 3, 15, 13], fill=(250, 220, 40, 255), outline=(190, 150, 10, 255))
    d.point([(0, 8), (15, 8)], fill=(190, 150, 10, 255))
    d.line([(4, 5), (7, 4)], fill=(255, 250, 170, 255))
    s["lemon"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.polygon([(8, 1), (12, 5), (13, 11), (15, 13), (1, 13), (3, 11), (4, 5)], fill=(240, 190, 40, 255), outline=(160, 110, 10, 255))
    d.ellipse([6, 12, 10, 15], fill=(160, 110, 10, 255))
    d.line([(6, 4), (5, 10)], fill=(255, 240, 150, 255))
    s["bell"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.ellipse([2, 3, 14, 15], fill=(240, 200, 40, 255), outline=(170, 120, 10, 255))
    d.line([(8, 3), (8, 0)], fill=(110, 70, 30, 255))
    d.polygon([(8, 2), (12, 0), (13, 2)], fill=(80, 170, 50, 255))
    d.ellipse([4, 5, 7, 8], fill=(255, 250, 180, 255))
    s["apple"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.rectangle([0, 4, 15, 11], fill=(20, 20, 24, 255))
    d.rectangle([0, 4, 15, 11], outline=(240, 240, 240, 255))
    # B A R in 3x5 pixels
    glyphs = {"B": ["##.", "#.#", "##.", "#.#", "##."], "A": [".#.", "#.#", "###", "#.#", "#.#"], "R": ["##.", "#.#", "##.", "#.#", "#.#"]}
    x = 2
    for letter in "BAR":
        for r, row in enumerate(glyphs[letter]):
            for c, ch in enumerate(row):
                if ch == "#":
                    i.putpixel((x + c, 5 + r), (255, 70, 60, 255))
        x += 4
    s["bar"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.polygon([(8, 0), (14, 4), (14, 11), (8, 15), (2, 11), (2, 4)], fill=(40, 190, 90, 255), outline=(10, 110, 50, 255))
    d.polygon([(8, 3), (11, 5), (11, 10), (8, 12), (5, 10), (5, 5)], fill=(90, 230, 130, 255))
    d.point([(6, 5), (7, 4)], fill=(220, 255, 230, 255))
    s["emerald"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.polygon([(4, 2), (12, 2), (15, 6), (8, 15), (1, 6)], fill=(90, 220, 230, 255), outline=(30, 130, 150, 255))
    d.line([(1, 6), (15, 6)], fill=(180, 250, 255, 255))
    d.line([(8, 2), (8, 14)], fill=(140, 235, 245, 255))
    s["diamond"] = i
    i = img()
    d = ImageDraw.Draw(i)
    d.polygon([(2, 1), (14, 1), (14, 4), (9, 15), (5, 15), (10, 4), (2, 4)], fill=(230, 30, 40, 255), outline=(130, 0, 10, 255))
    d.line([(3, 2), (12, 2)], fill=(255, 150, 150, 255))
    s["seven"] = i
    i = img()
    d = ImageDraw.Draw(i)
    rng = random.Random(3)
    for x in range(1, 15):
        for y in range(1, 15):
            g = 120 + rng.randint(-30, 30)
            i.putpixel((x, y), (40, g, 40, 255))
    for x, y, w, h in ((3, 4, 3, 3), (10, 4, 3, 3), (6, 8, 4, 3), (5, 10, 2, 3), (9, 10, 2, 3)):
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=(10, 10, 10, 255))
    s["creeper"] = i
    for name, image in s.items():
        image.save(out("textures", "gui", "slots", f"{name}.png"))


def noise(base, seed, amount=6):
    rng = random.Random(seed)
    i = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            n = rng.randint(-amount, amount)
            i.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    return i


def block_textures():
    red = (170, 20, 30)
    cab = noise(red, 1, 5)
    d = ImageDraw.Draw(cab)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    for y in range(1, 15):
        cab.putpixel((3, y), (205, 60, 70, 255))
    cab.save(out("textures", "block", "slot_cabinet.png"))
    side = noise(red, 2, 5)
    d = ImageDraw.Draw(side)
    d.rectangle([6, 0, 9, 15], fill=(200, 200, 210, 255))
    d.line([(7, 0), (7, 15)], fill=(240, 240, 250, 255))
    side.save(out("textures", "block", "slot_side.png"))
    panel = noise((20, 20, 24), 3, 3)
    d = ImageDraw.Draw(panel)
    for x, c in ((2, (220, 40, 40)), (7, (240, 200, 40)), (12, (40, 200, 70))):
        d.ellipse([x - 1, 5, x + 2, 9], fill=c + (255,), outline=(255, 255, 255, 255))
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    panel.save(out("textures", "block", "slot_panel.png"))
    tray = noise((190, 190, 200), 4, 4)
    d = ImageDraw.Draw(tray)
    d.rectangle([2, 6, 13, 12], fill=(30, 30, 34, 255))
    d.rectangle([2, 6, 13, 12], outline=(240, 240, 250, 255))
    for x in (4, 7, 10):
        d.ellipse([x, 9, x + 2, 11], fill=GOLD + (255,))
    tray.save(out("textures", "block", "slot_tray.png"))
    marquee = noise((90, 10, 20), 5, 4)
    d = ImageDraw.Draw(marquee)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    for x in range(1, 15, 2):
        marquee.putpixel((x, 1), (255, 240, 160, 255))
        marquee.putpixel((x, 14), (255, 240, 160, 255))
    marquee.save(out("textures", "block", "slot_marquee.png"))
    wood = noise((95, 40, 22), 6, 6)
    for y in range(0, 16, 4):
        for x in range(16):
            wood.putpixel((x, y), (70, 28, 14, 255))
    wood.save(out("textures", "block", "roulette_wood.png"))
    felt = noise((24, 105, 50), 7, 5)
    felt.save(out("textures", "block", "roulette_felt.png"))
    brass = noise(GOLD, 8, 10)
    brass.save(out("textures", "block", "roulette_brass.png"))
    icon = img()
    d = ImageDraw.Draw(icon)
    d.rectangle([3, 1, 12, 15], fill=red + (255,), outline=GOLD + (255,))
    d.rectangle([4, 3, 11, 7], fill=(244, 240, 228, 255))
    for x, c in ((5, (230, 30, 40)), (7, (230, 30, 40)), (9, (230, 30, 40))):
        icon.putpixel((x, 5), c + (255,))
    d.rectangle([5, 10, 10, 12], fill=(30, 30, 34, 255))
    d.line([(13, 6), (13, 2)], fill=(200, 200, 210, 255))
    icon.putpixel((13, 1), (230, 30, 40, 255))
    icon.save(out("textures", "item", "slot_machine.png"))


# Blockbench specs: cubes with per-face textures; "#key" refers to TEXTURES below.
TEXTURES = {
    "cabinet": "gamblecraft:block/slot_cabinet", "side": "gamblecraft:block/slot_side", "panel": "gamblecraft:block/slot_panel",
    "tray": "gamblecraft:block/slot_tray", "marquee": "gamblecraft:block/slot_marquee", "gold": "minecraft:block/gold_block",
    "iron": "minecraft:block/iron_block", "black": "minecraft:block/black_concrete",
    "wood": "gamblecraft:block/roulette_wood", "felt": "gamblecraft:block/roulette_felt", "brass": "gamblecraft:block/roulette_brass",
}


def cube(name, frm, to, tex, faces=None):
    return {"name": name, "from": frm, "to": to, "tex": tex, "faces": faces or {}}


def specs():
    slot = [
        cube("cabinet", [1, 0, 1.5], [15, 15, 15], "side", {"north": "cabinet"}),
        cube("tray", [3, 2, 1], [13, 6, 1.5], "tray"),
        cube("ledge", [1, 13, 0], [15, 15, 3], "cabinet", {"up": "panel"}),
        cube("upper", [1, 15, 2], [15, 27.5, 15], "side", {"north": "black"}),
        cube("frame_top", [2, 26, 1.2], [14, 27.5, 2], "gold"),
        cube("frame_bottom", [2, 16.5, 1.2], [14, 18, 2], "gold"),
        cube("frame_left", [13.3, 18, 1.2], [14, 26, 2], "gold"),
        cube("frame_right", [2, 18, 1.2], [2.7, 26, 2], "gold"),
        cube("marquee", [1, 27.5, 1.5], [15, 31.5, 15], "marquee"),
        cube("lever_base", [0, 14, 7], [1, 17, 9], "iron"),
    ]
    wheel = [
        cube("base", [1, 0, 1], [15, 9, 15], "wood"),
        cube("bowl", [0, 9, 0], [16, 13, 16], "wood", {"up": "wood"}),
        cube("rim", [0.5, 13, 0.5], [15.5, 14, 15.5], "brass"),
        cube("foot_n", [5, 0, 0], [11, 2, 1], "brass"),
        cube("foot_s", [5, 0, 15], [11, 2, 16], "brass"),
    ]
    felt = [
        cube("top", [0, 12, 0], [16, 14, 16], "wood", {"up": "felt"}),
        cube("apron", [1, 9, 1], [15, 12, 15], "wood"),
        cube("leg_a", [1, 0, 1], [3, 9, 3], "wood"),
        cube("leg_b", [13, 0, 1], [15, 9, 3], "wood"),
        cube("leg_c", [1, 0, 13], [3, 9, 15], "wood"),
        cube("leg_d", [13, 0, 13], [15, 9, 15], "wood"),
        cube("edge", [0, 13.9, 0], [16, 14.1, 0.6], "brass"),
    ]
    for name, cubes in (("slot_machine", slot), ("roulette_wheel", wheel), ("roulette_felt", felt)):
        path = os.path.join(BB, f"{name}.json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"name": name, "textures": TEXTURES, "cubes": cubes,
                       "export": f"src/main/resources/assets/gamblecraft/models/block/{name}.json"}, f, indent=2)
            f.write("\n")


def states_and_items():
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def variant(model, facing):
        return {"model": model, "y": rot[facing]} if rot[facing] else {"model": model}

    slot = {}
    for facing in rot:
        slot[f"facing={facing},half=lower"] = variant("gamblecraft:block/slot_machine", facing)
        slot[f"facing={facing},half=upper"] = {"model": "gamblecraft:block/slot_machine_top"}
    write_json({"variants": slot}, "blockstates", "slot_machine.json")
    write_json({"textures": {"particle": "gamblecraft:block/slot_cabinet"}}, "models", "block", "slot_machine_top.json")
    wheel = {}
    for facing in rot:
        for assembled in ("false", "true"):
            wheel[f"assembled={assembled},facing={facing}"] = variant("gamblecraft:block/roulette_wheel", facing)
    write_json({"variants": wheel}, "blockstates", "roulette_wheel.json")
    felt = {}
    for facing in rot:
        for part in range(4):
            felt[f"facing={facing},part={part}"] = variant("gamblecraft:block/roulette_felt", facing)
    write_json({"variants": felt}, "blockstates", "roulette_felt.json")
    write_json(generated_model("gamblecraft:item/slot_machine"), "models", "item", "slot_machine.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/slot_machine"}}, "items", "slot_machine.json")
    for name in ("roulette_wheel", "roulette_felt"):
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:block/{name}"}}, "items", f"{name}.json")


def data_json(content, *parts):
    path = os.path.join(DATA, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2)
        f.write("\n")


def data():
    data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "gamblecraft:slot_machine",
               "condition": {"type": "minecraft:match_block", "blocks": "gamblecraft:slot_machine", "state": {"half": "lower"}}}],
               "condition": {"type": "minecraft:survives_explosion"}}]}, "gamblecraft", "loot_table", "blocks", "slot_machine.json")
    for name in ("roulette_wheel", "roulette_felt"):
        data_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"gamblecraft:{name}"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]}, "gamblecraft", "loot_table", "blocks", f"{name}.json")
    data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GRG", "IDI", "IEI"],
               "key": {"G": "minecraft:gold_ingot", "R": "minecraft:redstone", "I": "minecraft:iron_ingot", "D": "minecraft:diamond",
                       "E": "minecraft:emerald"},
               "result": {"id": "gamblecraft:slot_machine", "count": 1}}, "gamblecraft", "recipe", "slot_machine.json")
    data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GIG", "PCP", "PPP"],
               "key": {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot", "C": "minecraft:compass", "P": "#minecraft:planks"},
               "result": {"id": "gamblecraft:roulette_wheel", "count": 1}}, "gamblecraft", "recipe", "roulette_wheel.json")
    data_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["WWW", "P P"],
               "key": {"W": "minecraft:green_wool", "P": "#minecraft:planks"},
               "result": {"id": "gamblecraft:roulette_felt", "count": 2}}, "gamblecraft", "recipe", "roulette_felt.json")


def finish():
    """Blockbench names textures after their files; map them back to ids and add the particle texture."""
    by_file = {v.split("/")[-1]: v for v in TEXTURES.values()}
    for name, particle in (("slot_machine", "gamblecraft:block/slot_cabinet"), ("roulette_wheel", "gamblecraft:block/roulette_wood"),
                           ("roulette_felt", "gamblecraft:block/roulette_felt")):
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
        print("F7 models finished")
    else:
        symbols()
        block_textures()
        specs()
        states_and_items()
        data()
        print("F7 assets written")
