#!/usr/bin/env python3
"""Möbel für den Survival-Juwelenshop als Item-Modelle aus Quadern mit Vanilla-Texturen (kuby:furniture/<id>).

Die Stücke stehen in tools/furniture_models.py (je Reiter wie im Shop), Namen und Preise in
tools/jewelshop_survival_data.py. Dieses Skript schreibt:
  resourcepack/KubyUI/assets/kuby/models/furniture/<id>.json   Modell (Elemente, Texturen, Anzeige im Inventar)
  resourcepack/KubyUI/assets/kuby/items/furniture/<id>.json    Item-Definition (mit Tönung für Blätter und Gras)
  KubySurvival/src/main/resources/moebel.yml                   Katalog fürs Plugin (Reiter, Preis, Fläche, Sitze, Licht)

Koordinaten in Pixeln (16 = 1 Block): Boden y = 0, Mitte des ersten Blocks x = z = 8, vorne = Norden (kleines z).
Das ItemDisplay im Spiel dreht um 180 Grad, die Vorderseite zeigt dann zum Spieler, der das Möbel aufstellt.
Breite Stücke reichen nach +x (zweiter Block x 16..32), tiefe nach +z (hinten, zweiter Block z 16..32).

Aufruf: python3 tools/build_furniture.py [<pack>] [--sheet <vorschau.png>] [--only <reiter|id>]
  --sheet zeichnet jedes Modell im Inventar-Blickwinkel neben das Referenzbild aus dem Shop (zum Vergleichen).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import jewelshop_survival_data as data  # noqa: E402

CATALOG = os.path.join(REPO, "KubySurvival-v1.0.0/KubySurvival/src/main/resources/moebel.yml")
ICONS = os.path.join(HERE, "assets/jewelshop_survival")

# Blickwinkel im Inventar (wie die Shop-Bilder: Vorderseite links unten, Oberseite sichtbar)
GUI_ROT = [30, 135, 0]


def tex(name):
    """Kurzname einer Vanilla-Blocktextur oder volle ID."""
    return name if ":" in name else "minecraft:block/" + name


# Eigene Texturen (Stoffe, Tischdecken, Bildschirme): Name -> 16 x 16-Bild, im Pack unter kuby:block/furniture/<name>
CUSTOM = {}


def custom(name, image):
    """Meldet eine eigene Textur an und gibt ihre ID zurück."""
    CUSTOM[name] = image
    return "kuby:block/furniture/" + name


def write_textures(pack):
    d = os.path.join(pack, "assets/kuby/textures/block/furniture")
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        if f.endswith(".png") and f[:-4] not in CUSTOM:
            os.remove(os.path.join(d, f))
    for name, img in CUSTOM.items():
        img.save(os.path.join(d, name + ".png"))


class Model:
    """Ein Möbelstück: Elemente (Quader und Ebenen) mit Texturen; Fläche in Blöcken, Sitze und Licht für den Katalog."""

    def __init__(self, mid):
        self.id = mid
        self.elements = []
        self.textures = {}
        self.tints = []
        self.width = 1          # Blöcke nach +x
        self.depth = 1          # Blöcke nach +z (nach hinten)
        self.seats = []         # Sitzpunkte (x, z) in Pixeln
        self.seat_y = 0         # Sitzhöhe in Pixeln
        self.light = -1         # Lichtstärke 1..15 oder -1
        self.gui_scale = None   # Größe im Inventar (sonst automatisch)

    # ---------- Texturen und Tönung ----------

    def _t(self, name):
        full = tex(name)
        for k, v in self.textures.items():
            if v == full:
                return "#" + k
        k = str(len(self.textures))
        self.textures[k] = full
        return "#" + k

    def _tint(self, rgb):
        if rgb is None:
            return None
        if rgb not in self.tints:
            self.tints.append(rgb)
        return self.tints.index(rgb)

    # ---------- Bausteine ----------

    def box(self, f, t, texture, faces="nsewud", uv=None, rot=None, tint=None, shade=True):
        """Quader von f nach t (Pixel). texture: Name oder {Seite: Name}; faces: Auswahl aus n s e w u d;
        uv: {Seite: [u0, v0, u1, v1]} (sonst wie Vanilla nach Lage); rot: (Achse, Winkel, Ursprung)."""
        names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
        fs = {}
        for c in faces:
            d = names[c]
            name = texture.get(d, texture.get(c, texture.get("*"))) if isinstance(texture, dict) else texture
            if name is None:
                continue
            face = {"texture": self._t(name)}
            if uv and (d in uv or c in uv):
                face["uv"] = [round(float(x), 3) for x in uv.get(d, uv.get(c))]
            ti = self._tint(tint)
            if ti is not None:
                face["tintindex"] = ti
            fs[d] = face
        el = {"from": [round(float(x), 3) for x in f], "to": [round(float(x), 3) for x in t], "faces": fs}
        for i in range(3):
            if not (-16 <= f[i] <= 32 and -16 <= t[i] <= 32):
                raise ValueError("%s: Element außerhalb -16..32: %s %s" % (self.id, f, t))
        if rot:
            axis, angle, origin = rot
            if angle % 22.5 or abs(angle) > 45:
                raise ValueError("%s: Winkel %s" % (self.id, angle))
            el["rotation"] = {"axis": axis, "angle": float(angle), "origin": [round(float(x), 3) for x in origin]}
        if not shade:
            el["shade"] = False
        self.elements.append(el)
        return self

    def plane_z(self, x0, y0, x1, y1, z, texture, uv=None, rot=None, tint=None):
        """Senkrechte Ebene bei z (beidseitig)."""
        u = {"n": uv, "s": uv} if uv else None
        return self.box((x0, y0, z), (x1, y1, z), texture, "ns", u, rot, tint)

    def plane_x(self, z0, y0, z1, y1, x, texture, uv=None, rot=None, tint=None):
        u = {"e": uv, "w": uv} if uv else None
        return self.box((x, y0, z0), (x, y1, z1), texture, "ew", u, rot, tint)

    def plane_y(self, x0, z0, x1, z1, y, texture, uv=None, rot=None, tint=None):
        u = {"u": uv, "d": uv} if uv else None
        return self.box((x0, y, z0), (x1, y, z1), texture, "ud", u, rot, tint)

    def cross(self, cx, y0, cz, w, h, texture, uv=None, tint=None):
        """Pflanze als zwei gekreuzte Ebenen (45 Grad), Breite w und Höhe h, Mitte (cx, cz)."""
        r = w / 2
        self.plane_z(cx - r, y0, cx + r, y0 + h, cz, texture, uv, ("y", 45, (cx, y0, cz)), tint)
        self.plane_z(cx - r, y0, cx + r, y0 + h, cz, texture, uv, ("y", -45, (cx, y0, cz)), tint)
        return self

    def legs(self, x0, z0, x1, z1, t, h, texture, y0=0):
        """Vier Beine (Dicke t, Höhe h) an den Ecken des Rechtecks (x0, z0)..(x1, z1)."""
        for x, z in ((x0, z0), (x1 - t, z0), (x0, z1 - t), (x1 - t, z1 - t)):
            self.box((x, y0, z), (x + t, y0 + h, z + t), texture)
        return self

    # ---------- Ausgabe ----------

    def bounds(self):
        pts = []
        for el in self.elements:
            pts += [el["from"], el["to"]]
        a = np.array(pts, dtype=float)
        return a.min(axis=0), a.max(axis=0)

    def height(self):
        """Höhe in Blöcken (für die Klickfläche)."""
        return round(max(0.25, float(self.bounds()[1][1]) / 16), 2)

    def json(self):
        # Inventar: gedrehte Eckpunkte messen, das Bild füllt 14 von 16 Pixeln, seine Mitte liegt in der Feldmitte
        m = rot_matrix(*GUI_ROT)
        pts = np.array([[x, y, z] for el in self.elements for x in (el["from"][0], el["to"][0])
                        for y in (el["from"][1], el["to"][1]) for z in (el["from"][2], el["to"][2])], dtype=float) - 8
        q = (m @ pts.T).T
        span = max(q[:, 0].max() - q[:, 0].min(), q[:, 1].max() - q[:, 1].min())
        s = self.gui_scale or round(min(1.0, 14.0 / span), 3)
        mid = np.array([(q[:, 0].max() + q[:, 0].min()) / 2, (q[:, 1].max() + q[:, 1].min()) / 2])
        disp = {
            "gui": {"rotation": GUI_ROT, "translation": [0, 0, 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        }
        disp["gui"]["translation"] = [round(float(-mid[0] * s), 2), round(float(-mid[1] * s), 2), 0]
        tx = dict(self.textures)
        tx["particle"] = tx.get("0", "minecraft:block/oak_planks")
        return {"textures": tx, "elements": self.elements, "display": disp}

    def item_json(self):
        m = {"type": "minecraft:model", "model": "kuby:furniture/" + self.id}
        if self.tints:
            m["tints"] = [{"type": "minecraft:constant", "value": -16777216 | (r << 16) | (g << 8) | b}
                          for (r, g, b) in self.tints]
        return {"model": m}


def rot_matrix(rx_deg, ry_deg, rz_deg=0):
    """Wie Quaternionf.rotationXYZ: v' = Rx · Ry · Rz · v."""
    a, b, c = (math.radians(x) for x in (rx_deg, ry_deg, rz_deg))
    rx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    ry = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return rx @ ry @ rz


# =====================================================================================================================
# Vorschau im Inventar-Blickwinkel
# =====================================================================================================================

_TEX = {}


def load_tex(ident, vanilla):
    if ident in _TEX:
        return _TEX[ident]
    ns, path = ident.split(":", 1)
    roots = [os.path.join(REPO, "resourcepack/KubyUI"), vanilla]
    img = None
    for r in roots:
        p = os.path.join(r, "assets", ns, "textures", path + ".png")
        if os.path.exists(p):
            img = Image.open(p).convert("RGBA")
            break
    if img is None:
        img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    if img.height > img.width:  # Animation: erstes Bild
        img = img.crop((0, 0, img.width, img.width))
    a = np.array(img).astype(float) / 255
    _TEX[ident] = a
    return a


CORNERS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}


def default_uv(d, f, t):
    if d == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if d == "up":
        return [f[0], f[2], t[0], t[2]]
    if d == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if d == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if d == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]


def axis_rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def quads(model):
    """(Punkte in Pixeln, UVs 0..1, Textur-ID, Tönung)."""
    out = []
    for el in model.elements:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for d, face in el["faces"].items():
            pts = np.array([[f[i] + (t[i] - f[i]) * c[i] for i in range(3)] for c in CORNERS[d]], dtype=float)
            uv = face.get("uv") or default_uv(d, f, t)
            u0, v0, u1, v1 = [x / 16.0 for x in uv]
            uvs = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
            if rot:
                o = np.array(rot["origin"], dtype=float)
                pts = (axis_rot(rot["axis"], rot["angle"]) @ (pts - o).T).T + o
            ident = model.textures[face["texture"][1:]]
            tint = model.tints[face["tintindex"]] if "tintindex" in face else None
            out.append((pts, uvs, ident, tint))
    return out


def render(model, size, vanilla, rot=None, scale=None):
    """Ortho-Bild wie im Inventar: Drehung GUI_ROT, Modell füllt das Feld (oder Faktor scale)."""
    rot = rot or GUI_ROT
    m = rot_matrix(*rot)
    qs = quads(model)
    allp = np.concatenate([q[0] for q in qs]) - 8
    proj = (m @ allp.T).T
    lo, hi = proj.min(axis=0), proj.max(axis=0)
    k = scale or 0.92 * size / max(hi[0] - lo[0], hi[1] - lo[1])
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    img = np.zeros((size, size, 4))
    zb = np.full((size, size), -np.inf)
    light = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
    light2 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
    for pts, uvs, ident, tint in qs:
        p = (m @ (pts - 8).T).T
        sp = np.stack([(p[:, 0] - cx) * k + size / 2, size / 2 - (p[:, 1] - cy) * k, p[:, 2]], axis=1)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        ln = np.linalg.norm(n)
        if ln == 0:
            continue
        n = n / ln
        if n[2] <= 1e-6:
            continue
        # Licht ungefähr wie bei Items im Inventar (zwei Richtungen, Grundhelligkeit)
        world_n = np.linalg.inv(m) @ n
        shade = min(1.0, 0.4 + 0.6 * (max(0, world_n @ light) + max(0, world_n @ light2)) * 0.75)
        texa = load_tex(ident, vanilla)
        for tri in ((0, 1, 2), (0, 2, 3)):
            fill(img, zb, sp[list(tri)], np.array(uvs)[list(tri)], texa, shade, tint)
    out = (np.clip(img, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def fill(img, zb, q, UV, tex_a, shade, tint):
    size = img.shape[0]
    x, y, z = q[:, 0], q[:, 1], q[:, 2]
    x0, x1 = max(int(math.floor(x.min())), 0), min(int(math.ceil(x.max())), size - 1)
    y0, y1 = max(int(math.floor(y.min())), 0), min(int(math.ceil(y.max())), size - 1)
    if x0 > x1 or y0 > y1:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])
    if abs(area) < 1e-9:
        return
    w0 = ((x[1] - gx) * (y[2] - gy) - (x[2] - gx) * (y[1] - gy)) / area
    w1 = ((x[2] - gx) * (y[0] - gy) - (x[0] - gx) * (y[2] - gy)) / area
    w2 = 1 - w0 - w1
    inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
    if not inside.any():
        return
    depth = w0 * z[0] + w1 * z[1] + w2 * z[2]
    u = w0 * UV[0, 0] + w1 * UV[1, 0] + w2 * UV[2, 0]
    v = w0 * UV[0, 1] + w1 * UV[1, 1] + w2 * UV[2, 1]
    th, tw = tex_a.shape[:2]
    ti = np.clip((u * tw).astype(int), 0, tw - 1)
    tj = np.clip((v * th).astype(int), 0, th - 1)
    col = tex_a[tj, ti]
    tgt_z = zb[y0:y1 + 1, x0:x1 + 1]
    mask = inside & (depth > tgt_z) & (col[..., 3] > 0.1)
    if not mask.any():
        return
    rgb = col[..., :3] * shade
    if tint is not None:
        rgb = rgb * (np.array(tint) / 255.0)
    tgt = img[y0:y1 + 1, x0:x1 + 1]
    tgt[mask, :3] = rgb[mask]
    tgt[mask, 3] = 1
    tgt_z[mask] = depth[mask]


def sheet(models, path, vanilla):
    """Vergleich: je Möbel Referenzbild (30 px, 4fach), eigenes Modell in 30 px (4fach) und groß."""
    k = 4
    cw, ch = 3 * 30 * k + 22, 30 * k + 18
    cols = 3
    rows = (len(models) + cols - 1) // cols
    o = Image.new("RGB", (cols * cw, rows * ch), (33, 21, 9))
    dr = ImageDraw.Draw(o)
    for i, m in enumerate(models):
        x, y = (i % cols) * cw + 4, (i // cols) * ch + 2
        ref = os.path.join(ICONS, "f_%s.png" % m.id)
        if os.path.exists(ref):
            r = Image.open(ref).convert("RGBA").resize((30 * k, 30 * k), Image.NEAREST)
            o.paste(r, (x, y), r)
        small = render(m, 30, vanilla).resize((30 * k, 30 * k), Image.NEAREST)
        o.paste(small, (x + 30 * k + 6, y), small)
        img = render(m, 30 * k, vanilla)
        o.paste(img, (x + 2 * 30 * k + 12, y), img)
        dr.text((x, y + 30 * k + 3), m.id, fill=(240, 180, 100))
    o.save(path)


# =====================================================================================================================
# Ausgabe
# =====================================================================================================================

def yaml_str(s):
    return '"' + s.replace('"', '\\"') + '"'


def local(v):
    """Modell-Pixel (Mitte des ersten Blocks bei 8) in Blöcke im Möbel-Rahmen (um 180 Grad gedreht)."""
    return round(-(v - 8) / 16, 3) + 0.0


def catalog(models):
    by_id = {m.id: m for m in models}
    lines = ["# Möbel im Survival-Juwelenshop. Generiert von tools/build_furniture.py, nicht von Hand ändern.",
             "# reiter: id;Name;Untertitel. moebel: Reiter, Name, Preis (Juwelen), Felder (Blöcke [x, z] ab dem Block, auf",
             "# den man klickt), Höhe (Blöcke, Klickfläche), Sitze ([x, z] in Blöcken ab der Mitte dieses Blocks),",
             "# Sitzhöhe (Blöcke), Licht (Stärke, -1 = keins). Alles im Möbel-Rahmen: +z = vorne (zum Spieler, der es",
             "# aufstellt); das ItemDisplay dreht das Modell um 180 Grad, Modell-x und -z zählen also umgekehrt.",
             "reiter:"]
    for t in data.FURNITURE:
        lines.append("  - %s" % yaml_str("%s;%s;%s" % (t["id"], t["name"], t["sub"])))
    lines.append("moebel:")
    for t in data.FURNITURE:
        for fid, name, price in t["items"]:
            m = by_id[fid]
            seats = ", ".join("[%s, %s]" % (local(sx), local(sz)) for sx, sz in m.seats)
            cells = ", ".join("[%d, %d]" % (-i, -j) for i in range(m.width) for j in range(m.depth))
            lines.append("  %s: {reiter: %s, name: %s, preis: %d, felder: [%s], hoehe: %s, sitze: [%s], "
                         "sitzhoehe: %s, licht: %d}" % (fid, t["id"], yaml_str(name), price, cells,
                                                       m.height(), seats, round(m.seat_y / 16, 3), m.light))
    with open(CATALOG, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def write(models, pack):
    mdir = os.path.join(pack, "assets/kuby/models/furniture")
    idir = os.path.join(pack, "assets/kuby/items/furniture")
    for d in (mdir, idir):
        os.makedirs(d, exist_ok=True)
    keep = {m.id + ".json" for m in models}
    for d in (mdir, idir):
        for f in os.listdir(d):
            if f.endswith(".json") and f not in keep:
                os.remove(os.path.join(d, f))
    for m in models:
        with open(os.path.join(mdir, m.id + ".json"), "w", encoding="utf-8") as f:
            json.dump(m.json(), f, separators=(",", ":"))
        with open(os.path.join(idir, m.id + ".json"), "w", encoding="utf-8") as f:
            json.dump(m.item_json(), f, separators=(",", ":"))


def all_models():
    import furniture_models as fm
    models = fm.build()
    want = [fid for t in data.FURNITURE for fid, _n, _p in t["items"]]
    have = {m.id for m in models}
    missing = [x for x in want if x not in have]
    if missing:
        raise SystemExit("Modelle fehlen: %s" % ", ".join(missing))
    order = {fid: i for i, fid in enumerate(want)}
    return sorted([m for m in models if m.id in order], key=lambda m: order[m.id])


def main():
    args = sys.argv[1:]
    pack = os.path.join(REPO, "resourcepack/KubyUI")
    sheet_path, only = None, None
    i = 0
    while i < len(args):
        if args[i] == "--sheet":
            sheet_path = args[i + 1]
            i += 2
        elif args[i] == "--only":
            only = args[i + 1]
            i += 2
        else:
            pack = args[i]
            i += 1
    if sheet_path:
        import furniture_models as fm
        models = fm.build()
        write_textures(pack)
        if only:
            tab = next((t for t in data.FURNITURE if t["id"] == only), None)
            ids = [fid for fid, _n, _p in tab["items"]] if tab else only.split(",")
            models = [m for m in models if m.id in ids]
            models.sort(key=lambda m: ids.index(m.id))
        sheet(models, sheet_path, os.environ.get("KUBY_VANILLA", "vanilla"))
        print("Vorschau:", sheet_path, len(models), "Modelle")
        return
    models = all_models()
    write_textures(pack)
    write(models, pack)
    catalog(models)
    print("ok furniture:", len(models), "Modelle")


if __name__ == "__main__":
    # als Modul laufen lassen: furniture_models importiert build_furniture, die eigenen Texturen landen dort
    import build_furniture
    build_furniture.main()
