#!/usr/bin/env python3
"""Prüft nach dem Bedrock-Test, ob das, was der Bedrock-Client bekommen hat, auf dem Gerät wirklich darstellbar ist.

* Pack-Lieferung: das vom Client wie von einem Gerät heruntergeladene Pack ist byte-gleich mit KubyBedrock.mcpack.
* Pack-Aufbau: Manifest (Format 2, UUIDs, Version), Glyph-Seiten 16 x 16 Zellen, item_texture.json zeigt nur auf
  vorhandene Texturen, jede Item-Mapping-Icon gibt es in item_texture.json, ui/scoreboards.json ist gültiges JSON.
* Formulare: jedes Knopf-Bild, das der Client bekommen hat (Pfad), liegt im Pack und ist nicht leer.
* Glyphen: jedes eigene Zeichen in Formular-Texten, Knöpfen und Scoreboard hat eine Glyph-Seite und eine sichtbare Zelle.

Aufruf: verify.py bedrock-result.json KubyBedrock.mcpack downloaded.mcpack KubyBedrock-Items.json
Ausgabe: Markdown-Zeilen; Probleme in verify-problems.txt (leer = alles gut).
"""
import hashlib
import io
import json
import re
import sys
import zipfile

from PIL import Image


def main():
    result, original, downloaded, items = sys.argv[1:5]
    r = json.load(open(result, encoding="utf-8"))
    probs = []
    out = []
    z = zipfile.ZipFile(original)
    names = set(z.namelist())

    # ---------- Lieferung ----------
    pack = r.get("pack", {})
    try:
        a = hashlib.sha256(open(original, "rb").read()).hexdigest()
        b = hashlib.sha256(open(downloaded, "rb").read()).hexdigest()
        same = a == b
    except OSError:
        same = False
    out.append("- Pack-Lieferung: angeboten %s, angefordert %s, %d/%d Stücke, %d Bytes, SHA-256 laut Geyser %s, byte-gleich mit KubyBedrock.mcpack: %s"
               % ("ja" if pack.get("offered") else "nein", "ja" if pack.get("requested") else "nein", pack.get("chunks", 0),
                  pack.get("chunkCount", 0), pack.get("saved") or 0, "stimmt" if pack.get("sha256ok") else "falsch", "ja" if same else "nein"))
    if not pack.get("offered"):
        probs.append("Geyser hat unser Pack nicht angeboten")
    elif not same:
        probs.append("Heruntergeladenes Pack weicht ab (SHA-256 Geyser: %s)" % pack.get("sha256ok"))

    # ---------- Aufbau ----------
    m = json.loads(z.read("manifest.json"))
    h = m.get("header", {})
    ok_manifest = m.get("format_version") == 2 and len(h.get("version", [])) == 3 and h.get("uuid") and m.get("modules") \
        and m["modules"][0].get("type") == "resources" and m["modules"][0].get("uuid") != h.get("uuid")
    if not ok_manifest:
        probs.append("manifest.json ungültig")
    pages = sorted(n for n in names if re.match(r"font/glyph_[0-9A-F]{2}\.png$", n))
    sheets = {}
    for n in pages:
        im = Image.open(io.BytesIO(z.read(n))).convert("RGBA")
        sheets[int(n[11:13], 16)] = im
        if im.width != im.height or im.width % 16:
            probs.append("%s: %dx%d ist kein 16x16-Raster" % (n, im.width, im.height))
    tex = json.loads(z.read("textures/item_texture.json"))
    missing_tex = [k for k, v in tex["texture_data"].items() if v["textures"] + ".png" not in names]
    if missing_tex:
        probs.append("item_texture.json: %d Einträge ohne Bild (z. B. %s)" % (len(missing_tex), missing_tex[0]))
    mp = json.load(open(items, encoding="utf-8"))
    defs = [d for lst in mp["items"].values() for d in lst]
    bad_icon = [d["bedrock_identifier"] for d in defs if d.get("bedrock_options", {}).get("icon") not in tex["texture_data"]]
    if mp.get("format_version") != 2 or bad_icon:
        probs.append("Item-Mappings: %d ohne Icon (z. B. %s)" % (len(bad_icon), bad_icon[:1]))
    try:
        json.loads(z.read("ui/scoreboards.json"))
    except Exception as e:
        probs.append("ui/scoreboards.json: %s" % e)
    out.append("- Pack-Aufbau: Manifest %s, %d Glyph-Seiten (%s bis %s), %d Item-Texturen, %d Item-Mappings, alle Icons vorhanden: %s"
               % ("ok" if ok_manifest else "FEHLER", len(pages), pages[0][5:13] if pages else "-", pages[-1][5:13] if pages else "-",
                  len(tex["texture_data"]), len(defs), "ja" if not missing_tex and not bad_icon else "nein"))

    # ---------- Bilder in den Formularen ----------
    seen, missing_img, empty_img = set(), [], []
    for f in r.get("forms", []):
        for b in f.get("buttons", []):
            img = b.get("image")
            if not img or img in seen:
                continue
            seen.add(img)
            path = img + ".png"
            if path not in names:
                missing_img.append(img)
                continue
            im = Image.open(io.BytesIO(z.read(path))).convert("RGBA")
            if im.getchannel("A").getbbox() is None:
                empty_img.append(img)
    out.append("- Knopf-Bilder, die der Client bekommen hat: %d verschiedene, davon im Pack und sichtbar: %d"
               % (len(seen), len(seen) - len(missing_img) - len(empty_img)))
    if missing_img:
        probs.append("Knopf-Bilder fehlen im Pack: %s" % ", ".join(missing_img[:5]))
    if empty_img:
        probs.append("Knopf-Bilder leer: %s" % ", ".join(empty_img[:5]))

    # ---------- Glyphen in Texten und Scoreboard ----------
    texts = []
    for f in r.get("forms", []):
        texts.append(f.get("content") or "")
        texts += [b.get("text") or "" for b in f.get("buttons", [])]
        texts += f.get("elements") or []
    side = (r.get("sidebarLines") or []) + [r.get("sidebarTitle") or ""]
    glyphs = {}
    for t in texts + side:
        for ch in t:
            if 0xE300 <= ord(ch) <= 0xF8FF:
                glyphs[ch] = glyphs.get(ch, 0) + 1
    bad = []
    for ch in glyphs:
        page, slot = ord(ch) >> 8, ord(ch) & 255
        sh = sheets.get(page)
        if sh is None:
            bad.append("%04x (keine Seite)" % ord(ch))
            continue
        cell = sh.width // 16
        c = sh.crop(((slot % 16) * cell, (slot // 16) * cell, (slot % 16 + 1) * cell, (slot // 16 + 1) * cell))
        if c.getchannel("A").getbbox() is None:
            bad.append("%04x (leer)" % ord(ch))
    side_glyphs = sum(1 for t in side for ch in t if 0xE300 <= ord(ch) <= 0xF8FF)
    out.append("- Eigene Zeichen (Icons im Text), die der Client bekommen hat: %d verschiedene, %d im Scoreboard, alle mit sichtbarem Bild: %s"
               % (len(glyphs), side_glyphs, "ja" if not bad else "nein"))
    if bad:
        probs.append("Glyphen ohne Bild: %s" % ", ".join(bad[:8]))
    if not glyphs:
        probs.append("Der Client hat keine eigenen Icon-Zeichen bekommen")

    open("verify-problems.txt", "w").write("\n".join(probs))
    print("\n".join(out))


if __name__ == "__main__":
    main()
