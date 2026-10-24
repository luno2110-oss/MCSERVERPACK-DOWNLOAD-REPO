#!/usr/bin/env python3
"""3D-Vorschau für Anzeige-Entities (JSON von h3d.Scene), gerechnet wie der 1.21.11-Client:

ItemDisplay:  Pos · Ausrichtung · Transformation · Ry(pi) · Itemmodell (-0.5 zentriert)
BlockDisplay: Pos · Ausrichtung · Transformation · Blockmodell (0..1)
TextDisplay:  Pos · Ausrichtung · Transformation · diag(0.025, -0.025, 0.025) · translate(1 - w/2, -h)
Alle Flächen sind einseitig (Rückseiten werden weggelassen), generierte Items haben eine gespiegelte Rückseite.

Aufruf: KUBY_VANILLA=<vanilla-assets> r3d.py szene.json out.png "cam=x,y,z" "look=x,y,z" [fov=70] [w=960] [h=540]
        [ss=2] [ctx=slots|roulette] [hit=1]
Die Szene kommt aus run.sh (echte Plugin-Klassen gegen nachgebaute Bukkit-Teile). Nur zum Entwickeln.
"""
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(REPO, "tools"))
import preview_text as pt  # noqa: E402
import build_skins as bs  # noqa: E402

PACK = os.path.join(REPO, "resourcepack/KubyUI")
VAN = os.environ.get("KUBY_VANILLA", "vanilla")
ROOTS = [PACK, VAN]


# ---------------------------------------------------------------------------------------------------------------------
# Mathe
# ---------------------------------------------------------------------------------------------------------------------

def quat_mat(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0],
        [0, 0, 0, 1]], dtype=float)


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def sc(x, y, z):
    return np.diag([x, y, z, 1.0])


def apply(m, pts):
    p = np.asarray(pts, dtype=float)
    return p @ m[:3, :3].T + m[:3, 3]


# ---------------------------------------------------------------------------------------------------------------------
# Ressourcen
# ---------------------------------------------------------------------------------------------------------------------

def find(kind, ident, ext):
    ns, p = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    for r in ROOTS:
        f = os.path.join(r, "assets", ns, kind, p + ext)
        if os.path.exists(f):
            return f
    return None


TEX = {}


def texture(ident):
    if ident not in TEX:
        f = find("textures", ident, ".png")
        if f is None:
            img = np.zeros((2, 2, 4))
            img[..., 0] = 1
            img[..., 2] = 1
            img[..., 3] = 1
            img[0, 1, :3] = 0
            img[1, 0, :3] = 0
        else:
            im = Image.open(f).convert("RGBA")
            if im.height > im.width:  # animierte Textur: erstes Bild
                im = im.crop((0, 0, im.width, im.width))
            img = np.asarray(im, dtype=float) / 255.0
        TEX[ident] = img
    return TEX[ident]


def load_json(kind, ident):
    f = find(kind, ident, ".json")
    if f is None:
        return None
    with open(f, encoding="utf-8") as fh:
        return json.load(fh)


def resolve_model(ident):
    """Gibt (elemente, texturen, generated) zurück."""
    textures = {}
    elements = None
    generated = False
    cur = ident
    seen = 0
    while cur and seen < 12:
        seen += 1
        if cur in ("builtin/generated", "minecraft:builtin/generated"):
            generated = True
            break
        m = load_json("models", cur)
        if m is None:
            if cur.endswith("item/generated"):
                generated = True
            break
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        if elements is None and "elements" in m:
            elements = m["elements"]
        cur = m.get("parent")
        if cur and cur.endswith("item/generated") and elements is None:
            generated = True
            break
    return elements, textures, generated


def tex_ref(textures, ref, depth=0):
    while ref.startswith("#") and depth < 10:
        ref = textures.get(ref[1:], "missing")
        depth += 1
    return ref


# Ecken je Richtung: oben links, unten links, unten rechts, oben rechts (von außen gesehen), wie FaceInfo
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


def model_quads(ident):
    """Quads des Modells in Pixeln (0..16): [(4 Punkte, 4 UV, Textur-ID)]."""
    elements, textures, generated = resolve_model(ident)
    quads = []
    if generated:
        layer = tex_ref(textures, "#layer0")
        quads.append(([(0, 16, 8.5), (0, 0, 8.5), (16, 0, 8.5), (16, 16, 8.5)],
                      [(0, 0), (0, 1), (1, 1), (1, 0)], layer))
        quads.append(([(16, 16, 7.5), (16, 0, 7.5), (0, 0, 7.5), (0, 16, 7.5)],
                      [(1, 0), (1, 1), (0, 1), (0, 0)], layer))
        return quads
    for el in elements or []:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for d, face in el.get("faces", {}).items():
            pts = [tuple(f[i] + (t[i] - f[i]) * c[i] for i in range(3)) for c in CORNERS[d]]
            uv = face.get("uv") or default_uv(d, f, t)
            u0, v0, u1, v1 = [x / 16.0 for x in uv]
            corner_uv = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
            r = int(face.get("rotation", 0)) // 90
            uvs = [corner_uv[(j + r) % 4] for j in range(4)]
            if rot:
                o = np.array(rot.get("origin", [8, 8, 8]), dtype=float)
                a = math.radians(rot.get("angle", 0))
                m = {"x": rx, "y": ry, "z": rz}[rot.get("axis", "y")](a)
                p = apply(m, np.array(pts) - o)
                if rot.get("rescale"):
                    s = 1 / math.cos(a) if abs(math.cos(a)) > 1e-6 else 1
                    ax = "xyz".index(rot.get("axis", "y"))
                    for i in range(3):
                        if i != ax:
                            p[:, i] *= s
                pts = [tuple(x) for x in (p + o)]
            quads.append((pts, uvs, tex_ref(textures, face.get("texture", "#missing"))))
    return quads


def item_model(key):
    """Item-ID (z. B. kuby:casino/m_white) zu Modell-ID über assets/<ns>/items."""
    j = load_json("items", key)
    if j:
        m = j.get("model", {})
        while isinstance(m, dict) and m.get("type", "minecraft:model").endswith("model") is False:
            # Auswahl-Modelle: erstes Ziel nehmen
            m = m.get("fallback") or m.get("model") or (m.get("cases") or [{}])[0].get("model") or {}
        if isinstance(m, dict) and "model" in m:
            return m["model"]
    ns, p = key.split(":", 1)
    return ns + ":item/" + p


def block_model(material, state):
    name = material.split(":", 1)[1]
    bsj = load_json("blockstates", "minecraft:" + name)
    props = {}
    for k, v in re.findall(r"(\w+)=(\w+)", state or ""):
        if k == "material":
            continue
        k2 = re.sub(r"([A-Z])", r"_\1", k).lower()
        if k2 == "attached_face":
            k2 = "face"
        props[k2] = v.lower()
    if bsj and "variants" in bsj:
        best = None
        for key, var in bsj["variants"].items():
            conds = dict(c.split("=") for c in key.split(",") if "=" in c)
            if all(props.get(k, v) == v for k, v in conds.items()):
                best = var[0] if isinstance(var, list) else var
                break
        if best is None:
            var = next(iter(bsj["variants"].values()))
            best = var[0] if isinstance(var, list) else var
        return best["model"], best.get("x", 0), best.get("y", 0)
    return None, 0, 0


# ---------------------------------------------------------------------------------------------------------------------
# Raster
# ---------------------------------------------------------------------------------------------------------------------

class Raster:
    def __init__(self, w, h, cam, look, fov, bg=(0.55, 0.66, 0.85)):
        self.w, self.h = w, h
        self.cam = np.array(cam, dtype=float)
        f = np.array(look, dtype=float) - self.cam
        self.fwd = f / np.linalg.norm(f)
        r = np.cross(self.fwd, [0, 1, 0])
        self.right = r / np.linalg.norm(r)
        self.up = np.cross(self.right, self.fwd)
        self.f = 1 / math.tan(math.radians(fov) / 2)
        self.z = np.full((h, w), np.inf)
        self.img = np.zeros((h, w, 3))
        self.img[:] = bg
        self.trans = []
        # Kamera-Winkel wie Minecraft (für Billboards)
        self.yaw = math.degrees(math.atan2(-self.fwd[0], self.fwd[2]))
        self.pitch = math.degrees(-math.asin(max(-1, min(1, self.fwd[1]))))

    def project(self, p):
        d = p - self.cam
        x, y, z = d @ self.right, d @ self.up, d @ self.fwd
        k = self.f * self.h / 2
        return np.stack([self.w / 2 + x / z * k, self.h / 2 - y / z * k, z], axis=-1)

    def quad(self, pts, uvs, tex, shade=1.0, translucent=False, tint=None, offset=0.0):
        pts = np.asarray(pts, dtype=float)
        if offset:
            n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
            ln = np.linalg.norm(n)
            if ln > 0:
                pts = pts + n / ln * offset
        for tri in ((0, 1, 2), (0, 2, 3)):
            self.tri(pts[list(tri)], np.asarray(uvs, dtype=float)[list(tri)], tex, shade, translucent, tint)

    def tri(self, P, UV, tex, shade, translucent, tint):
        q = self.project(P)
        if np.any(q[:, 2] < 0.05):
            return
        x, y = q[:, 0], q[:, 1]
        area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])
        if area >= 0:  # Rückseite
            return
        if translucent:
            self.trans.append((float(np.mean(q[:, 2])), P, UV, tex, shade, tint))
            return
        self.fill(q, UV, tex, shade, tint, False)

    def fill(self, q, UV, tex, shade, tint, blend):
        x, y, z = q[:, 0], q[:, 1], q[:, 2]
        x0, x1 = max(int(math.floor(x.min())), 0), min(int(math.ceil(x.max())), self.w - 1)
        y0, y1 = max(int(math.floor(y.min())), 0), min(int(math.ceil(y.max())), self.h - 1)
        if x0 > x1 or y0 > y1:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])
        w0 = ((x[1] - gx) * (y[2] - gy) - (x[2] - gx) * (y[1] - gy)) / area
        w1 = ((x[2] - gx) * (y[0] - gy) - (x[0] - gx) * (y[2] - gy)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            return
        iz = w0 / z[0] + w1 / z[1] + w2 / z[2]
        depth = 1 / np.where(iz > 0, iz, 1e-9)
        u = (w0 * UV[0, 0] / z[0] + w1 * UV[1, 0] / z[1] + w2 * UV[2, 0] / z[2]) * depth
        v = (w0 * UV[0, 1] / z[0] + w1 * UV[1, 1] / z[1] + w2 * UV[2, 1] / z[2]) * depth
        th, tw = tex.shape[:2]
        ti = np.clip((u * tw).astype(int), 0, tw - 1)
        tj = np.clip((v * th).astype(int), 0, th - 1)
        col = tex[tj, ti]
        zb = self.z[y0:y1 + 1, x0:x1 + 1]
        a = col[..., 3]
        mask = inside & (depth < zb)
        if not blend:
            mask &= a > 0.1
        if not mask.any():
            return
        rgb = col[..., :3] * shade
        if tint is not None:
            rgb = rgb * np.array(tint)
        tgt = self.img[y0:y1 + 1, x0:x1 + 1]
        if blend:
            aa = a[..., None]
            tgt[mask] = (tgt * (1 - aa) + rgb * aa)[mask]
        else:
            tgt[mask] = rgb[mask]
            zb[mask] = depth[mask]

    def finish(self):
        for _, P, UV, tex, shade, tint in sorted(self.trans, key=lambda t: -t[0]):
            q = self.project(P)
            self.fill(q, UV, tex, shade, tint, True)
        return Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))


def shade_of(n):
    ln = np.linalg.norm(n)
    if ln == 0:
        return 1.0
    nx, ny, nz = n / ln
    return nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (1.0 if ny > 0 else 0.5)


def draw_quads(rs, m, quads, unit=1 / 16.0, light=True, offset=0.0):
    for pts, uvs, tid in quads:
        p = apply(m, np.array(pts) * unit)
        tex = texture(tid)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        rs.quad(p, uvs, tex, shade_of(n) if light else 1.0, offset=offset)


# ---------------------------------------------------------------------------------------------------------------------
# Entities
# ---------------------------------------------------------------------------------------------------------------------

def orientation(e, rs):
    bb = e.get("billboard", "FIXED")
    yaw, pitch = e.get("yaw", 0), e.get("pitch", 0)
    if bb in ("VERTICAL", "CENTER"):
        yaw = rs.yaw - 180
    if bb in ("HORIZONTAL", "CENTER"):
        pitch = -rs.pitch
    return ry(math.radians(-yaw)) @ rx(math.radians(pitch))


def transformation(e):
    t = e.get("transformation")
    if not t:
        return np.eye(4)
    return tr(*t["t"]) @ quat_mat(t["l"]) @ sc(*t["s"]) @ quat_mat(t["r"])


FONTS = pt.Fonts(ROOTS)


def text_image(comp, line_width, shadow, bg_argb):
    chars = []
    pt.flatten(comp, {"font": "minecraft:default", "color": "white"}, chars)
    # Zeilen, dann Umbruch an Leerzeichen
    lines = [[]]
    for ch, st in chars:
        if ch == "\n":
            lines.append([])
        else:
            lines[-1].append((ch, st))

    def adv(ch, st):
        return FONTS.glyph(st.get("font", "minecraft:default"), ch)[0] + (1 if st.get("bold") else 0)

    wrapped = []
    for line in lines:
        cur, wsum, last_space = [], 0, -1
        for ch, st in line:
            a = adv(ch, st)
            if wsum + a > line_width and cur:
                if last_space >= 0:
                    wrapped.append(cur[:last_space])
                    cur = cur[last_space + 1:]
                else:
                    wrapped.append(cur)
                    cur = []
                wsum = sum(adv(c, s) for c, s in cur)
                last_space = -1
            if ch == " ":
                last_space = len(cur)
            cur.append((ch, st))
            wsum += a
        wrapped.append(cur)
    widths = [sum(adv(c, s) for c, s in ln) for ln in wrapped]
    w = max(widths) if widths else 0
    h = len(wrapped) * 10 - 1
    img = Image.new("RGBA", (w + 1, h + 1), (0, 0, 0, 0))
    a = (bg_argb >> 24) & 255
    if a:
        img.paste(((bg_argb >> 16) & 255, (bg_argb >> 8) & 255, bg_argb & 255, a), (0, 0, w + 1, h + 1))
    for li, line in enumerate(wrapped):
        x = 1 + (w - widths[li]) / 2.0
        x = int(round(x))
        y0 = 1 + li * 10
        for ch, st in line:
            font = st.get("font", "minecraft:default")
            ad, cell, ascent, scale, _ = FONTS.glyph(font, ch)
            bold = bool(st.get("bold"))
            if cell is not None:
                col = pt.color_of(st.get("color")) or 0xFFFFFF
                top = y0 + 7 - ascent
                if shadow:
                    shc = ((col >> 16 & 255) // 4 << 16) | ((col >> 8 & 255) // 4 << 8) | ((col & 255) // 4)
                    s = pt.tint(cell, shc)
                    img.alpha_composite(s, (x + 1, top + 1))
                    if bold:
                        img.alpha_composite(s, (x + 2, top + 1))
                g = pt.tint(cell, col)
                img.alpha_composite(g, (x, top))
                if bold:
                    img.alpha_composite(g, (x + 1, top))
            x += ad + (1 if bold else 0)
    return np.asarray(img, dtype=float) / 255.0, w, h


def draw_text(rs, e, base):
    comp = e.get("text")
    if comp is None:
        return
    bg = e.get("backgroundColor", 0x40000000)
    if bg < 0:
        bg += 1 << 32
    tex, w, h = text_image(comp, e.get("lineWidth", 200), e.get("shadowed", False), bg)
    m = base @ sc(0.025, -0.025, 0.025) @ tr(1 - w / 2.0, -h, 0)
    # Bild deckt Textraum [-1, w] x [-1, h] ab
    p = apply(m, [(-1, -1, 0), (-1, h, 0), (w, h, 0), (w, -1, 0)])
    rs.quad(p, [(0, 0), (0, 1), (1, 1), (1, 0)], tex, 1.0, translucent=True)


def player_boxes(slim):
    aw = 3 if slim else 4
    return [
        ("head", (-4, 24, -4), (4, 32, 4), 0), ("hat", (-4, 24, -4), (4, 32, 4), 0.5),
        ("body", (-4, 12, -2), (4, 24, 2), 0), ("jacket", (-4, 12, -2), (4, 24, 2), 0.25),
        ("rarm", (-4 - aw, 12, -2), (-4, 24, 2), 0), ("rsleeve", (-4 - aw, 12, -2), (-4, 24, 2), 0.25),
        ("larm", (4, 12, -2), (4 + aw, 24, 2), 0), ("lsleeve", (4, 12, -2), (4 + aw, 24, 2), 0.25),
        ("rleg", (-4, 0, -2), (0, 12, 2), 0), ("rpants", (-4, 0, -2), (0, 12, 2), 0.25),
        ("lleg", (0, 0, -2), (4, 12, 2), 0), ("lpants", (0, 0, -2), (4, 12, 2), 0.25),
    ]


def draw_player(rs, e, skin_img):
    """Spieler stehend; lokale Achsen: l = linke Seite der Figur, u = oben, f = vorne."""
    yaw = math.radians(e.get("yaw", 0))
    f = np.array([-math.sin(yaw), 0, math.cos(yaw)])
    left = np.array([f[2], 0, -f[0]]) * -1  # Blick nach Süden (+Z): links = Osten (+X)
    left = np.array([math.cos(yaw), 0, math.sin(yaw)])
    up = np.array([0, 1.0, 0])
    pos = np.array(e["pos"], dtype=float)
    k = 0.9375 / 16
    tex = np.asarray(skin_img.convert("RGBA"), dtype=float) / 255.0
    th, tw = tex.shape[:2]

    def P(l, u, ff):
        return pos + (left * l + up * u + f * ff) * k

    for part, a, b, infl in player_boxes(True):
        l0, u0, f0 = a[0] - infl, a[1] - infl, a[2] - infl
        l1, u1, f1 = b[0] + infl, b[1] + infl, b[2] + infl
        uv = bs.UV[part]
        faces = {
            "front": [P(l0, u1, f1), P(l0, u0, f1), P(l1, u0, f1), P(l1, u1, f1)],
            "back": [P(l1, u1, f0), P(l1, u0, f0), P(l0, u0, f0), P(l0, u1, f0)],
            "right": [P(l0, u1, f0), P(l0, u0, f0), P(l0, u0, f1), P(l0, u1, f1)],
            "left": [P(l1, u1, f1), P(l1, u0, f1), P(l1, u0, f0), P(l1, u1, f0)],
            "top": [P(l0, u1, f0), P(l0, u1, f1), P(l1, u1, f1), P(l1, u1, f0)],
            "bottom": [P(l0, u0, f1), P(l0, u0, f0), P(l1, u0, f0), P(l1, u0, f1)],
        }
        for side, pts in faces.items():
            x, y, w, h = uv[side]
            uvs = [(x / tw, y / th), (x / tw, (y + h) / th), ((x + w) / tw, (y + h) / th), ((x + w) / tw, y / th)]
            n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
            rs.quad(pts, uvs, tex, shade_of(n))


def draw_entity(rs, e, opts):
    t = e["type"]
    pos = np.array(e["pos"], dtype=float)
    base = tr(*pos) @ orientation(e, rs) @ transformation(e)
    if t == "ItemDisplay":
        it = e.get("item")
        if not it:
            return
        key = it.get("model") or it.get("material")
        mid = item_model(key)
        quads = model_quads(mid)
        draw_quads(rs, base @ ry(math.pi) @ tr(-0.5, -0.5, -0.5), quads)
    elif t == "BlockDisplay":
        b = e.get("block")
        if not b:
            return
        mid, bx, by = block_model(b["material"], b.get("state"))
        if mid is None:
            mid = "minecraft:block/" + b["material"].split(":")[1]
        quads = model_quads(mid)
        if not quads:
            name = b["material"].split(":")[1]
            quads = [(pts, [(0, 0), (0, 1), (1, 1), (1, 0)], "minecraft:block/" + name)
                     for pts in ([tuple(16 * c for c in cc) for cc in CORNERS[d]] for d in CORNERS)]
        rot = tr(0.5, 0.5, 0.5) @ ry(math.radians(-by)) @ rx(math.radians(-bx)) @ tr(-0.5, -0.5, -0.5)
        draw_quads(rs, base @ rot, quads)
    elif t == "TextDisplay":
        draw_text(rs, e, base)
    elif t == "Mannequin":
        prof = str(e.get("profile", ""))
        mm = re.search(r"kuby:entity/casino/(\w+)", prof)
        name = mm.group(1) if mm else "croupier"
        skin = bs.SKINS[name]() if name in bs.SKINS else bs.SKINS["croupier"]()
        draw_player(rs, e, skin)
    elif t == "Interaction" and opts.get("hit"):
        w, h = e.get("interactionWidth", 1), e.get("interactionHeight", 1)
        p = pos
        red = np.zeros((1, 1, 4))
        red[..., 0] = 1
        red[..., 3] = 0.25
        hw = w / 2
        c = [p + (-hw, 0, -hw), p + (hw, 0, -hw), p + (hw, 0, hw), p + (-hw, 0, hw)]
        top = [x + (0, h, 0) for x in c]
        for i in range(4):
            j = (i + 1) % 4
            for quad in ([top[i], c[i], c[j], top[j]], [top[j], c[j], c[i], top[i]]):
                rs.quad(quad, [(0, 0), (0, 1), (1, 1), (1, 0)], red, 1.0, translucent=True)


# ---------------------------------------------------------------------------------------------------------------------
# Umgebung (Boden, gebaute Blöcke)
# ---------------------------------------------------------------------------------------------------------------------

def cube(rs, x, y, z, name):
    m = tr(x, y, z)
    quads = []
    for d in CORNERS:
        side = name
        if isinstance(name, dict):
            side = name.get(d, name.get("side"))
        quads.append(([tuple(16 * c for c in cc) for cc in CORNERS[d]], [(0, 0), (0, 1), (1, 1), (1, 0)],
                      "minecraft:block/" + side))
    draw_quads(rs, m, quads)


def context(rs, name):
    for x in range(-6, 7):
        for z in range(-6, 7):
            cube(rs, x, -1, z, "polished_andesite" if (x + z) % 2 else "andesite")
    if name == "slots":
        # Pult (1 hoch, 3 breit, 1 tief), Wand dahinter, Seiten glasierte Terrakotta, Dach mit Leuchtkasten
        for x in (-1, 0, 1):
            cube(rs, x, 0, 0, "cyan_terracotta")
            cube(rs, x, 0, -1, "cyan_terracotta")
            cube(rs, x, 1, -1, "white_concrete")
            cube(rs, x, 2, -1, "redstone_lamp" if x == 0 else "prismarine_bricks")
            cube(rs, x, 2, 0, "dark_prismarine")
        for x in (-2, 2):
            for y in range(0, 3):
                cube(rs, x, y, -1, "pink_glazed_terracotta")
                cube(rs, x, y, 0, "pink_glazed_terracotta")
    if name == "roulette":
        for x in range(-4, 6):
            for z in range(-3, 5):
                cube(rs, x, 0, z, "black_wool")


def main():
    src, out = sys.argv[1], sys.argv[2]
    opts = {}
    for a in sys.argv[3:]:
        k, v = a.split("=", 1)
        opts[k] = v
    cam = [float(x) for x in opts.get("cam", "0.5,1.6,3").split(",")]
    look = [float(x) for x in opts.get("look", "0.5,1.5,0").split(",")]
    ss = int(opts.get("ss", 2))
    W, H = int(opts.get("w", 960)), int(opts.get("h", 540))
    rs = Raster(W * ss, H * ss, cam, look, float(opts.get("fov", 70)))
    if opts.get("ctx"):
        context(rs, opts["ctx"])
    ents = json.load(open(src, encoding="utf-8"))
    for e in ents:
        draw_entity(rs, e, opts)
    img = rs.finish()
    if ss > 1:
        img = img.resize((W, H), Image.LANCZOS)
    img.save(out)
    print(out, len(ents), "Entities")


if __name__ == "__main__":
    main()
