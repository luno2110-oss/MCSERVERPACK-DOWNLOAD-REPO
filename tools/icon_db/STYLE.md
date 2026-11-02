# Icon-Stil der Referenzen (gemessen)

Erzeugt von `tools/icon_db/build_icon_db.py` aus 800 Bildern der Referenz-Sätze (battlepass, friends, guide, infobar, jewelshop, jewelshop_survival, playtime, profile, pwarps, quests, rules, settings, support). Gemessen an 437 Icons, Möbeln und Pets (ohne Rahmen, Balken, Knöpfe). Kontaktbögen: `tools/icon_db/sheets/`, Rohdaten: `tools/icon_db/icons.json`, Abfrage: `tools/icon_db/refpal.py`.

## Kontur

- 72 % der Icons haben eine geschlossene dunkle Kontur (mindestens 70 % der Randpixel dunkel), 21 % eine teilweise (selektive Kontur: an hellen Stellen fehlt sie oder ist getönt).
- Kontur ist immer 1 px. Häufigste Konturfarben: `#060401`, `#322313`, `#30200C`, `#001800`, `#050301`, `#342414`, `#31220C`, `#020101` - fast schwarz, meist leicht getönt (warm-braun oder kühl-lila), selten reines #000000.

## Farbverläufe (Rampen)

- Stufen je Material (Farbfamilie in einem Icon): Median 4, meist 3 bis 5. Mehr Stufen nur bei großen Flächen.
- Helligkeitsschritt zwischen zwei Stufen: Median 6 % (empfundene Helligkeit), also deutlich sichtbare, gleichmäßige Stufen statt weicher Übergänge.
- Lichter wandern im Farbton Richtung Gelb (wärmer): 82 % der Rampen mit Farbtonwechsel; Schatten Richtung Blau/Lila (kühler): 83 %.
- Sättigung: in 84 % der Rampen ist die Mittelstufe mindestens so satt wie die hellste - Lichter werden heller und etwas blasser, nicht weiß; Schatten dunkler und oft satter.

## Licht

- Licht von oben: 48 % der Icons werden nach unten dunkler. Von links: 52 %, von rechts: 12 % (sonst gleichmäßig).
- Glanzpunkte (einzelne Pixel deutlich heller als alle Nachbarn): in 43 % der Icons, meist 1 bis 3, oben links auf runden Flächen (Edelsteine, Kugeln, Metall).

## Symmetrie und Muster

- Form fast symmetrisch (mindestens 90 % spiegelgleich): 35 % der Icons. Bei diesen sind im Mittel nur 49 % auch farblich spiegelgleich - die Form ist symmetrisch, das Licht bricht sie (eine Seite heller, Glanz nur auf einer Seite).
- Dithering (Schachbrett aus zwei deutlich verschiedenen Farben, mindestens 3 % der Fläche): 3 % der Icons - selten, Übergänge sind harte Stufen.
- Farben je Icon: Median 15. Nach Größe: 16 px: 4, 32 px: 14, 64 px: 18.

## Regeln für neue Icons

1. Jede Farbe kommt aus einer Rampe (3 bis 5 Stufen je Material), keine Einzelfarben ohne Rolle.
2. 1 px Kontur, fast schwarz und leicht getönt; an der Lichtseite darf sie selektiv heller werden.
3. Licht oben links: oberste Kante je Fläche 1 Stufe heller, Unterseite 1 bis 2 Stufen dunkler.
4. Lichter wärmer (Richtung Gelb), Schatten kühler (Richtung Blau/Lila), Mitte am sattesten.
5. Form symmetrisch, Licht und Details bewusst asymmetrisch (Glanz nur auf einer Seite, ein Detail versetzt).
6. Muster wiederholen (Nieten, Bretter, Streifen) mit gleichem Abstand, dann einmal brechen (ein Riss, ein Fleck).
7. Kein Dithering, keine Kantenglättung mit Zwischenfarben, keine verwaisten Einzelpixel ohne Zweck.

## Hex-Verhalten (Kanäle Schritt für Schritt)

So bewegen sich die Hex-Werte in typischen Referenz-Rampen (dunkel -> hell):

- Der Hauptkanal steigt in großen, gleichmäßigen Schritten (etwa +0x14 bis +0x29) bis knapp unter dem Maximum (0xF8 bis 0xFA). Reines 0xFF kommt in Rampen praktisch nicht vor.
- Erst wenn der Hauptkanal oben ist, steigen die anderen Kanäle für die Lichter: Rot bekommt Grün dazu (wird Orange), Gold bekommt Blau dazu (wird blasses Gelb, nicht Weiß), Blau bekommt Rot und Grün dazu (wird helles Himmelblau). So werden Lichter wärmer bzw. blasser, ohne die Farbe zu verlieren.
- Satte Farben (Smaragd, Rot) halten die Nebenkanäle fast auf 0x00 bis 0x10, bis zur obersten Stufe.
- Die dunkelste Stufe liegt im Hauptkanal bei etwa 0x4E bis 0x89, also nie fast schwarz - das Schwarz macht die Kontur, nicht die Rampe.

**jewelshop/art_crate** (rot)

| Kanal | `#890416` | `#A50617` | `#BB0C15` | `#D20B19` | `#E71217` | `#F72C18` | `#FA471D` |
|---|---|---|---|---|---|---|---|
| R | 89 | A5 (+28) | BB (+22) | D2 (+23) | E7 (+21) | F7 (+16) | FA (+3) |
| G | 04 | 06 (+2) | 0C (+6) | 0B (-1) | 12 (+7) | 2C (+26) | 47 (+27) |
| B | 16 | 17 (+1) | 15 (-2) | 19 (+4) | 17 (-2) | 18 (+1) | 1D (+5) |

**jewelshop_survival/art_ov_farm** (gold)

| Kanal | `#F8A403` | `#FABE09` | `#FBD20E` | `#FAD921` | `#FAE63D` | `#FBEF59` | `#FAF376` | `#FAF694` |
|---|---|---|---|---|---|---|---|---|
| R | F8 | FA (+2) | FB (+1) | FA (-1) | FA (+0) | FB (+1) | FA (-1) | FA (+0) |
| G | A4 | BE (+26) | D2 (+20) | D9 (+7) | E6 (+13) | EF (+9) | F3 (+4) | F6 (+3) |
| B | 03 | 09 (+6) | 0E (+5) | 21 (+19) | 3D (+28) | 59 (+28) | 76 (+29) | 94 (+30) |

**jewelshop/art_emerald_big** (gruen)

| Kanal | `#034E04` | `#026204` | `#028B05` | `#02A606` | `#01C408` | `#03E00F` |
|---|---|---|---|---|---|---|
| R | 03 | 02 (-1) | 02 (+0) | 02 (+0) | 01 (-1) | 03 (+2) |
| G | 4E | 62 (+20) | 8B (+41) | A6 (+27) | C4 (+30) | E0 (+28) |
| B | 04 | 04 (+0) | 05 (+1) | 06 (+1) | 08 (+2) | 0F (+7) |

**support/art_tickets** (blau)

| Kanal | `#0376F0` | `#0796EE` | `#269BF8` | `#43ADF9` | `#5FBDF9` | `#75CAFA` | `#8AD4FA` | `#A4E1FA` |
|---|---|---|---|---|---|---|---|---|
| R | 03 | 07 (+4) | 26 (+31) | 43 (+29) | 5F (+28) | 75 (+22) | 8A (+21) | A4 (+26) |
| G | 76 | 96 (+32) | 9B (+5) | AD (+18) | BD (+16) | CA (+13) | D4 (+10) | E1 (+13) |
| B | F0 | EE (-2) | F8 (+10) | F9 (+1) | F9 (+0) | FA (+1) | FA (+0) | FA (+0) |

## Rampen je Farbfamilie (häufigste aus den Referenzen, dunkel -> hell)

### rot

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#890416` `#A50617` `#BB0C15` `#D20B19` `#E71217` `#F72C18` `#FA471D` | +2/-0.01/+0.04 · +3/-0.03/+0.04 · -1/+0.01/+0.03 · +3/-0.03/+0.04 · +7/-0.02/+0.08 · +6/-0.02/+0.07 | jewelshop/art_crate |
| `#800214` `#94031A` `#AC0A1B` `#C81D23` `#E52220` `#FA4B2D` | -1/-0.01/+0.03 · +3/-0.04/+0.04 · +4/-0.09/+0.08 · +3/+0.01/+0.04 · +8/-0.04/+0.12 | jewelshop_survival/art_ov_furniture |
| `#412822` `#55221E` `#6B271F` `#822E28` `#9B362D` `#BC3D34` `#C44B42` | -7/+0.17/+0.01 · +2/+0.06/+0.04 · -2/-0.02/+0.05 · +1/+0.02/+0.05 · -1/+0.01/+0.06 · +0/-0.06/+0.05 | jewelshop_survival/f_fliegenpilz |
| `#9F0101` `#AF0100` `#BF0101` `#D60202` `#F30803` `#F72607` | +0/+0.01/+0.02 · -0/-0.01/+0.02 · +0/-0.00/+0.03 · +1/-0.00/+0.05 · +6/-0.02/+0.08 | jewelshop/art_rank_devil |
| `#4B2227` `#662E2F` `#7F3D3C` `#785149` `#93564E` `#9F6566` `#B4756F` | +6/+0.00/+0.06 · +2/-0.02/+0.07 · +9/-0.14/+0.04 · -3/+0.08/+0.04 · -8/-0.10/+0.06 · +6/+0.02/+0.07 | jewelshop_survival/p_magmadrache |
| `#870500` `#830701` `#A90201` `#851900` `#CB0600` `#EB0300` `#BA1E00` `#F71301` `#F72B01` | +0/-0.01/+0.00 · -2/+0.00/+0.03 · +11/+0.01/+0.01 · -10/+0.00/+0.04 · -1/+0.00/+0.03 · +9/+0.00/+0.01 · -5/-0.00/+0.05 · +6/+0.00/+0.06 | jewelshop/art_rank_legend |

### orange

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#AC3200` `#C34600` `#DC5C00` `#EC7301` `#DD8803` `#F68801` | +4/+0.00/+0.07 · +4/+0.00/+0.08 · +4/-0.00/+0.07 · +8/-0.01/+0.03 · -4/+0.01/+0.03 | jewelshop_survival/art_tok_25 |
| `#A63001` `#C44800` `#D35901` `#E06400` `#ED7501` `#F48801` | +5/+0.01/+0.09 · +3/-0.01/+0.06 · +2/+0.01/+0.04 · +3/-0.00/+0.06 · +4/+0.00/+0.05 | jewelshop_survival/art_tok_250 |
| `#A12F01` `#BA3C01` `#D85801` `#F07601` `#F68401` `#F79202` | +2/+0.00/+0.06 · +5/+0.00/+0.10 · +5/+0.00/+0.10 · +3/+0.00/+0.04 · +3/-0.00/+0.03 | jewelshop_survival/art_tok_75 |
| `#C67218` `#C97D2F` `#D58D44` `#E39029` `#EAA242` `#F6BB57` | -1/-0.11/+0.04 · -0/-0.09/+0.06 · +3/+0.14/+0.01 · +1/-0.10/+0.06 · +4/-0.07/+0.08 | pwarps/art_map |
| `#AA5C23` `#C36B22` `#E86001` `#EC7D0B` `#DF8023` `#F89001` `#F39D27` | +2/+0.03/+0.06 · -2/+0.17/+0.00 · +6/-0.04/+0.08 · -1/-0.11/+0.00 · +5/+0.15/+0.05 · -0/-0.16/+0.04 | jewelshop_survival/art_ov_furniture |
| `#AC4001` `#B74D02` `#C45501` `#BE541A` `#DA6C26` `#E17201` `#F18A02` | +3/-0.01/+0.04 · +1/+0.01/+0.03 · -5/-0.13/+0.00 · +2/-0.04/+0.09 · +7/+0.17/+0.01 · +4/-0.00/+0.07 | jewelshop_survival/art_ov_farm |

### braun

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#362616` `#48311B` `#52422A` `#685536` `#7B633D` `#917C52` | -1/+0.03/+0.05 · +7/-0.14/+0.06 · +1/-0.01/+0.07 · -0/+0.02/+0.06 · +3/-0.07/+0.09 | jewelshop_survival/f_felsen_2 |
| `#54210A` `#74300A` `#6C4103` `#813801` `#8D4014` `#974000` | +3/+0.03/+0.07 · +14/+0.06/+0.03 · -10/+0.02/+0.00 · -4/-0.13/+0.04 · +4/+0.14/+0.00 | jewelshop/art_chest |
| `#4B1F03` `#50330C` `#653507` `#6B4911` `#815E18` `#956A18` | +11/-0.11/+0.06 · -5/+0.08/+0.03 · +8/-0.09/+0.06 · +3/-0.03/+0.08 · -1/+0.03/+0.05 | jewelshop_survival/f_anzeigetafel |
| `#4B2201` `#612A01` `#7A3601` `#904300` `#794E07` `#945700` | -1/+0.00/+0.04 · +1/+0.00/+0.06 · +2/+0.01/+0.06 · +10/-0.06/+0.00 · -2/+0.06/+0.05 | jewelshop/art_pack_ultimate |
| `#3E250A` `#5B2C04` `#50350E` `#763B06` `#9B530C` `#8C6016` `#987220` | -4/+0.12/+0.05 · +8/-0.13/+0.01 · -7/+0.12/+0.06 · +1/-0.03/+0.10 · +8/-0.08/+0.02 · +3/-0.05/+0.06 | jewelshop_survival/f_stehlampe |
| `#4B1F06` `#5A260A` `#6E3D1C` `#763F1D` `#91471E` `#915C31` | -1/-0.03/+0.04 · +3/-0.14/+0.08 · -1/+0.01/+0.01 · -2/+0.04/+0.05 · +6/-0.13/+0.06 | rules/art_book |

### gold

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#F8A403` `#FABE09` `#FBD20E` `#FAD921` `#FAE63D` `#FBEF59` `#FAF376` `#FAF694` | +6/-0.02/+0.07 · +5/-0.02/+0.05 · +1/-0.08/+0.02 · +3/-0.11/+0.04 · +2/-0.11/+0.03 · +1/-0.12/+0.02 · +1/-0.12/+0.02 | jewelshop_survival/art_ov_farm |
| `#F49D02` `#F7B804` `#F8DE06` `#F9EE0A` `#F9F123` `#FAF44A` `#F8F374` `#FAF695` `#FAF9B6` | +6/-0.01/+0.07 · +9/-0.01/+0.09 · +4/-0.02/+0.04 · +0/-0.10/+0.02 · +0/-0.15/+0.03 · -0/-0.17/+0.01 · -0/-0.13/+0.02 · +2/-0.13/+0.02 | jewelshop/art_chest |
| `#CC8904` `#F7AD02` `#F7CA05` `#F8E407` `#F9F020` `#FAF340` `#F9F45D` `#FAF584` | +2/+0.01/+0.13 · +7/-0.01/+0.07 · +6/-0.01/+0.06 · +2/-0.10/+0.04 · +0/-0.13/+0.02 · +0/-0.12/+0.01 · -1/-0.15/+0.02 | battlepass/art_crown |
| `#F49D02` `#F8AE02` `#F9C803` `#F9E206` `#FAE624` `#F9F10E` | +4/+0.00/+0.04 · +6/-0.00/+0.06 · +6/-0.01/+0.06 · +0/-0.12/+0.02 · +4/+0.09/+0.01 | jewelshop_survival/art_tok_25 |
| `#F7AD03` `#F7C903` `#F8E006` `#FAF024` `#F9ED3E` `#FAF455` | +7/+0.00/+0.06 · +5/-0.01/+0.06 · +3/-0.12/+0.05 · -1/-0.10/+0.00 · +2/-0.09/+0.03 | jewelshop/art_pack_starter |
| `#F5A703` `#F7BE04` `#FADE09` `#F9E91E` `#F8EE42` `#FAF132` | +5/-0.00/+0.06 · +7/-0.02/+0.08 · +3/-0.08/+0.03 · +1/-0.15/+0.03 · +1/+0.07/+0.00 | jewelshop/art_pack_ultimate |

### gruen

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#293D09` `#3B530C` `#4F6910` `#618411` `#78AE13` `#8CC811` | -3/+0.00/+0.07 · -3/-0.01/+0.08 · +1/+0.02/+0.08 · +3/+0.02/+0.12 · -1/+0.02/+0.08 | jewelshop_survival/f_geldstapel |
| `#265631` `#387244` `#448F50` `#5CB16A` `#76D084` `#7EDD8D` `#90E69D` | -1/-0.05/+0.09 · -3/+0.02/+0.09 · +0/-0.04/+0.12 · -0/-0.05/+0.11 · +0/-0.00/+0.04 · -0/-0.06/+0.05 | jewelshop_survival/art_rank_smaragd |
| `#32470A` `#485F0D` `#5C7D12` `#699617` `#7DAF19` `#90C81A` `#A2DE1B` | -4/+0.00/+0.08 · +2/-0.01/+0.10 · +3/-0.01/+0.07 · -1/+0.01/+0.08 · -1/+0.01/+0.08 · -1/+0.01/+0.07 | jewelshop_survival/f_geldbuendel |
| `#034E04` `#026204` `#028B05` `#02A606` `#01C408` `#03E00F` | +0/+0.02/+0.04 · +0/+0.01/+0.10 · +0/+0.00/+0.06 · +1/+0.01/+0.07 · +1/-0.01/+0.07 | jewelshop/art_emerald_big |
| `#2C4410` `#3E5418` `#45661C` `#5E8927` `#6FA12E` `#86C339` | -6/-0.05/+0.06 · +5/+0.01/+0.05 · -0/-0.01/+0.12 · -0/-0.00/+0.08 · +0/-0.01/+0.11 | jewelshop_survival/f_vorratskiste_20 |
| `#014714` `#015623` `#006B2A` `#008029` `#019427` `#01B427` `#02CD27` `#04E228` `#11EE37` `#21F250` `#2CF436` | +8/+0.00/+0.04 · -0/+0.01/+0.05 · -4/+0.00/+0.05 · -4/-0.01/+0.05 · -3/+0.00/+0.07 · -2/-0.00/+0.06 · -1/-0.01/+0.05 · +1/-0.05/+0.05 · +3/-0.07/+0.04 · -10/-0.04/+0.01 | jewelshop/art_chest |

### tuerkis

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#25424B` `#386472` `#41707B` `#4E8D96` `#5EA0AB` `#6BB2BC` `#89D3DD` `#A4E9F3` | +0/+0.00/+0.12 · -3/-0.04/+0.04 · -4/+0.01/+0.09 · +1/-0.03/+0.07 · -1/-0.02/+0.06 · -0/-0.05/+0.13 · +0/-0.06/+0.09 | jewelshop_survival/art_rank_diamant |
| `#024345` `#036667` `#027F7E` `#0395B1` `#01A5C7` `#01B5A6` `#03BDD4` `#02CDB8` `#02D4E3` `#05E7CC` `#08EFF1` | -1/-0.00/+0.10 · -1/+0.01/+0.07 · +10/-0.00/+0.07 · +1/+0.01/+0.04 · -15/-0.00/+0.02 · +12/-0.01/+0.04 · -13/+0.00/+0.02 · +10/+0.00/+0.04 · -11/-0.01/+0.04 · +8/-0.01/+0.04 | pwarps/art_logo |
| `#355552` `#416864` `#487B78` `#5A9090` `#62ADA6` `#71CAC3` | -0/-0.00/+0.07 · +3/+0.04/+0.06 · +4/-0.04/+0.08 · -6/+0.06/+0.09 · +1/+0.01/+0.10 | jewelshop_survival/p_winddrache |
| `#019491` `#00A5A3` `#00A8C0` `#01C0BA` `#02D5C9` `#05EDDD` `#12F6E9` `#29F7E9` | +0/+0.01/+0.05 · +8/+0.00/+0.02 · -9/-0.01/+0.05 · -2/-0.00/+0.06 · -1/-0.01/+0.07 · +1/-0.05/+0.04 · -1/-0.09/+0.03 | pwarps/art_portal |
| `#163532` `#10443E` `#0E4E4D` `#194D46` `#126262` `#247B69` `#108378` | -1/+0.18/+0.03 · +6/+0.06/+0.03 · -7/-0.14/+0.01 · +8/+0.14/+0.05 · -12/-0.11/+0.08 · +7/+0.17/+0.00 | jewelshop_survival/p_wirrpilz |
| `#006F8F` `#0181A3` `#029CB4` `#02B4C3` `#02B7EB` `#1CB6C8` `#02C7D7` `#07E3EB` `#24D7F8` `#50D2F9` `#34EEF1` `#4DF2F6` `#6DF5F6` `#98F8F9` `#C2EFF8` | -1/-0.01/+0.05 · -4/-0.01/+0.07 · -3/+0.00/+0.06 · +9/+0.00/+0.03 · -7/-0.13/+0.01 · -2/+0.13/+0.01 · -2/-0.02/+0.08 · +7/-0.12/+0.01 · +4/-0.18/+0.04 · -13/+0.11/+0.03 · +0/-0.10/+0.04 · -1/-0.13/+0.04 · +0/-0.17/+0.06 · +9/-0.17/+0.03 | jewelshop/art_pack_profi |

### blau

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#0376F0` `#0796EE` `#269BF8` `#43ADF9` `#5FBDF9` `#75CAFA` `#8AD4FA` `#A4E1FA` | -8/-0.02/+0.08 · +4/-0.12/+0.05 · -2/-0.12/+0.08 · -2/-0.11/+0.07 · -2/-0.09/+0.06 · -1/-0.08/+0.05 · -3/-0.10/+0.06 | support/art_tickets |
| `#2D3846` `#344662` `#3E5173` `#4C5E7C` `#627697` `#7789A9` `#8497B6` `#A1B2CC` | +3/+0.11/+0.05 · +2/-0.01/+0.04 · -1/-0.07/+0.05 · -0/-0.04/+0.09 · +1/-0.06/+0.08 · -1/-0.02/+0.05 · -1/-0.06/+0.11 | jewelshop/art_rank_platin |
| `#375863` `#3E678E` `#4579A3` `#598999` `#4F8DB7` `#67AAC3` | +14/+0.12/+0.06 · -2/+0.01/+0.06 · -12/-0.16/+0.06 · +9/+0.15/+0.01 · -8/-0.10/+0.10 | jewelshop_survival/f_kissenstapel |
| `#3F91E5` `#64A1E9` `#7BB1F0` `#96C0F3` `#A7D3F6` `#B8E0F7` `#C9E9F8` | +2/-0.15/+0.08 · -0/-0.08/+0.07 · +1/-0.10/+0.07 · -6/-0.06/+0.07 · -5/-0.07/+0.05 · -3/-0.07/+0.04 | jewelshop_survival/art_ov_pets |
| `#3D5D6D` `#4A6984` `#527785` `#557E99` `#6189AC` `#5F9DB3` `#6EAACE` | +8/-0.00/+0.05 · -12/-0.06/+0.04 · +7/+0.06/+0.03 · +4/-0.01/+0.05 · -12/+0.03/+0.05 · +7/-0.00/+0.06 | jewelshop_survival/p_wasserdrache |
| `#00387D` `#004391` `#02569A` `#105A7A` `#1B628C` `#036BAC` `#0479CA` `#0089B9` `#0383E0` `#029BE6` | -1/+0.00/+0.03 · -5/-0.01/+0.05 · -9/-0.12/+0.01 · +4/-0.06/+0.04 · +1/+0.17/+0.01 · +2/-0.00/+0.05 · -9/+0.02/+0.03 · +10/-0.01/+0.01 · -6/+0.01/+0.06 | pwarps/art_portal |

### lila

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#4D0098` `#5B00AF` `#6F01CA` `#7302E4` `#8901DE` `#9C02EE` `#B807F5` `#D51FFA` `#CF3AFA` `#F038FA` `#F550FA` `#FA71FB` | +1/+0.00/+0.03 · +2/-0.01/+0.04 · -3/-0.00/+0.02 · +7/+0.00/+0.02 · +2/-0.00/+0.03 · +6/-0.02/+0.05 · +5/-0.10/+0.09 · -3/-0.11/+0.06 · +10/+0.01/+0.03 · +1/-0.10/+0.06 · +1/-0.13/+0.08 | jewelshop_survival/art_ov_crate |
| `#3B1B57` `#482269` `#592A87` `#6A359A` `#7538B2` `#8641CC` `#9146E0` `#A04CF4` | +0/-0.01/+0.04 · -2/+0.01/+0.05 · +1/-0.03/+0.05 · -2/+0.03/+0.03 · -0/-0.00/+0.05 · -1/+0.01/+0.03 · +1/+0.00/+0.04 | jewelshop_survival/f_vorratskiste_19 |
| `#450694` `#4C06A6` `#590EBC` `#691FC7` `#7413E8` `#861FF6` | -0/+0.00/+0.02 · -0/-0.04/+0.04 · +1/-0.08/+0.06 · +1/+0.07/+0.00 · +1/-0.04/+0.06 | support/art_question |
| `#362841` `#4C3957` `#624A70` `#774D7C` `#775D81` `#846593` `#9B7CB0` `#BE90D5` `#D7ABE1` | +4/-0.04/+0.07 · -0/-0.01/+0.08 · +16/+0.04/+0.04 · -10/-0.10/+0.04 · -3/+0.03/+0.04 · -5/-0.02/+0.09 · +4/+0.03/+0.10 · +9/-0.08/+0.10 | jewelshop_survival/p_kristallini |
| `#322236` `#482E4F` `#5C3D62` `#73487B` `#8D5599` `#B06ABC` `#CD7CDD` `#E289EE` | -1/+0.05/+0.07 · +3/-0.04/+0.07 · +0/+0.04/+0.06 · -1/+0.03/+0.07 · +2/-0.01/+0.10 · -1/+0.00/+0.09 · +3/-0.01/+0.06 | jewelshop_survival/p_wunschbaer |
| `#6F28B8` `#8036CB` `#9950DD` `#B571EC` `#CA8EF3` `#DAA5F8` | +0/-0.05/+0.06 · +1/-0.10/+0.10 · +2/-0.12/+0.12 · +2/-0.11/+0.10 · +3/-0.08/+0.07 | jewelshop_survival/art_fp_egg |

### rosa

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#A72656` `#B92664` `#CE316E` `#E03C7D` `#F86298` `#F985B1` | -3/+0.02/+0.03 · +2/-0.03/+0.05 · -0/-0.03/+0.05 · +2/-0.13/+0.13 · -1/-0.14/+0.09 | playtime/art_gift_big |
| `#431B38` `#562646` `#743066` `#8A3781` `#9E3E7D` `#B24887` | +4/-0.04/+0.05 · -8/+0.03/+0.07 · -6/+0.01/+0.05 · +14/+0.01/+0.04 · +4/-0.01/+0.05 | jewelshop_survival/f_kleiderstange_1 |
| `#3E1A39` `#532241` `#682856` `#7E3361` `#973E7E` `#AB438F` | +14/+0.01/+0.05 · -5/+0.03/+0.05 · +6/-0.02/+0.06 · -6/-0.01/+0.07 · -1/+0.02/+0.04 | jewelshop_survival/f_kissenstapel |
| `#62164C` `#881463` `#AA087F` `#B70489` `#CD0593` `#E805AA` `#F409C0` `#FA09D4` `#FA1BE6` `#F622CD` | +2/+0.08/+0.05 · -3/+0.10/+0.03 · -0/+0.03/+0.01 · +2/-0.00/+0.03 · -1/+0.00/+0.04 · -3/-0.01/+0.03 · -4/+0.00/+0.02 · -4/-0.07/+0.05 · +6/-0.03/+0.00 | jewelshop/art_pass |
| `#5B1849` `#851762` `#A20C7C` `#B20585` `#CC0592` `#E705A9` `#F409C0` `#FA09D4` `#FA1BE6` `#F622CD` | +3/+0.09/+0.06 · -4/+0.10/+0.02 · +0/+0.05/+0.01 · +2/+0.00/+0.04 · -1/+0.00/+0.04 · -3/-0.01/+0.04 · -4/+0.00/+0.02 · -4/-0.07/+0.05 · +6/-0.03/+0.00 | battlepass/art_ticket |
| `#451D38` `#5E274E` `#6E2D61` `#83377B` `#923A7D` `#A6418D` `#BB4E95` `#C86199` | -2/+0.01/+0.06 · -6/+0.01/+0.04 · -6/-0.01/+0.06 · +8/+0.02/+0.03 · +0/+0.01/+0.05 · +6/-0.03/+0.06 · +6/-0.07/+0.06 | jewelshop_survival/f_bunte_stoffe |

### grau

| Rampe | Schritte (Farbton° / Sättigung / Helligkeit) | aus |
|---|---|---|
| `#393E44` `#4F535A` `#6A6F77` `#7C8187` `#898F96` `#A4A9AF` | +6/-0.04/+0.08 · -1/-0.01/+0.11 · -4/-0.03/+0.07 · -0/+0.01/+0.05 · +0/-0.02/+0.10 | quests/art_node_off |
| `#645B55` `#8A8079` `#A49890` `#C4B4A7` `#E1D1C6` `#F6E5D9` | +1/-0.03/+0.15 · -1/-0.00/+0.10 · +3/+0.03/+0.11 · -2/-0.03/+0.12 · +0/-0.00/+0.08 | jewelshop_survival/p_roboflitz |
| `#353640` `#5D606D` `#727274` `#828691` `#C9D5EF` `#DEE7F7` `#F1F4FA` | -6/-0.03/+0.16 · +11/-0.13/+0.07 · -16/+0.09/+0.08 · -3/+0.06/+0.31 · -3/-0.06/+0.07 · +2/-0.07/+0.05 | jewelshop_survival/f_barhocker |
| `#61636F` `#75757B` `#82858F` `#9899A2` `#ACAEB7` `#C4C4C7` | +9/-0.08/+0.07 · -14/+0.04/+0.06 · +8/-0.03/+0.08 · -5/-0.00/+0.08 · +11/-0.04/+0.09 | jewelshop_survival/p_mondzahn |
| `#404043` `#55565C` `#828489` `#9EA6B5` `#B0B6C1` `#D3D8DF` `#DCE5F4` | -9/+0.03/+0.09 · -9/-0.03/+0.18 · -4/+0.08/+0.13 · -0/-0.04/+0.06 · -4/-0.03/+0.13 · +2/+0.04/+0.05 | jewelshop_survival/f_marktstand_4 |
| `#3D434C` `#5D646D` `#737981` `#8D9399` `#A4A9AF` `#DDDEE0` | -2/-0.05/+0.13 · +0/-0.04/+0.08 · -4/-0.03/+0.10 · +3/-0.02/+0.09 · +7/-0.05/+0.21 | quests/art_lock |

