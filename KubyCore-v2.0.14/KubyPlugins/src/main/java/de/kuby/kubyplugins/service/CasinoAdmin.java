package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.Placeable;
import de.kuby.kubyplugins.casino.TableTier;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Casino-Admin (v2.07, Wunsch des Besitzers: „wie das KAdmin, nur mit Violett-Gold als UI-Textur, mit den Tisch-Kategorien:
 * dort die verschiedenfarbigen Tische holen, individuell einstellen und mit Knöpfen x, y, z verschieben; sehr übersichtlich
 * und mit allen Features“).
 *
 * <p>Reiter: <b>Übersicht</b> (Status, Jackpot, Bank je Spiel, Schnellzugriff), <b>Aufbau</b> (Kacheln je Kategorie:
 * Tische, Automaten, Service), <b>Einstellungen</b> (allgemeine Werte) und <b>Protokoll</b>. Eine Kategorie zeigt die
 * aufgestellten Teile und Knöpfe zum Aufstellen je Farbe; ein Teil lässt sich in Schritten von 0,1 bis 5 Blöcken entlang
 * x, y und z verschieben, drehen, vergrößern, umfärben (Blackjack), zum Admin holen, anfliegen und entfernen. Jede
 * Kategorie hat eigene Einstellungen (−/+/Wert, Schalter). Layout rein (Views + Nav-Schlüssel), damit SELFTEST es prüft.
 */
public final class CasinoAdmin {

    public static final int W = 390, HEIGHT = 330, PER = 8, SET_PER = 8, LOG_PER = 14;

    static final TextColor GOLD = TextColor.color(0xF4D46A), GOLD_SOFT = TextColor.color(0xF2DA90), GRAY = TextColor.color(0xB0A6BE),
            DIM = TextColor.color(0x8A7E98), LIGHT = TextColor.color(0xEDE4F4), GREEN = TextColor.color(0x6BF07A),
            RED = TextColor.color(0xFF5A5A), ON_GOLD = TextColor.color(0x2C1C06), ON_GREEN = TextColor.color(0x06240E),
            BTN_GRAY = TextColor.color(0x8E8A84), VIOLET = TextColor.color(0xC9A2F5), HEAD = TextColor.color(0xC08CF0);

    /** Klick-Ziel je Schlüssel (der Controller löst ihn auf). */
    @FunctionalInterface
    public interface Nav {
        Consumer<Player> go(String key);
    }

    public enum Tab {
        OVERVIEW("Übersicht", "tab:start"), BUILD("Aufbau", "tab:aufbau"), SETTINGS("Einstellungen", "tab:einstellungen"), LOG("Protokoll", "tab:log");

        final String label, key;

        Tab(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    // =================================================================================================================
    // Views
    // =================================================================================================================

    public record Overview(boolean open, String jackpot, String draw, int players, List<String[]> counts, List<String[]> bank,
                           String jackpotsPaid) {}

    public record Tile(String section, String key, KitData.Icon icon, String name, String desc) {}

    /** Knopf: style GOLD = Hauptaktion, VIOLET = Nebenaktion; key null = gesperrt. */
    public record Btn(String label, KitData.Small style, TextColor color, String key, String hover) {}

    public record Row(String title, TextColor color, String sub, String key, boolean busy) {}

    public record Cat(KitData.Icon icon, String name, String desc, List<Btn> place, List<Row> rows, int page, int pages, String catKey,
                      String empty) {}

    public record PieceView(KitData.Icon icon, String name, String sub, double step, boolean blocks, String size, TableTier tier,
                            boolean fetch, boolean armed, String catKey, String hint, String variant) {
        public PieceView(KitData.Icon icon, String name, String sub, double step, boolean blocks, String size, TableTier tier,
                         boolean fetch, boolean armed, String catKey, String hint) {
            this(icon, name, sub, step, blocks, size, tier, fetch, armed, catKey, hint, null);
        }
    }

    public record SetRow(String key, String label, String value, boolean bool, boolean on, String hover) {}

    public record SettingsView(KitData.Icon icon, String title, String sub, List<SetRow> rows, int page, int pages, String back, String prefix) {}

    // =================================================================================================================
    // Bausteine
    // =================================================================================================================

    private static Canvas base() {
        return new Canvas(W).heading("Casino-Admin", HEAD);
    }

    private static boolean titleOk(String text) {
        for (char ch : text.toUpperCase(Locale.ROOT).toCharArray()) if (KitData.TITLE_CHARS.indexOf(ch) < 0) return false;
        return true;
    }

    private static int bw(String label) {
        return Canvas.width(label, true) + 14;
    }

    /** Kleiner Knopf; act null = gesperrt (grau). Gibt die Breite zurück. */
    private static int button(Canvas c, int x, int y, int w, String label, KitData.Small style, TextColor color, Consumer<Player> act, String hover) {
        if (w <= 0) w = bw(label);
        if (act == null) {
            c.smallButton(x, y, w, KitData.Small.GOLD_GRAY, label, BTN_GRAY, true);
            if (hover != null) c.action(x, y, w, 16, null, hover);
        } else {
            c.smallButton(x, y, w, style, label, color, true);
            c.action(x, y, w, 16, act, hover == null ? label : hover);
        }
        return w;
    }

    private static int button(Canvas c, int x, int y, int w, Btn b, Nav nav) {
        return button(c, x, y, w, b.label(), b.style(), b.color(), b.key() == null ? null : nav.go(b.key()), b.hover());
    }

    /** Violetter Kopf mit Goldrahmen: Icon, große Titelschrift (sonst fett), Unterzeile, rechts Zurück oder Status. */
    private static void head(Canvas c, KitData.Icon icon, String title, String sub, Nav nav, String back, Boolean open) {
        c.box(0, 0, W, 51, KitData.Box.CV_HEAD);
        c.icon(10, 10, icon, true);
        int right = 380;
        if (back != null) {
            int w = bw("‹ Zurück");
            right -= w;
            button(c, right, 9, w, "‹ Zurück", KitData.Small.VIOLET, LIGHT, nav.go(back), "Zurück");
        } else if (open != null) {
            String l = open ? "● Offen" : "● Geschlossen";
            int w = bw(l);
            right -= w;
            button(c, right, 9, w, l, open ? KitData.Small.GREEN : KitData.Small.DARK, open ? ON_GREEN : RED, nav.go("toggle"),
                    open ? "Casino schließen (keine neuen Runden, Auszahlen geht weiter)" : "Casino wieder öffnen");
        }
        int room = right - 8 - 47;
        if (titleOk(title) && Canvas.titleWidth(title) <= room) c.title(46, 11, title);
        else c.text(47, 14, Canvas.fit(title, true, room), GOLD, true);
        c.text(47, 33, Canvas.fit(sub, false, room), GRAY, false);
    }

    /** Reiter über die ganze Breite: aktiver gold, die anderen violett. */
    private static void tabs(Canvas c, Tab active, Nav nav) {
        Tab[] all = Tab.values();
        int gap = 2, text = 0;
        for (Tab t : all) text += Canvas.width(t.label, true);
        int spare = W - gap * (all.length - 1) - text, x = 0;
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            int w = Canvas.width(t.label, true) + spare / all.length + (i < spare % all.length ? 1 : 0);
            boolean on = t == active;
            c.smallButton(x, 55, w, on ? KitData.Small.GOLD_ON : KitData.Small.VIOLET, t.label, on ? ON_GOLD : LIGHT, true);
            if (!on) c.action(x, 55, w, 16, nav.go(t.key), t.label + " öffnen");
            x += w + gap;
        }
    }

    private static void card(Canvas c, int y, int h, String caps) {
        c.box(0, y, W, h, KitData.Box.CV_CARD);
        if (caps != null) c.text(10, y + 8, Canvas.caps(caps), GOLD, true);
    }

    private static void pager(Canvas c, int y, int page, int pages, Nav nav, String prefix) {
        if (pages <= 1) return;
        String label = "Seite " + (page + 1) + " / " + pages;
        int lw = Canvas.width(label, false);
        c.textCenter(W / 2, y + 4, label, GRAY, false);
        int lx = W / 2 - lw / 2 - 8 - 24, rx = W / 2 + lw / 2 + 8;
        button(c, lx, y, 24, "‹", KitData.Small.VIOLET, LIGHT, page > 0 ? nav.go(prefix + (page - 1)) : null, page > 0 ? "Vorherige Seite" : null);
        button(c, rx, y, 24, "›", KitData.Small.VIOLET, LIGHT, page + 1 < pages ? nav.go(prefix + (page + 1)) : null,
                page + 1 < pages ? "Nächste Seite" : null);
    }

    // =================================================================================================================
    // Seiten
    // =================================================================================================================

    public static Canvas overview(Overview v, Nav nav) {
        Canvas c = base();
        head(c, KitData.Icon.ROULETTE_WHEEL, "CASINO-ADMIN", "Tische, Automaten, Einstellungen und Protokoll", nav, null, v.open());
        tabs(c, Tab.OVERVIEW, nav);
        int y = 76;
        card(c, y, 62, "Status");
        c.text(10, y + 21, List.of(Canvas.Span.of("Jackpot ", GRAY), Canvas.Span.bold(v.jackpot(), GOLD),
                Canvas.Span.of(" · Ziehung " + v.draw() + " · " + v.players() + " Teilnehmer", GRAY)), W - 20);
        c.text(10, y + 33, List.of(Canvas.Span.of("Ausgezahlte Jackpots ", GRAY), Canvas.Span.of(v.jackpotsPaid(), LIGHT)), W - 20);
        StringBuilder sb = new StringBuilder();
        for (String[] k : v.counts()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(k[1]).append(' ').append(k[0]);
        }
        c.text(10, y + 45, Canvas.fit(sb.length() == 0 ? "Noch nichts aufgestellt – Reiter „Aufbau“." : "Aufgestellt: " + sb, false, W - 20),
                sb.length() == 0 ? DIM : LIGHT, false);
        y += 65;
        int rows = Math.max(1, (Math.min(10, v.bank().size()) + 1) / 2);
        int h = 22 + 11 * rows + 4;
        card(c, y, h, "Bank je Spiel");
        c.textRight(380, y + 8, "Runden · Plus der Bank", DIM, false);
        if (v.bank().isEmpty()) c.text(10, y + 22, "Noch keine Runden gespielt.", DIM, false);
        for (int i = 0; i < Math.min(10, v.bank().size()); i++) {
            String[] b = v.bank().get(i);
            int x = i % 2 == 0 ? 10 : 200, ry = y + 22 + 11 * (i / 2);
            c.text(x, ry, Canvas.fit(b[0], true, 70), GOLD_SOFT, true);
            c.text(x + 74, ry, Canvas.fit(b[1], false, 40), GRAY, false);
            c.textRight(x + 180, ry, b[2], b[2].startsWith("-") ? RED : GREEN, true);
        }
        y += h + 3;
        card(c, y, 42, "Schnellzugriff");
        int x = 10;
        x += button(c, x, y + 20, 0, "Jackpot ziehen", KitData.Small.GOLD, GOLD_SOFT, nav.go("jackpot"), "Zieht sofort unter den Teilnehmern") + 4;
        x += button(c, x, y + 20, 0, "Einrichten", KitData.Small.VIOLET, LIGHT, nav.go("cmd:casino einrichten"),
                "Einrichtungs-Assistent: Schritt für Schritt alles aufstellen") + 4;
        x += button(c, x, y + 20, 0, "Rundgang", KitData.Small.VIOLET, LIGHT, nav.go("cmd:casino rundgang"), "Den Rundgang mit Vincent ansehen") + 4;
        button(c, x, y + 20, 0, "Chip-Shop", KitData.Small.VIOLET, LIGHT, nav.go("cmd:casino shop"), "Chip-Shop öffnen");
        return c;
    }

    public static Canvas build(List<Tile> tiles, boolean open, Nav nav) {
        Canvas c = base();
        head(c, KitData.Icon.ROULETTE_WHEEL, "AUFBAU", "Kategorie wählen: aufstellen, verschieben, einstellen", nav, null, open);
        tabs(c, Tab.BUILD, nav);
        Map<String, List<Tile>> sections = new java.util.LinkedHashMap<>();
        for (Tile t : tiles) sections.computeIfAbsent(t.section(), s -> new ArrayList<>()).add(t);
        int y = 76, tw = (W - 20 - 2 * 4) / 3;
        for (Map.Entry<String, List<Tile>> e : sections.entrySet()) {
            int rows = (e.getValue().size() + 2) / 3;
            int h = 20 + 30 * rows + 2;
            card(c, y, h, e.getKey());
            for (int i = 0; i < e.getValue().size(); i++) {
                Tile t = e.getValue().get(i);
                int x = 10 + (i % 3) * (tw + 4), ty = y + 18 + 30 * (i / 3);
                c.box(x, ty, tw, 28, KitData.Box.CV_TILE, Canvas.Z_BOX + 1);
                c.icon(x + 6, ty + 6, t.icon(), false);
                c.text(x + 27, ty + 6, Canvas.fit(t.name(), true, tw - 31), GOLD_SOFT, true);
                c.text(x + 27, ty + 17, Canvas.fit(t.desc(), false, tw - 31), t.desc().startsWith("noch") ? DIM : GRAY, false);
                c.action(x, ty, tw, 28, nav.go("kat:" + t.key()), t.name() + " · " + t.desc());
            }
            y += h + 3;
        }
        return c;
    }

    /** Zeilen der Aufstell-Knöpfe (wie in category umbrochen). */
    static int placeRows(List<Btn> place) {
        int x = 0, rows = place.isEmpty() ? 0 : 1;
        for (Btn b : place) {
            int w = bw(b.label());
            if (x + w > W) {
                x = 0;
                rows++;
            }
            x += w + 3;
        }
        return rows;
    }

    /** Einträge je Seite: jede Knopfzeile über zwei kostet einen Eintrag (Figuren-Kategorie „Leute“ hat drei). */
    static int perPage(List<Btn> place) {
        return PER - Math.max(0, placeRows(place) - 2);
    }

    public static Canvas category(Cat v, Nav nav) {
        Canvas c = base();
        head(c, v.icon(), v.name().toUpperCase(Locale.ROOT), v.desc(), nav, "tab:aufbau", null);
        int x = 0, y = 55;
        for (Btn b : v.place()) {
            int w = bw(b.label());
            if (x + w > W) {
                x = 0;
                y += 19;
            }
            x += button(c, x, y, w, b, nav) + 3;
        }
        y += 21;
        if (v.rows().isEmpty()) {
            c.box(0, y, W, 26, KitData.Box.CV_CARD);
            c.text(10, y + 9, Canvas.fit(v.empty(), false, W - 20), GRAY, false);
            y += 30;
        }
        for (Row r : v.rows()) {
            c.box(0, y, W, 26, KitData.Box.CV_CARD);
            int right = W - 6;
            String[][] bs = {{"Bewegen", "teil:" + r.key()}, {"Hin", "hin:" + r.key()}};
            for (int i = bs.length - 1; i >= 0; i--) {
                boolean gold = i == 0;
                int w = bw(bs[i][0]);
                right -= w;
                button(c, right, y + 5, w, bs[i][0], gold ? KitData.Small.GOLD : KitData.Small.VIOLET, gold ? GOLD_SOFT : LIGHT, nav.go(bs[i][1]),
                        gold ? "Verschieben, drehen, Größe, Farbe, entfernen" : "Hinfliegen");
                right -= 3;
            }
            int max = right - 3 - 10;
            c.text(10, y + 4, Canvas.fit(r.title(), true, max), r.color() == null ? GOLD_SOFT : r.color(), true);
            c.text(10, y + 15, Canvas.fit(r.sub(), false, max), r.busy() ? TextColor.color(0xFF9A4A) : GRAY, false);
            c.action(0, y, Math.max(40, right - 3), 26, nav.go("teil:" + r.key()), r.title());
            y += 28;
        }
        pager(c, y + 2, v.page(), v.pages(), nav, "kat:" + v.catKey() + ":");
        return c;
    }

    private static final String[][] STEPS = {{"0,1", "0.1"}, {"0,5", "0.5"}, {"1", "1"}, {"5", "5"}};

    public static Canvas piece(PieceView v, Nav nav) {
        Canvas c = base();
        head(c, v.icon(), v.name(), v.sub(), nav, "kat:" + v.catKey(), null);
        int y = 55;
        // Verschieben
        int h = v.size() != null || v.tier() != null ? 104 : 84;
        card(c, y, h, "Verschieben");
        c.textRight(380, y + 8, v.blocks() ? "ganze Blöcke · 90°" : "x = Ost · y = hoch · z = Süd", DIM, false);
        int x = 10, ry = y + 20;
        c.text(x, ry + 4, "Schritt", GRAY, false);
        x += Canvas.width("Schritt", false) + 6;
        for (String[] s : STEPS) {
            boolean on = Math.abs(Double.parseDouble(s[1]) - v.step()) < 1e-9;
            int w = bw(s[0]) + 6;
            c.smallButton(x, ry, w, on ? KitData.Small.GOLD_ON : KitData.Small.VIOLET, s[0], on ? ON_GOLD : LIGHT, true);
            if (!on) c.action(x, ry, w, 16, nav.go("step:" + s[1]), "Schrittweite " + s[0] + " Blöcke");
            x += w + 3;
        }
        ry += 20;
        String[][] axes = {{"X −", "mv:x-", "Richtung Westen"}, {"X +", "mv:x+", "Richtung Osten"}, {"Y −", "mv:y-", "Nach unten"},
                {"Y +", "mv:y+", "Nach oben"}, {"Z −", "mv:z-", "Richtung Norden"}, {"Z +", "mv:z+", "Richtung Süden"}};
        int aw = (W - 20 - 5 * 4) / 6;
        for (int i = 0; i < axes.length; i++) {
            button(c, 10 + i * (aw + 4), ry, aw, axes[i][0], KitData.Small.GOLD, GOLD_SOFT, nav.go(axes[i][1]), axes[i][2]);
        }
        ry += 20;
        String[][] turn = v.blocks() ? new String[][]{{"‹ 90°", "rot:-90", "90° nach links"}, {"90° ›", "rot:90", "90° nach rechts"}}
                : new String[][]{{"‹ 15°", "rot:-15", "15° nach links"}, {"15° ›", "rot:15", "15° nach rechts"},
                {"‹ 90°", "rot:-90", "90° nach links"}, {"90° ›", "rot:90", "90° nach rechts"}};
        c.text(10, ry + 4, "Drehen", GRAY, false);
        x = 10 + Canvas.width("Drehen", false) + 6;
        for (String[] t : turn) x += button(c, x, ry, 0, t[0], KitData.Small.VIOLET, LIGHT, nav.go(t[1]), "Drehen: " + t[2]) + 3;
        if (v.size() != null) {
            ry += 20;
            c.text(10, ry + 4, "Größe", GRAY, false);
            x = 10 + Canvas.width("Größe", false) + 6;
            x += button(c, x, ry, 24, "−", KitData.Small.VIOLET, LIGHT, nav.go("size:-"), "Kleiner") + 4;
            c.text(x, ry + 4, v.size(), LIGHT, true);
            x += Canvas.width(v.size(), true) + 4;
            button(c, x, ry, 24, "+", KitData.Small.VIOLET, LIGHT, nav.go("size:+"), "Größer");
        }
        if (v.tier() != null) {
            int tx = v.size() != null ? 200 : 10;
            if (v.size() == null) ry += 20;
            c.text(tx, ry + 4, "Farbe", GRAY, false);
            tx += Canvas.width("Farbe", false) + 6;
            for (TableTier t : TableTier.values()) {
                boolean on = t == v.tier();
                String l = t == TableTier.GRUEN ? "Grün" : t == TableTier.ROT ? "Rot" : "Violett";
                int w = bw(l);
                c.smallButton(tx, ry, w, on ? KitData.Small.GOLD_ON : KitData.Small.VIOLET, l, on ? ON_GOLD : LIGHT, true);
                if (!on) c.action(tx, ry, w, 16, nav.go("tier:" + t.key), "Tisch in " + l + " umbauen (Filz, Limits, Chips)");
                tx += w + 3;
            }
        }
        y += h + 3;
        if (v.variant() != null) {
            // Figuren (v2.07): Variante wechseln (Haut, Haare, Kleidung)
            card(c, y, 42, "Variante");
            int bx = 10;
            bx += button(c, bx, y + 20, 24, "‹", KitData.Small.VIOLET, LIGHT, nav.go("var:-"), "Vorige Variante") + 4;
            bx += button(c, bx, y + 20, 24, "›", KitData.Small.GOLD, GOLD_SOFT, nav.go("var:+"), "Nächste Variante") + 8;
            c.text(bx, y + 24, Canvas.fit(v.variant(), true, W - bx - 10), LIGHT, true);
            y += 45;
        }
        // Aktionen
        card(c, y, 42, "Aktionen");
        x = 10;
        x += button(c, x, y + 20, 0, "Zu mir holen", KitData.Small.GOLD, GOLD_SOFT, v.fetch() ? nav.go("fetch") : null,
                v.fetch() ? "Auf deinen Standplatz (Blickrichtung bleibt)" : "Hängt an einer Wand: dort neu aufstellen") + 4;
        x += button(c, x, y + 20, 0, "Hinfliegen", KitData.Small.VIOLET, LIGHT, nav.go("goto"), "Zu diesem Teil teleportieren") + 4;
        button(c, x, y + 20, 0, v.armed() ? "Sicher? Entfernen!" : "Entfernen", v.armed() ? KitData.Small.GOLD : KitData.Small.DARK, RED,
                nav.go(v.armed() ? "del!" : "del"), v.armed() ? "Jetzt wirklich entfernen" : "Entfernen (danach noch einmal bestätigen)");
        y += 45;
        if (v.hint() != null) {
            c.box(0, y, W, 24, KitData.Box.CV_CARD);
            c.text(10, y + 8, Canvas.fit(v.hint(), false, W - 20), DIM, false);
        }
        return c;
    }

    public static Canvas settings(SettingsView v, boolean open, Nav nav) {
        Canvas c = base();
        boolean main = v.back() == null;
        head(c, v.icon(), v.title(), v.sub(), nav, v.back(), main ? open : null);
        int y = 55;
        if (main) {
            tabs(c, Tab.SETTINGS, nav);
            y = 76;
        }
        for (SetRow r : v.rows()) {
            c.box(0, y, W, 26, KitData.Box.CV_CARD);
            int right = W - 6;
            if (r.bool()) {
                String l = r.on() ? "An" : "Aus";
                int w = 44;
                right -= w;
                c.smallButton(right, y + 5, w, r.on() ? KitData.Small.GREEN : KitData.Small.DARK, l, r.on() ? ON_GREEN : BTN_GRAY, true);
                c.action(right, y + 5, w, 16, nav.go("set:" + r.key() + ":toggle"), r.on() ? "Ausschalten" : "Einschalten");
            } else {
                int w = bw("Wert");
                right -= w;
                button(c, right, y + 5, w, "Wert", KitData.Small.GOLD, GOLD_SOFT, nav.go("set:" + r.key() + ":edit"), "Wert eingeben");
                right -= 3 + 24;
                button(c, right, y + 5, 24, "+", KitData.Small.VIOLET, LIGHT, nav.go("set:" + r.key() + ":+"), "Mehr");
                right -= 3 + 24;
                button(c, right, y + 5, 24, "−", KitData.Small.VIOLET, LIGHT, nav.go("set:" + r.key() + ":-"), "Weniger");
                right -= 6;
                right -= c.textRight(right, y + 9, r.value(), GOLD, true) + 6;
            }
            int max = right - 10;
            c.text(10, y + (r.hover() == null ? 9 : 4), Canvas.fit(r.label(), true, max), GOLD_SOFT, true);
            if (r.hover() != null) c.text(10, y + 15, Canvas.fit(r.hover(), false, max), DIM, false);
            y += 28;
        }
        pager(c, y + 2, v.page(), v.pages(), nav, v.prefix());
        return c;
    }

    public static Canvas log(List<String> lines, int page, int pages, boolean open, Nav nav) {
        Canvas c = base();
        head(c, KitData.Icon.ROULETTE_WHEEL, "PROTOKOLL", "Neueste zuerst · vollständig in casino-log.txt", nav, null, open);
        tabs(c, Tab.LOG, nav);
        int y = 76, h = 14 + 14 * Math.max(1, lines.size());
        card(c, y, h, null);
        if (lines.isEmpty()) c.text(10, y + 8, "Noch keine Einträge seit dem Start.", DIM, false);
        for (int i = 0; i < lines.size(); i++) c.text(10, y + 8 + 14 * i, Canvas.fit(lines.get(i), false, W - 20), LIGHT, false);
        pager(c, y + h + 4, page, pages, nav, "log:");
        return c;
    }

    // =================================================================================================================
    // Selbsttest: Grenzwerte (lange Namen, viele Teile, große Zahlen)
    // =================================================================================================================

    public static void samples(BiConsumer<String, Supplier<Canvas>> reg) {
        Nav nav = k -> p -> { };
        List<String[]> bank = new ArrayList<>();
        for (String g : new String[]{"Blackjack", "Roulette", "Slots", "Crash", "Plinko", "Rennen", "Losbude", "Roulette_violett_hr", "Glücksrad", "X"}) {
            bank.add(new String[]{g, "12.345", "-1.234.567"});
        }
        reg.accept("Casino-Admin Übersicht", () -> overview(new Overview(true, "12.345.678", "Sa 18:00", 120,
                List.of(new String[]{"Blackjack", "3"}, new String[]{"Roulette", "3"}, new String[]{"Poker", "3"}, new String[]{"Slots", "12"},
                        new String[]{"Plinko", "2"}, new String[]{"Rennen", "1"}, new String[]{"Crash", "1"}, new String[]{"Glücksrad", "1"}),
                bank, "4 (1.234.567 Chips)"), nav));
        reg.accept("Casino-Admin Aufbau", () -> build(tiles(k -> 3), true, nav));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < PER; i++) rows.add(new Row("Blackjack-Tisch " + (i + 1) + " · Elite " + "W".repeat(20), TextColor.color(0xC08CF0),
                "12345,5 64 -9876,5 · 135° · Radius 4,5 · Runde läuft " + "W".repeat(20), "blackjack:0:t" + i, i % 2 == 0));
        reg.accept("Casino-Admin Kategorie", () -> category(new Cat(KitData.Icon.CHIP_GREEN, "Blackjack", "Tische in Grün, Rot und Violett",
                List.of(new Btn("Grün hier", KitData.Small.GOLD, GOLD_SOFT, "x", "h"), new Btn("Rot hier", KitData.Small.GOLD, GOLD_SOFT, "x", "h"),
                        new Btn("Violett hier", KitData.Small.GOLD, GOLD_SOFT, "x", "h"), new Btn("Einstellungen", KitData.Small.VIOLET, LIGHT, "x", "h")),
                rows, 1, 3, "blackjack", "leer"), nav));
        List<Btn> many = new ArrayList<>();
        for (Kind k : KINDS) if (k.key().equals("leute")) many.addAll(k.place());
        List<Row> fewer = new ArrayList<>(rows.subList(0, Math.min(rows.size(), perPage(many))));
        reg.accept("Casino-Admin Kategorie Leute", () -> category(new Cat(KitData.Icon.STAR, "Leute", "Gäste und Security mit Tätigkeiten",
                many, fewer, 0, 2, "leute", "leer"), nav));
        reg.accept("Casino-Admin Kategorie leer", () -> category(new Cat(KitData.Icon.STAR, "Glücksrad", "Bühne", List.of(), List.of(), 0, 1,
                "gluecksrad", "Noch nichts aufgestellt."), nav));
        reg.accept("Casino-Admin Teil", () -> piece(new PieceView(KitData.Icon.CHIP_GREEN, "Blackjack-Tisch 12 · High Roller " + "W".repeat(10),
                "12345,5 64 -9876,5 · Blick 135° · frei " + "W".repeat(30), 0.5, false, "4,5", TableTier.ROT, true, true, "blackjack",
                "Tipp: Spieler am Tisch? Verschieben geht erst nach der Runde."), nav));
        reg.accept("Casino-Admin Teil Figur", () -> piece(new PieceView(KitData.Icon.STAR, "Viktor · Gruppe", "1 64 2 · Blick 90° · in einer Gruppe",
                0.5, false, null, null, true, true, "leute", "Variante › wechselt die Person.", "Croupière (3/3) " + "W".repeat(12)), nav));
        reg.accept("Casino-Admin Teil Poker", () -> piece(new PieceView(KitData.Icon.CHIP_RED, "Pokertisch Elite", "1 64 2", 1, true, null, null,
                false, false, "poker", null), nav));
        List<SetRow> set = new ArrayList<>();
        for (int i = 0; i < SET_PER; i++) {
            set.add(i % 3 == 0 ? new SetRow("k" + i, "Schalter " + "W".repeat(30), "", true, i % 2 == 0, null)
                    : new SetRow("k" + i, "Höchsteinsatz roter Tisch " + "W".repeat(20), "2.000.000.000", false, false, i % 2 == 0 ? "Hinweis " + "W".repeat(40) : null));
        }
        reg.accept("Casino-Admin Einstellungen", () -> settings(new SettingsView(KitData.Icon.COMPARATOR, "EINSTELLUNGEN", "Allgemein", set, 0, 3,
                null, "einst:"), true, nav));
        reg.accept("Casino-Admin Kategorie-Einstellungen", () -> settings(new SettingsView(KitData.Icon.ROULETTE_WHEEL, "Roulette · Einstellungen",
                "Grün, Rot, Violett", set, 1, 2, "kat:roulette", "kset:roulette:"), false, nav));
        List<String> log = new ArrayList<>();
        for (int i = 0; i < LOG_PER; i++) log.add("2026-11-24 12:00 Blackjack " + "W".repeat(60));
        reg.accept("Casino-Admin Protokoll", () -> log(log, 2, 5, false, nav));
    }

    // =================================================================================================================
    // Kategorien (was es gibt, wie man es aufstellt, welche Einstellungen)
    // =================================================================================================================

    /** Quelle von Teilen: Dienst, Name der Farbe (oder null), Farbe der Zeile, ob „zu mir holen“ passt (Bodenteile). */
    private record Source(Placeable p, String tier, TextColor color, boolean floor) {}

    /** Einstellung: kind 0 = ganze Zahl, 1 = Prozent (gespeichert als Anteil), 2 = Kommazahl, 3 = Schalter. */
    private record Setting(String path, String label, double def, double step, double min, double max, int kind, String hint) {}

    private record Kind(String key, String section, KitData.Icon icon, String name, String desc, List<Btn> place,
                        java.util.function.Function<KubyPlugins, List<Source>> sources, List<Setting> settings, String hint) {}

    private static final TextColor T_GREEN = TextColor.color(0x6BF07A), T_RED = TextColor.color(0xFF6A6A), T_VIOLET = TextColor.color(0xC08CF0);

    private static Btn place(String label, String cmd, String hover) {
        return new Btn(label, KitData.Small.GOLD, GOLD_SOFT, "cmd:" + cmd, hover);
    }

    private static Setting i(String path, String label, double def, double step, double min, double max) {
        return new Setting(path, label, def, step, min, max, 0, null);
    }

    private static Setting pct(String path, String label, double def, double step, double max, String hint) {
        return new Setting(path, label, def, step, 0, max, 1, hint);
    }

    private static Setting dec(String path, String label, double def, double step, double min, double max) {
        return new Setting(path, label, def, step, min, max, 2, null);
    }

    private static Setting bool(String path, String label, boolean def, String hint) {
        return new Setting(path, label, def ? 1 : 0, 0, 0, 1, 3, hint);
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("blackjack", "Tische", KitData.Icon.CHIP_GREEN, "Blackjack", "Tische in Grün, Rot und Violett",
                    List.of(place("Grün hier", "casino blackjack tisch", "An die gerade Kante (Dealer-Seite) stellen, Blick zu den Spielern"),
                            place("Rot hier", "casino blackjack tisch rot", "High Roller: an die Dealer-Seite stellen, Blick zu den Spielern"),
                            place("Violett hier", "casino blackjack tisch violett", "Elite: an die Dealer-Seite stellen, Blick zu den Spielern")),
                    pl -> List.of(new Source(pl.blackjack(), null, null, true)),
                    List.of(i("casino.tische.gruen.min-einsatz", "Grün: Mindesteinsatz", 10, 10, 1, 1_000_000),
                            i("casino.tische.gruen.max-einsatz", "Grün: Höchsteinsatz", 100_000, 10_000, 100, 100_000_000),
                            i("casino.tische.rot.min-einsatz", "Rot: Mindesteinsatz", 10_000, 1_000, 1, 10_000_000),
                            i("casino.tische.rot.max-einsatz", "Rot: Höchsteinsatz", 500_000, 50_000, 100, 100_000_000),
                            i("casino.tische.violett.min-einsatz", "Violett: Mindesteinsatz", 50_000, 5_000, 1, 10_000_000),
                            i("casino.tische.violett.max-einsatz", "Violett: Höchsteinsatz", 1_000_000, 100_000, 100, 1_000_000_000),
                            i("casino.blackjack.bet-seconds", "Countdown nach dem ersten Einsatz (s)", 8, 1, 3, 60),
                            i("casino.blackjack.turn-seconds", "Zeit je Zug (s)", 15, 1, 5, 120)),
                    "Die Tischgrenzen gelten auch für Poker (Blinds) der gleichen Farbe."),
            new Kind("roulette", "Tische", KitData.Icon.ROULETTE_WHEEL, "Roulette", "Grün, High Roller, Elite",
                    List.of(place("Grün hier", "casino roulette-tisch", "Spielerseite, Blick zum Croupier; Tischmitte 2 Blöcke vor dir"),
                            place("Rot hier", "casino roulette-tisch rot", "High Roller: Spielerseite, Blick zum Croupier"),
                            place("Violett hier", "casino roulette-tisch violett", "Elite: Spielerseite, Blick zum Croupier")),
                    pl -> List.of(new Source(pl.roulette(), "Grün", T_GREEN, true), new Source(pl.rouletteHr(), "Rot", T_RED, true),
                            new Source(pl.rouletteVio(), "Violett", T_VIOLET, true)),
                    List.of(i("casino.roulette.wait-seconds", "Wartezeit bis zum Wurf (s)", 20, 5, 5, 120),
                            i("casino.roulette.min-bet", "Grün: Mindesteinsatz", 10, 10, 1, 1_000_000),
                            i("casino.roulette.max-round", "Grün: Höchstens je Runde", 100_000, 10_000, 100, 100_000_000),
                            i("casino.roulette.blitz-min", "Grün: Blitz-Zahlen mindestens", 1, 1, 0, 12),
                            i("casino.roulette.blitz-max", "Grün: Blitz-Zahlen höchstens", 3, 1, 0, 12),
                            i("casino.roulette.zahl-quote", "Grün: Volltreffer zahlt …:1", 31, 1, 2, 35),
                            i("casino.roulette.rot.min-bet", "Rot: Mindesteinsatz", 1_000, 500, 1, 10_000_000),
                            i("casino.roulette.rot.max-round", "Rot: Höchstens je Runde", 500_000, 50_000, 100, 100_000_000),
                            i("casino.roulette.rot.blitz-min", "Rot: Blitz-Zahlen mindestens", 3, 1, 0, 12),
                            i("casino.roulette.rot.blitz-max", "Rot: Blitz-Zahlen höchstens", 3, 1, 0, 12),
                            i("casino.roulette.rot.zahl-quote", "Rot: Volltreffer zahlt …:1", 29, 1, 2, 35),
                            i("casino.roulette.violett.min-bet", "Violett: Mindesteinsatz", 5_000, 1_000, 1, 10_000_000),
                            i("casino.roulette.violett.max-round", "Violett: Höchstens je Runde", 2_000_000, 100_000, 100, 1_000_000_000),
                            i("casino.roulette.violett.blitz-min", "Violett: Blitz-Zahlen mindestens", 3, 1, 0, 12),
                            i("casino.roulette.violett.blitz-max", "Violett: Blitz-Zahlen höchstens", 5, 1, 0, 12),
                            i("casino.roulette.violett.zahl-quote", "Violett: Volltreffer zahlt …:1", 27, 1, 2, 35)),
                    "Mehr Blitz-Zahlen brauchen eine kleinere Volltreffer-Quote, sonst gewinnt die Bank nicht (SELFTEST prüft es)."),
            new Kind("poker", "Tische", KitData.Icon.CHIP_RED, "Poker", "Texas Hold'em, drei Farben",
                    List.of(place("Grün hier", "casino poker-tisch gruen", "Auf die Tischmitte stellen, Blick zur Seite der Dealerin"),
                            place("Rot hier", "casino poker-tisch rot", "High Roller: Tischmitte, Blick zur Dealerin"),
                            place("Violett hier", "casino poker-tisch violett", "Elite: Tischmitte, Blick zur Dealerin")),
                    pl -> List.of(new Source(pl.poker(), "Grün", T_GREEN, true), new Source(pl.pokerHr(), "Rot", T_RED, true),
                            new Source(pl.pokerVio(), "Violett", T_VIOLET, true)),
                    List.of(i("casino.poker.min-buyin-bb", "Kleinstes Buy-in (Big Blinds)", 40, 5, 10, 200),
                            i("casino.poker.turn-seconds", "Grün: Zeit je Zug (s)", 30, 5, 10, 180),
                            i("casino.poker-rot.turn-seconds", "Rot: Zeit je Zug (s)", 30, 5, 10, 180),
                            i("casino.poker-violett.turn-seconds", "Violett: Zeit je Zug (s)", 40, 5, 10, 180)),
                    "Poker steht auf ganzen Blöcken: verschieben in ganzen Blöcken, drehen in 90°-Schritten, nur wenn niemand sitzt."),
            new Kind("slots", "Automaten", KitData.Icon.SLOT_BIG, "Slots", "Automaten an der Wand",
                    List.of(place("Automat", "casino slots automat", "Auf die Wand über der Pult-Mitte schauen (Pult 1 hoch, 3 breit)"),
                            place("Klein", "casino slots automat klein", "Halbe Breite: auf die Wand über dem Pult schauen")),
                    pl -> List.of(new Source(pl.slots(), null, null, false)),
                    List.of(i("casino.slots.jackpot-start", "Jackpot-Start (drei Siebener)", 20_000, 5_000, 0, 100_000_000),
                            pct("casino.slots.jackpot-share", "Anteil jedes Drehs für den Jackpot", 0.02, 0.005, 0.2, null),
                            dec("casino.slots.hocker-luft", "Luft zwischen Hocker und Pult (Blöcke)", 0.3, 0.05, 0, 1.5),
                            bool("casino.slots.kino", "Kino-Kamera bei zwei gleichen", true, "Spannung auf der letzten Walze"),
                            bool("casino.slots.tempo-wechsel", "Dreh-Tempo wechselt jede Runde", true, null)),
                    "Automaten hängen an einer Wand: zum Neu-Aufstellen auf die Wand schauen."),
            new Kind("plinko", "Automaten", KitData.Icon.PLINKO_BIG, "Plinko", "Bretter an der Wand",
                    List.of(place("Brett", "casino plinko automat", "Auf die Wand schauen, an die das Brett soll")),
                    pl -> List.of(new Source(pl.plinko(), null, null, false)),
                    List.of(i("casino.plinko.min-bet", "Mindesteinsatz", 10, 10, 1, 1_000_000), i("casino.plinko.max-bet", "Höchsteinsatz", 5_000, 500, 10, 10_000_000)),
                    null),
            new Kind("rennen", "Automaten", KitData.Icon.EGG_HORSE, "Pferderennen", "Rennbildschirm",
                    List.of(place("Bildschirm", "casino rennen bildschirm", "Auf eine Wand schauen (gut 7 breit, 4 hoch)")),
                    pl -> List.of(new Source(pl.race(), null, null, false)),
                    List.of(i("casino.rennen.wait-seconds", "Countdown ab der ersten Wette (s)", 25, 5, 5, 180),
                            i("casino.rennen.min-bet", "Mindesteinsatz", 10, 10, 1, 1_000_000),
                            i("casino.rennen.max-round", "Höchstens je Spieler und Rennen", 20_000, 5_000, 10, 100_000_000)),
                    null),
            new Kind("crash", "Automaten", KitData.Icon.CHIP_BLACK, "Crash", "Bildschirm, Titel, Info, Schilder",
                    List.of(place("Bildschirm", "casino crash bildschirm", "Auf die Mitte der dunklen Bildfläche schauen"),
                            place("Titel", "casino crash titel", "Auf die Stelle schauen, wo „CRASH“ stehen soll"),
                            place("Runden", "casino crash runden", "Auf die Stelle für „Letzte Runden“ schauen"),
                            place("Info", "casino crash info", "Auf die Wand für die Info schauen"),
                            place("Schild", "casino crash schild", "Auf ein Schild schauen")),
                    pl -> List.of(new Source(pl.crash(), null, null, false)),
                    List.of(i("casino.crash.min-bet", "Mindesteinsatz", 10, 10, 1, 1_000_000),
                            i("casino.crash.max-bet", "Höchsteinsatz", 100_000, 10_000, 10, 100_000_000),
                            i("casino.crash.wait-seconds", "Countdown (s)", 10, 1, 3, 60),
                            dec("casino.crash.rate", "Grundtempo", 0.07, 0.01, 0.01, 0.5),
                            i("casino.crash.max-multiplier", "Höchstwert (x)", 50, 5, 2, 1000),
                            pct("casino.crash.house-edge", "Hausvorteil", 0.03, 0.005, 0.2, "Rückfluss = 100 % minus Hausvorteil")),
                    "Verschieben nimmt Bildschirm, Titel, „Letzte Runden“ und Info mit; Schilder sind echte Blöcke."),
            new Kind("gluecksrad", "Automaten", KitData.Icon.STAR, "Glücksrad", "Große Bühne",
                    List.of(place("Bühne hier", "casino gluecksrad aufstellen", "Vor eine Wand stellen, Blick zur Wand (Bühne gut 7 breit)")),
                    pl -> List.of(new Source(pl.wheel(), null, null, true)),
                    List.of(i("gluecksrad.casino.stunden", "Casino: Stunden bis zum nächsten Dreh", 24, 1, 1, 168),
                            i("gluecksrad.casino.serie-stunden", "Casino: Serie reißt nach (h)", 48, 1, 1, 336),
                            i("gluecksrad.casino.gold-dreh", "Casino: jeder …-te Dreh golden", 7, 1, 2, 50),
                            i("gluecksrad.casino.ansage-ab", "Casino: Ansage ab (Chips)", 100_000, 10_000, 0, 100_000_000),
                            i("gluecksrad.smp.stunden", "SMP: Stunden bis zum nächsten Dreh", 24, 1, 1, 168),
                            i("gluecksrad.smp.gold-dreh", "SMP: jeder …-te Dreh golden", 7, 1, 2, 50)),
                    null),
            new Kind("figuren", "Service", KitData.Icon.CHIP_PINK, "Figuren", "Kasse, Croupier, Mia …",
                    List.of(place("Kasse", "casino npc kasse", "Figur an deinem Standplatz, Blick wie du"),
                            place("Croupier", "casino npc croupier", "Figur an deinem Standplatz"),
                            place("Crash", "casino npc crash", "Creeper an deinem Standplatz"),
                            place("Mia", "casino npc mia", "DJ Mia an deinem Standplatz"),
                            place("Losbude", "casino npc losbude", "Losbude an deinem Standplatz"),
                            place("Chip-Shop", "casino npc chipshop", "Chip-Shop an deinem Standplatz"),
                            place("Jukebox", "casino npc musik", "Jukebox an deinem Standplatz"),
                            place("Barkeeper", "casino barkeeper", "Barkeeper an deinem Standplatz")),
                    pl -> List.of(new Source(pl.casinoNpc(), null, null, true)),
                    List.of(), "Figur antippen: „Variante ›“ wechselt Haut, Haare und Kleidung. Nur geladene Chunks."),
            new Kind("leute", "Service", KitData.Icon.STAR, "Leute", "Gäste und Security mit Tätigkeiten",
                    List.of(place("Gruppe", "casino leute gruppe", "Drei Gäste im Kreis um dich, unterhalten sich"),
                            place("Trinkt", "casino leute trinken", "Gast mit Glas an deinem Standplatz"),
                            place("Telefoniert", "casino leute telefon", "Geht in Blickrichtung auf und ab"),
                            place("Bestellt", "casino leute bestellen", "An die Bar stellen, Blick zur Theke"),
                            place("Wartet", "casino leute warten", "Wartet auf Freunde, Blick wie du"),
                            place("Bank", "casino leute bank", "Auf die Bank/den Hocker stellen"),
                            place("Boden", "casino leute boden", "Sitzt am Boden, Blick wie du"),
                            place("Wand", "casino leute wand", "Rücken zur Wand stellen"),
                            place("Verzweifelt", "casino leute verzweifelt", "Pechvogel am Boden, bettelt"),
                            place("Zuschauer", "casino leute zuschauer", "Neben einen Tisch: fiebert und jubelt mit"),
                            place("Security", "casino leute security", "Steht Wache, Blick wie du"),
                            place("Streife", "casino leute streife", "Läuft in Blickrichtung auf und ab")),
                    pl -> List.of(new Source(pl.people(), null, null, true)),
                    List.of(), "Variante › wechselt die Person, Größe = Weglänge (Streife, Telefon). Rechtsklick: sie reden mit dir."),
            new Kind("hologramme", "Service", KitData.Icon.BELL, "Hologramme", "Jackpot, Ranking, Eingang",
                    List.of(place("Jackpot", "casino holo jackpot", "Hologramm an deinem Standplatz"),
                            place("Ranking", "casino holo ranking", "Auf die Wand schauen (Mitte der Tafel)"),
                            place("Eingang", "casino holo eingang", "Schriftzug an deinem Standplatz")),
                    pl -> List.of(new Source(pl.casinoHolos(), null, null, true)),
                    List.of(bool("casino.jackpot-ticker.aktiv", "Jackpot zählt sichtbar hoch", true, null),
                            bool("casino.jackpot-ticker.ton", "Münzklang beim Hochzählen", true, null)),
                    null));

    private static final List<Setting> GENERAL = List.of(
            bool("casino.bedrock-gesperrt", "Casino für Bedrock gesperrt", true, "Hinweisfenster statt Spiel"),
            i("casino.cashout-tax-percent", "Steuer beim Auszahlen (%)", 5, 1, 0, 50),
            i("casino.jackpot-start", "Jackpot-Start", 10_000, 5_000, 0, 1_000_000_000),
            pct("casino.jackpot-share", "Jackpot-Anteil jedes Einsatzes", 0.01, 0.005, 0.1, null),
            pct("casino.bonus.cashback", "Cashback grün und übrige Spiele", 0.03, 0.005, 0.039, "Über 4 % verliert die Bank beim Roulette"),
            pct("casino.bonus.cashback-rot", "Cashback rote Tische", 0.035, 0.005, 0.039, null),
            pct("casino.bonus.cashback-violett", "Cashback violette Tische", 0.0375, 0.0025, 0.039, null),
            dec("casino.bonus.serie-schritt", "Siegesserie: Plus je Sieg (x)", 0.2, 0.05, 0, 2),
            dec("casino.bonus.serie-max", "Siegesserie: höchstens (x)", 2.5, 0.25, 1, 20),
            i("casino.bonus.comeback-ab", "Comeback ab Niederlagen in Folge", 3, 1, 1, 20),
            pct("casino.bonus.comeback-bonus", "Comeback-Bonus", 0.25, 0.05, 2, null),
            dec("casino.bonus.max-gesamt", "Alle Faktoren zusammen höchstens (x)", 6, 0.5, 1, 50),
            dec("casino.highroller.serie-max", "High Roller: Serie höchstens (x)", 8, 0.5, 1, 50),
            bool("casino.goldene-zeit.aktiv", "Goldene Zeit", true, "Bonus-Stunden am Abend"),
            i("casino.goldene-zeit.von", "Goldene Zeit von (Uhr)", 17, 1, 0, 23),
            i("casino.goldene-zeit.bis", "Goldene Zeit bis (Uhr)", 22, 1, 0, 24),
            dec("casino.goldene-zeit.faktor", "Goldene Zeit: Faktor", 2, 0.25, 1, 10),
            i("casino.vip.daily-chips", "VIP: tägliche Chips", 500, 100, 0, 10_000_000),
            dec("casino.music.volume", "Musik-Lautstärke", 0.6, 0.1, 0, 1),
            bool("casino.rundgang.aktiv", "Rundgang beim ersten Betreten", true, null));

    private static List<Tile> tiles(java.util.function.ToIntFunction<Kind> count) {
        List<Tile> out = new ArrayList<>();
        for (Kind k : KINDS) {
            int n = count.applyAsInt(k);
            out.add(new Tile(k.section(), k.key(), k.icon(), k.name(), n == 0 ? "noch keiner" : n + " aufgestellt"));
        }
        return out;
    }

    private static Kind kind(String key) {
        for (Kind k : KINDS) if (k.key().equals(key)) return k;
        return null;
    }

    // =================================================================================================================
    // Controller
    // =================================================================================================================

    private static final class Session {
        double step = 0.5;
        String cat;
        int src;
        String piece;
        boolean armed;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static Session session(Player p) {
        return SESSIONS.computeIfAbsent(p.getUniqueId(), k -> new Session());
    }

    public static void open(KubyPlugins plugin, Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        overview(plugin, p);
    }

    private static Nav nav(KubyPlugins plugin) {
        return key -> pl -> {
            if (!pl.hasPermission("kuby.admin")) return;
            try {
                go(plugin, pl, key);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Casino-Admin (" + key + "): " + e);
                Msg.send(pl, "<red>Fehler: " + Msg.escape(String.valueOf(e.getMessage())));
            }
        };
    }

    private static boolean isOpen(KubyPlugins plugin) {
        return plugin.getConfig().getBoolean("casino.open", true);
    }

    private static void show(Player p, Canvas c) {
        UiDialog.show(p, c, HEIGHT);
    }

    private static void go(KubyPlugins plugin, Player p, String key) {
        Session s = session(p);
        String[] k = key.split(":", 2);
        String arg = k.length > 1 ? k[1] : "";
        switch (k[0]) {
            case "tab" -> {
                switch (arg) {
                    case "aufbau" -> build(plugin, p);
                    case "einstellungen" -> general(plugin, p, 0);
                    case "log" -> log(plugin, p, 0);
                    default -> overview(plugin, p);
                }
            }
            case "toggle" -> {
                boolean now = !isOpen(plugin);
                plugin.getConfig().set("casino.open", now);
                plugin.saveConfig();
                Sfx.play(plugin, p, now ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                Msg.send(p, now ? "<green>Das Casino ist wieder offen." : "<red>Das Casino ist geschlossen (Auszahlen geht weiter).");
                overview(plugin, p);
            }
            case "jackpot" -> {
                plugin.jackpotDraw().drawNow();
                overview(plugin, p);
            }
            case "cmd" -> {
                UiDialog.close(p);
                p.performCommand(arg);
                // Kategorie danach wieder zeigen (neues Teil in der Liste)
                if (s.cat != null && arg.startsWith("casino ") && !arg.equals("casino einrichten") && !arg.equals("casino rundgang")
                        && !arg.equals("casino shop")) {
                    String cat = s.cat;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) category(plugin, p, cat, 0);
                    }, 10L);
                }
            }
            case "kat" -> {
                String[] a = arg.split(":");
                category(plugin, p, a[0], a.length > 1 ? parseInt(a[1]) : 0);
            }
            case "kset" -> {
                String[] a = arg.split(":");
                kindSettings(plugin, p, a[0], a.length > 1 ? parseInt(a[1]) : 0);
            }
            case "einst" -> general(plugin, p, parseInt(arg));
            case "log" -> log(plugin, p, parseInt(arg));
            case "teil", "hin" -> {
                String[] a = arg.split(":", 3);
                if (a.length < 3) return;
                s.cat = a[0];
                s.src = parseInt(a[1]);
                s.piece = a[2];
                s.armed = false;
                if (k[0].equals("hin")) teleport(plugin, p);
                else piece(plugin, p);
            }
            case "step" -> {
                s.step = Math.max(0.1, Math.min(5, Double.parseDouble(arg)));
                piece(plugin, p);
            }
            case "mv" -> {
                double d = arg.endsWith("-") ? -s.step : s.step;
                double dx = arg.startsWith("x") ? d : 0, dy = arg.startsWith("y") ? d : 0, dz = arg.startsWith("z") ? d : 0;
                act(plugin, p, src -> src.p().move(s.piece, dx2(src, dx), dx2(src, dy), dx2(src, dz), 0));
            }
            case "rot" -> {
                float r = Float.parseFloat(arg);
                act(plugin, p, src -> src.p().move(s.piece, 0, 0, 0, r));
            }
            case "size" -> act(plugin, p, src -> src.p().resize(s.piece, (arg.equals("-") ? -1 : 1) * src.p().sizeStep()));
            case "var" -> act(plugin, p, src -> src.p().variant(s.piece, arg.equals("-") ? -1 : 1));
            case "tier" -> {
                TableTier t = TableTier.parse(arg);
                act(plugin, p, src -> src.p() instanceof de.kuby.kubyplugins.casino.BlackjackService bj && t != null ? bj.tier(s.piece, t)
                        : "Nur Blackjack-Tische wechseln die Farbe.");
            }
            case "fetch" -> act(plugin, p, src -> {
                Placeable.Piece pc = find(src, s.piece);
                if (pc == null || pc.at() == null) return "Nicht gefunden.";
                Location me = p.getLocation(), at = pc.at();
                if (!me.getWorld().equals(at.getWorld())) return "Du bist in einer anderen Welt.";
                double tx = me.getBlockX() + 0.5, tz = me.getBlockZ() + 0.5;
                return src.p().move(s.piece, tx - at.getX(), me.getBlockY() - at.getY(), tz - at.getZ(), 0);
            });
            case "goto" -> teleport(plugin, p);
            case "del" -> {
                s.armed = true;
                piece(plugin, p);
            }
            case "del!" -> {
                Source src = source(plugin, s);
                String err = src == null ? "Nicht gefunden." : src.p().remove(s.piece);
                if (err != null) {
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                    Msg.send(p, "<red>" + err);
                    s.armed = false;
                    piece(plugin, p);
                    return;
                }
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>Entfernt.");
                s.piece = null;
                category(plugin, p, s.cat, 0);
            }
            case "set" -> setting(plugin, p, arg);
            default -> overview(plugin, p);
        }
    }

    /** Poker steht auf ganzen Blöcken: kleine Schritte auf 1 aufrunden. */
    private static double dx2(Source src, double d) {
        if (src.p() instanceof PokerService && d != 0 && Math.abs(d) < 1) return Math.signum(d);
        return d;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Source source(KubyPlugins plugin, Session s) {
        Kind k = kind(s.cat);
        if (k == null) return null;
        List<Source> l = k.sources().apply(plugin);
        return s.src >= 0 && s.src < l.size() ? l.get(s.src) : null;
    }

    private static Placeable.Piece find(Source src, String id) {
        for (Placeable.Piece pc : src.p().pieces()) if (pc.id().equals(id)) return pc;
        return null;
    }

    /** Aktion auf dem gewählten Teil, Meldung bei Fehler, dann die Teil-Seite neu. */
    private static void act(KubyPlugins plugin, Player p, java.util.function.Function<Source, String> f) {
        Session s = session(p);
        s.armed = false;
        Source src = source(plugin, s);
        String err = src == null ? "Nicht gefunden." : f.apply(src);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        } else {
            Sfx.play(plugin, p, Sfx.Kind.CLICK);
        }
        piece(plugin, p);
    }

    private static void teleport(KubyPlugins plugin, Player p) {
        Session s = session(p);
        Source src = source(plugin, s);
        Placeable.Piece pc = src == null ? null : find(src, s.piece);
        if (pc == null || pc.at() == null || pc.at().getWorld() == null) {
            Msg.send(p, "<red>Nicht gefunden.");
            return;
        }
        double r = Math.toRadians(pc.yaw());
        Location to = pc.at().clone().add(-Math.sin(r) * 3, 0.1, Math.cos(r) * 3);
        to.setYaw(Placeable.norm(pc.yaw() + 180f));
        to.setPitch(15);
        p.teleport(to);
        piece(plugin, p);
    }

    // ---------- Seiten mit Daten ----------

    private static int count(KubyPlugins plugin, Kind k) {
        int n = 0;
        for (Source s : k.sources().apply(plugin)) {
            try {
                n += s.p().pieces().size();
            } catch (RuntimeException ignored) {
            }
        }
        return n;
    }

    private static void overview(KubyPlugins plugin, Player p) {
        session(p).cat = null;
        List<String[]> counts = new ArrayList<>();
        for (Kind k : KINDS) {
            int n = count(plugin, k);
            if (n > 0) counts.add(new String[]{k.name(), String.valueOf(n)});
        }
        List<String[]> bank = new ArrayList<>();
        Map<String, CasinoLog.Totals> totals = plugin.casinoLog().totals();
        for (Map.Entry<String, CasinoLog.Totals> e : totals.entrySet()) {
            long house = e.getValue().staked - e.getValue().paid;
            bank.add(new String[]{e.getKey(), Msg.coins(e.getValue().rounds), (house >= 0 ? "+" : "") + Msg.coins(house)});
        }
        String jp = Msg.coins(plugin.casino().jackpot());
        show(p, overview(new Overview(isOpen(plugin), jp, plugin.jackpotDraw().nextDrawLabel(), plugin.jackpotDraw().participantCount(),
                counts, bank, plugin.casinoLog().jackpotsPaid() + " (" + Msg.coins(plugin.casinoLog().jackpotMoney()) + ")"), nav(plugin)));
    }

    private static void build(KubyPlugins plugin, Player p) {
        session(p).cat = null;
        show(p, build(tiles(k -> count(plugin, k)), isOpen(plugin), nav(plugin)));
    }

    private static void category(KubyPlugins plugin, Player p, String key, int page) {
        Kind k = kind(key);
        if (k == null) {
            build(plugin, p);
            return;
        }
        Session s = session(p);
        s.cat = key;
        List<Row> all = new ArrayList<>();
        List<Source> sources = k.sources().apply(plugin);
        for (int si = 0; si < sources.size(); si++) {
            Source src = sources.get(si);
            for (Placeable.Piece pc : src.p().pieces()) {
                TextColor col = src.color();
                String name = pc.name();
                if (src.p() instanceof de.kuby.kubyplugins.casino.BlackjackService bj) {
                    TableTier t = bj.tierOf(pc.id());
                    col = t == TableTier.ROT ? T_RED : t == TableTier.VIOLETT ? T_VIOLET : T_GREEN;
                }
                StringBuilder sub = new StringBuilder(Placeable.where(pc.at()));
                if (pc.at() != null && pc.at().getWorld() != null && !pc.at().getWorld().equals(p.getWorld())) sub.append(" (").append(pc.at().getWorld().getName()).append(")");
                sub.append(" · ").append(Math.round(pc.yaw())).append("°");
                if (!Double.isNaN(pc.size())) sub.append(" · Größe ").append(Placeable.short1(pc.size()));
                if (pc.info() != null && !pc.info().isEmpty()) sub.append(" · ").append(pc.info());
                all.add(new Row(name, col, sub.toString(), key + ":" + si + ":" + pc.id(), pc.busy()));
            }
        }
        List<Btn> place = new ArrayList<>(k.place());
        if (!k.settings().isEmpty()) place.add(new Btn("Einstellungen", KitData.Small.VIOLET, LIGHT, "kset:" + key, k.name() + ": Werte einstellen"));
        int per = perPage(place);
        int pages = Math.max(1, (all.size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
        show(p, category(new Cat(k.icon(), k.name(), k.desc(), place, all.subList(pg * per, Math.min(all.size(), (pg + 1) * per)), pg, pages, key,
                "Noch nichts aufgestellt. Oben „… hier“ drücken: es wird an deinem Standplatz bzw. Blickpunkt aufgebaut."), nav(plugin)));
    }

    private static void piece(KubyPlugins plugin, Player p) {
        Session s = session(p);
        Kind k = kind(s.cat);
        Source src = source(plugin, s);
        Placeable.Piece pc = src == null ? null : find(src, s.piece);
        if (k == null || pc == null) {
            if (k != null) category(plugin, p, k.key(), 0);
            else build(plugin, p);
            return;
        }
        boolean poker = src.p() instanceof PokerService;
        TableTier tier = src.p() instanceof de.kuby.kubyplugins.casino.BlackjackService bj ? bj.tierOf(pc.id()) : null;
        String size = src.p().sizeStep() > 0 && !Double.isNaN(pc.size()) ? Placeable.short1(pc.size()) : null;
        String sub = Placeable.where(pc.at()) + " · Blick " + Math.round(pc.yaw()) + "°" + (pc.info() == null || pc.info().isEmpty() ? "" : " · " + pc.info());
        String hint = pc.busy() ? "Gerade in Benutzung: Verschieben geht erst danach." : k.hint();
        String name = pc.name() + (src.tier() != null && !pc.name().contains("High") && !pc.name().contains("Elite") && !src.tier().equals("Grün")
                ? " · " + src.tier() : "");
        show(p, piece(new PieceView(k.icon(), name, sub, poker ? Math.max(1, s.step) : s.step, poker, size, tier, src.floor(), s.armed,
                s.cat, hint, src.p().variantName(pc.id())), nav(plugin)));
    }

    // ---------- Einstellungen ----------

    private static String value(KubyPlugins plugin, Setting st) {
        double v = plugin.getConfig().getDouble(st.path(), st.def());
        return switch (st.kind()) {
            case 1 -> new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(v * 100) + " %";
            case 2 -> new java.text.DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(v);
            default -> Msg.coins(Math.round(v));
        };
    }

    private static List<SetRow> rows(KubyPlugins plugin, List<Setting> list, int from, int to) {
        List<SetRow> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Setting st = list.get(i);
            boolean on = st.kind() == 3 && plugin.getConfig().getBoolean(st.path(), st.def() > 0);
            out.add(new SetRow(st.path(), st.label(), st.kind() == 3 ? "" : value(plugin, st), st.kind() == 3, on, st.hint()));
        }
        return out;
    }

    private static void general(KubyPlugins plugin, Player p, int page) {
        session(p).cat = null;
        int pages = Math.max(1, (GENERAL.size() + SET_PER - 1) / SET_PER), pg = Math.max(0, Math.min(page, pages - 1));
        show(p, settings(new SettingsView(KitData.Icon.COMPARATOR, "EINSTELLUNGEN", "Allgemein: Jackpot, Boni, Cashback, Goldene Zeit",
                rows(plugin, GENERAL, pg * SET_PER, Math.min(GENERAL.size(), (pg + 1) * SET_PER)), pg, pages, null, "einst:"), isOpen(plugin), nav(plugin)));
    }

    private static void kindSettings(KubyPlugins plugin, Player p, String key, int page) {
        Kind k = kind(key);
        if (k == null || k.settings().isEmpty()) {
            category(plugin, p, key, 0);
            return;
        }
        session(p).cat = key;
        int pages = Math.max(1, (k.settings().size() + SET_PER - 1) / SET_PER), pg = Math.max(0, Math.min(page, pages - 1));
        show(p, settings(new SettingsView(k.icon(), k.name() + " · Einstellungen", k.desc(),
                rows(plugin, k.settings(), pg * SET_PER, Math.min(k.settings().size(), (pg + 1) * SET_PER)), pg, pages, "kat:" + key,
                "kset:" + key + ":"), isOpen(plugin), nav(plugin)));
    }

    private static Setting settingOf(String path) {
        for (Setting s : GENERAL) if (s.path().equals(path)) return s;
        for (Kind k : KINDS) for (Setting s : k.settings()) if (s.path().equals(path)) return s;
        return null;
    }

    /** set:&lt;pfad&gt;:+|-|toggle|edit */
    private static void setting(KubyPlugins plugin, Player p, String arg) {
        int c = arg.lastIndexOf(':');
        if (c < 0) return;
        String path = arg.substring(0, c), op = arg.substring(c + 1);
        Setting st = settingOf(path);
        if (st == null) return;
        if (op.equals("edit")) {
            ask(plugin, p, st);
            return;
        }
        if (st.kind() == 3) {
            plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, st.def() > 0));
        } else {
            double v = plugin.getConfig().getDouble(path, st.def()) + (op.equals("-") ? -st.step() : st.step());
            store(plugin, st, v);
        }
        plugin.saveConfig();
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        back(plugin, p, st);
    }

    private static void store(KubyPlugins plugin, Setting st, double v) {
        v = Math.max(st.min(), Math.min(st.max(), v));
        if (st.kind() == 0) plugin.getConfig().set(st.path(), Math.round(v));
        else plugin.getConfig().set(st.path(), Math.round(v * 100000.0) / 100000.0);
    }

    /** Nach einer Änderung zur Seite der Einstellung zurück. */
    private static void back(KubyPlugins plugin, Player p, Setting st) {
        int gi = GENERAL.indexOf(st);
        if (gi >= 0) {
            general(plugin, p, gi / SET_PER);
            return;
        }
        for (Kind k : KINDS) {
            int i = k.settings().indexOf(st);
            if (i >= 0) {
                kindSettings(plugin, p, k.key(), i / SET_PER);
                return;
            }
        }
    }

    private static void ask(KubyPlugins plugin, Player p, Setting st) {
        Canvas head = base();
        head.box(0, 0, W, 40, KitData.Box.CV_HEAD);
        head.icon(10, 4, KitData.Icon.NAME_TAG, true);
        head.text(47, 9, Canvas.fit(st.label(), true, W - 57), GOLD, true);
        String range = st.kind() == 1 ? "0 bis " + Placeable.short1(st.max() * 100) + " (Prozent)"
                : Placeable.short1(st.min()) + " bis " + Placeable.short1(st.max());
        head.text(47, 22, Canvas.fit("Jetzt " + value(plugin, st) + " · erlaubt " + range, false, W - 57), GRAY, false);
        String init = st.kind() == 1 ? Placeable.short1(plugin.getConfig().getDouble(st.path(), st.def()) * 100)
                : st.kind() == 0 ? String.valueOf(Math.round(plugin.getConfig().getDouble(st.path(), st.def())))
                : Placeable.short1(plugin.getConfig().getDouble(st.path(), st.def()));
        List<DialogInput> inputs = List.of(DialogInput.text("v", Msg.parse("<#B0A6BE>Neuer Wert")).width(300).maxLength(20).initial(init).build());
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        DialogAction ok = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl) || !pl.hasPermission("kuby.admin")) return;
            String raw = response.getText("v");
            try {
                double v = Double.parseDouble(raw.trim().replace(".", "").replace(',', '.').replace("%", "").replace("k", "000"));
                if (st.kind() == 1) v /= 100.0;
                store(plugin, st, v);
                plugin.saveConfig();
                Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
            } catch (NumberFormatException e) {
                Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                Msg.send(pl, "<red>Keine Zahl: " + Msg.escape(raw));
            }
            back(plugin, pl, st);
        }, once);
        DialogAction cancel = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) back(plugin, pl, st);
        }, once);
        ActionButton yes = ActionButton.create(Msg.parse("<#F4D46A><b>Übernehmen</b>"), null, 150, ok);
        ActionButton no = ActionButton.create(Msg.parse("<#B0A6BE>‹ Zurück"), null, 150, cancel);
        Dialog d = UiDialog.dialog(p, head, inputs, List.of(yes, no), 2);
        UiDialog.open(p, d);
    }

    private static void log(KubyPlugins plugin, Player p, int page) {
        session(p).cat = null;
        List<String> all = plugin.casinoLog().recent();
        int pages = Math.max(1, (all.size() + LOG_PER - 1) / LOG_PER), pg = Math.max(0, Math.min(page, pages - 1));
        show(p, log(new ArrayList<>(all.subList(pg * LOG_PER, Math.min(all.size(), (pg + 1) * LOG_PER))), pg, pages, isOpen(plugin), nav(plugin)));
    }

    @SuppressWarnings("unused")
    private static final TextColor WHITE = NamedTextColor.WHITE;
}
