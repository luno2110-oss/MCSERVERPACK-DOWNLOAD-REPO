#!/usr/bin/env python3
"""Icons für alle Vanilla-Items (für Dialoge, z. B. die Shop-Seiten): ein Bild, wenige Schriften.

Jedes Item bekommt eine 32-px-Zelle in assets/kuby/textures/k/items.png: Blöcke als 3D-Modell wie im Inventar (Modell
mit display.gui gedreht und skaliert, Seiten schattiert wie bei Licht von der Seite), Items flach aus ihren Ebenen
(layer0, layer1 …, mit Einfärbung wie Laub, Gras, Leder, Tränke). Spezial-Modelle (Truhen, Betten, Banner, Köpfe,
Schilde) fehlen, dafür nimmt das Plugin eigene Icons.

Schriften kuby:k/x<s> für jede Verschiebung s = -24..8 (wie die Icon-Schriften k/i<s>, Oberkante = Zeile * 9 + s):
je eine Quelle für 32 px (Zeichen E000 + Nummer) und ab s >= -9 für 16 px (E800 + Nummer), dazu die Leerzeichen.
Für das Plugin: KubyCore …/resources/ui/items.tsv mit MATERIAL TAB Nummer TAB Vorschub 32 TAB Vorschub 16.

Aufruf: KUBY_VANILLA=<vanilla-assets> python3 tools/build_item_icons.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(HERE, "preview3d"))
import r3d  # noqa: E402

VAN = os.environ.get("KUBY_VANILLA")
PACK = os.path.join(REPO, "resourcepack/KubyUI")
TSV = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/ui/items.tsv")
CELL, COLS = 32, 32
BIG, SMALL = 0xE000, 0xE800
SHIFTS = range(-24, 9)

GRASS = 0x79C05A


def argb(v):
    v &= 0xFFFFFFFF
    return ((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0


def tint_color(t):
    """Farbe einer Einfärbung (Item-Definition), None = keine."""
    if t is None:
        return None
    typ = t.get("type", "").split(":")[-1]
    if typ == "constant":
        return argb(t.get("value", -1))
    if typ == "grass":
        return argb(GRASS)
    if "default" in t:
        return argb(t["default"])
    return None


def pick(m):
    """Item-Definition -> ('model', Modell-ID, Einfärbungen) oder None (Spezial-Modell, leer)."""
    if not isinstance(m, dict):
        return None
    typ = m.get("type", "minecraft:model").split(":")[-1]
    if typ == "model":
        return "model", m["model"], m.get("tints", [])
    if typ in ("select", "condition", "range_dispatch"):
        nxt = m.get("fallback") or m.get("on_false")
        if nxt is None and m.get("cases"):
            nxt = m["cases"][0].get("model")
        if nxt is None and m.get("entries"):
            nxt = m["entries"][0].get("model")
        if nxt is None:
            nxt = m.get("on_true")
        return pick(nxt)
    if typ == "composite":
        for sub in m.get("models", []):
            r = pick(sub)
            if r:
                return r
    return None


def model_chain(ident):
    """display.gui und gui_light aus der Eltern-Kette."""
    gui, light, cur, n = None, None, ident, 0
    while cur and n < 12:
        n += 1
        j = r3d.load_json("models", cur)
        if j is None:
            break
        if gui is None and "gui" in j.get("display", {}):
            gui = j["display"]["gui"]
        if light is None and "gui_light" in j:
            light = j["gui_light"]
        cur = j.get("parent")
    return gui, light or "side"


def flat_icon(textures, tints):
    """Flaches Item: Ebenen übereinander, 16 px -> 32 px."""
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(5):
        ref = textures.get("layer%d" % i)
        if ref is None:
            break
        ref = r3d.tex_ref(textures, ref)
        f = r3d.find("textures", ref, ".png")
        if f is None:
            continue
        im = Image.open(f).convert("RGBA")
        im = im.crop((0, 0, im.width, im.width)).resize((16, 16), Image.NEAREST)
        col = tint_color(tints[i]) if i < len(tints) else None
        if col:
            a = np.asarray(im, dtype=float)
            a[..., 0] *= col[0]
            a[..., 1] *= col[1]
            a[..., 2] *= col[2]
            im = Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")
        out.alpha_composite(im)
    if out.getchannel("A").getbbox() is None:
        return None
    return out.resize((CELL, CELL), Image.NEAREST)


def face_tints(ident):
    """Je Fläche der Modell-Elemente der tintindex (in derselben Reihenfolge wie r3d.model_quads)."""
    elements, _, _ = r3d.resolve_model(ident)
    out = []
    for el in elements or []:
        for d, face in el.get("faces", {}).items():
            out.append(face.get("tintindex", -1))
    return out


def cube_quads(tex):
    """Würfel 0..16 mit einer Textur auf allen Seiten (für Spezial-Modelle wie Shulker-Kisten)."""
    out = []
    for d, corners in r3d.CORNERS.items():
        pts = [tuple(16 * c for c in k) for k in corners]
        out.append((pts, [(0, 0), (0, 1), (1, 1), (1, 0)], tex))
    return out


def render_3d(ident, tints, gui, light, quads=None):
    """Block-Modell wie im Inventar: Transformation display.gui, Orthografie, Tiefenpuffer, Nächster-Nachbar-Texturen."""
    if quads is None:
        quads = r3d.model_quads(ident)
    if not quads:
        return None
    tidx = face_tints(ident) if ident else []
    rot = [math.radians(a) for a in (gui or {}).get("rotation", [0, 0, 0])]
    trans = (gui or {}).get("translation", [0, 0, 0])
    scale = (gui or {}).get("scale", [1, 1, 1])
    m = r3d.tr(trans[0] / 16, trans[1] / 16, trans[2] / 16) @ r3d.rx(rot[0]) @ r3d.ry(rot[1]) @ r3d.rz(rot[2]) \
        @ r3d.sc(scale[0], scale[1], scale[2]) @ r3d.tr(-0.5, -0.5, -0.5)
    img = np.zeros((CELL, CELL, 4))
    depth = np.full((CELL, CELL), -1e9)
    ys, xs = np.mgrid[0:CELL, 0:CELL]
    px = (xs + 0.5) / CELL - 0.5
    py = 0.5 - (ys + 0.5) / CELL
    for qi, (pts, uvs, tid) in enumerate(quads):
        p = r3d.apply(m, np.array(pts, dtype=float) / 16.0)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        if n[2] <= 1e-9:      # Rückseite (zeigt vom Betrachter weg)
            continue
        tex = r3d.texture(tid)
        th, tw = tex.shape[:2]
        if light == "front":
            shade = 1.0
        else:
            # Licht wie im Inventar (Seitenlicht): oben hell, die beiden sichtbaren Seiten unterschiedlich
            ln = n / np.linalg.norm(n)
            shade = 0.45 + 0.55 * max(0.0, ln[1] * 0.85 + ln[0] * -0.35 + ln[2] * 0.55)
            shade = min(1.0, shade + 0.12)
        col = None
        ti = tidx[qi] if qi < len(tidx) else -1
        if ti >= 0 and ti < len(tints):
            col = tint_color(tints[ti])
        elif ti >= 0 and "grass" in tid or (ti >= 0 and "leaves" in tid):
            col = argb(GRASS)
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = p[list(tri)]
            ua, ub, uc = [np.array(uvs[k]) for k in tri]
            x0, x1 = min(a[0], b[0], c[0]), max(a[0], b[0], c[0])
            y0, y1 = min(a[1], b[1], c[1]), max(a[1], b[1], c[1])
            mask = (px >= x0 - 1e-6) & (px <= x1 + 1e-6) & (py >= y0 - 1e-6) & (py <= y1 + 1e-6)
            if not mask.any():
                continue
            det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(det) < 1e-12:
                continue
            l1 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / det
            l2 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / det
            l3 = 1 - l1 - l2
            inside = mask & (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not inside.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
            v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            sample = tex[ty, tx]
            ok = inside & (sample[..., 3] > 0.1) & (z > depth)
            if not ok.any():
                continue
            rgb = sample[..., :3] * shade
            if col:
                rgb = rgb * np.array(col)
            img[ok, :3] = rgb[ok]
            img[ok, 3] = np.maximum(img[ok, 3], sample[ok, 3])
            depth[ok] = z[ok]
    if img[..., 3].max() <= 0:
        return None
    return Image.fromarray((img.clip(0, 1) * 255).astype(np.uint8), "RGBA")


def icon(name):
    if name.endswith("shulker_box") and r3d.find("textures", "minecraft:block/" + name, ".png"):
        gui, _ = model_chain("minecraft:block/block")
        return render_3d(None, [], gui, "side", cube_quads("minecraft:block/" + name))
    j = r3d.load_json("items", "minecraft:" + name)
    if not j:
        return None
    sel = pick(j.get("model"))
    if not sel:
        return None
    _, ident, tints = sel
    elements, textures, generated = r3d.resolve_model(ident)
    if generated:
        return flat_icon(textures, tints)
    if not elements:
        return None
    gui, light = model_chain(ident)
    return render_3d(ident, tints, gui, light)


def advance(img, height):
    bbox = img.getchannel("A").getbbox()
    w = bbox[2] if bbox else 0
    return int(0.5 + w * height / CELL) + 1


def main():
    if not VAN:
        sys.exit("KUBY_VANILLA fehlt (Pfad zu den Vanilla-Assets)")
    r3d.ROOTS[:] = [VAN]
    names = sorted(f[:-5] for f in os.listdir(os.path.join(VAN, "assets/minecraft/items")) if f.endswith(".json"))
    cells, rows = [], []
    for name in names:
        try:
            im = icon(name)
        except Exception as e:  # einzelne kaputte Modelle überspringen
            print("übersprungen", name, e)
            im = None
        if im is None:
            continue
        cells.append(im)
        rows.append((name.upper(), len(cells) - 1, advance(im, 32), advance(im, 16)))
    n = len(cells)
    assert n < SMALL - BIG, "zu viele Items für den Zeichenbereich"
    nrows = (n + COLS - 1) // COLS
    sheet = Image.new("RGBA", (COLS * CELL, nrows * CELL), (0, 0, 0, 0))
    for i, im in enumerate(cells):
        sheet.paste(im, ((i % COLS) * CELL, (i // COLS) * CELL))
    tex_dir = os.path.join(PACK, "assets/kuby/textures/k")
    os.makedirs(tex_dir, exist_ok=True)
    sheet.save(os.path.join(tex_dir, "items.png"), optimize=True)

    def grid(first):
        out = []
        for r in range(nrows):
            row = ""
            for c in range(COLS):
                i = r * COLS + c
                row += chr(first + i) if i < n else "\u0000"
            out.append(row)
        return out

    with open(os.path.join(PACK, "assets/kuby/font/k/i0.json"), encoding="utf-8") as f:
        space = [p for p in json.load(f)["providers"] if p["type"] == "space"]
    fdir = os.path.join(PACK, "assets/kuby/font/k")
    for s in SHIFTS:
        providers = list(space)
        providers.append({"type": "bitmap", "file": "kuby:k/items.png", "height": 32, "ascent": 7 - s, "chars": grid(BIG)})
        if 7 - s <= 16:
            providers.append({"type": "bitmap", "file": "kuby:k/items.png", "height": 16, "ascent": 7 - s, "chars": grid(SMALL)})
        with open(os.path.join(fdir, "x%d.json" % s), "w", encoding="utf-8") as f:
            json.dump({"providers": providers}, f, ensure_ascii=True, separators=(",", ":"))
    os.makedirs(os.path.dirname(TSV), exist_ok=True)
    with open(TSV, "w", encoding="utf-8") as f:
        f.write("# MATERIAL\tNummer\tVorschub32\tVorschub16 (tools/build_item_icons.py)\n")
        for r in rows:
            f.write("%s\t%d\t%d\t%d\n" % r)
    print("ok Item-Icons:", n, "von", len(names), "Items, Bild", sheet.size)


if __name__ == "__main__":
    main()
