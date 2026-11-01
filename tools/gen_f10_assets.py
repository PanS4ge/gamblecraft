"""Textures, models and data for F10: the casino's people (gambler, loan shark and collectors, bar staff, pickpocket,
boss, muggers), their items, the VIP door and the NPC marker.

  python tools/gen_f10_assets.py
"""
import json
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json, generated_model, spawn_egg  # noqa: E402
from gen_entity_textures import Painter, faces, villager_head, SKIN, SKIN_DARK  # noqa: E402
from gen_f6_assets import vanilla  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
ENTITY = os.path.join(ROOT, "src", "main", "resources", "assets", "gamblecraft", "textures", "entity")
T = (0, 0, 0, 0)
GOLD = (222, 180, 60)
SIDES = ("front", "left", "right", "back")


# ---------------------------------------------------------------- villager-shaped people (blockbench/street_dealer.js layout)

def person(seed, hair, eyes, shirt, trousers, shoes=(20, 18, 16), vest=None, vest_open=True, tie=None, sleeves=None, cuffs=True):
    """A casino person on the dealer model: head, shirt, optional waistcoat/jacket overlay, tie, sleeves and hands, legs."""
    p = Painter(seed)
    villager_head(p, hair=hair, eyes=eyes)
    if tie:
        p.cube(28, 44, 4, 2, 1, tie, 4)
    body = p.cube(16, 20, 8, 12, 6, shirt, 4)
    for name in SIDES:
        x, y, w, h = body[name]
        p.fill((x, y + 10, w, 2), trousers, 3)
    if vest:
        v = p.cube(0, 38, 8, 12, 6, vest, 4)
        if vest_open:
            x, y, w, h = v["front"]
            for i in range(y, y + 6):
                for j in range(x + 3, x + 5):
                    p.dot(j, i, T)
        p.fill(v["top"], T, 0)
        p.fill(v["bottom"], T, 0)
    arm = p.cube(44, 22, 4, 12, 4, sleeves or shirt, 4)
    for name in SIDES:
        x, y, w, h = arm[name]
        if cuffs:
            p.fill((x, y + 8, w, 1), (236, 236, 232), 2)
        p.fill((x, y + 9, w, 3), SKIN, 4)
    p.fill(arm["bottom"], SKIN, 4)
    leg = p.cube(0, 22, 4, 12, 4, trousers, 4)
    for name in SIDES:
        x, y, w, h = leg[name]
        p.fill((x, y + 10, w, 2), shoes, 3)
    p.fill(leg["bottom"], shoes, 3)
    return p


def cigar(p):
    c = p.cube(54, 52, 1, 1, 3, (110, 70, 40), 6)
    x, y, w, h = c["front"]
    p.dot(x, y, (240, 120, 40))


def tray(p):
    t = p.cube(28, 56, 6, 1, 6, (200, 200, 210), 6)
    p.fill(t["top"], (220, 222, 230), 4)
    g = p.cube(60, 40, 1, 2, 1, (240, 220, 140), 6)


def npcs():
    # the addicted gambler: crumpled brown suit, loosened tie, bloodshot eyes, stubble
    p = person(101, hair=(60, 50, 40), eyes=(170, 40, 40), shirt=(200, 196, 180), trousers=(90, 70, 50), vest=(110, 85, 60),
               tie=(120, 40, 40), sleeves=(110, 85, 60))
    head = faces(0, 0, 8, 10, 8)
    fx, fy, fw, fh = head["front"]
    for i in range(fx + 1, fx + 7):
        p.dot(i, fy + 7, (120, 95, 80))
    p.save("addicted_gambler.png")

    # the loan shark: pinstripe suit, slicked hair, gold chain, a cigar
    p = person(102, hair=(16, 14, 14), eyes=(50, 40, 30), shirt=(30, 30, 36), trousers=(40, 40, 48), vest=(44, 44, 54), vest_open=True,
               sleeves=(44, 44, 54))
    v = faces(0, 38, 8, 12, 6)
    for name in SIDES:
        x, y, w, h = v[name]
        for j in range(x, x + w, 2):
            for i in range(y, y + h):
                if p.px[j, i][3]:
                    p.dot(j, i, (90, 90, 104))
    x, y, w, h = faces(16, 20, 8, 12, 6)["front"]
    for j in range(x + 2, x + 6):
        p.dot(j, y + 3, GOLD)
    p.dot(x + 3, y + 4, GOLD)
    p.dot(x + 4, y + 4, GOLD)
    cigar(p)
    p.save("loan_shark.png")

    # the bartender: white shirt, black waistcoat, red bow tie, rolled sleeves
    p = person(103, hair=(110, 70, 40), eyes=(40, 90, 120), shirt=(238, 238, 234), trousers=(28, 28, 32), vest=(26, 26, 30), tie=(180, 20, 30))
    p.save("bartender.png")

    # the waitress: black dress, white apron, a silver tray with a glass
    p = person(104, hair=(150, 60, 30), eyes=(60, 120, 60), shirt=(26, 26, 30), trousers=(26, 26, 30), shoes=(150, 20, 30),
               sleeves=(26, 26, 30), cuffs=False)
    x, y, w, h = faces(16, 20, 8, 12, 6)["front"]
    p.fill((x + 1, y + 5, w - 2, 7), (240, 240, 236), 3)
    head = faces(0, 0, 8, 10, 8)
    for name in ("back", "left", "right"):
        hx, hy, hw, hh = head[name]
        p.fill((hx, hy, hw, 6), (150, 60, 30), 5)
    tray(p)
    p.save("waitress.png")

    # the pickpocket: grey flat cap and coat, shifty look
    p = person(105, hair=(80, 80, 84), eyes=(30, 30, 30), shirt=(110, 110, 116), trousers=(60, 60, 66), vest=(90, 90, 96), vest_open=False,
               sleeves=(90, 90, 96))
    hat = faces(32, 0, 8, 10, 8)
    p.fill(hat["top"], (70, 70, 76), 4)
    for name in SIDES:
        x, y, w, h = hat[name]
        p.fill((x, y, w, 2), (70, 70, 76), 4)
    brim = faces(28, 38, 8, 1, 4)
    for rect in brim.values():
        p.fill(rect, (60, 60, 66), 4)
    p.save("pickpocket.png")

    # the boss: white suit, black shirt, gold chain and rings, cigar
    p = person(106, hair=(200, 200, 205), eyes=(40, 40, 50), shirt=(24, 24, 28), trousers=(236, 234, 228), shoes=(240, 240, 240),
               vest=(236, 234, 228), sleeves=(236, 234, 228))
    x, y, w, h = faces(16, 20, 8, 12, 6)["front"]
    for j in range(x + 2, x + 6):
        p.dot(j, y + 2, GOLD)
    p.dot(x + 4, y + 3, GOLD)
    deck = p.cube(40, 44, 3, 1, 4, (235, 235, 235), 2)
    p.fill(deck["top"], (20, 20, 24), 4)
    cigar(p)
    p.save("casino_boss.png")


# ---------------------------------------------------------------- thugs (player skin layout, 64x64)

def skin(seed, face, hair, top, sleeves, legs, shoes, mask=None):
    p = Painter(seed)
    head = p.cube(0, 0, 8, 8, 8, face, 4)
    p.fill(head["top"], hair, 4)
    for name in ("back", "left", "right"):
        x, y, w, h = head[name]
        p.fill((x, y, w, 3), hair, 4)
    fx, fy, fw, fh = head["front"]
    p.fill((fx, fy, fw, 1), hair, 3)
    p.dot(fx + 1, fy + 4, (255, 255, 255))
    p.dot(fx + 2, fy + 4, (30, 30, 30))
    p.dot(fx + 5, fy + 4, (30, 30, 30))
    p.dot(fx + 6, fy + 4, (255, 255, 255))
    for i in range(fx + 3, fx + 5):
        p.dot(i, fy + 6, SKIN_DARK)
    if mask:
        # a balaclava over the whole head, eye slit open
        for rect in head.values():
            p.fill(rect, mask, 4)
        for i in range(fx + 1, fx + 7):
            p.dot(i, fy + 4, face)
        p.dot(fx + 2, fy + 4, (30, 30, 30))
        p.dot(fx + 5, fy + 4, (30, 30, 30))
    for u, v in ((16, 16),):
        p.cube(u, v, 8, 12, 4, top, 4)
    for u, v in ((40, 16), (32, 48)):
        a = p.cube(u, v, 4, 12, 4, sleeves, 4)
        for name in SIDES:
            x, y, w, h = a[name]
            p.fill((x, y + 10, w, 2), SKIN if not mask else (30, 30, 34), 4)
        p.fill(a["bottom"], SKIN if not mask else (30, 30, 34), 4)
    for u, v in ((0, 16), (16, 48)):
        leg = p.cube(u, v, 4, 12, 4, legs, 4)
        for name in SIDES:
            x, y, w, h = leg[name]
            p.fill((x, y + 10, w, 2), shoes, 3)
        p.fill(leg["bottom"], shoes, 3)
    return p


def thugs():
    p = skin(201, SKIN, (40, 34, 30), (24, 24, 28), (24, 24, 28), (30, 30, 36), (14, 14, 16))
    fx, fy, fw, fh = faces(0, 0, 8, 8, 8)["front"]
    for i in range(fx, fx + 8):
        p.dot(i, fy + 3, (14, 14, 18))
        p.dot(i, fy + 4, (14, 14, 18))
    p.save("debt_collector.png")
    p = skin(202, SKIN, (20, 20, 22), (50, 54, 60), (50, 54, 60), (40, 44, 70), (30, 30, 34), mask=(26, 26, 30))
    p.save("mugger.png")


def elite_guard():
    img = Image.open(os.path.join(ENTITY, "casino_guard.png")).convert("RGBA")
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            # the tie gold, the badge and cuffs brighter
            if a and r > 120 and g < 60 and b < 60:
                px[x, y] = (230, 190, 60, a)
    d = ImageDraw.Draw(img)
    # gold piping down the jacket front and round the sleeves (box-UV of blockbench/casino_guard.js)
    d.line([(11, 51), (11, 62)], fill=GOLD + (255,))
    d.line([(17, 51), (17, 62)], fill=GOLD + (255,))
    for x0 in (60, 70):
        d.line([(x0, 80), (x0 + 9, 80)], fill=GOLD + (255,))
    img.save(os.path.join(ENTITY, "casino_guard_elite.png"))


# ---------------------------------------------------------------- items

def icon():
    return Image.new("RGBA", (16, 16), T)


def items():
    i = icon()
    d = ImageDraw.Draw(i)
    d.line([(3, 13), (11, 5)], fill=(90, 60, 35, 255), width=3)
    d.line([(9, 7), (13, 3)], fill=(70, 45, 25, 255), width=4)
    d.line([(2, 14), (4, 12)], fill=(30, 30, 30, 255), width=2)
    i.save(out("textures", "item", "club.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.rectangle([3, 1, 12, 14], fill=(236, 226, 196, 255), outline=(150, 130, 90, 255))
    for y in (4, 6, 8):
        d.line([(5, y), (10, y)], fill=(120, 110, 90, 255))
    d.line([(5, 11), (8, 12)], fill=(30, 30, 120, 255))
    d.ellipse([9, 10, 12, 13], fill=(170, 30, 30, 255))
    i.save(out("textures", "item", "promissory_note.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.ellipse([2, 2, 13, 13], fill=GOLD + (255,), outline=(150, 110, 20, 255))
    d.ellipse([5, 5, 10, 10], outline=(250, 220, 120, 255))
    i.putpixel((7, 7), (60, 160, 60, 255))
    i.putpixel((8, 7), (60, 160, 60, 255))
    i.putpixel((7, 8), (60, 160, 60, 255))
    i.save(out("textures", "item", "lucky_coin.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.line([(8, 0), (8, 3)], fill=GOLD + (255,))
    d.ellipse([2, 3, 13, 14], fill=GOLD + (255,), outline=(150, 110, 20, 255))
    d.ellipse([4, 5, 11, 12], fill=(245, 240, 225, 255))
    d.line([(7, 8), (7, 6)], fill=(30, 30, 30, 255))
    d.line([(7, 8), (9, 9)], fill=(30, 30, 30, 255))
    i.save(out("textures", "item", "gold_watch.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.polygon([(2, 3), (13, 3), (13, 12), (2, 12)], fill=(220, 200, 150, 255), outline=(140, 110, 60, 255))
    d.ellipse([3, 6, 5, 8], fill=(150, 120, 70, 255))
    d.text((6, 3), "7", fill=(40, 40, 40, 255))
    d.line([(7, 10), (12, 10)], fill=(120, 100, 60, 255))
    i.save(out("textures", "item", "pawn_ticket.png"))

    def bottle(name, body, neck, label):
        i = icon()
        d = ImageDraw.Draw(i)
        d.rectangle([6, 1, 9, 5], fill=neck + (255,))
        d.rectangle([4, 5, 11, 15], fill=body + (255,), outline=tuple(max(0, c - 50) for c in body) + (255,))
        d.rectangle([5, 9, 10, 12], fill=label + (255,))
        i.save(out("textures", "item", f"{name}.png"))

    bottle("champagne", (30, 80, 40), GOLD, (240, 230, 190))
    bottle("whisky", (170, 100, 30), (60, 40, 20), (30, 30, 30))
    i = icon()
    d = ImageDraw.Draw(i)
    d.rectangle([3, 7, 11, 13], fill=(240, 240, 240, 255), outline=(180, 180, 180, 255))
    d.rectangle([4, 7, 10, 8], fill=(60, 35, 20, 255))
    d.arc([10, 8, 14, 12], 270, 90, fill=(240, 240, 240, 255))
    d.ellipse([2, 13, 13, 15], fill=(230, 230, 230, 255))
    for x, y in ((6, 4), (8, 2), (7, 5)):
        i.putpixel((x, y), (220, 220, 220, 180))
    i.save(out("textures", "item", "espresso.png"))
    i = icon()
    d = ImageDraw.Draw(i)
    d.polygon([(3, 3), (12, 3), (11, 15), (4, 15)], fill=(200, 240, 200, 160), outline=(150, 190, 150, 255))
    d.rectangle([5, 7, 10, 14], fill=(150, 220, 120, 200))
    d.line([(9, 1), (7, 9)], fill=(40, 160, 60, 255), width=1)
    d.ellipse([3, 4, 6, 7], fill=(60, 170, 60, 255))
    i.save(out("textures", "item", "mojito.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.rounded_rectangle([1, 3, 14, 12], 2, fill=(20, 20, 24, 255), outline=GOLD + (255,))
    d.rectangle([2, 5, 5, 7], fill=GOLD + (255,))
    for x, letter in ((7, "VIP"),):
        d.text((6, 4), "VIP", fill=GOLD + (255,))
    i.save(out("textures", "item", "vip_card.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.ellipse([3, 6, 12, 15], outline=GOLD + (255,), width=2)
    d.rectangle([5, 2, 10, 7], fill=GOLD + (255,), outline=(150, 110, 20, 255))
    d.rectangle([6, 3, 9, 6], fill=(150, 20, 30, 255))
    i.putpixel((7, 4), (255, 120, 120, 255))
    i.save(out("textures", "item", "golden_signet.png"))

    i = icon()
    d = ImageDraw.Draw(i)
    d.rectangle([3, 3, 12, 12], outline=(200, 60, 200, 255))
    d.line([(3, 3), (12, 12)], fill=(200, 60, 200, 255))
    d.line([(3, 12), (12, 3)], fill=(200, 60, 200, 255))
    i.save(out("textures", "item", "npc_marker.png"))

    for name in ("promissory_note", "lucky_coin", "gold_watch", "pawn_ticket", "champagne", "espresso", "whisky", "mojito",
                 "vip_card", "golden_signet", "npc_marker"):
        write_json(generated_model(f"gamblecraft:item/{name}"), "models", "item", f"{name}.json")
        write_json({"model": {"type": "minecraft:model", "model": f"gamblecraft:item/{name}"}}, "items", f"{name}.json")
    write_json({"parent": "minecraft:item/handheld", "textures": {"layer0": "gamblecraft:item/club"}}, "models", "item", "club.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/club"}}, "items", "club.json")

    eggs = {
        "addicted_gambler": ((110, 85, 60), (170, 40, 40)), "loan_shark": ((44, 44, 54), (222, 180, 60)),
        "bartender": ((26, 26, 30), (238, 238, 234)), "waitress": ((26, 26, 30), (200, 200, 210)),
        "pickpocket": ((90, 90, 96), (40, 40, 44)), "casino_boss": ((236, 234, 228), (222, 180, 60)),
        "debt_collector": ((24, 24, 28), (90, 60, 35)), "mugger": ((50, 54, 60), (180, 30, 30)),
    }
    for seed, (name, (base, spot)) in enumerate(eggs.items()):
        spawn_egg(f"{name}_spawn_egg", base, spot, 20 + seed)


# ---------------------------------------------------------------- blocks

def blocks():
    rng = random.Random(9)

    def panel(img):
        for x in range(16):
            for y in range(16):
                n = rng.randint(-3, 3)
                img.putpixel((x, y), (16 + n, 14 + n, 20 + n, 255))

    top = Image.new("RGBA", (16, 16))
    panel(top)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.rectangle([3, 3, 12, 15], outline=(160, 120, 30, 255))
    # V I P in 3x5 letters
    glyphs = {"V": ["#.#", "#.#", "#.#", "#.#", ".#."], "I": ["###", ".#.", ".#.", ".#.", "###"], "P": ["##.", "#.#", "##.", "#..", "#.."]}
    x = 3
    for letter in "VIP":
        for r, row in enumerate(glyphs[letter]):
            for c, ch in enumerate(row):
                if ch == "#":
                    top.putpixel((x + c, 6 + r), GOLD + (255,))
        x += 4 if letter != "I" else 3
    top.save(out("textures", "block", "vip_door_top.png"))
    bottom = Image.new("RGBA", (16, 16))
    panel(bottom)
    d = ImageDraw.Draw(bottom)
    d.rectangle([0, 0, 15, 15], outline=GOLD + (255,))
    d.rectangle([3, 0, 12, 9], outline=(160, 120, 30, 255))
    d.rectangle([1, 12, 14, 14], fill=GOLD + (255,))
    d.rectangle([12, 1, 13, 3], fill=GOLD + (255,))
    bottom.save(out("textures", "block", "vip_door_bottom.png"))
    i = icon()
    d = ImageDraw.Draw(i)
    d.rectangle([4, 0, 11, 15], fill=(16, 14, 20, 255), outline=GOLD + (255,))
    d.rectangle([6, 2, 9, 6], outline=GOLD + (255,))
    i.putpixel((10, 9), GOLD + (255,))
    i.save(out("textures", "item", "vip_door.png"))

    states = json.loads(vanilla("assets/minecraft/blockstates/dark_oak_door.json"))
    write_json(json.loads(json.dumps(states).replace("minecraft:block/dark_oak_door", "gamblecraft:block/vip_door")), "blockstates", "vip_door.json")
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right", "top_right_open"):
        write_json({"parent": f"minecraft:block/door_{part}",
                    "textures": {"bottom": "gamblecraft:block/vip_door_bottom", "top": "gamblecraft:block/vip_door_top"}},
                   "models", "block", f"vip_door_{part}.json")
    write_json(generated_model("gamblecraft:item/vip_door"), "models", "item", "vip_door.json")
    write_json({"model": {"type": "minecraft:model", "model": "gamblecraft:item/vip_door"}}, "items", "vip_door.json")
    loot = vanilla("data/minecraft/loot_table/blocks/dark_oak_door.json")
    loot = loot.replace("minecraft:dark_oak_door", "gamblecraft:vip_door").replace("minecraft:blocks/dark_oak_door", "gamblecraft:blocks/vip_door")
    path = os.path.join(DATA, "gamblecraft", "loot_table", "blocks", "vip_door.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(loot)

    # the marker never shows: a model without elements
    write_json({"textures": {"particle": "minecraft:block/barrier"}}, "models", "block", "npc_marker.json")
    write_json({"variants": {f"npc={n}": {"model": "gamblecraft:block/npc_marker"} for n in ("gambler", "pickpocket", "waitress")}},
               "blockstates", "npc_marker.json")


if __name__ == "__main__":
    npcs()
    thugs()
    elite_guard()
    items()
    blocks()
    print("F10 assets written")
