"""Vorschau für Dialog-Text mit eigenen Schriften: zeichnet eine Text-Komponente (JSON) wie Minecraft.

Aufruf: python3 tools/preview_text.py <komponente.json> <pack-ordner> <vanilla-ordner> <bild.png> [skala]
Nur zum Entwickeln. Meldet außerdem Zeichen einer Zeile, die sich mit verschiedenen Texturen überdecken
(Minecraft zeichnet die in zufälliger Reihenfolge).
"""
import json
import os
import sys

from PIL import Image

NAMED = {"black": 0x000000, "dark_blue": 0x0000AA, "dark_green": 0x00AA00, "dark_aqua": 0x00AAAA,
         "dark_red": 0xAA0000, "dark_purple": 0xAA00AA, "gold": 0xFFAA00, "gray": 0xAAAAAA, "dark_gray": 0x555555,
         "blue": 0x5555FF, "green": 0x55FF55, "aqua": 0x55FFFF, "red": 0xFF5555, "light_purple": 0xFF55FF,
         "yellow": 0xFFFF55, "white": 0xFFFFFF}


class Fonts:
    def __init__(self, roots):
        self.roots = roots
        self.cache = {}
        self.images = {}

    def path(self, kind, ident, ext):
        ns, p = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
        for r in self.roots:
            f = os.path.join(r, "assets", ns, kind, p + ext)
            if os.path.exists(f):
                return f
        return None

    def image(self, ident):
        if ident not in self.images:
            ns, p = ident.split(":", 1)
            f = None
            for r in self.roots:
                c = os.path.join(r, "assets", ns, "textures", p)
                if os.path.exists(c):
                    f = c
                    break
            self.images[ident] = Image.open(f).convert("RGBA") if f else None
        return self.images[ident]

    def providers(self, font):
        if font in self.cache:
            return self.cache[font]
        f = self.path("font", font, ".json")
        out = []
        if f:
            for p in json.load(open(f, encoding="utf-8"))["providers"]:
                if p["type"] == "reference":
                    out.extend(self.providers(p["id"]))
                elif p["type"] in ("space", "bitmap"):
                    out.append(p)
        self.cache[font] = out
        return out

    def glyph(self, font, ch):
        """(advance, image oder None, ascent, scale, texture-id)"""
        for p in self.providers(font):
            if p["type"] == "space":
                if ch in p["advances"]:
                    return p["advances"][ch], None, 0, 1, None
                continue
            for r, row in enumerate(p["chars"]):
                c = row.find(ch)
                if c >= 0:
                    img = self.image(p["file"])
                    if img is None:
                        return 6, None, 0, 1, None
                    cw = img.width // len(row)
                    chh = img.height // len(p["chars"])
                    cell = img.crop((c * cw, r * chh, (c + 1) * cw, (r + 1) * chh))
                    h = p.get("height", 8)
                    scale = h / chh
                    bbox = cell.getchannel("A").getbbox()
                    w = bbox[2] if bbox else 0
                    adv = int(0.5 + w * scale) + 1
                    if scale != 1:
                        cell = cell.resize((max(1, round(cw * scale)), max(1, round(chh * scale))), Image.NEAREST)
                    return adv, cell, p["ascent"], scale, (p["file"], font)
        return 6, None, 0, 1, None


def color_of(v):
    if v is None:
        return None
    if isinstance(v, str):
        if v.startswith("#"):
            return int(v[1:], 16)
        return NAMED.get(v, 0xFFFFFF)
    return v


def flatten(comp, style, out):
    if isinstance(comp, str):
        out.extend((ch, style) for ch in comp)
        return
    if isinstance(comp, list):
        for c in comp:
            flatten(c, style, out)
        return
    st = dict(style)
    for k in ("color", "font", "bold", "shadow_color"):
        if k in comp:
            st[k] = comp[k]
    if "click_event" in comp or "clickEvent" in comp:
        st["click"] = True
    text = comp.get("text", "")
    out.extend((ch, st) for ch in text)
    for c in comp.get("extra", []):
        flatten(c, st, out)


def tint(cell, rgb, alpha_scale=1.0):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    px = cell.load()
    out = Image.new("RGBA", cell.size, (0, 0, 0, 0))
    po = out.load()
    for y in range(cell.height):
        for x in range(cell.width):
            cr, cg, cb, ca = px[x, y]
            if ca:
                po[x, y] = (cr * r // 255, cg * g // 255, cb * b // 255, int(ca * alpha_scale))
    return out


def render(comp, fonts, bg=(42, 60, 100, 255), margin=8):
    chars = []
    flatten(comp, {"font": "minecraft:default", "color": "white"}, chars)
    lines = [[]]
    for ch, st in chars:
        if ch == "\n":
            lines.append([])
        else:
            lines[-1].append((ch, st))
    width = 0
    for line in lines:
        x = 0
        mx = 0
        for ch, st in line:
            adv = fonts.glyph(st.get("font", "minecraft:default"), ch)[0]
            x += adv + (1 if st.get("bold") else 0)
            mx = max(mx, x)
        width = max(width, x)
    H = len(lines) * 9 + 2 * margin + 40
    img = Image.new("RGBA", (width + 2 * margin, H), bg)
    conflicts = []
    for li, line in enumerate(lines):
        x = 0
        y0 = margin + li * 9
        drawn = []
        for ch, st in line:
            font = st.get("font", "minecraft:default")
            adv, cell, ascent, scale, tex = fonts.glyph(font, ch)
            bold = bool(st.get("bold"))
            if cell is not None:
                col = color_of(st.get("color")) or 0xFFFFFF
                top = y0 + 7 - ascent
                left = margin + x
                sh = st.get("shadow_color")
                if sh is None or (isinstance(sh, int) and (sh >> 24) & 255):
                    shc = ((col >> 16 & 255) // 4 << 16) | ((col >> 8 & 255) // 4 << 8) | ((col & 255) // 4)
                    s = tint(cell, shc)
                    img.alpha_composite(s, (left + 1, top + 1))
                    if bold:
                        img.alpha_composite(s, (left + 2, top + 1))
                g = tint(cell, col)
                img.alpha_composite(g, (left, top))
                if bold:
                    img.alpha_composite(g, (left + 1, top))
                drawn.append((left, top, cell, tex))
            x += adv + (1 if bold else 0)
        # gleiche Zeile, verschiedene Texturen, deckende Pixel übereinander
        for i in range(len(drawn)):
            for j in range(i + 1, len(drawn)):
                a, b = drawn[i], drawn[j]
                if a[3] == b[3]:
                    continue
                ax, ay, ac, _ = a
                bx, by, bc, _ = b
                x0, y0b = max(ax, bx), max(ay, by)
                x1, y1 = min(ax + ac.width, bx + bc.width), min(ay + ac.height, by + bc.height)
                if x0 >= x1 or y0b >= y1:
                    continue
                pa, pb = ac.load(), bc.load()
                bad = False
                for yy in range(y0b, y1):
                    for xx in range(x0, x1):
                        qa = pa[xx - ax, yy - ay]
                        qb = pb[xx - bx, yy - by]
                        if qa[3] and qb[3] and qa != qb:
                            bad = True
                            break
                    if bad:
                        break
                if bad:
                    conflicts.append((li, ax - margin, ay - margin, bx - margin, by - margin))
    return img, conflicts


if __name__ == "__main__":
    comp = json.load(open(sys.argv[1], encoding="utf-8"))
    fonts = Fonts([sys.argv[2], sys.argv[3]])
    img, conflicts = render(comp, fonts)
    scale = int(sys.argv[5]) if len(sys.argv) > 5 else 3
    img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    img.save(sys.argv[4])
    print("Bild", img.size, "Konflikte", len(conflicts))
    for c in conflicts[:20]:
        print("  Zeile %d: (%d,%d) / (%d,%d)" % c)
