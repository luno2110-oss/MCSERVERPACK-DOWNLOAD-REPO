package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Survival-Hilfe als Dialog-Karussell nach der Hilfe-Referenz: eine Seite je Feature mit eigener Farbe, Kopf mit Icon,
 * "So geht's", Befehle und drei Buttons (vorherige Seite, Aktion, nächste Seite). Öffnen mit /hilfe [Seite].
 * Aktionen rufen den passenden Befehl auf; gibt es ihn (noch) nicht, kommt ein Hinweis.
 */
public final class SurvivalHelp {

    private static final int WIDTH = 320;
    private static final TextColor BODY = TextColor.color(0xE8E8E8);
    private static final TextColor SUB = TextColor.color(0xBDBDBD);
    private static final TextColor CMD = TextColor.color(0x5FE3C4);

    /** Eine Hilfe-Seite. action: Befehl ohne Slash (null = Casino-Hilfe öffnen). */
    private record Page(String id, String nav, Ui.Theme theme, Ui.Icon icon, String title, String subtitle,
                        List<String> how, List<String[]> commands, String actionLabel, String action) {}

    private static String[] c(String cmd, String desc) {
        return new String[]{cmd, desc};
    }

    private static final List<Page> PAGES = List.of(
            new Page("regeln", "Regeln", Ui.Theme.GREEN, Ui.Icon.BOOK, "REGELN", "Fair spielen, gemeinsam bauen",
                    List.of("Auf Survival gelten zwölf Paragraphen, zum Beispiel zu Griefing, Claims, PvP und Chat. Mit /regeln liest du sie alle.",
                            "Die Regeln gelten für jeden Spieler, egal welcher Rang und wie lange schon dabei.",
                            "Wer gegen die Regeln verstößt, kann verwarnt, gemutet oder gebannt werden. Siehst du einen Verstoß, melde ihn mit /report."),
                    List.of(c("/regeln", "Alle Regeln"), c("/regeln <Nummer>", "Einen Paragraphen lesen"), c("/report <Spieler>", "Spieler melden")),
                    "Regeln lesen", "regeln"),
            new Page("einstieg", "Einstieg", Ui.Theme.GOLD, Ui.Icon.SPAWN, "EINSTIEG", "So startest du auf Survival",
                    List.of("Am Spawn bist du sicher. Gebaut wird in der Bauwelt, dahin kommst du mit /bauwelt oder über den Brunnen am Spawn.",
                            "Starte am besten mit der Tour: /tutorial zeigt dir in ein paar Stationen alles Wichtige.",
                            "Such dir einen Platz und sichere ihn mit der Claim-Axt. Dann kann dort niemand etwas kaputt machen.",
                            "Wähle einen Job, hol Rohstoffe aus der Farmwelt und erledige Quests. So kommen Coins und Belohnungen fast von allein."),
                    List.of(c("/tutorial", "Tour starten"), c("/bauwelt", "Zur Bauwelt"), c("/spawn", "Zurück zum Spawn"),
                            c("/claim axt", "Claim-Axt holen"), c("/job", "Job wählen"), c("/quest", "Deine Quests")),
                    "Tour starten", "tutorial"),
            new Page("grundstuecke", "Grundstücke", Ui.Theme.GOLD, Ui.Icon.HOUSE, "GRUNDSTÜCKE", "Dein Land, deine Regeln",
                    List.of("Mit der Claim-Axt steckst du dein Land ab: Linksklick auf eine Ecke, Rechtsklick auf die andere, dann /claim ok.",
                            "Ein Grundstück kostet Claim-Blöcke, so viele wie Boden drin liegt. Ein 10×10 kostet 100. Fürs Spielen bekommst du immer neue dazu.",
                            "Zu klein geworden? Mit der Claim-Axt kannst du dein Grundstück erweitern.",
                            "Freunde trägst du mit /claim trust ein. Was Fremde bei dir dürfen, stellst du im Menü ein."),
                    List.of(c("/claim", "Deine Grundstücke"), c("/claim axt", "Claim-Axt holen"), c("/claim ok", "Auswahl anlegen"),
                            c("/claim trust <Spieler>", "Freund eintragen"), c("/claim zeigen", "Grenzen anzeigen"), c("/claim hilfe", "Alle Befehle")),
                    "Menü öffnen", "claim"),
            new Page("jobs", "Jobs", Ui.Theme.GRAY, Ui.Icon.PICKAXE, "JOBS", "Coins für das, was du sowieso machst",
                    List.of("Ein Job bezahlt dich fürs Abbauen, Ernten, Holzhacken, Angeln, Jagen und mehr.",
                            "Jede Aufgabe bringt Coins und Job-XP. Mit Job-XP steigt dein Level, und es bleibt für immer.",
                            "Es gibt ein Limit für Coins. Mit jedem Level wird es größer und die Aufgaben zahlen mehr.",
                            "Wechseln geht jederzeit, dein Level bleibt. Danach musst du eine Weile warten."),
                    List.of(c("/job", "Job-Menü"), c("/job info <Beruf>", "Aufgaben eines Berufs"), c("/job waehlen <Beruf>", "Beruf wählen"),
                            c("/job limit", "Was heute noch geht"), c("/job auftrag", "Dein Wochenauftrag"), c("/job hilfe", "Alle Befehle")),
                    "Menü öffnen", "job"),
            new Page("quests", "Quests", Ui.Theme.ORANGE, Ui.Icon.SCROLL, "QUESTS", "Aufgaben mit Belohnung",
                    List.of("Jeden Tag gibt es neue Quests, jede Woche größere. Dazu kommen eine lange Geschichte, Meilensteine, Team-Quests und ein paar geheime.",
                            "Fertige Quests holst du im Menü ab. Wer jeden Tag Quests macht, baut eine Serie auf und bekommt Extra-Belohnungen.",
                            "Eine Quest, die du verfolgst, siehst du oben am Bildschirm."),
                    List.of(c("/quest", "Quest-Menü"), c("/quest daily", "Tages-Quests"), c("/quest weekly", "Wochen-Quests"),
                            c("/quest abholen", "Belohnungen abholen"), c("/quest streak", "Deine Serie"), c("/quest top", "Rangliste")),
                    "Menü öffnen", "quest"),
            new Page("nebenquests", "Nebenquests", Ui.Theme.ORANGE, Ui.Icon.MAP, "NEBENQUESTS", "Kleine Geschichten bei Figuren",
                    List.of("Nebenquests bekommst du bei Figuren in der Welt. Jede Figur hat mehrere Aufgaben, die nacheinander kommen.",
                            "/sidequests zeigt dir alle Nebenquests. Die Wegweisung bringt dich zur richtigen Figur.",
                            "Eine laufende Nebenquest kannst du jederzeit abbrechen."),
                    List.of(c("/sidequests", "Alle Nebenquests"), c("/sidequests nav", "Wegweisung an/aus"), c("/sidequests cancel", "Laufende abbrechen")),
                    "Menü öffnen", "sidequests"),
            new Page("farmwelt", "Farmwelt", Ui.Theme.BLUE, Ui.Icon.PICKAXE, "FARMWELT", "Rohstoffe ohne Ende",
                    List.of("Die Farmwelt ist zum Abbauen da. Hier darfst du alles farmen, Grundstücke gibt es dort nicht.",
                            "Jede Woche wird sie komplett neu gemacht. Bau dort nichts, was du behalten willst.",
                            "Homes kannst du in der Farmwelt nicht setzen."),
                    List.of(c("/farmwelt", "In die Farmwelt"), c("/farmwelt info", "Infos zur Farmwelt")),
                    "Hinreisen", "farmwelt"),
            new Page("geld", "Geld & Markt", Ui.Theme.GOLD, Ui.Icon.COINS, "GELD & MARKT", "Coins verdienen, kaufen, verkaufen",
                    List.of("Coins verdienst du mit Jobs, Quests, beim Voten und wenn du Sachen an andere Spieler verkaufst.",
                            "Im Markt bietest du Items zum Festpreis an: Item in die Hand nehmen und /markt sell <Preis> eingeben.",
                            "Was du gekauft hast oder was nicht verkauft wurde, wartet in deiner Abholbox."),
                    List.of(c("/money", "Dein Kontostand"), c("/markt", "Markt öffnen"), c("/markt sell <Preis>", "Item aus der Hand anbieten"),
                            c("/abholbox", "Deine Abholbox"), c("/trade <Spieler>", "Sicher tauschen")),
                    "Markt öffnen", "markt"),
            new Page("laeden", "Läden & Warps", Ui.Theme.RED, Ui.Icon.STALL, "LÄDEN & WARPS", "Eigener Laden, eigener Warp",
                    List.of("Spieler-Warps bringen andere zu deinem Laden oder Bauwerk. Mit /sw siehst du alle und reist mit einem Klick hin.",
                            "Einen eigenen Warp kaufst du mit /sw create <Name> genau dort, wo du stehst. Du kannst bis zu 3 haben: Der erste kostet 50.000 Coins, der zweite 150.000 und der dritte 500.000.",
                            "Für einen Laden setzt du ein Schild an eine Kiste und schreibst [Shop] in die erste Zeile. Das geht in jedem Grundstück, in dem du bauen darfst.",
                            "Mit einem Klick aufs Schild kaufen und verkaufen deine Kunden. Du verwaltest dort deinen Laden."),
                    List.of(c("/sw", "Alle Spieler-Warps"), c("/sw create <Name>", "Eigenen Warp kaufen"), c("/sw manage", "Deine Warps"),
                            c("/chestshop", "Deine Läden"), c("/chestshop help", "Kurze Anleitung")),
                    "Warps ansehen", "sw"),
            new Page("tokens", "Tokens", Ui.Theme.GOLD, Ui.Icon.TOKEN, "TOKENS", "Belohnung fürs Spielen",
                    List.of("Tokens bekommst du fürs Spielen: einen pro Stunde online, dazu einen Tagesbonus und Extra für viele Tage in Folge. Auch beim Voten gibt es welche.",
                            "Tokens kann man nicht kaufen und nicht tauschen.",
                            "Im Token-Shop gibt es dafür Claim-Blöcke, einen Rucksack, besondere Verzauberungen, Booster und Bauhelfer.",
                            "Deinen Rucksack öffnest du jederzeit mit /backpack."),
                    List.of(c("/tokens", "Dein Stand"), c("/tokenshop", "Token-Shop"), c("/backpack", "Rucksack öffnen"), c("/backpack item", "Rucksack-Item holen")),
                    "Token-Shop", "tokenshop"),
            new Page("level", "Level", Ui.Theme.GREEN, Ui.Icon.STAR, "LEVEL", "Zeigt, wie viel du spielst",
                    List.of("Dein Level zeigt, wie viel du auf dem Server spielst.",
                            "XP gibt es fürs Online-Sein, für Jobs und für Quests. Jedes Level braucht etwas mehr als das davor."),
                    List.of(c("/level", "Dein Level"), c("/level <Spieler>", "Level eines anderen"), c("/level top", "Rangliste")),
                    "Dein Level", "level"),
            new Page("farmpass", "Farmpass", Ui.Theme.GREEN, Ui.Icon.LEAF, "FARMPASS", "Monats-Pass mit Vorteilen",
                    List.of("Der Farmpass gibt dir Vorteile, solange er läuft: Fliegen am Spawn, im Casino und auf deinen und den Grundstücken deiner Freunde, ein Nebenjob, keine Gebühren im Auktionshaus und im Grundstücks-Markt und keine Casino-Steuer.",
                            "Dazu 2 Homes mehr, mehr Plätze im Auktionshaus, ein Grab für 60 statt 30 Minuten, /werkbank und /ec überall, eine Wochenkiste, 4 Haustiere nur für den Pass und ein grünes Blatt in Tab und Chat.",
                            "Für jeden bezahlten Monat darfst du dir ein Haustier aussuchen und bekommst 250 Claim-Blöcke. Beides bleibt für immer.",
                            "Den Farmpass gibt es nur im Store: 9,99 € im Monat, jederzeit kündbar."),
                    List.of(c("/farmpass", "Vorteile und Store-Link"), c("/farmpass pet", "Monats-Haustier wählen"), c("/farmpass fly", "Fliegen an/aus")),
                    "Vorteile ansehen", "farmpass"),
            new Page("casino", "Casino", Ui.Theme.CASINO, Ui.Icon.DICE, "CASINO", "Slots, Roulette, Blackjack, Poker und Crash",
                    List.of("Im Casino spielst du mit Chips an Slots, Roulette, Blackjack und Crash. In der VIP-Lounge gibt es Poker.",
                            "Chips tauschst du an der Kasse gegen Coins und wieder zurück.",
                            "Jede Woche wird der Jackpot ausgelost. Wer seit der letzten Ziehung gespielt hat, ist dabei."),
                    List.of(c("/casino", "Ins Casino"), c("/casino regeln", "Casino-Hilfe"), c("/casino stats", "Rangliste")),
                    "Casino-Hilfe", null),
            new Page("haustiere", "Haustiere", Ui.Theme.ORANGE, Ui.Icon.PAW, "HAUSTIERE", "Begleiter, die dir folgen",
                    List.of("Haustiere laufen dir hinterher und sehen einfach gut aus. Auf manchen kannst du sogar reiten.",
                            "Du bekommst sie mit dem Farmpass, aus Kisten, mit einem Rang oder kaufst sie im Haustier-Menü.",
                            "Im Menü rufst du dein Haustier, gibst ihm einen eigenen Namen und markierst Lieblinge."),
                    List.of(c("/pet", "Haustier-Menü"), c("/pet summon <Name>", "Haustier rufen"), c("/pet dismiss", "Haustier wegschicken"), c("/pet ride", "Aufsteigen")),
                    "Menü öffnen", "pet"),
            new Page("teams", "Teams", Ui.Theme.BLUE, Ui.Icon.FLAG, "TEAMS", "Zusammen spielen",
                    List.of("Gründe ein Team mit deinen Freunden. Ihr bekommt einen eigenen Chat, Team-Homes, ein Lager und eine Kasse.",
                            "Je mehr ihr zusammen spielt, desto höher steigt euer Team-Level. Das bringt mehr Plätze."),
                    List.of(c("/team", "Team-Menü"), c("/team create <Name>", "Team gründen"), c("/team invite <Spieler>", "Spieler einladen"),
                            c("/team accept", "Einladung annehmen"), c("/tc <Text>", "Team-Chat"), c("/team help", "Alle Befehle")),
                    "Menü öffnen", "team"),
            new Page("homes", "Homes", Ui.Theme.RED, Ui.Icon.BED, "HOMES", "Deine Orte zum Hinspringen",
                    List.of("Ein Home ist ein Ort, zu dem du dich jederzeit zurück teleportierst.",
                            "Jeder hat ein Home. Ab Platin kommt mit jedem Rang eins dazu, eins mehr gibt es fürs Verlinken mit Discord.",
                            "Setzen geht in der Oberwelt, im Nether und im End. Am Spawn, im Casino und in der Farmwelt nicht."),
                    List.of(c("/sethome [Name]", "Home setzen"), c("/home [Name]", "Zum Home"), c("/homes", "Alle Homes"), c("/delhome <Name>", "Home löschen")),
                    "Deine Homes", "homes"),
            new Page("voten", "Voten", Ui.Theme.GREEN, Ui.Icon.CHECK, "VOTEN", "Stimme ab und hol dir Belohnungen",
                    List.of("Stimme jeden Tag auf den Vote-Seiten für den Server ab. Für jede Stimme gibt es eine Belohnung.",
                            "Wer viele Tage hintereinander votet, baut eine Serie auf: ab 10 Tagen gibt es je Vote-Tag 250 Coins extra, ab 25 Tagen 500 und ab 50 Tagen 1.000. Die besten Voter des Monats bekommen extra Preise."),
                    List.of(c("/vote", "Links und Belohnungen")),
                    "Vote-Menü", "vote"),
            new Page("einstellungen", "Einstellungen", Ui.Theme.GRAY, Ui.Icon.GEAR, "EINSTELLUNGEN", "Stell den Server nach deinem Geschmack ein",
                    List.of("In /settings schaltest du zum Beispiel private Nachrichten, Freundesanfragen, Sounds, das Scoreboard und die Info-Leiste an und aus.",
                            "Was oben in der Info-Leiste steht, stellst du mit /bossbar ein."),
                    List.of(c("/settings", "Deine Einstellungen"), c("/bossbar", "Info-Leiste einrichten")),
                    "Einstellungen", "settings"),
            new Page("heiraten", "Heiraten", Ui.Theme.PINK, Ui.Icon.RINGS, "HEIRATEN", "Antrag, Hochzeit und Ehe-Konto",
                    List.of("Mach einem Spieler mit /marry propose einen Antrag.",
                            "Das Ja-Wort gebt ihr euch beim Priester in der Kirche, beide müssen dort stehen. Die Hochzeit bezahlt, wer den Antrag gemacht hat.",
                            "Verheiratet steht ein Herz vor deinem Namen im Chat.",
                            "Mit /bank eröffnet ihr ein Ehe-Konto und zahlt überall ein und aus, große Beträge gibt es nur an der Bank am Spawn. Dort gehen auch Konten mit Freunden.",
                            "Bei einer Scheidung wird das Ehe-Konto halbiert, jeder bekommt seinen Teil. Danach dürft ihr eine Zeit lang nicht neu heiraten."),
                    List.of(c("/marry", "Ehe-Menü"), c("/marry propose <Spieler>", "Antrag machen"), c("/marry accept", "Antrag annehmen, beim Priester"),
                            c("/marry church", "Zur Kirche"), c("/marry tp", "Zu deinem Partner"), c("/bank", "Bank und Ehe-Konto"), c("/marry divorce", "Scheidung")),
                    "Menü öffnen", "marry"),
            new Page("juwelen", "Juwelen & Shop", Ui.Theme.GREEN, Ui.Icon.GEM, "JUWELEN & SHOP", "Ränge, Keys, Möbel und mehr",
                    List.of("Juwelen bekommst du im Store. Ein paar gibt es auch für besondere Quests.",
                            "Im Juwelenshop gibt es dafür Ränge, Crate-Keys, Vorteilspakete, Möbel und Haustiere.",
                            "Ränge, Keys und Pakete kannst du auch an andere Spieler verschenken. Bezahlt wird mit deinen Juwelen.",
                            "Deko-Köpfe findest du mit /heads. Jeder Kopf kostet 1.000 Coins."),
                    List.of(c("/juwelen", "Dein Juwelen-Stand"), c("/juwelenshop", "Juwelenshop öffnen"), c("/juwelenshop gift", "Etwas verschenken"),
                            c("/heads", "Kopf-Datenbank"), c("/heads <Suchbegriff>", "Kopf suchen")),
                    "Shop öffnen", "juwelenshop"),
            new Page("tod", "Tod & Gräber", Ui.Theme.GRAY, Ui.Icon.SKULL, "TOD & GRÄBER", "Deine Sachen nach dem Tod",
                    List.of("Stirbst du, entsteht an der Stelle ein Grabstein. Darin liegen alle deine Sachen und deine XP.",
                            "30 Minuten lang kommst nur du an dein Grab. Ein Rechtsklick darauf legt alles, was passt, zurück in dein Inventar.",
                            "Danach zerfällt das Grab und alles fällt heraus. Dann kann es jeder aufheben. Mit dem Farmpass hält dein Grab länger.",
                            "Mit /grab siehst du alle deine Gräber mit Welt, Koordinaten und Restzeit."),
                    List.of(c("/grab", "Deine Gräber")),
                    "Deine Gräber", "grab"),
            new Page("moebel", "Möbel & Sitzen", Ui.Theme.ORANGE, Ui.Icon.CHAIR, "MÖBEL & SITZEN", "Einrichten und hinsetzen",
                    List.of("Möbel kaufst du im Juwelenshop. Du suchst dir jedes Stück selbst aus, der Preis hängt von der Größe ab.",
                            "Rechtsklick auf einem Grundstück, auf dem du bauen darfst, stellt ein Möbel auf. Shift + Rechtsklick dreht es, Shift + Linksklick hebt es wieder auf. Bis zu 50 Möbel gehen gleichzeitig.",
                            "Auf Stühle, Bänke und Hocker setzt du dich mit Rechtsklick, überall auf Survival. Aufstehen mit Shift.",
                            "Mit /sit setzt du dich auf den Boden, mit /lay legst du dich hin. Wer liegt, zählt beim Überspringen der Nacht mit."),
                    List.of(c("/juwelenshop", "Möbel kaufen"), c("/sit", "Hinsetzen"), c("/lay", "Hinlegen")),
                    "Möbel kaufen", "juwelenshop"),
            new Page("support", "Support", Ui.Theme.RED, Ui.Icon.LIFEBUOY, "SUPPORT", "Fragen stellen, Hilfe bekommen",
                    List.of("Du hast eine Frage? Mit /support schreibst du ein Ticket: einen kurzen Titel und deine Frage.",
                            "Erfahrene Spieler helfen dort als Helfer. Sie antworten im Ticket, und du bekommst eine Nachricht im Chat.",
                            "Kommt ein Helfer nicht weiter, gibt er dein Ticket an das Team. Ist deine Frage gelöst, bestätigst du das und vergibst 1 bis 5 Sterne.",
                            "Willst du einen Spieler melden oder geht es um eine Strafe? Dann öffne ein Ticket in unserem Discord. Den Link bekommst du mit /discord.",
                            "Wer schon lange spielt und seinen Discord verlinkt hat, kann selbst Helfer werden."),
                    List.of(c("/support", "Support-Menü"), c("/support new", "Neues Ticket"), c("/support top", "Beste Helfer"),
                            c("/support helper", "Helfer werden"), c("/discord", "Discord-Link")),
                    "Support-Menü", "support"),
            new Page("discord", "Discord", Ui.Theme.BLUE, Ui.Icon.DISCORD, "DISCORD", "Verlinken und Vorteile sichern",
                    List.of("Auf unserem Discord findest du Neuigkeiten, andere Spieler und Tickets für Meldungen an das Team.",
                            "Verlinke deinen Account: Geh im Discord in den Kanal „verify“ und klick auf „Verifizieren“. /verify zeigt dir den Weg.",
                            "Dafür bekommst du einen Haken hinter deinem Namen und ein Home mehr.",
                            "Ohne Verlinkung kannst du keine Spielzeit-Belohnungen abholen und kein Helfer im Support werden."),
                    List.of(c("/discord", "Discord-Link"), c("/verify", "So verlinkst du dich")),
                    "Anleitung zeigen", "verify"),
            new Page("freunde", "Freunde", Ui.Theme.GREEN, Ui.Icon.PEOPLE, "FREUNDE", "Freunde finden und verwalten",
                    List.of("Mit /friend add schickst du einem Spieler eine Freundschaftsanfrage. Nimmt er sie an, seid ihr Freunde.",
                            "In /friends siehst du, wer gerade online ist und auf welchem Server. Von dort schreibst du Freunden oder springst ihnen nach.",
                            "Mit einem Rang passen mehr Freunde in deine Liste.",
                            "Keine Lust auf Anfragen? In /settings schaltest du sie ab."),
                    List.of(c("/friends", "Freundesliste"), c("/friend add <Spieler>", "Anfrage schicken"), c("/friend accept <Spieler>", "Anfrage annehmen"),
                            c("/friend jump <Spieler>", "Nachspringen"), c("/friend remove <Spieler>", "Freund entfernen")),
                    "Freundesliste", "friends"),
            new Page("spielzeit", "Spielzeit", Ui.Theme.GOLD, Ui.Icon.HOURGLASS, "SPIELZEIT", "Belohnungen fürs Dabeisein",
                    List.of("Je länger du auf Survival spielst, desto mehr Stufen schaltest du frei.",
                            "Jede Stufe bringt Coins und Tokens, viele auch Kisten-Schlüssel oder Juwelen.",
                            "Fertige Stufen holst du in /playtime ab. Mit „Alle abholen“ geht alles auf einmal.",
                            "Zum Abholen muss dein Discord verlinkt sein."),
                    List.of(c("/playtime", "Deine Stufen"), c("/playtime claim", "Alles abholen")),
                    "Belohnungen ansehen", "spielzeit"),
            new Page("kosmetik", "Kosmetik", Ui.Theme.PINK, Ui.Icon.HAT, "KOSMETIK", "Hüte, Trails und Haustiere",
                    List.of("In /cosmetics findest du Hüte, Trails und Haustiere. Ein Trail ist eine Spur aus Partikeln, die dir beim Laufen folgt.",
                            "Viele Teile kaufst du dort mit Juwelen. Manche gibt es nur aus Kisten.",
                            "Was du einmal hast, behältst du und kannst es jederzeit an- und ablegen."),
                    List.of(c("/cosmetics", "Kosmetik-Menü"), c("/pet", "Haustier-Menü")),
                    "Kosmetik öffnen", "cosmetics"),
            new Page("kisten", "Kisten", Ui.Theme.ORANGE, Ui.Icon.CHEST, "KISTEN", "Schlüssel einlösen und gewinnen",
                    List.of("Die Kisten stehen am Spawn. Rechtsklick öffnet eine Kiste, Linksklick zeigt dir, was drin sein kann.",
                            "Zum Öffnen brauchst du den passenden Schlüssel. Schlüssel gibt es beim Voten, für Spielzeit, bei Events und im Juwelenshop.",
                            "Deine Schlüssel liegen nicht im Inventar. Wie viele du hast, siehst du mit /keys.",
                            "Schleichen + Rechtsklick überspringt die Animation."),
                    List.of(c("/keys", "Deine Schlüssel"), c("/spawn", "Zu den Kisten"), c("/vote", "Schlüssel durch Voten")),
                    "Deine Schlüssel", "keys"),
            new Page("statistik", "Statistik", Ui.Theme.GRAY, Ui.Icon.CHART, "STATISTIK", "Deine Zahlen und Ranglisten",
                    List.of("Mit /stats siehst du unter anderem deine Spielzeit, wie viel du abgebaut hast und wie oft du gestorben bist.",
                            "Die Zahlen von anderen Spielern schaust du dir mit /stats und ihrem Namen an, auch wenn sie offline sind.",
                            "Ranglisten gibt es für Geld, Level, Quests und das Casino."),
                    List.of(c("/stats", "Deine Statistik"), c("/stats <Spieler>", "Statistik eines anderen"), c("/baltop", "Reichste Spieler"),
                            c("/level top", "Level-Rangliste"), c("/quest top", "Quest-Rangliste"), c("/casino stats", "Casino-Rangliste")),
                    "Deine Statistik", "stats"),
            new Page("infoleiste", "Info-Leiste", Ui.Theme.BLUE, Ui.Icon.BAR, "INFO-LEISTE", "Was oben am Bildschirm steht",
                    List.of("Oben am Bildschirm zeigt dir die Info-Leiste deine Coins, Juwelen, deinen Job, wo du gerade bist und deine Quest.",
                            "Mit /bossbar suchst du aus, was dort stehen soll. Auch die Uhrzeit kannst du dazuschalten.",
                            "Mit /bossbar off blendest du die Leiste ganz aus, mit /bossbar on kommt sie zurück."),
                    List.of(c("/bossbar", "Leiste einrichten"), c("/bossbar on", "Einblenden"), c("/bossbar off", "Ausblenden"), c("/bossbar reset", "Zurücksetzen")),
                    "Leiste einrichten", "bossbar"),
            new Page("angeln", "Angeln", Ui.Theme.BLUE, Ui.Icon.ROD, "ANGELN", "Fische fangen, Rekorde jagen",
                    List.of("Angeln geht überall in Survival, in den Angelzonen beißen seltene Fische öfter.",
                            "Jeder Fang bringt Angel-XP. Mit jedem Level verdienst du mehr, und seltene Fische werden häufiger.",
                            "Im Fischbuch siehst du alle Arten, deine Fänge und deine Rekorde. Verkaufen kannst du im Angel-Menü oder beim Fischhändler.",
                            "Je besser die Rute, desto schneller beißt es und desto öfter hängen seltene Fische dran."),
                    List.of(c("/angeln", "Angel-Menü: Fischbuch, Rute, Verkauf, Bestenliste")),
                    "Menü öffnen", "angeln"));

    private SurvivalHelp() {
    }

    /** Öffnet die erste Seite oder die Seite mit dieser Kennung (auch über den Navigationsnamen). False, wenn unbekannt. */
    public static boolean open(KubyPlugins plugin, Player p, String page) {
        if (page == null || page.isBlank()) {
            show(plugin, p, 0);
            return true;
        }
        String key = page.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue");
        for (int i = 0; i < PAGES.size(); i++) {
            Page pg = PAGES.get(i);
            String nav = pg.nav().toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue");
            if (pg.id().equals(key) || nav.startsWith(key)) {
                show(plugin, p, i);
                return true;
            }
        }
        return false;
    }

    public static List<String> pageIds() {
        List<String> out = new ArrayList<>();
        for (Page p : PAGES) out.add(p.id());
        return out;
    }

    private static void show(KubyPlugins plugin, Player p, int index) {
        int size = PAGES.size();
        int i = Math.floorMod(index, size);
        Page page = PAGES.get(i);
        Page prev = PAGES.get(Math.floorMod(i - 1, size));
        Page next = PAGES.get(Math.floorMod(i + 1, size));
        List<ActionButton> buttons = new ArrayList<>();
        boolean textured = plugin.uiPack().active(p);
        buttons.add(button(textured ? Ui.button("< " + prev.nav(), false) : Msg.parse("<white>< " + Msg.escape(prev.nav())),
                "Zurück: " + prev.nav(), pl -> show(plugin, pl, i - 1)));
        buttons.add(button(textured ? Ui.button(page.actionLabel(), page.theme())
                        : Msg.parse("<b>" + Msg.escape(page.actionLabel()) + "</b>"),
                page.actionLabel(), pl -> act(plugin, pl, page)));
        buttons.add(button(textured ? Ui.button(next.nav() + " >", false) : Msg.parse("<white>" + Msg.escape(next.nav()) + " >"),
                "Weiter: " + next.nav(), pl -> show(plugin, pl, i + 1)));
        p.showDialog(textured ? textured(page, buttons) : plain(page, buttons));
    }

    private static void act(KubyPlugins plugin, Player p, Page page) {
        p.closeInventory();
        if (page.action() == null) {
            CasinoRules.open(plugin, p, null);
            return;
        }
        if (Bukkit.getServer().getCommandMap().getCommand(page.action()) == null) {
            Msg.send(p, "<gray>" + page.title().charAt(0) + page.title().substring(1).toLowerCase(Locale.ROOT) + ": kommt bald.");
            return;
        }
        p.performCommand(page.action());
    }

    private static Dialog textured(Page page, List<ActionButton> buttons) {
        Ui.Theme t = page.theme();
        Ui.Panel head = new Ui.Panel(t)
                .headIcon(Ui.Icon.BACK, 8)
                .headIcon(page.icon(), 26)
                .text(48, page.title(), t.title, true)
                .text(48, page.subtitle(), SUB, false);
        Ui.Panel how = new Ui.Panel(t).text(7, "SO GEHT'S", t.accent, true);
        for (String line : page.how()) how.bullet(line, BODY);
        List<Ui.Panel> panels = new ArrayList<>(List.of(head, how));
        if (!page.commands().isEmpty()) {
            Ui.Panel cmds = new Ui.Panel(t).text(7, "BEFEHLE", t.accent, true);
            int right = 8 + 6;
            for (String[] cmd : page.commands()) right = Math.max(right, 8 + Ui.width(cmd[0], false) + 12);
            for (String[] cmd : page.commands()) cmds.columns(cmd[0], CMD, cmd[1], BODY, Math.min(right, 150));
            panels.add(cmds);
        }
        DialogBase base = DialogBase.builder(Ui.titleBar("Hilfe"))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(Ui.stack(panels), WIDTH)))
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(3).build()));
    }

    private static Dialog plain(Page page, List<ActionButton> buttons) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Msg.parse("<gray>" + Msg.escape(page.subtitle())), WIDTH));
        StringBuilder sb = new StringBuilder("<gold><b>SO GEHT'S</b></gold>");
        for (String line : page.how()) sb.append("\n<gold>▪</gold> <white>").append(Msg.escape(line)).append("</white>");
        body.add(DialogBody.plainMessage(Msg.parse(sb.toString()), WIDTH));
        if (!page.commands().isEmpty()) {
            StringBuilder cmds = new StringBuilder("<gold><b>BEFEHLE</b></gold>");
            for (String[] cmd : page.commands()) {
                cmds.append("\n<aqua>").append(Msg.escape(cmd[0])).append("</aqua> <gray>").append(Msg.escape(cmd[1])).append("</gray>");
            }
            body.add(DialogBody.plainMessage(Msg.parse(cmds.toString()), WIDTH));
        }
        DialogBase base = DialogBase.builder(Msg.parse("<gold><b>" + Msg.escape(page.title()) + "</b>"))
                .canCloseWithEscape(true)
                .body(body)
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(3).build()));
    }

    private static ActionButton button(Component label, String tooltip, Consumer<Player> onClick) {
        return ActionButton.create(label, Msg.parse("<gray>" + Msg.escape(tooltip)), Ui.BUTTON_W,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) onClick.accept(pl);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }
}
