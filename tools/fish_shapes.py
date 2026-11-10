"""Handgezeichnete Körperformen der Fisch-Icons. Kopf links, Schwanz rechts, höchstens 14 x 14 (Kontur kommt außen
dazu, from_grid setzt die Form mittig auf 16 x 16).

Jede Form ist pixelgenau angegeben: Körper als Ober- und Unterkante je Spalte, Schwanz als Zeilen je Spalte, Flossen,
Auge, Maul, Kiemendeckel, Brustflosse und Extras als einzelne Pixel. make() baut daraus das Zeichenraster für
fishart.from_grid (Zeichen siehe dort).
"""


def make(body, tail=None, fins=(), dark_fins=(), eye=None, eye_size=2, iris=False, mouth=(), gill=None, pect=(),
         extra=(), loose=(), acc=None, light_px=(), ink_px=(), belly=(), back=(), gills=(), eyes=()):
    """body: {x: (oben, unten)}; tail: {x: [Zeilen]}; eye: (x, y) obere linke Ecke; gill: (x, y0, y1);
    acc: {"1": [(x, y)], ...} Akzentfarben; loose: Extras ohne Kontur (Bartfäden)."""
    g = [["."] * 14 for _ in range(14)]

    def put(x, y, ch):
        if 0 <= x < 14 and 0 <= y < 14:
            g[y][x] = ch

    for x, (y0, y1) in body.items():
        for y in range(y0, y1 + 1):
            put(x, y, "b")
    for x, ys in (tail or {}).items():
        for y in ys:
            put(x, y, "t")
    for x, y in fins:
        put(x, y, "f")
    for x, y in dark_fins:
        put(x, y, "F")
    for x, y in back:
        put(x, y, "B")
    for x, y in belly:
        put(x, y, "L")
    if gill:
        gx, gy0, gy1 = gill
        for y in range(gy0, gy1 + 1):
            put(gx, y, "g")
    for x, y in pect:
        put(x, y, "p")
    for x, y in gills:
        put(x, y, "g")
    for x, y in light_px:
        put(x, y, "h")
    for x, y in ink_px:
        put(x, y, "k")
    for x, y in mouth:
        put(x, y, "m")
    if eye:
        ex, ey = eye
        if eye_size >= 2:
            put(ex, ey, "w")
            put(ex + 1, ey, "e")
            put(ex, ey + 1, "i" if iris else "e")
            put(ex + 1, ey + 1, "e")
        else:
            put(ex, ey, "e")
    for x, y in eyes:
        put(x, y, "e")
    for x, y in extra:
        put(x, y, "x")
    for x, y in loose:
        put(x, y, "X")
    for k, pts in (acc or {}).items():
        for x, y in pts:
            put(x, y, k)
    # leere Zeilen und Spalten am Rand weg (from_grid zentriert)
    return ["".join(r) for r in g]


def span(pairs):
    """{x: (oben, unten)} aus einer Liste [(oben, unten), ...] ab x = 0."""
    return {x: p for x, p in enumerate(pairs)}


SHAPES = {}

# Rotauge, Rotfeder, Hering, Sardine, Lodde: spindelförmig, Gabelschwanz
SHAPES["classic"] = make(
    span([(5, 6), (4, 7), (3, 8), (3, 8), (2, 9), (2, 9), (2, 9), (3, 8), (3, 8), (4, 7), (5, 6)]),
    tail={11: [4, 5, 6, 7], 12: [3, 4, 7, 8], 13: [2, 3, 8, 9]},
    fins=[(5, 0), (4, 1), (5, 1), (6, 1), (7, 2), (9, 8), (8, 9), (5, 10)],
    eye=(2, 4), mouth=[(0, 6)], gill=(4, 5, 7), pect=[(5, 7), (6, 7)])

# Barsche: Buckel, stachelige erste und weiche zweite Rückenflosse
SHAPES["perch"] = make(
    span([(6, 7), (5, 8), (4, 9), (3, 9), (2, 9), (2, 9), (2, 9), (2, 8), (3, 8), (3, 7), (4, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [3, 4, 7, 8]},
    dark_fins=[(3, 2), (4, 1), (4, 0), (5, 1), (6, 1), (6, 0)],
    fins=[(8, 2), (9, 2), (9, 1), (10, 3), (9, 8), (10, 8), (4, 10), (5, 10)],
    eye=(2, 5), iris=True, mouth=[(0, 7)], gill=(4, 5, 8), pect=[(5, 7), (6, 7), (6, 8)])

# Forelle, Lachs, Saibling, Huchen: gestreckt, Fettflosse, fast gerader Schwanz
SHAPES["salmon"] = make(
    span([(5, 6), (4, 7), (4, 8), (3, 8), (3, 8), (3, 8), (3, 8), (3, 8), (4, 8), (4, 7), (5, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [3, 4, 5, 6, 7, 8]},
    fins=[(5, 1), (6, 1), (5, 2), (6, 2), (7, 2), (10, 4), (9, 8), (10, 8), (6, 9)],
    eye=(2, 5), mouth=[(0, 6)], gill=(4, 5, 7), pect=[(5, 7), (6, 7)])

# Äsche: wie Forelle, mit großer Fahnen-Rückenflosse
SHAPES["grayling"] = make(
    span([(5, 6), (4, 7), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (5, 7), (5, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [3, 4, 7, 8]},
    fins=[(4, 3), (5, 3), (5, 2), (6, 3), (6, 2), (6, 1), (7, 3), (7, 2), (7, 1), (8, 3), (8, 2), (9, 3),
          (10, 4), (9, 9), (6, 9)],
    eye=(2, 5), mouth=[(0, 6)], gill=(4, 5, 7), pect=[(5, 7), (6, 7)])

# Karpfen, Brasse, Schleie, Dorade, Piranha: hochrückig, lange Rückenflosse
SHAPES["deep"] = make(
    span([(5, 7), (4, 8), (3, 9), (2, 10), (2, 10), (1, 10), (1, 10), (2, 10), (2, 9), (3, 9), (4, 8), (5, 7)]),
    tail={12: [4, 5, 6, 7, 8], 13: [3, 4, 8, 9]},
    fins=[(5, 0), (6, 0), (7, 1), (8, 1), (9, 2), (9, 10), (10, 9), (5, 11), (6, 11)],
    eye=(2, 5), iris=True, mouth=[(0, 7)], gill=(4, 5, 8), pect=[(5, 8), (6, 8), (6, 9)])

# Hecht: lang, Entenschnabel, Rücken- und Afterflosse weit hinten
SHAPES["pike"] = make(
    span([(5, 6), (5, 6), (4, 7), (4, 7), (4, 7), (3, 7), (3, 8), (3, 8), (3, 8), (3, 8), (4, 7), (4, 7)]),
    tail={12: [3, 4, 5, 6, 7, 8], 13: [2, 3, 8, 9]},
    fins=[(8, 2), (9, 2), (9, 1), (10, 3), (8, 9), (9, 9), (9, 10), (10, 8), (6, 9)],
    eye=(3, 4), iris=True, mouth=[(1, 6), (2, 6)], gill=(5, 4, 6), pect=[(6, 7), (7, 7)])

# Barrakuda: noch schlanker, Unterkiefer vorne, zwei kleine Rückenflossen, Gabelschwanz
SHAPES["barracuda"] = make(
    span([(6, 6), (5, 7), (5, 7), (4, 7), (4, 7), (4, 8), (4, 8), (4, 8), (4, 8), (4, 7), (5, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [2, 3, 4, 7, 8, 9]},
    fins=[(5, 3), (6, 3), (6, 2), (9, 3), (8, 9), (6, 9)],
    eye=(3, 5), mouth=[(0, 6), (1, 6)], gill=(5, 5, 7), pect=[(6, 7)])

# Wels: breiter flacher Kopf, Bartfäden, lange Afterflosse, runder Schwanz
SHAPES["catfish"] = make(
    span([(5, 7), (4, 8), (3, 8), (3, 8), (3, 8), (3, 8), (3, 8), (4, 8), (4, 8), (4, 8), (5, 7), (5, 7)]),
    tail={12: [4, 5, 6, 7, 8], 13: [5, 6, 7]},
    fins=[(4, 2), (5, 2), (4, 1), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (6, 10), (7, 10), (8, 10), (10, 8),
          (11, 8)],
    eye=(2, 4), eye_size=1, mouth=[(0, 6), (1, 6)], pect=[(3, 7), (4, 7), (4, 8)],
    loose=[(0, 8), (0, 9), (0, 10), (2, 9), (2, 10), (3, 11)])

# Thunfisch, Makrele: Torpedo, Sichelschwanz, Flössel
SHAPES["tuna"] = make(
    span([(5, 6), (4, 7), (3, 8), (3, 8), (2, 9), (2, 9), (2, 9), (2, 9), (3, 8), (3, 8), (4, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [1, 2, 3, 8, 9, 10]},
    fins=[(4, 1), (5, 1), (5, 0), (6, 1), (8, 2), (8, 1), (8, 9), (8, 10), (10, 3), (10, 8)],
    eye=(2, 4), mouth=[(0, 6)], gill=(4, 4, 7), pect=[(5, 5), (6, 6), (7, 6)])

# Haie: spitze Schnauze, hohe Rückenflosse, Kiemenspalten, große Brustflosse, ungleicher Schwanz
SHAPES["shark"] = make(
    span([(5, 6), (4, 7), (4, 7), (3, 8), (3, 8), (3, 8), (3, 8), (3, 8), (4, 7), (4, 7), (5, 6)]),
    tail={11: [4, 5, 6, 7], 12: [2, 3, 4, 7, 8], 13: [0, 1, 2, 8]},
    fins=[(5, 0), (4, 1), (5, 1), (3, 2), (4, 2), (5, 2), (6, 2), (9, 3), (4, 9), (5, 9), (5, 10), (6, 10),
          (6, 11), (9, 8)],
    eye=(2, 5), eye_size=1, mouth=[(1, 7), (2, 7)], gills=[(5, 5), (5, 6), (6, 5), (6, 6)])

# Kabeljau, Polardorsch: drei Rücken-, zwei Afterflossen, Bartel am Kinn, gerader Schwanz
SHAPES["cod"] = make(
    span([(5, 7), (4, 8), (3, 8), (3, 9), (2, 9), (2, 9), (2, 9), (3, 9), (3, 8), (4, 8), (4, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [3, 4, 5, 6, 7, 8]},
    fins=[(3, 2), (4, 1), (6, 1), (7, 2), (7, 1), (9, 3), (10, 3), (6, 10), (7, 10), (9, 9), (10, 8)],
    eye=(2, 4), mouth=[(0, 7)], gill=(4, 4, 7), pect=[(5, 6), (6, 6), (6, 7)], loose=[(1, 9), (1, 10)])

# Hering, Sardine, Lodde, Moderlieschen, Eisfisch: schlank, tiefer Gabelschwanz
SHAPES["slim"] = make(
    span([(5, 6), (5, 7), (4, 7), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 7), (5, 7), (5, 6), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [2, 3, 8, 9]},
    fins=[(5, 3), (6, 3), (6, 2), (7, 3), (9, 8), (10, 8), (6, 9)],
    eye=(1, 5), mouth=[(0, 6)], gill=(3, 5, 7), pect=[(4, 7), (5, 7)])

# Barbe: schlank, Maul unten, vier Bartfäden
SHAPES["barbel"] = make(
    span([(6, 7), (5, 8), (4, 8), (4, 8), (3, 8), (3, 8), (3, 8), (4, 8), (4, 8), (4, 7), (5, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [2, 3, 4, 7, 8, 9]},
    fins=[(5, 2), (5, 1), (6, 2), (9, 9), (10, 8), (6, 9)],
    eye=(2, 5), mouth=[(1, 8)], gill=(4, 5, 7), pect=[(5, 7), (6, 7)],
    loose=[(0, 8), (0, 9), (2, 9), (2, 10)])

# Stör: Schaufelschnauze, Knochenplatten, Bartfäden, ungleicher Schwanz
SHAPES["sturgeon"] = make(
    span([(6, 6), (6, 7), (5, 7), (5, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (5, 8), (5, 7), (6, 7)]),
    tail={12: [3, 4, 5, 6, 7], 13: [1, 2, 3, 7]},
    fins=[(9, 4), (10, 4), (5, 9), (6, 9), (8, 9)],
    eye=(4, 5), eye_size=1, mouth=[(2, 7)], loose=[(1, 8), (1, 9)],
    acc={"1": [(4, 4), (6, 4), (8, 4), (4, 6), (6, 6), (8, 6), (10, 6)]})

# Schwertfisch: langes Schwert, Sichelflosse, Sichelschwanz
SHAPES["sword"] = make(
    {0: (6, 6), 1: (6, 6), 2: (6, 6), 3: (6, 6), 4: (5, 7), 5: (4, 8), 6: (4, 8), 7: (4, 8), 8: (4, 8), 9: (4, 7),
     10: (5, 7), 11: (5, 6)},
    tail={12: [4, 5, 6, 7], 13: [1, 2, 3, 8, 9, 10]},
    fins=[(5, 3), (6, 3), (5, 2), (6, 2), (6, 1), (6, 0), (10, 4), (9, 8), (5, 9), (6, 9), (7, 10)],
    eye=(5, 5), mouth=[(4, 7)], back=[(0, 6), (1, 6), (2, 6), (3, 6)])

# Marlin: Schwert, hohe lange Rückenflosse (Segel)
SHAPES["marlin"] = make(
    {0: (6, 6), 1: (6, 6), 2: (6, 6), 3: (5, 7), 4: (5, 7), 5: (4, 8), 6: (4, 8), 7: (4, 8), 8: (4, 8), 9: (4, 7),
     10: (5, 7), 11: (5, 6)},
    tail={12: [4, 5, 6, 7], 13: [1, 2, 3, 8, 9, 10]},
    fins=[(5, 3), (5, 2), (5, 1), (5, 0), (6, 3), (6, 2), (6, 1), (7, 3), (7, 2), (8, 3), (9, 3), (9, 8),
          (5, 9), (6, 9), (7, 10)],
    eye=(4, 5), eye_size=1, mouth=[(3, 7)], back=[(0, 6), (1, 6), (2, 6)])

# Mondfisch: hohe Scheibe, Ruderschwanz, hohe Rücken- und Afterflosse hinten
SHAPES["sunfish"] = make(
    span([(6, 7), (4, 9), (3, 10), (3, 10), (3, 10), (3, 10), (3, 10), (4, 9), (4, 9)]),
    tail={9: [4, 5, 6, 7, 8, 9], 10: [4, 5, 7, 8]},
    fins=[(6, 2), (7, 3), (7, 2), (7, 1), (8, 1), (8, 0), (6, 11), (7, 10), (7, 11), (7, 12), (8, 12), (8, 13)],
    eye=(2, 5), mouth=[(0, 7)], pect=[(4, 6), (4, 7)])

# Kugelfisch: rund, kleiner Schwanz, Stacheln als Höcker
SHAPES["puffer"] = make(
    span([(6, 7), (5, 8), (3, 10), (2, 11), (2, 11), (2, 11), (2, 11), (2, 11), (3, 10), (4, 9), (5, 8)]),
    tail={11: [5, 6, 7, 8], 12: [4, 5, 6, 7, 8, 9]},
    fins=[(8, 2), (9, 3), (8, 11), (9, 10)],
    extra=[(4, 1), (7, 1), (4, 12), (7, 12), (1, 4)],
    eye=(2, 5), mouth=[(0, 7)], pect=[(6, 6), (6, 7)])

# Seehase: rund, Saugscheibe, kurzer Schwanz, Höcker als Muster
SHAPES["lump"] = make(
    span([(6, 7), (5, 8), (3, 10), (2, 11), (2, 11), (2, 11), (2, 11), (2, 11), (3, 10), (4, 9), (5, 8)]),
    tail={11: [5, 6, 7, 8], 12: [5, 6, 7, 8]},
    fins=[(5, 1), (6, 1), (7, 1), (8, 2), (8, 11), (9, 10)],
    eye=(2, 5), mouth=[(0, 7)], pect=[(5, 8), (6, 8), (6, 9)])

# Feuerfisch: Stachelkamm, große gestreifte Brustflossen, runder Schwanz
SHAPES["lion"] = make(
    span([(5, 7), (4, 8), (3, 8), (3, 9), (3, 9), (3, 9), (3, 9), (3, 8), (4, 8), (4, 7), (5, 7)]),
    tail={11: [4, 5, 6, 7, 8], 12: [4, 5, 6, 7, 8], 13: [5, 6, 7]},
    fins=[(3, 2), (3, 1), (3, 0), (5, 2), (5, 1), (5, 0), (7, 2), (7, 1), (7, 0), (9, 3), (9, 2),
          (3, 10), (4, 10), (4, 11), (5, 11), (5, 12), (6, 12), (8, 9), (9, 9)],
    eye=(1, 5), iris=True, mouth=[(0, 6)], pect=[(4, 7), (5, 7), (5, 8)])

# Mahi-Mahi: steile Stirn, Rückenflosse vom Kopf bis zum Schwanz, Gabelschwanz
SHAPES["mahi"] = make(
    span([(4, 7), (3, 8), (3, 8), (3, 8), (3, 8), (3, 8), (4, 8), (4, 8), (4, 7), (5, 7), (5, 6)]),
    tail={11: [4, 5, 6, 7], 12: [2, 3, 4, 7, 8, 9], 13: [1, 2, 9, 10]},
    fins=[(1, 2), (2, 2), (2, 1), (3, 2), (3, 1), (4, 2), (5, 2), (6, 3), (7, 3), (8, 3), (9, 4),
          (5, 9), (6, 9), (7, 9), (8, 8), (9, 8)],
    eye=(1, 4), mouth=[(0, 7)], pect=[(3, 6), (4, 6)])

# Hohe Scheibe mit Saumflossen: Kaiserfisch, Doktorfisch
SHAPES["disc"] = make(
    span([(6, 7), (4, 9), (3, 10), (2, 11), (2, 11), (2, 11), (2, 11), (3, 10), (4, 9), (5, 8)]),
    tail={10: [5, 6, 7, 8], 11: [4, 5, 6, 7, 8, 9]},
    fins=[(2, 1), (3, 1), (4, 1), (5, 1), (6, 1), (7, 2), (8, 3), (9, 4),
          (3, 12), (4, 12), (5, 12), (6, 12), (7, 11), (8, 10), (9, 9)],
    eye=(2, 5), mouth=[(0, 7)], pect=[(4, 7), (5, 7)])

# Clownfisch: oval, runde Flossen
SHAPES["clown"] = make(
    span([(5, 7), (4, 8), (3, 9), (3, 9), (3, 9), (3, 9), (3, 9), (3, 9), (4, 8), (4, 8), (5, 7)]),
    tail={11: [4, 5, 6, 7, 8], 12: [4, 5, 6, 7, 8], 13: [5, 6, 7]},
    fins=[(3, 2), (4, 1), (5, 1), (6, 2), (7, 2), (8, 2), (9, 3), (8, 9), (9, 9), (4, 10), (5, 10)],
    eye=(2, 4), mouth=[(0, 6)], pect=[(4, 6), (5, 6)])

# Riffbarsch: wie Clownfisch, Gabelschwanz
SHAPES["damsel"] = make(
    span([(5, 7), (4, 8), (3, 9), (3, 9), (3, 9), (3, 9), (3, 9), (3, 9), (4, 8), (4, 8), (5, 7)]),
    tail={11: [5, 6, 7], 12: [3, 4, 5, 7, 8, 9], 13: [2, 3, 9, 10]},
    fins=[(3, 2), (4, 1), (5, 1), (6, 2), (7, 2), (8, 2), (9, 3), (8, 9), (9, 9), (4, 10), (5, 10)],
    eye=(2, 4), mouth=[(0, 6)], pect=[(4, 6), (5, 6)])

# Plattfisch (Heilbutt) von oben: Saumflossen rundum, beide Augen auf einer Seite
SHAPES["flat"] = make(
    {1: (5, 7), 2: (4, 8), 3: (3, 9), 4: (3, 9), 5: (3, 9), 6: (3, 9), 7: (3, 9), 8: (3, 9), 9: (4, 8), 10: (5, 7)},
    tail={11: [5, 6, 7], 12: [4, 5, 6, 7, 8]},
    fins=[(2, 3), (3, 2), (4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 3), (10, 4),
          (2, 9), (3, 10), (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 9), (10, 8)],
    eyes=[(2, 5), (3, 4)], mouth=[(1, 7)])

# Langgestreckt mit Saumflosse: Steinbeißer, Schlammpeitzger, Lungenfisch, Schlangenkopf
SHAPES["eelfish"] = make(
    span([(5, 7), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (5, 8), (5, 8), (5, 7), (5, 7), (5, 7), (6, 7)]),
    tail={12: [5, 6, 7, 8], 13: [6, 7]},
    fins=[(4, 3), (5, 3), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 5),
          (6, 9), (7, 9), (8, 9), (9, 8), (10, 8), (11, 8)],
    eye=(1, 5), mouth=[(0, 7)], pect=[(3, 7), (4, 7)])

# Aal: geschwungen, Saumflosse
SHAPES["eel"] = make(
    {0: (6, 8), 1: (5, 8), 2: (5, 8), 3: (5, 7), 4: (4, 6), 5: (4, 6), 6: (4, 6), 7: (5, 7), 8: (6, 8), 9: (7, 9),
     10: (7, 9), 11: (7, 9), 12: (6, 8), 13: (5, 7)},
    fins=[(6, 3), (7, 4), (8, 5), (9, 6), (10, 6), (11, 6), (12, 5), (13, 4),
          (9, 10), (10, 10), (11, 10), (12, 9), (13, 8)],
    eye=(1, 6), eye_size=1, mouth=[(0, 8)], pect=[(3, 7)])

# Seeschlange (Leviathan): geschwungen, Stacheln auf dem Rücken, Horn
SHAPES["serpent"] = make(
    {0: (6, 9), 1: (5, 9), 2: (5, 9), 3: (5, 8), 4: (4, 7), 5: (4, 7), 6: (4, 7), 7: (5, 8), 8: (6, 9), 9: (7, 10),
     10: (7, 10), 11: (6, 9), 12: (5, 8), 13: (4, 7)},
    fins=[(3, 4), (5, 3), (7, 4), (9, 6), (11, 5), (13, 3)],
    extra=[(1, 4), (2, 3)],
    eye=(1, 6), mouth=[(0, 8)])

# Grottenolm: dünner Körper, rote Kiemenbüschel, Beinchen, Flossensaum am Schwanz, keine Augen
SHAPES["olm"] = make(
    {0: (6, 7), 1: (5, 8), 2: (5, 8), 3: (6, 7), 4: (6, 7), 5: (6, 7), 6: (6, 7), 7: (6, 7), 8: (6, 7), 9: (6, 7),
     10: (6, 7), 11: (6, 7), 12: (6, 7), 13: (7, 7)},
    fins=[(5, 8), (5, 9), (10, 8), (10, 9), (9, 5), (10, 5), (11, 5), (12, 5), (11, 8), (12, 8)],
    acc={"1": [(3, 4), (3, 5), (4, 5), (3, 8), (3, 9), (4, 8)]}, mouth=[(0, 7)])

# Axolotl: breiter Kopf, drei Kiemenäste je Seite, Beine, Flossensaum
SHAPES["axolotl"] = make(
    {0: (6, 8), 1: (5, 9), 2: (5, 9), 3: (5, 9), 4: (6, 8), 5: (6, 8), 6: (6, 8), 7: (6, 8), 8: (6, 8), 9: (6, 8),
     10: (6, 8), 11: (6, 7), 12: (6, 7), 13: (7, 7)},
    fins=[(5, 9), (5, 10), (9, 9), (9, 10), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5), (12, 5), (11, 8),
          (12, 8), (13, 8)],
    acc={"1": [(2, 4), (3, 4), (1, 3), (3, 3), (4, 2), (2, 10), (3, 10), (1, 11), (3, 11), (4, 12)]},
    eye=(1, 6), eye_size=1, mouth=[(0, 8)])

# Tintenfisch: Mantel nach rechts, Flossen hinten, Arme vorne
SHAPES["squid"] = make(
    {3: (5, 8), 4: (4, 9), 5: (4, 9), 6: (4, 9), 7: (4, 9), 8: (4, 9), 9: (4, 9), 10: (5, 8), 11: (5, 8),
     12: (6, 7)},
    fins=[(10, 3), (11, 4), (12, 5), (10, 10), (11, 9), (12, 8)],
    loose=[(2, 5), (1, 5), (0, 4), (2, 7), (1, 7), (0, 7), (2, 9), (1, 9), (0, 10), (2, 6), (2, 8)],
    eye=(4, 5))

# Qualle: Schirm oben, Tentakel unten
SHAPES["jelly"] = [
    "....bbbbbb....",
    "..bbbbbbbbbb..",
    ".bbbbbbbbbbbb.",
    ".bbbbbbbbbbbb.",
    "bbbbbbbbbbbbbb",
    "b1b1b1b1b1b1bb",
    ".X..X..X..X...",
    ".X...X.X...X..",
    "..X..X..X..X..",
    "..X.X...X.X...",
    "...X....X.....",
    "...X.....X....",
]

# Seepferdchen: aufrecht, Röhrenschnauze, eingerollter Schwanz
SHAPES["seahorse"] = [
    ".....bb.......",
    "....bbbb......",
    "bbbbbwebb.....",
    "....bbbbbf....",
    ".....bbbbff...",
    ".....bbbbbf...",
    "......bbbbf...",
    "......bbbbf...",
    "......bbbb....",
    ".......bbb....",
    "....b...bb....",
    "....bb..bb....",
    ".....bbbb.....",
]

# Seeteufel: riesiger Kopf, offenes Maul mit Zähnen, Angel mit Leuchtköder
SHAPES["angler"] = make(
    span([(5, 10), (3, 11), (3, 11), (3, 11), (3, 11), (4, 10), (4, 10), (5, 9), (5, 9), (6, 8), (6, 8)]),
    tail={11: [5, 6, 7, 8, 9], 12: [5, 6, 7, 8, 9]},
    fins=[(7, 3), (8, 4), (8, 10), (5, 11)],
    ink_px=[(0, 7), (1, 7), (2, 7), (0, 8), (1, 8), (2, 8), (3, 8), (0, 9), (1, 9)],
    acc={"1": [(0, 6), (2, 6), (1, 10)], "2": [(0, 0)]},
    extra=[(3, 2), (2, 1), (1, 0)],
    eye=(4, 4), eye_size=1)

# Hammerhai: Hai mit Hammerkopf
SHAPES["hammer"] = make(
    {0: (2, 10), 1: (4, 8), 2: (4, 7), 3: (3, 8), 4: (3, 8), 5: (3, 8), 6: (3, 8), 7: (3, 8), 8: (4, 7), 9: (4, 7),
     10: (5, 6)},
    tail={11: [4, 5, 6, 7], 12: [2, 3, 4, 7, 8], 13: [0, 1, 2, 8]},
    fins=[(5, 0), (4, 1), (5, 1), (3, 2), (4, 2), (5, 2), (6, 2), (9, 3), (4, 9), (5, 9), (5, 10), (6, 10),
          (6, 11), (9, 8)],
    eyes=[(0, 2), (0, 10)], mouth=[(1, 8)], gills=[(5, 5), (5, 6), (6, 5), (6, 6)])

# Rochen von oben: Flügel, Kopflappen, langer dünner Schwanz
SHAPES["ray"] = make(
    {0: (6, 6), 1: (5, 7), 2: (5, 7), 3: (4, 8), 4: (3, 8), 5: (3, 9), 6: (3, 9), 7: (3, 9), 8: (3, 8),
     9: (4, 8), 10: (5, 7), 11: (5, 7), 12: (6, 6)},
    fins=[(5, 2), (8, 2)],
    extra=[(7, 10), (8, 11), (9, 12), (10, 13)],
    eyes=[(5, 4), (8, 4)], acc={"1": [(4, 6), (9, 6)]})

# Prachtflossen (Phönix): Körper wie classic, lange wehende Flossen
SHAPES["fancy"] = make(
    span([(5, 6), (4, 7), (3, 8), (3, 8), (2, 9), (2, 9), (2, 9), (3, 8), (3, 8), (4, 7)]),
    tail={10: [4, 5, 6, 7], 11: [2, 3, 4, 5, 6, 7, 8, 9], 12: [1, 2, 3, 8, 9, 10], 13: [0, 1, 10, 11]},
    fins=[(4, 1), (5, 1), (5, 0), (6, 1), (6, 0), (7, 0), (7, 2), (8, 1), (8, 2), (9, 3),
          (5, 10), (5, 11), (6, 11), (8, 9), (9, 9)],
    eye=(2, 4), mouth=[(0, 6)], gill=(4, 5, 7), pect=[(5, 7), (6, 7)])

# Drachenfisch: lang, schwarz, Leuchtbartel am Kinn, Zähne
SHAPES["dragon"] = make(
    span([(5, 7), (5, 7), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (4, 8), (5, 7), (5, 7), (5, 6)]),
    tail={12: [4, 5, 6, 7], 13: [3, 4, 7, 8]},
    fins=[(9, 3), (10, 4), (9, 9), (10, 8)],
    loose=[(1, 8), (1, 9), (1, 10), (2, 11)], acc={"1": [(0, 6)], "2": [(3, 11)]},
    eye=(1, 5), mouth=[(0, 7), (1, 7)])
