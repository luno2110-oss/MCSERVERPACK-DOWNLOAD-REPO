#!/usr/bin/env python3
"""Bausteine des Einstellungen-Menüs (Referenz „Referenzen2“): aus tools/assets/settings (pixelgenau aus den
Referenzbildern geschnitten, GUI-Pixel).

- Kästen als 9-Slice (15 x 15: Ecken 7 x 7, 1 px Mitte): Umriss-Stile set_<farbe> und gefüllte setf_<farbe>
  (aktiver Reiter), dazu set_head (Kopf), set_gray (inaktiv) und set_button (untere Knöpfe).
- Mehrfach-Schalter 16 hoch (kleine Knöpfe): set_<farbe> und set_gray; Blau, Orange, Gold und Violett werden aus
  der türkisen Vorlage über die Farben der Reiter umgefärbt.
- AN/AUS-Schalter 40 x 14 (als zwei Hälften zu 20 px, kleine Icons), dazu ON/OFF für Englisch.
- Icons (Kopf-Zahnrad 31 x 31 groß, sonst klein).
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets", "settings")
COLORS = ("blue", "orange", "gold", "teal", "violet")


def _img(name):
    return Image.open(os.path.join(ASSETS, name + ".png")).convert("RGBA")


def available():
    return os.path.isdir(ASSETS)


# =====================================================================================================================
# Kästen
# =====================================================================================================================

def boxes():
    """Stil -> 15 x 15-Vorlage."""
    if not available():
        return {}
    out = {"set_head": _img("box_head"), "set_gray": _img("box_gray"), "set_button": _img("box_button")}
    for c in COLORS:
        out["set_" + c] = _img("box_" + c)
        out["setf_" + c] = _img("boxf_" + c)
    return out


def draw_sliced(w, h, sample, n=7):
    """Kasten w x h aus einer (2n+1)-Vorlage: Ecken bleiben, die Mitte wird gestreckt."""
    sw, sh = sample.size
    src = np.array(sample)
    xs = [x if x < n else (sw - (w - x) if x >= w - n else n) for x in range(w)]
    ys = [y if y < n else (sh - (h - y) if y >= h - n else n) for y in range(h)]
    arr = src[np.ix_(ys, xs)]
    return Image.fromarray(arr, "RGBA")


def straight_row(sample, n=7):
    """Erste Zeile, ab der die Ränder links und rechts der Mittelzeile gleichen (so weit dürfen Zeilen überlappen)."""
    a = np.array(sample)
    mid = a[n]
    r = n
    for y in range(n - 1, -1, -1):
        if not np.array_equal(a[y, :n], mid[:n]) or not np.array_equal(a[y, -n:], mid[-n:]):
            break
        r = y
    return r


# =====================================================================================================================
# Mehrfach-Schalter (16 hoch)
# =====================================================================================================================

def _palette_map(src, dst):
    """Farbzuordnung Pixel für Pixel zwischen zwei gleich gebauten Vorlagen (Reiter türkis -> Reiter Farbe)."""
    a, b = np.array(src), np.array(dst)
    m = {}
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            if a[y, x, 3] and b[y, x, 3]:
                m.setdefault(tuple(int(v) for v in a[y, x, :3]), tuple(int(v) for v in b[y, x, :3]))
    return m


def _recolor(img, m):
    arr = np.array(img).astype(int)
    keys = np.array(list(m.keys()))
    vals = np.array(list(m.values()))
    for y in range(arr.shape[0]):
        for x in range(arr.shape[1]):
            if not arr[y, x, 3]:
                continue
            c = arr[y, x, :3]
            if c.sum() < 30:
                continue
            d = np.abs(keys - c).sum(axis=1)
            k = int(d.argmin())
            # kleine Abweichung (Schattierung) mitnehmen
            arr[y, x, :3] = np.clip(vals[k] + (c - keys[k]), 0, 255)
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def smalls():
    """Stil -> 15 x 16-Vorlage der Mehrfach-Schalter."""
    if not available():
        return {}
    teal = _img("small_teal")
    out = {"set_teal": teal, "set_gray": _img("small_gray")}
    tab_teal = _img("boxf_teal")
    for c in COLORS:
        if c == "teal":
            continue
        m = _palette_map(tab_teal, _img("boxf_" + c))
        # Füllung und Rand des Kastens (Umriss) ergänzen, falls die Reiter-Vorlage sie nicht enthält
        out["set_" + c] = _recolor(teal, m)
    return out


def draw_small_sliced(w, h, sample, n=7):
    sw = sample.size[0]
    src = np.array(sample)
    xs = [x if x < n else (sw - (w - x) if x >= w - n else n) for x in range(w)]
    return Image.fromarray(src[:, xs], "RGBA")


# =====================================================================================================================
# Schalter AN/AUS und Icons
# =====================================================================================================================

def _glyphs(vroot):
    """Vanilla-Glyphen (5 x 7) aus ascii.png für ON/OFF."""
    sheet = Image.open(os.path.join(vroot, "assets/minecraft/textures/font/ascii.png")).convert("RGBA")
    a = np.array(sheet)
    out = {}
    for ch in "ONF":
        code = ord(ch)
        cx, cy = (code % 16) * 8, (code // 16) * 8
        out[ch] = a[cy:cy + 7, cx:cx + 5, 3] > 0
    return out


def _stamp(img, text, x, y, color, bold, glyphs):
    arr = np.array(img)
    for ch in text:
        g = glyphs[ch]
        for dy in range(g.shape[0]):
            for dx in range(g.shape[1]):
                if g[dy, dx]:
                    for b in ((0, 1) if bold else (0,)):
                        arr[y + dy, x + dx + b, :3] = color
                        arr[y + dy, x + dx + b, 3] = 255
        x += 6 + (1 if bold else 0)
    return Image.fromarray(arr, "RGBA")


def _halves(prefix, img):
    return [(prefix + "_l", img.crop((0, 0, 20, 14))), (prefix + "_r", img.crop((20, 0, 40, 14)))]


def small_icons(vroot):
    """[(name, Bild <= 20 x 20)] für das Kit (Reihenfolge fest, neue Einträge hinten anhängen)."""
    if not available():
        return []
    out = []
    for n in ("check", "chat", "chat_section", "envelope", "bell", "eye", "gear", "gear_button"):
        out.append(("set_" + n, _img("icon_" + n)))
    glyphs = _glyphs(vroot)
    dark = (28, 18, 45)
    gray = (121, 125, 132)
    for c in COLORS:
        out += _halves("set_on_" + c, _img("pill_on_" + c))
        en = _stamp(_img("pill_on_%s_blank" % c), "ON", 8, 3, dark, True, glyphs)
        out += _halves("set_on_en_" + c, en)
    out += _halves("set_off", _img("pill_off"))
    off = np.array(_img("pill_off"))
    # "AUS" entfernen (graue Schrift) und "OFF" an dieselbe Stelle
    m = (np.abs(off[..., :3].astype(int) - np.array(gray)).sum(axis=2) < 6) & (off[..., 3] > 0)
    off[m, :3] = (21, 22, 24)
    out += _halves("set_off_en", _stamp(Image.fromarray(off, "RGBA"), "OFF", 16, 3, gray, False, glyphs))
    return out


def big_icons():
    """[(name, Bild 32 x 32)]: Kopf-Zahnrad (31 x 31) oben links in einer 32er-Zelle."""
    if not available():
        return []
    cell = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    cell.alpha_composite(_img("icon_gear_big"), (0, 0))
    return [("set_gear_big", cell)]
