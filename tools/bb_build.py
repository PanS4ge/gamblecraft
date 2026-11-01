"""Builds block models in a running Blockbench from blockbench/<model>.json specs, through the MCP plugin.

  python tools/bb_build.py slot_machine roulette_wheel roulette_felt

Each spec names its textures by id; the files are taken from src/main/resources (gamblecraft:) or from vanilla
textures unpacked into VANILLA (minecraft:). Saves blockbench/<model>.bbmodel and exports the model JSON.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bb  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
VANILLA = "C:/Users/patry/AppData/Local/Temp/claude/C--Users-patry-Desktop-gamblingmod/f3559456-0561-4fa1-87f7-66f58f6c8684/scratchpad/bbtex"


def texture_file(texture_id):
    namespace, path = texture_id.split(":")
    if namespace == "minecraft":
        return f"{VANILLA}/assets/minecraft/textures/{path}.png"
    # the sportsbook add-on keeps its own resources
    base = "sportsbook/src/main/resources" if namespace == "gamblecraft_sportsbook" else "src/main/resources"
    return f"{ROOT}/{base}/assets/{namespace}/textures/{path}.png".replace("\\", "/")


def build(name):
    with open(os.path.join(ROOT, "blockbench", f"{name}.json"), encoding="utf-8") as f:
        spec = json.load(f)
    used = {c["tex"] for c in spec["cubes"]} | {t for c in spec["cubes"] for t in c["faces"].values()}
    spec["files"] = {key: texture_file(spec["textures"][key]) for key in sorted(used)}
    spec["exportPath"] = f"{ROOT}/{spec['export']}".replace("\\", "/")
    spec["savePath"] = f"{ROOT}/blockbench/{name}.bbmodel".replace("\\", "/")
    with open(os.path.join(ROOT, "blockbench", "build_block.js"), encoding="utf-8") as f:
        # risky_eval refuses code with comments, so they are kept in the file only
        lines = [line for line in f.read().splitlines() if not line.strip().startswith("//")]
    code = "\n".join(lines).replace("__SPEC__", json.dumps(spec))
    print(bb.call("create_project", {"name": name, "format": "java_block"}))
    print(bb.call("risky_eval", {"code": code}))


if __name__ == "__main__":
    bb.connect()
    for model in sys.argv[1:]:
        build(model)
