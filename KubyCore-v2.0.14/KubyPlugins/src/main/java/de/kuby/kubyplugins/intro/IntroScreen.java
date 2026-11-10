package de.kuby.kubyplugins.intro;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialoge der Einführung (im Hub-Look wie die Modus-Wahl): ein Schritt der Willkommens-Tour, die Einführung eines
 * Modus mit Aufgaben, „Geschafft“ und die Rückfrage beim Überspringen. Nur Zeichnen, die Abläufe stehen im IntroService.
 */
public final class IntroScreen {

    public static final int W = 390;
    static final TextColor GOLD = TextColor.color(0xFBCD01), GRAY = TextColor.color(0x9CA3B4), CREAM = TextColor.color(0xF4E6C0),
            GREEN = TextColor.color(0x6CE086), DARK = TextColor.color(0x1A1206), WHITE = TextColor.color(0xFFFFFF),
            DIM = TextColor.color(0x6E7686);

    private IntroScreen() {
    }

    /** Zeile mit Symbol, fetter Überschrift und kurzem Text. */
    public record Line(KitData.Icon icon, String head, String text) {}

    /** Ein Schritt der Willkommens-Tour. next: Text des Weiter-Knopfs. */
    public record Step(KitData.Icon icon, String title, String sub, String intro, List<Line> lines, int index, int count, String next,
                       Consumer<Player> onNext, Consumer<Player> onBack, Consumer<Player> onSkip) {}

    /** Eine Aufgabe der Modus-Einführung. */
    public record Task(KitData.Icon icon, String text, long reward, boolean done) {}

    /** Einführung eines Modus mit Aufgaben (zu Beginn und jederzeit über /einfuehrung). */
    public record ModeView(KitData.Icon icon, String mode, TextColor color, String sub, List<Line> lines, List<Task> tasks, long bonus,
                           String unit, boolean started, Consumer<Player> onGo, Consumer<Player> onSkip) {}

    /** Geschafft: Belohnung und Tipps, wie es weitergeht. */
    public record Done(KitData.Icon icon, String title, String reward, List<Line> tips, Consumer<Player> onClose) {}

    // =================================================================================================================

    private static int header(Canvas c, KitData.Icon icon, String title, TextColor color, String sub) {
        c.box(0, 0, W, 52, KitData.Box.HUB);
        c.icon(12, 10, icon, true);
        c.headline(52, 9, Canvas.fit(title, true, W - 62), color);
        c.text(52, 35, Canvas.fit(sub, false, W - 62), GRAY, false);
        return 58;
    }

    private static int lines(Canvas c, int y, List<Line> lines) {
        for (Line l : lines) {
            c.box(0, y, W, 30, KitData.Box.NAVY);
            c.icon(7, y + 7, l.icon(), false);
            c.text(30, y + 5, Canvas.fit(l.head(), true, W - 36), GOLD, true);
            c.text(30, y + 17, Canvas.fit(l.text(), false, W - 36), CREAM, false);
            y += 32;
        }
        return y;
    }

    private static int paragraph(Canvas c, int y, String text) {
        if (text == null || text.isBlank()) return y;
        for (String l : Canvas.wrap(text, W - 12)) {
            c.text(6, y, l, CREAM, false);
            y += 11;
        }
        return y + 4;
    }


    public static Canvas step(Step s) {
        Canvas c = new Canvas(W).heading("Einführung", GOLD);
        int y = header(c, s.icon(), s.title(), GOLD, s.sub());
        // Fortschritt der Tour als schmaler Balken unter dem Kopf
        c.bar(0, y - 3, W, (s.index() + 1) / (double) Math.max(1, s.count()), KitData.Fill.GOLD);
        y += KitData.BAR_H;
        y = paragraph(c, y + 2, s.intro());
        y = lines(c, y, s.lines());
        y += 4;
        int sw = 104, bw = 140;
        c.button(0, y, sw, KitData.Button.DARK, "Überspringen", GRAY, false);
        c.action(0, y, sw, 22, s.onSkip(), "Einführung überspringen (später mit /einfuehrung)");
        if (s.onBack() != null) {
            c.button(W - bw - 4 - 80, y, 80, KitData.Button.DARK, "Zurück", GRAY, false);
            c.action(W - bw - 4 - 80, y, 80, 22, s.onBack(), "Einen Schritt zurück");
        }
        c.button(W - bw, y, bw, KitData.Button.GOLD, s.next(), DARK, true);
        c.action(W - bw, y, bw, 22, s.onNext(), s.next());
        return c;
    }

    public static Canvas mode(ModeView v) {
        Canvas c = new Canvas(W).heading("Einführung", v.color());
        int done = 0;
        for (Task t : v.tasks()) if (t.done()) done++;
        int y = header(c, v.icon(), "Willkommen in " + v.mode(), v.color(), v.sub());
        y = lines(c, y, v.lines());
        y += 4;
        c.text(4, y, Canvas.caps("Deine ersten Aufgaben"), GOLD, true);
        c.textRight(W - 4, y, done + " / " + v.tasks().size() + " geschafft", done == v.tasks().size() ? GREEN : GRAY, false);
        y += 13;
        for (Task t : v.tasks()) {
            c.box(0, y, W, 24, t.done() ? KitData.Box.GREEN : KitData.Box.GRAY);
            c.icon(5, y + 4, t.icon(), false);
            String reward = "+" + de.kuby.kubyplugins.util.Msg.coins(t.reward()) + " " + v.unit();
            int rw = Canvas.width(reward, true);
            c.text(27, y + 8, Canvas.fit(t.text(), false, W - 27 - rw - 30), t.done() ? GREEN : CREAM, false);
            c.textRight(W - 8, y + 8, t.done() ? "✔ " + reward : reward, t.done() ? GREEN : GOLD, true);
            y += 26;
        }
        if (v.bonus() > 0) {
            c.text(4, y + 2, "Alle geschafft: ", GRAY, false);
            c.text(4 + Canvas.width("Alle geschafft: ", false), y + 2, "+" + de.kuby.kubyplugins.util.Msg.coins(v.bonus()) + " " + v.unit() + " Bonus", GOLD, true);
            y += 14;
        }
        y += 4;
        int sw = 104, bw = 140;
        if (v.onSkip() != null) {
            c.button(0, y, sw, KitData.Button.DARK, "Überspringen", GRAY, false);
            c.action(0, y, sw, 22, v.onSkip(), "Diese Einführung überspringen");
        }
        c.button(W - bw, y, bw, KitData.Button.GOLD, v.started() ? "Weiterspielen" : "Los geht's ›", DARK, true);
        c.action(W - bw, y, bw, 22, v.onGo(), "Aufgaben im Spiel erledigen, Fortschritt mit /einfuehrung");
        return c;
    }

    public static Canvas done(Done d) {
        Canvas c = new Canvas(W).heading("Einführung", GREEN);
        int y = header(c, d.icon(), d.title(), GREEN, d.reward());
        c.text(4, y, Canvas.caps("So geht es weiter"), GOLD, true);
        y = lines(c, y + 13, d.tips());
        y += 4;
        int bw = 140;
        c.button(W - bw, y, bw, KitData.Button.GOLD, "Los geht's", DARK, true);
        c.action(W - bw, y, bw, 22, d.onClose(), "Schließen");
        return c;
    }

    /** Rückfrage: nur diese Einführung oder alle überspringen. */
    public static Canvas skip(String what, Consumer<Player> onlyThis, Consumer<Player> all, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Einführung überspringen?", GOLD);
        int y = header(c, KitData.Icon.CLOCK, "Überspringen?", GOLD, "Du kannst sie jederzeit mit /einfuehrung neu starten");
        y = paragraph(c, y + 2, "Möchtest du nur " + what + " überspringen oder alle Einführungen (auch die der Spielmodi)? "
                + "Aufgaben, die du später trotzdem erledigst, zählen über /einfuehrung weiter.");
        y += 2;
        int w3 = (W - 8) / 3;
        c.button(0, y, w3, KitData.Button.DARK, "Zurück", GRAY, false);
        c.action(0, y, w3, 22, back, "Zurück zur Einführung");
        c.button(w3 + 4, y, w3, KitData.Button.GOLD, "Nur diese", DARK, true);
        c.action(w3 + 4, y, w3, 22, onlyThis, "Nur " + what + " überspringen");
        c.button(2 * (w3 + 4), y, w3, KitData.Button.RED, "Alle", WHITE, true);
        c.action(2 * (w3 + 4), y, w3, 22, all, "Alle Einführungen überspringen");
        return c;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(60);
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) lines.add(new Line(KitData.Icon.COMPASS, w, w + w));
        reg.accept("Einführung Schritt", () -> step(new Step(KitData.Icon.COMPASS, w, w, w + " " + w + " " + w, lines, 3, 4, "Modus wählen ›", no, no, no)));
        reg.accept("Einführung Schritt 1", () -> step(new Step(KitData.Icon.STAR, "Willkommen!", "Einführung · Schritt 1 von 4", null,
                lines.subList(0, 3), 0, 4, "Weiter ›", no, null, no)));
        List<Task> tasks = List.of(new Task(KitData.Icon.BOOK, w, 999_999, false), new Task(KitData.Icon.OAK_DOOR, w, 999_999, true),
                new Task(KitData.Icon.IRON_PICKAXE, w, 999_999, false));
        reg.accept("Einführung Modus", () -> mode(new ModeView(KitData.Icon.GRASS_BLOCK, "Survival", GREEN, w, lines.subList(0, 3), tasks, 999_999,
                "Coins", false, no, no)));
        reg.accept("Einführung Modus läuft", () -> mode(new ModeView(KitData.Icon.GRASS_BLOCK, w, GREEN, w, lines.subList(0, 3), tasks, 0,
                "Coins", true, no, null)));
        reg.accept("Einführung geschafft", () -> done(new Done(KitData.Icon.STAR, w, w, lines, no)));
        reg.accept("Einführung überspringen", () -> skip("die Willkommens-Tour", no, no, no));
    }
}
