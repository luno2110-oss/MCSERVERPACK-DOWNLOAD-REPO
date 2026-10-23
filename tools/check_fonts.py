"""Prüft die Schriften eines Resource Packs. Ein einziger Fehler in einer Schrift-Datei lässt Minecraft die ganze
Schrift verwerfen, dann erscheinen alle Zeichen als Kästchen.

Aufruf: python3 tools/check_fonts.py <pack-ordner>   (Exit-Code 1 bei Fehlern)
"""
import json
import os
import struct
import sys


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    return struct.unpack(">II", head[16:24])


def check_fonts(root):
    problems = []
    for ns in sorted(os.listdir(os.path.join(root, "assets"))):
        fdir = os.path.join(root, "assets", ns, "font")
        if not os.path.isdir(fdir):
            continue
        files = sorted(os.path.relpath(os.path.join(d, n), fdir) for d, _, ns in os.walk(fdir) for n in ns)
        for fn in files:
            with open(os.path.join(fdir, fn), encoding="utf-8") as f:
                data = json.load(f)
            for p in data["providers"]:
                if p["type"] != "bitmap":
                    continue
                where = "%s:%s %s" % (ns, fn, p["file"])
                height = p.get("height", 8)
                if p["ascent"] > height:
                    problems.append("%s: ascent %d > height %d" % (where, p["ascent"], height))
                rows = p["chars"]
                if len({len(r) for r in rows}) != 1:
                    problems.append("%s: Zeilen unterschiedlich lang" % where)
                fns, path = p["file"].split(":", 1)
                img = os.path.join(root, "assets", fns, "textures", path)
                if fns == "minecraft" and not os.path.exists(img):
                    continue                    # Vanilla-Textur
                if not os.path.exists(img):
                    problems.append("%s: Textur fehlt" % where)
                    continue
                w, h = png_size(img)
                if w % len(rows[0]) or h % len(rows):
                    problems.append("%s: Bild %dx%d passt nicht zu %dx%d Zeichen" % (where, w, h, len(rows[0]), len(rows)))
    return problems


if __name__ == "__main__":
    found = check_fonts(sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI")
    for x in found:
        print("FEHLER " + x)
    print("Schriften geprüft: " + ("ok" if not found else "%d Fehler" % len(found)))
    sys.exit(1 if found else 0)
