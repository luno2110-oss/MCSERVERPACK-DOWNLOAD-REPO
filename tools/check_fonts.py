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
            # Kit-Schriften (k/i*, k/b*): dasselbe Zeichen in zwei Providern heißt, ein Bild fehlt im Spiel (nur der
            # erste gilt). Die Text-Schriften (k/t*) überdecken Vanilla-Zeichen dagegen absichtlich.
            seen = {}
            kit = ns == "kuby" and os.path.basename(fn)[:1] in ("i", "b") and fn.startswith("k" + os.sep)
            for p in (data["providers"] if kit else []):
                if p["type"] == "space":
                    chars = list(p.get("advances", {}))
                elif p["type"] == "bitmap":
                    chars = [ch for row in p["chars"] for ch in row if ch != "\u0000"]
                else:
                    continue
                for ch in chars:
                    if ch in seen and seen[ch] != p.get("file"):
                        problems.append("%s:%s: Zeichen U+%04X doppelt (%s und %s)" % (ns, fn, ord(ch), seen[ch],
                                                                                    p.get("file", "space")))
                    seen.setdefault(ch, p.get("file", "space"))
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
                # Minecraft legt Zeichen auf 256 x 256 große Schrift-Seiten; größere Zellen fehlen im Spiel (Kästchen),
                # und alles dahinter in derselben Zeile verrutscht
                elif w // len(rows[0]) > 256 or h // len(rows) > 256:
                    problems.append("%s: Zelle %dx%d größer als 256 px" % (where, w // len(rows[0]), h // len(rows)))
    return problems


if __name__ == "__main__":
    found = check_fonts(sys.argv[1] if len(sys.argv) > 1 else "resourcepack/KubyUI")
    for x in found:
        print("FEHLER " + x)
    print("Schriften geprüft: " + ("ok" if not found else "%d Fehler" % len(found)))
    sys.exit(1 if found else 0)
