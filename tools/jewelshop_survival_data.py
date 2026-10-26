"""Inhalt des Survival-Juwelenshops nach den Referenzbildern (Release "Referenz2"): Möbel in sieben Reitern und
die Pets nach Seltenheit, in der Reihenfolge der Aufnahmen. Gemeinsame Quelle für das Zuschneiden der Bilder
(extract_jewelshop_survival.py), die Möbel-Modelle (build_furniture.py) und den Katalog im Plugin.

Möbel: (id, Name, Preis in Juwelen); Pets: (id, Name, Seltenheit, Preis).
"""

FURNITURE = [
    {"id": "sitzen", "name": "Sitzen", "sub": "Stühle, Bänke, Hocker", "first": 3, "items": [
        ("holzstuhl", "Holzstuhl", 100), ("stuhl_blau", "Stuhl Blau", 100), ("stuhl_weiss", "Stuhl Weiß", 100),
        ("lehnstuhl", "Lehnstuhl", 100), ("holzbank", "Holzbank", 100), ("bank_gruen", "Bank Grün", 100),
        ("bank_weiss", "Bank Weiß", 100), ("lehnbank", "Lehnbank", 150), ("buerostuhl", "Bürostuhl", 100),
        ("klapphocker", "Klapphocker", 50), ("barhocker", "Barhocker", 150), ("casino_hocker", "Casino-Hocker", 50),
    ]},
    {"id": "tische", "name": "Tische", "sub": "Tische, Kommoden, Tafeln", "first": 5, "items": [
        ("holztisch", "Holztisch", 100), ("tisch_gruen", "Tisch Grün", 100), ("tisch_weiss", "Tisch Weiß", 100),
        ("holztafel", "Holztafel", 150), ("tafel_gruen", "Tafel Grün", 150), ("tafel_weiss", "Tafel Weiß", 150),
        ("esstisch", "Esstisch", 100), ("bauerntisch", "Bauerntisch", 50), ("beistelltisch", "Beistelltisch", 100),
        ("tischchen", "Tischchen", 100), ("kommode", "Kommode", 100), ("anrichte", "Anrichte", 150),
        ("schreibtisch", "Schreibtisch", 100), ("blumenhocker_1", "Blumenhocker 1", 100),
        ("blumenhocker_2", "Blumenhocker 2", 100),
    ]},
    {"id": "pflanzen", "name": "Pflanzen", "sub": "Töpfe, Ranken, Pilze", "first": 7, "items": [
        ("pflanzkasten", "Pflanzkasten", 50)]
        + [("topfpflanze_%d" % i, "Topfpflanze %d" % i, p) for i, p in enumerate(
            [100, 100, 100, 100, 100, 100, 150, 100, 100, 100, 100, 100, 150, 150, 150, 150, 150, 150, 100, 100], 1)]
        + [("haengepflanze_%d" % i, "Hängepflanze %d" % i, p) for i, p in enumerate(
            [100, 100, 100, 100, 100, 100, 100, 100, 150], 1)]
        + [("ranke_%d" % i, "Ranke %d" % i, p) for i, p in enumerate([100, 100, 150, 100, 100, 150, 100, 100, 150], 1)]
        + [("pflanzbrett", "Pflanzbrett", 100), ("kaktus", "Kaktus", 50), ("fliegenpilz", "Fliegenpilz", 100),
           ("pilzchen_rot", "Pilzchen Rot", 50), ("pilzgruppe_rot", "Pilzgruppe Rot", 100),
           ("steinpilz", "Steinpilz", 100), ("pilzchen_braun", "Pilzchen Braun", 50),
           ("pilzgruppe_braun", "Pilzgruppe Braun", 100)]},
    {"id": "lager", "name": "Lager", "sub": "Kisten, Körbe, Schränke", "first": 13, "items": [
        ("vorratskiste_%d" % i, "Vorratskiste %d" % i, 150 if i == 11 else 100) for i in range(1, 27)]
        + [("obstkorb", "Obstkorb", 150), ("kleiderschrank", "Kleiderschrank", 250),
           ("schliessfach", "Schließfach", 100), ("offenes_fach", "Offenes Fach", 100)]},
    {"id": "deko", "name": "Deko", "sub": "Banner, Schilder, Vorhänge", "first": 17, "items": [
        ("banner_%d" % i, "Banner %d" % i, 150 if i <= 2 else 250) for i in range(1, 10)]
        + [("wegweiser_%d" % i, "Wegweiser %d" % i, p) for i, p in enumerate([150, 150, 100, 150, 150, 100], 1)]
        + [("vorhang_%d" % i, "Vorhang %d" % i, p) for i, p in enumerate([150, 150, 100, 150, 150, 100], 1)]
        + [("pinnwand", "Pinnwand", 250), ("holzwand", "Holzwand", 250), ("spiegel", "Spiegel", 150),
           ("ladenschild", "Ladenschild", 250)]
        + [("kleiderpuppe_%d" % i, "Kleiderpuppe %d" % i, p) for i, p in enumerate([150, 100, 150, 100], 1)]
        + [("puppenkopf", "Puppenkopf", 50), ("kleiderstange_1", "Kleiderstange 1", 100),
           ("kleiderstange_2", "Kleiderstange 2", 100), ("bunte_stoffe", "Bunte Stoffe", 50),
           ("kissenstapel", "Kissenstapel", 50), ("stoffstapel_1", "Stoffstapel 1", 50),
           ("stoffstapel_2", "Stoffstapel 2", 50), ("felsen_1", "Felsen 1", 250), ("felsen_2", "Felsen 2", 250)]},
    {"id": "markt", "name": "Markt", "sub": "Marktstände und Karren", "first": 22, "items": [
        ("marktstand_%d" % i, "Marktstand %d" % i, 250) for i in range(1, 5)]
        + [("handkarren_1", "Handkarren 1", 250), ("handkarren_2", "Handkarren 2", 250),
           ("stoffbahn_1", "Stoffbahn 1", 250), ("stoffbahn_2", "Stoffbahn 2", 250)]},
    {"id": "bank", "name": "Bank", "sub": "Bank und Casino", "first": 23, "items": [
        ("geldautomat", "Geldautomat", 100), ("tresortuer", "Tresortür", 150), ("bankschild", "Bankschild", 150),
        ("waage", "Waage", 100), ("computer", "Computer", 100), ("kamera", "Kamera", 50),
        ("schranke", "Schranke", 100), ("vitrine", "Vitrine", 50), ("aktenkoffer", "Aktenkoffer", 50),
        ("papierstapel", "Papierstapel", 50), ("geldbuendel", "Geldbündel", 50), ("geldstapel", "Geldstapel", 100),
        ("geldkoffer", "Geldkoffer", 100), ("chip_vitrine", "Chip-Vitrine", 150), ("chipstapel", "Chipstapel", 50),
        ("stehlampe", "Stehlampe", 150), ("anzeigetafel", "Anzeigetafel", 250), ("servierwagen", "Servierwagen", 100),
    ]},
]

PETS_FIRST = 26
C, R, E, L, M = "common", "rare", "epic", "legendary", "mythic"
PETS = [
    ("baby_creeper", "Baby-Creeper", C, 500), ("baby_zombie", "Baby-Zombie", C, 500),
    ("endsteinchen", "Endsteinchen", C, 500), ("netherknirps", "Netherknirps", C, 500),
    ("schwarzsteinchen", "Schwarzsteinchen", C, 500), ("wunschbaer", "Wunschbär", C, 500),
    ("baby_enderman", "Baby-Enderman", R, 900), ("basaltling", "Basaltling", R, 900),
    ("chorusling", "Chorusling", R, 900), ("goldhamster", "Goldhamster", R, 900), ("hexlein", "Hexlein", R, 900),
    ("karmesinpilz", "Karmesinpilz", R, 900), ("knochenmagier", "Knochenmagier", R, 900),
    ("laubdrache", "Laubdrache", R, 900), ("magmara", "Magmara", R, 900), ("perlchen", "Perlchen", R, 900),
    ("roboflitz", "Roboflitz", R, 150), ("steindrache", "Steindrache", R, 900), ("waldwaechter", "Waldwächter", R, 900),
    ("winddrache", "Winddrache", R, 900), ("wirrpilz", "Wirrpilz", R, 900),
    ("anzuki", "Anzuki", E, 1500), ("baby_phoenix", "Baby-Phönix", E, 1500), ("baby_warden", "Baby-Warden", E, 1500),
    ("frostmagierin", "Frostmagierin", E, 1500), ("hexenkatze", "Hexenkatze", E, 1500), ("knarzi", "Knarzi", E, 1500),
    ("kristallini", "Kristallini", E, 1500), ("magmadrache", "Magmadrache", E, 1500),
    ("portalchen", "Portalchen", E, 1500), ("seelenfunke", "Seelenfunke", E, 1500), ("shulki", "Shulki", E, 1500),
    ("sumpfhexe", "Sumpfhexe", E, 1500), ("truhling", "Truhling", E, 1500), ("wasserdrache", "Wasserdrache", E, 1500),
    ("baby_wither", "Baby-Wither", L, 2500), ("donnerzahn", "Donnerzahn", L, 2500),
    ("dunkelzahn", "Dunkelzahn", L, 2500), ("eiszahn", "Eiszahn", L, 2500), ("endfunke", "Endfunke", L, 2500),
    ("endportalchen", "Endportalchen", L, 2500), ("kampftiger", "Kampftiger", L, 2500),
    ("miauberer", "Miauberer", L, 2500), ("mondzahn", "Mondzahn", L, 2500), ("nachtdrache", "Nachtdrache", L, 2500),
    ("nebelzahn", "Nebelzahn", L, 2500), ("sonnendrache", "Sonnendrache", L, 2500),
    ("zauberspinne", "Zauberspinne", L, 2500), ("rabenkoenig", "Rabenkönig", M, 4000),
]


def count():
    return sum(len(t["items"]) for t in FURNITURE)


if __name__ == "__main__":
    print(count(), "Möbel,", len(PETS), "Pets")
    for t in FURNITURE:
        pages = (len(t["items"]) + 7) // 8
        print(t["id"], len(t["items"]), "Seiten", pages, "Bilder", t["first"], "-", t["first"] + pages - 1)
