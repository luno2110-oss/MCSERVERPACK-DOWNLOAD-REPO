# Beta-Test: Checkliste für den Server

Ziel: 10 Tester spielen gleichzeitig, wechseln zwischen den Modi, öffnen Menüs und lösen Events aus. Der
Belastungstest in der CI (Release „load-test“) spielt genau das mit 10 Bots nach und muss grün sein.

## Vor dem Start

1. **Paper 1.21.11**, Java 21, mindestens 4 GB RAM für den Server (`-Xmx4G`).
2. **server.properties**
   - `allow-flight=true` (Farmpass-Fliegen, Cutscenes und Moduswechsel heben Spieler kurz an; sonst kickt Minecraft
     mit „Flying is not enabled“)
   - `online-mode=true` (im echten Betrieb nie `false`)
   - `view-distance=8`, `simulation-distance=6` reichen für 10 Spieler gut
   - `max-players=20`
3. **Plugins**: alle Jars aus `KubyPlugins.zip` in `plugins/`, im Log muss „Bukkit plugins (6)“ stehen.
4. **Resource Pack**: `KubyUI-Pack.zip` hochladen (z. B. eigener Webspace), Link und SHA-1 in `server.properties`
   (`resource-pack`, `resource-pack-sha1`, `require-resource-pack=true`). Ohne Pack laufen nur die Truhen-Menüs.
5. **Modi einrichten**: Welten und Spawns für Hub, SMP, Survival und Skyblock (`/kp setspawn`, `/kp setworld`,
   Skyblock mit `/kp skyblock`). Test: `/modus survival`, `/modus skyblock`, `/modus smp`, `/hub`.
6. **Backups**: laufen stündlich von selbst (`/kp backup liste`). Vor dem Start einmal `/kp backup jetzt beta-start`.
7. **Selbsttest**: `/kp selftest` muss „SELFTEST OK“ und „LAYOUT 0 Auffälligkeiten“ melden.

## Tester

- Rolle vergeben: `/kp beta add <Spieler>` (oder alle: `/kp beta alle`), Liste: `/kp beta liste`
- Tester sehen beim Join den Hinweis auf `/feedback` (Formular, 7 Seiten, 42 Sterne-Fragen 1 bis 6 und 9 Textfragen)
  und `/bug <Text>` (Bug sofort melden, Ort und Modus werden mitgespeichert).
- Für das erste abgeschickte Formular gibt es 50 Juwelen (`beta.belohnung-juwelen`).

## Während des Tests

- Kadmin → Sicherheit → **Beta-Feedback** (`/kadmin feedback`): Durchschnitt je Frage, Textantworten, Bugs
  (Hinreisen, Erledigt).
- Kadmin → **Warnungen** und **Wirtschaft**: auffällige Buchungen (große Gutschriften, schnelles Geld).
- `/tps` und `/mspt` im Auge behalten (Belastungstest: 18 bis 20 TPS mit 10 Bots, die dauernd Menüs klicken, Events
  auslösen und das Angel-Minispiel spielen).
- Selbst nachspielen, was der Belastungstest macht (nur Konsole und Admins): `/kubytest klicks 3` (jeder Spieler klickt
  3 zufällige Knöpfe im offenen Menü), `/kubytest events` (alle Events einmal auslösen, auch eine Minispiel-Runde für
  bis zu 3 Spieler in Survival).

## Danach

- `/kp beta export` schreibt alles als `plugins/KubyCore/beta-export.csv` (Excel: Semikolon, UTF-8).
- Backup ziehen, dann Wirtschaft zurücksetzen oder übernehmen (Entscheidung des Besitzers).
