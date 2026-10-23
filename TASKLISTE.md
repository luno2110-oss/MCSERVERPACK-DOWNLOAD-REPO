# KubyPlugins – Gesamtplan (Hauptchat)

Legende: [x] fertig · [~] teilweise · [ ] offen · (?) Annahme, bitte bestätigen
Stand: **v1.32** (ungetestet, nicht kompiliert)

---

## Ordnergruppe 1 – Casino (Refs: CASINO REF) – nur Survival
Prinzip: Keine GUI. Spiel läuft über physische Dinge am Tisch (Karten, Chips, Rad, Dealer).

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
- [~] 2.1 Angelzonen mit schwebendem Text (KubySurvival, Test offen): `/kp angel pos1|pos2`, `/kp angel zone erstellen <id> [Name]`. Ohne Zonen gilt das System überall in Survival
- [~] 2.2 Fangsystem (Test offen): 8 Fischarten, Seltenheit, Gewicht in kg je Art, Rute beeinflusst Chance. Tageszeit: nachts seltene Fische öfter, tagsüber häufige
- [~] 2.3 Angel-Level und XP mit Fortschritt (Test offen)
- [~] 2.4 Belohnungen (Test offen): XP beim Fang, Geld bei Levelaufstieg, Fische als Items mit Gewicht und Wert
- [~] 2.5 Fischbuch mit Fortschritt, Anzahl und Rekord pro Fischart (Test offen)
- [~] 2.6 Angelrute-Upgrades, 5 Stufen (Test offen): Stufe hängt am Spieler (nicht duplizierbar), seltene Fische öfter, schnelleres Beißen
- [~] 2.7 Bestenliste (Top 10 nach XP, Server-Rekord je Art) und Angel-Turnier (Test offen): `/kp angel turnier start <Minuten>`, Gesamtgewicht zählt, Preise 5.000/2.500/1.000
- [~] 2.8 SFX und schwebende Texte bei jedem Fang (Test offen), seltene Fänge werden angesagt
- [~] 2.9 Fischverkauf im Angel-Menü und beim Fischhändler-NPC (`/kp angel haendler`) (Test offen)

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
- [ ] 4.12 Farmpass (Store-Abo, Rechte): Fliegen (Spawn, Casino, eigene und Freundes-Grundstücke), Nebenjob, keine Gebühren (AH, Grundstücksmarkt), Casino steuerfrei, +2 Homes, mehr AH-Plätze, Grab 60 Min., `/werkbank` und `/ec` überall, Wochenkiste, 4 exklusive Pets, Monats-Pet, +250 Claim-Blöcke pro Monat, grünes Blatt in Tab und Chat; Menü wie Referenz; `/farmpass [pet|fly]`
- [ ] 4.13 Haustiere: folgen, manche reitbar, aus Farmpass/Kisten/Rang/Haustier-Menü; rufen, umbenennen, Lieblinge; `/pet [summon|dismiss|ride]`
- [ ] 4.14 Teams: Chat, Team-Homes, Lager, Kasse, Team-Level mit mehr Plätzen; `/team create|invite|accept|help`, `/tc` (Teile vorhanden)
- [ ] 4.15 Homes: 1 Home, ab Platin +1 je Rang, +1 für Discord; nicht am Spawn, im Casino, in der Farmwelt (vorhanden, anpassen)
- [ ] 4.16 Voten: Serie ab 10/25/50 Tagen +250/500/1.000 Coins je Vote-Tag, Monats-Top-Voter (vorhanden, erweitern)
- [ ] 4.17 Einstellungen: `/settings` (Nachrichten, Freundesanfragen, Sounds, Scoreboard, Info-Leiste)
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
- [~] 4.29 Info-Leiste (gebaut als Survival-Leiste): `/bossbar` auswählen, was oben steht (Coins, Juwelen, Job, Ort, Quest, Uhrzeit), `/bossbar on|off|reset`

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
