package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyService;
import de.kuby.kubyplugins.service.PlaytimeService;
import de.kuby.kubyplugins.service.PlaytimeService.Prize;
import de.kuby.kubyplugins.service.PlaytimeService.Stage;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.I18n;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /playtime nach der Referenz „Neue-referenzen“: Kopf mit Geschenk, abgeholten Stufen und aktiver Spielzeit; darunter
 * „Alle abholen“ und die Seiten (4 Stufen je Seite, die letzte Seite zeigt die letzten 4). Jede Stufe ist ein Kasten in
 * der Farbe ihrer Gruppe (bereit: grün) mit Name, Belohnungen und Balken; der Tooltip zeigt alles noch einmal. Ohne
 * Discord-Verlinkung führt der linke Knopf zu /verify. Ohne Pack gibt es die Truhe (PlaytimeMenu).
 */
public final class PlaytimeScreen {

    private static final int W = 390, HEIGHT = 357, PER_PAGE = 4, ROW_Y = 81, ROW_H = 60, ROW_DY = 72;
    private static final int BAR_X = 30, BAR_W = 200;
    private static final TextColor GRAY = TextColor.color(0x9FA1A7), LIGHT = TextColor.color(0xC0C4CB),
            GOLD = TextColor.color(0xFCD45D), VIOLET = TextColor.color(0xB7A6FC), TEAL = TextColor.color(0x7AE5D7),
            GREEN = TextColor.color(0x6DE288), JEWEL = TextColor.color(0x5FE3A0), WHITE = TextColor.color(0xFCFCFC),
            DOT = TextColor.color(0x797D84), ON_GREEN = TextColor.color(0x1C122D), RED = TextColor.color(0xFF8A8A),
            IDLE = TextColor.color(0xD1D2D5), IDLE_GIFT = TextColor.color(0xC2C6CE);

    public enum State { CLAIMED, READY, LOCKED }

    /** Eine Belohnung: Text in der Zeile ("2× Vote-Key"), im Tooltip ("+2× Vote-Schlüssel"), Farben dafür. */
    public record Reward(String text, TextColor color, String tip, String tipColor) {}

    public record Row(int number, String name, String style, long minutes, List<Reward> rewards, State state) {}

    /** blocked: warum gerade nichts abgeholt werden darf (Discord), sonst null. page -1: Seite der nächsten Stufe. */
    public record View(long activeMinutes, List<Row> stages, String blocked, int page) {}

    public interface Actions {
        Consumer<Player> claim(int number);

        Consumer<Player> claimAll();

        Consumer<Player> verify();

        Consumer<Player> page(int page);
    }

    /** Kasten und Schriftfarbe je Farbe (PlaytimeService.STYLES). */
    private record Look(KitData.Box box, TextColor color) {}

    private static Look look(String style) {
        return switch (style) {
            case "tuerkis" -> new Look(KitData.Box.PT_TEAL, TEAL);
            case "gold" -> new Look(KitData.Box.PT_GOLD, GOLD);
            case "orange" -> new Look(KitData.Box.PT_ORANGE, TextColor.color(0xFCB079));
            case "bernstein" -> new Look(KitData.Box.PT_AMBER, TextColor.color(0xFCC965));
            case "blau" -> new Look(KitData.Box.PT_BLUE, TextColor.color(0x7BC4FC));
            case "pink" -> new Look(KitData.Box.PT_PINK, TextColor.color(0xFC99DA));
            case "lila" -> new Look(KitData.Box.PT_PURPLE, VIOLET);
            case "rot" -> new Look(KitData.Box.PT_RED, TextColor.color(0xFC7986));
            default -> new Look(KitData.Box.PT_GRAY, LIGHT);
        };
    }

    private final KubyPlugins plugin;

    public PlaytimeScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (plugin.uiPack().active(p)) show(p, -1);
        else new PlaytimeMenu(plugin).open(p);
    }

    /** Dauer in Stunden und Minuten, z. B. "42 Std. 15 Min." oder "20 Min.". */
    public static String duration(long min) {
        long h = min / 60, m = min % 60;
        if (h == 0) return m + " Min.";
        return Msg.coins(h) + " Std." + (m == 0 ? "" : " " + m + " Min.");
    }

    /** Restzeit wie in der Referenz: ab 100 Stunden ohne Minuten ("noch 107 Std."). */
    public static String left(long min) {
        return min >= 100 * 60 ? Msg.coins(min / 60) + " Std." : duration(min);
    }

    private Reward reward(Prize pr) {
        long n = pr.amount();
        return switch (pr.kind()) {
            case "coins" -> new Reward(Msg.coins(n) + " Coins", GOLD, "+" + Msg.coins(n) + " Coins", "gold");
            case "tokens" -> new Reward(n + (n == 1 ? " Token" : " Tokens"), VIOLET, "+" + n + (n == 1 ? " Token" : " Tokens"),
                    "light_purple");
            case "jewels" -> new Reward(n + (n == 1 ? " Juwel" : " Juwelen"), JEWEL, "+" + n + (n == 1 ? " Juwel" : " Juwelen"),
                    "green");
            case "xp" -> new Reward(Msg.coins(n) + " XP", GREEN, "+" + Msg.coins(n) + " XP", "green");
            case "key" -> {
                KeyService.KeyType t = plugin.keys().type(pr.value());
                String label = t == null ? pr.value() + "-Key" : t.label();
                String full = t == null ? pr.value() + "-Schlüssel" : t.full();
                yield new Reward(n + "× " + label, TEAL, "+" + n + "× " + full, "aqua");
            }
            default -> {
                String d = plugin.playtime().describe(pr);
                yield new Reward(d, LIGHT, "+" + d, "white");
            }
        };
    }

    /** Für den Selbsttest: eine Seite mit den echten Stufen eines Modus, aktive Zeit act, ohne Klicks. */
    public Canvas preview(String mode, long act, int page) {
        List<Row> rows = new ArrayList<>();
        for (Stage s : plugin.playtime().stages(mode)) {
            List<Reward> rewards = new ArrayList<>();
            for (Prize pr : s.prizes()) rewards.add(reward(pr));
            rows.add(new Row(s.number(), s.title(), s.style(), s.minutes(), rewards, act >= s.minutes() ? State.READY : State.LOCKED));
        }
        Consumer<Player> no = pl -> { };
        return canvas(new View(act, rows, null, page), new Actions() {
            public Consumer<Player> claim(int number) { return no; }
            public Consumer<Player> claimAll() { return no; }
            public Consumer<Player> verify() { return no; }
            public Consumer<Player> page(int n) { return no; }
        });
    }

    /** Seiten der Stufen eines Modus. */
    public int pageCount(String mode) {
        return pages(plugin.playtime().stages(mode).size());
    }

    /** page -1: die Seite mit der ersten noch nicht abgeholten Stufe. */
    public void show(Player p, int page) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        PlaytimeService pt = plugin.playtime();
        long act = pt.activeMinutes(id, mode);
        List<Row> rows = new ArrayList<>();
        for (Stage s : pt.stages(mode)) {
            List<Reward> rewards = new ArrayList<>();
            for (Prize pr : s.prizes()) rewards.add(reward(pr));
            State st = pt.isClaimed(id, mode, s) ? State.CLAIMED : act >= s.minutes() ? State.READY : State.LOCKED;
            rows.add(new Row(s.number(), s.title(), s.style(), s.minutes(), rewards, st));
        }
        View v = new View(act, rows, pt.blocked(p), page);
        UiDialog.show(p, canvas(v, new Actions() {
            public Consumer<Player> claim(int number) {
                return pl -> {
                    String m = plugin.modes().modeOf(pl.getUniqueId());
                    Stage s = pt.stages(m).stream().filter(x -> x.number() == number).findFirst().orElse(null);
                    String err = s == null ? "Diese Stufe gibt es nicht mehr." : pt.claim(pl, s);
                    if (err != null) {
                        Msg.send(pl, "<red>" + err);
                        Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                    } else {
                        List<String> what = new ArrayList<>();
                        for (Prize pr : s.prizes()) what.add(pt.describe(pr));
                        Msg.send(pl, "<#F2C84A><b>SPIELZEIT</b> <dark_gray>▶ <gray>Stufe " + number + " abgeholt: <white>"
                                + String.join(", ", what));
                        Sfx.play(plugin, pl, Sfx.Kind.LEVEL_UP);
                    }
                    show(pl, pageOf(number - 1, pt.stages(m).size()));
                };
            }

            public Consumer<Player> claimAll() {
                return pl -> {
                    int n = pt.claimAll(pl);
                    if (n < 0) Msg.send(pl, "<red>" + pt.blocked(pl));
                    else if (n == 0) Msg.send(pl, "<gray>Gerade ist keine Stufe fertig.");
                    else {
                        Msg.send(pl, "<#F2C84A><b>SPIELZEIT</b> <dark_gray>▶ <gray>" + n + (n == 1 ? " Stufe" : " Stufen") + " abgeholt.");
                        Sfx.play(plugin, pl, Sfx.Kind.SUCCESS_BIG);
                    }
                    show(pl, -1);
                };
            }

            public Consumer<Player> verify() {
                return pl -> {
                    pl.closeDialog();
                    pl.performCommand("verify");
                };
            }

            public Consumer<Player> page(int n) {
                return pl -> show(pl, n);
            }
        }), HEIGHT);
    }

    private static int pages(int count) {
        return Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
    }

    /** Erste Stufe (Index) einer Seite; die letzte Seite zeigt die letzten PER_PAGE Stufen. */
    private static int first(int page, int count) {
        return Math.max(0, Math.min(page * PER_PAGE, count - PER_PAGE));
    }

    private static int pageOf(int index, int count) {
        return Math.min(index / PER_PAGE, pages(count) - 1);
    }

    private static Component tooltip(Row r, long act) {
        List<String> lines = new ArrayList<>();
        lines.add("<white><b>" + r.name());
        lines.add("<gray>Ab <yellow>" + Msg.coins(r.minutes() / 60) + " Std.</yellow> Spielzeit");
        lines.add("");
        lines.add("<gray>Belohnung:");
        for (Reward w : r.rewards()) lines.add(" <" + w.tipColor() + ">" + w.tip());
        lines.add("");
        switch (r.state()) {
            case CLAIMED -> lines.add("<green>✔ Abgeholt");
            case READY -> lines.add("<green>Bereit zum Abholen!");
            case LOCKED -> {
                lines.add("<gray>Fortschritt: <yellow>" + percent(act, r.minutes()) + "%");
                lines.add("<gray>Noch <yellow>" + left(r.minutes() - act));
            }
        }
        // Zeilen als Ganzes übersetzen (zerlegt in Komponenten passen die Vorlagen nicht mehr)
        lines.replaceAll(I18n::ctx);
        return Msg.parse(String.join("<reset>\n", lines));
    }

    private static int percent(long act, long need) {
        return need <= 0 ? 100 : (int) Math.min(100, act * 100 / need);
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        List<Row> rows = v.stages();
        int claimed = 0, ready = 0;
        for (Row r : rows) {
            if (r.state() == State.CLAIMED) claimed++;
            if (r.state() == State.READY) ready++;
        }
        Canvas c = new Canvas(W).heading("Spielzeit", GOLD);

        // Kopf
        c.box(0, 0, W, 51, KitData.Box.PT_HEAD);
        c.sprite(11, 9, KitData.Sprite.PT_GIFT_BIG);
        c.text(50, 16, "SPIELZEIT", GOLD, true);
        c.text(50, 29, claimed + " von " + rows.size() + " Stufen abgeholt", GRAY, false);
        c.sprite(234, 18, KitData.Sprite.PT_HOURGLASS);
        c.text(254, 17, "Deine", GRAY, false);
        c.text(254, 27, "Spielzeit", GRAY, false);
        c.textRight(380, 22, duration(v.activeMinutes()), GOLD, true);

        // Alle abholen / Discord / nichts bereit
        if (v.blocked() != null && ready > 0) {
            c.box(0, 54, 187, 24, KitData.Box.PT_IDLE);
            c.sprite(7, 58, KitData.Sprite.PT_GIFT);
            c.text(30, 62, Canvas.fit("Erst Discord verlinken", false, 136), RED, false);
            c.text(172, 62, "›", RED, true);
            c.action(0, 54, 187, 24, a.verify(), "Zum Abholen muss dein Discord verlinkt sein (/verify)");
        } else if (ready > 0) {
            c.box(0, 54, 187, 24, KitData.Box.PT_CLAIM);
            c.sprite(7, 58, KitData.Sprite.PT_GIFT);
            c.text(30, 62, "Alle abholen (" + ready + ")", GREEN, true);
            c.text(172, 62, "›", GREEN, true);
            c.action(0, 54, 187, 24, a.claimAll(), "Alle fertigen Stufen abholen");
        } else {
            c.box(0, 54, 187, 24, KitData.Box.PT_IDLE);
            c.sprite(7, 58, KitData.Sprite.PT_GIFT, Canvas.Z_ICON, IDLE_GIFT);
            c.text(30, 62, "Gerade nichts bereit", IDLE, false);
        }

        // Seiten
        int pages = pages(rows.size());
        int pg = v.page();
        if (pg < 0) {
            int focus = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).state() != State.CLAIMED) {
                    focus = i;
                    break;
                }
            }
            pg = pageOf(focus, rows.size());
        }
        pg = Math.max(0, Math.min(pg, pages - 1));
        int from = first(pg, rows.size()), to = Math.min(rows.size(), from + PER_PAGE);
        c.box(203, 54, 187, 24, KitData.Box.PT_PAGER);
        c.sprite(209, 59, pg > 0 ? KitData.Sprite.PT_NAV_L_ON : KitData.Sprite.PT_NAV_L_OFF);
        c.sprite(360, 59, pg + 1 < pages ? KitData.Sprite.PT_NAV_R_ON : KitData.Sprite.PT_NAV_R_OFF);
        if (pg > 0) c.action(209, 59, 24, 14, a.page(pg - 1), "Vorige Seite");
        if (pg + 1 < pages) c.action(360, 59, 24, 14, a.page(pg + 1), "Nächste Seite");
        if (!rows.isEmpty()) {
            c.textCenter(296, 57, "Stufen " + rows.get(from).number() + " bis " + rows.get(to - 1).number(), WHITE, true);
            c.textCenter(296, 66, Msg.coins(rows.get(from).minutes() / 60) + " bis "
                    + Msg.coins(rows.get(to - 1).minutes() / 60) + " Std.", GRAY, false);
        } else {
            c.textCenter(296, 62, "Keine Stufen", GRAY, false);
        }

        // Stufen
        if (rows.isEmpty()) {
            c.box(0, ROW_Y, W, ROW_H, KitData.Box.PT_GRAY);
            c.sprite(10, ROW_Y + 9, KitData.Sprite.PT_HOURGLASS);
            c.text(30, ROW_Y + 12, "In diesem Modus gibt es keine Spielzeit-Stufen", LIGHT, true);
            c.text(30, ROW_Y + 23, "Sie gibt es in Survival.", GRAY, false);
        }
        for (int i = from; i < to; i++) {
            Row r = rows.get(i);
            int y = ROW_Y + ROW_DY * (i - from);
            Look lk = look(r.style());
            c.box(0, y, W, ROW_H, r.state() == State.READY ? KitData.Box.PT_READY : lk.box());
            if (r.state() == State.LOCKED) c.sprite(10, y + 9, KitData.Sprite.PT_HOURGLASS);
            else c.sprite(8, y + 9, KitData.Sprite.PT_GIFT);
            String ab = "ab " + Msg.coins(r.minutes() / 60) + " Std.";
            int abX = 380 - Canvas.width(ab, false);
            c.text(30, y + 12, Canvas.fit(r.name(), true, abX - 36), lk.color(), true);
            c.text(abX, y + 12, ab, GRAY, false);
            // Belohnungen: „100 Coins · 1 Token · 1× Vote-Key“
            List<Span> line = new ArrayList<>();
            int lw = 0;
            for (Reward w : r.rewards()) {
                String sep = line.isEmpty() ? "" : " · ";
                int add = Canvas.width(sep, false) + Canvas.width(w.text(), false);
                if (lw + add > 350) break;
                if (!sep.isEmpty()) line.add(Span.of(sep, DOT));
                line.add(Span.of(w.text(), w.color()));
                lw += add;
            }
            c.text(30, y + 23, line);
            // Balken und Stand
            int by = y + 35;
            switch (r.state()) {
                case READY, CLAIMED -> {
                    c.sprite(BAR_X, by, KitData.Sprite.PT_BAR_READY, Canvas.Z_BUTTON);
                    if (r.state() == State.READY) {
                        c.text(238, y + 38, "Bereit!", GREEN, true);
                        c.box(316, y + 34, 64, 14, KitData.Box.PT_BTN, Canvas.Z_BUTTON);
                        c.textCenter(347, y + 37, "Abholen", ON_GREEN, false);
                    } else {
                        c.text(238, y + 38, "✔ Abgeholt", GREEN, true);
                    }
                }
                case LOCKED -> {
                    long need = Math.max(1, r.minutes());
                    int fill = Math.max(3, (int) Math.round(BAR_W * (double) v.activeMinutes() / need) - 1);
                    c.sprite(BAR_X, by, KitData.Sprite.PT_BAR_EMPTY, Canvas.Z_BUTTON);
                    c.strip(BAR_X + 1, by + 1, KitData.Strip.PT_GOLD, Math.min(fill, BAR_W - 3));
                    String pct = percent(v.activeMinutes(), r.minutes()) + "%";
                    c.text(238, y + 38, pct, lk.color(), true);
                    c.text(238 + Canvas.width(pct, true) + 8, y + 38, "noch " + left(r.minutes() - v.activeMinutes()), GRAY, false);
                }
            }
            // Tooltip über der ganzen Zeile, der Knopf darin klickbar (spätere Bereiche gehen vor)
            c.action(0, y, W, ROW_H, null, tooltip(r, v.activeMinutes()));
            if (r.state() == State.READY) {
                c.action(316, y + 34, 64, 14, v.blocked() != null ? a.verify() : a.claim(r.number()), tooltip(r, v.activeMinutes()));
            }
        }
        return c;
    }
}
