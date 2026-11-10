"""Exportiert Animationen aus einer .bbmodel in das Format der Mod (eine JSON je Animation).

Format (assets/kubyrematch/rematch/anim/<name>.json):
  {"name", "length", "loop": "once"|"loop"|"hold", "meta": {...},
   "bones": {knochen: {"rotation"|"position"|"scale": [[zeit, x, y, z, "linear"|"catmullrom"|"step"], ...]}},
   "events": [[zeit, "kontakt"], ...]}
Werte wie Blockbench sie intern speichert (Grad, Pixel, ohne Bedrock-Spiegelung). Zeitleisten-Marken werden zu
"kontakt", Effekt-Spuren zu ihrem Text (Timeline-Skript), "sound:<name>" oder "partikel:<name>".

Altes Gerüst (Skizzen, 38 Pixel hoch, Mitte bei x=1) wird automatisch auf das Spieler-Gerüst (32 Pixel) umgerechnet:
Drehungen bleiben, Verschiebungen werden skaliert, der Ball wird auf den neuen Drehpunkt umgelegt.

Aufruf: python3 tools/bbanim/export_anim.py <datei.bbmodel> <zielordner> [animation ...]
Zusatzwerte je Animation (Priorität, Überblenden, Abbruch) aus rematch/animationen/meta.json, falls vorhanden.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig_spieler  # noqa: E402

RIG_PIVOT = {n: p for n, _, p in rig_spieler.BONES}
DEFAULT_META = {"priority": 50, "blend_in": 0.12, "blend_out": 0.25, "ease_in": "out_quad", "ease_out": "in_out_cubic",
                "cancel_after": None, "speed": 1.0}


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def r5(x):
    return round(float(x), 5)


def load_model(path):
    m = json.load(open(path))
    groups = {g["uuid"]: g for g in m.get("groups", [])}
    names = {u: g["name"] for u, g in groups.items()}
    origin = {g["name"]: g.get("origin", [0, 0, 0]) for g in groups.values()}
    return m, names, origin


def retarget(origin):
    """(skala, x-versatz) für Verschiebungen: altes Gerüst ist 38 hoch, das neue 32."""
    head = origin.get("head")
    if head is None:
        return 1.0, 0.0
    # Halsansatz: neues Gerüst 24, altes 30
    s = 24.0 / head[1] if head[1] > 0 else 1.0
    cx = origin.get("full_body", [0, 0, 0])[0]
    return s, cx


def export(path, outdir, only=None):
    m, names, origin = load_model(path)
    s, cx = retarget(origin)
    meta_path = os.path.join(os.path.dirname(path), "meta.json")
    metas = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    os.makedirs(outdir, exist_ok=True)
    written = []
    for a in m.get("animations", []):
        if only and a["name"] not in only:
            continue
        bones, events = {}, []
        for uid, an in (a.get("animators") or {}).items():
            if an.get("type") == "effect" or uid == "effects":
                for k in an.get("keyframes", []):
                    dp = (k.get("data_points") or [{}])[0]
                    if k["channel"] == "timeline" and dp.get("script", "").strip():
                        events.append([r5(k["time"]), dp["script"].strip()])
                    elif k["channel"] == "sound" and dp.get("effect"):
                        events.append([r5(k["time"]), "sound:" + dp["effect"]])
                    elif k["channel"] == "particle" and dp.get("effect"):
                        events.append([r5(k["time"]), "partikel:" + dp["effect"]])
                continue
            bone = names.get(uid, an.get("name"))
            if bone not in RIG_PIVOT:
                continue
            ch = {}
            for k in an.get("keyframes", []):
                c = k["channel"]
                if c not in ("rotation", "position", "scale"):
                    continue
                dp = k["data_points"][0]
                v = [num(dp.get(x)) for x in "xyz"]
                if c == "position":
                    if bone == "ball":
                        o = origin.get("ball", RIG_PIVOT["ball"])
                        absx = [(o[0] + v[0] - cx) * s, (o[1] + v[1]) * s, (o[2] + v[2]) * s]
                        v = [absx[i] - RIG_PIVOT["ball"][i] for i in range(3)]
                        v[1] += 2.5 - 2.5 * s  # Ball bleibt gleich groß: Mitte über dem Boden wie vorher
                    else:
                        v = [x * s for x in v]
                ip = k.get("interpolation", "linear")
                if ip not in ("linear", "catmullrom", "step"):
                    ip = "linear"
                ch.setdefault(c, []).append([r5(k["time"])] + [r5(x) for x in v] + [ip])
            for c in ch:
                ch[c].sort(key=lambda e: e[0])
            if ch:
                bones[bone] = ch
        for mk in a.get("markers", []) or []:
            events.append([r5(mk["time"]), "kontakt"])
        events.sort(key=lambda e: e[0])
        meta = dict(DEFAULT_META)
        meta.update(metas.get(a["name"], {}))
        out = {"name": a["name"], "length": r5(a.get("length", 0)), "loop": a.get("loop", "once"), "meta": meta,
               "bones": bones, "events": events}
        dst = os.path.join(outdir, a["name"] + ".json")
        json.dump(out, open(dst, "w"), separators=(",", ":"))
        written.append(a["name"])
    return written


if __name__ == "__main__":
    print("exportiert:", ", ".join(export(sys.argv[1], sys.argv[2], sys.argv[3:] or None)))
