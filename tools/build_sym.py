"""Kleine farbige Symbole (8 x 8) für Chat, Aktionsleiste und Hologramme: Schrift kuby:sym.

Ein Zeichen ist so hoch wie eine Textzeile und steht auf der Grundlinie. Erzeugt außerdem
de.kuby.kubyplugins.ui.Sym (Zeichen je Symbol). Eigene Zeichnungen.
"""
import json
import os

from PIL import Image

T = (0, 0, 0, 0)
HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_OUT = os.path.join(HERE, "..", "KubyCore-v2.0.14", "KubyPlugins", "src", "main", "java", "de", "kuby",
                        "kubyplugins", "ui", "Sym.java")


def rgb(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# Name -> (Muster 8 x 8, Farben)
SYMS = {
    "fish": (["........", "......bb", "..bbbb.b", ".bwbbbbb", "bbbbbbb.", ".bbbb..b", "......bb", "........"],
             {"b": "5AB4F0", "w": "0E1E30"}),
    "rod": (["......wb", ".....w.b", "....w..b", "...w...b", "..w....b", ".w.....b", "w......h", "........"],
            {"w": "A8743A", "b": "C8C8C8", "h": "D8D8D8"}),
    "gem": (["........", "..cccc..", ".cwccCc.", "cwcccCCc", ".ccccCc.", "..cccc..", "...cc...", "........"],
            {"c": "4AE0F0", "C": "1E9AB8", "w": "E8FCFF"}),
    "book": (["........", ".rrrrrw.", ".rRRRrw.", ".rrrrrw.", ".rRRRrw.", ".rrrrrw.", ".dddddd.", "........"],
             {"r": "C83030", "R": "F2C23A", "w": "F2EEE0", "d": "7A1C1C"}),
    "anvil": (["........", ".gggggg.", "gGGGGGGg", "..gggg..", "...gg...", "..gggg..", ".gggggg.", "........"],
              {"g": "5E626A", "G": "8A8E96"}),
    "coin": (["..yyyy..", ".yYYYYy.", "yYyyyyYy", "yYyddyYy", "yYyddyYy", "yYyyyyYy", ".yYYYYy.", "..yyyy.."],
             {"y": "C8901A", "Y": "F2C23A", "d": "FFE27A"}),
    "sparkle": (["...c....", "...c....", "..ccc...", "ccccccc.", "..ccc...", "...c....", "...c....", "........"],
                {"c": "5AE8F8"}),
    "chest": (["........", ".bbbbbb.", "bBBBBBBb", "dddyyddd", "bBByyBBb", "bBBBBBBb", ".dddddd.", "........"],
              {"b": "6A4220", "B": "B8803E", "d": "3A2410", "y": "F2C23A"}),
    "star": (["...y....", "...y....", "..yyy...", "yyyyyyy.", ".yyyyy..", ".yy.yy..", ".y...y..", "........"],
             {"y": "F2D23A"}),
    "heart": (["........", ".rr.rr..", "rRrrrrr.", "rrrrrrr.", ".rrrrr..", "..rrr...", "...r....", "........"],
              {"r": "E8384A", "R": "FF9AA8"}),
    "clock": (["..gggg..", ".gwwwwg.", "gwwkwwwg", "gwwkwwwg", "gwwkkkwg", "gwwwwwwg", ".gwwwwg.", "..gggg.."],
              {"g": "C8901A", "w": "F2F2E0", "k": "3A3A3A"}),
    "chip": (["..rrrr..", ".rwrrwr.", "rrrwwrrr", "rwwrrwwr", "rwwrrwwr", "rrrwwrrr", ".rwrrwr.", "..rrrr.."],
             {"r": "D83040", "w": "F4F4F4"}),
    "dice": (["........", ".wwwwww.", ".wkwwkw.", ".wwwwww.", ".wwkwww.", ".wkwwkw.", ".wwwwww.", "........"],
             {"w": "F4F4F4", "k": "1E1E1E"}),
    "gift": (["...rr...", "..r..r..", "pppppppp", "pppRpppp", ".ppRppp.", ".ppRppp.", ".ppRppp.", "........"],
             {"r": "F2D23A", "p": "C83CA8", "R": "F2D23A"}),
    "arrow": (["...g....", "...gg...", "gggggg..", "ggggggg.", "gggggg..", "...gg...", "...g....", "........"],
              {"g": "F2D23A"}),
    "crown": (["........", "y..y..y.", "yy.y.yy.", "yyyyyyy.", "yrygyby.", "yyyyyyy.", "........", "........"],
              {"y": "F2C23A", "r": "E8384A", "g": "3AC85A", "b": "4A8AE8"}),
    "pinkgem": (["........", "..pppp..", ".pwppPp.", "pwpppPPp", ".ppppPp.", "..pppp..", "...pp...", "........"],
                {"p": "F06AC8", "P": "A82A88", "w": "FFE0F4"}),
    "clover": ([".gg.gg..", "gGggGgg.", "gggggg..", ".gggg...", "gGggGgg.", ".gg.gg..", "...g....", "..g....."],
               {"g": "3AC85A", "G": "8AF08A"}),
    "music": (["....kkkk", "....k..k", "....k..k", "....k..k", "..kkk.kk", ".kkkk.kk", ".kkk....", "........"],
              {"k": "F2D23A"}),
}
ORDER = list(SYMS)


def build(out):
    tex = os.path.join(out, "assets/kuby/textures/sym")
    font = os.path.join(out, "assets/kuby/font")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(font, exist_ok=True)
    sheet = Image.new("RGBA", (8 * len(ORDER), 8), T)
    for i, name in enumerate(ORDER):
        rows, pal = SYMS[name]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    sheet.putpixel((i * 8 + x, y), rgb(pal[ch]))
    sheet.save(os.path.join(tex, "sym.png"))
    chars = "".join(chr(0xE000 + i) for i in range(len(ORDER)))
    with open(os.path.join(font, "sym.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": [{"type": "bitmap", "file": "kuby:sym/sym.png", "height": 8, "ascent": 7,
                                  "chars": [chars]}]}, f, ensure_ascii=True)
    L = ["package de.kuby.kubyplugins.ui;", "", "import net.kyori.adventure.key.Key;",
         "import net.kyori.adventure.text.Component;", "import net.kyori.adventure.text.format.NamedTextColor;",
         "import net.kyori.adventure.text.format.ShadowColor;", "",
         "// ERZEUGT von tools/build_sym.py - nicht von Hand ändern.", "",
         "/** Kleine farbige Symbole (8 x 8, Schrift kuby:sym) für Chat, Aktionsleiste und Hologramme. */",
         "public enum Sym {"]
    L.append(",\n".join("    %s('\\u%04x')" % (n.upper(), 0xE000 + i) for i, n in enumerate(ORDER)) + ";")
    L += ["", "    public static final Key FONT = Key.key(\"kuby\", \"sym\");", "", "    public final char c;", "",
          "    Sym(char c) {", "        this.c = c;", "    }", "",
          "    /** Das Symbol als Komponente (weiß, damit die Farben stimmen). */",
          "    public Component component() {",
          "        return Component.text(String.valueOf(c)).font(FONT).color(NamedTextColor.WHITE);", "    }", "",
          "    /** Ohne Schatten (für helle Hintergründe). */",
          "    public Component flat() {", "        return component().shadowColor(ShadowColor.none());", "    }",
          "", "    /** MiniMessage-Text des Symbols. */", "    public String mini() {",
          "        return \"<white><font:kuby:sym>\" + c + \"</font></white>\";", "    }", "}", ""]
    with open(JAVA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    return sheet
