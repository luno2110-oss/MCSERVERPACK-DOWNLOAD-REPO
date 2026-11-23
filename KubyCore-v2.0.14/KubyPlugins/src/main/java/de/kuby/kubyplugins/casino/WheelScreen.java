package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Glücksrad als Fenster (v2.07): links das Rad (Bild wh_ui_&lt;rad&gt;), rechts alle zwölf Felder mit Chance, oben die
 * Serie bis zum Gold-Dreh, unten „Gratis drehen“ oder die Wartezeit, dazu der letzte Gewinn und die letzten Gewinner.
 *
 * <p>Der Dreh selbst läuft als Bild über den Bildschirm (Titel in der Schrift kuby:wheel aus tools/build_wheel.py):
 * 36 Bilder je Rad (alle 10 Grad), darüber die Nadel in 9 Stellungen (-10 bis +30 Grad), mittig gehalten über
 * Leerzeichen mit fester Breite.
 */
public final class WheelScreen {

    public static final int W = 390, HEIGHT = 280;
    public static final Key FONT = Key.key("kuby", "wheel");
    /** Schrift kuby:wheel: Bild i des Casino-Rads = E000 + i, SMP-Rad = E040 + i, Nadel k = E080 + k. */
    public static final char CASINO_BASE = '', SMP_BASE = '', NEEDLE_BASE = '', BACK = '', FWD = '';
    public static final int FRAMES = 36, NEEDLES = 9;

    private static final TextColor GOLD = TextColor.color(0xF2C94C), LIGHT = TextColor.color(0xFFE870),
            SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC),
            GREEN = TextColor.color(0x6DE288), TEAL = TextColor.color(0x62E0C8), DIM = TextColor.color(0x3A3540),
            TIP_CLICK = TextColor.color(0xFCFC54);
    /** Feldfarben wie im Bild (tools/build_wheel.py SEG, mittlerer Ton), Hauptgewinn schwarz-gold. */
    private static final int[] SEG = {0x9A1222, 0x1A4A9A, 0x128040, 0x62248E, 0xA8540C, 0x106A7A};

    /** Letzter Gewinn (für das Fenster direkt nach dem Dreh). */
    public record Win(String wheel, String text, boolean gold, boolean jackpot, long at) {}

    public record View(String wheel, List<Wheel.Field> fields, int jackpot, long left, int streak, int goldEvery,
                       boolean nextGold, long streakLeft, long hours, boolean spinning, Win win, List<String> winners,
                       boolean hasStage) {}

    public interface Actions {
        Consumer<Player> spin();

        Consumer<Player> refresh();

        Consumer<Player> casino();
    }

    private WheelScreen() {
    }

    // =================================================================================================================
    // Bild über den Bildschirm
    // =================================================================================================================

    /** Ein Bild der Dreh-Animation: Rad bei Winkel a (Grad, gegen den Uhrzeigersinn), Nadel um deg nach links. */
    public static Component frame(String wheel, double a, double deg) {
        int i = Math.floorMod((int) Math.round(a / 10.0), FRAMES);
        int k = Math.max(0, Math.min(NEEDLES - 1, (int) Math.round((deg + 10) / 5.0)));
        char base = Wheel.SMP.equals(wheel) ? SMP_BASE : CASINO_BASE;
        String s = String.valueOf((char) (base + i)) + BACK + (char) (NEEDLE_BASE + k) + FWD;
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    // =================================================================================================================
    // Fenster
    // =================================================================================================================

    static String currency(String wheel) {
        return Wheel.SMP.equals(wheel) ? "$" : "Chips";
    }

    public static Canvas canvas(View v, Actions a) {
        boolean casino = !Wheel.SMP.equals(v.wheel());
        TextColor accent = casino ? GOLD : TEAL;
        Canvas c = new Canvas(W).heading(casino ? "Glücksrad · Casino" : "Glücksrad · SMP", accent);
        // Kopf: Schriftzug, Untertitel, Serie
        c.box(0, 0, W, 46, KitData.Box.CASINO_DARK);
        c.headline(10, 9, "GLÜCKS|RAD", GOLD);
        c.text(10, 31, (casino ? "Casino" : "SMP") + " · alle " + v.hours() + " h ein Gratis-Dreh", SUB, false);
        streak(c, v);
        // links: das Rad
        c.box(0, 50, 172, 156, KitData.Box.CASINO_DARK);
        c.sprite(22, 54, casino ? KitData.Sprite.WH_UI_CASINO : KitData.Sprite.WH_UI_SMP);
        // links unten: letzter Gewinn oder letzte Gewinner
        bottomLeft(c, v);
        // rechts: Felder mit Chance
        fieldList(c, v, accent);
        // rechts unten: Drehen oder Wartezeit
        action(c, v, a);
        return c;
    }

    private static void streak(Canvas c, View v) {
        int ge = v.goldEvery();
        int next = v.streak() + 1;
        int pos = (next - 1) % ge;            // Platz des nächsten Drehs in der Reihe (0 bis ge - 1)
        int cell = 11, size = 8;
        int x0 = W - 10 - ge * cell + (cell - size);
        c.textRight(W - 10, 8, List.of(Span.of("SERIE ", SUB), Span.bold(v.streak() + "", v.streak() > 0 ? GOLD : SUB)));
        for (int i = 0; i < ge; i++) {
            int x = x0 + i * cell, y = 20;
            boolean done = i < pos, now = i == pos, gold = i == ge - 1;
            TextColor col = done ? GOLD : now ? WHITE : DIM;
            if (gold) {
                rect(c, x - 1, y - 1, size + 2, size + 2, LIGHT);
                rect(c, x, y, size, size, done ? GOLD : TextColor.color(0x5A4410));
                if (now) rect(c, x + 2, y + 2, size - 4, size - 4, LIGHT);
            } else {
                rect(c, x, y, size, size, col);
            }
        }
        String line = v.nextGold() ? "Jetzt: GOLD-DREH (x2)!" : "Gold-Dreh beim " + ge + ". Dreh in Folge";
        c.textRight(W - 10, 32, line, v.nextGold() ? LIGHT : SUB, v.nextGold());
    }

    private static void rect(Canvas c, int x, int y, int w, int h, TextColor col) {
        for (int i = 0; i < h; i++) c.line(x, y + i, w, col);
    }

    private static void bottomLeft(Canvas c, View v) {
        int y = 210;
        Win win = v.win();
        if (win != null) {
            KitData.Box b = win.jackpot() || win.gold() ? KitData.Box.JS_GOLD : KitData.Box.JS_GREEN;
            c.box(0, y, 172, 66, b);
            c.textCenter(86, y + 8, win.jackpot() ? "HAUPTGEWINN!" : win.gold() ? "GOLD-DREH!" : "GEWONNEN!", win.jackpot() || win.gold() ? LIGHT : GREEN, true);
            List<String> lines = Canvas.wrap(win.text().replaceFirst("^GOLD-DREH: ", ""), 156);
            for (int i = 0; i < Math.min(3, lines.size()); i++) c.textCenter(86, y + 22 + 11 * i, lines.get(i), WHITE, i == 0);
            return;
        }
        c.box(0, y, 172, 66, KitData.Box.CASINO_DARK);
        c.text(8, y + 6, "LETZTE GEWINNER", GOLD, true);
        if (v.winners().isEmpty()) {
            c.text(8, y + 20, "Noch niemand – du?", SUB, false);
            return;
        }
        for (int i = 0; i < Math.min(4, v.winners().size()); i++) {
            String[] p = v.winners().get(i).split(";", 5);
            if (p.length < 2) continue;
            boolean big = p.length > 4 && ("1".equals(p[3]) || "1".equals(p[4]));
            String name = Canvas.fit(p[0], false, 62);
            int nw = Canvas.width(name, false);
            c.text(8, y + 19 + 11 * i, name, DESC, false);
            c.text(8 + nw + 4, y + 19 + 11 * i, Canvas.fit(p[1], big, 156 - nw - 4), big ? LIGHT : WHITE, big);
        }
    }

    private static void fieldList(Canvas c, View v, TextColor accent) {
        int x = 178, y = 50, w = W - x;
        c.box(x, y, w, 172, KitData.Box.CASINO_DARK);
        c.text(x + 8, y + 6, "GEWINNE", accent, true);
        c.textRight(W - 8, y + 6, v.nextGold() ? "doppelt beim Gold-Dreh" : "Chance", v.nextGold() ? LIGHT : SUB, false);
        int sum = 0;
        for (Wheel.Field f : v.fields()) sum += f.weight();
        for (int i = 0; i < v.fields().size(); i++) {
            Wheel.Field f = v.fields().get(i);
            int ry = y + 19 + i * 12;
            boolean jp = i == v.jackpot();
            TextColor sw = jp ? TextColor.color(0x221A1C) : TextColor.color(SEG[i % SEG.length]);
            if (jp) rect(c, x + 7, ry, 9, 9, LIGHT);
            rect(c, x + 8, ry + 1, 7, 7, sw);
            String name = (jp ? "★ " : "") + f.name().replaceFirst("(?i)^hauptgewinn\\s*", "");
            c.text(x + 20, ry + 1, Canvas.fit(name, jp, w - 70), jp ? LIGHT : WHITE, jp);
            double pct = sum == 0 ? 0 : 100.0 * f.weight() / sum;
            String ps = pct < 1 ? String.format(Locale.GERMAN, "%.1f %%", pct) : String.format(Locale.GERMAN, "%.0f %%", pct);
            c.textRight(W - 8, ry + 1, ps, jp ? LIGHT : SUB, false);
        }
    }

    private static void action(Canvas c, View v, Actions a) {
        int x = 178, y = 226, w = W - x;
        if (v.spinning()) {
            c.box(x, y, w, 26, KitData.Box.JS_GRAY, 1);
            c.textCenter(x + w / 2, y + 9, "Das Rad dreht …", DESC, false);
        } else if (v.left() == 0) {
            boolean gold = v.nextGold();
            c.box(x, y, w, 26, gold ? KitData.Box.JS_GOLD : KitData.Box.JS_GREEN, 1);
            c.textCenter(x + w / 2, y + 9, gold ? "★ GOLD-DREH STARTEN ★" : "GRATIS DREHEN", gold ? LIGHT : GREEN, true);
            if (a != null) c.action(x, y, w, 26, a.spin(), tip(gold ? "Gold-Dreh" : "Gratis drehen",
                    gold ? "Dein " + v.goldEvery() + ". Dreh in Folge: jeder Gewinn zählt doppelt!"
                            : "Einmal alle " + v.hours() + " h gratis. Dreh jeden Tag für deine Serie: beim "
                            + v.goldEvery() + ". Dreh in Folge gibt es den Gold-Dreh mit doppeltem Gewinn.", "Klick: drehen"));
        } else {
            c.box(x, y, w, 26, KitData.Box.JS_GRAY, 1);
            List<Span> sp = List.of(Span.of("Nächster Dreh in ", DESC), Span.bold(WheelService.duration(v.left()), WHITE));
            c.text(x + w / 2 - Canvas.width(sp) / 2, y + 4, sp);
            java.time.LocalTime at = java.time.LocalTime.now().plusSeconds(v.left() / 1000);
            String sl = v.streakLeft() > 0 ? " · Serie hält " + WheelService.duration(v.streakLeft()) : "";
            c.textCenter(x + w / 2, y + 15, Canvas.fit("ab " + String.format("%02d:%02d", at.getHour(), at.getMinute()) + " Uhr" + sl, false, w - 10), SUB, false);
            if (a != null) c.action(x, y, w, 26, a.refresh(), tip("Wartezeit", "Komm wieder, wenn die Zeit um ist. Du bekommst eine Nachricht, sobald dein Rad bereit ist."
                    + (v.streak() > 0 ? " Dreh vor Ablauf der Serie, sonst beginnt sie von vorn." : ""), "Klick: aktualisieren"));
        }
        int by = 258;
        if (v.hasStage()) {
            c.box(x, by, w, 18, KitData.Box.JS_GOLD, 1);
            c.textCenter(x + w / 2, by + 5, "Zum großen Rad im Casino ›", LIGHT, true);
            if (a != null) c.action(x, by, w, 18, a.casino(), tip("Großes Rad", "Im Casino steht das Rad auf der Bühne: Rechtsklick aufs Rad, alle sehen deinen Gewinn. Gleiche Wartezeit wie hier.", "Klick: zum Casino"));
        } else {
            c.textCenter(x + w / 2, by + 4, Wheel.SMP.equals(v.wheel()) ? "Gewinne gelten in SMP" : "Gewinne gelten in Survival", SUB, false);
        }
    }

    private static Component tip(String title, String text, String click) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, WHITE).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }

    /** Ansicht für den SELFTEST und Vorschau-Bilder. state: ready, wait, gold, win. */
    public static View sample(org.bukkit.configuration.file.FileConfiguration cfg, String wheel, String state) {
        List<Wheel.Field> fs = Wheel.fields(cfg, wheel);
        List<String> wl = List.of("Kuby_Max;25.000 Chips;0;0;0", "Lena;★ 2 Episch-Keys;0;1;0", "Tom;10.000 Chips;0;0;0",
                "Sara;HAUPTGEWINN 1.000.000 Chips;0;0;1");
        Win win = "win".equals(state) ? new Win(wheel, "GOLD-DREH: 50.000 Chips", true, false, 0) : null;
        boolean gold = "gold".equals(state);
        return new View(wheel, fs, Wheel.jackpot(fs), "wait".equals(state) || "win".equals(state) ? 23 * 3_600_000L + 41 * 60_000L : 0,
                gold ? 6 : 3, 7, gold, 40 * 3_600_000L, 24, false, win, wl, !Wheel.SMP.equals(wheel));
    }
}
