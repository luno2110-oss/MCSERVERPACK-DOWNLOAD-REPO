"""Spieler-Gerüst mit Minecraft-Maßen, eine Quelle für die Blockbench-Vorlage und die Mod.

Knochennamen wie in den Skizzen des Besitzers (auch der Tippfehler left_lag2 bleibt, damit seine Animationen passen).
Maße wie der echte Spieler (32 Pixel hoch): Beine, Rumpf und Arme sind je 12 Pixel und in der Mitte geteilt
(Knie, Bauch, Ellbogen biegen). Koordinaten wie in Blockbench: y nach oben, Füße bei y=0, Figur schaut nach -z,
rechte Körperseite bei x+.

Aufruf:
  python3 tools/bbanim/rig_spieler.py vorlage <ziel.bbmodel> [skin.png] [slim]   Blockbench-Vorlage mit Skin
  python3 tools/bbanim/rig_spieler.py rig <ziel.json>                             Knochenliste für die Mod
"""
import base64
import json
import sys
import uuid as uuidlib

# (name, eltern, drehpunkt)
BONES = [
    ("full_body", None, (0, 17, 0)),         # Körpermitte: Saltos drehen hier
    ("lower_body", "full_body", (0, 12, 0)),
    ("legs", "lower_body", (0, 12, 0)),
    ("right2", "legs", (1.9, 12, 0)),        # rechte Hüfte
    ("right_leg", "right2", (1.9, 12, 0)),   # rechter Oberschenkel (meist 0, die Hüfte dreht)
    ("right_leg2", "right2", (1.9, 6, 0)),   # rechtes Knie
    ("left2", "legs", (-1.9, 12, 0)),
    ("left_leg", "left2", (-1.9, 12, 0)),
    ("left_lag2", "left2", (-1.9, 6, 0)),    # linkes Knie (Name wie in der Skizze)
    ("middle_body", "full_body", (0, 12, 0)),
    ("upper_body", "middle_body", (0, 12, 0)),
    ("torso1", "upper_body", (0, 12, 0)),    # Bauch
    ("torso2", "torso1", (0, 18, 0)),        # Brust
    ("head", "torso2", (0, 24, 0)),
    ("arms", "torso2", (0, 22, 0)),
    ("right", "arms", (5, 22, 0)),           # rechte Schulter
    ("right_arm", "right", (5, 22, 0)),      # rechter Oberarm (meist 0, die Schulter dreht)
    ("right_arm2", "right", (6, 18, 0)),     # rechter Ellbogen
    ("left", "arms", (-5, 22, 0)),
    ("left_arm", "left", (-5, 22, 0)),
    ("left_arm2", "left", (-6, 18, 0)),
    ("ball", None, (0, 2.5, -10)),
]


def limb(u, v, w, h, d):
    """Box-UV eines ganzen Glieds (wie Minecraft) als Flächen je Seite, Blockbench-Namen."""
    return {
        "east": [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "up": [u + d, v, u + d + w, v + d],
        "down": [u + d + w, v + d, u + d + 2 * w, v],
    }


def half(full, top):
    """Obere oder untere Hälfte der Seitenflächen eines Glieds; Deckel/Boden je nach Hälfte."""
    out = {}
    for f, (u1, v1, u2, v2) in full.items():
        if f in ("up", "down"):
            out[f] = [u1, v1, u2, v2]
            continue
        m = (v1 + v2) / 2
        out[f] = [u1, v1, u2, m] if top else [u1, m, u2, v2]
    return out


def cubes(slim=False):
    """(name, knochen, from, to, uv-flächen, aufblasen) in Blockbench-Koordinaten."""
    aw = 3 if slim else 4
    out = []

    def split(name, bone_up, bone_lo, x0, y0, z0, w, h, d, u, v, inflate=0.0):
        full = limb(u, v, w, h, d)
        hh = h / 2
        out.append((name + "_oben", bone_up, (x0, y0 + hh, z0), (x0 + w, y0 + h, z0 + d), half(full, True), inflate))
        out.append((name + "_unten", bone_lo, (x0, y0, z0), (x0 + w, y0 + hh, z0 + d), half(full, False), inflate))

    out.append(("kopf", "head", (-4, 24, -4), (4, 32, 4), limb(0, 0, 8, 8, 8), 0.0))
    out.append(("hut", "head", (-4, 24, -4), (4, 32, 4), limb(32, 0, 8, 8, 8), 0.5))
    split("rumpf", "torso2", "torso1", -4, 12, -2, 8, 12, 4, 16, 16)
    split("jacke", "torso2", "torso1", -4, 12, -2, 8, 12, 4, 16, 32, 0.25)
    # rechter Arm: Minecraft x -8..-4 (breit) bzw. -7..-4 (schmal) -> Blockbench gespiegelt
    split("arm_r", "right_arm", "right_arm2", 4, 12, -2, aw, 12, 4, 40, 16)
    split("aermel_r", "right_arm", "right_arm2", 4, 12, -2, aw, 12, 4, 40, 32, 0.25)
    split("arm_l", "left_arm", "left_arm2", -4 - aw, 12, -2, aw, 12, 4, 32, 48)
    split("aermel_l", "left_arm", "left_arm2", -4 - aw, 12, -2, aw, 12, 4, 48, 48, 0.25)
    split("bein_r", "right_leg", "right_leg2", -0.1, 0, -2, 4, 12, 4, 0, 16)
    split("hose_r", "right_leg", "right_leg2", -0.1, 0, -2, 4, 12, 4, 0, 32, 0.25)
    split("bein_l", "left_leg", "left_lag2", -3.9, 0, -2, 4, 12, 4, 16, 48)
    split("hose_l", "left_leg", "left_lag2", -3.9, 0, -2, 4, 12, 4, 0, 48, 0.25)
    return out


def vorlage(path, skin=None, slim=False):
    ids = {b[0]: str(uuidlib.uuid4()) for b in BONES}
    elements, members = [], {b[0]: [] for b in BONES}
    for name, bone, a, b, faces, inflate in cubes(slim):
        uid = str(uuidlib.uuid4())
        bp = dict((n, p) for n, _, p in BONES)[bone]
        el = {"name": name, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
              "allow_mirror_modeling": True, "from": list(a), "to": list(b), "autouv": 0, "color": 0,
              "origin": list(bp), "uuid": uid, "type": "cube",
              "faces": {f: {"uv": uv, "texture": 0} for f, uv in faces.items()}}
        if inflate:
            el["inflate"] = inflate
        elements.append(el)
        members[bone].append(uid)
    # Ball: 5x5x5, weiß
    buid = str(uuidlib.uuid4())
    elements.append({"name": "ball", "box_uv": False, "rescale": False, "from": [-2.5, 0, -12.5], "to": [2.5, 5, -7.5],
                     "autouv": 0, "color": 1, "origin": [0, 2.5, -10], "uuid": buid, "type": "cube",
                     "faces": {f: {"uv": [8, 8, 9, 9], "texture": None} for f in
                               ("north", "east", "south", "west", "up", "down")}})
    members["ball"].append(buid)

    groups, children = [], {b[0]: [] for b in BONES}
    for n, parent, _ in BONES:
        if parent:
            children[parent].append(n)

    def node(n):
        return {"uuid": ids[n], "isOpen": True, "children": members[n] + [node(c) for c in children[n]]}

    for n, parent, p in BONES:
        groups.append({"name": n, "origin": list(p), "color": 0, "uuid": ids[n], "export": True, "isOpen": True,
                       "locked": False, "visibility": True, "autouv": 0, "rotation": [0, 0, 0]})
    textures = []
    if skin:
        data = base64.b64encode(open(skin, "rb").read()).decode()
        textures.append({"path": "", "name": "skin.png", "folder": "", "namespace": "", "id": "0", "width": 64,
                         "height": 64, "uv_width": 64, "uv_height": 64, "particle": False, "use_as_default": True,
                         "layers_enabled": False, "render_mode": "default", "render_sides": "auto", "frame_time": 1,
                         "visible": True, "internal": True, "saved": True, "uuid": str(uuidlib.uuid4()),
                         "relative_path": "", "source": "data:image/png;base64," + data})
    model = {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
             "name": "kuby_spieler", "model_identifier": "kuby_spieler", "visible_box": [1, 1, 0],
             "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
             "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64},
             "elements": elements, "groups": groups,
             "outliner": [node(n) for n, parent, _ in BONES if parent is None],
             "textures": textures, "animations": []}
    json.dump(model, open(path, "w"), indent=1)


def rig(path):
    json.dump({"bones": [{"name": n, "parent": p, "pivot": list(pv)} for n, p, pv in BONES]},
              open(path, "w"), indent=1)


if __name__ == "__main__":
    if sys.argv[1] == "vorlage":
        vorlage(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None, len(sys.argv) > 4 and sys.argv[4] == "slim")
    elif sys.argv[1] == "rig":
        rig(sys.argv[2])
