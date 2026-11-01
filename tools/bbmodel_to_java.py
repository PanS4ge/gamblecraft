"""Converts a Blockbench "Modded Entity" project (.bbmodel) into a Java class for Minecraft 26.3:
a LayerDefinition built with MeshDefinition/CubeListBuilder and one AnimationDefinition per animation.

  python tools/bbmodel_to_java.py blockbench/street_dealer.bbmodel StreetDealerModelData

Blockbench space is Y-up with the model facing -Z; Minecraft model space is the same rotated 180 degrees about Z
(entity renderers apply scale(-1, -1, 1)), so X and Y flip: positions become (-x, 24 - y, z) and rotations (-rx, -ry, rz).
The model in blockbench/ is the source of truth; the generated file is overwritten on every run.
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT_DIR = os.path.join(ROOT, "src", "client", "java", "pl", "pansage", "gamblecraft", "client", "model")
PACKAGE = "pl.pansage.gamblecraft.client.model"


def f(x):
    x = float(x)
    if x == int(x):
        return f"{int(x)}.0F"
    return f"{x:.4f}".rstrip("0") + "F"


def const_name(name):
    return name.split(".")[-1].upper()


def convert(path, class_name):
    with open(path, encoding="utf-8") as fh:
        model = json.load(fh)
    width = model["resolution"]["width"]
    height = model["resolution"]["height"]
    groups = {g["uuid"]: g for g in model["groups"]}
    elements = {e["uuid"]: e for e in model["elements"]}

    lines = []

    def emit_group(node, parent_var, parent_origin, depth):
        group = groups[node["uuid"]]
        name = group["name"]
        ox, oy, oz = group["origin"]
        if parent_origin is None:
            px, py, pz = -ox, 24 - oy, oz
        else:
            px, py, pz = -(ox - parent_origin[0]), -(oy - parent_origin[1]), oz - parent_origin[2]
        rx, ry, rz = group.get("rotation", [0, 0, 0])
        cubes = ["CubeListBuilder.create()"]
        for child in node.get("children", []):
            if isinstance(child, str):
                e = elements[child]
                if any(e.get("rotation", [0, 0, 0])):
                    raise SystemExit(f"Cube {e['name']} has its own rotation; put it in a rotated group instead")
                fx, fy, fz = e["from"]
                tx, ty, tz = e["to"]
                u, v = e.get("uv_offset", [0, 0])
                inflate = e.get("inflate", 0)
                mirror = e.get("mirror_uv", False)
                box = f"addBox({f(ox - tx)}, {f(oy - ty)}, {f(fz - oz)}, {f(tx - fx)}, {f(ty - fy)}, {f(tz - fz)}"
                box += f", new CubeDeformation({f(inflate)}))" if inflate else ")"
                piece = f".texOffs({u}, {v})"
                piece += ".mirror()" if mirror else ""
                piece += "." + box
                piece += ".mirror(false)" if mirror else ""
                cubes.append(piece)
        if any((rx, ry, rz)):
            pose = (f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(-rx)} * DEG, {f(-ry)} * DEG, {f(rz)} * DEG)")
        else:
            pose = f"PartPose.offset({f(px)}, {f(py)}, {f(pz)})"
        var = name.replace(".", "_")
        indent = "\t\t"
        lines.append(f'{indent}PartDefinition {var} = {parent_var}.addOrReplaceChild("{name}", {chr(10).join(cubes) if False else "".join(cubes)}, {pose});')
        for child in node.get("children", []):
            if isinstance(child, dict):
                emit_group(child, var, group["origin"], depth + 1)

    for node in model["outliner"]:
        if isinstance(node, dict):
            emit_group(node, "root", None, 0)
        else:
            raise SystemExit("Cubes must live inside groups")

    anims = []
    for anim in model.get("animations", []):
        const = const_name(anim["name"])
        body = [f"\tpublic static final AnimationDefinition {const} = AnimationDefinition.Builder.withLength({f(anim['length'])})"]
        if anim.get("loop") == "loop":
            body.append("\t\t\t.looping()")
        for uuid, animator in anim.get("animators", {}).items():
            bone = animator["name"]
            by_channel = {}
            for kf in animator["keyframes"]:
                by_channel.setdefault(kf["channel"], []).append(kf)
            for channel, kfs in by_channel.items():
                kfs.sort(key=lambda k: k["time"])
                target = {"rotation": "ROTATION", "position": "POSITION", "scale": "SCALE"}[channel]
                frames = []
                for kf in kfs:
                    p = kf["data_points"][0]
                    x, y, z = (float(p["x"]), float(p["y"]), float(p["z"]))
                    if channel == "rotation":
                        vec = f"KeyframeAnimations.degreeVec({f(-x)}, {f(-y)}, {f(z)})"
                    elif channel == "position":
                        vec = f"KeyframeAnimations.posVec({f(-x)}, {f(y)}, {f(z)})"
                    else:
                        vec = f"KeyframeAnimations.scaleVec({x}, {y}, {z})"
                    interp = "CATMULLROM" if kf.get("interpolation") == "catmullrom" else "LINEAR"
                    frames.append(f"\t\t\t\t\tnew Keyframe({f(kf['time'])}, {vec}, AnimationChannel.Interpolations.{interp})")
                body.append(f'\t\t\t.addAnimation("{bone}", new AnimationChannel(AnimationChannel.Targets.{target},\n' + ",\n".join(frames) + "))")
        body.append("\t\t\t.build();")
        anims.append("\n".join(body))

    source = f"""package {PACKAGE};

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/bbmodel_to_java.py from {os.path.relpath(path, ROOT).replace(os.sep, '/')}.
 * Edit the Blockbench model and run the script again instead of changing this file.
 */
@SuppressWarnings("unused")
public final class {class_name} {{
	private static final float DEG = (float) (Math.PI / 180.0);

	private {class_name}() {{
	}}

	public static LayerDefinition createBodyLayer() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
{chr(10).join(lines)}
		return LayerDefinition.create(mesh, {width}, {height});
	}}

{(chr(10) + chr(10)).join(anims)}
}}
"""
    os.makedirs(OUT_DIR, exist_ok=True)
    target = os.path.join(OUT_DIR, f"{class_name}.java")
    with open(target, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(source)
    print("wrote", os.path.normpath(target))


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])
