"""Fortbewegung im Rematch-Stil: stehen, gehen, laufen, sprinten (Schleifen) und luft (Pose für Sprung/Fallen).

Gangarten werden als halber Zyklus (rechtes Bein vorn) angelegt und für die zweite Hälfte gespiegelt. Die Wurzel wird
wie bei den Aktionen gelöst (Standfuß klebt am Boden); daraus ergibt sich die Schrittlänge. Die Schleife selbst bleibt
an Ort und Stelle (nur das Auf und Ab bleibt), die Mod spielt sie so schnell ab, dass die Füße zum echten Tempo passen:
speed_at (Blöcke/s) steht in meta.json.

Aufruf: python3 tools/bbanim/locomotion.py  (schreibt rematch/animationen/kuby_fortbewegung.bbmodel und die JSON der Mod)
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import animkit as K  # noqa: E402
import render_bbmodel as R  # noqa: E402
import export_anim  # noqa: E402

VORLAGE = os.path.join(ROOT, "rematch", "animationen", "vorlage_spieler.bbmodel")
ZIEL = os.path.join(ROOT, "rematch", "animationen", "kuby_fortbewegung.bbmodel")
MOD_ANIM = os.path.join(ROOT, "rematch", "mod", "src", "main", "resources", "assets", "kubyrematch", "rematch", "anim")
PX_PER_BLOCK = 16 / 0.9375
G = 330.0

SWAP = {"hipR": "hipL", "hipL": "hipR", "kneeR": "kneeL", "kneeL": "kneeR", "shR": "shL", "shL": "shR",
        "elR": "elL", "elL": "elR"}


def mirror(p):
    """Pose spiegeln: links/rechts tauschen, y- und z-Drehungen umdrehen."""
    out = {}
    for k, v in p.items():
        v = tuple(v) + (0,) * (3 - len(v))
        out[SWAP.get(k, k)] = (v[0], -v[1], -v[2])
    return out


def cycle(c, half, keys):
    """keys: {bild: pose} für die erste Hälfte (0..half); zweite Hälfte gespiegelt, Bild 2*half = Bild 0."""
    for f, p in keys.items():
        c.pose(f, **p)
        c.pose(f + half, **mirror(p))
    c.pose(2 * half, **keys[0])


def finish(c, n):
    """Wurzel lösen, Schrittlänge messen, waagerechte Wurzelbewegung entfernen (Schleife an Ort und Stelle)."""
    c.solve_root()
    tr = np.array(c.root_track)
    stride = abs(tr[n][2] - tr[0][2])
    speed = stride / (n / K.FPS) / PX_PER_BLOCK
    y0 = tr[0][1]
    c.root_track = [np.array([0.0, p[1] - y0 + (tr[:, 1] - y0).min() * 0 + 0.0, 0.0]) for p in tr]
    # Auf und Ab so verschieben, dass der tiefste Fußpunkt über den Zyklus am Boden ist
    lows = [min(c.rel(f)["L"]["min"], c.rel(f)["R"]["min"]) + c.root_track[f][1] for f in range(n + 1)]
    off = -min(lows)
    c.root_track = [np.array([0.0, p[1] + off, 0.0]) for p in c.root_track]
    c.root_track[n] = c.root_track[0].copy()
    return round(float(speed), 3)


def stehen(model):
    """Bereitschaftshaltung wie in Rematch: Knie locker gebeugt, leicht vor, Arme locker, Atmen, Gewicht wandert."""
    c = K.Clip(model, "stehen", 48)
    base = dict(hipR=(10, 0, 4), hipL=(8, 0, -5), kneeR=(-16,), kneeL=(-14,), shR=(4, 0, 9), shL=(2, 0, -9),
                elR=(22,), elL=(20,), spine=(-6, 0, 0), torso1=(0, 0, 0), chest=(0, 0, 0), head=(4, 0, 0),
                legs=(0, 0, 0), root=(0, 0, 0))
    c.pose(0, **base)
    c.pose(12, **{**base, "chest": (2.5, 2, 0), "shR": (3, 0, 11), "shL": (1, 0, -11), "head": (3, 3, 0),
                  "root": (0, 0, 1.2), "kneeR": (-17,), "kneeL": (-12,)})
    c.pose(24, **{**base, "chest": (0, 0, 0), "head": (5, 0, 0)})
    c.pose(36, **{**base, "chest": (2.5, -2, 0), "shR": (3, 0, 11), "shL": (1, 0, -11), "head": (3, -3, 0),
                  "root": (0, 0, -1.2), "kneeR": (-12,), "kneeL": (-17,)})
    c.pose(48, **base)
    c.phase(0, 48, "LR", anchor="L")
    c.solve_root()
    tr = c.root_track
    c.root_track = [np.array([0.0, p[1], 0.0]) for p in tr]
    c.root_track[48] = c.root_track[0].copy()
    return c, 0


def gehen(model):
    c = K.Clip(model, "gehen", 24)
    cycle(c, 12, {
        0: dict(hipR=(22, 0, 2), kneeR=(-4,), hipL=(-18, 0, -2), kneeL=(-14,), shR=(-16, 0, 7), elR=(22,),
                shL=(16, 0, -7), elL=(20,), spine=(-5, -4, 0), chest=(0, 5, 0), head=(4, 0, 0), legs=(0, 4, 0),
                torso1=(0, 0, 0), root=(0, 0, 0)),
        3: dict(hipR=(16, 0, 2), kneeR=(-16,), hipL=(-14, 0, -2), kneeL=(-30,), shR=(-10, 0, 7), elR=(22,),
                shL=(10, 0, -7), elL=(22,), spine=(-6, -2, 0), chest=(0, 3, 0), legs=(0, 2, 0), root=(0, 0, 1)),
        6: dict(hipR=(2, 0, 2), kneeR=(-8,), hipL=(10, 0, -2), kneeL=(-52,), shR=(0, 0, 7), elR=(20,),
                shL=(0, 0, -7), elL=(20,), spine=(-5, 0, 0), chest=(0, 0, 0), legs=(0, 0, 0), root=(0, 0, 1.5)),
        9: dict(hipR=(-14, 0, 2), kneeR=(-6,), hipL=(22, 0, -2), kneeL=(-26,), shR=(12, 0, 7), elR=(20,),
                shL=(-12, 0, -7), elL=(22,), spine=(-5, 3, 0), chest=(0, -4, 0), legs=(0, -3, 0), root=(0, 0, 0.5)),
    })
    c.phase(0, 12, "R")
    c.phase(12, 24, "L")
    return c, finish(c, 24)


def laufen(model):
    """Lockerer Trab (Rematch-Standardlauf): leicht vor, Arme angewinkelt, kurze Flugphase."""
    c = K.Clip(model, "laufen", 16)
    cycle(c, 8, {
        0: dict(hipR=(30, 0, 2), kneeR=(-14,), hipL=(-22, 0, -2), kneeL=(-64,), shR=(-32, 0, 9), elR=(72,),
                shL=(34, 0, -9), elL=(80,), spine=(-12, -5, 0), torso1=(-2, 0, 0), chest=(-2, 7, 0),
                head=(6, -2, 0), legs=(0, 7, 0), root=(0, 0, 0)),
        2: dict(hipR=(10, 0, 2), kneeR=(-32,), hipL=(-6, 0, -2), kneeL=(-100,), shR=(-12, 0, 9), elR=(74,),
                shL=(14, 0, -9), elL=(78,), spine=(-14, -1, 0), chest=(-3, 2, 0), legs=(0, 2, 0), root=(0, 0, 1.5)),
        4: dict(hipR=(-26, 0, 2), kneeR=(-14,), hipL=(32, 0, -2), kneeL=(-82,), shR=(26, 0, 9), elR=(78,),
                shL=(-28, 0, -9), elL=(72,), spine=(-12, 5, 0), chest=(-2, -6, 0), legs=(0, -6, 0), root=(0, 0, 1)),
        6: dict(hipR=(-30, 0, 2), kneeR=(-62,), hipL=(38, 0, -2), kneeL=(-40,), shR=(32, 0, 9), elR=(78,),
                shL=(-34, 0, -9), elL=(74,), spine=(-12, 5, 0), chest=(-2, -7, 0), legs=(0, -7, 0), root=(0, 0, 0)),
    })
    c.phase(0, 4, "R")
    c.phase(4, 8, "air", land="L", carry=1.0, g=G)
    c.phase(8, 12, "L")
    c.phase(12, 16, "air", land="R", carry=1.0, g=G)
    return c, finish(c, 16)


def sprinten(model):
    """Sprint: starke Vorlage, hohe Knie, Fersen hoch, Arme pumpen kräftig, Kopf hebt gegen die Vorlage."""
    c = K.Clip(model, "sprinten", 14)
    cycle(c, 7, {
        0: dict(hipR=(36, 0, 2), kneeR=(-20,), hipL=(-30, 0, -2), kneeL=(-104,), shR=(-52, 0, 10), elR=(86,),
                shL=(62, 0, -10), elL=(92,), spine=(-24, -6, 0), torso1=(-6, 0, 0), chest=(-4, 8, 0),
                head=(16, -3, 0), legs=(0, 8, 0), root=(0, 0, 0)),
        2: dict(hipR=(8, 0, 2), kneeR=(-36,), hipL=(8, 0, -2), kneeL=(-124,), shR=(-14, 0, 10), elR=(88,),
                shL=(24, 0, -10), elL=(90,), spine=(-26, -1, 0), chest=(-5, 2, 0), legs=(0, 2, 0), root=(0, 0, 1.5)),
        4: dict(hipR=(-32, 0, 2), kneeR=(-12,), hipL=(62, 0, -2), kneeL=(-92,), shR=(46, 0, 10), elR=(80,),
                shL=(-50, 0, -10), elL=(88,), spine=(-24, 6, 0), chest=(-4, -8, 0), legs=(0, -8, 0), root=(0, 0, 1)),
        5: dict(hipR=(-38, 0, 2), kneeR=(-66,), hipL=(66, 0, -2), kneeL=(-70,), shR=(52, 0, 10), elR=(82,),
                shL=(-56, 0, -10), elL=(88,), spine=(-24, 6, 0), chest=(-4, -8, 0), legs=(0, -8, 0), root=(0, 0, 0)),
    })
    c.phase(0, 3, "R")
    c.phase(3, 7, "air", land="L", carry=1.0, g=G)
    c.phase(7, 10, "L")
    c.phase(10, 14, "air", land="R", carry=1.0, g=G)
    return c, finish(c, 14)


def luft(model):
    """Sprung/Fallen: Beine angezogen (eins vor, eins zurück), Arme zur Balance raus."""
    c = K.Clip(model, "luft", 6)
    c.pose(0, hipR=(24, 0, 3), kneeR=(-36,), hipL=(-8, 0, -3), kneeL=(-60,), shR=(-8, 0, 30), elR=(40,),
           shL=(10, 0, -36), elL=(40,), spine=(-8, 0, 0), chest=(0, 0, 0), head=(6, 0, 0), legs=(0, 0, 0), root=(0, 0, 0))
    c.pose(6, hipR=(34, 0, 4), kneeR=(-56,), hipL=(-14, 0, -4), kneeL=(-82,), shR=(-14, 0, 44), elR=(34,),
           shL=(14, 0, -50), elL=(34,), spine=(-6, 0, 0), chest=(2, 0, 0), head=(8, 0, 0))
    return c, 0


def build():
    model = R.Model(json.load(open(VORLAGE)))
    data = json.load(open(VORLAGE))
    meta_path = os.path.join(ROOT, "rematch", "animationen", "meta.json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    anims = []
    for fn, loop in ((stehen, "loop"), (gehen, "loop"), (laufen, "loop"), (sprinten, "loop"), (luft, "hold")):
        c, speed = fn(model)
        j = c.to_json()
        j["loop"] = loop
        anims.append(j)
        meta[c.name] = {"priority": 0, "speed_at": speed}
        print(c.name, "Tempo", speed, "Blöcke/s")
    data["animations"] = anims
    json.dump(data, open(ZIEL, "w"), indent=1)
    json.dump(meta, open(meta_path, "w"), indent=1, ensure_ascii=False)
    print("exportiert:", export_anim.export(ZIEL, MOD_ANIM))


if __name__ == "__main__":
    build()
