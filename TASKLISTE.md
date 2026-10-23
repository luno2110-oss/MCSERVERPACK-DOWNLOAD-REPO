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
- [~] 1.3 Anleitung (Test im Spiel offen): Casino-Hilfe als Dialog-Fenster nach der Referenz (Titel, "So geht's", Befehle, Buttons je Spiel, "Hinreisen"). Öffnen per `/casino regeln [spiel]` oder `/casino hilfe`, Rechtsklick auf Kasse oder Roulette-Tisch, Schleichen + Rechtsklick auf einen Spieltisch. Werte aus der config.yml. Offen: roter Rahmen der Referenz braucht Grafiken im Resource Pack
- [~] 1.4 Sound und Anzeige (Test im Spiel offen): bei jedem Ergebnis Sound für den Spieler und aufsteigender Text über dem Tisch ("Name +12 Chips"), eigener Klang für Gewinn, großen Gewinn, Unentschieden und Verlust. Jackpot: Text, Totem-Partikel und Sound für alle im Umkreis von 30 Blöcken. Offen: Jackpot-Ticker
- [~] 1.5 Admin (Test im Spiel offen): Casino-Admin-Fenster per `/casino admin` oder Admin-Panel (Casino-Button). Status und Aufbau der Tische, Schieberegler für Chip-Preis, Einsätze, Roulette-Limit und -Wartezeit, Crash-Höchstwert und Jackpot-Anteil, Buttons zum Setzen der Tische, Casino öffnen/schließen (Notschalter). Auszahlung je Spiel (eingesetzt, ausgezahlt, Casino-Plus), Protokoll jeder Runde in casino-log.txt
- [~] 1.6 Aus der Casino-Referenz (entschieden: wie Referenz, Jackpot um 19 Uhr)
  - [~] Jackpot (Test im Spiel offen): jeden Samstag 19 Uhr Verlosung unter allen, die seit der letzten Ziehung gespielt haben. Gewinn wird gutgeschrieben, sobald der Gewinner in Survival ist. Nachziehen nach Server-Ausfall. Admin: "Jackpot jetzt ziehen". Drei Siebener knacken den Jackpot nicht mehr (per Config `casino.jackpot.seven-wins` wieder einschaltbar)
  - [~] Casino-Musik (Test im Spiel offen): startet beim Betreten der Region `casino`, stoppt beim Verlassen, läuft in Schleife. `/music` öffnet die Songauswahl (12 Schallplatten, „Musik aus“), nur im Casino. Wahl bleibt gespeichert
  - [~] VIP-Lounge (Test im Spiel offen): Region `casino-vip`, Zutritt mit LuckPerms-Gruppe legend oder Top 10 mit Plus (auch Teleport wird geblockt). Barkeeper per `/casino barkeeper` (entfernen: `/casino barkeeper entfernen`), geben einmal am Tag 500 Chips
  - [~] Rangliste „Am meisten Plus“ (Test im Spiel offen): `/casino stats` oder Button in der Hilfe, Top 10 und eigener Platz, zählt Gewinne minus Einsätze aller Spiele
  - [~] Poker (Test im Spiel offen): Texas Hold'em, bis 6 Spieler, Blinds 10/20, Buy-in 40 bis 200 BB per `/poker sitzen <Chips>`, `/poker aufstehen` nimmt den Stack mit. Aktionen als Klick-Buttons im Chat, 30 s Zeit pro Zug, eigene Karten in der Aktionsleiste, Tisch-Anzeige mit Board, Pot und Stacks. Side-Pots, Hand-Auswertung getestet (17 Fälle). Stacks laufend gesichert, nach Absturz zurück. Tisch per `/casino poker-tisch` oder Admin-Fenster

## Ordnergruppe 2 – Angeln (Refs: ANGEL REF) – nur Survival
- [x] 2.1 Angelzonen mit schwebendem Text (v1.36)
- [x] 2.2 Fangsystem: 8 Fischarten, Seltenheit, Gewichte, Rute beeinflusst Chance (v1.36). Tageszeit noch nicht
- [x] 2.3 Angel-Level und XP mit Fortschritt (v1.36)
- [x] 2.4 Belohnungen: XP beim Fang, Geld bei Levelaufstieg, Fische als Items (v1.36)
- [x] 2.5 Fischbuch mit Fortschritt pro Fischart (v1.36)
- [x] 2.6 Angelrute-Upgrades, 5 Stufen (v1.36)
- [x] 2.7 Bestenliste (v1.36). Turnier noch offen
- [x] 2.8 SFX und schwebende Texte bei jedem Fang (v1.36)
- [x] 2.9 Fischverkauf im Angel-Menü (v1.36). Händler-NPC noch offen

## Ordnergruppe 3 – Juwelenshop (Refs: JUWELENSHOP SURVIVAL, JUWELENSHOP SMP)
- [x] 3.1 Survival: Ränge, Keys, Juwelen mit Inhalt (v1.37)
- [x] 3.2 SMP: Ränge, Keys, Juwelen mit Inhalt (v1.37)
- [x] 3.3 Battlepass und Vorteilspakete als Barriere, im Editor aktivierbar (v1.37)
- [x] 3.4 Editor: /kp shop editor survival|smp (v1.37)

## Ordnergruppe 4 – Features Survival (Refs: REFERENZEN ANDERER FEATURE) – nur Survival, Schwerpunkt
Titel per OCR ausgelesen, bitte bestätigen. Viele Karten sind unleserlich.
- [ ] 4.1 Einstieg und Regeln (?)
- [ ] 4.2 Grundstoffe (?)
- [ ] 4.3 Quests und Level (?)
- [ ] 4.4 Haustiere (?)
- [ ] 4.5 Heiraten (?)
- [ ] 4.6 Freunde und Support (?)
- [ ] 4.7 Spielzeit (?)
- [ ] 4.8 Kosmetik (?)
- [ ] 4.9 Einstellungen und Info-Leiste (?)
- [ ] 4.10 Voten (?)
- [ ] 4.11 Juwelen (?)
- [ ] 4.12 Tod und Ort (?)
- [ ] 4.13 Fraktionen / Gangs (?)
Bereits vorhanden (mit Referenz verknüpfen): Kits, Enderchest, Kristalltausch, Homes, Markt, Totenkiste

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
- [x] 6.10 Landclaim in Survival (v1.34, Claimblöcke in claims.yml)
- [x] 6.11 Daten pro Modus: Battlepass, Kosmetik, Homes, Kits (v1.33)
- [x] 6.12 /rtp in der Welt des aktuellen Modus (v1.33; Duo-RTP folgt)
- [x] 6.13 Sounds für Bearbeitungsmodus und Hub-Editor (v1.31)

## Ordnergruppe 7 – Spielerfortschritt (Stand)
- [x] 7.1 Spielzeit-Belohnungen mit eigenem Menü (Tags: Neuling, Arbeitslos, Hobbylos, Sweater)
- [x] 7.2 Homes mit Stufen (Grundplätze, XP-Kauf, Voten, /verify, Ränge)
- [x] 7.3 TPA mit Teleport-Effekten
- [x] 7.4 XP-Quellen: Erze, Mobs, Angeln
- [x] 7.5 Admin-XP: geben, nehmen, setzen
- [ ] 7.6 Erfolge mit Fortschritt
- [ ] 7.7 Vote-Anbindung für Homes und Belohnungen

## Ordnergruppe 8 – Bestehende Systeme (Stand)
- [x] Shop, Sell, Markt, Auktionshaus, Orders, Handel
- [x] Teams, Freunde, Nachrichten, Homes
- [x] Duelle, Kopfgelder, Turniere, Trophäen, Ligen
- [x] Battlepass, Kosmetik, Kits, Totenkiste, Enderchest, Kristalltausch
- [x] Daily, Tagespot, Quiz, Wochen-Meta, Booster, Juwelen
- [x] Raid-Versicherung, Discord-Verknüpfung, Config-Editor, Admin-Panel
- [ ] Vault-Brücke (optional, nur für externe Shops)

## Ordnergruppe 9 – Technik und Qualität
- [~] 9.1 Syntax-Check mit JDK 21 (v1.38): keine Syntaxfehler in 151 Dateien. Bukkit/Paper-API fehlt in der Sandbox, echter Bau mit Maven nötig
- [x] 9.2 Testplan als TESTPLAN.md angelegt (14 Abschnitte)
- [ ] 9.3 Config-Editor: Kommentare in der config.yml bleiben erhalten
- [ ] 9.4 Quiz auf 200 bis 300 Fragen erweitern (aktuell 168)
- [ ] 9.5 Anti-Missbrauch für XP aus Erzen (Spielerblöcke ausschließen)
- [ ] 9.6 Kits: Rang-Filter

## Ordnergruppe 10 – Entscheidungen (offen)
- [ ] Rang-Berechtigungen (kuby.rank.*) festlegen oder über LuckPerms vergeben
- [ ] Kit-Namen 2 bis 5 und Inhalte
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
- [ ] Pack-Link: direkten Download-Link eintragen (config.yml: resource-pack.url), dann verschickt das Plugin das Pack beim Join (v1.45)


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
- [ ] 3. KubySurvival aus Angel- und Claim-Teilen bauen, dann Casino und Features

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
