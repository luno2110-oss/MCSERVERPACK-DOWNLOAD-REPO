package de.kuby.kubyplugins.staff;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.SettingsMenu;
import de.kuby.kubyplugins.service.AlertService;
import de.kuby.kubyplugins.service.AltService;
import de.kuby.kubyplugins.service.BattlepassService;
import de.kuby.kubyplugins.service.EconomyLog;
import de.kuby.kubyplugins.service.MarketService;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.service.TeamService;
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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Kadmin-Seiten als Dialog statt Truhe: ein gemeinsamer Listen-Baustein (Kopf, Knöpfe, Zeilen mit Icon, Text, Wert und
 * Knöpfen, Seiten) für Einstellungen, Config-Editor, Shop, Preisliste, Battlepass, Markt, Teams sowie die neuen
 * Bereiche Wirtschaft (Protokoll mit Suche), Warnungen, Backups und Zweitkonten. Eingaben über Textfeld-Dialoge.
 */
public final class KadminScreens {

    public static final int W = 390, HEIGHT = 330, PER = 8;

    static final TextColor GOLD = TextColor.color(0xF4D46A), GOLD_SOFT = TextColor.color(0xF2DA90), GRAY = TextColor.color(0xACA48E),
            DIM = TextColor.color(0x7E7868), LIGHT = TextColor.color(0xE8E0D8), GREEN = TextColor.color(0x6BF07A),
            RED = TextColor.color(0xFF5A5A), ORANGE = TextColor.color(0xFF9A4A), ON_GREEN = TextColor.color(0x06240E),
            WHITE = NamedTextColor.WHITE, BTN_GRAY = TextColor.color(0x8E8A84);

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    /** Knopf: gold = Hauptaktion, act null = gesperrt (grau), on = als aktiver Filter (grün). */
    public record Btn(String label, boolean gold, Consumer<Player> act, String hover, boolean on) {
        public Btn(String label, boolean gold, Consumer<Player> act, String hover) {
            this(label, gold, act, hover, false);
        }
    }

    /** Zeile: Icon, Titel (Farbe), Unterzeile, Wert rechts (Farbe), Knöpfe rechts, Klick auf die ganze Zeile. */
    public record Row(Material icon, String title, TextColor color, String sub, String value, TextColor valueColor, List<Btn> buttons,
                      Consumer<Player> click, String hover) {}

    public record Page(KitData.Icon icon, String title, String sub, List<Btn> top, List<Row> rows, int page, int pages,
                       Consumer<Player> prev, Consumer<Player> next, Consumer<Player> back, String empty) {}

    private final KubyPlugins plugin;

    public KadminScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Baustein
    // =================================================================================================================

    private static int bw(String label) {
        return Canvas.width(label, true) + 14;
    }

    private static int button(Canvas c, int x, int y, Btn b) {
        int w = bw(b.label());
        if (b.act() == null) {
            c.smallButton(x, y, w, KitData.Small.GOLD_GRAY, b.label(), BTN_GRAY, true);
            if (b.hover() != null) c.action(x, y, w, 16, null, b.hover());
        } else {
            KitData.Small st = b.on() ? KitData.Small.GREEN : b.gold() ? KitData.Small.GOLD : KitData.Small.DARK;
            c.smallButton(x, y, w, st, b.label(), b.on() ? ON_GREEN : b.gold() ? GOLD_SOFT : LIGHT, true);
            c.action(x, y, w, 16, b.act(), b.hover() == null ? b.label() : b.hover());
        }
        return w;
    }

    public static Canvas render(Page v) {
        Canvas c = new Canvas(W).heading("Kadmin", TextColor.color(0xF2B829));
        c.box(0, 0, W, 51, KitData.Box.CASINO);
        c.icon(10, 10, v.icon(), true);
        int backW = v.back() == null ? 0 : bw("‹ Zurück");
        int room = 378 - backW - 8 - 47;
        c.text(47, 14, Canvas.fit(v.title(), true, room), GOLD, true);
        c.text(47, 33, Canvas.fit(v.sub(), false, room), GRAY, false);
        if (v.back() != null) button(c, 380 - backW, 9, new Btn("‹ Zurück", false, v.back(), "Zurück"));
        // Knopfleiste
        int x = 0, y = 55;
        for (Btn b : v.top()) {
            int w = bw(b.label());
            if (x + w > W) {
                x = 0;
                y += 19;
            }
            x += button(c, x, y, b) + 3;
        }
        y += 21;
        if (v.rows().isEmpty()) {
            c.box(0, y, W, 26, KitData.Box.CASINO_DARK);
            c.text(10, y + 9, Canvas.fit(v.empty() == null ? "Nichts da." : v.empty(), false, W - 20), GRAY, false);
            y += 30;
        }
        for (Row r : v.rows()) {
            c.box(0, y, W, 26, KitData.Box.CASINO_DARK);
            int tx = 8;
            if (r.icon() != null) {
                c.item(6, y + 5, r.icon(), false, KitData.Icon.PAPER);
                tx = 27;
            }
            int right = W - 6;
            List<Btn> bs = r.buttons() == null ? List.of() : r.buttons();
            for (int i = bs.size() - 1; i >= 0; i--) {
                Btn b = bs.get(i);
                int w = bw(b.label());
                right -= w;
                button(c, right, y + 5, b);
                right -= 3;
            }
            int vw = 0;
            if (r.value() != null && !r.value().isEmpty()) {
                vw = c.textRight(right - 3, y + 9, Canvas.fit(r.value(), true, 120), r.valueColor() == null ? WHITE : r.valueColor(), true) + 8;
            }
            int max = right - 3 - vw - tx;
            boolean two = r.sub() != null && !r.sub().isEmpty();
            c.text(tx, y + (two ? 4 : 9), Canvas.fit(r.title(), true, max), r.color() == null ? GOLD_SOFT : r.color(), true);
            if (two) c.text(tx, y + 15, Canvas.fit(r.sub(), false, max), GRAY, false);
            if (r.click() != null) c.action(0, y, Math.max(40, right - 3), 26, r.click(), r.hover() == null ? r.title() : r.hover());
            y += 28;
        }
        if (v.pages() > 1) {
            String label = "Seite " + (v.page() + 1) + " / " + v.pages();
            int lw = Canvas.width(label, false);
            c.textCenter(W / 2, y + 6, label, GRAY, false);
            int lx = W / 2 - lw / 2 - 8 - 24, rx = W / 2 + lw / 2 + 8;
            c.smallButton(lx, y + 2, 24, v.prev() != null ? KitData.Small.DARK : KitData.Small.GOLD_GRAY, "‹", v.prev() != null ? LIGHT : BTN_GRAY, true);
            if (v.prev() != null) c.action(lx, y + 2, 24, 16, v.prev(), "Vorherige Seite");
            c.smallButton(rx, y + 2, 24, v.next() != null ? KitData.Small.DARK : KitData.Small.GOLD_GRAY, "›", v.next() != null ? LIGHT : BTN_GRAY, true);
            if (v.next() != null) c.action(rx, y + 2, 24, 16, v.next(), "Nächste Seite");
        }
        return c;
    }

    /** Selbsttest: Grenzwerte (lange Texte, große Zahlen, viele Knöpfe). */
    public static void samples(BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Btn> many = List.of(new Btn("Spieler: WWWWWWWWWWWWWWWW", true, no, null), new Btn("Herkunft: Überweisung an WWWW", false, no, null),
                new Btn("Alle", false, no, null, true), new Btn("ab 100.000", false, no, null), new Btn("ab 1 Mio.", false, no, null),
                new Btn("24 Std.", false, no, null, true), new Btn("7 Tage", false, no, null));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < PER; i++) {
            rows.add(new Row(Material.GOLD_NUGGET, "WWWWWWWWWWWWWWWW · Überweisung an WWWWWWWWWWWWWWWW", GOLD_SOFT,
                    "vor 59 Min. · Skyblock · Stand 999,9 Mrd. $ · " + "W".repeat(40), "+999,9 Mrd. $", GREEN,
                    i % 2 == 0 ? List.of(new Btn("Buchungen", false, no, null), new Btn("Erledigt", true, no, null))
                            : List.of(new Btn("−100", false, no, null), new Btn("−10", false, no, null), new Btn("+10", false, no, null),
                            new Btn("+100", false, no, null), new Btn("Wert", true, no, null)), no, null));
        }
        reg.accept("Kadmin Liste", () -> render(new Page(KitData.Icon.GOLD_INGOT, "Wirtschaft " + "W".repeat(30), "W".repeat(90), many, rows, 3, 9,
                no, no, no, null)));
        reg.accept("Kadmin leer", () -> render(new Page(KitData.Icon.BELL, "Warnungen", "0 offen", List.of(new Btn("Offen", false, no, null, true)),
                List.of(), 0, 1, null, null, no, "Keine offenen Warnungen.")));
    }

    private void show(Player p, Page v) {
        UiDialog.show(p, render(v), HEIGHT);
    }

    /** Seite aus allen Zeilen schneiden. */
    private static <T> List<T> slice(List<T> all, int page) {
        int from = Math.min(all.size(), page * PER);
        return all.subList(from, Math.min(all.size(), from + PER));
    }

    private static int pages(int n) {
        return Math.max(1, (n + PER - 1) / PER);
    }

    private static int clamp(int page, int n) {
        return Math.max(0, Math.min(page, pages(n) - 1));
    }

    private void back(Player p) {
        if (plugin.staffMenus() != null) plugin.staffMenus().kadmin(p);
    }

    /** Textfeld-Dialog: Kopf, ein Feld, „OK“ und „Zurück“. */
    public void ask(Player p, String title, String label, String initial, int max, BiConsumer<Player, String> ok, Consumer<Player> back) {
        Canvas head = new Canvas(W).heading("Kadmin", TextColor.color(0xF2B829));
        head.box(0, 0, W, 40, KitData.Box.CASINO);
        head.icon(10, 4, KitData.Icon.NAME_TAG, true);
        head.text(47, 9, Canvas.fit(title, true, W - 57), GOLD, true);
        head.text(47, 22, Canvas.fit(label, false, W - 57), GRAY, false);
        List<DialogInput> inputs = List.of(DialogInput.text("text", Msg.parse("<#ACA48E>" + Msg.escape(label)))
                .width(360).maxLength(max).initial(initial == null ? "" : initial).build());
        DialogAction submit = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) ok.accept(pl, response.getText("text"));
        }, ONCE);
        DialogAction cancel = DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) back.accept(pl);
        }, ONCE);
        ActionButton yes = ActionButton.create(Msg.parse("<#F4D46A><b>OK</b>"), null, 150, submit);
        ActionButton no = ActionButton.create(Msg.parse("<#ACA48E>‹ Zurück"), null, 150, cancel);
        Dialog d = UiDialog.dialog(p, head, inputs, List.of(yes, no), 2);
        UiDialog.open(p, d);
    }

    /** Sicherheitsfrage mit „Ja“ (rot) und „Abbrechen“. */
    public void confirm(Player p, String title, String text, String yesLabel, Consumer<Player> yes, Consumer<Player> no) {
        List<Row> rows = List.of(new Row(Material.BARRIER, title, RED, text, null, null,
                List.of(new Btn(yesLabel, true, pl -> yes.accept(pl), title), new Btn("Abbrechen", false, no, "Abbrechen")), null, null));
        show(p, new Page(KitData.Icon.BARRIER, "Sicher?", title, List.of(), rows, 0, 1, null, null, no, null));
    }

    private void ok(Player p, String msg) {
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>" + msg);
    }

    private void err(Player p, String msg) {
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
        Msg.send(p, "<red>" + msg);
    }

    private static long num(String s, long def) {
        if (s == null) return def;
        try {
            return Long.parseLong(s.trim().replace(".", "").replace("_", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String ago(long time) {
        long m = (System.currentTimeMillis() - time) / 60_000L;
        if (m < 1) return "gerade eben";
        if (m < 60) return "vor " + m + " Min.";
        if (m < 48 * 60) return "vor " + (m / 60) + " Std.";
        return "vor " + (m / 1440) + " Tagen";
    }

    private static String plain(String mini) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.parse(mini));
    }

    private static String name(UUID id) {
        if (id == null) return "-";
        OfflinePlayer o = Bukkit.getOfflinePlayer(id);
        return o.getName() == null ? id.toString().substring(0, 8) : o.getName();
    }

    // =================================================================================================================
    // Wirtschaft (Protokoll mit Suche)
    // =================================================================================================================

    /** Filter je Admin: Spieler, Herkunft, Mindestbetrag, Tage. */
    private record Filter(String player, String source, long min, int days) {}

    private final Map<UUID, Filter> filters = new HashMap<>();

    public void economy(Player p, int page) {
        Filter f = filters.getOrDefault(p.getUniqueId(), new Filter(null, null, 0, 1));
        UUID who = null;
        if (f.player() != null) {
            OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(f.player());
            who = o == null ? null : o.getUniqueId();
        }
        UUID target = who;
        long since = System.currentTimeMillis() - f.days() * 86_400_000L;
        String src = f.source() == null ? null : f.source().toLowerCase(Locale.ROOT);
        List<EconomyLog.Entry> hits = plugin.economyLog().search(e -> e.time() >= since
                && (target == null || e.player().equals(target))
                && (f.player() == null || target != null)
                && Math.abs(e.amount()) >= f.min()
                && (src == null || e.source().toLowerCase(Locale.ROOT).contains(src)), 400);
        long in = 0, out = 0;
        for (EconomyLog.Entry e : hits) {
            if (e.amount() > 0) in += e.amount();
            else out -= e.amount();
        }
        int pg = clamp(page, hits.size());
        List<Row> rows = new ArrayList<>();
        for (EconomyLog.Entry e : slice(hits, pg)) {
            String n = name(e.player());
            rows.add(new Row(e.amount() > 0 ? Material.GOLD_NUGGET : Material.IRON_NUGGET, n + " · " + e.source(), GOLD_SOFT,
                    ago(e.time()) + " · " + de.kuby.kubyplugins.service.ModeService.label(e.mode()) + " · Stand " + Canvas.num(e.balance()) + " $",
                    (e.amount() > 0 ? "+" : "") + Canvas.num(e.amount()) + " $", e.amount() > 0 ? GREEN : RED,
                    List.of(), pl -> {
                        filters.put(pl.getUniqueId(), new Filter(n, f.source(), f.min(), f.days()));
                        economy(pl, 0);
                    }, "Nur " + n + " zeigen"));
        }
        List<Btn> top = new ArrayList<>();
        top.add(new Btn(f.player() == null ? "Spieler" : "Spieler: " + f.player(), true, pl -> ask(pl, "Spieler suchen",
                "Name (leer = alle)", f.player(), 16, (q, t) -> {
                    filters.put(q.getUniqueId(), new Filter(t == null || t.isBlank() ? null : t.trim(), f.source(), f.min(), f.days()));
                    economy(q, 0);
                }, q -> economy(q, 0)), "Nach Spieler filtern"));
        top.add(new Btn(f.source() == null ? "Herkunft" : "Herkunft: " + f.source(), false, pl -> ask(pl, "Herkunft suchen",
                "z. B. Markt, Überweisung, Casino, Admin (leer = alle)", f.source(), 30, (q, t) -> {
                    filters.put(q.getUniqueId(), new Filter(f.player(), t == null || t.isBlank() ? null : t.trim(), f.min(), f.days()));
                    economy(q, 0);
                }, q -> economy(q, 0)), "Nach Herkunft filtern"));
        for (long m : new long[]{0, 100_000, 1_000_000}) {
            top.add(new Btn(m == 0 ? "Alle" : "ab " + Canvas.num(m), false, pl -> {
                filters.put(pl.getUniqueId(), new Filter(f.player(), f.source(), m, f.days()));
                economy(pl, 0);
            }, "Mindestbetrag", f.min() == m));
        }
        for (int d : new int[]{1, 7}) {
            top.add(new Btn(d == 1 ? "24 Std." : "7 Tage", false, pl -> {
                filters.put(pl.getUniqueId(), new Filter(f.player(), f.source(), f.min(), d));
                economy(pl, 0);
            }, "Zeitraum", f.days() == d));
        }
        String sub = hits.size() + (hits.size() >= 400 ? "+" : "") + " Buchungen · rein " + Canvas.num(in) + " $ · raus " + Canvas.num(out) + " $";
        if (f.player() != null && target == null) sub = "Spieler „" + f.player() + "“ unbekannt";
        show(p, new Page(KitData.Icon.GOLD_INGOT, "Wirtschaft", sub, top, rows, pg, pages(hits.size()),
                pg > 0 ? pl -> economy(pl, pg - 1) : null, pg + 1 < pages(hits.size()) ? pl -> economy(pl, pg + 1) : null,
                this::back, "Keine Buchungen für diese Suche (protokolliert ab " + Canvas.num(plugin.getConfig().getLong("wirtschaft.protokoll-ab", 1000))
                + " $, Überweisungen und Admin immer)."));
    }

    // =================================================================================================================
    // Warnungen
    // =================================================================================================================

    public void alerts(Player p, int page, boolean onlyOpen) {
        List<AlertService.Alert> all = plugin.alerts().list(onlyOpen);
        int pg = clamp(page, all.size());
        List<Row> rows = new ArrayList<>();
        for (AlertService.Alert a : slice(all, pg)) {
            List<Btn> bs = new ArrayList<>();
            if (a.player != null) {
                String n = name(a.player);
                bs.add(new Btn("Buchungen", false, pl -> {
                    filters.put(pl.getUniqueId(), new Filter(n, null, 0, 7));
                    economy(pl, 0);
                }, "Wirtschafts-Protokoll von " + n));
            }
            if (!a.done) bs.add(new Btn("Erledigt", true, pl -> {
                plugin.alerts().done(a.id);
                alerts(pl, pg, onlyOpen);
            }, "Als erledigt markieren"));
            rows.add(new Row(a.done ? Material.GRAY_DYE : Material.RED_DYE, a.kind.label, a.done ? DIM : TextColor.fromHexString(a.kind.color),
                    ago(a.time) + " · " + a.text, null, null, bs, null, a.text));
        }
        List<Btn> top = List.of(
                new Btn("Offen", false, pl -> alerts(pl, 0, true), "Nur offene", onlyOpen),
                new Btn("Alle", false, pl -> alerts(pl, 0, false), "Auch erledigte", !onlyOpen),
                new Btn("Alle erledigt", true, plugin.alerts().open() > 0 ? pl -> {
                    plugin.alerts().doneAll();
                    alerts(pl, 0, onlyOpen);
                } : null, "Alle als erledigt markieren"));
        show(p, new Page(KitData.Icon.BELL, "Warnungen", plugin.alerts().open() + " offen · große Gutschriften, schnelles Geld, Überweisungen, Zweitkonten",
                top, rows, pg, pages(all.size()), pg > 0 ? pl -> alerts(pl, pg - 1, onlyOpen) : null,
                pg + 1 < pages(all.size()) ? pl -> alerts(pl, pg + 1, onlyOpen) : null, this::back,
                onlyOpen ? "Keine offenen Warnungen." : "Noch keine Warnungen."));
    }

    // =================================================================================================================
    // Backups
    // =================================================================================================================

    public void backups(Player p, int page) {
        var b = plugin.backups();
        List<File> all = b.list();
        int pg = clamp(page, all.size());
        String pending = b.pending();
        List<Row> rows = new ArrayList<>();
        for (File f : slice(all, pg)) {
            String n = f.getName();
            boolean marked = n.equals(pending);
            rows.add(new Row(marked ? Material.RECOVERY_COMPASS : Material.CHEST, n.replace("kuby-", "").replace(".zip", ""),
                    marked ? ORANGE : GOLD_SOFT, ago(f.lastModified()) + (marked ? " · wird beim nächsten Start eingespielt" : ""),
                    String.format(Locale.GERMANY, "%.1f MB", f.length() / 1024.0 / 1024.0), GRAY,
                    List.of(new Btn("Zurück", false, pl -> confirm(pl, "Wiederherstellen: " + n,
                            "Beim nächsten Neustart werden alle Kuby-Daten und Spielerdaten auf diesen Stand gesetzt. Der aktuelle Stand wird vorher gesichert.",
                            "Vormerken", q -> {
                                String err = b.schedule(f);
                                if (err != null) err(q, err);
                                else ok(q, "Vorgemerkt: " + n + ". Jetzt den Server neu starten.");
                                backups(q, pg);
                            }, q -> backups(q, pg)), "Auf diesen Stand zurücksetzen (beim Neustart)")), null, null));
        }
        List<Btn> top = new ArrayList<>();
        top.add(new Btn(b.running() ? "Läuft …" : "Jetzt sichern", true, b.running() ? null : pl -> {
            String err = b.run("manuell", m -> Msg.send(pl, "<green>" + Msg.escape(m)));
            if (err != null) err(pl, err);
            else Msg.send(pl, "<gray>Backup läuft im Hintergrund …");
            backups(pl, 0);
        }, "Sofort ein Backup machen"));
        if (pending != null) top.add(new Btn("Vormerkung löschen", false, pl -> {
            b.cancel();
            ok(pl, "Wiederherstellung abgebrochen.");
            backups(pl, pg);
        }, "Doch nichts wiederherstellen"));
        long total = 0;
        for (File f : all) total += f.length();
        show(p, new Page(KitData.Icon.CHEST, "Backups", all.size() + " Backups · " + String.format(Locale.GERMANY, "%.0f MB", total / 1024.0 / 1024.0)
                + " · alle " + plugin.getConfig().getLong("backup.intervall-minuten", 60) + " Min. · letztes: " + b.last(),
                top, rows, pg, pages(all.size()), pg > 0 ? pl -> backups(pl, pg - 1) : null,
                pg + 1 < pages(all.size()) ? pl -> backups(pl, pg + 1) : null, this::back, "Noch keine Backups (das erste kommt 10 Minuten nach dem Start)."));
    }

    // =================================================================================================================
    // Zweitkonten
    // =================================================================================================================

    public void alts(Player p, String query) {
        AltService a = plugin.alts();
        List<Row> rows = new ArrayList<>();
        String sub;
        if (query == null) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                List<UUID> l = a.alts(o.getUniqueId());
                if (l.isEmpty()) continue;
                List<String> names = new ArrayList<>();
                for (UUID u : l) names.add(name(u));
                rows.add(new Row(Material.PLAYER_HEAD, o.getName(), GOLD_SOFT, "teilt die IP mit " + String.join(", ", names), l.size() + "", ORANGE,
                        List.of(new Btn("Details", false, pl -> alts(pl, o.getName()), "Alle Konten mit gleicher IP")), null, null));
                if (rows.size() >= PER) break;
            }
            sub = "Online mit gleicher IP wie andere Konten · Sperren " + (a.enforce() ? "an" : "aus");
        } else {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(query);
            if (t == null) {
                sub = "Spieler „" + query + "“ unbekannt";
            } else {
                for (AltService.Link l : a.links(t.getUniqueId())) {
                    String n = name(l.other());
                    boolean banned = Bukkit.getOfflinePlayer(l.other()).isBanned();
                    String state = l.allowed() ? "freigegeben" : l.recent() ? "verknüpft" : "alt";
                    rows.add(new Row(banned ? Material.BARRIER : Material.PLAYER_HEAD, n, banned ? RED : GOLD_SOFT,
                            "zuletzt " + ago(l.lastSeen()) + (banned ? " · gebannt" : ""), state, l.allowed() ? GREEN : l.recent() ? ORANGE : DIM,
                            List.of(new Btn(l.allowed() ? "Sperren" : "Freigeben", !l.allowed(), pl -> {
                                a.allow(t.getUniqueId(), l.other(), !l.allowed());
                                ok(pl, l.allowed() ? n + " gilt wieder als verknüpft." : n + " ist freigegeben (z. B. Geschwister).");
                                alts(pl, query);
                            }, l.allowed() ? "Wieder als Zweitkonto behandeln" : "Kein Zweitkonto (Geschwister o. Ä.)")), null, null));
                    if (rows.size() >= PER * 2) break;
                }
                sub = rows.size() + " Konto/Konten mit gleicher IP wie " + query;
            }
        }
        List<Btn> top = new ArrayList<>();
        top.add(new Btn("Spieler prüfen", true, pl -> ask(pl, "Zweitkonten prüfen", "Spielername", query, 16,
                (q, t) -> alts(q, t == null || t.isBlank() ? null : t.trim()), q -> alts(q, query)), "Beliebigen Spieler prüfen"));
        if (query != null) top.add(new Btn("Online-Liste", false, pl -> alts(pl, null), "Alle online mit Zweitkonten"));
        show(p, new Page(KitData.Icon.NAME_TAG, "Zweitkonten", sub, top, rows, 0, 1, null, null, this::back,
                query == null ? "Niemand online teilt gerade eine IP mit einem anderen Konto." : "Keine anderen Konten mit dieser IP."));
    }

    // =================================================================================================================
    // Einstellungen
    // =================================================================================================================

    public void settings(Player p, int cat) {
        List<SettingsMenu.Category> cats = SettingsMenu.categories();
        if (cat < 0 || cat >= cats.size()) {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < cats.size(); i++) {
                SettingsMenu.Category c = cats.get(i);
                int idx = i;
                rows.add(new Row(c.icon(), c.name(), GOLD_SOFT, null, c.settings().size() + " Werte", GRAY,
                        List.of(new Btn("Öffnen", false, pl -> settings(pl, idx), c.name())), pl -> settings(pl, idx), c.name()));
            }
            int pg = 0;
            showAll(p, KitData.Icon.COMPARATOR, "Einstellungen", "Wichtige Werte nach Bereich", rows, List.of(
                    new Btn("Config-Editor", false, pl -> config(pl, "", 0), "Jeder Wert der config"),
                    new Btn("Preisliste", false, pl -> worth(pl, 0), "Verkaufspreise")), pg, (pl, n) -> settings(pl, -1), this::back);
            return;
        }
        SettingsMenu.Category c = cats.get(cat);
        List<Row> rows = new ArrayList<>();
        for (SettingsMenu.Setting s : c.settings()) {
            String path = s.path();
            List<Btn> bs = new ArrayList<>();
            String value;
            switch (s.kind()) {
                case BOOL -> {
                    boolean on = plugin.getConfig().getBoolean(path);
                    value = on ? "an" : "aus";
                    bs.add(new Btn(on ? "Aus" : "An", true, pl -> {
                        plugin.getConfig().set(path, !on);
                        save(pl);
                        settings(pl, cat);
                    }, "Umschalten"));
                }
                case TEXT -> {
                    value = plugin.getConfig().getString(path, "");
                    bs.add(new Btn("Ändern", true, pl -> ask(pl, s.label(), s.hint(), plugin.getConfig().getString(path, ""), 200, (q, t) -> {
                        plugin.getConfig().set(path, t);
                        save(q);
                        settings(q, cat);
                    }, q -> settings(q, cat)), "Neuen Text eingeben"));
                }
                default -> {
                    double cur = plugin.getConfig().getDouble(path);
                    value = s.kind() == SettingsMenu.Kind.INT ? Canvas.num((long) cur) : String.format(Locale.GERMANY, "%.2f", cur);
                    for (int sign : new int[]{-1, 1}) {
                        bs.add(new Btn(sign < 0 ? "−" : "+", false, pl -> {
                            double v = Math.max(s.min(), Math.min(s.max(), plugin.getConfig().getDouble(path) + sign * s.step()));
                            plugin.getConfig().set(path, s.kind() == SettingsMenu.Kind.INT ? (Object) (int) Math.round(v) : (Object) (Math.round(v * 100) / 100.0));
                            save(pl);
                            settings(pl, cat);
                        }, (sign < 0 ? "−" : "+") + (s.kind() == SettingsMenu.Kind.INT ? String.valueOf((long) s.step()) : String.valueOf(s.step()))));
                    }
                    bs.add(new Btn("Wert", true, pl -> ask(pl, s.label(), s.hint() + " (" + fmt(s, s.min()) + " bis " + fmt(s, s.max()) + ")",
                            value, 20, (q, t) -> {
                                try {
                                    double v = Math.max(s.min(), Math.min(s.max(), Double.parseDouble(t.trim().replace(",", "."))));
                                    plugin.getConfig().set(path, s.kind() == SettingsMenu.Kind.INT ? (Object) (int) Math.round(v) : (Object) (Math.round(v * 100) / 100.0));
                                    save(q);
                                } catch (NumberFormatException ex) {
                                    err(q, "Keine Zahl.");
                                }
                                settings(q, cat);
                            }, q -> settings(q, cat)), "Wert eingeben"));
                }
            }
            rows.add(new Row(null, s.label(), GOLD_SOFT, s.hint(), value, WHITE, bs, null, path));
        }
        showAll(p, KitData.Icon.COMPARATOR, c.name(), "Einstellungen · Änderungen gelten sofort", rows, List.of(), 0,
                (pl, n) -> settings(pl, cat), pl -> settings(pl, -1));
    }

    private static String fmt(SettingsMenu.Setting s, double v) {
        return s.kind() == SettingsMenu.Kind.INT ? Canvas.num((long) v) : String.valueOf(v);
    }

    private void save(Player p) {
        plugin.saveConfig();
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
    }

    /** Alle Zeilen mit eigener Seitenwahl (Seite in der Lambda). */
    private final Map<UUID, Integer> lastPage = new HashMap<>();

    private void showAll(Player p, KitData.Icon icon, String title, String sub, List<Row> rows, List<Btn> top, int ignored,
                         BiConsumer<Player, Integer> reopen, Consumer<Player> back) {
        String key = title;
        int pg = clamp(lastPage.getOrDefault(p.getUniqueId(), 0), rows.size());
        lastPage.put(p.getUniqueId(), pg);
        show(p, new Page(icon, title, sub, top, slice(rows, pg), pg, pages(rows.size()),
                pg > 0 ? pl -> {
                    lastPage.put(pl.getUniqueId(), pg - 1);
                    reopen.accept(pl, pg - 1);
                } : null,
                pg + 1 < pages(rows.size()) ? pl -> {
                    lastPage.put(pl.getUniqueId(), pg + 1);
                    reopen.accept(pl, pg + 1);
                } : null,
                pl -> {
                    lastPage.put(pl.getUniqueId(), 0);
                    back.accept(pl);
                }, "Keine Einträge (" + key + ")."));
    }

    /** Seite zurücksetzen, wenn eine neue Liste geöffnet wird. */
    private void fresh(Player p) {
        lastPage.put(p.getUniqueId(), 0);
    }

    // =================================================================================================================
    // Config-Editor
    // =================================================================================================================

    public void config(Player p, String path, int ignored) {
        ConfigurationSection cs = path.isEmpty() ? plugin.getConfig() : plugin.getConfig().getConfigurationSection(path);
        if (cs == null) {
            err(p, "Diesen Bereich gibt es nicht mehr.");
            config(p, "", 0);
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (String key : cs.getKeys(false)) {
            String full = path.isEmpty() ? key : path + "." + key;
            Object v = cs.get(key);
            if (cs.isConfigurationSection(key)) {
                int n = cs.getConfigurationSection(key).getKeys(false).size();
                rows.add(new Row(Material.CHEST, key, GOLD_SOFT, null, n + " Einträge", GRAY,
                        List.of(new Btn("Öffnen", false, pl -> {
                            fresh(pl);
                            config(pl, full, 0);
                        }, full)), pl -> {
                    fresh(pl);
                    config(pl, full, 0);
                }, full));
            } else if (v instanceof Boolean b) {
                rows.add(new Row(b ? Material.LIME_DYE : Material.GRAY_DYE, key, GOLD_SOFT, null, b ? "an" : "aus", b ? GREEN : DIM,
                        List.of(new Btn(b ? "Aus" : "An", true, pl -> {
                            plugin.getConfig().set(full, !b);
                            save(pl);
                            config(pl, path, 0);
                        }, "Umschalten")), null, full));
            } else if (v instanceof List<?> l) {
                List<String> parts = new ArrayList<>();
                for (Object o : l) parts.add(String.valueOf(o));
                rows.add(new Row(Material.BOOK, key, GOLD_SOFT, Canvas.fit(String.join(" | ", parts), false, 400), l.size() + " Zeilen", GRAY,
                        List.of(new Btn("Ändern", true, pl -> ask(pl, full, "Zeilen mit | trennen", String.join(" | ", parts), 4000, (q, t) -> {
                            List<String> out = new ArrayList<>();
                            for (String s : t.split("\\|")) if (!s.isBlank()) out.add(s.trim());
                            plugin.getConfig().set(full, out);
                            save(q);
                            config(q, path, 0);
                        }, q -> config(q, path, 0)), "Liste bearbeiten")), null, full));
            } else {
                boolean number = v instanceof Number;
                rows.add(new Row(number ? Material.IRON_NUGGET : Material.NAME_TAG, key, GOLD_SOFT, null, String.valueOf(v), WHITE,
                        List.of(new Btn("Ändern", true, pl -> ask(pl, full, number ? "Zahl" : "Text", String.valueOf(v), 500, (q, t) -> {
                            Object nv = t;
                            if (number) {
                                try {
                                    nv = v instanceof Double || v instanceof Float ? (Object) Double.parseDouble(t.trim().replace(",", "."))
                                            : (Object) Long.parseLong(t.trim());
                                } catch (NumberFormatException ex) {
                                    err(q, "Keine Zahl.");
                                    config(q, path, 0);
                                    return;
                                }
                            }
                            plugin.getConfig().set(full, nv);
                            save(q);
                            config(q, path, 0);
                        }, q -> config(q, path, 0)), "Wert ändern")), null, full));
            }
        }
        String up = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
        showAll(p, KitData.Icon.COMPARATOR, path.isEmpty() ? "Config" : path, "Config-Editor · Änderungen werden sofort gespeichert",
                rows, List.of(), 0, (pl, n) -> config(pl, path, 0), path.isEmpty() ? this::back : pl -> {
                    fresh(pl);
                    config(pl, up, 0);
                });
    }

    // =================================================================================================================
    // Preisliste
    // =================================================================================================================

    public void worth(Player p, int ignored) {
        List<Row> rows = new ArrayList<>();
        List<Map.Entry<Material, Long>> list = new ArrayList<>(plugin.sell().worth().entrySet());
        list.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Material, Long> e : list) {
            Material m = e.getKey();
            String n = de.kuby.kubyplugins.ui.ItemNames.de(m);
            rows.add(new Row(m, n, GOLD_SOFT, null, Canvas.num(e.getValue()) + " $", GOLD,
                    List.of(new Btn("Preis", true, pl -> ask(pl, "Preis für " + n, "Verkaufspreis je Stück (0 = entfernen)", String.valueOf(e.getValue()), 15, (q, t) -> {
                        long v = num(t, -1);
                        if (v < 0) err(q, "Keine Zahl.");
                        else {
                            plugin.getConfig().set("worth." + m.name(), v <= 0 ? null : v);
                            save(q);
                        }
                        worth(q, 0);
                    }, q -> worth(q, 0)), "Preis ändern")), null, m.name()));
        }
        showAll(p, KitData.Icon.GOLD_INGOT, "Preisliste", rows.size() + " Items mit Verkaufspreis", rows, List.of(
                new Btn("Item aus der Hand", true, pl -> {
                    ItemStack hand = pl.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        err(pl, "Halte das Item in der Haupthand.");
                        worth(pl, 0);
                        return;
                    }
                    Material m = hand.getType();
                    ask(pl, "Preis für " + de.kuby.kubyplugins.ui.ItemNames.de(m), "Verkaufspreis je Stück", "", 15, (q, t) -> {
                        long v = num(t, -1);
                        if (v <= 0) err(q, "Keine gültige Zahl.");
                        else {
                            plugin.getConfig().set("worth." + m.name(), v);
                            save(q);
                        }
                        worth(q, 0);
                    }, q -> worth(q, 0));
                }, "Neues Item mit Preis")), 0, (pl, n) -> worth(pl, 0), pl -> settings(pl, -1));
    }

    // =================================================================================================================
    // Shop
    // =================================================================================================================

    public void shop(Player p) {
        List<Row> rows = new ArrayList<>();
        for (String sec : ShopService.SECTIONS) {
            int n = plugin.shop().items(sec).size();
            rows.add(new Row(Material.CHEST, ShopService.TITLES.get(sec), GOLD_SOFT, null, n + " Items", GRAY,
                    List.of(new Btn("Öffnen", false, pl -> {
                        fresh(pl);
                        shopSection(pl, sec);
                    }, "Items bearbeiten")), pl -> {
                fresh(pl);
                shopSection(pl, sec);
            }, null));
        }
        rows.add(numberRow(p, Material.PAPER, "Wochenende: Angebote", "Pro Spieler", "weekend.count", 1, 10, this::shop));
        rows.add(numberRow(p, Material.ENDER_EYE, "Neuwurf-Kosten", "Juwelen", "weekend.reroll-jewels", 0, 1_000_000, this::shop));
        rows.add(numberRow(p, Material.BARRIER, "Max. Neuwürfe", "Pro Spieler und Wochenende", "weekend.max-rerolls", 0, 100, this::shop));
        rows.add(numberRow(p, Material.CLOCK, "Daily Shop: Angebote", "Pro Tag, für alle gleich", "daily-shop.count", 1, 12, this::shop));
        showAll(p, KitData.Icon.EMERALD, "Shop", "Sektionen und Einstellungen", rows, List.of(), 0, (pl, n) -> shop(pl), this::back);
    }

    private Row numberRow(Player p, Material icon, String label, String hint, String path, long min, long max, Consumer<Player> reopen) {
        long cur = plugin.getConfig().getLong(path);
        return new Row(icon, label, GOLD_SOFT, hint, Canvas.num(cur), WHITE, List.of(new Btn("Ändern", true, pl -> ask(pl, label,
                hint + " (" + min + " bis " + Canvas.num(max) + ")", String.valueOf(cur), 15, (q, t) -> {
                    long v = num(t, Long.MIN_VALUE);
                    if (v == Long.MIN_VALUE) err(q, "Keine Zahl.");
                    else {
                        plugin.getConfig().set(path, Math.max(min, Math.min(max, v)));
                        save(q);
                    }
                    reopen.accept(q);
                }, reopen), "Wert ändern")), null, path);
    }

    public void shopSection(Player p, String sec) {
        List<Row> rows = new ArrayList<>();
        for (ShopService.Item it : plugin.shop().items(sec)) {
            String n = it.name() != null ? plain(it.name()) : de.kuby.kubyplugins.ui.ItemNames.de(it.material());
            rows.add(new Row(it.material(), n + " ×" + it.amount(), GOLD_SOFT, null, Canvas.num(it.price()) + " $", GOLD,
                    List.of(new Btn("Bearbeiten", false, pl -> shopItem(pl, it.id()), "Preis, Menge, Name …")), pl -> shopItem(pl, it.id()), null));
        }
        showAll(p, KitData.Icon.EMERALD, ShopService.TITLES.get(sec), rows.size() + " Items · Shop-Editor", rows, List.of(
                new Btn("Item aus der Hand", true, pl -> {
                    ItemStack hand = pl.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) err(pl, "Halte das Item in der Haupthand.");
                    else {
                        int amount = Math.min(hand.getAmount(), hand.getMaxStackSize());
                        plugin.shop().add(sec, new ShopService.Item(ShopService.newId(), hand.getType(), amount, 100, null, List.of()));
                        ok(pl, "Item hinzugefügt (100 $). Preis anpassen.");
                    }
                    shopSection(pl, sec);
                }, "Neues Item, Startpreis 100 $")), 0, (pl, n) -> shopSection(pl, sec), pl -> {
                    fresh(pl);
                    shop(pl);
                });
    }

    public void shopItem(Player p, String id) {
        ShopService.Item it = plugin.shop().byId(id);
        if (it == null) {
            err(p, "Dieses Item gibt es nicht mehr.");
            shop(p);
            return;
        }
        String sec = plugin.shop().sectionOf(id);
        Consumer<Player> again = pl -> shopItem(pl, id);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Material.GOLD_INGOT, "Preis", GOLD_SOFT, null, Canvas.num(it.price()) + " $", GOLD, List.of(
                new Btn("−100", false, pl -> put(pl, sec, withPrice(it, it.price() - 100), again), "−100 $"),
                new Btn("−10", false, pl -> put(pl, sec, withPrice(it, it.price() - 10), again), "−10 $"),
                new Btn("+10", false, pl -> put(pl, sec, withPrice(it, it.price() + 10), again), "+10 $"),
                new Btn("+100", false, pl -> put(pl, sec, withPrice(it, it.price() + 100), again), "+100 $"),
                new Btn("Wert", true, pl -> ask(pl, "Preis", "Neuer Preis in $", String.valueOf(it.price()), 15, (q, t) -> {
                    long v = num(t, -1);
                    if (v < 0) err(q, "Keine Zahl.");
                    else put(q, sec, withPrice(it, v), again);
                    if (v < 0) again.accept(q);
                }, again), "Preis eingeben")), null, null));
        rows.add(new Row(Material.PAPER, "Menge", GOLD_SOFT, null, String.valueOf(it.amount()), WHITE, List.of(
                new Btn("−1", false, pl -> put(pl, sec, withAmount(it, it.amount() - 1), again), "Eins weniger"),
                new Btn("+1", false, pl -> put(pl, sec, withAmount(it, it.amount() + 1), again), "Eins mehr"),
                new Btn("Wert", true, pl -> ask(pl, "Menge", "Stück je Kauf", String.valueOf(it.amount()), 4, (q, t) -> {
                    long v = num(t, -1);
                    if (v < 1) {
                        err(q, "Keine gültige Menge.");
                        again.accept(q);
                    } else put(q, sec, withAmount(it, (int) v), again);
                }, again), "Menge eingeben")), null, null));
        rows.add(new Row(Material.NAME_TAG, "Name", GOLD_SOFT, it.name() == null ? "Standard-Name" : it.name(), null, null, List.of(
                new Btn("Ändern", true, pl -> ask(pl, "Name", "MiniMessage erlaubt, „-“ = Standard", it.name() == null ? "" : it.name(), 100, (q, t) ->
                        put(q, sec, new ShopService.Item(it.id(), it.material(), it.amount(), it.price(), t == null || t.isBlank() || t.equals("-") ? null : t, it.lore()), again),
                        again), "Name ändern")), null, null));
        rows.add(new Row(Material.WRITABLE_BOOK, "Beschreibung", GOLD_SOFT, it.lore().isEmpty() ? "keine" : String.join(" | ", it.lore()), null, null, List.of(
                new Btn("Ändern", true, pl -> ask(pl, "Beschreibung", "Zeilen mit | trennen, „-“ = keine", String.join(" | ", it.lore()), 500, (q, t) -> {
                    List<String> lore = new ArrayList<>();
                    if (t != null && !t.isBlank() && !t.equals("-")) for (String s : t.split("\\|")) lore.add(s.trim());
                    put(q, sec, new ShopService.Item(it.id(), it.material(), it.amount(), it.price(), it.name(), lore), again);
                }, again), "Beschreibung ändern")), null, null));
        rows.add(new Row(it.material(), "Icon", GOLD_SOFT, de.kuby.kubyplugins.ui.ItemNames.de(it.material()), null, null, List.of(
                new Btn("Aus der Hand", true, pl -> {
                    ItemStack hand = pl.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        err(pl, "Halte ein Item in der Haupthand.");
                        again.accept(pl);
                    } else put(pl, sec, new ShopService.Item(it.id(), hand.getType(), Math.min(it.amount(), hand.getMaxStackSize()), it.price(), it.name(), it.lore()), again);
                }, "Icon durch das Item in der Hand ersetzen")), null, null));
        rows.add(new Row(Material.BARRIER, "Löschen", RED, "Item aus dem Shop entfernen", null, null, List.of(
                new Btn("Löschen", false, pl -> confirm(pl, "Shop-Item löschen", "Das Item verschwindet aus „" + ShopService.TITLES.get(sec) + "“.", "Löschen", q -> {
                    plugin.shop().remove(id);
                    ok(q, "Item gelöscht.");
                    shopSection(q, sec);
                }, again), "Löschen")), null, null));
        String n = it.name() != null ? plain(it.name()) : de.kuby.kubyplugins.ui.ItemNames.de(it.material());
        show(p, new Page(KitData.Icon.EMERALD, n, ShopService.TITLES.get(sec) + " · ID " + it.id(), List.of(), rows, 0, 1, null, null,
                pl -> shopSection(pl, sec), null));
    }

    private static ShopService.Item withPrice(ShopService.Item it, long price) {
        return new ShopService.Item(it.id(), it.material(), it.amount(), Math.max(0, price), it.name(), it.lore());
    }

    private static ShopService.Item withAmount(ShopService.Item it, int amount) {
        int max = it.material().getMaxStackSize();
        return new ShopService.Item(it.id(), it.material(), Math.max(1, Math.min(max, amount)), it.price(), it.name(), it.lore());
    }

    private void put(Player p, String sec, ShopService.Item n, Consumer<Player> again) {
        if (sec != null) plugin.shop().replace(sec, n);
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        again.accept(p);
    }

    // =================================================================================================================
    // Battlepass
    // =================================================================================================================

    public void battlepass(Player p) {
        List<BattlepassService.Reward> all = plugin.battlepass().rewards();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            BattlepassService.Reward r = all.get(i);
            int idx = i;
            boolean prem = BattlepassService.PREMIUM.equals(r.track());
            rows.add(new Row(BattlepassService.iconOf(r), "Stufe " + r.level() + " · " + (prem ? "Premium" : "Free"), prem ? TextColor.color(0xD08CFF) : GREEN,
                    r.type() + " " + r.value(), null, null, List.of(new Btn("Entfernen", false, pl -> confirm(pl, "Belohnung entfernen",
                    "Stufe " + r.level() + " (" + r.track() + "): " + r.type() + " " + r.value(), "Entfernen", q -> {
                        List<BattlepassService.Reward> l = new ArrayList<>(plugin.battlepass().rewards());
                        if (idx < l.size()) l.remove(idx);
                        storeRewards(l);
                        ok(q, "Belohnung entfernt.");
                        battlepass(q);
                    }, q -> battlepass(q)), "Diese Belohnung entfernen")), null, null));
        }
        showAll(p, KitData.Icon.BOOK, "Battlepass", all.size() + " Belohnungen · Format beim Hinzufügen: STUFE SPUR ART WERT", rows, List.of(
                new Btn("Belohnung hinzufügen", true, pl -> ask(pl, "Belohnung hinzufügen",
                        "STUFE SPUR ART WERT, z. B. 30 PREMIUM ITEM NETHERITE_INGOT:2 (Art: COINS XP JEWELS COMMAND ITEM)", "", 200, (q, t) -> {
                            if (addReward(t)) ok(q, "Belohnung hinzugefügt.");
                            else err(q, "Format passt nicht, z. B.: 30 PREMIUM ITEM NETHERITE_INGOT:2");
                            battlepass(q);
                        }, q -> battlepass(q)), "Neue Belohnung")), 0, (pl, n) -> battlepass(pl), this::back);
    }

    private boolean addReward(String text) {
        if (text == null) return false;
        String[] parts = text.trim().split(" +", 4);
        if (parts.length < 4) return false;
        try {
            int level = Integer.parseInt(parts[0]);
            String track = parts[1].toUpperCase(Locale.ROOT), type = parts[2].toUpperCase(Locale.ROOT);
            if (level < 1 || level > BattlepassService.MAX_LEVEL) return false;
            if (!track.equals(BattlepassService.FREE) && !track.equals(BattlepassService.PREMIUM)) return false;
            if (!List.of("COINS", "XP", "JEWELS", "COMMAND", "ITEM").contains(type)) return false;
            List<BattlepassService.Reward> all = new ArrayList<>(plugin.battlepass().rewards());
            all.add(new BattlepassService.Reward(level, track, type, parts[3].trim()));
            storeRewards(all);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void storeRewards(List<BattlepassService.Reward> all) {
        List<String> lines = new ArrayList<>();
        for (BattlepassService.Reward r : all) lines.add(r.line());
        plugin.getConfig().set("battlepass.rewards", lines);
        plugin.saveConfig();
    }

    // =================================================================================================================
    // Kits (Liste als Dialog; der Inhalt bleibt eine Truhe, weil man echte Items hineinlegt)
    // =================================================================================================================

    public void kits(Player p) {
        List<Row> rows = new ArrayList<>();
        for (de.kuby.kubyplugins.service.KitService.Kit kit : plugin.kits().kits()) {
            rows.add(new Row(kit.icon(), kit.name(), GOLD_SOFT, (kit.rank().isEmpty() ? "für alle" : "ab " + de.kuby.kubyplugins.service.KitService.rankName(kit.rank()))
                    + " · alle " + de.kuby.kubyplugins.gui.KitEditMenu.minutes(kit.cooldownMinutes()) + " · " + de.kuby.kubyplugins.gui.KitEditMenu.modesText(kit.modes()),
                    null, null, List.of(new Btn("Bearbeiten", true, pl -> new de.kuby.kubyplugins.gui.KitEditMenu(plugin, kit.id()).open(pl),
                    "Inhalt (Items hineinlegen), Name, Icon, Rang, Abklingzeit, Modi")), null, null));
        }
        showAll(p, KitData.Icon.IRON_CHESTPLATE, "Kits", rows.size() + " Kits · der Inhalt wird in einer Truhe bearbeitet (echte Items)", rows, List.of(
                new Btn("Neues Kit", true, pl -> new de.kuby.kubyplugins.gui.KitEditMenu(plugin, plugin.kits().create().id()).open(pl), "Leeres Kit anlegen")),
                0, (pl, n) -> kits(pl), this::back);
    }

    // =================================================================================================================
    // Markt und Teams
    // =================================================================================================================

    public void market(Player p) {
        List<Row> rows = new ArrayList<>();
        for (MarketService.Listing l : plugin.market().all()) {
            ItemStack it = l.item();
            String n = it.hasItemMeta() && it.getItemMeta().hasDisplayName() ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName())
                    : de.kuby.kubyplugins.ui.ItemNames.de(it.getType());
            String mode = plugin.market().modeOf(l.id());
            rows.add(new Row(it.getType(), n + " ×" + it.getAmount(), GOLD_SOFT, "von " + l.sellerName() + " · " + de.kuby.kubyplugins.service.ModeService.label(mode),
                    Canvas.num(l.price()) + " $", GOLD, List.of(new Btn("Entfernen", false, pl -> confirm(pl, "Angebot entfernen",
                    n + " von " + l.sellerName() + ": das Item geht in die Abholbox des Verkäufers.", "Entfernen", q -> {
                        MarketService.Listing removed = plugin.market().remove(l.id());
                        if (removed != null) {
                            plugin.modes().abholboxIn(removed.seller(), mode, removed.item());
                            ok(q, "Angebot entfernt, Item in der Abholbox des Verkäufers.");
                        } else err(q, "Das Angebot gibt es nicht mehr.");
                        market(q);
                    }, q -> market(q)), "Angebot entfernen")), null, null));
        }
        showAll(p, KitData.Icon.CHEST, "Auktionshaus", rows.size() + " Angebote", rows, List.of(), 0, (pl, n) -> market(pl), this::back);
    }

    public void teams(Player p) {
        List<Row> rows = new ArrayList<>();
        for (TeamService.Team t : plugin.teams().all()) {
            rows.add(new Row(Material.WHITE_BANNER, t.name(), GOLD_SOFT, "Chef " + name(t.leader()) + " · " + t.members().size() + " Mitglieder",
                    Canvas.num(t.bank()) + " $", GOLD, List.of(new Btn("Auflösen", false, pl -> confirm(pl, "Team auflösen",
                    "Team " + t.name() + " wird aufgelöst.", "Auflösen", q -> {
                        String e = plugin.teams().adminDisband(t);
                        if (e != null) err(q, e);
                        else ok(q, "Team " + t.name() + " aufgelöst.");
                        teams(q);
                    }, q -> teams(q)), "Team auflösen")), null, null));
        }
        showAll(p, KitData.Icon.WHITE_BANNER, "Teams", rows.size() + " Teams", rows, List.of(), 0, (pl, n) -> teams(pl), this::back);
    }
}
