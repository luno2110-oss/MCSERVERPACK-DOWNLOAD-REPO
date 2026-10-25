package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.PlaytimeService;
import de.kuby.kubyplugins.service.PlaytimeService.Prize;
import de.kuby.kubyplugins.service.PlaytimeService.Stage;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /playtime auf dem Dialog-Kit (Kopf wie die Hilfe-Seiten): aktive Zeit im Modus, Fortschritt zur nächsten Stufe,
 * alle Stufen mit ihren Belohnungen (Icons), Abholen je Stufe und „Alle abholen“. Ohne Discord-Verlinkung ein Hinweis
 * statt der Abholen-Knöpfe. Ohne Pack gibt es die Truhe (PlaytimeMenu).
 */
public final class PlaytimeScreen {

    private static final int W = 390, HEIGHT = 330, ROW = 20;
    private static final TextColor ACCENT = Ui.Theme.GOLD.accent, GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x6CE86E), PURPLE = TextColor.color(0xD08CFF), JEWEL = TextColor.color(0x5FE3A0),
            DIM = TextColor.color(0x70707A), ON = TextColor.color(0x201400), NAV = TextColor.color(0xC0C4CB),
            RED = TextColor.color(0xFF8A8A);

    public enum State { CLAIMED, READY, LOCKED }

    /** Eine Belohnung zum Anzeigen: Icon und Text in seiner Farbe. */
    public record Chip(KitData.Icon icon, String text, TextColor color) {}

    public record Row(int number, long minutes, List<Chip> chips, State state) {}

    public record View(String modeLabel, long activeMinutes, List<Row> stages, String blocked, int page) {}

    public interface Actions {
        Consumer<Player> claim(int number);

        Consumer<Player> claimAll();

        Consumer<Player> verify();

        Consumer<Player> page(int page);
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
        return h + " Std." + (m == 0 ? "" : " " + m + " Min.");
    }

    private Chip chip(Prize pr) {
        return switch (pr.kind()) {
            case "coins" -> new Chip(KitData.Icon.GOLD_NUGGET, Msg.coins(pr.amount()), GOLD);
            case "tokens" -> new Chip(KitData.Icon.AMETHYST, String.valueOf(pr.amount()), PURPLE);
            case "jewels" -> new Chip(KitData.Icon.EMERALD, String.valueOf(pr.amount()), JEWEL);
            case "xp" -> new Chip(KitData.Icon.EXPERIENCE, Msg.coins(pr.amount()), GREEN);
            case "key" -> {
                KeyService.KeyType t = plugin.keys().type(pr.value());
                String name = t == null ? pr.value() : t.name();
                TextColor c = t == null ? GOLD : TextColor.fromHexString(t.color()) == null ? GOLD : TextColor.fromHexString(t.color());
                yield new Chip(KitData.Icon.TRIAL_KEY, (pr.amount() > 1 ? pr.amount() + "× " : "") + name, c);
            }
            case "tag" -> new Chip(KitData.Icon.NAME_TAG, "Titel", LIGHT);
            default -> new Chip(KitData.Icon.BUNDLE, "Extra", LIGHT);
        };
    }

    /** page -1: die Seite mit der ersten fertigen oder der nächsten Stufe. */
    public void show(Player p, int page) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        PlaytimeService pt = plugin.playtime();
        long act = pt.activeMinutes(id, mode);
        List<Row> rows = new ArrayList<>();
        for (Stage s : pt.stages(mode)) {
            List<Chip> chips = new ArrayList<>();
            for (Prize pr : s.prizes()) chips.add(chip(pr));
            State st = pt.isClaimed(id, mode, s) ? State.CLAIMED : act >= s.minutes() ? State.READY : State.LOCKED;
            rows.add(new Row(s.number(), s.minutes(), chips, st));
        }
        View v = new View(ModeService.label(mode), act, rows, pt.blocked(p), page);
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
                    show(pl, -1);
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

    private static int perPage(View v) {
        int top = 53 + 39 + (v.blocked() != null ? 37 : 0);
        return Math.max(3, (HEIGHT - top - 22 - 4 - KitData.BTN_H - 4) / ROW);
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 51, KitData.Box.GOLD);
        c.smallIcon(13, 16, KitData.SmallIcon.H_HOURGLASS);
        c.text(41, 15, "SPIELZEIT", Ui.Theme.GOLD.title, true);
        c.text(41, 28, "Aktive Zeit in " + v.modeLabel() + ", ohne AFK", GRAY, false);
        c.textRight(378, 21, List.of(Span.bold(duration(v.activeMinutes()), GOLD)));
        int y = 53;
        List<Row> rows = v.stages();

        // Fortschritt zur nächsten Stufe
        Row next = rows.stream().filter(r -> r.state() == State.LOCKED).findFirst().orElse(null);
        long prevMin = 0;
        for (Row r : rows) if (r.state() != State.LOCKED) prevMin = Math.max(prevMin, r.minutes());
        c.box(0, y, W, 36, KitData.Box.GOLD);
        c.text(9, y + 9, "FORTSCHRITT", ACCENT, true);
        if (rows.isEmpty()) {
            c.textRight(378, y + 9, "In diesem Modus gibt es keine Stufen", GRAY, false);
            c.bar(12, y + 22, W - 24, 0, KitData.Fill.GOLD);
        } else if (next == null) {
            c.textRight(378, y + 9, List.of(Span.bold("Alle " + rows.size() + " Stufen erreicht", GREEN)));
            c.bar(12, y + 22, W - 24, 1, KitData.Fill.GOLD);
        } else {
            long span = Math.max(1, next.minutes() - prevMin);
            double frac = (double) (v.activeMinutes() - prevMin) / span;
            c.textRight(378, y + 9, List.of(Span.of("noch ", GRAY), Span.bold(duration(next.minutes() - v.activeMinutes()), WHITE),
                    Span.of(" bis Stufe " + next.number(), GRAY)));
            c.bar(12, y + 22, W - 24, frac, KitData.Fill.GOLD);
        }
        y += 39;

        // Ohne Discord: Hinweis statt Abholen
        if (v.blocked() != null) {
            c.box(0, y, W, 34, KitData.Box.RED);
            c.smallIcon(9, y + 8, KitData.SmallIcon.H_DISCORD);
            c.text(35, y + 7, "Zum Abholen muss dein Discord verlinkt sein.", WHITE, true);
            c.text(35, y + 19, "Klick hier: so verlinkst du deinen Account (/verify).", LIGHT, false);
            c.action(0, y, W, 34, a.verify(), "Anleitung zum Verlinken");
            y += 37;
        }

        // Stufen mit Seiten
        int per = perPage(v);
        int pages = Math.max(1, (rows.size() + per - 1) / per);
        int pg = v.page();
        if (pg < 0) {
            int focus = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).state() != State.CLAIMED) {
                    focus = i;
                    break;
                }
            }
            pg = focus / per;
        }
        pg = Math.max(0, Math.min(pg, pages - 1));
        int from = pg * per, to = Math.min(rows.size(), from + per);
        int h = 22 + ROW * Math.max(1, to - from) + 2;
        c.box(0, y, W, h, KitData.Box.GOLD);
        c.text(9, y + 9, "STUFEN", ACCENT, true);
        if (pages > 1) c.textRight(378, y + 9, "Seite " + (pg + 1) + " / " + pages, GRAY, false);
        if (rows.isEmpty()) c.text(12, y + 23, "Spielzeit-Stufen gibt es in Survival.", LIGHT, false);
        int ready = 0;
        for (Row r : rows) if (r.state() == State.READY) ready++;
        for (int i = from; i < to; i++) {
            Row r = rows.get(i);
            int ty = y + 19 + ROW * (i - from);
            TextColor nameColor = switch (r.state()) {
                case CLAIMED -> GRAY;
                case READY -> WHITE;
                case LOCKED -> LIGHT;
            };
            c.text(12, ty + 4, "Stufe " + r.number(), nameColor, true);
            c.text(62, ty + 4, r.minutes() / 60 + " Std.", r.state() == State.CLAIMED ? DIM : GRAY, false);
            // rechts: Status
            int right;
            if (r.state() == State.CLAIMED) {
                right = 378 - Canvas.width("✔ abgeholt", false);
                c.text(right, ty + 4, "✔ abgeholt", GREEN, false);
            } else if (r.state() == State.READY && v.blocked() == null) {
                int w = Canvas.width("Abholen", true) + 14;
                right = 380 - w;
                c.smallButton(right, ty, w, KitData.Small.GOLD, "Abholen", GOLD, true);
                c.action(right, ty, w, KitData.SMALL_H, a.claim(r.number()), "Stufe " + r.number() + " abholen");
            } else if (r.state() == State.READY) {
                right = 378 - Canvas.width("fertig", true);
                c.text(right, ty + 4, "fertig", GOLD, true);
            } else {
                String left = "noch " + duration(r.minutes() - v.activeMinutes());
                right = 378 - Canvas.width(left, false);
                c.text(right, ty + 4, left, DIM, false);
            }
            // Mitte: Belohnungen
            int px = 104;
            for (Chip ch : r.chips()) {
                int tw = Canvas.width(ch.text(), true);
                if (px + 18 + tw > right - 6) break;
                c.icon(px, ty, ch.icon(), false);
                c.text(px + 18, ty + 4, ch.text(), r.state() == State.CLAIMED ? DIM : ch.color(), true);
                px += 18 + tw + 7;
            }
        }
        y += h + 3;

        // Knöpfe
        boolean can = ready > 0 && v.blocked() == null;
        String label = can ? "Alle abholen (" + ready + ")" : "Alle abholen";
        int w = Canvas.width(label, can) - 1 + (can ? 24 : 18);
        c.button(0, y, w, can ? KitData.Button.H_GOLD : KitData.Button.H_NAV, label, can ? ON : DIM, can);
        if (can) c.action(0, y, w, KitData.BTN_H, a.claimAll(), "Alle fertigen Stufen abholen");
        if (pages > 1) {
            int nw = Canvas.width("›", false) + 18;
            if (pg + 1 < pages) {
                c.button(W - nw, y, nw, KitData.Button.H_NAV, "›", NAV, false);
                c.action(W - nw, y, nw, KitData.BTN_H, a.page(pg + 1), "Nächste Seite");
            }
            if (pg > 0) {
                c.button(W - 2 * nw - 4, y, nw, KitData.Button.H_NAV, "‹", NAV, false);
                c.action(W - 2 * nw - 4, y, nw, KitData.BTN_H, a.page(pg - 1), "Vorige Seite");
            }
        }
        return c;
    }
}
