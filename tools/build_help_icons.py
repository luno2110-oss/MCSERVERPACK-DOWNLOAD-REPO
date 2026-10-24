"""Icons der Survival-Hilfe (18 x 18, Originalgröße wie in der Referenz) als SmallIcons im Dialog-Kit.

28 Icons stammen pixelgenau aus den Referenz-Screenshots (tools/assets/help_icons/*.png, GUI-Skala 2, Hintergrund
ausgestanzt). Für Seiten ohne Icon in der Referenz (Regeln, Casino, Angeln) entstehen hier passende im selben Stil:
16-px-Vorlage mit schwarzer Kontur. Die Namen landen als H_... in KitData.SmallIcon (build_kit.py).
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets", "help_icons")
# Reihenfolge = Reihenfolge der SmallIcons (neue nur hinten anhängen)
NAMES = ["einstieg", "axe", "pickaxe", "scroll", "map", "farmwelt", "coins", "stall", "token", "level", "farmpass",
         "paw", "flag", "bed", "ballot", "gear", "rings", "gem", "grave", "chair", "lifebuoy", "discord", "people",
         "hourglass", "hat", "chest", "chart", "infobar"]
OUTLINE = (0, 0, 0, 255)


def outlined(img16):
    """16-px-Bild mit schwarzer Kontur auf 18 x 18 (Stil der Referenz-Icons)."""
    out = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    out.alpha_composite(img16.convert("RGBA"), (1, 1))
    a = out.getchannel("A").load()
    px = out.load()
    edge = []
    for y in range(18):
        for x in range(18):
            if a[x, y]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 18 and 0 <= ny < 18 and a[nx, ny] > 128:
                    edge.append((x, y))
                    break
    for x, y in edge:
        px[x, y] = OUTLINE
    return out


def build(vroot, chip_red):
    """Gibt [(name, bild 18 x 18)] zurück. vroot: Vanilla-Assets, chip_red: 16-px-Chip aus build_casino."""
    result = []
    for n in NAMES:
        result.append(("h_" + n, Image.open(os.path.join(ASSETS, n + ".png")).convert("RGBA")))
    tex = os.path.join(vroot, "assets/minecraft/textures/item")
    for name, img in (("h_regeln", Image.open(os.path.join(tex, "written_book.png"))),
                      ("h_angeln", Image.open(os.path.join(tex, "fishing_rod.png"))),
                      ("h_casino", chip_red)):
        result.append((name, outlined(img.convert("RGBA").crop((0, 0, 16, 16)))))
    return result


if __name__ == "__main__":
    import sys
    sys.path.insert(0, HERE)
    import build_casino
    icons = build(sys.argv[1], build_casino.chip("red"))
    sheet = Image.new("RGBA", (22 * len(icons), 22), (40, 50, 80, 255))
    for i, (_, img) in enumerate(icons):
        sheet.alpha_composite(img, (22 * i + 2, 2))
    sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(sys.argv[2])
