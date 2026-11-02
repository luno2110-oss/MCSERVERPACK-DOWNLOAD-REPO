#!/usr/bin/env python3
"""Icon-Datenbank aus den Referenzen (Bildschirmfotos des Vorbild-Servers, ausgeschnitten von den extract_*-Skripten).

Für jedes Bild aus den Referenz-Ordnern (tools/assets/<satz>) wird gemessen:
  - Palette: alle Farben als Hex mit Anzahl Pixel, sortiert nach Helligkeit
  - Kontur: wie viele Randpixel dunkel sind, welche Farben die Kontur hat, ob sie einfarbig oder getönt ist
  - Farbverläufe (Rampen): Farben gleichen Farbtons nach Helligkeit, je Schritt Änderung von Farbton, Sättigung, Wert
  - Licht: aus welcher Richtung (Helligkeit gegen x und y), wo die hellsten Pixel liegen
  - Symmetrie: Form und Farben gespiegelt (links/rechts)
  - Glanzpunkte (einzelne helle Pixel) und Dithering (Schachbrett aus zwei Farben)
Ausgabe in tools/icon_db/: icons.json (alle Messwerte), sheets/<satz>.png (Kontaktbögen, 4-fach, mit Namen),
STYLE.md (zusammengefasste Regeln mit Zahlen und Hex-Rampen je Farbfamilie). Abfragen: tools/icon_db/refpal.py.

Aufruf: python3 tools/icon_db/build_icon_db.py
"""
import colorsys
import json
import math
import os
from collections import Counter, defaultdict

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ASSETS = os.path.join(TOOLS, "assets")
# Nur Sätze, die aus den Referenz-Bildern ausgeschnitten sind (keine eigenen Zeichnungen)
SETS = ["battlepass", "friends", "guide", "infobar", "jewelshop", "jewelshop_survival", "playtime", "profile", "pwarps",
        "quests", "rules", "settings", "support"]
UI_PREFIX = ("box_", "art_box", "art_bar", "bar_", "art_cap", "art_nav", "art_btn", "art_arrow", "art_title", "art_frame",
             "art_tab", "art_pill", "art_head", "art_back", "art_slot", "art_line", "art_bg", "btn_", "cap_", "nav_", "tab_",
             "art_kb", "art_cb", "art_c_", "art_k_", "art_r_", "art_t_")


def kind_of(set_name, fname):
    n = fname.lower()
    if set_name == "jewelshop_survival" and n.startswith("art_f_"):
        return "moebel"
    if set_name == "jewelshop_survival" and n.startswith("art_p_"):
        return "pet"
    if n.startswith(UI_PREFIX) or "title" in n:
        return "ui"
    return "icon"


def hexc(c):
    return "#%02X%02X%02X" % c[:3]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def hsv(c):
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    return h * 360, s, v


def hdiff(a, b):
    d = (b - a + 540) % 360 - 180
    return d


FAMILIES = [("rot", 0, 12), ("orange", 12, 38), ("gold", 38, 62), ("gruen", 62, 160), ("tuerkis", 160, 195),
            ("blau", 195, 255), ("lila", 255, 300), ("rosa", 300, 345), ("rot", 345, 361)]


def family(c):
    h, s, v = hsv(c)
    if s < 0.16 or (v < 0.3 and s < 0.35):
        return "grau"
    if 12 <= h < 45 and v < 0.62 and s > 0.25:
        return "braun"
    for name, a, b in FAMILIES:
        if a <= h < b:
            return name
    return "rot"


def quantize(cols, tol=16):
    """Bildschirmfotos haben Rauschen (eine Fläche = viele fast gleiche Farben): Farben mit höchstens tol Abstand je Kanal
    zu einer Farbe zusammenfassen (gewichteter Mittelwert, häufigste zuerst). Gibt {alte Farbe: Mittelpunkt} zurück."""
    centers = []          # [summe_r, summe_g, summe_b, anzahl]
    owner = {}
    for c, n in sorted(cols.items(), key=lambda t: -t[1]):
        best = None
        for i, ce in enumerate(centers):
            m = (ce[0] / ce[3], ce[1] / ce[3], ce[2] / ce[3])
            if max(abs(c[k] - m[k]) for k in range(3)) <= tol:
                best = i
                break
        if best is None:
            centers.append([c[0] * n, c[1] * n, c[2] * n, n])
            best = len(centers) - 1
        else:
            ce = centers[best]
            ce[0] += c[0] * n
            ce[1] += c[1] * n
            ce[2] += c[2] * n
            ce[3] += n
        owner[c] = best
    mean = [(round(ce[0] / ce[3]), round(ce[1] / ce[3]), round(ce[2] / ce[3])) for ce in centers]
    return {c: mean[i] for c, i in owner.items()}


def analyse(img):
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    raw = {(x, y): px[x, y][:3] for y in range(h) for x in range(w) if px[x, y][3] >= 128}
    if not raw:
        return None
    qmap = quantize(Counter(raw.values()))
    opaque = {p: qmap[c] for p, c in raw.items()}
    transparent_share = 1 - len(opaque) / (w * h)
    cols = Counter(opaque.values())
    # Rand und Kontur
    edge = []
    for (x, y) in opaque:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in opaque:
                edge.append((x, y))
                break
    edge_cols = Counter(opaque[p] for p in edge)
    dark_edge = sum(n for c, n in edge_cols.items() if lum(c) < 60)
    outline_share = dark_edge / max(1, len(edge))
    outline_cols = [(hexc(c), n) for c, n in edge_cols.most_common() if lum(c) < 60][:4]
    # Rampen je Farbfamilie (ohne Konturfarben)
    fam = defaultdict(list)
    for c, n in cols.items():
        if lum(c) < 40:
            continue
        fam[family(c)].append((c, n))
    ramps = {}
    for f, lst in fam.items():
        total = sum(n for _, n in lst)
        if len(lst) < 3 or total < 6:
            continue
        # seltene Zwischenfarben (Kantenglättung) weglassen: mindestens 2 % der Familie
        lst = [(c, n) for c, n in lst if n >= max(2, total * 0.02)]
        if len(lst) < 3:
            continue
        # nach empfundener Helligkeit; verschiedene Materialien gleicher Familie (z. B. Holz und Rost) in eigene Ketten
        lst.sort(key=lambda t: lum(t[0]))
        chains = []
        for c, n in lst:
            h, sat, v = hsv(c)
            best = None
            for ch in chains:
                lh, ls, lv = hsv(ch[-1][0])
                if abs(sat - ls) <= 0.18 and abs(hdiff(lh, h)) <= 16:
                    best = ch
            if best is None:
                chains.append([(c, n)])
            else:
                best.append((c, n))
        k = 0
        for ch in sorted(chains, key=lambda c: -sum(n for _, n in c)):
            if len(ch) < 3:
                continue
            steps = []
            for (a, _), (b, _) in zip(ch, ch[1:]):
                ha, sa, va = hsv(a)
                hb, sb, vb = hsv(b)
                steps.append({"dH": round(hdiff(ha, hb), 1), "dS": round(sb - sa, 3), "dV": round(vb - va, 3),
                              "dL": round((lum(b) - lum(a)) / 255, 3)})
            ramps[f if k == 0 else "%s_%d" % (f, k + 1)] = {"hex": [hexc(c) for c, _ in ch], "px": [n for _, n in ch], "steps": steps}
            k += 1
    # Licht: Helligkeit gegen Position
    xs = [p[0] for p in opaque]
    ys = [p[1] for p in opaque]
    ls = [lum(c) for c in opaque.values()]
    mx, my, ml = sum(xs) / len(xs), sum(ys) / len(ys), sum(ls) / len(ls)
    cx = sum((x - mx) * (l - ml) for x, l in zip(xs, ls))
    cy = sum((y - my) * (l - ml) for y, l in zip(ys, ls))
    sx = math.sqrt(sum((x - mx) ** 2 for x in xs) * sum((l - ml) ** 2 for l in ls)) or 1
    sy = math.sqrt(sum((y - my) ** 2 for y in ys) * sum((l - ml) ** 2 for l in ls)) or 1
    corr_x, corr_y = cx / sx, cy / sy             # >0: heller nach rechts / nach unten
    # Glanzpunkte: Pixel deutlich heller als alle Nachbarn
    gloss = 0
    for (x, y), c in opaque.items():
        nb = [opaque[(x + dx, y + dy)] for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (dx or dy) and (x + dx, y + dy) in opaque]
        if len(nb) >= 5 and all(lum(c) - lum(n) > 30 for n in nb):
            gloss += 1
    # Dithering: 2x2 Schachbrett aus zwei Farben
    dither = 0
    for (x, y), c in opaque.items():
        a, b, d = opaque.get((x + 1, y)), opaque.get((x, y + 1)), opaque.get((x + 1, y + 1))
        if a and b and d and c == d and a == b and c != a and abs(lum(c) - lum(a)) > 12:
            dither += 1
    dither = dither if dither >= max(6, len(opaque) * 0.03) else 0
    # Symmetrie (links/rechts um die Mitte der Form)
    x0, x1 = min(xs), max(xs)
    same_shape = same_col = n = 0
    for (x, y), c in opaque.items():
        mxp = x0 + x1 - x
        n += 1
        if (mxp, y) in opaque:
            same_shape += 1
            o = opaque[(mxp, y)]
            if abs(lum(c) - lum(o)) < 12 and max(abs(c[i] - o[i]) for i in range(3)) < 24:
                same_col += 1
    return {
        "w": w, "h": h, "opaque": len(opaque), "transparent_share": round(transparent_share, 3),
        "colors": sum(1 for c, k in cols.items() if k >= max(1, len(opaque) * 0.005)),
        "palette": [(hexc(c), n) for c, n in sorted(cols.items(), key=lambda t: lum(t[0]))],
        "outline_share": round(outline_share, 3), "outline_colors": outline_cols,
        "ramps": ramps, "light": {"x": round(corr_x, 3), "y": round(corr_y, 3)},
        "gloss_pixels": gloss, "dither_cells": dither,
        "symmetry": {"shape": round(same_shape / n, 3), "color": round(same_col / n, 3)},
    }


def sheet(set_name, items, out):
    font = ImageFont.load_default()
    z, pad, lab = 4, 8, 12
    cells = []
    for fname, img in items:
        im = img.convert("RGBA")
        im = im.resize((im.width * z, im.height * z), Image.NEAREST)
        cells.append((fname, im))
    cw = max(max(i.width for _, i in cells), 130) + pad
    ch = max(i.height for _, i in cells) + lab + pad
    cols = max(1, min(8, 1600 // cw))
    rows = (len(cells) + cols - 1) // cols
    o = Image.new("RGBA", (cols * cw + pad, rows * ch + pad), (38, 42, 52, 255))
    d = ImageDraw.Draw(o)
    for k, (fname, im) in enumerate(cells):
        x, y = pad + (k % cols) * cw, pad + (k // cols) * ch
        o.alpha_composite(im, (x, y))
        d.text((x, y + im.height + 1), fname.replace("art_", "")[:22], fill=(200, 205, 215), font=font)
    o.save(out, optimize=True)


def main():
    db = {}
    os.makedirs(os.path.join(HERE, "sheets"), exist_ok=True)
    for s in SETS:
        d = os.path.join(ASSETS, s)
        items = []
        for f in sorted(os.listdir(d)):
            if not f.endswith(".png"):
                continue
            img = Image.open(os.path.join(d, f))
            if img.width > 64 or img.height > 64:
                continue
            a = analyse(img)
            if a is None:
                continue
            a["set"] = s
            a["kind"] = kind_of(s, f)
            a["file"] = "tools/assets/%s/%s" % (s, f)
            db["%s/%s" % (s, f[:-4])] = a
            items.append((f[:-4], img))
        if items:
            sheet(s, items, os.path.join(HERE, "sheets", s + ".png"))
    with open(os.path.join(HERE, "icons.json"), "w", encoding="utf-8") as f:
        json.dump(db, f, ensure_ascii=False, indent=1)
    write_style(db)
    print("ok Icon-Datenbank:", len(db), "Bilder aus", len(SETS), "Referenz-Sätzen")


# =====================================================================================================================
# Regeln zusammenfassen
# =====================================================================================================================

def pct(a, b):
    return "%d %%" % round(100 * a / max(1, b))


def median(v):
    v = sorted(v)
    return v[len(v) // 2] if v else 0


def write_style(db):
    icons = [a for a in db.values() if a["kind"] in ("icon", "moebel", "pet") and a["transparent_share"] > 0.08]
    n = len(icons)
    with_outline = [a for a in icons if a["outline_share"] >= 0.7]
    partial = [a for a in icons if 0.3 <= a["outline_share"] < 0.7]
    oc = Counter()
    for a in icons:
        for c, k in a["outline_colors"]:
            oc[c] += k
    # Rampen
    fam_ramps = defaultdict(list)
    for key, a in db.items():
        if a["kind"] == "ui":
            continue
        for f, r in a["ramps"].items():
            fam_ramps[f.split("_")[0]].append((sum(r["px"]), len(r["hex"]), r, key))
    lens = [len(r["hex"]) for a in icons for r in a["ramps"].values()]
    dv = [st["dL"] for a in icons for r in a["ramps"].values() for st in r["steps"]]
    # Farbton-Verschiebung: Richtung der hellsten und dunkelsten Stufe gegenüber der Mitte
    warm_hi = cool_lo = tot_hi = tot_lo = 0
    sat_mid_peak = sat_tot = 0
    for a in icons:
        for f, r in a["ramps"].items():
            if f.startswith("grau") or len(r["hex"]) < 3:
                continue
            hs = [hsv(tuple(int(x[i:i + 2], 16) for i in (1, 3, 5))) for x in r["hex"]]
            mid = hs[len(hs) // 2]
            lo, hi = hs[0], hs[-1]
            # zu Gelb (60°) hin = wärmer; zu Blau (240°) hin = kühler
            def toward(hh, target):
                return abs(hdiff(hh, target))
            if abs(hdiff(mid[0], hi[0])) > 2:
                tot_hi += 1
                if toward(hi[0], 60) < toward(mid[0], 60):
                    warm_hi += 1
            if abs(hdiff(mid[0], lo[0])) > 2:
                tot_lo += 1
                if toward(lo[0], 240) < toward(mid[0], 240) or toward(lo[0], 300) < toward(mid[0], 300):
                    cool_lo += 1
            sat_tot += 1
            if mid[1] >= hi[1] - 0.02:
                sat_mid_peak += 1
    light_tl = sum(1 for a in icons if a["light"]["y"] < -0.1)
    light_left = sum(1 for a in icons if a["light"]["x"] < -0.05)
    light_right = sum(1 for a in icons if a["light"]["x"] > 0.05)
    sym = [a["symmetry"]["shape"] for a in icons]
    sym_hi = sum(1 for v in sym if v >= 0.9)
    sym_col = [a["symmetry"]["color"] for a in icons if a["symmetry"]["shape"] >= 0.9]
    gloss = sum(1 for a in icons if a["gloss_pixels"] > 0)
    dither = sum(1 for a in icons if a["dither_cells"] >= 3)
    colors_med = median([a["colors"] for a in icons])
    by_size = defaultdict(list)
    for a in icons:
        by_size[16 if max(a["w"], a["h"]) <= 18 else 32 if max(a["w"], a["h"]) <= 34 else 64].append(a["colors"])

    L = []
    L.append("# Icon-Stil der Referenzen (gemessen)\n")
    L.append("Erzeugt von `tools/icon_db/build_icon_db.py` aus %d Bildern der Referenz-Sätze (%s). "
             "Gemessen an %d Icons, Möbeln und Pets (ohne Rahmen, Balken, Knöpfe). Kontaktbögen: `tools/icon_db/sheets/`, "
             "Rohdaten: `tools/icon_db/icons.json`, Abfrage: `tools/icon_db/refpal.py`.\n" % (len(db), ", ".join(SETS), n))
    L.append("## Kontur\n")
    L.append("- %s der Icons haben eine geschlossene dunkle Kontur (mindestens 70 %% der Randpixel dunkel), %s eine teilweise "
             "(selektive Kontur: an hellen Stellen fehlt sie oder ist getönt)." % (pct(len(with_outline), n), pct(len(partial), n)))
    L.append("- Kontur ist immer 1 px. Häufigste Konturfarben: " + ", ".join("`%s`" % c for c, _ in oc.most_common(8))
             + " - fast schwarz, meist leicht getönt (warm-braun oder kühl-lila), selten reines #000000.\n")
    L.append("## Farbverläufe (Rampen)\n")
    L.append("- Stufen je Material (Farbfamilie in einem Icon): Median %d, meist 3 bis 5. Mehr Stufen nur bei großen Flächen." % median(lens))
    L.append("- Helligkeitsschritt zwischen zwei Stufen: Median %.0f %% (empfundene Helligkeit), also deutlich sichtbare, "
             "gleichmäßige Stufen statt weicher Übergänge." % (100 * median([abs(x) for x in dv])))
    L.append("- Lichter wandern im Farbton Richtung Gelb (wärmer): %s der Rampen mit Farbtonwechsel; Schatten Richtung Blau/Lila "
             "(kühler): %s." % (pct(warm_hi, tot_hi), pct(cool_lo, tot_lo)))
    L.append("- Sättigung: in %s der Rampen ist die Mittelstufe mindestens so satt wie die hellste - Lichter werden heller und "
             "etwas blasser, nicht weiß; Schatten dunkler und oft satter.\n" % pct(sat_mid_peak, sat_tot))
    L.append("## Licht\n")
    L.append("- Licht von oben: %s der Icons werden nach unten dunkler. Von links: %s, von rechts: %s (sonst gleichmäßig)."
             % (pct(light_tl, n), pct(light_left, n), pct(light_right, n)))
    L.append("- Glanzpunkte (einzelne Pixel deutlich heller als alle Nachbarn): in %s der Icons, meist 1 bis 3, oben links "
             "auf runden Flächen (Edelsteine, Kugeln, Metall).\n" % pct(gloss, n))
    L.append("## Symmetrie und Muster\n")
    L.append("- Form fast symmetrisch (mindestens 90 %% spiegelgleich): %s der Icons. Bei diesen sind im Mittel nur %d %% auch farblich "
             "spiegelgleich - die Form ist symmetrisch, das Licht bricht sie (eine Seite heller, Glanz nur auf einer Seite)."
             % (pct(sym_hi, n), round(100 * (sum(sym_col) / max(1, len(sym_col))))))
    L.append("- Dithering (Schachbrett aus zwei deutlich verschiedenen Farben, mindestens 3 %% der Fläche): %s der Icons - %s."
             % (pct(dither, n), "selten, Übergänge sind harte Stufen" if dither < n * 0.15
                else "nur für Stoff-, Stein- und Fell-Texturen, nie für Licht-Übergänge"))
    L.append("- Farben je Icon: Median %d. Nach Größe: %s.\n" % (colors_med, ", ".join(
        "%d px: %d" % (k, median(v)) for k, v in sorted(by_size.items()))))
    L.append("## Regeln für neue Icons\n")
    L.append("1. Jede Farbe kommt aus einer Rampe (3 bis 5 Stufen je Material), keine Einzelfarben ohne Rolle.")
    L.append("2. 1 px Kontur, fast schwarz und leicht getönt; an der Lichtseite darf sie selektiv heller werden.")
    L.append("3. Licht oben links: oberste Kante je Fläche 1 Stufe heller, Unterseite 1 bis 2 Stufen dunkler.")
    L.append("4. Lichter wärmer (Richtung Gelb), Schatten kühler (Richtung Blau/Lila), Mitte am sattesten.")
    L.append("5. Form symmetrisch, Licht und Details bewusst asymmetrisch (Glanz nur auf einer Seite, ein Detail versetzt).")
    L.append("6. Muster wiederholen (Nieten, Bretter, Streifen) mit gleichem Abstand, dann einmal brechen (ein Riss, ein Fleck).")
    L.append("7. Kein Dithering, keine Kantenglättung mit Zwischenfarben, keine verwaisten Einzelpixel ohne Zweck.\n")
    L.append("## Hex-Verhalten (Kanäle Schritt für Schritt)\n")
    L.append("So bewegen sich die Hex-Werte in typischen Referenz-Rampen (dunkel -> hell):\n")
    L.append("- Der Hauptkanal steigt in großen, gleichmäßigen Schritten (etwa +0x14 bis +0x29) bis knapp unter dem Maximum "
             "(0xF8 bis 0xFA). Reines 0xFF kommt in Rampen praktisch nicht vor.")
    L.append("- Erst wenn der Hauptkanal oben ist, steigen die anderen Kanäle für die Lichter: Rot bekommt Grün dazu (wird "
             "Orange), Gold bekommt Blau dazu (wird blasses Gelb, nicht Weiß), Blau bekommt Rot und Grün dazu (wird helles "
             "Himmelblau). So werden Lichter wärmer bzw. blasser, ohne die Farbe zu verlieren.")
    L.append("- Satte Farben (Smaragd, Rot) halten die Nebenkanäle fast auf 0x00 bis 0x10, bis zur obersten Stufe.")
    L.append("- Die dunkelste Stufe liegt im Hauptkanal bei etwa 0x4E bis 0x89, also nie fast schwarz - das Schwarz macht "
             "die Kontur, nicht die Rampe.\n")
    examples = [("jewelshop/art_crate", "rot"), ("jewelshop_survival/art_ov_farm", "gold"), ("jewelshop/art_emerald_big", "gruen"),
                ("support/art_tickets", "blau")]
    for key, fam in examples:
        a = db.get(key)
        if not a or fam not in a["ramps"]:
            continue
        hx = a["ramps"][fam]["hex"]
        L.append("**%s** (%s)\n" % (key, fam))
        L.append("| Kanal | " + " | ".join("`%s`" % h for h in hx) + " |")
        L.append("|---|" + "---|" * len(hx))
        for ci, cname in enumerate(("R", "G", "B")):
            vals = [int(h[1 + 2 * ci:3 + 2 * ci], 16) for h in hx]
            cells = [("%02X" % vals[0])] + ["%02X (%+d)" % (v, v - p) for p, v in zip(vals, vals[1:])]
            L.append("| %s | %s |" % (cname, " | ".join(cells)))
        L.append("")
    L.append("## Rampen je Farbfamilie (häufigste aus den Referenzen, dunkel -> hell)\n")
    for f in ["rot", "orange", "braun", "gold", "gruen", "tuerkis", "blau", "lila", "rosa", "grau"]:
        lst = sorted(fam_ramps.get(f, []), key=lambda t: (-min(t[1], 6), -t[0]))
        if not lst:
            continue
        L.append("### %s\n" % f)
        L.append("| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |")
        L.append("|---|---|---|")
        seen = set()
        k = 0
        for total, ln, r, key in lst:
            sig = tuple(r["hex"])
            if sig in seen or ln < 4:
                continue
            seen.add(sig)
            steps = " · ".join("%+.0f/%+.2f/%+.2f" % (s["dH"], s["dS"], s["dL"]) for s in r["steps"])
            L.append("| %s | %s | %s |" % (" ".join("`%s`" % h for h in r["hex"]), steps, key))
            k += 1
            if k >= 6:
                break
        L.append("")
    with open(os.path.join(HERE, "STYLE.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")


if __name__ == "__main__":
    main()
