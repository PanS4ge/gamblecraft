"""Builds an entity model ("Modded Entity" project) in a running Blockbench from blockbench/<model>.js.

  python tools/bb_entity.py casino_guard

The script builds the groups, cubes, texture and animations and saves blockbench/<model>.bbmodel; turn that into
Java with tools/bbmodel_to_java.py.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bb  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))


def build(name):
    with open(os.path.join(ROOT, "blockbench", f"{name}.js"), encoding="utf-8") as f:
        # risky_eval refuses code with comments, so they are kept in the file only
        lines = [line for line in f.read().splitlines() if not line.strip().startswith("//")]
    print(bb.call("create_project", {"name": name, "format": "modded_entity"}))
    print(bb.call("risky_eval", {"code": "\n".join(lines)}))


if __name__ == "__main__":
    bb.connect()
    for model in sys.argv[1:]:
        build(model)
