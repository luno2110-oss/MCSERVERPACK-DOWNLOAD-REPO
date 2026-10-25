# Plugins neben KubyPlugins

KubyPlugins bringt fast alles selbst mit. Dieser Text sagt, welche fremden Plugins **optional** sind, welche
**empfohlen** werden und welche du **nicht brauchst** (und besser weglässt, weil sie sich mit unseren Befehlen und
Menüs überschneiden).

Server: **Paper 1.21.11**. Die Kuby-Jars: `KubyCore.jar` (Pflicht) und die Addons `KubySurvival.jar`,
`KubySMP.jar`, `KubySkyblock.jar`, `KubyScoreboard.jar`, `KubyJR.jar`. Dazu das Grafik-Pack `KubyUI-Pack.zip`
(liefert der Server beim Einloggen selbst aus).

## Empfohlen

| Plugin | Wofür | Ohne das Plugin |
|---|---|---|
| **LuckPerms** | Ränge und Rechte. Kuby erkennt Ränge über `group.<rang>` (LuckPerms) oder `kuby.rank.<rang>`. Der Juwelenshop vergibt Ränge mit `lp user %player% parent add <rang>`. | Ränge nur über `kuby.rank.<rang>` mit einem anderen Rechte-Plugin; die Rang-Käufe im Juwelenshop brauchen dann einen anderen Befehl (Abschnitt `jewelshop` in der config.yml). |

## Optional

| Plugin | Wofür | Ohne das Plugin |
|---|---|---|
| **DiscordSRV** | Discord-Verlinkung (`/verify`): blauer Haken hinter dem Namen, +1 Home, Pflicht für Spielzeit-Belohnungen und Support-Helfer. Kuby fragt DiscordSRV nur ab (keine feste Abhängigkeit). | `/verify` meldet „gerade nicht möglich“. Spielzeit-Belohnungen und Helfer gehen dann ohne Verlinkung (die Pflicht greift nur, wenn DiscordSRV läuft). `/discord` zeigt weiter den Link aus `discord.invite`. |
| **NuVotifier** (oder Votifier) | Votes von Serverlisten: Juwelen, Coins, Vote-Schlüssel, Serien, Monatspreise. | Votes kommen nicht an. Zum Testen: `/kp vote <Spieler>`. |
| **WorldEdit** | Nur zum Bauen (Spawn, Casino, Kirche). Kuby erkennt WorldEdit-Befehle und vergrößert bei Bedarf die Weltgrenze. | Nichts fehlt im Spiel. |

**Phoenix Crates wird nicht mehr gebraucht.** Kisten und Schlüssel sind jetzt eingebaut: virtuelle Schlüssel
(`/keys`), Kisten am Spawn (`/keys kiste set <id>`), Animation, Vorschau mit Chancen. Alte Befehle in der Config
(`crate givekey …`) sind auf `keys give %player% <art> 1` umgestellt. Wer trotzdem ein anderes Kisten-Plugin nutzt,
trägt dessen Befehl bei `keyall.command`, `battlepass.key-command` und im Juwelenshop ein.

## Nicht nötig (bitte weglassen)

Diese Plugins machen Dinge, die Kuby schon kann. Zusammen gibt es doppelte Befehle, doppelte Konten oder Menüs, die
sich gegenseitig überschreiben.

| Plugin | Ersetzt durch Kuby | Warum weglassen |
|---|---|---|
| **EssentialsX** (inkl. Chat, Spawn) | `/spawn`, `/home`, `/sethome`, `/delhome`, `/homes`, `/tpa`, `/tpaccept`, `/tpadeny`, `/msg`, `/r`, `/pay`, `/balance`, `/baltop`, `/kits`, `/enderchest`, `/werkbank`, `/rtp`, Chat-Format mit Rang, Haken und Herz | Eigene Wirtschaft und Homes **je Modus** (SMP, Survival, Skyblock getrennt). Essentials hätte ein zweites Konto und überschreibt die Befehle. |
| **Vault** + Wirtschafts-Plugins | Kuby-Wirtschaft (Coins je Modus, Juwelen, Tokens, Bank, Ehe-Konto) | Nur nötig, wenn ein fremdes Plugin Geld braucht. Kuby selbst nutzt Vault nicht. |
| **Multiverse** | Modi und Welten (`/hub`, Moduswahl), Farmwelt mit Reset, Bauwelt | Kuby legt die Welten selbst an und trennt Inventar, Geld und Fortschritt je Modus. Multiverse-Inventar-Trennung würde sich damit beißen. |
| **GriefPrevention**, **WorldGuard** (für Grundstücke) | Survival-Grundstücke mit der Claim-Axt (`/claim`), Regionen im Kern (Casino, Spawn) | Doppelter Schutz, unterschiedliche Rechte. WorldGuard nur, wenn du es für Spezial-Regionen brauchst. |
| **Phoenix Crates**, **CrazyCrates**, **ExcellentCrates** | `/keys`, Kisten am Spawn | Siehe oben. |
| **Jobs Reborn** | `/job`, `/level`, Tokens, Token-Shop | Eigene Berufe mit Levels und Tagesgrenzen. |
| **ChestShop**, **QuickShop**, **AuctionHouse** | Kistenläden mit Schild `[Shop]` (`/chestshop`), Markt (`/markt`), Aufträge (`/orders`), Admin-Shop (`/shop`, `/sell`) | Gleiche Schilder und Befehle. |
| **VotingPlugin** | Vote-Belohnungen, Serien, Monatspreise, Vote-Schlüssel | Nur NuVotifier wird gebraucht. |
| **PlayTime-/Rewards-Plugins** | `/playtime` mit Stufen | Doppelte Belohnungen. |
| **DecentHolograms**, **HolographicDisplays** | Schilder und Ranglisten über Anzeige-Entities | Nicht nötig. |
| **Citizens** | NPCs (Croupier, Priester, Bankier, Händler) als Mannequins | Nicht nötig. |
| **TAB**, **FeatherBoard**, **Animated Scoreboard** | `KubyScoreboard` (Scoreboard mit Grafik), Info-Leiste, Tab-Namen | Zwei Scoreboards flackern gegeneinander. |
| **HeadDatabase** | `/heads` | Nicht nötig. |
| **GravesX**, **AngelChest** | Gräber in Survival (`/grab`), Totenkiste | Würden die Sachen doppelt einsammeln. |
| **ItemsAdder**, **Oraxen** (für Hüte, Möbel) | Hüte (`/cosmetics`), Möbel (`/moebel`), eigenes Grafik-Pack KubyUI | Zwei Resource-Packs gleichzeitig gehen nur mit Zusammenführen. |
| **Marriage-Plugins** | `/marry`, `/bank` | Nicht nötig. |
| **Friends-/Party-Plugins** | `/friends`, `/friend` | Nicht nötig. |
| **Ticket-/HelpOp-Plugins** | `/support` | Nicht nötig. |

## Unbedenklich

Plugins, die nichts davon berühren, laufen normal mit, z. B. Anti-Cheat, Backups, Performance (Spark),
Welt-Generatoren, DiscordSRV. Im Zweifel: prüfen, ob es einen der oben genannten Befehle selbst registriert.
