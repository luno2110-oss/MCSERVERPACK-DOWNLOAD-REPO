#!/usr/bin/env python3
"""Bedrock-Pack für Spieler über Geyser (Handy, Konsole, Windows): KubyBedrock.mcpack und Geyser-Item-Mappings.

Bedrock kann das Java-Pack (Schriften mit Verschiebung, Dialoge) nicht anzeigen. Das Plugin schickt Bedrock-Spielern
die Menüs deshalb als native Formulare (de.kuby.kubyplugins.bedrock). Dieses Pack liefert dafür:

* Bilder für Formular-Knöpfe: jedes Zeichen der Java-Icon-Schriften (kuby:k/i…) und jedes Item-Bild (kuby:k/x…) als
  eigene Textur textures/kuby/ui/i_<hex>.png bzw. x_<hex>.png (64 px, scharf hochskaliert).
* Glyphen für Text: kleine Icons (16 px) und Item-Bilder als Zeichen in font/glyph_E3.png … (32-px-Zellen), damit
  Formular-Inhalte, Scoreboard und Chat dieselben Icons zeigen wie auf Java.
* Eigene Items: jedes Item-Modell aus assets/<ns>/items/ (Angeln, Fische, Möbel, Hüte, Casino, Event, Generatoren …)
  als Bedrock-Item mit Icon (2D-Textur oder gerendertes 3D-Modell), dazu die Geyser-Mappings (Format 2).

Ergebnis:
  build/KubyBedrock/             (Pack-Ordner)
  build/KubyBedrock.mcpack       (für plugins/Geyser-Spigot/packs/)
  build/kuby_items.json          (für plugins/Geyser-Spigot/custom_mappings/)
  KubyCore …/resources/bedrock/glyphs.tsv (Zuordnung für das Plugin, wird mit eingebaut)

Aufruf: KUBY_VANILLA=<vanilla-assets> python3 tools/build_bedrock_pack.py
"""
import glob
import hashlib
import json
import os
import shutil
import sys
import uuid
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "preview3d"))
import build_item_icons as bii  # noqa: E402
import r3d  # noqa: E402

PACK = os.path.join(REPO, "resourcepack/KubyUI")
VAN = os.environ.get("KUBY_VANILLA", "vanilla")
BUILD = os.path.join(REPO, "build")
OUT = os.path.join(BUILD, "KubyBedrock")
TSV = os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/resources/bedrock/glyphs.tsv")
NS = uuid.UUID("6f3c2a1e-8b7d-4c5e-9a1f-0b2c3d4e5f60")
FIRST_PAGE = 0xE3          # glyph_E0 … E2 lässt Bedrock für eigene Zeichen (Tasten-Symbole)
LAST_PAGE = 0xF8
IMG = 64                   # Knopf-Bilder
CELL = 32                  # Glyph-Zellen


def asset(ref, kind="textures"):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    for root in (PACK, VAN):
        f = os.path.join(root, "assets", ns, kind, path)
        if os.path.exists(f):
            return f
    return None


def font_cells(font_file):
    """Zeichen -> (Bild, Höhe) aus einem Java-Font (nur Bitmap-Quellen)."""
    out = {}
    try:
        data = json.load(open(font_file, encoding="utf-8"))
    except Exception:
        return out
    for p in data.get("providers", []):
        if p.get("type") != "bitmap":
            continue
        f = asset(p["file"])
        if not f:
            continue
        rows = p["chars"]
        img = Image.open(f).convert("RGBA")
        ncols = max(len(r) for r in rows)
        cw, ch = img.width // max(1, ncols), img.height // max(1, len(rows))
        if len(rows) == 1 and ncols == 1 and img.height > img.width:      # Animation: erstes Bild
            ch = img.width
        for ri, row in enumerate(rows):
            for ci, c in enumerate(row):
                if c in ("\u0000", " ") or c in out:
                    continue
                cell = img.crop((ci * cw, ri * ch, ci * cw + cw, ri * ch + ch))
                if cell.getchannel("A").getbbox() is None:
                    continue
                out[c] = (cell, p.get("height", ch))
    return out


def square(img, size):
    """Bild mittig auf ein Quadrat, scharf skaliert."""
    bbox = img.getchannel("A").getbbox()
    if bbox:
        img = img.crop(bbox)
    w, h = img.size
    k = max(1, size // max(w, h))
    if max(w, h) * k > size:
        k = 1
    big = img.resize((w * k, h * k), Image.NEAREST)
    if max(big.size) > size:
        f = size / max(big.size)
        big = img.resize((max(1, int(w * f)), max(1, int(h * f))), Image.NEAREST)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(big, ((size - big.width) // 2, (size - big.height) // 2))
    return out


def glyph_cell(img):
    """Glyph: links ausgerichtet (Bedrock misst die Breite bis zum letzten Pixel), senkrecht mittig, 32 px."""
    bbox = img.getchannel("A").getbbox()
    if bbox:
        img = img.crop(bbox)
    w, h = img.size
    f = min(CELL / max(1, h), CELL / max(1, w))
    k = int(f) if f >= 1 else f
    big = img.resize((max(1, int(w * k)), max(1, int(h * k))), Image.NEAREST)
    out = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    out.alpha_composite(big, (0, (CELL - big.height) // 2))
    return out


def kit_icon_chars():
    """Zeichen echter Icons aus KitData.java: Icon (16-px-Fassung, erstes Zeichen) und SmallIcon."""
    import re
    src = open(os.path.join(REPO, "KubyCore-v2.0.14/KubyPlugins/src/main/java/de/kuby/kubyplugins/ui/KitData.java"),
               encoding="utf-8").read()
    out = set()
    for enum in ("Icon", "SmallIcon"):
        i = src.find("public enum %s {" % enum)
        if i < 0:
            continue
        j = src.find("\n    }", i)
        for m in re.finditer(r"^\s+[A-Z0-9_]+\('\\u([0-9a-fA-F]{4})'", src[i:j], re.M):
            out.add(chr(int(m.group(1), 16)))
    return out


def iconish(img):
    """Echtes Icon (nicht Balken-, Rahmen- oder Strichstück): sichtbarer Teil grob quadratisch und nicht winzig."""
    bbox = img.getchannel("A").getbbox()
    if not bbox:
        return False
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    return w >= 5 and h >= 5 and 0.34 <= w / h <= 3.0


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, "textures/kuby/ui"))
    os.makedirs(os.path.join(OUT, "textures/kuby/items"))
    os.makedirs(os.path.join(OUT, "font"))

    # ---------- Icon- und Item-Zeichen aus den Java-Schriften ----------
    icons = {}
    for f in sorted(glob.glob(os.path.join(PACK, "assets/kuby/font/k/i*.json"))):
        for c, v in font_cells(f).items():
            icons.setdefault(c, v)
    items = {}
    for f in sorted(glob.glob(os.path.join(PACK, "assets/kuby/font/k/x*.json"))):
        for c, v in font_cells(f).items():
            items.setdefault(c, v)
    print("Icons:", len(icons), "Item-Bilder:", len(items))

    kit = kit_icon_chars()
    print("Kit-Icons:", len(kit))
    rows = []
    glyphs = []                     # (art, Java-Zeichen, Bild)
    for c in sorted(icons):
        img, h = icons[c]
        name = "-"
        if h >= 12 and iconish(img):
            name = "i_%04x" % ord(c)
            square(img, IMG).save(os.path.join(OUT, "textures/kuby/ui", name + ".png"))
        if c in kit and h <= 20 and iconish(img):
            glyphs.append(("i", c, img))
        rows.append(["i", c, name])
    for c in sorted(items):
        img, h = items[c]
        name = "-"
        if ord(c) < 0xE800:         # 32-px-Fassung als Knopf-Bild
            name = "x_%04x" % ord(c)
            square(img, IMG).save(os.path.join(OUT, "textures/kuby/ui", name + ".png"))
        else:                       # 16-px-Fassung als Glyph (aus dem 32-px-Bild, schärfer)
            big = items.get(chr(ord(c) - 0x0800))
            glyphs.append(("x", c, big[0] if big else img))
        rows.append(["x", c, name])

    # Scoreboard-Icons und Modus-Logos (kubyplugins:sb): Glyphen für die Bedrock-Sidebar
    try:
        sb = json.load(open(os.path.join(PACK, "assets/kubyplugins/font/sb.json"), encoding="utf-8"))
        for p in sb["providers"]:
            if p.get("type") != "bitmap" or not ("/sb_" in p["file"] or "/logo_" in p["file"]):
                continue
            f = asset(p["file"])
            if not f:
                continue
            c = p["chars"][0][0]
            glyphs.append(("s", c, Image.open(f).convert("RGBA")))
            rows.append(["s", c, "-"])
    except Exception as e:
        print("Scoreboard-Icons nicht gelesen:", e)

    # ---------- Glyph-Seiten ----------
    code = {}
    page, slot, sheet = FIRST_PAGE, 0, None

    def flush():
        if sheet is not None:
            sheet.save(os.path.join(OUT, "font", "glyph_%02X.png" % page))

    for art, c, img in glyphs:
        if sheet is None:
            sheet = Image.new("RGBA", (16 * CELL, 16 * CELL), (0, 0, 0, 0))
        sheet.alpha_composite(glyph_cell(img), ((slot % 16) * CELL, (slot // 16) * CELL))
        code[(art, c)] = (page << 8) | slot
        slot += 1
        if slot == 256:
            flush()
            page, slot, sheet = page + 1, 0, None
            if page > LAST_PAGE:
                print("Zu viele Glyphen, Rest ohne Text-Icon")
                break
    flush()
    print("Glyphen:", len(code), "Seiten bis", "%02X" % page)

    os.makedirs(os.path.dirname(TSV), exist_ok=True)
    with open(TSV, "w", encoding="utf-8") as out:
        out.write("# Art\tJava\tBedrock-Glyph\tBild  (tools/build_bedrock_pack.py)\n")
        for art, c, name in rows:
            g = code.get((art, c))
            out.write("%s\t%04x\t%s\t%s\n" % (art, ord(c), "%04x" % g if g else "-", name))

    # ---------- Eigene Items (Geyser-Mappings, Format 2) ----------
    bases = item_bases()
    texture_data = {}
    mapping = {}
    count = 0
    attachables = 0
    os.makedirs(os.path.join(OUT, "attachables/kuby"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "models/entity/kuby"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "textures/kuby/att"), exist_ok=True)
    for f in sorted(glob.glob(os.path.join(PACK, "assets/*/items/**/*.json"), recursive=True)):
        rel = os.path.relpath(f, os.path.join(PACK, "assets"))
        ns = rel.split(os.sep)[0]
        if ns == "minecraft":
            continue
        path = rel.split(os.sep, 2)[2][:-5].replace(os.sep, "/")
        ident = ns + ":" + path
        try:
            sel = bii.pick(json.load(open(f, encoding="utf-8")).get("model"))
        except Exception:
            sel = None
        icon = None
        if sel:
            _, model, tints = sel
            elements, textures, generated = r3d.resolve_model(model)
            if generated:
                icon = bii.flat_icon(textures, tints)
            elif elements:
                gui, light = bii.model_chain(model)
                icon = bii.render_3d(model, tints, gui, light)
                if icon is None or icon.getbbox() is None:
                    # flache Platten ohne display.gui (Filz, Karten, Knöpfe) sieht man von vorn nur als Kante:
                    # Schrägansicht wie ein Block im Inventar, sonst die Textur selbst als Bild
                    icon = bii.render_3d(model, tints, {"rotation": [30, 225, 0], "scale": [0.625] * 3}, "side")
                if icon is None or icon.getbbox() is None:
                    icon = _texture_icon(textures)
        if icon is None:
            continue
        key = ns + "." + path.replace("/", "_")
        icon.resize((IMG, IMG), Image.NEAREST).save(os.path.join(OUT, "textures/kuby/items", key + ".png"))
        texture_data[key] = {"textures": "textures/kuby/items/" + key}
        three_d = bool(sel) and not generated and bool(elements)
        for base in bases_for(ident, bases):
            bid = ns + ":" + (base.split(":")[1] + "_" if base != "minecraft:paper" else "") + path.replace("/", "_")
            if three_d:
                try:
                    if attachable(bid, key + "_" + base.split(":")[1], elements, textures):
                        attachables += 1
                except Exception as e:  # ein kaputtes Modell soll das Pack nicht verhindern
                    print("Attachable", bid, "übersprungen:", e)
            mapping.setdefault(base, []).append({
                "type": "definition",
                "model": ident,
                "bedrock_identifier": ns + ":" + (base.split(":")[1] + "_" if base != "minecraft:paper" else "") + path.replace("/", "_"),
                "bedrock_options": {"icon": key, "allow_offhand": True,
                                    "display_handheld": base in ("minecraft:fishing_rod",)},
            })
            count += 1
    print("Eigene Items:", len(texture_data), "Bedrock-Items:", count, "3D-Attachables:", attachables)

    # Scoreboard ohne rote Zahlen rechts (die Java-Sidebar blendet sie mit NumberFormat.blank aus)
    os.makedirs(os.path.join(OUT, "ui"), exist_ok=True)
    json.dump({"namespace": "scoreboard", "scoreboard_sidebar_score": {"ignored": True}},
              open(os.path.join(OUT, "ui/scoreboards.json"), "w"), indent=1)
    json.dump({"resource_pack_name": "kuby", "texture_name": "atlas.items", "texture_data": texture_data},
              open(os.path.join(OUT, "textures/item_texture.json"), "w"), indent=1)
    json.dump({"format_version": 2, "items": mapping}, open(os.path.join(BUILD, "kuby_items.json"), "w"), indent=1)

    # ---------- Manifest, Icon, Packen ----------
    digest = hashlib.sha1()
    for root, _, files in sorted(os.walk(OUT)):
        for name in sorted(files):
            digest.update(name.encode())
            digest.update(open(os.path.join(root, name), "rb").read())
    h = int(digest.hexdigest()[:8], 16)
    version = [1, (h >> 16) & 0x7FFF, h & 0xFFFF]
    manifest = {
        "format_version": 2,
        "header": {"name": "Kuby (Bedrock)", "description": "Icons, Formular-Bilder und eigene Items für KubyPlugins",
                   "uuid": str(uuid.uuid5(NS, "header")), "version": version, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": str(uuid.uuid5(NS, "resources")), "version": version}],
    }
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"), indent=1)
    logo = next((f for f in (os.path.join(PACK, "pack.png"), os.path.join(PACK, "assets/kubyplugins/textures/sb/logo_hub.png"))
                 if os.path.exists(f)), None)
    if logo:
        Image.open(logo).convert("RGBA").resize((256, 256), Image.NEAREST).save(os.path.join(OUT, "pack_icon.png"))
    mc = os.path.join(BUILD, "KubyBedrock.mcpack")
    with zipfile.ZipFile(mc, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in sorted(os.walk(OUT)):
            for name in sorted(files):
                full = os.path.join(root, name)
                info = zipfile.ZipInfo(os.path.relpath(full, OUT), (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, open(full, "rb").read())
    print("Pack:", mc, "%.1f MB" % (os.path.getsize(mc) / 1e6), "Version", version)


# ---------------------------------------------------------------------------------------------------------------------
# 3D-Modelle für Bedrock (Attachables): Java-Elemente -> Bedrock-Geometrie wie java2bedrock (Kas-tle), damit
# Item-Anzeigen (Tische, Karten, Chips, Keks, Möbel …) über die Geyser-Erweiterung GeyserDisplayEntity als echtes 3D
# erscheinen. Würfel: origin [8 - to.x, from.y, from.z - 8], Knochen "geyser_custom" mit Bindung und Pivot [0, 8, 0];
# die Erweiterung setzt die Anzeige um 0,5 tiefer (y-offset), so liegt die Modellmitte am Ort der Anzeige.
# ---------------------------------------------------------------------------------------------------------------------

ATLAS_MAX = 1024


def _texture_icon(textures):
    """Ersatz-Bild für ein Modell ohne sichtbares Inventar-Icon: die erste echte Textur (particle, top, front …)."""
    for k in ("particle", "top", "front", "side"):
        ref = textures.get(k)
        while isinstance(ref, str) and ref.startswith("#"):
            ref = textures.get(ref[1:])
        if not ref:
            continue
        im = _load_tex(ref)
        if im is not None and im.getbbox() is not None:
            side = min(im.width, im.height)
            return im.crop((0, 0, side, side))
    return None


def _load_tex(ref):
    f = asset(ref.split(":", 1)[0] + ":" + ref.split(":", 1)[1] + ".png" if ":" in ref else "minecraft:" + ref + ".png")
    if f is None:
        return None
    im = Image.open(f).convert("RGBA")
    if im.height > im.width and im.height % im.width == 0 and os.path.exists(f + ".mcmeta"):
        im = im.crop((0, 0, im.width, im.width))  # animierte Textur: erstes Bild
    return im


def _atlas(refs):
    """Packt Texturen untereinander/nebeneinander in einen Atlas. Gibt (Bild, {ref: (x, y, w, h)}) zurück."""
    imgs = {r: _load_tex(r) for r in refs}
    imgs = {r: i for r, i in imgs.items() if i is not None}
    if not imgs:
        return None, {}
    # einfache Regal-Packung nach Höhe
    order = sorted(imgs, key=lambda r: -imgs[r].height)
    width = max(max(i.width for i in imgs.values()), 16)
    width = max(width, min(ATLAS_MAX, sum(i.width for i in imgs.values())))
    x = y = row = 0
    frames = {}
    for r in order:
        im = imgs[r]
        if x + im.width > width:
            x, y, row = 0, y + row, 0
        frames[r] = (x, y, im.width, im.height)
        x += im.width
        row = max(row, im.height)
    height = y + row
    # Bedrock mag Zweierpotenzen
    W = 1 << (width - 1).bit_length()
    H = 1 << (max(height, 1) - 1).bit_length()
    atlas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for r, (fx, fy, fw, fh) in frames.items():
        atlas.paste(imgs[r], (fx, fy))
    return atlas, frames


DEFAULT_UV = {
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
}


def attachable(bid, key, elements, textures):
    refs = set()
    for el in elements:
        for face in el.get("faces", {}).values():
            refs.add(r3d.tex_ref(textures, face.get("texture", "#missing")))
    refs = {r for r in refs if not r.startswith("#") and r != "missing"}
    atlas, frames = _atlas(refs)
    if atlas is None:
        return False
    AW, AH = atlas.size

    def rnd(v):
        return round(v * 10000) / 10000

    cubes_plain, groups = [], {}
    for el in elements:
        f, t = el["from"], el["to"]
        cube = {"origin": [rnd(8 - t[0]), rnd(f[1]), rnd(f[2] - 8)],
                "size": [rnd(t[0] - f[0]), rnd(t[1] - f[1]), rnd(t[2] - f[2])], "uv": {}}
        for side, face in el.get("faces", {}).items():
            ref = r3d.tex_ref(textures, face.get("texture", "#missing"))
            if ref not in frames:
                continue
            fx, fy, fw, fh = frames[ref]
            uv = face.get("uv") or DEFAULT_UV[side](f, t)
            u0 = (uv[0] * fw / 16 + fx) * 16 / AW
            v0 = (uv[1] * fh / 16 + fy) * 16 / AH
            u1 = (uv[2] * fw / 16 + fx) * 16 / AW
            v1 = (uv[3] * fh / 16 + fy) * 16 / AH
            xs = max(-1, min(1, u1 - u0))
            ys = max(-1, min(1, v1 - v0))
            if side in ("up", "down"):
                cube["uv"][side] = {"uv": [rnd(u1 - 0.016 * xs), rnd(v1 - 0.016 * ys)],
                                    "uv_size": [rnd(u0 - u1 + 0.016 * xs), rnd(v0 - v1 + 0.016 * ys)]}
            else:
                cube["uv"][side] = {"uv": [rnd(u0 + 0.016 * xs), rnd(v0 + 0.016 * ys)],
                                    "uv_size": [rnd(u1 - u0 - 0.016 * xs), rnd(v1 - v0 - 0.016 * ys)]}
        rot = el.get("rotation")
        if rot and rot.get("angle"):
            o = rot.get("origin", [8, 8, 8])
            pivot = (rnd(8 - o[0]), rnd(o[1]), rnd(o[2] - 8))
            a = float(rot["angle"])
            r = {"x": (-a, 0, 0), "y": (0, -a, 0), "z": (0, 0, a)}[rot.get("axis", "y")]
            groups.setdefault((pivot, r), []).append(cube)
        else:
            cubes_plain.append(cube)
    geo = "geometry.kuby." + key
    bones = [
        {"name": "geyser_custom", "binding": "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)",
         "pivot": [0, 8, 0]},
        {"name": "geyser_custom_x", "parent": "geyser_custom", "pivot": [0, 8, 0]},
        {"name": "geyser_custom_y", "parent": "geyser_custom_x", "pivot": [0, 8, 0]},
        {"name": "geyser_custom_z", "parent": "geyser_custom_y", "pivot": [0, 8, 0], "cubes": cubes_plain},
    ]
    for i, ((pivot, r), cubes) in enumerate(groups.items()):
        bones.append({"name": "rot_%d" % (i + 1), "parent": "geyser_custom_z", "pivot": list(pivot), "rotation": list(r),
                      "cubes": cubes})
    span = max(max(abs(c) for el in elements for c in el["from"] + el["to"]) / 16 * 2, 2)
    json.dump({"format_version": "1.16.0", "minecraft:geometry": [{
        "description": {"identifier": geo, "texture_width": 16, "texture_height": 16,
                        "visible_bounds_width": round(span + 2, 1), "visible_bounds_height": round(span + 2, 1),
                        "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}, open(os.path.join(OUT, "models/entity/kuby", key + ".geo.json"), "w"), separators=(",", ":"))
    atlas.save(os.path.join(OUT, "textures/kuby/att", key + ".png"))
    json.dump({"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": bid,
        "materials": {"default": "entity_alphatest_one_sided", "enchanted": "entity_alphatest_one_sided"},
        "textures": {"default": "textures/kuby/att/" + key, "enchanted": "textures/misc/enchanted_item_glint"},
        "geometry": {"default": geo},
        "render_controllers": ["controller.render.item_default"]}}},
        open(os.path.join(OUT, "attachables/kuby", key + ".json"), "w"), indent=1)
    return True


def item_bases():
    """Basis-Items je Modell: Event-Vorlagen (sammeln: item/material) aus den Survival-Ressourcen, sonst Regeln."""
    out = {}
    try:
        import yaml
        for f in glob.glob(os.path.join(REPO, "KubySurvival-v1.0.0/KubySurvival/src/main/resources/events/*.yml")):
            y = yaml.safe_load(open(f, encoding="utf-8")) or {}
            s = y.get("sammeln") or {}
            for k_item, k_mat in (("item", "material"), ("selten-item", "selten-material")):
                if s.get(k_item) and s.get(k_mat):
                    out["kuby:event/" + s[k_item]] = {"minecraft:" + s[k_mat].lower()}
    except Exception as e:
        print("Event-Vorlagen nicht gelesen:", e)
    out["kuby:event/candy_corn"] = {"minecraft:orange_dye"}
    out["kuby:event/candy_corn_purple"] = {"minecraft:purple_dye"}
    return out


def bases_for(ident, bases):
    """Auf welchen Java-Items das Plugin dieses Modell setzt (immer auch Papier: Menüs und Vorschauen)."""
    out = {"minecraft:paper"}
    path = ident.split(":", 1)[1]
    if ident in bases:
        out |= bases[ident]
    elif path.startswith("event/"):
        out |= {"minecraft:sunflower"}
    if path.startswith("angeln/rod_"):
        out = {"minecraft:fishing_rod"}
    elif path.startswith("fish/"):
        out = {"minecraft:cod", "minecraft:paper"}
    elif path.startswith("generator/"):
        out = {"minecraft:spawner", "minecraft:paper"}
    elif path.startswith("cookie/"):
        out = {"minecraft:cookie"}
    return sorted(out)


if __name__ == "__main__":
    main()
