package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubysurvival.quests.QuestScreen.*;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** /kp selftest: alle Quest-Seiten mit Grenzwerten (lange Namen, große Zahlen, volle Seiten, leere Seiten). */
public final class QuestSamples {

    private static final Consumer<Player> NO = p -> { };
    private static final String W16 = "WWWWWWWWWWWWWWWW";
    /** Größte Werte, die im Spiel vorkommen können (Belohnungen, Zähler, Tokens). */
    private static final long BIG = 99_999L, SMALL = 999L;

    private QuestSamples() {
    }

    private static final Actions ACTIONS = new Actions() {
        public Consumer<Player> area(Area a) { return NO; }
        public Consumer<Player> overview() { return NO; }
        public Consumer<Player> page(int page) { return NO; }
        public Consumer<Player> track() { return NO; }
        public Consumer<Player> ranking() { return NO; }
        public Consumer<Player> box() { return NO; }
        public Consumer<Player> streak() { return NO; }
        public Consumer<Player> help() { return NO; }
    };

    private static List<Reward> allRewards() {
        return List.of(Reward.of(Kind.COINS, BIG), Reward.of(Kind.CLAIMS, SMALL), Reward.of(Kind.TOKENS, SMALL), new Reward(Kind.TITLE, "Weltenbummler"));
    }

    private static List<Tab> tabs() {
        List<Tab> t = new ArrayList<>();
        for (Area a : Area.values()) if (a != Area.SIDE) t.add(new Tab(a, a == Area.EVENT ? W16 : a.label, "999/999"));
        return t;
    }

    private static Card card(Style s, Btn b, String label, Double progress) {
        return new Card(s, label, TextColor.color(0xFCCF79), label == null ? null : "ohne Tod", b == Btn.LOCKED ? Icon.LOCK : Icon.TASK,
                W16 + W16, "Eine sehr lange Beschreibung, die über mehrere Zeilen laufen muss, damit der Umbruch geprüft wird. "
                + "Noch ein Satz für die dritte Zeile.", allRewards(), progress, s == Style.BROWN, "99.999/99.999", b,
                b == Btn.LOCKED ? null : "Weg zeigen", b == Btn.LOCKED ? null : "100 %", b == Btn.LOCKED ? "Erst Kapitel 99" : null,
                b == Btn.LOCKED ? null : NO);
    }

    private static List<Stage> stages(boolean reached) {
        return List.of(new Stage(3, SMALL, 99, reached), new Stage(7, 5, 2, reached), new Stage(14, 10, 4, false), new Stage(999, SMALL, 99, false));
    }

    public static void register(BiConsumer<String, Supplier<Canvas>> reg) {
        Head head = new Head("Questmeister", "Legende", 0.5, 9999, BIG);
        Head top = new Head("Legende", null, 1, 0, 0);
        List<Tile> tiles = new ArrayList<>();
        for (Area a : Area.values()) tiles.add(new Tile(a, a == Area.EVENT ? W16 : a.label, Canvas.caps("Neu in") + " 23h", SMALL, a == Area.TEAM ? -1 : SMALL));
        Tracked tr = new Tracked(W16, W16 + W16, "Eine sehr lange Aufgabe. ".repeat(12), BIG, SMALL, SMALL, 0.99, "99/99 " + Canvas.caps("Ziele"), "Annehmen & verfolgen", W16);
        reg.accept("Quests Übersicht", () -> QuestScreen.overview(new Overview(head, tr, tiles), ACTIONS));
        reg.accept("Quests Übersicht leer", () -> QuestScreen.overview(new Overview(top, null, tiles), ACTIONS));

        Bonus bonus = new Bonus(12, 12, "23h", List.of(Reward.of(Kind.COINS, BIG), Reward.of(Kind.TOKENS, SMALL), Reward.of(Kind.ITEMS, SMALL)));
        List<Card> mixed = List.of(card(Style.GOLD, Btn.GOLD, null, 1.0), card(Style.RED, Btn.LINE, "Tages-Herausforderung", 0.5),
                card(Style.GRAY, Btn.LOCKED, null, null));
        for (Area a : Area.values()) {
            if (a == Area.SIDE) continue;
            reg.accept("Quests " + a.label, () -> QuestScreen.list(new ListView(a, W16, "99 von 99 Kapiteln · endet 31.12. um 23:59 Uhr",
                    "999/999", tabs(), mixed, null, null, 98, 99, a == Area.DAILY || a == Area.WEEKLY ? bonus : null), ACTIONS));
        }
        reg.accept("Quests Team ohne Team", () -> QuestScreen.list(new ListView(Area.TEAM, "Team", "Neue Aufträge in 23h", "0 %",
                tabs(), List.of(), "Kein Team", "W".repeat(120), 0, 1, null), ACTIONS));
        reg.accept("Quests Event leer", () -> QuestScreen.list(new ListView(Area.EVENT, "Event", "", "0/0",
                tabs(), List.of(), "Kein Event", "Gerade läuft kein Quest-Event.", 0, 1, null), ACTIONS));
        // Event mit Kapiteln: angenommenes Kapitel mit vier Zielen (lange Texte), Shop-Leiste; Candy-Shop voll und leer
        List<Reward> er = List.of(Reward.of(Kind.COINS, BIG), Reward.of(Kind.CANDY, SMALL), Reward.of(Kind.TOKENS, SMALL));
        String chapterText = "Ein sehr langer Kapiteltext, der umbrechen muss, damit der Umbruch geprüft wird."
                + "\n✔ Ein erledigtes Ziel mit sehr langem Text, der gekürzt oder umbrochen werden muss"
                + "\n▸ Sammle 99.999 Candy Corn zwischen 18 und 24 Uhr am Spawn · 12.345/99.999"
                + "\n▸ Besiege nachts 99.999 Zombies · 0/99.999\n▸ Wehr einen Überfall ab · 0/1";
        List<Card> chapters = List.of(
                new Card(Style.GOLD, null, null, null, Icon.TRACK, W16 + W16, chapterText, er, 0.5, false, "3/4 " + Canvas.caps("Ziele"), Btn.LINE,
                        "Verfolgt", Canvas.caps("Kapitel") + " 99", null, NO),
                new Card(Style.GOLD, null, null, null, Icon.QUEST, W16 + W16, "Kurz.", er, 0.0, false, "0/4 " + Canvas.caps("Ziele"), Btn.GOLD,
                        "Annehmen", Canvas.caps("Kapitel") + " 99", null, NO),
                new Card(Style.GRAY, null, null, null, Icon.LOCK, W16 + W16, "Gesperrt.", er, null, false, null, Btn.LOCKED, null, null,
                        "Ab Do. 29.10.", null));
        reg.accept("Quests Event Kapitel", () -> QuestScreen.list(new ListView(Area.EVENT, W16, "99 von 99 Kapiteln · endet 31.12. um 23:59 Uhr",
                "99/99", tabs(), chapters, null, null, 98, 99, null, new QuestScreen.Shop(W16 + W16, BIG, NO)), ACTIONS));
        List<QuestScreen.ShopOffer> offers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            offers.add(new QuestScreen.ShopOffer(KitData.Icon.JACK_O_LANTERN, W16 + W16, BIG, i % 3 == 0, i % 2 == 0, NO));
        }
        reg.accept("Quests Candy-Shop", () -> QuestScreen.eventShop(new QuestScreen.ShopView(W16, W16, BIG, offers), NO));
        reg.accept("Quests Candy-Shop leer", () -> QuestScreen.eventShop(new QuestScreen.ShopView("Event", "Candy Corn", 0, List.of()), NO));

        KitData.Sprite[] faces = {KitData.Sprite.QU_FACE_LEONIE, KitData.Sprite.QU_FACE_FRIEDA, KitData.Sprite.QU_FACE_MORTIS,
                KitData.Sprite.QU_FACE_LILIA, KitData.Sprite.QU_FACE_KONRAD, KitData.Sprite.QU_FACE_HERMANN,
                KitData.Sprite.QU_FACE_RUDOLF, KitData.Sprite.QU_FACE_MIA, null};
        List<Npc> npcs = new ArrayList<>();
        for (int i = 0; i < 9; i++) npcs.add(new Npc(W16, faces[i], W16, W16 + W16, i == 0 ? 99 : 0, 99, i % 2 == 0, NO));
        reg.accept("Quests Nebenquests", () -> QuestScreen.sides(new SideView(999, 999, npcs, 9, 10), ACTIONS));
        List<Goal> goals = List.of(new Goal(null, W16 + W16, BIG, BIG), new Goal(KitData.Icon.IRON_INGOT, W16, 0, SMALL),
                new Goal(KitData.Icon.DIAMOND, "Diamant", 1, 64));
        reg.accept("Quests Nebenquest", () -> QuestScreen.side(new SideDetail(W16, null, "Läuft", false, W16, 99, 99, 99, "Schwer",
                W16 + W16, "„" + "Ein sehr langes Zitat der Figur. ".repeat(8) + "“", goals, BIG, SMALL, "Weg zeigen", NO), NO));
        reg.accept("Quests Nebenquest frei", () -> QuestScreen.side(new SideDetail("Ralf", faces[0], "Verfügbar", true, "Minenarbeiter", 0, 8, 1,
                "Einfach", "Kohle aus der Mine", "„Kurz.“", List.of(), 0, 0, "Annehmen & verfolgen", NO), NO));

        reg.accept("Quests Streak", () -> QuestScreen.streak(new StreakView(9999, 9999, true, stages(true), 999), NO));
        reg.accept("Quests Streak Gnadentag", () -> QuestScreen.streak(new StreakView(0, 0, false, stages(false), 0), NO));
        reg.accept("Quests Streak ohne Gnade", () -> QuestScreen.streak(new StreakView(1, 1, null, stages(false), 2), NO));

        List<BoxItem> items = new ArrayList<>();
        KitData.Icon[] icons = {KitData.Icon.DIAMOND, KitData.Icon.IRON_INGOT, null};
        for (int i = 0; i < 40; i++) items.add(new BoxItem(icons[i % icons.length], W16 + W16, i == 0 ? 9999 : 64, NO));
        reg.accept("Quests Abholbox voll", () -> QuestScreen.box(new BoxView(items, 0, NO), NO));
        reg.accept("Quests Abholbox leer", () -> QuestScreen.box(new BoxView(List.of(), 32, NO), NO));

        for (int pg = 0; pg < 4; pg++) {
            int k = pg;
            reg.accept("Quests Hilfe " + (pg + 1), () -> QuestScreen.help(k, QuestScreen.helpPage(k,
                    "Neuling, Wanderer ab 15, Questmeister ab 60, Großmeister ab 120 und Legende ab 200.", stages(false),
                    "99 Hauptquests in Kapiteln zu je 25, der Reihe nach, beim Quest-NPC am Spawn.",
                    "Alle 3 Tagesaufträge: 99.999 Coins und 999 Tokens\nAlle 3 Wochenaufträge: 99.999 Coins und 999 Tokens"),
                    NO, i -> NO));
        }

        List<Entry> ranks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) ranks.add(new Entry(i, "_" + W16.substring(0, 15), BIG));
        reg.accept("Quests Rangliste Monat", () -> QuestScreen.ranking(new RankView(true, "September", BIG, 9999, ranks,
                "Noch 99.999 Tokens bis Platz 10"), NO, NO, NO));
        reg.accept("Quests Rangliste gesamt leer", () -> QuestScreen.ranking(new RankView(false, "Mai", 0, 0, List.of(),
                "Noch niemand hat Tokens gesammelt."), NO, NO, NO));
    }
}
