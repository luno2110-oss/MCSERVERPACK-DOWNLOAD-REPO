# Bedrock-Spieler (Handy, Konsole, Windows) über Geyser

Bedrock-Spieler kommen über **Geyser** auf den Java-Server, **Floodgate** lässt sie ohne Java-Konto spielen. Unsere
Plugins erkennen sie und zeigen ihnen alles in einer eigenen Bedrock-Fassung:

- **Alle Menüs als native Bedrock-Formulare.** Jedes Kuby-Menü (Hauptmenü, Shops, Quests, Angeln, Casino, Insel,
  Kadmin, Editor …) wird automatisch zu einem Formular: Titel, Text mit Farben und den Kuby-Icons im Text, ein Knopf je
  klickbarer Stelle mit Bild. Formulare mit Eingabefeldern (Feedback, Namen, Beträge) kommen als Bedrock-Eingabeformular.
- **Eigene Items mit eigenem Bild**: Angeln, Fische, Möbel, Hüte, Haustiere, Casino-Chips, Event-Items, Generatoren,
  Kekse … (764 Modelle, 3D-Modelle als gerendertes Icon).
- **Scoreboard** mit Modus-Logo und den Scoreboard-Icons, ohne rote Zahlen.
- **Chat**: Kuby-Präfix und Überschriften in normalen Buchstaben (die Bedrock-Schrift hat keine Kapitälchen).
- **Angel-Minispiel** mit eigener Leiste aus Blöcken und Fisch-Icon; Klicks und Bewegung steuern wie auf Java.

Was Java-only bleibt (Bedrock kann es nicht anzeigen, es gibt einen Ersatz): die Info-Leiste oben (die Werte stehen im
Scoreboard), Kino-Szenen von Kisten und Schwert (Bedrock bekommt den Gewinn direkt), Grafik-Schriften in Hologrammen.

## Versionen: was muss zusammenpassen?

Stand November 2026 unterstützt Geyser **Bedrock 26.40 bis 26.63** und **Java 26.3**.

- **Bedrock-Spieler** müssen nichts tun: Bedrock aktualisiert sich auf Handy, Konsole und Windows von selbst, und
  Geyser unterstützt immer die aktuellen Bedrock-Versionen. Ältere Bedrock-Versionen lässt Geyser nicht rein.
- **Der Server muss nicht „die Bedrock-Version“ haben.** Geyser übersetzt zwischen Bedrock und Java und tritt
  gegenüber dem Server wie ein Java-26.3-Spieler auf. Darum gilt:
  - Läuft der Server auf **26.3** (so hast du es getestet): passt direkt.
  - Läuft der Server auf **1.21.11** (unser Ziel): zusätzlich **ViaVersion** installieren, dann dürfen 26.3-Spieler
    (also auch Geyser) auf den älteren Server. So läuft auch unser automatischer Bedrock-Test.
- **Wichtig ist nur: Geyser und Floodgate aktuell halten.** Kommt ein neues Bedrock-Update, zieht Geyser meist in
  wenigen Tagen nach; bis dahin kommen Spieler mit der neuesten Bedrock-Version eventuell nicht rein.
- Geyser braucht **Java 21 oder neuer** und läuft als Plugin auf Paper ab 1.20.5.

## Einrichtung

1. In `plugins/` legen: **Geyser-Spigot.jar**, **floodgate-spigot.jar** (beide von geysermc.org/download) und bei einem
   Server unter 26.3 **ViaVersion.jar**. Server einmal starten und stoppen (Configs entstehen).
2. **Port 19132 für TCP und UDP freigeben** (Hoster-Panel, Router, Firewall). Seit Bedrock 26.50 verbinden sich die
   Spieler über **NetherNet**: über TCP findet Bedrock den Server (Signaling), über UDP wird gespielt. Nur UDP (wie
   früher) reicht nicht mehr, dann taucht der Server nicht auf. Der Port darf nicht der Java-Port sein (25565 bleibt
   Java). In `plugins/Geyser-Spigot/config.yml` unter `bedrock:` steht dafür `port: 19132` und `transport: nethernet`
   (Standard der aktuellen Geyser-Versionen; `both` nimmt zusätzlich ältere Verbindungen über RakNet an, braucht dann
   aber einen zweiten UDP-Port, z. B. `webrtc-port: 19133`).
   - Empfohlen: Signaling über **HTTPS** einrichten (Geyser-Wiki „HTTPS for NetherNet“), sonst fragt Bedrock beim
     ersten Beitritt „Trust on First Use?“.
   - Kein offener Port möglich: playit.gg (Ngrok geht nicht, kein UDP).
3. `plugins/Geyser-Spigot/config.yml`:
   - `auth-type: floodgate` (mit Floodgate der Standard)
   - `enable-custom-content: true` (für unsere eigenen Items)
4. Aus `KubyPlugins.zip`:
   - `KubyBedrock.mcpack` → `plugins/Geyser-Spigot/packs/`
   - `KubyBedrock-Items.json` → `plugins/Geyser-Spigot/custom_mappings/`
5. Server neu starten. Im Log: Geyser lädt das Pack und die Items („custom items“). Bedrock-Spieler laden das Pack beim
   ersten Beitreten (ca. 6 MB).
6. Bedrock-Spieler heißen auf dem Server mit Punkt davor (`.Name`, Floodgate-Standard). Rechte, Ränge und Befehle mit
   Spielernamen gehen ganz normal.

## Schalter (KubyCore `config.yml`)

```yaml
bedrock:
  aktiv: true        # Bedrock-Anpassungen überhaupt
  formulare: true    # Menüs als Bedrock-Formulare (false: Truhen-Menüs wie ohne Grafik-Pack)
  scoreboard: true   # eigene Bedrock-Sidebar mit Icons
```

## Nach jedem Update

`KubyBedrock.mcpack` und `KubyBedrock-Items.json` aus der neuen ZIP wieder in die beiden Geyser-Ordner legen (das Pack
hat dann eine neue Version, Bedrock lädt es neu). Das Plugin bringt die passende Icon-Tabelle selbst mit.
