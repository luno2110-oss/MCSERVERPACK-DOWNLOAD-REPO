"""Die Möbel des Survival-Juwelenshops als Modelle (Bau-Sprache aus build_furniture.py), je Reiter wie im Shop.

Maße in Pixeln (16 = 1 Block), Boden y = 0, Mitte des ersten Blocks x = z = 8, vorne = Norden (kleines z).
Vorbild sind die Bilder im Shop (tools/assets/jewelshop_survival/f_<id>.png); Vergleich mit
python3 tools/build_furniture.py --sheet <bild.png> --only <reiter>.
"""
import numpy as np
from PIL import Image

from build_furniture import Model, custom

# Holz und Stoffe
OAK, OAK_LOG = "oak_planks", "stripped_oak_log"
SPRUCE, SPRUCE_LOG = "spruce_planks", "stripped_spruce_log"
DARK, DARK_LOG = "dark_oak_planks", "stripped_dark_oak_log"
JUNGLE_LOG = "stripped_jungle_log"


def _rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def cloth(name, base, var=10, seed=1, ring=None, border=None, stripes=None):
    """Eigene Stoff-Textur 16 x 16: Grundfarbe mit leichtem Rauschen, wahlweise Ring (Tischdecke), Rand, Streifen."""
    rng = np.random.default_rng(seed)
    img = _rgb(base)[None, None, :] + rng.integers(-var, var + 1, (16, 16))[..., None]
    yy, xx = np.mgrid[0:16, 0:16]
    if stripes:
        col, width = stripes
        img[(xx // width) % 2 == 1] = _rgb(col) + rng.integers(-var, var + 1, (16, 16))[(xx // width) % 2 == 1][..., None]
    if ring:
        d = np.hypot(xx - 7.5, yy - 7.5)
        img[(d >= 3.6) & (d <= 4.9)] = _rgb(ring)
        img[d <= 1.2] = _rgb(ring)
    if border:
        img[[0, 15], :] = _rgb(border)
        img[:, [0, 15]] = _rgb(border)
    a = np.zeros((16, 16, 4), dtype=np.uint8)
    a[..., :3] = np.clip(img, 0, 255)
    a[..., 3] = 255
    return custom(name, Image.fromarray(a, "RGBA"))


# =====================================================================================================================
# Sitzen
# =====================================================================================================================

def chair(mid, seat, frame, cushion=None, back="ladder", tall=False):
    m = Model(mid)
    m.legs(3, 3, 13, 13, 2, 7, frame)
    m.box((2, 7, 2), (14, 9, 14), seat)
    top = 9
    if cushion:
        m.box((3, 9, 3), (13, 10, 13), cushion)
        top = 10
    h = 25 if tall else 22
    m.box((3, 9, 11), (5, h, 13), frame).box((11, 9, 11), (13, h, 13), frame)
    if back == "ladder":
        for y in (12, 15.5, 19):
            m.box((5, y, 11.5), (11, y + 1.5, 12.5), frame)
        m.box((3, h - 1.5, 11), (13, h, 13), frame)
    else:  # Sprossen senkrecht
        m.box((3, 12, 11.5), (13, 13.5, 12.5), frame)
        for x in (6, 7.5, 9):
            m.box((x, 13.5, 11.75), (x + 1, h - 1.5, 12.25), frame)
        m.box((3, h - 2, 11), (13, h, 13), frame)
    m.seats = [(8, 7)]
    m.seat_y = top
    return m


def bench(mid, seat, frame, cushion=None, back=False):
    m = Model(mid)
    m.width = 2
    for x in (2, 28):
        m.box((x, 0, 3), (x + 2, 6, 5), frame).box((x, 0, 11), (x + 2, 6, 13), frame)
        m.box((x, 2, 5), (x + 2, 3.5, 11), frame)
    m.box((1, 6, 2), (31, 8, 14), seat)
    top = 8
    if cushion:
        m.box((2, 8, 3), (30, 9, 13), cushion)
        top = 9
    if back:
        for x in (2, 28):
            m.box((x, 8, 11), (x + 2, 20, 13), frame)
        for y in (12, 16):
            m.box((4, y, 11.5), (28, y + 2, 12.5), seat)
        m.box((1, 19, 11), (31, 21, 13), frame)
    m.seats = [(8, 7), (24, 7)]
    m.seat_y = top
    return m


def sitzen():
    out = [
        chair("holzstuhl", OAK, DARK_LOG),
        chair("stuhl_blau", OAK, DARK_LOG, "blue_wool"),
        chair("stuhl_weiss", OAK, DARK_LOG, "white_wool"),
        chair("lehnstuhl", SPRUCE, SPRUCE_LOG, back="spindle", tall=True),
        bench("holzbank", OAK, DARK_LOG),
        bench("bank_gruen", DARK, DARK_LOG, "moss_block"),
        bench("bank_weiss", DARK, DARK_LOG, "white_wool"),
        bench("lehnbank", OAK, DARK_LOG, back=True),
    ]
    # Bürostuhl: Fuß aus fünf Armen, Säule, Sitz und Lehne aus Holz, schwarze Armlehnen
    m = Model("buerostuhl")
    m.box((7, 0, 2), (9, 1.5, 14), "black_concrete").box((2, 0, 7), (14, 1.5, 9), "black_concrete")
    m.box((7, 1.5, 7), (9, 7, 9), "gray_concrete")
    m.box((3, 7, 3), (13, 9, 13), "black_concrete")
    m.box((3.5, 9, 3), (12.5, 10.5, 12.5), OAK)
    m.box((4, 10.5, 12), (12, 22, 13.5), OAK).box((3.5, 10, 12.5), (12.5, 11, 14), "black_concrete")
    m.box((2, 9, 4), (3, 13, 10), "black_concrete").box((13, 9, 4), (14, 13, 10), "black_concrete")
    m.box((2, 13, 4), (3.5, 14, 10), "black_concrete").box((12.5, 13, 4), (14, 14, 10), "black_concrete")
    m.seats, m.seat_y = [(8, 7)], 10.5
    out.append(m)
    # Klapphocker: gekreuzte Beine, Platte
    m = Model("klapphocker")
    for z in (3, 11):
        m.box((3, 0, z), (13, 2, z + 2), "polished_deepslate", rot=("x", 0, (8, 0, 8)))
    m.box((7, 0, 3), (9, 9, 5), "polished_deepslate", rot=("z", 45, (8, 4.5, 4)))
    m.box((7, 0, 3), (9, 9, 5), "polished_deepslate", rot=("z", -45, (8, 4.5, 4)))
    m.box((7, 0, 11), (9, 9, 13), "polished_deepslate", rot=("z", 45, (8, 4.5, 12)))
    m.box((7, 0, 11), (9, 9, 13), "polished_deepslate", rot=("z", -45, (8, 4.5, 12)))
    m.box((2, 8, 2), (14, 10, 14), "deepslate_tiles")
    m.seats, m.seat_y = [(8, 8)], 10
    out.append(m)
    # Barhocker: Fuß, Säule, runde Platte (weiß)
    m = Model("barhocker")
    m.box((3, 0, 3), (13, 1, 13), "gray_concrete")
    m.box((6.5, 1, 6.5), (9.5, 10, 9.5), "light_gray_concrete")
    m.box((3, 10, 3), (13, 12, 13), "white_concrete")
    m.box((2, 12, 2), (14, 13, 14), "quartz_block_top")
    m.seats, m.seat_y = [(8, 8)], 13
    out.append(m)
    # Casino-Hocker: hoch, vier Beine mit Querstreben, grünes Polster
    m = Model("casino_hocker")
    m.legs(3, 3, 13, 13, 2, 15, JUNGLE_LOG)
    for y in (4, 10):
        m.box((5, y, 3.5), (11, y + 1, 4.5), "gold_block").box((5, y, 11.5), (11, y + 1, 12.5), "gold_block")
        m.box((3.5, y, 5), (4.5, y + 1, 11), "gold_block").box((11.5, y, 5), (12.5, y + 1, 11), "gold_block")
    m.box((2, 15, 2), (14, 16, 14), DARK)
    m.box((2.5, 16, 2.5), (13.5, 18, 13.5), "green_wool")
    m.seats, m.seat_y = [(8, 8)], 18
    out.append(m)
    return out


# =====================================================================================================================
# Tische
# =====================================================================================================================

def table(mid, top, frame, w=1, h=15, cover=None, cover_edge=None, braces=False, shelf=None, runner=None, thick=2):
    """Tisch: Platte top (w Blöcke breit), Zarge und Beine frame; cover = Decke oben (mit Saum cover_edge),
    braces = Kreuzstreben unten, shelf = Ablage unten, runner = Läufer quer über die Mitte (Tafeln)."""
    m = Model(mid)
    m.width = w
    x1 = 16 * w
    m.legs(2, 2, x1 - 2, 14, thick, h - 2, frame)
    m.box((2, h - 4, 2), (x1 - 2, h - 2, 14), frame, faces="nsewd")
    m.box((1, h - 2, 1), (x1 - 1, h, 15), top)
    if cover:
        m.box((0.5, h, 0.5), (x1 - 0.5, h + 0.5, 15.5), {"u": cover, "*": cover_edge or cover})
        m.box((0.5, h - 2.5, 0.5), (x1 - 0.5, h, 0.5), cover_edge or cover, faces="ns")
        m.box((0.5, h - 2.5, 15.5), (x1 - 0.5, h, 15.5), cover_edge or cover, faces="ns")
        m.box((0.5, h - 2.5, 0.5), (0.5, h, 15.5), cover_edge or cover, faces="ew")
        m.box((x1 - 0.5, h - 2.5, 0.5), (x1 - 0.5, h, 15.5), cover_edge or cover, faces="ew")
    if runner:
        cx = x1 / 2
        m.box((cx - 4, h, 0.5), (cx + 4, h + 0.5, 15.5), runner)
        m.box((cx - 4, h - 3, 0.5), (cx + 4, h, 0.5), runner, faces="ns")
        m.box((cx - 4, h - 3, 15.5), (cx + 4, h, 15.5), runner, faces="ns")
    if braces:
        c = (x1 / 2, 3.5, 8)
        m.box((x1 / 2 - 0.75, 3, 1), (x1 / 2 + 0.75, 4, 15), frame, rot=("y", 45, c))
        m.box((x1 / 2 - 0.75, 3, 1), (x1 / 2 + 0.75, 4, 15), frame, rot=("y", -45, c))
    if shelf:
        m.box((2.5, 3, 2.5), (x1 - 2.5, 4.5, 13.5), shelf)
    return m


def dresser(mid, body, front, top, w=2, h=13):
    """Kommode: Korpus, zwei Reihen Schubladen mit Griffen, Platte."""
    m = Model(mid)
    m.width = w
    x1 = 16 * w
    m.box((1, 0, 3), (x1 - 1, 1, 13), body)
    m.box((1, 1, 2), (x1 - 1, h, 14), body)
    m.box((0.5, h, 1.5), (x1 - 0.5, h + 1.5, 14.5), top)
    n = 2 * w
    dw = (x1 - 3) / n
    for row, (y0, y1) in enumerate(((1.75, h / 2 - 0.25), (h / 2 + 0.25, h - 0.75))):
        for i in range(n):
            x = 1.5 + i * dw
            m.box((x + 0.25, y0, 1.5), (x + dw - 0.25, y1, 2), front, faces="nsewu")
            cx = x + dw / 2
            m.box((cx - 1, (y0 + y1) / 2 - 0.5, 1), (cx + 1, (y0 + y1) / 2 + 0.5, 1.5), "gold_block")
    return m


def tische():
    white = cloth("cloth_white", "D9DEEA", ring="7F8FB8", border="B8C2DA", seed=2)
    green = cloth("cloth_green", "6A8C2E", ring="8DB040", border="58761F", seed=3)
    out = [
        table("holztisch", OAK, DARK_LOG),
        table("tisch_gruen", OAK, DARK_LOG, cover=green, braces=True),
        table("tisch_weiss", OAK, DARK_LOG, cover=white, braces=True),
        table("holztafel", OAK, OAK_LOG, w=2, thick=3),
        table("tafel_gruen", OAK, OAK_LOG, w=2, thick=3, runner=cloth("runner_green", "7AA23A", seed=4)),
        table("tafel_weiss", OAK, OAK_LOG, w=2, thick=3, runner=cloth("runner_white", "E4E8F0", seed=5)),
        table("esstisch", "jungle_planks", JUNGLE_LOG, w=2),
        table("bauerntisch", SPRUCE, SPRUCE_LOG, thick=3),
        table("beistelltisch", OAK, DARK_LOG, h=13, shelf=OAK),
        table("tischchen", "calcite", DARK_LOG, h=13, shelf=OAK),
        dresser("kommode", SPRUCE, OAK, OAK),
        dresser("anrichte", SPRUCE, OAK, "calcite"),
    ]
    # Bauerntisch: erhabener Rand auf der Platte
    m = out[7]
    for f, t in (((1, 15, 1), (15, 16, 2)), ((1, 15, 14), (15, 16, 15)), ((1, 15, 2), (2, 16, 14)), ((14, 15, 2), (15, 16, 14))):
        m.box(f, t, SPRUCE_LOG)
    # Schreibtisch: Platte, graue Seitenwangen, Rückwand
    m = Model("schreibtisch")
    m.width = 2
    m.box((1, 0, 2), (3, 14, 14), "smooth_stone").box((29, 0, 2), (31, 14, 14), "smooth_stone")
    m.box((3, 6, 12.5), (29, 14, 13.5), "smooth_stone")
    m.box((0.5, 14, 1.5), (31.5, 16, 14.5), OAK)
    out.append(m)
    # Blumenhocker: hoher Ständer mit Platte; 1 mit Fuß aus vier Füßchen, 2 mit vier Beinen und Ablage
    m = Model("blumenhocker_1")
    m.box((5, 0, 5), (11, 1, 11), DARK_LOG)
    for x, z in ((3, 7), (11, 7), (7, 3), (7, 11)):
        m.box((x, 0, z), (x + 2, 1.5, z + 2), DARK_LOG)
    m.box((6, 1, 6), (10, 16, 10), DARK_LOG)
    m.box((3, 16, 3), (13, 18, 13), "stripped_birch_log")
    out.append(m)
    m = Model("blumenhocker_2")
    m.legs(4, 4, 12, 12, 2, 16, DARK_LOG)
    m.box((4.5, 5, 4.5), (11.5, 6, 11.5), DARK_LOG)
    m.box((3, 16, 3), (13, 18, 13), "stripped_birch_log")
    out.append(m)
    return out


# =====================================================================================================================
# Pflanzen
# =====================================================================================================================

LEAF, LEAF_DARK, LEAF_PALE, LEAF_BRIGHT = (88, 150, 44), (48, 92, 52), (156, 190, 96), (104, 182, 58)


def pot(m, wide=False, y=0, soil="rooted_dirt"):
    """Topf aus Holz mit grauem Sockel und hellem Rand; gibt die Oberkante zurück."""
    a, b = (3, 13) if wide else (4.5, 11.5)
    m.box((a, y, a), (b, y + 2, b), "smooth_stone")
    m.box((a + 0.25, y + 2, a + 0.25), (b - 0.25, y + 6, b - 0.25), SPRUCE, faces="nsewd")
    m.box((a, y + 6, a), (b, y + 7, b), OAK_LOG, faces="nsewd")
    m.box((a + 0.75, y + 6.25, a + 0.75), (b - 0.75, y + 6.5, b - 0.75), soil, faces="u")
    for f, t in (((a, y + 6, a), (b, y + 7, a + 0.75)), ((a, y + 6, b - 0.75), (b, y + 7, b)),
                 ((a, y + 6, a), (a + 0.75, y + 7, b)), ((b - 0.75, y + 6, a), (b, y + 7, b))):
        m.box(f, t, OAK_LOG, faces="u")
    return y + 6.5


def leaves(m, f, t, tint=LEAF, texture="oak_leaves"):
    m.box(f, t, texture, tint=tint)


def potted(mid, wide=False):
    m = Model(mid)
    return m, pot(m, wide)


def pflanzen():
    out = []
    # Pflanzkasten: großer Holzkasten mit Erde, grauer Sockel
    m = Model("pflanzkasten")
    m.box((0.5, 0, 0.5), (15.5, 2.5, 15.5), "smooth_stone")
    m.box((1, 2.5, 1), (15, 11, 15), SPRUCE, faces="nsewd")
    m.box((1, 6.5, 0.75), (15, 7.5, 15.25), DARK_LOG, faces="nsew")
    for f, t in (((0.5, 11, 0.5), (15.5, 12.5, 2)), ((0.5, 11, 14), (15.5, 12.5, 15.5)),
                 ((0.5, 11, 2), (2, 12.5, 14)), ((14, 11, 2), (15.5, 12.5, 14))):
        m.box(f, t, OAK_LOG)
    m.box((2, 10.5, 2), (14, 11, 14), "farmland", faces="u")
    out.append(m)

    # Topfpflanzen 1-20
    m, y = potted("topfpflanze_1")
    leaves(m, (5, y, 5), (11, y + 12, 11), LEAF_BRIGHT, "azalea_leaves")
    m.cross(8, y + 4, 8, 9, 9, "fern", tint=LEAF)
    out.append(m)
    m, y = potted("topfpflanze_2")
    m.cross(8, y, 8, 9, 9, "rose_bush_bottom", tint=None)
    m.cross(8, y + 8, 8, 9, 9, "rose_bush_top")
    out.append(m)
    m, y = potted("topfpflanze_3", wide=True)
    leaves(m, (3, y, 3), (13, y + 9, 13), LEAF_BRIGHT, "azalea_leaves")
    m.cross(8, y + 5, 8, 14, 8, "large_fern_top", tint=LEAF)
    out.append(m)
    m, y = potted("topfpflanze_4", wide=True)
    leaves(m, (3.5, y, 3.5), (12.5, y + 5, 12.5), LEAF)
    for cx, cz in ((5.5, 5.5), (10.5, 6), (8, 10.5), (6, 9), (10.5, 10.5)):
        m.cross(cx, y + 3, cz, 6, 6, "dandelion")
    out.append(m)
    m, y = potted("topfpflanze_5")
    m.cross(7, y, 7, 10, 15, "red_tulip").cross(9.5, y, 9.5, 9, 12, "poppy")
    out.append(m)
    m, y = potted("topfpflanze_6", wide=True)
    for cx, cz, h in ((5.5, 6, 13), (10.5, 6.5, 12), (8, 10.5, 14), (6, 10, 11)):
        m.cross(cx, y, cz, 8, h, "allium")
    out.append(m)
    for mid, lo in (("topfpflanze_7", 0), ("topfpflanze_8", 1)):
        m, y = potted(mid)
        m.box((7.25, y, 7.25), (8.75, y + 5 + lo, 8.75), "oak_log")
        leaves(m, (2.5 + lo, y + 4 + lo, 2.5 + lo), (13.5 - lo, y + 12 + lo, 13.5 - lo), LEAF_BRIGHT)
        out.append(m)
    m, y = potted("topfpflanze_9")
    m.cross(8, y, 8, 8, 9, "fern", tint=LEAF)
    m.cross(6.5, y + 3, 7, 7, 11, "lily_of_the_valley").cross(10, y + 1, 9.5, 6, 9, "white_tulip")
    out.append(m)
    m, y = potted("topfpflanze_10")
    leaves(m, (4, y, 4), (12, y + 6, 12), LEAF_BRIGHT, "azalea_leaves")
    leaves(m, (4.5, y + 6, 4.5), (11.5, y + 11, 11.5), LEAF_BRIGHT, "azalea_leaves")
    leaves(m, (5.5, y + 11, 5.5), (10.5, y + 15, 10.5), LEAF_BRIGHT, "azalea_leaves")
    out.append(m)
    for mid, h in (("topfpflanze_11", 16), ("topfpflanze_12", 12)):
        m, y = potted(mid)
        leaves(m, (6, y, 6), (10, y + h, 10), LEAF_DARK, "spruce_leaves")
        m.cross(8, y, 8, 7, h - 2, "fern", tint=LEAF_DARK)
        out.append(m)
    m, y = potted("topfpflanze_13", wide=True)
    leaves(m, (2, y, 2), (14, y + 6, 14), LEAF_DARK, "spruce_leaves")
    leaves(m, (3, y + 6, 3), (13, y + 12, 13), LEAF_DARK, "spruce_leaves")
    leaves(m, (5, y + 12, 5), (11, y + 17, 11), LEAF_DARK, "spruce_leaves")
    out.append(m)
    m, y = potted("topfpflanze_14")
    m.cross(8, y, 8, 15, 15, "large_fern_top", tint=LEAF_BRIGHT)
    out.append(m)
    for mid, wide, two in (("topfpflanze_15", False, False), ("topfpflanze_16", True, False), ("topfpflanze_17", True, True)):
        m, y = potted(mid)
        m.box((7.25, y, 7.25), (8.75, y + 6, 8.75), "birch_log")
        if wide:
            m.box((3, y + 5, 7.25), (13, y + 6, 8.75), "birch_log")
            leaves(m, (-3, y + 6, 2), (19, y + 10, 14), LEAF_PALE)
        else:
            leaves(m, (2, y + 5, 2), (14, y + 12, 14), LEAF_PALE)
            leaves(m, (11, y + 3, 6), (16, y + 7, 11), LEAF_PALE)
        if two:
            m.box((14, y + 6, 7.25), (20, y + 7, 8.75), "birch_log")
            leaves(m, (17, y + 7, 4), (27, y + 12, 12), LEAF_PALE)
            leaves(m, (-2, y + 10, 4), (10, y + 13, 12), LEAF_PALE)
        out.append(m)
    m, y = potted("topfpflanze_18")
    m.cross(8, y, 8, 16, 15, "bamboo_large_leaves")
    out.append(m)
    m, y = potted("topfpflanze_19")
    leaves(m, (5.5, y, 5.5), (10.5, y + 16, 10.5), LEAF_BRIGHT)
    m.cross(8, y + 2, 8, 9, 8, "sweet_berry_bush_stage3").cross(8, y + 9, 8, 9, 8, "sweet_berry_bush_stage3")
    out.append(m)
    m, y = potted("topfpflanze_20", wide=True)
    leaves(m, (4, y, 4), (12, y + 8, 12), LEAF)
    m.cross(8, y + 2, 8, 12, 10, "fern", tint=LEAF)
    out.append(m)

    # Hängepflanzen: Topf an drei Seilen (oben y 16), Pflanze im Topf
    def hanging(mid, plant=None):
        m = Model(mid)
        y = pot(m, False, y=3)
        for x0, z0 in ((4.75, 4.75), (11.25, 4.75), (4.75, 11.25), (11.25, 11.25)):
            sx = 1 if x0 < 8 else -1
            m.box((x0 - 0.3, y, z0 - 0.3), (x0 + 0.3, 16, z0 + 0.3), DARK, rot=("z", 22.5 * -sx, (x0, y, z0)))
        m.box((7.6, 14.5, 7.6), (8.4, 16, 8.4), DARK)
        if plant:
            plant(m, y)
        return m

    out.append(hanging("haengepflanze_1"))
    out.append(hanging("haengepflanze_2", lambda m, y: m.cross(7, y, 7, 7, 9, "white_tulip").cross(9, y, 9, 7, 8, "fern", tint=LEAF)))
    out.append(hanging("haengepflanze_3", lambda m, y: m.cross(8, y, 8, 9, 9, "allium").cross(7, y, 9, 7, 7, "allium")))
    out.append(hanging("haengepflanze_4", lambda m, y: m.cross(8, y, 8, 9, 7, "poppy").cross(6.5, y, 9, 7, 6, "rose_bush_top")))
    out.append(hanging("haengepflanze_5", lambda m, y: m.cross(8, y, 8, 9, 7, "dandelion").cross(9.5, y, 7, 7, 6, "fern", tint=LEAF)))
    out.append(hanging("haengepflanze_6", lambda m, y: m.cross(8, y, 8, 9, 8, "fern", tint=LEAF)))
    out.append(hanging("haengepflanze_7", lambda m, y: m.cross(8, y, 8, 8, 10, "large_fern_top", tint=LEAF)))

    def spill(flower):
        def f(m, y):
            leaves(m, (5, y - 0.5, 5), (11, y + 3.5, 11), LEAF_BRIGHT, "azalea_leaves")
            for z0 in (4, 12):
                m.plane_z(4, y - 6, 12, y + 1, z0, "vine", tint=LEAF)
            for cx, cz in ((5, 4), (11, 5), (4, 11), (12, 11)):
                m.cross(cx, y - 2, cz, 4, 4, flower)
        return f

    out.append(hanging("haengepflanze_8", spill("cornflower")))
    out.append(hanging("haengepflanze_9", spill("poppy")))

    # Ranken: Blätter-Vorhänge an der Wand (hinten), 1 Block hoch
    def vine(mid, x0, x1, tint, texture="oak_leaves", stems=None):
        m = Model(mid)
        m.box((x0, 0, 13), (x1, 16, 16), texture, tint=tint)
        m.plane_z(x0 - 1, 0, x1 + 1, 16, 12.5, "vine", tint=tint)
        if stems:
            m.plane_z(x0, 0, x1, 16, 12.25, stems)
        return m

    out += [vine("ranke_1", 5, 11, LEAF_BRIGHT), vine("ranke_2", 2, 14, LEAF, "jungle_leaves"),
            vine("ranke_3", 5, 11, LEAF), vine("ranke_4", 5, 11, LEAF_DARK), vine("ranke_5", 2, 14, LEAF_DARK, "jungle_leaves"),
            vine("ranke_6", 6, 10, LEAF_DARK), vine("ranke_7", 4, 12, LEAF_PALE, stems="pale_hanging_moss"),
            vine("ranke_8", 2, 14, LEAF_PALE, "jungle_leaves", stems="pale_hanging_moss"),
            vine("ranke_9", 5, 11, LEAF_PALE, stems="pale_hanging_moss")]

    # Pflanzbrett: Brett an der Wand mit zwei Winkeln
    m = Model("pflanzbrett")
    m.box((-2, 8, 6), (18, 9.5, 15), OAK)
    m.box((1, 4, 14), (2.5, 8, 15.5), DARK_LOG).box((13.5, 4, 14), (15, 8, 15.5), DARK_LOG)
    out.append(m)
    # Kaktus im Tontopf
    m = Model("kaktus")
    m.box((4.5, 0, 4.5), (11.5, 6, 11.5), "terracotta").box((4, 6, 4), (12, 7, 12), "terracotta", faces="nsewd")
    m.box((5, 6.5, 5), (11, 6.75, 11), "coarse_dirt", faces="u")
    m.box((6.5, 6.5, 6.5), (9.5, 15, 9.5), "cactus_side")
    m.box((9.5, 9, 7), (12, 11, 9), "cactus_side").box((10, 11, 7.25), (12, 14, 8.75), "cactus_side")
    m.box((4, 10, 7), (6.5, 12, 9), "cactus_side").box((4, 12, 7.25), (5.5, 14, 8.75), "cactus_side")
    out.append(m)

    # Pilze
    def shroom(m, cx, cz, stem_h, stem_w, cap_w, cap_h, cap):
        r = stem_w / 2
        m.box((cx - r, 0, cz - r), (cx + r, stem_h, cz + r), "mushroom_stem")
        c = cap_w / 2
        # Hut gewölbt: unten breit, oben eingerückt
        m.box((cx - c, stem_h, cz - c), (cx + c, stem_h + cap_h - 1.5, cz + c), {"d": "mushroom_block_inside", "*": cap})
        m.box((cx - c + 1.5, stem_h + cap_h - 1.5, cz - c + 1.5), (cx + c - 1.5, stem_h + cap_h, cz + c - 1.5), cap)

    m = Model("fliegenpilz")
    shroom(m, 8, 8, 7, 4, 15, 5, "red_mushroom_block")
    out.append(m)
    m = Model("pilzchen_rot")
    shroom(m, 8, 8, 5, 3, 13, 4, "red_mushroom_block")
    out.append(m)
    m = Model("pilzgruppe_rot")
    shroom(m, 4.5, 8, 5, 2, 8, 4, "red_mushroom_block")
    shroom(m, 11.5, 9, 10, 2, 9, 4, "red_mushroom_block")
    out.append(m)
    m = Model("steinpilz")
    shroom(m, 8, 8, 6, 6, 15, 4, "brown_mushroom_block")
    out.append(m)
    m = Model("pilzchen_braun")
    shroom(m, 8, 8, 5, 3, 13, 4, "mushroom_block_inside")
    out.append(m)
    m = Model("pilzgruppe_braun")
    shroom(m, 4.5, 8, 5, 2, 8, 4, "mushroom_block_inside")
    shroom(m, 11.5, 9, 10, 2, 9, 4, "mushroom_block_inside")
    out.append(m)
    return out


# =====================================================================================================================
# Lager
# =====================================================================================================================

def produce(name, base, dark=None, light=None, seed=7):
    """Obst und Gemüse als Haufen: vier runde Früchte je 16 x 16 mit Glanzpunkt, dazwischen dunkle Lücken."""
    rng = np.random.default_rng(seed)
    b = _rgb(base)
    dk = _rgb(dark) if dark else b * 0.55
    lt = _rgb(light) if light else np.minimum(255, b * 1.3 + 20)
    img = np.zeros((16, 16, 3)) + dk
    yy, xx = np.mgrid[0:16, 0:16]
    for cx, cy in ((3.5, 3.5), (11.5, 3.5), (3.5, 11.5), (11.5, 11.5), (7.5, 7.5)):
        cx += rng.uniform(-0.6, 0.6)
        cy += rng.uniform(-0.6, 0.6)
        d = np.hypot(xx - cx, yy - cy)
        inside = d <= 3.6
        shade = 1.0 - 0.25 * (d / 3.6)
        img[inside] = (b * shade[..., None])[inside] + rng.integers(-8, 9, (16, 16))[inside][..., None]
        hl = np.hypot(xx - (cx - 1.2), yy - (cy - 1.2)) <= 0.9
        img[hl] = lt
    a = np.zeros((16, 16, 4), dtype=np.uint8)
    a[..., :3] = np.clip(img, 0, 255)
    a[..., 3] = 255
    return custom(name, Image.fromarray(a, "RGBA"))


def crate(mid, low=False):
    """Lattenkiste: Eckpfosten (bei hohen Kisten oben überstehend), drei bzw. zwei Latten je Seite, Boden."""
    m = Model(mid)
    h = 7 if low else 11
    post = h if low else h + 2.5
    a, b = (0.5, 15.5) if low else (1.5, 14.5)
    for x, z in ((a, a), (b - 1.5, a), (a, b - 1.5), (b - 1.5, b - 1.5)):
        m.box((x, 0, z), (x + 1.5, post, z + 1.5), DARK_LOG)
    m.box((a + 1, 0.5, a + 1), (b - 1, 1.5, b - 1), SPRUCE)
    slats = ((1, 3), (4.5, 6.5)) if low else ((1, 3.5), (4.5, 7), (8, 10.5))
    for y0, y1 in slats:
        m.box((a + 1.5, y0, a + 0.25), (b - 1.5, y1, a + 1.25), SPRUCE)
        m.box((a + 1.5, y0, b - 1.25), (b - 1.5, y1, b - 0.25), SPRUCE)
        m.box((a + 0.25, y0, a + 1.5), (a + 1.25, y1, b - 1.5), SPRUCE)
        m.box((b - 1.25, y0, a + 1.5), (b - 0.25, y1, b - 1.5), SPRUCE)
    m.top = h
    m.inner = (a + 1.25, b - 1.25)
    return m


def fill(m, texture, amount="full", greens=None):
    """Inhalt einer Kiste: full = bis über den Rand, few = kleines Häufchen, greens = Kraut obendrauf."""
    a, b = m.inner
    h = m.top
    if amount == "full":
        m.box((a, 1.5, a), (b, h + 1, b), texture)
        m.box((a + 2, h + 1, a + 2), (b - 2, h + 2.5, b - 2), texture)
        top = h + 2.5
    else:
        c = (a + b) / 2
        m.box((c - 2.5, 1.5, c - 2.5), (c + 2.5, h - 3, c + 2.5), texture)
        top = h - 3
    if greens:
        spots = ((5, 5), (10.5, 6), (6, 10.5), (10.5, 10.5)) if amount == "full" else ((7.5, 7.5),)
        for cx, cz in spots:
            m.cross(cx, top - 1, cz, 5, 5, greens, tint=LEAF if greens in ("short_grass", "fern") else None)
    return m


def lager():
    red = produce("food_red", "C8312C", seed=11)
    orange = produce("food_orange", "E8961E", seed=12)
    green = produce("food_green", "4F9A2C", seed=13)
    melon = "melon_side"
    white = produce("food_white", "E6E4D8", dark="9A9A88", seed=14)
    brown = produce("food_brown", "B8743E", seed=15)
    lime = produce("food_lime", "8CC43C", seed=16)
    purple = produce("food_purple", "7A3CC8", seed=17)
    carrot = produce("food_carrot", "EE8A14", seed=18)
    tall = [
        (None, None, None), (red, "full", None), (red, "few", None), (orange, "full", None), (orange, "few", None),
        (carrot, "full", "carrots_stage3"), (carrot, "few", "carrots_stage3"), (green, "full", None), (melon, "full", None),
        (white, "full", "short_grass"), (white, "few", "short_grass"), (brown, "full", None), (brown, "few", None),
        (lime, "full", None), (lime, "few", None), (purple, "full", None), (purple, "few", None),
    ]
    out = []
    for i, (t, amount, greens) in enumerate(tall, 1):
        m = crate("vorratskiste_%d" % i)
        if t:
            fill(m, t, amount, greens)
        out.append(m)
    low = [(None, None), (purple, None), (lime, None), (red, None), (orange, None), (green, None), (brown, None),
           (carrot, "carrots_stage3"), (white, "short_grass")]
    for i, (t, greens) in enumerate(low, 18):
        m = crate("vorratskiste_%d" % i, low=True)
        if t:
            fill(m, t, "full", greens)
        out.append(m)
    # Obstkorb: kleiner hoher Korb mit Früchten
    m = Model("obstkorb")
    for x, z in ((4, 4), (10.75, 4), (4, 10.75), (10.75, 10.75)):
        m.box((x, 0, z), (x + 1.25, 11, z + 1.25), DARK_LOG)
    for y0 in (1, 4, 7):
        m.box((5.25, y0, 4.25), (10.75, y0 + 2, 5), SPRUCE).box((5.25, y0, 11), (10.75, y0 + 2, 11.75), SPRUCE)
        m.box((4.25, y0, 5.25), (5, y0 + 2, 10.75), SPRUCE).box((11, y0, 5.25), (11.75, y0 + 2, 10.75), SPRUCE)
    m.box((5, 0.5, 5), (11, 1, 11), SPRUCE)
    m.box((5, 6, 5), (11, 10.5, 11), orange)
    m.box((5, 10.5, 5), (8.5, 12.5, 8.5), red).box((8, 10.5, 7.5), (11, 12.5, 11), lime)
    m.cross(9, 11, 6.5, 4, 4, "carrots_stage3")
    out.append(m)
    # Kleiderschrank: zwei Blöcke hoch, zwei Türen mit Griffen, Kranz
    m = Model("kleiderschrank")
    m.box((0.5, 0, 2), (15.5, 24, 15), SPRUCE, faces="sewud")
    m.box((0, 24, 1.5), (16, 26, 15.5), DARK)
    m.box((0, 0, 1.5), (16, 1.5, 15.5), DARK)
    m.box((1, 1.5, 1.5), (7.9, 23.5, 2), SPRUCE, faces="nsewu")
    m.box((8.1, 1.5, 1.5), (15, 23.5, 2), SPRUCE, faces="nsewu")
    for x0 in (2, 9.1):
        m.box((x0, 13, 1.25), (x0 + 4.9, 22.5, 1.5), DARK, faces="n")
        m.box((x0, 2.5, 1.25), (x0 + 4.9, 12, 1.5), DARK, faces="n")
    m.box((6.9, 10.5, 1), (7.6, 14.5, 1.5), "gold_block").box((8.4, 10.5, 1), (9.1, 14.5, 1.5), "gold_block")
    out.append(m)
    # Schließfach und offenes Fach: Aktenschrank aus Metall, drei Schubladen
    for mid, opened in (("schliessfach", False), ("offenes_fach", True)):
        m = Model(mid)
        m.box((2, 0, 2), (14, 18, 14), "iron_block", faces="sewud")
        for i, (y0, y1) in enumerate(((0.5, 6), (6.5, 12), (12.5, 17.5))):
            dz = -6 if opened and i < 2 else 0
            if dz:
                m.box((2.5, y0, 2 + dz), (13.5, y1, 8), "light_gray_concrete", faces="nsewd")
                m.box((3, y1 - 1, 2.5 + dz), (13, y1 - 0.5, 7.5), "lime_wool" if i == 0 else "white_concrete", faces="u")
            else:
                m.box((2.25, y0, 1.5), (13.75, y1, 2), "light_gray_concrete", faces="nsewu")
            m.box((6, (y0 + y1) / 2, 1 + dz), (10, (y0 + y1) / 2 + 1, 1.5 + dz), "gray_concrete")
        out.append(m)
    return out


# =====================================================================================================================
# Deko
# =====================================================================================================================

def _img(a):
    out = np.zeros((16, 16, 4), dtype=np.uint8)
    out[..., :3] = np.clip(a[..., :3], 0, 255)
    out[..., 3] = 255 if a.shape[2] == 3 else a[..., 3]
    return Image.fromarray(out, "RGBA")


def arrow_sign(name, left):
    """Holzbrett (hell) mit blauem Pfeil, Bretter waagerecht, dunkler Rand."""
    rng = np.random.default_rng(21)
    a = _rgb("B49A6C")[None, None, :] + rng.integers(-10, 11, (16, 16))[..., None]
    a[[3, 7, 11], :] *= 0.85
    a[[0, 15], :] = _rgb("5A3E22")
    a[:, [0, 15]] = _rgb("5A3E22")
    blue = _rgb("5E86C8")
    for x in range(3, 13):
        a[7:9, x] = blue
    for i in range(4):
        xs = 3 + i if left else 12 - i
        a[7 - i:9 + i, xs] = blue
    return custom(name, _img(a))


def curtain(name, base="C4CDD2"):
    """Vorhang: senkrechte Falten hell/dunkel."""
    b = _rgb(base)
    a = np.zeros((16, 16, 3))
    for x in range(16):
        k = (0.78, 0.9, 1.0, 1.05)[x % 4]
        a[:, x] = b * k
    a[15, :] *= 0.85
    return custom(name, _img(a))


def corkboard(name):
    rng = np.random.default_rng(22)
    a = _rgb("6E4A2C")[None, None, :] + rng.integers(-12, 13, (16, 16))[..., None]
    for x, y in ((2, 2), (8, 3), (12, 2), (4, 7), (10, 8), (2, 12), (7, 12), (12, 12)):
        a[y:y + 2, x:x + 3] = _rgb("D8D4C4")
        a[y, x + 1] = _rgb("C83C3C")
    return custom(name, _img(a))


def mirror(name):
    a = np.zeros((16, 16, 3)) + _rgb("8EC6E8")
    yy, xx = np.mgrid[0:16, 0:16]
    a[((xx + yy) % 8) < 2] = _rgb("C8E8F8")
    a[((xx + yy) % 8) == 5] = _rgb("6AA8D8")
    return custom(name, _img(a))


def shop_sign(name):
    """Ladenschild: blau mit hellem Rand und Schriftzug (Blockbuchstaben)."""
    a = np.zeros((16, 16, 3)) + _rgb("2E5E9A")
    a[[1, 14], 1:15] = _rgb("8EB8E8")
    a[1:15, [1, 14]] = _rgb("8EB8E8")
    for x in range(3, 13, 2):
        a[6:10, x] = _rgb("C8DCF4")
    a[6, 3:13] = _rgb("C8DCF4")
    return custom(name, _img(a))


def flag(m, x0, x1, y0, y1, z, texture, point=True):
    """Fahne (Stoff 1 px dick) mit Spitze unten."""
    m.box((x0, y0 + (2 if point else 0), z), (x1, y1, z + 1), texture)
    if point:
        c = (x0 + x1) / 2
        m.box((x0 + 1, y0 + 1, z), (x1 - 1, y0 + 2, z + 1), texture)
        m.box((c - 1, y0, z), (c + 1, y0 + 1, z), texture, faces="ns")


def deko():
    green = cloth("banner_green", "4C7A2C", var=8, seed=31, stripes=("436C26", 2))
    white = cloth("banner_white", "C6D0E2", var=6, seed=32, stripes=("AEB9D0", 2))
    out = []
    # Banner 1-4: Stange mit Querholz, Fahne davor
    for i, t in enumerate((green, white, green, white), 1):
        m = Model("banner_%d" % i)
        m.box((7, 0, 10), (9, 30, 12), DARK_LOG)
        m.box((2, 27, 9), (14, 28.5, 10), DARK_LOG)
        flag(m, 2.5, 13.5, 8, 27, 8.5, t)
        out.append(m)
    # Banner 5-6: zwei Wimpel seitlich an der Stange
    for i, t in ((5, green), (6, white)):
        m = Model("banner_%d" % i)
        m.box((10, 0, 7), (12, 30, 9), DARK_LOG)
        for y in (17, 23):
            m.box((-3, y, 7.5), (10, y + 4, 8.5), t)
            m.box((-4.5, y + 1, 7.5), (-3, y + 3, 8.5), t)
        out.append(m)
    # Banner 7-9: Stange mit Ausleger, lange Fahne hängt am Arm
    for i, t in ((7, white), (8, green), (9, white)):
        m = Model("banner_%d" % i)
        m.box((3, 0, 7), (5, 31, 9), DARK_LOG)
        m.box((5, 28, 7.25), (22, 29.5, 8.75), DARK_LOG)
        flag(m, 7, 19, 5, 28, 7.5, t)
        out.append(m)
    # Wegweiser: Brett mit Pfeil auf einem oder zwei Pfosten
    right, left = arrow_sign("sign_right", False), arrow_sign("sign_left", True)
    signs = ((1, right, 2, False), (2, right, 1, False), (3, left, 1, True), (4, left, 1, False), (5, left, 1, False),
             (6, right, 1, True))
    for i, t, posts, big in signs:
        m = Model("wegweiser_%d" % i)
        w = 20 if big else 16
        x0 = 8 - w / 2
        y0, y1 = (11, 24) if big else (12, 22)
        if posts == 2:
            m.box((x0 + 1, 0, 7.5), (x0 + 2.5, y0, 9), DARK_LOG).box((x0 + w - 2.5, 0, 7.5), (x0 + w - 1, y0, 9), DARK_LOG)
        else:
            m.box((7.25, 0, 8.5), (8.75, y0 + 2, 10), DARK_LOG)
        m.box((x0, y0, 7), (x0 + w, y1, 8.5), {"n": t, "s": t, "*": DARK_LOG}, uv={"n": [0, 0, 16, 16], "s": [16, 0, 0, 16]})
        out.append(m)
    # Vorhänge: Stange oben mit Ringen, Stoff in Falten (schmal oder breit)
    cur = curtain("curtain")
    for i, wide in ((1, False), (2, False), (3, True), (4, False), (5, False), (6, True)):
        m = Model("vorhang_%d" % i)
        x0, x1 = (0, 16) if wide else (4, 12)
        m.box((x0 - 1, 27, 13), (x1 + 1, 28.5, 14.5), DARK_LOG)
        for x in np.arange(x0 + 1, x1, 3):
            m.box((x, 26.5, 12.75), (x + 1, 28.75, 14.75), "light_blue_concrete")
        if wide:
            m.box((x0, 1, 13.25), (7.5, 27, 14.25), cur).box((8.5, 1, 13.25), (x1, 27, 14.25), cur)
        else:
            m.box((x0, 1 + (i % 2) * 2, 13.25), (x1, 27, 14.25), cur)
        out.append(m)
    # Pinnwand und Holzwand auf Füßen
    for mid, board in (("pinnwand", corkboard("corkboard")), ("holzwand", SPRUCE)):
        m = Model(mid)
        m.width = 2
        for x in (1, 29):
            m.box((x, 0, 4), (x + 2, 1.5, 12), DARK_LOG)
            m.box((x, 1.5, 7), (x + 2, 22, 9), DARK_LOG)
        m.box((3, 7, 7.25), (29, 22, 8.75), {"n": board, "s": board, "*": DARK_LOG}
              if board != SPRUCE else SPRUCE, uv={"n": [0, 0, 16, 16], "s": [0, 0, 16, 16]} if board != SPRUCE else None)
        m.box((2.5, 21.5, 7), (29.5, 23, 9), DARK_LOG)
        out.append(m)
    # Spiegel: hoher Standspiegel
    m = Model("spiegel")
    m.box((3, 0, 6), (13, 1.5, 10), DARK_LOG)
    m.box((4, 1.5, 7.5), (12, 28, 8.5), DARK_LOG)
    m.box((5, 2.5, 7.25), (11, 27, 7.5), mirror("mirror"), faces="n", uv={"n": [0, 0, 16, 16]})
    out.append(m)
    # Ladenschild: großes Schild an der Wand, zwei Blöcke breit
    m = Model("ladenschild")
    m.width = 2
    m.box((0.5, 4, 14), (31.5, 18, 15.5), DARK_LOG)
    m.box((1.5, 5, 13.75), (30.5, 17, 14), shop_sign("shop_sign"), faces="n", uv={"n": [0, 0, 16, 16]})
    out.append(m)
    # Kleiderpuppen: Fuß, Stange, Rumpf, Kopf; Arme je Puppe anders
    form = SPRUCE_LOG
    arms = {1: ("down", "down"), 2: ("down", "side"), 3: ("up", "up"), 4: ("down", "front")}
    for i in range(1, 5):
        m = Model("kleiderpuppe_%d" % i)
        m.box((3, 0, 7), (13, 1, 9), DARK_LOG).box((7, 0, 3), (9, 1, 13), DARK_LOG)
        m.box((7.25, 1, 7.25), (8.75, 8, 8.75), DARK_LOG)
        m.box((4.5, 8, 6), (11.5, 20, 10), form)
        m.box((5.5, 20, 6.5), (10.5, 25, 9.5), form)
        for side, kind in zip((-1, 1), arms[i]):
            x = 2.5 if side < 0 else 11.5
            if kind == "down":
                m.box((x, 11, 7), (x + 2, 19.5, 9), form)
            elif kind == "up":
                m.box((x, 19, 7), (x + 2, 27, 9), form, rot=("z", 22.5 * -side, (x + 1, 19, 8)))
            elif kind == "side":
                m.box((x, 15, 7), (x + 2, 19.5, 9), form).box((x + 2 * side, 17.5, 7), (x + 2 + 2 * side, 19.5, 9), form)
            else:
                m.box((x, 17.5, 1), (x + 2, 19.5, 9), form)
        out.append(m)
    m = Model("puppenkopf")
    m.box((3, 0, 7), (13, 1, 9), DARK_LOG).box((7, 0, 3), (9, 1, 13), DARK_LOG)
    m.box((7.25, 1, 7.25), (8.75, 4, 8.75), DARK_LOG)
    m.box((2.5, 4, 2.5), (13.5, 15, 13.5), form)
    out.append(m)
    # Kleiderstangen mit Hemden
    def shirt(m, x, z, color):
        """Hemd quer zur Stange (Breitseite nach vorne-hinten), Bügel oben."""
        m.box((x, 7, z - 2), (x + 1.5, 15, z + 2), color)
        m.box((x, 12, z - 3.5), (x + 1.5, 15, z - 2), color).box((x, 12, z + 2), (x + 1.5, 15, z + 3.5), color)
        m.box((x + 0.5, 15, z - 0.25), (x + 1, 17, z + 0.25), "iron_block")

    for mid, w, colors in (("kleiderstange_1", 2, ("lime_wool", "red_wool", "purple_wool", "magenta_wool")),
                           ("kleiderstange_2", 1, ("lime_wool", "red_wool", "purple_wool"))):
        m = Model(mid)
        m.width = w
        x1 = 16 * w
        for x in (1, x1 - 3):
            m.box((x, 0, 4), (x + 2, 1.5, 12), DARK_LOG).box((x, 1.5, 7), (x + 2, 18, 9), DARK_LOG)
        m.box((1, 17, 7.5), (x1 - 1, 18, 8.5), "iron_block")
        n = len(colors)
        for k, c in enumerate(colors):
            shirt(m, 4 + k * (x1 - 9.5) / max(1, n - 1), 8, c)
        out.append(m)
    # Stoffe
    m = Model("bunte_stoffe")
    m.box((1, 0, 3), (8, 1, 10), "lime_wool").box((3, 0, 1), (6, 1, 3), "lime_wool")
    m.box((8, 0, 6), (15, 1.5, 13), "magenta_wool").box((10, 1.5, 7), (14, 2, 11), "magenta_wool")
    out.append(m)
    m = Model("kissenstapel")
    m.box((1.5, 0, 1.5), (14.5, 2.5, 14.5), "magenta_wool")
    m.box((1, 2.5, 1), (15, 5, 15), "orange_wool").box((1.5, 5, 1.5), (14.5, 7.5, 14.5), "light_blue_wool")
    m.box((3, 7.5, 3), (13, 8, 13), "light_blue_wool")
    out.append(m)
    m = Model("stoffstapel_1")
    m.box((0, 0, 3), (7.5, 2.5, 13), "light_blue_wool").box((8.5, 0, 2), (16, 2.5, 12), "lime_wool")
    out.append(m)
    m = Model("stoffstapel_2")
    for x, z, h, c in ((1, 1, 10, "light_blue_wool"), (9, 6, 9, "orange_wool"), (2, 9, 4, "red_wool"), (7, 1, 5, "orange_wool")):
        m.box((x, 0, z), (x + 6, h, z + 6), {"u": "oak_planks", "d": "oak_planks", "*": c})
    out.append(m)
    # Felsen: Haufen aus Steinbrocken
    m = Model("felsen_1")
    m.box((2, 0, 3), (11, 7, 12), "mud").box((7, 0, 6), (14, 10, 13), "deepslate")
    m.box((4, 7, 5), (10, 13, 10), "mud").box((9, 10, 8), (12, 15, 11), "coarse_dirt")
    out.append(m)
    m = Model("felsen_2")
    m.box((1, 0, 2), (10, 8, 11), "tuff").box((8, 0, 6), (15, 9, 14), "calcite")
    m.box((3, 8, 4), (9, 13, 9), "sandstone").box((5, 13, 5), (8, 15, 8), "tuff")
    out.append(m)
    return out


# =====================================================================================================================
# Markt
# =====================================================================================================================

def stripes(name, a_col, b_col, width=2):
    a = np.zeros((16, 16, 3))
    for x in range(16):
        a[:, x] = _rgb(a_col if (x // width) % 2 == 0 else b_col)
    a[:, :] *= np.linspace(1.0, 0.9, 16)[:, None, None]
    return custom(name, _img(a))


def markt():
    out = []
    green = stripes("awning_green", "E8ECEC", "5E8A2A", 3)
    white = cloth("awning_white", "DCE4EE", var=5, seed=41)
    for i, (t, frame) in enumerate(((green, True), (white, True), (green, False), (white, False)), 1):
        m = Model("marktstand_%d" % i)
        m.width, m.depth = 2, 2
        for x, z in ((2, 2), (28.5, 2), (2, 28.5), (28.5, 28.5)):
            m.box((x, 0, z), (x + 1.5, 24, z + 1.5), DARK_LOG)
        # Dach: flach mit Wölbung, rundum herabhängender Rand
        m.box((0, 25, 0), (32, 27, 32), t)
        m.box((3, 27, 3), (29, 28.5, 29), t)
        m.box((0, 22, 0), (32, 25, 0), t, faces="ns").box((0, 22, 32), (32, 25, 32), t, faces="ns")
        m.box((0, 22, 0), (0, 25, 32), t, faces="ew").box((32, 22, 0), (32, 25, 32), t, faces="ew")
        if frame:
            m.box((2, 12, 2.25), (30, 13.5, 3.75), DARK_LOG)
            m.box((3.5, 6, 3), (28.5, 7.5, 12), SPRUCE)
        out.append(m)
    for i, body in ((1, SPRUCE), (2, DARK)):
        m = Model("handkarren_%d" % i)
        m.width = 2
        m.box((10, 5, 2), (30, 6, 14), body)
        m.box((10, 6, 2), (30, 11, 3), body).box((10, 6, 13), (30, 11, 14), body)
        m.box((29, 6, 3), (30, 11, 13), body).box((10, 6, 3), (11, 9, 13), body)
        for z in (0.5, 14.5):
            m.box((17, 0, z), (23, 6, z + 1), DARK_LOG)
            m.box((18.5, 1.5, z - 0.25), (21.5, 4.5, z + 1.25), SPRUCE_LOG)
        m.box((-6, 7, 3), (10, 8, 4.5), SPRUCE_LOG).box((-6, 7, 11.5), (10, 8, 13), SPRUCE_LOG)
        m.box((-6, 0, 3.25), (-5, 7, 4.25), SPRUCE_LOG).box((-6, 0, 11.75), (-5, 7, 12.75), SPRUCE_LOG)
        out.append(m)
    for i, t in ((1, cloth("drape_white", "D8E0EA", var=6, seed=42)), (2, cloth("drape_green", "5E8C2C", var=6, seed=43))):
        m = Model("stoffbahn_%d" % i)
        m.width = 2
        # Bogen aus fünf Stücken: senkrecht, schräg (45 Grad), waagerecht oben, schräg, senkrecht
        m.box((1, 0, 4), (5, 13, 12), t)
        m.box((4, 11.5, 4), (8, 21.5, 12), t, rot=("z", -45, (6, 16.5, 8)))
        m.box((9, 19.5, 4), (23, 23.5, 12), t)
        m.box((24, 11.5, 4), (28, 21.5, 12), t, rot=("z", 45, (26, 16.5, 8)))
        m.box((27, 0, 4), (31, 13, 12), t)
        out.append(m)
    return out


# =====================================================================================================================
# Bank
# =====================================================================================================================

def screen(name):
    a = np.zeros((16, 16, 3)) + _rgb("2C6CC0")
    a[0:2, :] = _rgb("1E4E90")
    for x, y in ((3, 5), (9, 4), (5, 10), (11, 9)):
        a[y:y + 2, x:x + 3] = _rgb("9CC8F0")
    return custom(name, _img(a))


def money(name):
    a = np.zeros((16, 16, 3)) + _rgb("7CBA2E")
    yy, xx = np.mgrid[0:16, 0:16]
    a[(((xx - 7.5) / 4.5) ** 2 + ((yy - 7.5) / 3) ** 2) <= 1] = _rgb("A8DC5C")
    a[[0, 15], :] = _rgb("4E7E1C")
    a[:, [0, 15]] = _rgb("4E7E1C")
    a[7:9, 7:9] = _rgb("4E7E1C")
    return custom(name, _img(a))


def money_side(name):
    a = np.zeros((16, 16, 3))
    for y in range(16):
        a[y, :] = _rgb("8CC83C" if y % 2 == 0 else "5E9420")
    return custom(name, _img(a))


def bank_sign(name):
    a = np.zeros((16, 16, 3)) + _rgb("E0B83C")
    a[[0, 15], :] = _rgb("A8801C")
    a[:, [0, 15]] = _rgb("A8801C")
    red = _rgb("A82C24")
    # B A N K in 3 x 5 Pixeln
    letters = {"B": ["110", "101", "110", "101", "110"], "A": ["010", "101", "111", "101", "101"],
               "N": ["101", "111", "111", "111", "101"], "K": ["101", "110", "100", "110", "101"]}
    x = 1
    for ch in "BANK":
        for j, row in enumerate(letters[ch]):
            for i, c in enumerate(row):
                if c == "1":
                    a[5 + j, x + i] = red
        x += 4
    return custom(name, _img(a))


def chips(name):
    a = np.zeros((16, 16, 3))
    cols = ["C83030", "3060C8", "E8E8E8", "30A040", "C83030", "E8E8E8", "3060C8", "202020"]
    for y in range(16):
        a[y, :] = _rgb(cols[(y // 2) % len(cols)])
    a[:, ::4] *= 0.8
    return custom(name, _img(a))


def lattice(name):
    a = np.zeros((16, 16, 3)) + _rgb("1E4A34")
    yy, xx = np.mgrid[0:16, 0:16]
    a[((xx + yy) % 4 == 0) | ((xx - yy) % 4 == 0)] = _rgb("C8A040")
    return custom(name, _img(a))


def bank():
    out = []
    scr = screen("screen")
    # Geldautomat
    m = Model("geldautomat")
    m.box((2, 0, 3), (14, 22, 14), "gray_concrete")
    m.box((2.5, 0, 2), (13.5, 12, 3), "light_gray_concrete")
    m.box((3, 12, 2.5), (13, 20, 4), "black_concrete")
    m.box((4, 14, 2.25), (12, 19, 2.5), scr, faces="n", uv={"n": [0, 0, 16, 16]})
    m.box((4, 10, 0.5), (12, 12, 3), "smooth_stone")
    for x, c in ((5, "red_concrete"), (7.5, "lime_concrete"), (10, "yellow_concrete")):
        m.box((x, 10.5, 0.25), (x + 1, 11.5, 0.5), c, faces="n")
    m.box((5, 6, 1.75), (11, 7, 2), "black_concrete", faces="n")
    m.box((1.5, 22, 2.5), (14.5, 23, 14.5), "gray_concrete")
    out.append(m)
    # Tresortür
    m = Model("tresortuer")
    m.box((0.5, 0, 6), (15.5, 18, 15), "iron_block")
    m.box((1.5, 1, 5), (14.5, 17, 6), "polished_andesite")
    m.box((6, 7, 4), (10, 11, 5), "gray_concrete")
    for f, t in (((7.5, 4, 4.25), (8.5, 14, 4.75)), ((3, 8.5, 4.25), (13, 9.5, 4.75))):
        m.box(f, t, "smooth_stone")
    m.box((13, 4, 4.5), (14, 14, 5), "gray_concrete")
    out.append(m)
    # Bankschild
    m = Model("bankschild")
    m.width = 2
    m.box((0.5, 4, 13), (31.5, 14, 15), "gold_block")
    m.box((1.5, 5, 12.75), (30.5, 13, 13), bank_sign("bank_sign"), faces="n", uv={"n": [0, 0, 16, 16]})
    out.append(m)
    # Waage
    m = Model("waage")
    m.box((4, 0, 4), (12, 1.5, 12), "gold_block")
    m.box((7, 1.5, 7), (9, 20, 9), SPRUCE_LOG)
    m.box((6.5, 20, 6.5), (9.5, 22, 9.5), "gold_block")
    m.box((0, 18, 7.5), (16, 19, 8.5), "black_concrete")
    for x in (0.5, 15.5):
        m.box((x - 0.25, 9, 7.75), (x + 0.25, 18, 8.25), "black_concrete")
        m.box((x - 2.5, 8, 5.5), (x + 2.5, 9, 10.5), "gold_block")
    out.append(m)
    # Computer: Bildschirm auf Fuß, Tastatur davor
    m = Model("computer")
    m.box((6, 0, 9), (10, 1, 13), "light_gray_concrete").box((7, 1, 10.5), (9, 5, 11.5), "light_gray_concrete")
    m.box((1, 5, 9.5), (15, 16, 12), "white_concrete")
    m.box((2, 6, 9.25), (14, 15, 9.5), scr, faces="n", uv={"n": [0, 0, 16, 16]})
    m.box((2, 0, 2), (13, 1, 7), "white_concrete")
    m.box((2.5, 1, 2.5), (12.5, 1.25, 6.5), "light_gray_concrete", faces="u")
    out.append(m)
    # Kamera an Wandhalter
    m = Model("kamera")
    m.box((1, 2, 14), (6, 16, 16), "iron_block")
    m.box((3, 9, 9), (5, 11, 14), "gray_concrete")
    m.box((4, 6, 1), (15, 13, 11), "light_gray_concrete")
    m.box((6, 7.5, 0), (12, 11.5, 1), "black_concrete")
    m.box((7.5, 8.5, -0.5), (10.5, 10.5, 0), "blue_stained_glass", faces="n")
    out.append(m)
    # Schranke: Pfosten mit rot-weißem Arm
    m = Model("schranke")
    m.width = 2
    m.box((1, 0, 6), (7, 1.5, 12), "gray_concrete")
    m.box((2.5, 1.5, 7.5), (5.5, 18, 10.5), "light_gray_concrete")
    m.box((2, 18, 7), (6, 20, 11), "white_concrete")
    m.box((6, 17, 8.25), (31, 18.5, 9.75), "red_concrete")
    out.append(m)
    # Vitrine: zwei dunkle Säulen mit Glashaube
    m = Model("vitrine")
    m.width = 2
    for x in (2, 20):
        m.box((x, 0, 3), (x + 10, 2, 13), "black_concrete")
        m.box((x + 3, 2, 6), (x + 7, 8, 10), "polished_deepslate")
        m.box((x + 0.5, 8, 3.5), (x + 9.5, 18, 12.5), "black_stained_glass")
    out.append(m)
    # Aktenkoffer
    m = Model("aktenkoffer")
    m.box((1, 0, 4), (15, 4, 12), "gray_concrete")
    m.box((5, 4, 7.5), (11, 5.5, 8.5), "black_concrete")
    m.box((2, 1.5, 3.75), (14, 2, 4), "black_concrete", faces="n")
    out.append(m)
    # Papierstapel
    m = Model("papierstapel")
    m.box((1, 0, 1), (15, 6, 15), {"u": "white_concrete", "d": "white_concrete", "*": "light_gray_concrete"})
    m.box((1.5, 6, 1.5), (14.5, 11, 14.5), {"u": "white_concrete", "*": "light_gray_concrete"})
    out.append(m)
    bill, side = money("money"), money_side("money_side")
    cash = {"u": bill, "d": bill, "*": side}
    # Geldbündel, Geldstapel, Geldkoffer
    m = Model("geldbuendel")
    for f, t in (((1, 0, 3), (8, 2, 8)), ((2, 2, 3.5), (8.5, 4, 8)), ((9, 0, 8), (15, 2, 13)), ((5, 0, 10), (9, 1, 14))):
        m.box(f, t, cash)
    out.append(m)
    m = Model("geldstapel")
    for (x, z), h in (((1, 1), 20), ((9, 3), 12), ((5, 9), 8), ((10, 10), 10)):
        m.box((x, 0, z), (x + 6, h, z + 4), cash)
    out.append(m)
    m = Model("geldkoffer")
    m.box((1, 0, 2), (15, 4, 13), "gray_concrete")
    m.box((1, 4, 12.5), (15, 14, 14), "gray_concrete", rot=("x", -22.5, (8, 4, 13)))
    m.box((2.5, 4, 3.5), (13.5, 5.5, 11.5), cash)
    m.box((4, 5.5, 4), (9, 7, 8), cash)
    out.append(m)
    # Chip-Vitrine: Holzkasten mit Glas, Chips darin
    m = Model("chip_vitrine")
    m.box((0.5, 0, 2), (15.5, 7, 14), DARK)
    m.box((1.5, 7, 3), (14.5, 9, 13), chips("chips"))
    m.box((0.5, 7, 2), (15.5, 11, 14), "glass")
    m.box((0, 11, 1.5), (16, 12, 14.5), DARK, faces="nsewd")
    out.append(m)
    m = Model("chipstapel")
    for (x, z), h, c in (((1, 4), 3, "black_concrete"), ((4, 5), 6, "orange_concrete"), ((9, 4), 5, "white_concrete"),
                         ((8, 9), 4, "brown_concrete"), ((3, 10), 3, "white_concrete")):
        m.box((x, 0, z), (x + 4, h, z + 4), {"u": c, "*": "light_gray_concrete" if c != "white_concrete" else "smooth_stone"})
    out.append(m)
    # Stehlampe mit zwei grünen Schirmen (leuchtet)
    m = Model("stehlampe")
    m.box((4, 0, 4), (12, 1, 12), "orange_concrete")
    m.box((7.5, 1, 7.5), (8.5, 22, 8.5), "black_concrete")
    m.box((3, 21, 7.5), (13, 22, 8.5), "black_concrete")
    for x in (1, 10):
        m.box((x, 22, 5), (x + 5, 28, 11), "green_stained_glass")
        m.box((x + 1.5, 23, 6.5), (x + 3.5, 26, 9.5), "glowstone", shade=False)
    m.light = 13
    out.append(m)
    # Anzeigetafel: Paravent aus drei Feldern (goldener Rahmen, grünes Gitter)
    lat = lattice("lattice")
    m = Model("anzeigetafel")
    m.width = 2
    for x0, rot in ((0, 22.5), (11, 0), (22, -22.5)):
        r = ("y", rot, (x0 + 5, 0, 8)) if rot else None
        m.box((x0, 0, 7.5), (x0 + 10, 22, 8.5), {"n": lat, "s": lat, "*": "gold_block"}, rot=r,
              uv={"n": [0, 0, 16, 16], "s": [0, 0, 16, 16]})
        m.box((x0, 22, 7.25), (x0 + 10, 23, 8.75), "gold_block", rot=r)
    out.append(m)
    # Servierwagen
    m = Model("servierwagen")
    m.legs(2, 3, 14, 13, 1, 13, "gold_block", y0=1.5)
    for y in (4, 11):
        m.box((2, y, 3), (14, y + 1.5, 13), "green_concrete")
    m.box((2, 14.5, 3), (14, 15.5, 4), "gold_block").box((13, 13, 3), (14, 18, 4), "gold_block")
    m.box((5, 12.5, 6), (10, 15, 10), DARK)
    for x, z in ((2, 3), (13, 3), (2, 12), (13, 12)):
        m.box((x, 0, z), (x + 1, 1.5, z + 1), "black_concrete")
    out.append(m)
    return out


def build():
    return sitzen() + tische() + pflanzen() + lager() + deko() + markt() + bank()
