#!/usr/bin/env python3
"""Pixel-Icons im Stil der Referenzen zeichnen (Regeln: tools/icon_db/STYLE.md, Farben: materials.py).

Ein Icon besteht aus Teilen (Formen) mit Material und Form-Art; schattiert wird über Stufen der Material-Rampe:
  flat   Fläche: oben 1 bis 2 Stufen heller (Lichtkante), links heller, unten und rechts dunkler, leicht von oben nach unten
  round  Kugel/Scheibe: Licht von oben links nach der Form, dazu helle Kante oben links, dunkle unten rechts, Glanzpunkt
  cyl    stehender Zylinder: links hell, rechts dunkel, Lichtkante oben
Teile weiter vorn werfen auf Teile dahinter eine dunkle Trennlinie (wie Band und Geschenk). Um alles kommt 1 px Kontur
im warmen Fast-Schwarz (4er-Nachbarschaft, Ecken bleiben offen wie in den Referenzen). Details (Nieten, Risse, Glanz)
werden danach mit festen Stufen gesetzt. Keine Kantenglättung, kein Dithering.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import OUTLINE, RAMPS, rgb  # noqa: E402

LIGHT = (-0.55, -0.68, 0.48)
_ln = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / _ln for c in LIGHT)


class Part:
    def __init__(self, mask, mat, form, lo, hi, sep, rim, gloss, shade):
        self.mask, self.mat, self.form = mask, mat, form
        n = len(RAMPS[mat])
        self.lo = 1 if lo is None else lo
        self.hi = n - 2 if hi is None else (hi if hi >= 0 else n + hi)
        self.sep, self.rim, self.gloss, self.shade = sep, rim, gloss, shade


class Icon:
    def __init__(self, size=32):
        self.size = size
        self.parts = []
        self.details = []
        self.holes = set()
        self.outline = OUTLINE

    # ---------- Formen ----------
    def _mask(self, fn):
        m = Image.new("L", (self.size, self.size), 0)
        fn(ImageDraw.Draw(m))
        px = m.load()
        return {(x, y) for y in range(self.size) for x in range(self.size) if px[x, y] > 127}

    def add(self, mask, mat, form="flat", lo=None, hi=None, sep=True, rim=True, gloss=None, shade=0.0):
        self.parts.append(Part(set(mask), mat, form, lo, hi, sep, rim, gloss, shade))
        return self.parts[-1]

    def poly(self, pts, mat, form="flat", **kw):
        return self.add(self._mask(lambda d: d.polygon(pts, fill=255)), mat, form, **kw)

    def rect(self, x0, y0, x1, y1, mat, form="flat", **kw):
        return self.add({(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}, mat, form, **kw)

    def ellipse(self, x0, y0, x1, y1, mat, form="round", **kw):
        return self.add(self._mask(lambda d: d.ellipse((x0, y0, x1, y1), fill=255)), mat, form, **kw)

    def line(self, pts, width, mat, form="flat", **kw):
        return self.add(self._mask(lambda d: d.line(pts, fill=255, width=width)), mat, form, **kw)

    def cut(self, mask):
        """Löcher (durchsichtig, bekommen innen Kontur)."""
        self.holes |= set(mask)

    def cut_ellipse(self, x0, y0, x1, y1):
        self.cut(self._mask(lambda d: d.ellipse((x0, y0, x1, y1), fill=255)))

    def cut_rect(self, x0, y0, x1, y1):
        self.cut({(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)})

    def px(self, pts, mat, idx):
        """Einzelne Pixel mit fester Stufe (idx < 0: von hell gezählt, -1 = hellste). mat=None: Konturfarbe."""
        for p in pts:
            self.details.append((p[0], p[1], mat, idx))

    # ---------- Zeichnen ----------
    @staticmethod
    def _depth(m, x, y, dx, dy, limit=3):
        """Wie viele Pixel bis zum Rand der Form in Richtung (dx, dy) (1 = direkt am Rand)."""
        for k in range(1, limit + 1):
            if (x + dx * k, y + dy * k) not in m:
                return k
        return limit + 1

    def _tone(self, part, x, y, bbox):
        m = part.mask
        x0, y0, x1, y1 = bbox
        w, h = max(1, x1 - x0 + 1), max(1, y1 - y0 + 1)
        big = min(w, h) >= 10
        if part.form == "round":
            cx, cy = (x0 + x1 + 1) / 2, (y0 + y1 + 1) / 2
            nx, ny = (x + 0.5 - cx) / (w / 2), (y + 0.5 - cy) / (h / 2)
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            d = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
            t = 0.5 + 0.72 * d
            rim_hi, rim_lo = 0.2, 0.32
        elif part.form == "cyl":
            nx = (x + 0.5 - (x0 + x1 + 1) / 2) / (w / 2)
            t = 0.64 - 0.42 * nx - 0.14 * (y - y0) / h
            rim_hi, rim_lo = 0.26, 0.3
        else:
            t = 0.64 - 0.3 * (y - y0) / h
            rim_hi, rim_lo = 0.32, 0.34
        if part.rim:
            up, down = self._depth(m, x, y, 0, -1), self._depth(m, x, y, 0, 1)
            left, right = self._depth(m, x, y, -1, 0), self._depth(m, x, y, 1, 0)
            if up == 1:
                t += rim_hi
            elif up == 2 and big:
                t += rim_hi * 0.4
            if left == 1:
                t += rim_hi * 0.5
            if down == 1:
                t -= rim_lo
            elif down == 2 and big:
                t -= rim_lo * 0.45
            if right == 1:
                t -= rim_lo * 0.7
            elif right == 2 and big:
                t -= rim_lo * 0.3
        t += part.shade
        t = max(0.0, min(1.0, t))
        return part.lo + int(round(t * (part.hi - part.lo)))

    def render(self):
        S = self.size
        owner = {}
        for i, p in enumerate(self.parts):
            for q in p.mask:
                if 0 <= q[0] < S and 0 <= q[1] < S and q not in self.holes:
                    owner[q] = i
        idx = {}
        for i, p in enumerate(self.parts):
            vis = [q for q, o in owner.items() if o == i]
            if not vis:
                continue
            xs = [q[0] for q in p.mask]
            ys = [q[1] for q in p.mask]
            bbox = (min(xs), min(ys), max(xs), max(ys))
            for q in vis:
                idx[q] = self._tone(p, q[0], q[1], bbox)
        # Trennlinien: Pixel hinter einem vorderen Teil (mit sep) werden dunkel
        for q, o in owner.items():
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                r = (q[0] + dx, q[1] + dy)
                if r in owner and owner[r] > o and self.parts[owner[r]].sep and self.parts[owner[r]].mat != self.parts[o].mat:
                    idx[q] = max(0, min(idx[q], self.parts[o].lo) - 1)
                    break
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        pix = img.load()
        for q, o in owner.items():
            ramp = RAMPS[self.parts[o].mat]
            pix[q[0], q[1]] = rgb(ramp[max(0, min(len(ramp) - 1, idx[q]))]) + (255,)
        # Glanzpunkte
        for i, p in enumerate(self.parts):
            if not p.gloss:
                continue
            ramp = RAMPS[p.mat]
            pts = p.gloss
            if pts == "auto":
                vis = [q for q, o in owner.items() if o == i]
                if not vis:
                    continue
                xs = [q[0] for q in vis]
                ys = [q[1] for q in vis]
                gx = round(min(xs) + (max(xs) - min(xs)) * 0.28)
                gy = round(min(ys) + (max(ys) - min(ys)) * 0.24)
                pts = [(gx, gy), (gx + 1, gy), (gx, gy + 1)]
            for k, (x, y) in enumerate(pts):
                if (x, y) in owner:
                    pix[x, y] = rgb(ramp[-1 if k == 0 else -2]) + (255,)
        # Details
        for x, y, mat, k in self.details:
            if not (0 <= x < S and 0 <= y < S):
                continue
            if mat is None:
                pix[x, y] = rgb(self.outline) + (255,)
                owner.setdefault((x, y), -1)
                continue
            ramp = RAMPS[mat]
            pix[x, y] = rgb(ramp[k if k >= 0 else len(ramp) + k]) + (255,)
            owner.setdefault((x, y), -1)
        # Kontur außen (4er-Nachbarschaft)
        oc = rgb(self.outline) + (255,)
        filled = {q for q in owner}
        for y in range(S):
            for x in range(S):
                if (x, y) in filled:
                    continue
                if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    pix[x, y] = oc
        return img


def sheet(items, out, scale=4, cols=8, label=True):
    """Kontaktbogen: [(name, Bild)]."""
    from PIL import ImageFont
    font = ImageFont.load_default()
    cw = max(i.width for _, i in items) * scale + 16
    ch = max(i.height for _, i in items) * scale + (14 if label else 0) + 12
    rows = (len(items) + cols - 1) // cols
    o = Image.new("RGBA", (cols * cw + 8, rows * ch + 8), (40, 46, 58, 255))
    d = ImageDraw.Draw(o)
    for k, (n, im) in enumerate(items):
        x, y = 8 + (k % cols) * cw, 8 + (k // cols) * ch
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        o.alpha_composite(big, (x, y))
        if label:
            d.text((x, y + big.height + 2), n, fill=(215, 218, 226), font=font)
    o.save(out)
    return o
