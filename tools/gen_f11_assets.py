"""Particle textures and definitions for F11: confetti flakes and gold glitter (white, tinted in game).

  python tools/gen_f11_assets.py
"""
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_assets import out, write_json  # noqa: E402

WHITE = (255, 255, 255, 255)


def confetti():
    shapes = [[(2, 1), (5, 6)], [(1, 3), (6, 4)], [(2, 2), (5, 5)], [(3, 1), (4, 6)]]
    for i, (a, b) in enumerate(shapes):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        ImageDraw.Draw(img).rectangle([a, b], fill=WHITE)
        img.save(out("textures", "particle", f"confetti_{i}.png"))
    write_json({"textures": [f"gamblecraft:confetti_{i}" for i in range(len(shapes))]}, "particles", "confetti.json")


def glitter():
    for i, size in enumerate((1, 2, 3, 2)):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.line([(3, 3 - size), (3, 3 + size)], fill=WHITE)
        d.line([(3 - size, 3), (3 + size, 3)], fill=WHITE)
        if size > 1:
            img.putpixel((2, 2), (255, 255, 255, 160))
            img.putpixel((4, 4), (255, 255, 255, 160))
        img.save(out("textures", "particle", f"gold_glitter_{i}.png"))
    write_json({"textures": [f"gamblecraft:gold_glitter_{i}" for i in range(4)]}, "particles", "gold_glitter.json")


if __name__ == "__main__":
    confetti()
    glitter()
    print("F11 assets written")
