package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Mit Grafik-Pack auf dem Dialog-Kit: /hilfe zeigt zuerst die Übersicht mit allen Themen als Kacheln.
 * Aktionen rufen den passenden Befehl auf; gibt es ihn (noch) nicht, kommt ein Hinweis.
 */
public final class SurvivalHelp {

    private static final int WIDTH = 320;
    /** Breite der Kit-Ansicht. */
    private static final int W = 390;
    private static final TextColor CMD = TextColor.color(0x5FE3C4);
    private static final TextColor LIGHT = TextColor.color(0xE4E4EA);
    private static final TextColor GRAY = TextColor.color(0xA0A0AC);
    private static final TextColor ON_GOLD = TextColor.color(0x2C1C06);
    private static final TextColor GOLD = TextColor.color(0xF4D46A);
    private static final TextColor ON_GREEN = TextColor.color(0x0C2A12);

    /** Eine Hilfe-Seite. action: Befehl ohne Slash (null = Casino-Hilfe öffnen). */
    private record Page(String id, String nav, Ui.Theme theme, KitData.SmallIcon icon, String title, String subtitle,
                        List<String> how, List<String[]> commands, String actionLabel, String action) {}

    private static String[] c(String cmd, String desc) {
        return new String[]{cmd, desc};
    }

    private static final List<Page> PAGES = List.of(
            new Page("regeln", "Regeln", Ui.Theme.GREEN, KitData.SmallIcon.H_REGELN, "REGELN", "Fair spielen, gemeinsam bauen",
                    List.of("Auf Survival gelten zwölf Paragraphen, zum Beispiel zu Griefing, Claims, PvP und Chat. Mit /regeln liest du sie alle.",
                            "Die Regeln gelten für jeden Spieler, egal welcher Rang und wie lange schon dabei.",
                            "Wer gegen die Regeln verstößt, kann verwarnt, gemutet oder gebannt werden. Siehst du einen Verstoß, melde ihn mit /report."),
                    List.<String[]>of(c("/regeln", "Alle Regeln"), c("/regeln <Nummer>", "Einen Paragraphen lesen"), c("/report <Spieler>", "Spieler melden")),
                    "Regeln lesen", "regeln"),
            new Page("einstieg", "Einstieg", Ui.Theme.GOLD, KitData.SmallIcon.H_EINSTIEG, "EINSTIEG", "So startest du auf Survival",
                    List.of("Am Spawn bist du sicher. Gebaut wird in der Bauwelt, dahin kommst du mit /bauwelt oder über den Brunnen am Spawn.",
                            "Starte am besten mit der Tour: /tutorial zeigt dir in ein paar Stationen alles Wichtige.",
                            "Such dir einen Platz und sichere ihn mit der Claim-Axt. Dann kann dort niemand etwas kaputt machen.",
                            "Wähle einen Job, hol Rohstoffe aus der Farmwelt und erledige Quests. So kommen Coins und Belohnungen fast von allein."),
                    List.<String[]>of(c("/tutorial", "Tour starten"), c("/bauwelt", "Zur Bauwelt"), c("/spawn", "Zurück zum Spawn"),
                            c("/claim axt", "Claim-Axt holen"), c("/job", "Job wählen"), c("/quest", "Deine Quests")),
                    "Tour starten", "tutorial"),
            new Page("grundstuecke", "Grundstücke", Ui.Theme.GOLD, KitData.SmallIcon.H_AXE, "GRUNDSTÜCKE", "Dein Land, deine Regeln",
                    List.of("Mit der Claim-Axt steckst du dein Land ab: Linksklick auf eine Ecke, Rechtsklick auf die andere, dann /claim ok.",
                            "Ein Grundstück kostet Claim-Blöcke, so viele wie Boden drin liegt. Ein 10×10 kostet 100. Fürs Spielen bekommst du immer neue dazu.",
                            "Zu klein geworden? Mit der Claim-Axt kannst du dein Grundstück erweitern.",
                            "Freunde trägst du mit /claim trust ein. Was Fremde bei dir dürfen, stellst du im Menü ein."),
                    List.<String[]>of(c("/claim", "Deine Grundstücke"), c("/claim axt", "Claim-Axt holen"), c("/claim ok", "Auswahl anlegen"),
                            c("/claim trust <Spieler>", "Freund eintragen"), c("/claim zeigen", "Grenzen anzeigen"), c("/claim hilfe", "Alle Befehle")),
                    "Menü öffnen", "claim"),
            new Page("jobs", "Jobs", Ui.Theme.GRAY, KitData.SmallIcon.H_PICKAXE, "JOBS", "Coins für das, was du sowieso machst",
                    List.of("Ein Job bezahlt dich fürs Abbauen, Ernten, Holzhacken, Angeln, Jagen und mehr. Es gibt acht Berufe.",
                            "Jede fertige Aufgabe bringt Coins und Job-XP. Mit Job-XP steigt dein Level von 1 bis 100, und es bleibt für immer.",
                            "Pro Stunde gibt es ein Limit. Mit jedem Level wird es größer und die Aufgaben zahlen mehr.",
                            "Wechseln geht jederzeit, dein Level bleibt. Danach musst du 30 Minuten warten."),
                    List.<String[]>of(c("/job", "Job-Menü"), c("/job info <Beruf>", "Aufgaben eines Berufs"), c("/job waehlen <Beruf>", "Beruf wählen"),
                            c("/job limit", "Was diese Stunde noch geht"), c("/job auftrag", "Dein Wochenauftrag"), c("/job hilfe", "Alle Befehle")),
                    "Menü öffnen", "job"),
            new Page("quests", "Quests", Ui.Theme.ORANGE, KitData.SmallIcon.H_SCROLL, "QUESTS", "Aufgaben mit Belohnung",
                    List.of("Jeden Tag gibt es neue Quests, jede Woche größere. Dazu kommen eine lange Geschichte, Meilensteine, Team-Quests und ein paar geheime.",
                            "Fertige Quests holst du im Menü ab. Wer jeden Tag Quests macht, baut eine Serie auf und bekommt Extra-Belohnungen.",
                            "Eine Quest, die du verfolgst, siehst du oben am Bildschirm."),
                    List.<String[]>of(c("/quest", "Quest-Menü"), c("/quest daily", "Tages-Quests"), c("/quest weekly", "Wochen-Quests"),
                            c("/quest abholen", "Belohnungen abholen"), c("/quest streak", "Deine Serie"), c("/quest top", "Rangliste")),
                    "Menü öffnen", "quest"),
            new Page("nebenquests", "Nebenquests", Ui.Theme.ORANGE, KitData.SmallIcon.H_MAP, "NEBENQUESTS", "Kleine Geschichten bei Figuren",
                    List.of("Nebenquests bekommst du bei Figuren in der Welt. Jede Figur hat mehrere Aufgaben, die nacheinander kommen.",
                            "/sidequests zeigt dir alle Nebenquests. Die Wegweisung bringt dich zur richtigen Figur.",
                            "Eine laufende Nebenquest kannst du jederzeit abbrechen."),
                    List.<String[]>of(c("/sidequests", "Alle Nebenquests"), c("/sidequests nav", "Wegweisung an/aus"), c("/sidequests cancel", "Laufende abbrechen")),
                    "Menü öffnen", "sidequests"),
            new Page("farmwelt", "Farmwelt", Ui.Theme.BLUE, KitData.SmallIcon.H_FARMWELT, "FARMWELT", "Rohstoffe ohne Ende",
                    List.of("Die Farmwelt ist zum Abbauen da. Hier darfst du alles farmen, Grundstücke gibt es dort nicht.",
                            "Jede Woche wird sie komplett neu gemacht. Bau dort nichts, was du behalten willst.",
                            "Homes kannst du in der Farmwelt nicht setzen."),
                    List.<String[]>of(c("/farmwelt", "In die Farmwelt"), c("/farmwelt info", "Infos zur Farmwelt")),
                    "Hinreisen", "farmwelt"),
            new Page("geld", "Geld & Markt", Ui.Theme.GOLD, KitData.SmallIcon.H_COINS, "GELD & MARKT", "Coins verdienen, kaufen, verkaufen",
                    List.of("Coins verdienst du mit Jobs, Quests, beim Voten und wenn du Sachen an andere Spieler verkaufst.",
                            "Im Markt bietest du Items zum Festpreis an: Item in die Hand nehmen und /markt sell <Preis> eingeben.",
                            "Was du gekauft hast oder was nicht verkauft wurde, wartet in deiner Abholbox."),
                    List.<String[]>of(c("/money", "Dein Kontostand"), c("/markt", "Markt öffnen"), c("/markt sell <Preis>", "Item aus der Hand anbieten"),
                            c("/abholbox", "Deine Abholbox"), c("/trade <Spieler>", "Sicher tauschen")),
                    "Markt öffnen", "markt"),
            new Page("laeden", "Läden & Warps", Ui.Theme.RED, KitData.SmallIcon.H_STALL, "LÄDEN & WARPS", "Eigener Laden, eigener Warp",
                    List.of("Spieler-Warps bringen andere zu deinem Laden oder Bauwerk. Mit /sw siehst du alle und reist mit einem Klick hin.",
                            "Einen eigenen Warp kaufst du mit /sw create <Name> genau dort, wo du stehst. Du kannst bis zu 3 haben: Der erste kostet 50.000 Coins, der zweite 150.000 und der dritte 500.000.",
                            "Für einen Laden setzt du ein Schild an eine Kiste und schreibst [Shop] in die erste Zeile. Das geht in jedem Grundstück, in dem du bauen darfst.",
                            "Mit einem Klick aufs Schild kaufen und verkaufen deine Kunden. Du verwaltest dort deinen Laden."),
                    List.<String[]>of(c("/sw", "Alle Spieler-Warps"), c("/sw create <Name>", "Eigenen Warp kaufen"), c("/sw manage", "Deine Warps"),
                            c("/chestshop", "Deine Läden"), c("/chestshop help", "Kurze Anleitung")),
                    "Warps ansehen", "sw"),
            new Page("tokens", "Tokens", Ui.Theme.GOLD, KitData.SmallIcon.H_TOKEN, "TOKENS", "Belohnung fürs Spielen",
                    List.of("Tokens bekommst du fürs Spielen: einen pro Stunde online, dazu einen Tagesbonus und Extra für viele Tage in Folge. Auch beim Voten gibt es welche.",
                            "Tokens kann man nicht kaufen und nicht tauschen.",
                            "Im Token-Shop gibt es dafür Claim-Blöcke, einen Rucksack, besondere Verzauberungen, Booster und Bauhelfer.",
                            "Deinen Rucksack öffnest du jederzeit mit /backpack."),
                    List.<String[]>of(c("/tokens", "Dein Stand"), c("/tokenshop", "Token-Shop"), c("/backpack", "Rucksack öffnen"), c("/backpack item", "Rucksack-Item holen")),
                    "Token-Shop", "tokenshop"),
            new Page("level", "Level", Ui.Theme.GREEN, KitData.SmallIcon.H_LEVEL, "LEVEL", "Zeigt, wie viel du spielst",
                    List.of("Dein Level zeigt, wie viel du auf dem Server spielst.",
                            "XP gibt es fürs Online-Sein, für Jobs und für Quests. Jedes Level braucht etwas mehr als das davor."),
                    List.<String[]>of(c("/level", "Dein Level"), c("/level <Spieler>", "Level eines anderen"), c("/level top", "Rangliste")),
                    "Dein Level", "level"),
            new Page("farmpass", "Farmpass", Ui.Theme.GREEN, KitData.SmallIcon.H_FARMPASS, "FARMPASS", "Monats-Pass mit Vorteilen",
                    List.of("Der Farmpass gibt dir Vorteile, solange er läuft: Fliegen am Spawn, im Casino und auf deinen und den Grundstücken deiner Freunde, ein Nebenjob, keine Gebühren im Auktionshaus und im Grundstücks-Markt und keine Casino-Steuer.",
                            "Dazu 2 Homes mehr, mehr Plätze im Auktionshaus, ein Grab für 60 statt 30 Minuten, /werkbank und /ec überall, eine Wochenkiste, 4 Haustiere nur für den Pass und ein grünes Blatt in Tab und Chat.",
                            "Für jeden bezahlten Monat darfst du dir ein Haustier aussuchen und bekommst 250 Claim-Blöcke. Beides bleibt für immer.",
                            "Den Farmpass gibt es nur im Store: 9,99 € im Monat, jederzeit kündbar."),
                    List.<String[]>of(c("/farmpass", "Vorteile und Store-Link"), c("/farmpass pet", "Monats-Haustier wählen"), c("/farmpass fly", "Fliegen an/aus")),
                    "Vorteile ansehen", "farmpass"),
            new Page("casino", "Casino", Ui.Theme.CASINO, KitData.SmallIcon.H_CASINO, "CASINO", "Slots, Roulette, Blackjack, Poker und Crash",
                    List.of("Im Casino spielst du mit Chips an Slots, Roulette, Blackjack und Crash. In der VIP-Lounge gibt es Poker.",
                            "Chips tauschst du an der Kasse gegen Coins und wieder zurück. An der Losbude gibt es Rubbellose.",
                            "Jede Woche wird der Jackpot ausgelost. Wer seit der letzten Ziehung gespielt hat, ist dabei."),
                    List.<String[]>of(c("/casino", "Ins Casino"), c("/casino regeln", "Casino-Hilfe"), c("/casino stats", "Rangliste")),
                    "Casino-Hilfe", null),
            new Page("haustiere", "Haustiere", Ui.Theme.ORANGE, KitData.SmallIcon.H_PAW, "HAUSTIERE", "Begleiter, die dir folgen",
                    List.of("Haustiere laufen dir hinterher und sehen einfach gut aus. Auf manchen kannst du sogar reiten.",
                            "Du bekommst sie mit dem Farmpass, aus Kisten, mit einem Rang oder kaufst sie im Haustier-Menü.",
                            "Im Menü rufst du dein Haustier, gibst ihm einen eigenen Namen und markierst Lieblinge."),
                    List.<String[]>of(c("/pet", "Haustier-Menü"), c("/pet summon <Name>", "Haustier rufen"), c("/pet dismiss", "Haustier wegschicken"), c("/pet ride", "Aufsteigen")),
                    "Menü öffnen", "pet"),
            new Page("teams", "Teams", Ui.Theme.BLUE, KitData.SmallIcon.H_FLAG, "TEAMS", "Zusammen spielen",
                    List.of("Gründe ein Team mit deinen Freunden. Ihr bekommt einen eigenen Chat, Team-Homes, ein Lager und eine Kasse.",
                            "Je mehr ihr zusammen spielt, desto höher steigt euer Team-Level. Das bringt mehr Plätze."),
                    List.<String[]>of(c("/team", "Team-Menü"), c("/team create <Name>", "Team gründen"), c("/team invite <Spieler>", "Spieler einladen"),
                            c("/team accept", "Einladung annehmen"), c("/tc <Text>", "Team-Chat"), c("/team help", "Alle Befehle")),
                    "Menü öffnen", "team"),
            new Page("homes", "Homes", Ui.Theme.RED, KitData.SmallIcon.H_BED, "HOMES", "Deine Orte zum Hinspringen",
                    List.of("Ein Home ist ein Ort, zu dem du dich jederzeit zurück teleportierst.",
                            "Jeder hat ein Home. Ab Platin kommt mit jedem Rang eins dazu, eins mehr gibt es fürs Verlinken mit Discord.",
                            "Setzen geht in der Oberwelt, im Nether und im End. Am Spawn, im Casino und in der Farmwelt nicht."),
                    List.<String[]>of(c("/sethome [Name]", "Home setzen"), c("/home [Name]", "Zum Home"), c("/homes", "Alle Homes"), c("/delhome <Name>", "Home löschen")),
                    "Deine Homes", "homes"),
            new Page("voten", "Voten", Ui.Theme.GREEN, KitData.SmallIcon.H_BALLOT, "VOTEN", "Stimme ab und hol dir Belohnungen",
                    List.of("Stimme jeden Tag auf den Vote-Seiten für den Server ab. Für jede Stimme gibt es eine Belohnung.",
                            "Wer viele Tage hintereinander votet, baut eine Serie auf: ab 10 Tagen gibt es je Vote-Tag 250 Coins extra, ab 25 Tagen 500 und ab 50 Tagen 1.000. Die besten Voter des Monats bekommen extra Preise."),
                    List.<String[]>of(c("/vote", "Links und Belohnungen")),
                    "Vote-Menü", "vote"),
            new Page("einstellungen", "Einstellungen", Ui.Theme.GRAY, KitData.SmallIcon.H_GEAR, "EINSTELLUNGEN", "Stell den Server nach deinem Geschmack ein",
                    List.of("In /settings schaltest du zum Beispiel private Nachrichten, Freundesanfragen, Sounds, das Scoreboard und die Info-Leiste an und aus.",
                            "Was oben in der Info-Leiste steht, stellst du mit /bossbar ein."),
                    List.<String[]>of(c("/settings", "Deine Einstellungen"), c("/bossbar", "Info-Leiste einrichten")),
                    "Einstellungen", "settings"),
            new Page("heiraten", "Heiraten", Ui.Theme.PINK, KitData.SmallIcon.H_RINGS, "HEIRATEN", "Antrag, Hochzeit und Ehe-Konto",
                    List.of("Mach einem Spieler mit /marry propose einen Antrag.",
                            "Das Ja-Wort gebt ihr euch beim Priester in der Kirche, beide müssen dort stehen. Die Hochzeit bezahlt, wer den Antrag gemacht hat.",
                            "Verheiratet steht ein Herz vor deinem Namen im Chat.",
                            "Mit /bank eröffnet ihr ein Ehe-Konto und zahlt überall ein und aus, große Beträge gibt es nur an der Bank am Spawn. Dort gehen auch Konten mit Freunden.",
                            "Bei einer Scheidung wird das Ehe-Konto halbiert, jeder bekommt seinen Teil. Danach dürft ihr eine Zeit lang nicht neu heiraten."),
                    List.<String[]>of(c("/marry", "Ehe-Menü"), c("/marry propose <Spieler>", "Antrag machen"), c("/marry accept", "Antrag annehmen, beim Priester"),
                            c("/marry church", "Zur Kirche"), c("/marry tp", "Zu deinem Partner"), c("/bank", "Bank und Ehe-Konto"), c("/marry divorce", "Scheidung")),
                    "Menü öffnen", "marry"),
            new Page("juwelen", "Juwelen & Shop", Ui.Theme.GREEN, KitData.SmallIcon.H_GEM, "JUWELEN & SHOP", "Ränge, Keys, Möbel und mehr",
                    List.of("Juwelen bekommst du im Store. Ein paar gibt es auch für besondere Quests.",
                            "Im Juwelenshop gibt es dafür Ränge, Crate-Keys, Vorteilspakete, Möbel und Haustiere.",
                            "Ränge, Keys und Pakete kannst du auch an andere Spieler verschenken. Bezahlt wird mit deinen Juwelen.",
                            "Deko-Köpfe findest du mit /heads. Jeder Kopf kostet 1.000 Coins."),
                    List.<String[]>of(c("/juwelen", "Dein Juwelen-Stand"), c("/juwelenshop", "Juwelenshop öffnen"), c("/juwelenshop gift", "Etwas verschenken"),
                            c("/heads", "Kopf-Datenbank"), c("/heads <Suchbegriff>", "Kopf suchen")),
                    "Shop öffnen", "juwelenshop"),
            new Page("tod", "Tod & Gräber", Ui.Theme.GRAY, KitData.SmallIcon.H_GRAVE, "TOD & GRÄBER", "Deine Sachen nach dem Tod",
                    List.of("Stirbst du, entsteht an der Stelle ein Grabstein. Darin liegen alle deine Sachen und deine XP.",
                            "30 Minuten lang kommst nur du an dein Grab. Ein Rechtsklick darauf legt alles, was passt, zurück in dein Inventar.",
                            "Danach zerfällt das Grab und alles fällt heraus. Dann kann es jeder aufheben. Mit dem Farmpass hält dein Grab länger.",
                            "Mit /grab siehst du alle deine Gräber mit Welt, Koordinaten und Restzeit."),
                    List.<String[]>of(c("/grab", "Deine Gräber")),
                    "Deine Gräber", "grab"),
            new Page("moebel", "Möbel & Sitzen", Ui.Theme.ORANGE, KitData.SmallIcon.H_CHAIR, "MÖBEL & SITZEN", "Einrichten und hinsetzen",
                    List.of("Möbel kaufst du im Juwelenshop. Du suchst dir jedes Stück selbst aus, der Preis hängt von der Größe ab.",
                            "Rechtsklick auf einem Grundstück, auf dem du bauen darfst, stellt ein Möbel auf. Shift + Rechtsklick dreht es, Shift + Linksklick hebt es wieder auf. Bis zu 50 Möbel gehen gleichzeitig.",
                            "Auf Stühle, Bänke und Hocker setzt du dich mit Rechtsklick, überall auf Survival. Aufstehen mit Shift.",
                            "Mit /sit setzt du dich auf den Boden, mit /lay legst du dich hin. Wer liegt, zählt beim Überspringen der Nacht mit."),
                    List.<String[]>of(c("/juwelenshop", "Möbel kaufen"), c("/sit", "Hinsetzen"), c("/lay", "Hinlegen")),
                    "Möbel kaufen", "juwelenshop"),
            new Page("support", "Support", Ui.Theme.RED, KitData.SmallIcon.H_LIFEBUOY, "SUPPORT", "Fragen stellen, Hilfe bekommen",
                    List.of("Du hast eine Frage? Mit /support schreibst du ein Ticket: einen kurzen Titel und deine Frage.",
                            "Erfahrene Spieler helfen dort als Helfer. Sie antworten im Ticket, und du bekommst eine Nachricht im Chat.",
                            "Kommt ein Helfer nicht weiter, gibt er dein Ticket an das Team. Ist deine Frage gelöst, bestätigst du das und vergibst 1 bis 5 Sterne.",
                            "Willst du einen Spieler melden oder geht es um eine Strafe? Dann öffne ein Ticket in unserem Discord. Den Link bekommst du mit /discord.",
                            "Wer schon lange spielt und seinen Discord verlinkt hat, kann selbst Helfer werden."),
                    List.<String[]>of(c("/support", "Support-Menü"), c("/support new", "Neues Ticket"), c("/support top", "Beste Helfer"),
                            c("/support helper", "Helfer werden"), c("/discord", "Discord-Link")),
                    "Support-Menü", "support"),
            new Page("discord", "Discord", Ui.Theme.BLUE, KitData.SmallIcon.H_DISCORD, "DISCORD", "Verlinken und Vorteile sichern",
                    List.of("Auf unserem Discord findest du Neuigkeiten, andere Spieler und Tickets für Meldungen an das Team.",
                            "Verlinke deinen Account: Geh im Discord in den Kanal „verify“ und klick auf „Verifizieren“. /verify zeigt dir den Weg.",
                            "Dafür bekommst du einen Haken hinter deinem Namen und ein Home mehr.",
                            "Ohne Verlinkung kannst du keine Spielzeit-Belohnungen abholen und kein Helfer im Support werden."),
                    List.<String[]>of(c("/discord", "Discord-Link"), c("/verify", "So verlinkst du dich")),
                    "Anleitung zeigen", "verify"),
            new Page("freunde", "Freunde", Ui.Theme.GREEN, KitData.SmallIcon.H_PEOPLE, "FREUNDE", "Freunde finden und verwalten",
                    List.of("Mit /friend add schickst du einem Spieler eine Freundschaftsanfrage. Nimmt er sie an, seid ihr Freunde.",
                            "In /friends siehst du, wer gerade online ist und auf welchem Server. Von dort schreibst du Freunden oder springst ihnen nach.",
                            "Mit einem Rang passen mehr Freunde in deine Liste.",
                            "Keine Lust auf Anfragen? In /settings schaltest du sie ab."),
                    List.<String[]>of(c("/friends", "Freundesliste"), c("/friend add <Spieler>", "Anfrage schicken"), c("/friend accept <Spieler>", "Anfrage annehmen"),
                            c("/friend jump <Spieler>", "Nachspringen"), c("/friend remove <Spieler>", "Freund entfernen")),
                    "Freundesliste", "friends"),
            new Page("spielzeit", "Spielzeit", Ui.Theme.GOLD, KitData.SmallIcon.H_HOURGLASS, "SPIELZEIT", "Belohnungen fürs Dabeisein",
                    List.of("Je länger du auf Survival spielst, desto mehr Stufen schaltest du frei.",
                            "Jede Stufe bringt Coins und Tokens, viele auch Kisten-Schlüssel oder Juwelen.",
                            "Fertige Stufen holst du in /playtime ab. Mit „Alle abholen“ geht alles auf einmal.",
                            "Zum Abholen muss dein Discord verlinkt sein."),
                    List.<String[]>of(c("/playtime", "Deine Stufen"), c("/playtime claim", "Alles abholen")),
                    "Belohnungen ansehen", "spielzeit"),
            new Page("kosmetik", "Kosmetik", Ui.Theme.PINK, KitData.SmallIcon.H_HAT, "KOSMETIK", "Hüte, Trails und Haustiere",
                    List.of("In /cosmetics findest du Hüte, Trails und Haustiere. Ein Trail ist eine Spur aus Partikeln, die dir beim Laufen folgt.",
                            "Viele Teile kaufst du dort mit Juwelen. Manche gibt es nur aus Kisten.",
                            "Was du einmal hast, behältst du und kannst es jederzeit an- und ablegen."),
                    List.<String[]>of(c("/cosmetics", "Kosmetik-Menü"), c("/pet", "Haustier-Menü")),
                    "Kosmetik öffnen", "cosmetics"),
            new Page("kisten", "Kisten", Ui.Theme.ORANGE, KitData.SmallIcon.H_CHEST, "KISTEN", "Schlüssel einlösen und gewinnen",
                    List.of("Die Kisten stehen am Spawn. Rechtsklick öffnet eine Kiste, Linksklick zeigt dir, was drin sein kann.",
                            "Zum Öffnen brauchst du den passenden Schlüssel. Schlüssel gibt es beim Voten, für Spielzeit, bei Events und im Juwelenshop.",
                            "Deine Schlüssel liegen nicht im Inventar. Wie viele du hast, siehst du mit /keys.",
                            "Schleichen + Rechtsklick überspringt die Animation."),
                    List.<String[]>of(c("/keys", "Deine Schlüssel"), c("/spawn", "Zu den Kisten"), c("/vote", "Schlüssel durch Voten")),
                    "Deine Schlüssel", "keys"),
            new Page("statistik", "Statistik", Ui.Theme.GRAY, KitData.SmallIcon.H_CHART, "STATISTIK", "Deine Zahlen und Ranglisten",
                    List.of("Mit /stats siehst du unter anderem deine Spielzeit, wie viel du abgebaut hast und wie oft du gestorben bist.",
                            "Die Zahlen von anderen Spielern schaust du dir mit /stats und ihrem Namen an, auch wenn sie offline sind.",
                            "Ranglisten gibt es für Geld, Level, Quests und das Casino."),
                    List.<String[]>of(c("/stats", "Deine Statistik"), c("/stats <Spieler>", "Statistik eines anderen"), c("/baltop", "Reichste Spieler"),
                            c("/level top", "Level-Rangliste"), c("/quest top", "Quest-Rangliste"), c("/casino stats", "Casino-Rangliste")),
                    "Deine Statistik", "stats"),
            new Page("infoleiste", "Info-Leiste", Ui.Theme.BLUE, KitData.SmallIcon.H_INFOBAR, "INFO-LEISTE", "Was oben am Bildschirm steht",
                    List.of("Oben am Bildschirm zeigt dir die Info-Leiste deine Coins, Juwelen, deinen Job, wo du gerade bist und deine Quest.",
                            "Mit /bossbar suchst du aus, was dort stehen soll. Auch die Uhrzeit kannst du dazuschalten.",
                            "Mit /bossbar off blendest du die Leiste ganz aus, mit /bossbar on kommt sie zurück."),
                    List.<String[]>of(c("/bossbar", "Leiste einrichten"), c("/bossbar on", "Einblenden"), c("/bossbar off", "Ausblenden"), c("/bossbar reset", "Zurücksetzen")),
                    "Leiste einrichten", "bossbar"),
            new Page("angeln", "Angeln", Ui.Theme.BLUE, KitData.SmallIcon.H_ANGELN, "ANGELN", "Fische fangen, Rekorde jagen",
                    List.of("48 Fischarten in sechs Gewässern: Fluss, Ozean, warm, kalt, Sumpf und Mythen. Das Biom entscheidet.",
                            "Jeder Fang bringt Angel-Punkte fürs Level. Schwere Fische haben mehr Sterne und bringen mehr Geld.",
                            "In der Angel-Zone: +10 % Glück, Mythen-Fische und Schätze. Köder und Upgrades verbessern die Angel.",
                            "Verkaufen beim Fischhändler, Fische des Tages +50 %, jeden Tag eine Goldene Stunde und drei Aufträge."),
                    List.<String[]>of(c("/angeln", "Angel-Menü"), c("/angeln buch", "Fischbuch"), c("/angeln haendler", "Fische verkaufen"),
                            c("/angeln zone", "Zur Angel-Zone")),
                    "Menü öffnen", "angeln"));

    private SurvivalHelp() {
    }

    /** Öffnet die erste Seite oder die Seite mit dieser Kennung (auch über den Navigationsnamen). False, wenn unbekannt. */
    public static boolean open(KubyPlugins plugin, Player p, String page) {
        if (page == null || page.isBlank()) {
            if (plugin.uiPack().active(p)) overview(plugin, p);
            else show(plugin, p, 0);
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
        if (plugin.uiPack().active(p)) {
            UiDialog.show(p, screen(i, actions(plugin)), 300);
            return;
        }
        Page prev = PAGES.get(Math.floorMod(i - 1, size));
        Page next = PAGES.get(Math.floorMod(i + 1, size));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(Msg.parse("<white>< " + Msg.escape(prev.nav())), "Zurück: " + prev.nav(), pl -> show(plugin, pl, i - 1)));
        buttons.add(button(Msg.parse("<b>" + Msg.escape(page.actionLabel()) + "</b>"), page.actionLabel(), pl -> act(plugin, pl, page)));
        buttons.add(button(Msg.parse("<white>" + Msg.escape(next.nav()) + " >"), "Weiter: " + next.nav(), pl -> show(plugin, pl, i + 1)));
        p.showDialog(plain(page, buttons));
    }

    private static void overview(KubyPlugins plugin, Player p) {
        UiDialog.show(p, overview(actions(plugin)), 300);
    }

    private static Actions actions(KubyPlugins plugin) {
        return new Actions() {
            @Override
            public Consumer<Player> page(int index) {
                return pl -> show(plugin, pl, index);
            }

            @Override
            public Consumer<Player> act(int index) {
                return pl -> SurvivalHelp.act(plugin, pl, PAGES.get(Math.floorMod(index, PAGES.size())));
            }

            @Override
            public Consumer<Player> overview() {
                return pl -> SurvivalHelp.overview(plugin, pl);
            }
        };
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

    // =================================================================================================================
    // Dialog-Kit (mit Grafik-Pack)
    // =================================================================================================================

    /** Klicks der Kit-Ansicht. */
    public interface Actions {
        Consumer<Player> page(int index);

        Consumer<Player> act(int index);

        Consumer<Player> overview();
    }

    private static KitData.Box box(Ui.Theme t) {
        return switch (t) {
            case CASINO -> KitData.Box.CASINO;
            case GREEN -> KitData.Box.GREEN;
            case GOLD, ORANGE -> KitData.Box.GOLD;
            case GRAY -> KitData.Box.GRAY;
            case BLUE -> KitData.Box.NAVY;
            case RED -> KitData.Box.RED;
            case PINK -> KitData.Box.PURPLE;
        };
    }

    private static KitData.Button actionButton(Ui.Theme t) {
        return switch (t) {
            case CASINO, RED -> KitData.Button.RED;
            case GREEN -> KitData.Button.GREEN;
            case GOLD, ORANGE -> KitData.Button.GOLD;
            case GRAY -> KitData.Button.GOLD_LINE;
            case BLUE -> KitData.Button.TEAL;
            case PINK -> KitData.Button.PURPLE;
        };
    }

    /**
     * Eine Hilfe-Seite, Maße aus der Referenz (GUI-Pixel): Kopf 50 hoch mit Zurück-Knopf (9, 18) zur Übersicht, Icon
     * 18 x 18 (43, 18), Titel (74, 15) und Untertitel (74, 28); "SO GEHT'S" mit •-Aufzählung (Zeilen 11, Absätze +3),
     * "BEFEHLE" mit Beschreibung ab x = 133; unten "‹ vorherige", Aktion in der Themenfarbe (Mitte), "nächste ›".
     */
    public static Canvas screen(int index, Actions a) {
        int size = PAGES.size();
        int i = Math.floorMod(index, size);
        Page page = PAGES.get(i);
        Ui.Theme t = page.theme();
        KitData.Box style = box(t);
        // Casino: roter Kopf, dunkle Kästen wie in der Casino-Hilfe
        KitData.Box body = t == Ui.Theme.CASINO ? KitData.Box.CASINO_DARK : style;
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 50, style);
        c.back(9, 18);
        c.action(9, 18, 20, 20, a.overview(), "Alle Themen der Hilfe");
        c.smallIcon(43, 18, page.icon());
        c.text(74, 15, page.title(), t.title, true);
        c.text(74, 28, Canvas.fit(page.subtitle(), false, W - 86), GRAY, false);
        int y = 54;
        // So geht's: Zeilen im Abstand 11, zwischen den Punkten 3 mehr
        int ly = y + 23;
        List<int[]> rows = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int k = 0; k < page.how().size(); k++) {
            if (k > 0) ly += 3;
            List<String> w = Canvas.wrap(page.how().get(k), W - 29);
            for (int j = 0; j < w.size(); j++) {
                rows.add(new int[]{ly, j == 0 ? 1 : 0});
                texts.add(w.get(j));
                ly += 11;
            }
        }
        int h = ly - 11 + 7 + 16 - y;
        c.box(0, y, W, h, body);
        c.text(9, y + 9, "SO GEHT'S", t.accent, true);
        for (int k = 0; k < rows.size(); k++) {
            if (rows.get(k)[1] == 1) c.text(10, rows.get(k)[0], "•", t.accent, false);
            c.text(21, rows.get(k)[0], texts.get(k), LIGHT, false);
        }
        y += h + 13;
        if (!page.commands().isEmpty()) {
            h = 24 + 11 * (page.commands().size() - 1) + 7 + 11;
            c.box(0, y, W, h, body);
            c.text(9, y + 9, "BEFEHLE", t.accent, true);
            for (int k = 0; k < page.commands().size(); k++) {
                String[] cmd = page.commands().get(k);
                c.text(12, y + 24 + 11 * k, Canvas.fit(cmd[0], false, 118), CMD, false);
                c.text(133, y + 24 + 11 * k, Canvas.fit(cmd[1], false, W - 145), LIGHT, false);
            }
            y += h + 12;
        }
        Page prev = PAGES.get(Math.floorMod(i - 1, size));
        Page next = PAGES.get(Math.floorMod(i + 1, size));
        String back = "‹ " + prev.nav();
        String fwd = next.nav() + " ›";
        int bw = c.labelButton(0, y, 9, KitData.Button.DARK, back, LIGHT, false);
        c.action(0, y, bw, KitData.BTN_H, a.page(i - 1), "Zurück: " + prev.nav());
        int fw = Canvas.width(fwd, false) - 1 + 18;
        c.button(W - fw, y, fw, KitData.Button.DARK, fwd, LIGHT, false);
        c.action(W - fw, y, fw, KitData.BTN_H, a.page(i + 1), "Weiter: " + next.nav());
        KitData.Button act = actionButton(t);
        TextColor label = switch (act) {
            case GOLD -> ON_GOLD;
            case GREEN -> ON_GREEN;
            case GOLD_LINE -> GOLD;
            default -> NamedTextColor.WHITE;
        };
        int aw = Canvas.width(page.actionLabel(), true) - 1 + 24;
        c.button((W - aw) / 2, y, aw, act, page.actionLabel(), label, true);
        c.action((W - aw) / 2, y, aw, KitData.BTN_H, a.act(i), page.actionLabel());
        return c;
    }

    /** Übersicht: alle Themen als Kacheln (Icon und Name in der Farbe des Themas), Klick öffnet die Seite. */
    public static Canvas overview(Actions a) {
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 50, KitData.Box.NAVY);
        c.smallIcon(13, 16, KitData.SmallIcon.H_REGELN);
        c.text(40, 15, "HILFE", TextColor.color(0x64C0FF), true);
        c.text(40, 28, "Alles über Survival · klick auf ein Thema", GRAY, false);
        c.textRight(W - 12, 21, PAGES.size() + " Themen", GRAY, false);
        int cols = 4, gap = 3, tw = (W - gap * (cols - 1)) / cols, th = 24, top = 54;
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            int x = (i % cols) * (tw + gap) + (i % cols == cols - 1 ? W - (cols * tw + gap * (cols - 1)) : 0);
            int y = top + (i / cols) * (th + 3);
            c.box(x, y, tw, th, box(page.theme()));
            c.smallIcon(x + 3, y + 3, page.icon());
            // zu lange Namen ("Juwelen & Shop") auf den ersten Teil kürzen
            String label = page.nav();
            int room = tw - 26;
            if (Canvas.width(label, false) > room && label.contains(" & ")) label = label.substring(0, label.indexOf(" & "));
            c.text(x + 23, y + 9, Canvas.fit(label, false, room), LIGHT, false);
            c.action(x, y, tw, th, a.page(i), page.subtitle());
        }
        int rows = (PAGES.size() + cols - 1) / cols;
        c.textCenter(W / 2, top + rows * (th + 3) + 4, "Tipp: /hilfe <Thema> öffnet ein Thema direkt", GRAY, false);
        return c;
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
