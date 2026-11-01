"""Paints entity textures for villager-shaped GambleCraft NPCs (64x64, vanilla villager box-UV layout).

  python tools/gen_entity_textures.py

Each cube is painted face by face from its texture offset and size, so the art always lines up with the model.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gamblecraft", "textures", "entity")

SKIN = (178, 134, 104)
SKIN_DARK = (150, 108, 82)


def faces(u, v, w, h, d):
    """Box-UV rectangles (x, y, width, height) of a cube's faces."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


class Painter:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = random.Random(seed)

    def fill(self, rect, color, noise=6):
        x, y, w, h = rect
        for i in range(x, x + w):
            for j in range(y, y + h):
                n = self.rng.randint(-noise, noise)
                self.px[i, j] = tuple(max(0, min(255, c + n)) for c in color[:3]) + (color[3] if len(color) > 3 else 255,)

    def dot(self, x, y, color):
        self.px[x, y] = color[:3] + ((color[3],) if len(color) > 3 else (255,))

    def cube(self, u, v, w, h, d, color, noise=6):
        for rect in faces(u, v, w, h, d).values():
            self.fill(rect, color, noise)
        return faces(u, v, w, h, d)

    def save(self, name):
        os.makedirs(ROOT, exist_ok=True)
        self.img.save(os.path.join(ROOT, name))


def villager_head(p, hair=(70, 45, 30), eyes=(40, 120, 60)):
    head = p.cube(0, 0, 8, 10, 8, SKIN)
    fx, fy, fw, fh = head["front"]
    # unibrow and eyes
    for i in range(fx + 1, fx + 7):
        p.dot(i, fy + 3, SKIN_DARK)
    p.dot(fx + 1, fy + 4, (255, 255, 255))
    p.dot(fx + 2, fy + 4, eyes)
    p.dot(fx + 5, fy + 4, eyes)
    p.dot(fx + 6, fy + 4, (255, 255, 255))
    # mouth shadow
    for i in range(fx + 2, fx + 6):
        p.dot(i, fy + 8, SKIN_DARK)
    # hair on top and back
    p.fill(head["top"], hair)
    bx, by, bw, bh = head["back"]
    p.fill((bx, by, bw, 3), hair)
    for side in ("left", "right"):
        sx, sy, sw, sh = head[side]
        p.fill((sx, sy, sw, 2), hair)
    p.cube(24, 0, 2, 4, 2, (165, 120, 92))  # nose


def street_dealer():
    """Texture for blockbench/street_dealer.bbmodel (custom layout, see the model's uv offsets)."""
    p = Painter(7)
    villager_head(p)
    # Visor band overlay (32,0): green band around the forehead, the rest transparent.
    hat = faces(32, 0, 8, 10, 8)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = hat[name]
        p.fill((x, y + 1, w, 2), (30, 120, 60), 4)
    p.fill(hat["top"], (0, 0, 0, 0), 0)
    # Visor brim (28,38) 8x1x4: translucent-looking green, lighter on top.
    brim = faces(28, 38, 8, 1, 4)
    for name, rect in brim.items():
        p.fill(rect, (40, 150, 80) if name != "bottom" else (25, 95, 50), 4)
    # Bow tie (28,44) 4x2x1.
    tie = p.cube(28, 44, 4, 2, 1, (185, 20, 30), 5)
    fx, fy, fw, fh = tie["front"]
    p.dot(fx + 1, fy, (120, 10, 15))
    p.dot(fx + 2, fy + 1, (120, 10, 15))
    # Body (16,20) 8x12x6: white shirt, belt, trousers top.
    body = p.cube(16, 20, 8, 12, 6, (232, 232, 228), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = body[name]
        p.fill((x, y + 10, w, 1), (40, 30, 25), 2)
        p.fill((x, y + 11, w, 1), (55, 45, 40), 4)
    # Vest overlay (0,38) 8x12x6: black, open at the front, gold buttons.
    vest = p.cube(0, 38, 8, 12, 6, (28, 28, 32), 5)
    x, y, w, h = vest["front"]
    for i in range(y, y + 6):
        for j in range(x + 3, x + 5):
            p.dot(j, i, (0, 0, 0, 0))
    for i in range(y, y + 2):
        for j in (x + 2, x + 5):
            p.dot(j, i, (0, 0, 0, 0))
    for i in (y + 5, y + 7, y + 9):
        p.dot(x + 2, i, (230, 190, 60))
    p.fill(vest["top"], (0, 0, 0, 0), 0)
    p.fill(vest["bottom"], (0, 0, 0, 0), 0)
    # Arms (44,22) 4x12x4: white sleeves with red garters, rolled cuffs, bare forearms and hands.
    arm = p.cube(44, 22, 4, 12, 4, (232, 232, 228), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = arm[name]
        p.fill((x, y + 3, w, 1), (180, 25, 35), 3)
        p.fill((x, y + 6, w, 1), (210, 210, 205), 3)
        p.fill((x, y + 7, w, 5), SKIN, 4)
    p.fill(arm["bottom"], SKIN, 4)
    # Deck held in the right hand (40,44) 3x1x4: red card backs, white edges.
    deck = p.cube(40, 44, 3, 1, 4, (235, 235, 235), 2)
    p.fill(deck["top"], (200, 25, 30), 6)
    p.fill(deck["bottom"], (200, 25, 30), 6)
    # Legs (0,22) 4x12x4: dark trousers, polished shoes.
    leg = p.cube(0, 22, 4, 12, 4, (55, 45, 40), 5)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = leg[name]
        p.fill((x, y + 10, w, 2), (20, 18, 16), 3)
    p.fill(leg["bottom"], (20, 18, 16), 3)
    p.save("street_dealer.png")


def lottery_clerk():
    """Same layout as street_dealer: no visor, wire glasses on the head overlay, cream blouse, purple vest and brooch."""
    p = Painter(11)
    villager_head(p, hair=(40, 28, 22), eyes=(70, 50, 120))
    head = faces(0, 0, 8, 10, 8)
    bx, by, bw, bh = head["back"]
    p.fill((bx, by, bw, 7), (40, 28, 22))
    for side in ("left", "right"):
        sx, sy, sw, sh = head[side]
        p.fill((sx, sy, sw, 4), (40, 28, 22))
    hat = faces(32, 0, 8, 10, 8)
    for rect in hat.values():
        p.fill(rect, (0, 0, 0, 0), 0)
    # Half-rim gold reading glasses: lower rims, a bridge, thin arms; the eyes stay visible.
    fx, fy, fw, fh = hat["front"]
    frame = (215, 175, 60)
    for lens in (fx + 1, fx + 5):
        p.dot(lens - 1, fy + 4, frame)
        p.dot(lens, fy + 5, frame)
        p.dot(lens + 1, fy + 5, frame)
        p.dot(lens + 2, fy + 4, frame)
    p.dot(fx + 3, fy + 4, frame)
    p.dot(fx + 4, fy + 4, frame)
    # arms run from the front edge towards the ears ("right" touches the front on its last column, "left" on its first)
    rx, ry, rw, rh = hat["right"]
    lx, ly, lw, lh = hat["left"]
    for i in range(6):
        p.dot(rx + rw - 1 - i, ry + 4, frame)
        p.dot(lx + i, ly + 4, frame)
    for rect in faces(28, 38, 8, 1, 4).values():
        p.fill(rect, (0, 0, 0, 0), 0)
    brooch = p.cube(28, 44, 4, 2, 1, (120, 50, 160), 5)
    x, y, w, h = brooch["front"]
    p.dot(x + 1, y, (240, 210, 90))
    p.dot(x + 2, y + 1, (240, 210, 90))
    body = p.cube(16, 20, 8, 12, 6, (238, 228, 205), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = body[name]
        p.fill((x, y + 10, w, 2), (60, 30, 80), 4)
    vest = p.cube(0, 38, 8, 12, 6, (95, 40, 130), 5)
    x, y, w, h = vest["front"]
    for i in range(y, y + 4):
        for j in range(x + 3, x + 5):
            p.dot(j, i, (0, 0, 0, 0))
    for i in (y + 5, y + 7, y + 9):
        p.dot(x + 3, i, (240, 210, 90))
    for name in ("left", "right", "back"):
        x, y, w, h = vest[name]
        p.fill((x, y + 11, w, 1), (70, 25, 100), 3)
    p.fill(vest["top"], (0, 0, 0, 0), 0)
    p.fill(vest["bottom"], (0, 0, 0, 0), 0)
    arm = p.cube(44, 22, 4, 12, 4, (238, 228, 205), 4)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = arm[name]
        p.fill((x, y + 8, w, 1), (95, 40, 130), 3)
        p.fill((x, y + 9, w, 3), SKIN, 4)
    p.fill(arm["bottom"], SKIN, 4)
    for rect in faces(40, 44, 3, 1, 4).values():
        p.fill(rect, (0, 0, 0, 0), 0)
    leg = p.cube(0, 22, 4, 12, 4, (60, 30, 80), 5)
    for name in ("front", "left", "right", "back"):
        x, y, w, h = leg[name]
        p.fill((x, y + 10, w, 2), (25, 18, 20), 3)
    p.fill(leg["bottom"], (25, 18, 20), 3)
    p.save("lottery_clerk.png")


if __name__ == "__main__":
    street_dealer()
    lottery_clerk()
    print("entity textures written to", os.path.normpath(ROOT))
