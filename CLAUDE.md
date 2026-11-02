# Hinweise für Claude (KubyCore-Plugins)

## Icons und Grafiken zeichnen

Wenn ein eigenes Icon oder eine Grafik gezeichnet werden soll, **immer zuerst die Icon-Datenbank der Referenzen nutzen**
(Wunsch des Besitzers):

- `tools/icon_db/STYLE.md`: gemessene Regeln aus ~800 Referenz-Bildern (Kontur, Rampen, Licht, Symmetrie, Hex-Verhalten)
  und die häufigsten Farbrampen je Farbfamilie mit Hex-Werten.
- `tools/icon_db/sheets/<satz>.png`: Kontaktbögen aller Referenz-Icons (4-fach) zum Anschauen vor dem Zeichnen.
- `tools/icon_db/refpal.py`: Rampen und Konturfarben aus den Referenzen holen, z. B. `refpal.ramp("gold", 5)`,
  `refpal.nearest_ramp("#4DA22F")`, `python3 tools/icon_db/refpal.py ramps blau`.
- Neu bauen nach neuen Referenzen: `python3 tools/icon_db/build_icon_db.py` (Referenz-Sätze in `SETS`).

Stil-Regeln (vom Besitzer, dazu die Messwerte in STYLE.md):

1. Der Stil ist so gefüllt wie in den Referenzen: jeder Pixel hat einen Sinn und einen Grund, da zu sein. Keine
   verwaisten Einzelpixel, kein Rauschen, keine weichen Zwischenfarben ohne Rolle.
2. Linien, Schatten und Farben wie in den Referenzen: 1 px Kontur (fast schwarz, leicht getönt), Farben aus Rampen mit
   3 bis 5 Stufen, Licht oben links, Lichter wärmer, Schatten kühler.
3. Auf Muster und wiederholte Patterns achten (Nieten, Bretter, Streifen gleichmäßig) und sie bewusst einmal brechen.
4. Symmetrie ist wichtig, ebenso das Brechen der Symmetrie (Form symmetrisch, Licht und ein Detail asymmetrisch).
5. Professionell wirken lassen: klare Silhouette, lesbar auch in 16 px, vor dem Abgeben mit den Referenzen auf einem
   Kontaktbogen vergleichen.
6. **Lieblingsstil des Besitzers** („DIESEN ARTSTYLE LIEBE ICH!!!“): die Skyblock-Generator-Grafiken
   (`tools/build_generator_art.py`, Bilder in `tools/assets/generators/`). Neue Icons, wo es passt, daran ausrichten:
   Tiefe durch Ebenen (hinten dunkel, Motiv in der Mitte, vorne Rahmen), Glühen aus dem Inneren, das die Teile nahe der
   Mitte einfärbt, durchscheinende Stäbe oder Teile vor dem Motiv, weicher Bodenschatten, Funken und Funkeln darüber,
   Nieten an den Ecken, gedeckte tiefe Farben statt flacher Sättigung. Details in `tools/icon_db/STYLE.md`.

## Arbeitsablauf

- Nach jeder Änderung: `TASKLISTE.md` aktualisieren, auf `main` committen und pushen, CI prüfen (Build plugins,
  Server-Test, SELFTEST mit 0 Fehlern), dann KubyPlugins.zip, KubyUI-Pack.zip, TASKLISTE.md und ein Übersichtsbild
  liefern.
- Pack neu bauen: `KUBY_VANILLA=<vanilla-assets> python3 tools/build_ui_pack.py resourcepack/KubyUI` (baut auch
  Item-Bilder und Kategorie-Titel), danach `python3 tools/check_fonts.py`.
