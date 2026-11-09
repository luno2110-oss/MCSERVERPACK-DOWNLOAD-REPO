#!/usr/bin/env python3
"""Vorschau einer Cutscene der Kino-Engine (nur zum Entwickeln).

Liest frames.json (aus dem Java-Harness CinePreview: je Tick Kamera, Figur, Teile, Himmel, Risse, Blitz, Balken) und
rendert ausgewählte Bilder mit r3d (wie der 1.21.11-Client: Itemmodelle, Färbung, Durchsicht, Billboards) plus Himmel,
Boden mit Rissen, Farbstimmung, Kino-Balken und Blitzbild.

Aufruf: KUBY_VANILLA=<vanilla-assets> cine_preview.py frames.json out_dir [ticks=0,40,80|step=10] [w=480] [h=270]
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r3d  # noqa: E402

GRADES = {}


def grade(name):
    if name not in GRADES:
        f = os.path.join(r3d.PACK, "assets/kuby/textures/cine/grade_%s.png" % name)
        GRADES[name] = Image.open(f).convert("RGBA") if os.path.exists(f) else None
    return GRADES[name]


def sky(rs, time, weather):
    """Himmel als Verlauf nach Blickhöhe (Zenit/Horizont je Tageszeit und Wetter), nachts Sterne."""
    h, w = rs.h, rs.w
    ys, xs = np.mgrid[0:h, 0:w]
    k = rs.f * h / 2
    dx = (xs + 0.5 - w / 2) / k
    dy = -(ys + 0.5 - h / 2) / k
    d = rs.fwd[None, None, :] + dx[..., None] * rs.right[None, None, :] + dy[..., None] * rs.up[None, None, :]
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    el = np.clip(d[..., 1], -1, 1)
    t = np.clip(el / 0.6, 0, 1)[..., None]
    if 12000 <= time <= 13500:
        zen, hor = np.array([0.22, 0.28, 0.55]), np.array([1.0, 0.55, 0.25])
    elif 13500 < time < 23000:
        zen, hor = np.array([0.0, 0.0, 0.02]), np.array([0.03, 0.04, 0.09])
    else:
        zen, hor = np.array([0.47, 0.65, 1.0]), np.array([0.78, 0.86, 1.0])
    if weather == 1:
        zen, hor = zen * 0.45, hor * 0.5
    img = hor * (1 - t) + zen * t
    if 13500 < time < 23000:
        rnd = np.random.default_rng(3)
        star = rnd.random((h, w)) > 0.9965
        img[star & (el > 0.05)] = 0.85
    if 12000 <= time <= 13500:
        # Sonne tief im Westen (-x)
        sun = np.array([-0.97, 0.12, 0.2])
        sun /= np.linalg.norm(sun)
        c = d @ sun
        img += (np.clip((c - 0.985) / 0.015, 0, 1) ** 2)[..., None] * np.array([1.0, 0.75, 0.4])
        img += (np.clip((c - 0.8) / 0.2, 0, 1) ** 3)[..., None] * np.array([0.35, 0.15, 0.0])
    rs.img[:] = np.clip(img, 0, 1)


def hash01(x, z, seed):
    h = (x * 73856093) ^ (z * 19349663) ^ (seed * 83492791)
    h &= 0xFFFFFFFFFFFFFFFF
    h ^= h >> 13
    h = (h * 0x5bd1e995) & 0xFFFFFFFFFFFFFFFF
    h ^= h >> 15
    return (h & 0xffffff) / 0xffffff


def crack_material(bx, bz, cracks):
    """Wie Cinema.crack (vereinfacht): Material der Oberseite oder None."""
    for cx, cy, cz, rad, seed in cracks:
        px, pz = bx + 0.5 - cx, bz + 0.5 - cz
        d = math.hypot(px, pz)
        if d > rad or d < 1.2:
            continue
        rays = [(k + hash01(k, 7, seed) * 0.7) * math.pi * 2 / 5 for k in range(5)]
        a = math.atan2(pz, px)
        best = 99
        for k, ak in enumerate(rays):
            wob = 0.22 * math.sin(d * 0.9 + k * 1.7)
            da = abs(((a - ak - wob) % (math.pi * 2) + math.pi * 3) % (math.pi * 2) - math.pi)
            best = min(best, da * d)
        h = hash01(bx, bz, seed)
        if best < 0.5:
            return "magma"
        if best < 1.0:
            return "blackstone" if h < 0.45 else "cobbled_deepslate" if h < 0.8 else "basalt_top"
        if h < 0.12 * (1 - d / (rad + 1)) + 0.05:
            return "tuff" if h < 0.1 else "coarse_dirt"
    return None


GRASS_TINT = (0.49, 0.74, 0.32)


def ground(rs, sx, sy, sz, cracks, radius=16):
    for x in range(int(sx) - radius, int(sx) + radius):
        for z in range(int(sz) - radius, int(sz) + radius):
            if (x + 0.5 - sx) ** 2 + (z + 0.5 - sz) ** 2 > radius * radius:
                continue
            mat = crack_material(x, z, cracks)
            y = sy
            tex = "minecraft:block/" + (mat if mat else "grass_block_top")
            if mat == "magma":
                tex = "minecraft:block/magma"
            pts = np.array([(x, y, z), (x, y, z + 1), (x + 1, y, z + 1), (x + 1, y, z)], dtype=float)
            tint = None if mat else GRASS_TINT
            rs.quad(pts, [(0, 0), (0, 1), (1, 1), (1, 0)], r3d.texture(tex), 1.0 if mat == "magma" else 0.95, False, tint)


def translucent(tex):
    a = tex[..., 3]
    return bool(np.any((a > 0.1) & (a < 0.95)))


def draw_item(rs, e):
    it = e.get("item") or {}
    key = it.get("model")
    if not key:
        return
    mid = r3d.item_model(key)
    quads = r3d.model_quads(mid)
    if not quads:
        return
    base = r3d.tr(*e["pos"]) @ r3d.orientation(e, rs) @ r3d.transformation(e)
    m = base @ r3d.ry(math.pi) @ r3d.tr(-0.5, -0.5, -0.5)
    tint = e.get("tint", 0)
    tc = None
    if tint:
        tc = ((tint >> 16 & 255) / 255, (tint >> 8 & 255) / 255, (tint & 255) / 255)
    lit = e.get("light", -1) >= 0
    for pts, uvs, tid in quads:
        p = r3d.apply(m, np.array(pts) / 16.0)
        tex = r3d.texture(tid)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        shade = 1.0 if lit else r3d.shade_of(n)
        rs.quad(p, uvs, tex, shade, translucent(tex), tc)


def render(fr, out, W, H, skin):
    rs = r3d.Raster(W, H, fr["cam"], fr["look"], 70)
    sky(rs, fr["time"], fr["weather"])
    ground(rs, 0.5, 64.0, 0.5, fr["cracks"])
    dome = None
    for e in fr["ents"]:
        if e["type"] == "ItemDisplay" and str((e.get("item") or {}).get("model", "")).startswith("kuby:fx/dome"):
            dome = e
            continue
        if e["type"] == "ItemDisplay":
            draw_item(rs, e)
        elif e["type"] == "BlockDisplay":
            r3d.draw_entity(rs, e, {})
        elif e["type"] == "Mannequin":
            q = dict(e)
            q["yaw"] = e.get("headYaw", e["yaw"])
            r3d.draw_player(rs, q, skin, False)
    if dome is not None:
        # Kuppel (halbe Kante 23 Blöcke): tönt alles dahinter und den Himmel
        lv = int(str(dome["item"]["model"]).rsplit("_", 1)[1])
        a = {1: 0.16, 2: 0.28, 3: 0.42, 4: 0.56}[lv]
        c = dome.get("tint", 0xFFFFFF)
        col = np.array([(c >> 16 & 255) / 255, (c >> 8 & 255) / 255, (c & 255) / 255])
        mask = rs.z > 23
        rs.img[mask] = rs.img[mask] * (1 - a) + col * a
    img = rs.finish().convert("RGBA")
    # Farbstimmung (Mitte des Glyphen-Bilds wie bei GUI-Maßstab 2)
    g = fr.get("grade")
    if g and grade(g) is not None:
        gi = grade(g)
        cw, ch = int(gi.width * 0.34), int(gi.height * 0.78)
        crop = gi.crop(((gi.width - cw) // 2, (gi.height - ch) // 2, (gi.width + cw) // 2, (gi.height + ch) // 2))
        img.alpha_composite(crop.resize((W, H), Image.BILINEAR))
    # Kino-Balken (44 von 540 GUI-Pixeln je Seite)
    bars = fr.get("bars", 0)
    if bars > 0:
        bh = int(round(H * 44 / 540 * round(bars * 4) / 4))
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, W, bh), fill=(0, 0, 0, 255))
        d.rectangle((0, H - bh, W, H), fill=(0, 0, 0, 255))
    rgb, a = fr["flash"]
    if a > 0:
        col = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, int(255 * a))
        img.alpha_composite(Image.new("RGBA", (W, H), col))
    if fr.get("card"):
        d = ImageDraw.Draw(img)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", max(12, W // 22))
        except OSError:
            font = ImageFont.load_default()
        tw = d.textlength(fr["card"], font=font)
        d.text(((W - tw) / 2, H * 0.42), fr["card"], font=font, fill=(242, 200, 80, 255))
    img.convert("RGB").save(out)


def main():
    src, outdir = sys.argv[1], sys.argv[2]
    opts = dict(a.split("=", 1) for a in sys.argv[3:])
    W, H = int(opts.get("w", 480)), int(opts.get("h", 270))
    frames = json.load(open(src, encoding="utf-8"))
    by_t = {f["t"]: f for f in frames}
    if "ticks" in opts:
        ticks = [int(x) for x in opts["ticks"].split(",")]
    else:
        st = int(opts.get("step", 10))
        ticks = [f["t"] for f in frames if f["t"] % st == 0]
    sys.path.insert(0, os.path.join(r3d.REPO, "tools"))
    skin = r3d.bs.SKINS.get("mia", next(iter(r3d.bs.SKINS.values())))
    skin = (skin[0] if isinstance(skin, tuple) else skin)()
    os.makedirs(outdir, exist_ok=True)
    for t in ticks:
        fr = by_t.get(t) or by_t[min(by_t, key=lambda k: abs(k - t))]
        render(fr, os.path.join(outdir, "f%04d.png" % fr["t"]), W, H, skin)
        print("f%04d" % fr["t"], flush=True)


if __name__ == "__main__":
    main()
