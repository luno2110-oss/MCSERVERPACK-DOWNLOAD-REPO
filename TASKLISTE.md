# KubyPlugins – Gesamtplan (Hauptchat)

Legende: [x] fertig · [~] teilweise · [ ] offen · (?) Annahme, bitte bestätigen
Stand: **v1.32** (ungetestet, nicht kompiliert)

---

## Ordnergruppe 1 – Casino (Refs: CASINO REF) – nur Survival
Prinzip: Keine GUI. Spiel läuft über physische Dinge am Tisch (Karten, Chips, Rad, Dealer).

- [x] 1.0 Neu nach den Referenz-Clips (Angeln.Casino.zip, C1–C7 fertig, Test im Spiel offen), Menüs pixelgenau wie im Clip (Dialog-Kit, siehe 5.30). Reihenfolge:
  - [x] C1 Casino-Grafik: Kästen (roter Kopf mit Fase, Filz, dunkel mit Goldrand und Eck-Glanz), Farbfelder Rot/Grün/Schwarz, kleine 16-px-Knöpfe (Reiter, Zähler, „Wählen“, „Aktiv“), große Titelschrift (dicke Pixel-Großbuchstaben mit Kontur und Gold-Verlauf, auch als Schrift `kuby:title` für Hologramme), Poker-Chips (7 Farben), Roulette-Rad, Jukebox, Schallplatte, Noten, Stern, Münze. Tooltips überall im Clip-Stil (schwarz, lila Linie, goldene Eck-Knoten). Roulette- und Musik-Dialog liegen in der Vorschau fast deckungsgleich auf dem Clip
  - [x] C2 Kasse wie im Clip (Test im Spiel offen): Truhe „» CASINO-KASSE“ ohne Füllglas, oben „Dein Stand“ (eigener Kopf, Chips und Coins), Kaufen +100/+1.000/+10.000 (Chip-Stapel), „Alles in Chips“, eigener Betrag (Namensschild, Eingabe-Dialog, mind. 10, auch „5k“), Auszahlen 100/1.000/10.000 (einzelne Chips), „Alle Chips auszahlen“, eigener Betrag. Tooltips wie im Clip (Titel in Small Caps, Zeilen mit ▶, „So viele Coins/Chips hast du nicht“ in Rot). Kassen-Figur per `/casino npc kasse` (Dorfbewohner hinter der Theke, Hologramm „KASSE“ mit Chips, „Coins ⇄ Chips“, „➜ Rechtsklick“), entfernen mit `/casino npc entfernen`. Auch der alte Kassentisch und `/casino wechseln` öffnen die Kasse. Beim Betreten: „CASINO ▶ Dein Stand … · Kasse am Wechsel-NPC“. Goldene Eck-Klammern als Slot-Markierung in allen Truhen
    - Balance: **1 Chip = 1 Coin** wie im Clip (vorher 100 Coins je Chip). Alte Chip-Stände, Jackpot, Poker-Treuhand, Statistik und die Einsätze der alten Spiele werden beim ersten Start einmalig umgerechnet. Steuer beim Auszahlen 5 % (aufgerundet auf ganze Coins, Farmpass: keine)
  - [x] C3 Roulette neu (Test im Spiel offen): Dialog wie im Clip (roter Kopf mit Rad, Einfach: Rot / Grün · 0 / Schwarz, Chip-Auswahl 10–1.000, eigener Betrag, alles, „Letzte Einsätze wiederholen“, „Alle zurücknehmen“, Liste „Deine Einsätze“). Neu dazu: Experte mit Zahlenraster 0–36, Kolonnen 2:1, Dutzende, 1–18/19–36, Gerade/Ungerade, Rot/Schwarz (Goldchip zeigt eigene Einsätze), Verlauf (letzte 36 Ergebnisse, Statistik, heiße/kalte Zahlen). Tisch aus Anzeigen: 37 Felder im abgerundeten Rechteck entlang der Tischkante (rot/grün erhöht, schwarz flach, gelbe Zahlen), Mitteltext „ROULETTE / Warte auf Einsätze.. / /roulette oder Croupier“, Chips auf dem Tisch, Hologramm „Einsätze / Name … Chips / Im Topf …“. Der erste Einsatz startet einen Countdown (20 s), dann läuft ein Licht über die Felder, bremst ab und bleibt auf dem Ergebnis stehen (blinkt). Croupier mit eigenem Skin (rosa Haare, Dienstmädchen-Kleid) und Hologramm „ROULETTE / Einsatz setzen / ➜ Rechtsklick“
    - Aufbau: auf die Mitte der Tischplatte schauen, `/casino roulette-tisch [Breite] [Tiefe]` (Standard 10 x 8, Breite quer zu deinem Blick, die Null liegt hinten), Croupier mit `/casino npc croupier`. Spieler: `/roulette`, Rechtsklick auf Tisch oder Croupier, `/casino setzen <Einsatz> <rot|schwarz|gruen|gerade|ungerade|niedrig|hoch|0-36>`
    - Balance: Mindesteinsatz 10, höchstens 100.000 Chips je Spieler und Runde, 1 % jedes Einsatzes in den Jackpot. Offene Einsätze kommen nach einem Absturz zurück
  - [x] C4 Blackjack neu (Test im Spiel offen): halbrunder Tisch aus Anzeigen wie im Clip (glatter grüner Filz, braune Polsterkante, Holzsockel, 5 gelbe Kartenfelder, 5 Hocker mit Goldgestell und grünem Polster), Dealer mit Croupier-Skin und Hologramm „Dealer / Rechtsklick · Spielen“, Chip-Rack und Kartenschlitten. Freie Plätze: „Freier Platz / Rechtsklick zum Hinsetzen“. Sitzend erscheint ein Chip-Rack (10–5.000) zum Anklicken, die Chips stapeln sich auf deinem Feld mit Summe; der erste Einsatz startet den Countdown („Runde beginnt in 5 s“). Karten (52 eigene Grafiken + Rückseite) fallen einzeln auf den Tisch, Summe darunter, Dealer eine Karte verdeckt. Wer dran ist, bekommt die Felder Karte / Halten / Doppeln / Teilen und einen grünen Zeitbalken (15 s, dann Halten). Dealer zieht bis 17, Blackjack 3:2, Teilen einmal (Asse je eine Karte). Ergebnis über dem Feld („Gewonnen / 20 gegen 19 / zurück 200 Chips +100“), Chips fliegen zum Spieler oder zur Bank. Aufstehen mit Schleichen (in der Runde: automatisch Halten). Offene Einsätze kommen nach einem Absturz zurück
    - Aufbau: an die Mitte der geraden Tischkante (Dealer-Seite) stellen, zu den Spielern schauen, `/casino blackjack tisch [Radius]` (Standard 3), beliebig viele Tische („Blackjack-Tisch 1, 2 …“), `/casino blackjack entfernen`, `/casino blackjack liste`. Einstellungen `casino.blackjack.*` (min-bet 10, max-bet 10.000, bet-seconds 8, turn-seconds 15)
  - [x] C5 Slots neu (Test im Spiel offen): Automat aus Anzeigen wie im Clip: drei weiße Walzen als Trommeln (fünf Flächen, oben/Mitte/unten mit Symbol), dunkle Stege, goldene Leisten, orange Pfeile an der Gewinnlinie, Hebel auf Steinsockel am linken Pult-Ende (in Ruhe nach hinten gekippt, gezogen zum Spieler), schwarzer Einsatzknopf am rechten Ende (vom Hocker aus bleibt jedes Symbol frei), flache Anzeige „EINSATZ ▸10◂ 50 100 500 1k 5k“ auf dem Pult, Holzschild vorn am Pult (Gewinnplan), weißer Hocker. Hologramm „SLOTS“ mit Chips, Jackpot, Einsatz 10 bis 5.000, „➜ Hebel ziehen“, „Gewinnplan: Schleichen + Schild anklicken“. Zehn Symbole (Kirsche, Zitrone, Banane, Glocke, Stern, Kleeblatt, Chip, Münze, Diamant, Sieben), beim Drehen verschwommen, Walzen halten nacheinander an. Gewinne: drei gleiche 8- bis 80-fach, zwei Kirschen 4-fach, eine Kirsche 2-fach, drei Siebener = eigener Slots-Jackpot (Start 20.000, 2 % jedes Drehs fließen hinein, voller Einsatz holt den ganzen Topf, sonst anteilig, mindestens 100-fach). Rückfluss ohne Jackpot etwa 95 %. Gewinnplan als Dialog (Klick auf Schild oder Anzeige). Redstone-Lampen im Gehäuse über den Walzen leuchten beim Drehen (Leuchtkasten)
    - Aufbau: Pult (1 Block hoch, 3 breit) vor eine Wand bauen, dort hinstellen, wo der Hocker hin soll, auf die Wand direkt über der Pult-Mitte schauen, `/casino slots automat [Breite]` (Standard 2,7; Mitte rastet auf halbe Blöcke ein, Pult-Tiefe wird erkannt). `/casino slots entfernen`, `/casino slots jackpot [Betrag]`. Einstellungen `casino.slots.jackpot-start` und `casino.slots.jackpot-share`
  - [x] 3D-Prüfung aller Tische mit einem Nachbau der Client-Darstellung (`tools/preview3d`: echte Plugin-Klassen gegen nachgebaute Bukkit-Teile, Bild wie im Client) (Item-Anzeigen werden vom Client um 180° gedreht). Dabei behoben: Karten, Chips und Walzen-Symbole lagen mit der Rückseite nach oben und waren gespiegelt; Blackjack-Rack und Aktionsfelder jetzt wie im Clip von links aufsteigend (10 … 5.000, Karte · Halten · Doppeln · Teilen); Dealer-Karten näher am Dealer, Chip-Rack des Dealers mittig vor ihm, Kartenschlitten rechts; Aufdruck „BLACKJACK ZAHLT 3 ZU 2 / Dealer zieht bis 16, steht ab 17“ auf dem Filz; Handwert rechts neben den Karten; Sitzhöhe auf Hocker und Polster korrigiert. Roulette: rundere Ecken, Felder in Kurven schmaler (keine Überlappung), Zahlen passen auch bei kleinen Tischen aufs Feld
  - [x] C6 Crash neu (Test im Spiel offen): Bildschirm aus Anzeigen vor einer gebauten dunklen Wand wie im Clip: „BEREIT“ in Titelschrift mit „Warte auf Einsätze..“ bzw. „Einsätze — 9s“, während der Runde „1,45x“ grün mit „Läuft“, Kurve aus Leisten (türkis, beim Crash rot) mit dem Wert an der Spitze, nach dem Crash „1,48x“ rot und „CRASH — 1 Spieler verloren 50 Chips“. Darüber „CRASH“ im Verlauf gelb→rot, daneben „LETZTE RUNDEN“ (unter 2x rot, ab 2x grün). Schilder unter dem Bildschirm (leuchtende Schrift, gewachst): „CRASH / Warte auf / Einsätze“, „CRASH / Einsatz / 9s“, „CASHOUT / Klick oder Schlag / 1,44x / auszahlen“, „CRASH / bei / 1,48x“. Geladener Creeper als Croupier mit Hologramm „CRASH / Einsatz setzen / ➜ Rechtsklick“ öffnet die Truhe „» CRASH“ (Info-Kopf mit Sonnenbrille, Chips 10–5.000, letzten Einsatz wiederholen, eigener Einsatz bis 100K, Auszahlen). Info-Hologramm „CRASH INFO“ mit den fünf Regeln. Aussteigen mit Schleichen + Klick, Klick oder Schlag aufs Schild, Chat-Knopf [AUSZAHLEN] oder `/cashout`. Chat wie im Clip („Einsatz 50 Chips gesetzt…“, „Runde läuft! [AUSZAHLEN]“, „Crash bei 1,48x — Einsatz -50 Chips weg.“). Balance: 3 % Hausvorteil (Rückfluss 97 %), Höchstwert 50x mit automatischer Auszahlung, Einsätze 10 bis 100.000 je Runde und mehrfach aufstockbar. Offene Einsätze kommen nach einem Absturz zurück
    - Aufbau: dunkle Bildfläche bauen, auf ihre Mitte schauen, `/casino crash bildschirm [Breite] [Höhe]` (Standard 6 x 3). Titel und „Letzte Runden“ verschieben: draufschauen, `/casino crash titel` bzw. `runden`. Schilder: auf ein Schild schauen, `/casino crash schild` (beliebig viele; `schild entfernen`). `/casino npc crash` (Creeper), `/casino crash info` (Info an die Wand, auf die du schaust), `/casino crash entfernen`. Einstellungen `casino.crash.*` (min-bet, max-bet, wait-seconds, rate, max-multiplier, house-edge)
  - [x] C7 Rest des Casinos (Test im Spiel offen):
    - [x] Musik wie im Clip: drei eigene Notenblock-Songs (Brisk Casino Swing 2:58, Mid-Night Lounge 2:55, Late-night Lounge 3:02; Bass, Klavier/E-Piano, Gitarre, Schlagzeug, Melodie, Soli), laufen im Casino für jeden einzeln (Kategorie Jukebox/Notenblöcke). Dialog wie im Clip: „Alle Songs“ (wechseln sich ab, „Nächster Song“), einzelner Song in Dauerschleife, „Musik aus“; „Läuft gerade“ mit Länge. Actionbar „♪ Song · /music“, Chat „Casino-Musik: … läuft in Dauerschleife“. Songs entstehen mit `tools/build_songs.py`
    - [x] DJ Mia (eigener Skin: blond, graue Strickjacke, Jeans mit Rissen), Hologramm „Mia / Rechtsklick · Musik an/aus“, öffnet die Musik: `/casino npc mia`
    - [x] Jackpot-Hologramm wie im Clip: „JACKPOT“ (rot→gold) zwischen Chips, Betrag groß in Gelb, „Ein Topf, ein Gewinner“, wächst mit jedem Einsatz, Ziehung „Samstag 18:00 · noch 22 Std. 46 Min.“, Teilnehmerzahl, letzter Gewinner mit Betrag, darunter für jeden persönlich „✦ Du bist dabei ✓ ✦“ bzw. „Spiel eine Runde, dann bist du dabei“. Beim Vorbeigehen einmal „JACKPOT ▶ Ein Teil von jedem Einsatz wandert in den Tresor…“. Der Jackpot wird jetzt in Chips ausgezahlt (sofort, auch offline), Ziehung Standard Samstag 18:00. `/casino holo jackpot`
    - [x] Ranking-Tafel an der Wand: „CASINO RANKING“, „Top Casino-Spieler“, Platz 1–3 mit Gold/Silber/Bronze, Plus in Grün, „Mehr Statistiken: /casino stats“. `/casino holo ranking` (auf die Wand schauen)
    - [x] Eingang: „CASINO“ (pink→gelb), „Willkommen! schön, dass du da bist“, Coins gegen Chips, „Viel Spaß und viel Glück“. `/casino holo eingang`
    - [x] Losbude: Figur mit Regenbogen-Titel „LOSBUDE“ zwischen Kleeblättern und den Zeilen aus dem Clip (`/casino npc losbude`). Truhe „» LOSBUDE“ mit Bronze- (100), Silber- (1.000) und Gold-Los (10.000), Gewinnplan; Rubbellos mit neun Feldern zum Freirubbeln oder „Alles aufdecken“, drei gleiche Symbole gewinnen (Kirsche 1x, Zitrone 2x, Glocke 5x, Stern 10x, Sieben 50x), Gewinnchance etwa 1 zu 3, Rückfluss etwa 88 %. Ergebnis wird beim Kauf gutgeschrieben (nichts geht verloren, auch beim Schließen)
    - [x] Scoreboard im Casino wie im Clip: eigene Sidebar, sobald man in Survival in der Casino-Region steht: rot-oranger Rahmen mit Funkeln, Logo „KUBY CASINO“ mit Spielkarten-Fächer, Chips und Münzstapeln, unten Slot-Automat und Roulette-Rad. Zeilen: Rang, Name (Namensschild), Coins, Chips, Letzter Gewinn (+ grün / - rot / — ), Im Casino (Anzahl)
    - [x] Casino-Hilfe auf dem Kit (`/casino regeln [spiel]`, `/casino stats`): Kopf im Casino-Stil mit Chip-Icon, Titelschrift und „Hinreisen“, Reiter über die ganze Breite für alle Seiten (Casino, Blackjack, Roulette, Slots, Crash, Losbude, Rangliste), Kästen „So geht’s“ mit ▶-Aufzählung und „Befehle“. Neue Seite Losbude, Texte zu Slots, Crash, Musik und Jackpot auf den neuen Stand gebracht. Rangliste „Am meisten Plus“ mit Platz 1–3 in Gold/Silber/Bronze, Plus grün, Minus rot und dem eigenen Platz. Ohne Grafik-Pack weiter die einfache Ansicht
- [~] 1.1 Grundlagen (Code fertig, Build und Test offen)
  - [x] Casino-Bereich als Region: Region mit ID `casino` anlegen, Schutz über das Regionssystem
  - [x] Chips als eigene Währung, Umtausch $ ↔ Chips am Kassentisch (`/casino kaufen|verkaufen`, Kassentisch per `/casino kasse`)
  - [~] Jackpot-Anzeige: schwebender Text über dem Kassentisch fertig, Ticker (Animation) offen. Spiele füllen den Jackpot (1 % der Einsätze), drei Siebener bei Slots zahlen ihn aus
- [~] 1.2 Spiele
  - [~] Blackjack (Build grün, Test im Spiel offen): Rechtsklick auf den Tisch startet, Karten als schwebende Texte, Ziehen und Halten per `/casino hit` und `/casino stand`, Weggehen oder Ausloggen beendet die Runde. Offen: Dealer-NPC, Chips als Anzeige auf dem Tisch
  - [~] Roulette (Build grün, Test im Spiel offen): Tisch per `/casino roulette-tisch`, Einsatz per `/casino setzen <Einsatz> <rot|schwarz|0-36>`, Rad dreht sich nach 20 Sekunden, Anzeige "Warte auf Einsätze". Offen: echtes Rad aus Blöcken mit Drehanimation
  - [~] Slots (Test im Spiel offen): Automat per `/casino automat` (am besten ein Hebel), Rechtsklick dreht, drei Walzen als Anzeigen halten nacheinander an, drei Siebener gewinnen den Jackpot (Rückfluss ohne Jackpot etwa 92 %)
  - [~] Crash (Test im Spiel offen): Automat per `/casino crash`, Anzeige "BEREIT", Rechtsklick steigt ein (10 Chips), nach 10 Sekunden steigt der Multiplikator, Rechtsklick steigt aus. Crash-Punkt steht beim Start fest (1 % Hausvorteil, max. 50x)
- [~] 1.3 Anleitung (Test im Spiel offen, eigene Grafik über Pack KubyUI: Rahmen, Titelleiste, Buttons, Icons): Casino-Hilfe als Dialog-Fenster nach der Referenz (Titel, "So geht's", Befehle, Buttons je Spiel, "Hinreisen"). Öffnen per `/casino regeln [spiel]` oder `/casino hilfe`, Rechtsklick auf Kasse oder Roulette-Tisch, Schleichen + Rechtsklick auf einen Spieltisch. Werte aus der config.yml. Grafiken im Pack KubyUI (resourcepack/KubyUI, Generator tools/build_ui_pack.py), wird beim Join automatisch mitgeschickt
- [~] 1.4 Sound und Anzeige (Test im Spiel offen): bei jedem Ergebnis Sound für den Spieler und aufsteigender Text über dem Tisch ("Name +12 Chips"), eigener Klang für Gewinn, großen Gewinn, Unentschieden und Verlust. Jackpot: Text, Totem-Partikel und Sound für alle im Umkreis von 30 Blöcken. Offen: Jackpot-Ticker
- [~] 1.5 Admin (Test im Spiel offen): Casino-Admin-Fenster per `/casino admin` oder Admin-Panel (Casino-Button). Status und Aufbau der Tische, Schieberegler für Chip-Preis, Einsätze, Roulette-Limit und -Wartezeit, Crash-Höchstwert und Jackpot-Anteil, Buttons zum Setzen der Tische, Casino öffnen/schließen (Notschalter). Auszahlung je Spiel (eingesetzt, ausgezahlt, Casino-Plus), Protokoll jeder Runde in casino-log.txt
- [~] 1.6 Aus der Casino-Referenz (entschieden: wie Referenz, Jackpot um 19 Uhr)
  - [~] Jackpot (Test im Spiel offen): jeden Samstag 19 Uhr Verlosung unter allen, die seit der letzten Ziehung gespielt haben. Gewinn wird gutgeschrieben, sobald der Gewinner in Survival ist. Nachziehen nach Server-Ausfall. Admin: "Jackpot jetzt ziehen". Drei Siebener knacken den Jackpot nicht mehr (per Config `casino.jackpot.seven-wins` wieder einschaltbar)
  - [~] Casino-Musik (Test im Spiel offen): startet beim Betreten der Region `casino`, stoppt beim Verlassen, läuft in Schleife. `/music` öffnet die Songauswahl (12 Schallplatten, „Musik aus“), nur im Casino. Wahl bleibt gespeichert
  - [~] VIP-Lounge (Test im Spiel offen): Region `casino-vip`, Zutritt mit LuckPerms-Gruppe legend oder Top 10 mit Plus (auch Teleport wird geblockt). Barkeeper per `/casino barkeeper` (entfernen: `/casino barkeeper entfernen`), geben einmal am Tag 500 Chips
  - [~] Rangliste „Am meisten Plus“ (Test im Spiel offen): `/casino stats` oder Button in der Hilfe, Top 10 und eigener Platz, zählt Gewinne minus Einsätze aller Spiele
  - [~] Poker (Test im Spiel offen): Texas Hold'em, bis 6 Spieler, Blinds 10/20, Buy-in 40 bis 200 BB per `/poker sitzen <Chips>`, `/poker aufstehen` nimmt den Stack mit. Aktionen als Klick-Buttons im Chat, 30 s Zeit pro Zug, eigene Karten in der Aktionsleiste, Tisch-Anzeige mit Board, Pot und Stacks. Side-Pots, Hand-Auswertung getestet (17 Fälle). Stacks laufend gesichert, nach Absturz zurück. Tisch per `/casino poker-tisch` oder Admin-Fenster

## Ordnergruppe 2 – Angeln (Refs: ANGEL REF) – nur Survival
> Hinweis: Der Angel- und Claim-Code fehlte im hochgeladenen Stand (nur Config-Reste). Neu gebaut als Addon KubySurvival 1.0.0.
- [x] 2.0 Neu nach den Referenz-Clips (Angeln.Casino.zip), Menüs pixelgenau nachgebaut (Dialog-Kit, siehe 5.30). Test im Spiel offen
- [~] 2.1 Angel-Zonen (Test offen): `/kp angel pos1|pos2`, `/kp angel zone erstellen <id> [Name]` (deine Position = Teleport-Punkt „Zur Angel-Zone“), `/kp angel tp`, `/kp angel holo` stellt das Hologramm „ANGELZONE“ (Blau-Verlauf, Zeilen mit Symbolen) auf. In der Zone +10 % Glück, -10 % Wartezeit, Mythen-Fische und Schätze nur dort. Angeln geht überall in Survival
- [~] 2.2 Fangsystem (Test offen): 48 Arten auf 6 Gewässern (Flüsse & Seen, Ozean, Warme, Kalte, Sumpf & Höhlen, Mythen & Zone), Gewässer nach Biom am Schwimmer (Höhlenwasser = Sumpf & Höhlen), 6 Seltenheiten (Gewöhnlich bis Mythisch), 1 bis 5 Sterne nach Gewicht, manche Arten nachts doppelt so oft. Glück (Level, Glückshaken, Köder, Zone, Angler-Job) schiebt zu den seltenen. Eigene Grafik für alle 48 Fische (Items stapeln sich nach Art und Sternen, nicht essbar, nicht zu braten)
- [~] 2.3 Angel-Punkte, Level 1 bis 100 (Level L ab 35·L·(L-1) Punkten) und Ränge (Anfänger, Hobby-Angler, Angler, Profi, Meisterangler, Legende); Aufstieg: Titel „LEVEL 8“, Coins, alle 10 Level ein Token
- [~] 2.4 Fang wie in der Referenz: Aktionsleiste „Du angelst gerade… · Angel-Zone +10 %“ und „Es zieht! Jetzt einholen!“, großer Titel „SELTEN!“ in Seltenheitsfarbe mit Sternen und Gewicht, Fisch springt aus dem Wasser vor dich, kreiselt und landet im Inventar, Punkte in der Aktionsleiste, Rekord im Chat, Legendär und Mythisch werden angesagt, Schätze (Coins, Köder, Token, Items)
- [~] 2.5 Fischbuch (`/angeln buch`): 6 Seiten mit je 8 Kacheln, Anzahl und Rekord, unbekannte Arten als dunkler Umriss, Detailseite (wo sie beißen, Gewicht, Preis, Server-Rekord). Volle Seite: 3.000 Coins + 2 Tokens
- [~] 2.6 Rute & Upgrades (`/angeln rute`): Glückshaken (+4 % Glück, 5 Stufen), Schnellspule (-4 % Wartezeit, 5), Schatzsinn (+0,5 % Schatz, 3), Großfang-Rolle (Extra-Würfe aufs Gewicht, 3); Auto-Verkauf für 150 Tokens; Doppelfang (5/10/15 %) im Token-Shop. Kaufen mit zwei Klicks
- [~] 2.7 Köder (`/angeln koeder`): Regenwurm, Glitzer-, Nacht- und Goldköder, kaufen (10 Stück) oder basteln (Zutaten aus dem Inventar), einer aktiv, wird je Fang verbraucht
- [~] 2.8 Fischhändler (`/angeln haendler`, NPC per `/kp angel haendler`): alles, nur die kleinen, nur die seltenen oder nur die Fische des Tages verkaufen; Preise je Seltenheit und Sterne, Fische des Tages +50 %, jeden Tag eine Goldene Stunde +50 %, Angler-Job +1 % je Level. Balance: pro Stunde 1.000 Coins zum vollen Preis, darüber 20 % (`angeln.dealer.*`)
- [~] 2.9 Tagesaufträge (3 je Tag, Geld und Punkte, Bonus für alle drei: 1 Token + 3 Köder) und Bestenliste (Woche, Ränge, Fänge, Rekorde; alle 2 Minuten neu). Turnier wie bisher: `/kp angel turnier start <Minuten>`
- [~] 2.10 Hauptmenü `/angeln` wie in der Referenz: Profil (Level-Balken, Fänge, Woche, Fischbuch, Verdient, Serie, Schätze, Angler-Job), Heute (Fische des Tages, Goldene Stunde), sechs Kacheln, „Zur Angel-Zone“, „So funktioniert's“. Alte Daten (fishing.yml) werden übernommen

## Ordnergruppe 3 – Juwelenshop (Refs: JUWELENSHOP SURVIVAL, JUWELENSHOP SMP)
- [x] 3.1 Survival: Ränge, Keys, Juwelen mit Inhalt (v1.37)
- [x] 3.2 SMP: Ränge, Keys, Juwelen mit Inhalt (v1.37)
- [x] 3.3 Battlepass und Vorteilspakete als Barriere, im Editor aktivierbar (v1.37)
- [x] 3.4 Editor: /kp shop editor survival|smp (v1.37)

## Ordnergruppe 4 – Features Survival (Refs: Release „Referenzen“, Hilfe-Seiten je Feature) – nur Survival, Schwerpunkt
Reihenfolge wie das Hilfe-Karussell der Referenz. Jede Seite: Titel, Untertitel, „So geht's“, Befehle, Buttons (zurück, Aktion, weiter), eigene Farbe.
- [~] 4.0 Survival-Hilfe `/hilfe [seite]` (Build offen, Test im Spiel offen): Dialog-Karussell mit 31 Seiten nach der Referenz, 7 Farbthemen (grün, gold, grau, orange, blau, rot, pink), Icons je Seite, Aktions-Button ruft den passenden Befehl (fehlende Features: „kommt bald“). `/befehle` bleibt die Chat-Übersicht
- [~] 4.1 Regeln (Build offen): `/regeln` (12 Paragraphen zum Anklicken, Texte in config rules.list), `/regeln <Nummer>`, `/report <Spieler> [Grund]` (an Team mit kuby.report.see, reports.log, 60 s Sperre)
- [~] 4.2 Einstieg (Build offen): `/bauwelt` (Punkt per `/kp bauwelt setzen`, sonst Zufallsteleport), Brunnen = Region `bauwelt-brunnen`, `/tutorial` mit Stationen (`/kp tutorial add <Titel>|<Text>`), Belohnung beim ersten Abschluss (500 Coins, 3 Tokens)
- [x] 4.3 Grundstücke: Claim-Axt (Linksklick Ecke 1, Rechtsklick Ecke 2, `/claim ok`), Kosten = Fläche (10×10 = 100), Erweitern/Verkleinern: Linksklick auf eine Ecke des eigenen Grundstücks, Rechtsklick auf die neue Position; `/claim` öffnet ein Menü (freie Blöcke, eigene Grundstücke mit Rand anzeigen, vertraute Spieler entfernen, Rechte für Fremde: Türen, Knöpfe/Hebel/Druckplatten, Kisten, Tiere); `/claim axt|ok|zeigen|info|liste|trust|untrust|löschen|hilfe` (alte Befehle gehen weiter)
- [x] 4.4 Jobs: 8 Berufe (Holzfäller, Miner, Gräber, Farmer, Jäger, Angler, Züchter, Verzauberer), Level 1–100, Stundenlimit für Coins (300 + 15 je Level) und Lohn +1 % je Level, Wechsel jederzeit, danach 30 Min. Sperre (erste Wahl frei), `/job info|waehlen|limit|auftrag|hilfe` mit Tab-Ergänzung, Wochenauftrag (Job-XP im Job zu Wochenbeginn, 3.000 Coins + 3 Tokens), `/tokenshop`; fallender Sand/Kies behält die Markierung „selbst gesetzt“
- [x] 4.5 Quests (KubySurvival, `quests.yml` frei anpassbar, `/quest reload` für Admins): 3 Tages-Quests aus 17, 3 Wochen-Quests aus 10 (je Spieler zufällig), Geschichte mit 15 Kapiteln nacheinander, 7 Meilensteine mit Stufen, Team-Quest pro Woche für Survival-Teams (jeder holt einmal ab), 7 geheime Quests; Abholen im Menü oder `/quest abholen` (Vergessenes wird am Ende des Tages/der Woche ausgezahlt); Serie: erste Tages-Quest am Tag bringt 25 Coins je Serientag (bis 30) plus Tokens bei 3/7/14/30/60/100 Tagen; verfolgte Quest (sonst aktuelles Kapitel) als grüne Leiste oben unter der Survival-Leiste; `/quest daily|weekly|abholen|streak|top|aus|hilfe`; Belohnungen: Coins, Tokens, Spieler-XP; selbst gesetzte Blöcke und Spawner-Mobs zählen nicht
- [x] 4.6 Nebenquests: 5 Figuren (Fischer Fritz, Bäuerin Berta, Schmied Bruno, Wächterin Wanda, Holzfäller Hans) mit je 4–5 Aufgaben nacheinander (`sidequests.yml`, frei anpassbar); Typen wie bei Quests plus Gegenstände bringen und Botengänge zu anderen Figuren; eine laufende Nebenquest, Belohnung bei der Figur; Wegweisung mit Pfeil und Entfernung in der Aktionsleiste; laufende Nebenquest steht oben in der Quest-Leiste; `/sidequests [nav|cancel]`, Menü; Admin: `/kp figur setzen|entfernen|liste|reload`
- [x] 4.7 Farmwelt: eigene Oberwelt `farmwelt` (Weltgrenze 6.000, einstellbar), `/farmwelt` bringt an einen zufälligen sicheren Ort, `/farmwelt info` zeigt nächsten Neustart, Größe und Spieler; jeden Montag 4 Uhr Neustart mit neuem Seed (Warnungen 60/30/10/5/1 Min., Spieler landen am Survival-Spawn, auch wer offline drin war); keine Grundstücke, keine Homes (neuer Kern-Haken `homes().registerBlock`), keine Portale, Tod ohne Bett → Survival-Spawn; Survival-Leiste zeigt „Farmwelt“; Admin: `/farmwelt reset`
- [x] 4.8 Geld & Markt: `/money` (auch `/geld`, `/bal`) zeigt Konto des Modus und Juwelen, in Survival mit Links zu Job, Quests, Markt, Abholbox; `/markt`, `/markt sell <Preis>`, `/abholbox`, `/trade` sind pro Modus getrennt. Dabei behoben: Markt-Verkäufe, Order-Lieferungen und vom Admin entfernte Angebote landen jetzt im richtigen Modus und gehen offline nicht mehr verloren (neu: `modes().depositIn/abholboxIn`); `/trade` nur noch im selben Modus und nicht in der Lobby; `/pay` bucht im Modus des Senders; Skyblock-Stände in profiles.yml wurden beim Neustart nicht geladen (behoben); Quest-Auszahlungen gehen sicher aufs Survival-Konto
- [x] 4.9 Läden & Warps: Spieler-Warps `/sw` (Menü nach Beliebtheit, Klick reist hin, `/sw <Name>`), `/sw create <Name>` an der eigenen Position in der Survival-Welt (1./2./3. Warp: 50.000 / 150.000 / 500.000 Coins, `shops.warp-prices`), `/sw manage` (verschieben, Symbol aus der Hand, löschen); Kistenläden: Schild mit [Shop] an Kiste/Fass (oder obendrauf) auf Grundstücken mit Baurecht, Einrichten per Klick aufs Schild (Ware aus der Hand, 1–64 Stück, Verkaufs- und Ankaufspreis per Chat-Eingabe), Ladenkasse (Kunden zahlen hinein, Ankauf wird daraus bezahlt, Abheben/Einzahlen), nur der Besitzer öffnet/abbaut Kiste und Schild (auch Doppelkisten), Trichter ziehen nichts heraus, Schild gewachst; `/chestshop` (Übersicht mit Lager und Kasse), `/chestshop help`
- [x] 4.10 Tokens: 1 pro Stunde aktiv in Survival (5 Min. ohne Bewegung zählt nicht), Tagesbonus beim ersten Spielen am Tag, Extra bei 3/7/14/30 Tagen in Folge, 1 pro Vote (neuer Kern-Haken `votes().registerHook`), weiter auch für Quests und Aufstiege; nicht kaufbar/tauschbar; `/tokens`; Token-Shop neu: Claim-Blöcke (100/500/2.000), Rucksack (Reihe 1–6, `/backpack`, `/backpack item`, keine Shulker/Rucksäcke hinein), Job-Booster 30/60 Min. (+50 % XP, Coins und Stundenlimit), Jobwechsel sofort, besondere Verzauberungen (eine Stufe über Maximum: Effizienz VI, Haltbarkeit IV, Glück IV, Plünderung IV, Schärfe VI, Schutz V, Stärke VI, Köder IV, Glück des Meeres IV), Bauhelfer (Licht-Blöcke, Gerüst, Seelaternen, Glas) und Sonstiges; alles in der Config unter `tokens:`
- [x] 4.11 Level: Spieler-XP aus Online-Zeit (5 XP pro aktiver Minute in Survival, `level.xp-per-minute`), allen Job-XP und Quest-Belohnungen; Level L braucht 150·L·(L−1) XP, jeder Aufstieg bringt Tokens; `/level` (Fortschritt), `/level <Spieler>`, `/level top` (Top 10)
- [x] 4.12 Farmpass: Vergabe durch den Store per `/farmpass give <Spieler> [Tage]` (Konsole/Admin), `/farmpass take`; Vorteile solange aktiv: Fliegen am Spawn, in Fly-Regionen (Casino) und auf eigenen/Freundes-Grundstücken (`/farmpass fly`, kein Fallschaden beim Verlassen), Nebenjob (`/job nebenjob <Beruf>`), keine Markt-/Order-Steuer, keine Casino-Steuer (neu: `casino.cashout-tax-percent` 5 %), +2 Homes, 15 Markt-Plätze, `/werkbank` überall, Wochenkiste (Coins, Tokens, Items), 4 Pass-Haustiere, ☘ vor dem Namen in Tab und Chat; pro bezahltem Monat +250 Claim-Blöcke und ein Monats-Haustier für immer (`/farmpass pet`); Menü mit Status, Vorteilen, Store-Link (`farmpass.store-url`); Kern-Haken `registerPerk`/`hasPerk`, `registerNamePrefix`. Offen: Grab 60 Min. (kommt mit 4.24), Haustiere selbst (4.13), `/ec` ist schon für alle überall offen
- [x] 4.13 Haustiere: 23 Haustiere in `pets.yml` (7 kaufbar im Menü für Coins, 4 Farmpass, 8 Farmpass-Monat, 2 Kisten, 2 Rang), folgen dem Besitzer (teleportieren nach, auch in andere Welten), unverwundbar, nicht angreifbar, nicht anleinbar, keine Zucht, verschwinden beim Ausloggen/Moduswechsel; reitbar: Esel, Pferd, Kamel, Skelettpferd (Rechtsklick oder `/pet ride`, Sattel-Inventar gesperrt); Menü `/pet`: rufen/wegschicken, Lieblinge (★, oben), umbenennen (Shift-Klick), gesperrte zeigen ihre Quelle oder sind kaufbar; `/pet summon <Name>|dismiss|ride`; Kisten/Ränge: `/pet give <Spieler> <id>` oder Permission `kuby.pet.<id>`
- [x] 4.14 Teams: Team-Level 1–10 aus Team-XP (1 pro aktiver Minute in Survival, doppelt wenn Teamkollegen online), je Level +1 Platz, Team-Homes (1 + eins je 2 Level, max. 5; `/team home|sethome|delhome [Name]`, `/team homes`, gesperrte Orte wie bei Homes), Team-Lager (`/team lager`, 1–6 Reihen je nach Level, ein gemeinsames Inventar), Kasse mit Abheben für den Chef (`/team bank abheben <Betrag>`), `/team kick`, `/team info`; beim Auflösen gehen Kasse und Lager an den Chef (Abholbox), nicht mehr verloren; Menü zeigt Level, XP, Lager, Homes; Chat `/tc`, Bündnisse wie bisher
- [x] 4.15 Homes in Survival: 1 Home, +1 je Rang ab Platin (`homes.survival.ranks`, Permission `kuby.rank.<name>`), +1 für Discord (/verify), +2 Farmpass; kein Kauf von Plätzen in Survival; gesperrt am Survival-Spawn (80 Blöcke), im Casino (`homes.survival.blocked-regions`) und in der Farmwelt (gilt auch für Team-Homes und Warps); Menü zeigt die Survival-Regeln; SMP/Skyblock unverändert. Dabei behoben: `homes:` stand doppelt in der Config, dadurch galten Start-Homes und Teleport-Wartezeit nicht
- [x] 4.16 Voten: Serie (Tage in Folge mit mindestens einem Vote), Extra-Coins beim ersten Vote des Tages ab 10 Tagen 250, ab 25 Tagen 500, ab 50 Tagen 1.000 (`vote.streak-bonus`); Monatswertung, am Monatsende Juwelen für Platz 1–3 (50/30/20, `vote.monthly-prizes`) mit Ansage; `/vote` zeigt Links, Votes gesamt/Monat, Serie, Stufen und die Top 5 des Monats; dazu weiterhin Juwelen, Coins, 1 Token (Survival) pro Vote
- [x] 4.17 Einstellungen: `/settings` (Private Nachrichten – Freunde/Admins immer, Freundesanfragen, Teleport-Anfragen – Freunde immer, Sounds, Scoreboard, Info-Leiste) und `/bossbar` (Geld, Job, Ort, verfolgte Quest einzeln an/aus); gilt in allen Modi, gespeichert in settings.yml
- [ ] 4.18 Heiraten: `/marry propose|accept|church|tp|divorce`, Priester in der Kirche, Herz im Chat, `/bank` Ehe-Konto (große Beträge nur an der Bank am Spawn, auch Konten mit Freunden), Scheidung halbiert das Konto, Sperre danach
- [ ] 4.19 Juwelen & Shop: verschenken `/juwelenshop gift`, Kopf-Datenbank `/heads [Suchbegriff]` (1.000 Coins je Kopf) (Shop vorhanden)
- [ ] 4.20 Tod & Gräber: Grabstein mit Items und XP, 30 Min. nur Besitzer (Farmpass 60), Rechtsklick legt alles ins Inventar, danach zerfällt er, `/grab`
- [ ] 4.21 Möbel & Sitzen: Möbel aus dem Juwelenshop, auf Grundstücken aufstellen, drehen, aufheben, max. 50; Sitzen auf Stühlen, `/sit`, `/lay` (Liegen zählt beim Schlafen)
- [ ] 4.22 Support: Tickets `/support [new|top|helper]`, Helfer antworten, Sterne-Bewertung, Weitergabe ans Team
- [ ] 4.23 Discord: `/discord`, `/verify`, Haken hinter dem Namen, +1 Home, Pflicht für Spielzeit-Belohnungen und Helfer
- [ ] 4.24 Freunde: `/friends` (online, Server), `/friend add|accept|jump|remove`, mehr Plätze mit Rang, Anfragen abschaltbar (vorhanden, anpassen)
- [ ] 4.25 Spielzeit: Stufen mit Coins, Tokens, Schlüsseln, Juwelen, `/playtime [claim]`, nur mit Discord (vorhanden, anpassen)
- [ ] 4.26 Kosmetik: Hüte, Trails, Haustiere, `/cosmetics` (Trails vorhanden)
- [ ] 4.27 Kisten: am Spawn, virtuelle Schlüssel `/keys`, Linksklick Vorschau, Schleichen überspringt Animation
- [ ] 4.28 Statistik: `/stats [Spieler]`, `/baltop`, `/level top`, `/quest top`, `/casino stats`
- [x] 4.29 Info-Leiste (Survival-Leiste oben): `/bossbar` wählt aus, was oben steht (Geld, Job, Ort, Uhrzeit, verfolgte Quest), `/bossbar on|off|reset`

## Ordnergruppe 5 – Scoreboard und Anzeigen (Refs: Video Scoreboard_Money, GELD_XP-Anzeige)
- [x] 5.1 Sidebar SMP (v1.32)
- [x] 5.2 Sidebar Survival nach Clip (v1.34). Level, Job, Tokens zeigen noch „—“, die Systeme fehlen
- [~] 5.3 Sidebar Hub (v1.32), Layout angenommen (?)
- [x] 5.4 Geld-Zählanimation: Ziffern laufen hoch/runter, grün bei Plus, rot bei Minus, mit Ton (v1.32)
- [x] 5.5 Breitenschutz: Zeilen werden automatisch gekürzt (v1.32)
- [x] 5.6 Große Beträge: ab 1 Mio. "Mio$", ab 1 Mrd. "Mrd$" (v1.32)
- [x] 5.7 Pixel-Rahmen als Resource Pack (v1.34, Pack v2 mit Icons in v1.44). Pack-Format bitte prüfen
- [x] 5.8 Zahlen rechts ausblenden (v1.34, Paper NumberFormat)
- [x] 5.9 Geld-Zählanimation auch in der Hotbar/ActionBar (v1.35)
- [x] 5.10 Keyall-Anzeige an echten Keyall-Timer gekoppelt, gleiche Zeitzone (v1.35)
- [x] 5.11 Sidebar mit eigener Grafik nach Referenz (Sitzung mit Claude): goldener Rahmen mit runden Ecken, Logo je Modus oben („KUBY SMP“ rot mit Schwertern, „KUBY SURVIVAL“ grün mit Spitzhacken, „KUBY HUB“ blau), Münzen und Smaragde unten, 15 neue Icons (Coins, Juwelen, XP, Trophäen, Kills, Tode, Uhr, Spieler, Rang, Team, Claim, Tokens, Level, Job, Pfeil). Kommt über das Pack KubyUI, das KubyCore automatisch verschickt
- [x] 5.12 Ohne Pack (abgelehnt oder ui.custom-textures: off): saubere Text-Version ohne Kästchen, Titel aus der Config
- [x] 5.13 Fehler behoben: Die Sidebar wurde nie angezeigt (hud.enabled stand intern auf true)
- [x] 5.14 Hotbar-Anzeige nutzt die neuen Münz- und XP-Icons (die alten Zeichen gibt es nicht mehr)
- [x] 5.15 Scoreboard-Grafik nach hochauflösender Referenz neu gezeichnet: Auflösung wie im Beispiel (1 Texel je Pixel, Arcade-Look), Sidebar 116 px, Rahmen 125 px mit 6 px Rand (Schwarz, Gold, Braun, Schwarz), Goldverlauf Orange → Hellgelb → dunkles Gold, Innenschatten. Logo im Stil „CHAOS SMP“: zweifarbige Goldbuchstaben 13 × 12 px mit hellen Kanten, 3D-Kante und Schlagschatten, Silberbuchstaben 10 × 8 px, Figuren weich schattiert, Pfeile (SMP), Deko unten je Modus. Icons im Stil der Referenz neu gezeichnet (weiche, farbige Umrisse)
- [x] 5.16 Zu lange Zeilen werden gekürzt, ohne Farben und Icons zu verlieren
- [x] 5.18 Farben wie in der Referenz: Beschriftungen weiß, Werte farbig (Rang grau, Kills rot, Tode gold, Keyall gelb, Online-Zahl grün)
- [x] 5.19 Teamnamen ebenfalls in Small Caps (alle Texte der Sidebar sind Small Caps wie in der Referenz)
- [x] 5.20 Figuren und Eck-Grafiken entfernt. Dafür Holzranken, die sich links und rechts um den Rahmen nach oben winden (vorne über dem Rand, hinten verdeckt), ab und zu 2-3 Blätter, alles mit Schatten
- [x] 5.21 Logo-Schrift wieder in der vorherigen Form, mit 3 px 3D-Kante und kräftigerem Schatten (sticht mehr heraus)
- [x] 5.22 Survival-Scoreboard nach Referenz: Icons Stern (Level), Angel (Job), Token-Münze, Haus (Claimblöcke), Spawn-Plattform, oranger Pfeil vor „/bauwelt“ (normale Schrift). Coins in Ruhe gold, beim Zählen grün/rot
- [x] 5.23 Survival-Leiste oben (Platz der Bossbar): Holzleisten mit Blättern und Blume, Coins und Juwelen, Angler-Level mit Fortschritt, Ort (Spawn, Grundstück, Angelzone, Wildnis). Ersetzt in Survival die Anzeige über der Hotbar. Das Pack macht weiße Bossbars unsichtbar (die Leiste nutzt nur den Titel). config: survival-hud.*
- [~] 5.24 Level, Jobs und Tokens (KubySurvival, Build grün, Test im Spiel offen): `/job` (auch im Hauptmenü). 5 Jobs (Holzfäller, Miner, Farmer, Jäger, Angler), einer aktiv, Wechsel alle 60 Min. Job-XP und Coins pro Aktion, Coins mit Tageslimit 3.000. Kein Lohn für gesetzte Blöcke, Generator-Steine, Spawner-Mobs, Zucht, unreife Pflanzen. Job-Level bis 50 (Aufstieg: Coins + 1 Token), Spielerlevel aus allen Job-XP (Aufstieg: 3 Tokens). Token-Shop (Items aus jobs.token-shop, Jobwechsel sofort). Scoreboard zeigt Level, Job, Tokens; die Leiste oben den Job mit Fortschritt und „Coins heute / Limit“. Einstellungen: jobs.* in der KubyCore-config
- [x] 5.25 Survival-Leiste 5 px tiefer (mehr Abstand zum oberen Bildschirmrand)
- [x] 5.26 SMP-Logo: Pfeile wie in der Referenz, stecken schräg im oberen Rand neben „SMP“ (Spitze schaut heraus, 45°-Schaft, Feder als Fächer)
- [x] 5.27 Fehler behoben: Casino-Hilfe zeigte nur Kästchen (ungültiger Aufzählungspunkt ließ Minecraft die ganze Schrift verwerfen). Schriften werden jetzt bei jedem Build geprüft (tools/check_fonts.py)
- [x] 5.30 Dialog-Kit für Menüs mit eigener Grafik wie in den Referenz-Clips: Kästen, Kacheln, Reiter, Knöpfe, Balken und Icons werden pixelgenau als Dialog-Text gesetzt (Schriften kuby:k/…, Werkzeug tools/build_kit.py, Klassen Canvas, UiDialog, UiClicks). Ganze Kacheln sind klickbar mit Tooltip, kein Knopfbalken unten, das „!“ oben in Server-Dialogen ist ausgeblendet. Vorschau ohne Spiel: tools/preview_text.py. Test: `/kadmin kittest`
- [x] 5.32 Dialog-Kit: Zeichen einer Zeile zeichnet Minecraft (ab 1.21.6) in Textreihenfolge, Klicks treffen die gezeichnete Stelle (im Client-Code nachgelesen). Überlappungen ganz oben kommen jetzt in dieselbe Zeile, dadurch keine Leerzeile mehr über dem Dialog. Fehler behoben: 40 Zeichen außerhalb der Basis-Ebene brachten die Breiten-Tabelle durcheinander (Small Caps wurden zu „?“)
- [~] 5.31 Alte Hilfe-Kästen (Casino-Hilfe, Survival-Hilfe) auf das Kit umstellen: Minecraft zeichnet Schriftzeichen nur bis 256 px, die alten Kästen sind 300 px breit. Casino-Hilfe fertig (siehe C7), Survival-Hilfe offen
- [ ] 5.17 Im Spiel prüfen: Sitz des Rahmens bei GUI-Größe 2, 3 und 4

## Ordnergruppe 6 – Modi und Welten (Stand)
- Hinweis: SMP = viel Geld, PvP. Survival = wenig Geld, ruhig, Farmen. Beide Seiten sind getrennt.
- [x] 6.1 Hub, SMP, Survival mit getrennten Daten (Geld, XP, Inventar, Enderchest, Abholbox)
- [x] 6.2 Kompass-Wechsel, Sichtbarkeits-Farbstoffe, Lobby-Schutz, /kp
- [x] 6.3 /kp create (Void, Glasplattform, Worldborder)
- [x] 6.4 Worldborder wächst beim Bauen, auch bei Schematics und WorldEdit
- [x] 6.5 Module mit Store-Download (Index-Link ist ein Platzhalter)
- [x] 6.6 Bearbeitungsmodus: Blaze Rod, Redstone-Fackel, Barrier
- [x] 6.7 Regionen mit Schutz
- [x] 6.8 Hologramm-Marker (nur Admins sichtbar)
- [x] 6.9 Hub-Editor (Border, Zeit, Wetter, Musik, Begrüßung)
- [~] 6.10 Landclaim in Survival (neu gebaut in KubySurvival, Test offen): Claim-Schaufel, `/claim erstellen|löschen|liste|trust|untrust|anzeigen`, 500 Startblöcke +100/Stunde, max. 10 Claims, 5 bis 128 Blöcke Seitenlänge, Schutz gegen Abbauen, Bauen, Benutzen, Explosionen, Feuer, Kolben und Flüssigkeiten
- [x] 6.11 Daten pro Modus: Battlepass, Kosmetik, Homes, Kits (v1.33)
- [x] 6.12 /rtp in der Welt des aktuellen Modus (v1.33; Duo-RTP folgt)
- [x] 6.13 Sounds für Bearbeitungsmodus und Hub-Editor (v1.31)

## Ordnergruppe 7 – Spielerfortschritt (Stand)
- [x] 7.1 Spielzeit-Belohnungen mit eigenem Menü (Tags: Neuling, Arbeitslos, Hobbylos, Sweater)
- [x] 7.2 Homes mit Stufen (Grundplätze, XP-Kauf, Voten, /verify, Ränge)
- [x] 7.3 TPA mit Teleport-Effekten
- [x] 7.4 XP-Quellen: Erze, Mobs, Angeln
- [x] 7.5 Admin-XP: geben, nehmen, setzen
- [~] 7.6 Erfolge mit Fortschritt (Test offen): `/erfolge` und Hauptmenü, 14 Standard-Erfolge (Spielzeit, Votes, Angeln, Claims, Kills, Casino), frei definierbar in config.yml (achievements.list), Belohnung in Juwelen, Fortschrittsbalken
- [~] 7.7 Vote-Anbindung (Test offen): NuVotifier/Votifier optional per softdepend. Pro Vote 2 Juwelen + 250 Coins (offline: beim Login), erster Vote schaltet +1 Home frei. `/vote` zeigt die Vote-Seiten (config: vote.links eintragen!), `/kp vote <Spieler>` zum Testen

## Ordnergruppe 8 – Bestehende Systeme (Stand)
- [x] Shop, Sell, Markt, Auktionshaus, Orders, Handel
- [x] Teams, Freunde, Nachrichten, Homes
- [x] Duelle, Kopfgelder, Turniere, Trophäen, Ligen
- [x] Battlepass, Kosmetik, Kits, Totenkiste, Enderchest, Kristalltausch
- [x] Daily, Tagespot, Quiz, Wochen-Meta, Booster, Juwelen
- [x] Raid-Versicherung, Discord-Verknüpfung, Config-Editor, Admin-Panel
- [ ] Vault-Brücke (optional, nur für externe Shops)

## Ordnergruppe 9 – Technik und Qualität
- [x] 9.1 Echter Maven-Build gegen Paper 1.21.11 bei jedem Push (GitHub Actions), Compiler-Fehler als Annotationen, fertige ZIP als Release
- [x] 9.2 Testplan als TESTPLAN.md angelegt (14 Abschnitte)
- [~] 9.3 Config-Kommentare: Paper speichert sie seit 1.18 mit (Config-Editor nutzt den normalen Speicherweg). Im Spiel prüfen: Wert im Config-Editor ändern, Kommentare in config.yml müssen bleiben
- [x] 9.4 Quiz auf 241 Fragen erweitert (alle 7 Kategorien, keine Doppelten), falsche Glowstone-Frage ersetzt
- [~] 9.5 Anti-Farm-XP (Test offen): gesetzte XP-Blöcke im Chunk markiert (übersteht Neustart, keine Obergrenze, wandert mit Kolben), keine XP für Mobs aus Spawnern/Spawn-Eiern/Trial-Spawnern. Schalter: xp-sources.anti-farm
- [~] 9.6 Kit-Rang-Filter (Test offen): Kit 2 VIP, 3 Ultimate, 4 Warrior, 5 Legend, höhere Ränge dürfen niedrigere Kits. Erkennung über LuckPerms (group.x) oder kuby.rank.x, Reihenfolge in ranks.order

## Ordnergruppe 10 – Entscheidungen (offen)
- [x] Rang-Berechtigungen: beides geht (LuckPerms group.<rang> oder kuby.rank.<rang>)
- [~] Kit-Namen 2 bis 5: VIP, Ultimate, Warrior, Legend. Inhalte der Kits 2 bis 5 noch offen (Admin-Panel → Kits)
- [ ] Totenkiste Stufe 3: darf nur der Besitzer öffnen?
- [ ] Store-Index-Link für Module
- [ ] Hub-Scoreboard: Layout bestätigen (Clip fehlt)
- [ ] Survival-Scoreboard: Layout bestätigen (Clip fehlt)

## Ordnergruppe 11 – Plattform
- [x] Eigene Basis: KubyPlugins für Wirtschaft, Modi, Scoreboard, Homes, Teleport
- [ ] Optionale Abhängigkeiten dokumentieren: DiscordSRV, WorldEdit, Phoenix Crates (Kisten)
- [ ] Liste der Plugins, die wir NICHT brauchen (Essentials, Multiverse) und warum

## Punkt 1 – SMP-Trennung (Stand v1.39)
- [x] Tags pro Modus
- [x] Tagesbelohnung und Streak pro Modus
- [x] Marktwirtschaft-Anteile pro Modus
- [x] Shop-Lager pro Modus
- [x] Totenkiste pro Welt (je Modus eine)
- [x] Raid-Versicherung pro Welt
- [x] Teams: pro Modus, gleiche Namen in beiden Modi möglich (v1.41)
- [x] Freunde: pro Modus, Anfragen nur im selben Modus (v1.41)
- [x] Markt: Angebote pro Modus, Kauf nur im eigenen Modus (v1.40)
- [x] Orders: Bestellungen pro Modus, Lieferung nur im eigenen Modus (v1.40)
- [x] Kopfgelder: pro Modus, Auszahlung nur im selben Modus (v1.40)

## Nachtrag Scoreboard (v1.44)
- [x] Moduswechsel: Geld wird ohne Zählen und ohne Ton gesetzt
- [x] Abschlusston: tief bei Verlust, hell bei Gewinn, wenn die Zählung fertig ist
- [x] Scoreboard komplett in Small Caps (Zeilen und Titel)
- [x] Icons für jede Zeile (Pack v2, Platzhalter-Grafik)
- [x] Pack-Link: nicht mehr nötig. KubyCore verschickt das Pack KubyUI (mit Scoreboard-Grafik). resource-pack.url im Scoreboard leer lassen


## Aufteilung in Kategorien (Entscheidungen)
- Teams und Freunde: beide Modi
- Markt, Orders, Shop, Juwelenshop: Kern. Farmpass nur Survival
- Handel: beide Modi. Kopfgelder: SMP
- Kits, Totenkiste, Enderchest, Abholbox: Survival und SMP. Raid-Versicherung: nur SMP
- Battlepass: SMP. In Survival wird er zum Farmpass, mit eigenen Belohnungen
- Kosmetik, Tags, Tagesbelohnung, Spielzeit, Quiz: Kern, mit unterschiedlichen Belohnungen je Modus

## Schritte
- [x] 1. KubyCore: Hauptplugin umbenannt (v2.0.0), Prüf-Hook für Moduswechsel (v1.60)
- [x] 1a. Feste Prüfungen aus dem Moduswechsel entfernt. Kampf, Duell und RTP werden über Registrierung eingetragen (v2.0.1), das wandert in KubySMP. Handel bleibt Kern, weil er für beide Modi gilt
- [~] 2. KubySMP aus den PvP-Teilen bauen (Kampf, Duelle, Kopfgelder, Trophäen, Ligen, Turniere, Raid-Versicherung, Battlepass-SMP)
  - [x] 2a. PvP-Schnittstelle PvpApi im Kern, Kern-Dienste sprechen nur noch darüber (v2.0.2)
  - [x] 2b. Turnier-Duelle und Kampf-Tag bei Duo-RTP laufen über PvpApi (v2.0.3)
  - [x] 2c. Klassen verschoben nach KubySMP (v1.0.0). Kern und SMP zusammen geprüft: keine Verweise mehr auf die verschobenen Klassen (KubyCore 2.0.5). Offen: Test im Spiel
  - (alt) Klassen verschieben: Combat, Duel, Bounty, Trophy, Tournament, RaidInsurance, Listener, GUIs, Befehle /duell /bounty /bounties /turnier /versichern
    - Analyse: League bleibt im Kern (StatsService und TagService brauchen sie)
    - Blocker: Kern-Dateien nennen PvP-Klassen direkt: MainMenu (DuelMenu, InsuranceMenu, TournamentMenu, TrophyMenu), AdminMenu (AdminCombatMenu, TournamentAdminMenu), ExtraCommand (InsuranceMenu, TournamentMenu, InsuranceListener), KubyPlugins (Felder, Konstruktoren, Getter, Listener), SocialCommand (Befehle /bounty und /bounties)
    - Reihenfolge: erst 2d (Menüs und Befehle über eine Registrierung), dann verschieben
  - [x] 2d. Hauptmenü und Admin-Menü lesen Einträge aus einer Registrierung (registerMainEntry, registerAdminButton). Duelle, Turnier, Trophäen und Versicherung sind aus dem Kern raus (v2.0.4). Befehle folgen mit 2c
  - [x] 2e. KubySMP-Hauptklasse: Dienste erstellen, PvpApi setzen, Befehle registrieren (im Code geprüft, Build offen: Paper-API nicht erreichbar)
- [~] 3. KubySurvival 1.0.0 gebaut (Angeln, Claims). Casino liegt im Kern. Features aus Gruppe 4 folgen

## Addons
- [x] KubyScoreboard 1.0 (eigenes Addon)
- [x] KubyJR-Addon 1.0.0: kompatibel gemacht, fehlende Methode registerActivity im Hauptplugin ergänzt (v1.58)
- [x] KubyScoreboard 1.0.1 und KubyJR-Addon 1.0.1 auf KubyCore umgestellt
- [ ] Serverordner: plugins/KubyPlugins in plugins/KubyCore umbenennen (Daten bleiben erhalten)

## Regeln
- Neue Befehle MÜSSEN in /befehle stehen. Prüfung: jeder Befehl aus plugin.yml muss in HelpCommand vorkommen (aktuell vollständig, v1.59).

## Fokus: SMP (aktuell)
- [x] 2a-2d: PvP-Schnittstelle, Verschieben, Menüs (KubySMP 1.0.0)
- [x] Anti-Farm-XP für Erze (v2.0.7)
- [ ] Test im Spiel: Kampf, Duell, Turnier, Kopfgeld, Versicherung, Trophäen, Handel, Duo-RTP
- [ ] Offen für SMP: Kit-Ränge (Entscheidung nötig), Kit 2-5 Inhalte
- [ ] Farmpass und Survival (3b, 3c) warten bis der SMP getestet ist

## Skyblock (neuer Modus)
- [x] Etappe 1 Grundgerüst: Modus Skyblock im Kern, Modus-Auswahl als GUI am Kompass, Addon KubySkyblock (Insel pro Spieler beim Betreten, 50.000er Weltgrenze, Multiplikator-Serie 18-24 Uhr)
- [x] Etappe 2 (v1.1.0): Insel-Editor mit Werkzeug und zwei Ecken, Loot-Inseln (6 Stufen, nutzlos bis gott), drei One-Block-Plattformen mit Zufallsblock
- [x] Etappe 3 (KubySkyblock 1.2.0, KubyCore 2.0.14): Checkliste aller Items (aufheben, herstellen, Loot), Erfolge, Meilensteine (10/25/50/75/100%) mit Loot-Belohnung, /skyblock-Menü
- [ ] Etappe 4: Skyblock-Module (Insel-Vorlagen, Loot-Tabellen)
- [ ] Juwelen für sehr seltene Items
- [ ] Skyblock-Welt anlegen: /kp create skyblock, danach Spawn setzen

## Entscheidungen (Stand: Sitzung mit Claude)
- Ziel: Paper 1.21.11. SMP = PvP und Action (töten, greifen erlaubt, außer Cheaten, Duplizieren und Bugs ausnutzen). Survival = ruhig, bauen und farmen, Plots geschützt bzw. claimbar.
- Bugs, Glitches und Exploits werden zum Schluss geprüft, nicht zwischendurch.
- 7.7 Vote-Anbindung: NuVotifier/Votifier (Vote-Event).
- Kits: nach Rang. Rang-Liste (kuby.rank.*) fehlt noch, sie blockiert 9.6 und die Kit-Inhalte 2 bis 5.
- Casino (Gruppe 1): zuerst nur die Grundlagen (Region, Chips, Umtausch, Jackpot-Anzeige). Spiele danach.
