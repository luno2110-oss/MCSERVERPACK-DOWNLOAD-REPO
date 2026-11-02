package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TradingService;
import de.kuby.kubyplugins.service.TradingService.Candle;
import de.kuby.kubyplugins.service.TradingService.Frame;
import de.kuby.kubyplugins.service.TradingService.Order;
import de.kuby.kubyplugins.service.TradingService.OrderType;
import de.kuby.kubyplugins.service.TradingService.Position;
import de.kuby.kubyplugins.service.TradingService.Ticker;
import de.kuby.kubyplugins.service.TradingService.Trade;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Börse als Trading-App „KubyTrade“ im dunklen Terminal-Look. Reiter: Markt (Ticker-Leiste, Kurs, Kerzen-Chart in
 * 5 Minuten, 1 Stunde oder 1 Tag, Position, Kaufen und Verkaufen, Limit, Stop-Loss, Take-Profit, offene Orders),
 * Kurse (alle Werte nach Veränderung), Depot (Positionen mit Gewinn und Verlust), Orders und Verlauf. Daten:
 * TradingService. Ohne Pack: altes Truhen-Menü.
 */
public final class TradingScreen {

    static final int W = 390;
    static final TextColor TEXT = TextColor.color(0xE8EEF6), DIM = TextColor.color(0x7A8AA0), UP = TextColor.color(0x3AD86A),
            DOWN = TextColor.color(0xF2545B), CYAN = TextColor.color(0x64D8FF), GOLD = TextColor.color(0xF8D35A),
            GRID = TextColor.color(0x2A3850), WICK = TextColor.color(0x9AA8BC);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    /** Was der Spieler gerade sieht. */
    static final class State {
        String tab = "markt", ticker;
        Frame frame = Frame.H1;
        int page, listPage;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public enum Tab {
        MARKT("markt", "Markt"), KURSE("kurse", "Kurse"), DEPOT("depot", "Depot"), ORDERS("orders", "Orders"), VERLAUF("verlauf", "Verlauf");

        final String key, label;

        Tab(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    // ---- Daten für die Darstellung (auch für Vorschau und Selbsttest) ----

    public record Chip(String symbol, double price, double change, boolean selected, Consumer<Player> click) {}

    public record Bar(double o, double h, double l, double c) {}

    public record Head(long cash, long depot, long pnl, double pnlPct) {}

    /** Markt-Ansicht des gewählten Werts. */
    public record Market(Head head, List<Chip> chips, boolean prev, boolean next, Consumer<Player> prevPage, Consumer<Player> nextPage,
                         KitData.Icon icon, String symbol, String name, double price, double change, double hi, double lo, Frame frame,
                         List<Bar> bars, String position, long posPnl, List<String[]> orders, List<Consumer<Player>> cancels,
                         Map<String, Consumer<Player>> actions, Map<Frame, Consumer<Player>> frames, double fee, List<NewsLine> news) {
        public Market(Head head, List<Chip> chips, boolean prev, boolean next, Consumer<Player> prevPage, Consumer<Player> nextPage,
                      KitData.Icon icon, String symbol, String name, double price, double change, double hi, double lo, Frame frame,
                      List<Bar> bars, String position, long posPnl, List<String[]> orders, List<Consumer<Player>> cancels,
                      Map<String, Consumer<Player>> actions, Map<Frame, Consumer<Player>> frames, double fee) {
            this(head, chips, prev, next, prevPage, nextPage, icon, symbol, name, price, change, hi, lo, frame, bars, position, posPnl, orders,
                    cancels, actions, frames, fee, List.of());
        }
    }

    /** Nachricht im Markt: Uhrzeit, Symbol (leer = ganzer Markt), Text, Veränderung in Prozent. */
    public record NewsLine(String time, String symbol, String text, double pct) {}

    public record Row(String symbol, String name, double price, double change, String extra, long pnl, Consumer<Player> click) {}

    private final KubyPlugins plugin;

    public TradingScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private State state(Player p) {
        return STATES.computeIfAbsent(p.getUniqueId(), k -> new State());
    }

    private Consumer<Player> go(Consumer<Player> c) {
        return pl -> {
            c.accept(pl);
            open(pl);
        };
    }

    private void feedback(Player p, String err, String ok) {
        Sfx.play(plugin, p, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
        Msg.send(p, err == null ? "<green>" + ok : "<red>" + err);
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    public void open(Player p) {
        TradingService ts = plugin.trading();
        ts.ensureTickers();
        List<Ticker> all = ts.tickers();
        if (all.isEmpty()) {
            Msg.send(p, "<red>Die Börse hat noch keine Werte (Shop-Bereiche sind leer).");
            return;
        }
        State st = state(p);
        if (st.ticker == null || ts.ticker(st.ticker) == null) st.ticker = all.get(0).id;
        Head head = head(p);
        Canvas c = switch (st.tab) {
            case "kurse" -> list(head, Tab.KURSE, "Alle Werte · nach Veränderung (24 h)", rowsKurse(p, all), st.listPage, tabs(p),
                    page(p, -1), page(p, 1));
            case "depot" -> list(head, Tab.DEPOT, "Deine Positionen", rowsDepot(p), 0, tabs(p), null, null);
            case "orders" -> list(head, Tab.ORDERS, "Offene Orders · werden bei jedem Kurs-Schritt geprüft", rowsOrders(p), 0, tabs(p), null, null);
            case "verlauf" -> list(head, Tab.VERLAUF, "Deine letzten Trades", rowsHistory(p), 0, tabs(p), null, null);
            default -> market(market(p, all, head), tabs(p));
        };
        UiDialog.show(p, c, 330);
    }

    private Consumer<Player> page(Player p, int d) {
        return go(pl -> state(pl).listPage = Math.max(0, state(pl).listPage + d));
    }

    private Map<Tab, Consumer<Player>> tabs(Player p) {
        Map<Tab, Consumer<Player>> m = new java.util.EnumMap<>(Tab.class);
        for (Tab t : Tab.values()) m.put(t, go(pl -> state(pl).tab = t.key));
        return m;
    }

    private Head head(Player p) {
        TradingService ts = plugin.trading();
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        double value = 0, cost = 0;
        for (Map.Entry<String, Position> e : ts.positions(id, mode).entrySet()) {
            Ticker t = ts.ticker(e.getKey());
            if (t == null) continue;
            value += e.getValue().qty * t.price;
            cost += e.getValue().qty * e.getValue().avg;
        }
        return new Head(plugin.economy().balance(id), Math.round(value), Math.round(value - cost), cost <= 0 ? 0 : (value - cost) / cost * 100);
    }

    private Market market(Player p, List<Ticker> all, Head head) {
        TradingService ts = plugin.trading();
        State st = state(p);
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        Ticker t = ts.ticker(st.ticker);
        int per = 5, pages = (all.size() + per - 1) / per;
        int sel = all.indexOf(t);
        if (st.page * per > sel || sel >= (st.page + 1) * per) st.page = Math.max(0, sel / per);
        List<Chip> chips = new ArrayList<>();
        for (int i = st.page * per; i < Math.min(all.size(), (st.page + 1) * per); i++) {
            Ticker k = all.get(i);
            chips.add(new Chip(k.symbol, k.price, ts.change24(k), k == t, go(pl -> state(pl).ticker = k.id)));
        }
        List<Bar> bars = new ArrayList<>();
        for (Candle c : t.candles(st.frame)) bars.add(new Bar(c.o, c.h, c.l, c.c));
        Position pos = ts.positions(id, mode).get(t.id);
        String posText = null;
        long posPnl = 0;
        if (pos != null && pos.qty > 1e-9) {
            double value = pos.qty * t.price;
            posPnl = Math.round(value - pos.qty * pos.avg);
            double pct = pos.avg <= 0 ? 0 : (t.price - pos.avg) / pos.avg * 100;
            posText = TradingService.fmt(pos.qty) + " " + t.symbol + " · Ø " + TradingService.money(pos.avg) + " · Wert " + Msg.coins(Math.round(value))
                    + " $ · " + (posPnl >= 0 ? "+" : "") + Msg.coins(posPnl) + " $ (" + String.format(Locale.GERMANY, "%+.1f", pct) + " %)";
        }
        List<String[]> ord = new ArrayList<>();
        List<Consumer<Player>> cancels = new ArrayList<>();
        for (Order o : ts.orders(id, mode)) {
            if (!o.ticker().equals(t.id)) continue;
            ord.add(new String[]{o.type().label, TradingService.money(o.trigger()),
                    o.type() == OrderType.LIMIT_BUY ? Msg.coins(o.coins()) + " $" : TradingService.fmt(o.qty()) + " " + t.symbol});
            cancels.add(go(pl -> feedback(pl, ts.cancel(pl, o.id()), "Order storniert.")));
        }
        Map<String, Consumer<Player>> act = new java.util.LinkedHashMap<>();
        for (long amount : new long[]{100, 1_000, 10_000}) {
            act.put("buy" + amount, go(pl -> feedback(pl, ts.buy(pl, t.id, amount), "Gekauft: " + Msg.coins(amount) + " $ in " + t.symbol)));
        }
        act.put("buyX", pl -> ask(pl, "Kaufen: " + t.symbol, "Betrag in $", 1, v -> {
            feedback(pl, ts.buy(pl, t.id, Math.round(v)), "Gekauft: " + Msg.coins(Math.round(v)) + " $ in " + t.symbol);
            open(pl);
        }));
        for (int pct : new int[]{25, 50, 100}) {
            act.put("sell" + pct, go(pl -> feedback(pl, ts.sell(pl, t.id, pct / 100.0), (pct == 100 ? "Alles" : pct + " %") + " von " + t.symbol
                    + " verkauft.")));
        }
        act.put("limitBuy", pl -> ask(pl, "Limit-Kauf " + t.symbol, "Kaufen, wenn der Kurs fällt auf (aktuell " + TradingService.money(t.price) + ")",
                t.price * 0.97, trig -> ask(pl, "Limit-Kauf " + t.symbol, "Betrag in $ (wird reserviert)", 1000, coins -> {
                    feedback(pl, ts.place(pl, t.id, OrderType.LIMIT_BUY, trig, Math.round(coins), 1), "Limit-Kauf angelegt.");
                    open(pl);
                })));
        act.put("stop", pl -> ask(pl, "Stop-Loss " + t.symbol, "Alles verkaufen, wenn der Kurs fällt auf (aktuell " + TradingService.money(t.price) + ")",
                t.price * 0.95, trig -> {
                    feedback(pl, ts.place(pl, t.id, OrderType.STOP_LOSS, trig, 0, 1), "Stop-Loss gesetzt.");
                    open(pl);
                }));
        act.put("tp", pl -> ask(pl, "Take-Profit " + t.symbol, "Alles verkaufen, wenn der Kurs steigt auf (aktuell " + TradingService.money(t.price) + ")",
                t.price * 1.05, trig -> {
                    feedback(pl, ts.place(pl, t.id, OrderType.TAKE_PROFIT, trig, 0, 1), "Take-Profit gesetzt.");
                    open(pl);
                }));
        act.put("limitSell", pl -> ask(pl, "Limit-Verkauf " + t.symbol, "Hälfte verkaufen bei (aktuell " + TradingService.money(t.price) + ")",
                t.price * 1.03, trig -> {
                    feedback(pl, ts.place(pl, t.id, OrderType.LIMIT_SELL, trig, 0, 0.5), "Limit-Verkauf angelegt.");
                    open(pl);
                }));
        act.put("refresh", go(pl -> { }));
        Map<Frame, Consumer<Player>> frames = new java.util.EnumMap<>(Frame.class);
        for (Frame f : Frame.values()) frames.put(f, go(pl -> state(pl).frame = f));
        double[] range = ts.range24(t);
        return new Market(head, chips, st.page > 0, st.page + 1 < pages, go(pl -> state(pl).page = Math.max(0, state(pl).page - 1)),
                go(pl -> state(pl).page++), Icons.of(t.material, KitData.Icon.PAPER), t.symbol, t.name, t.price, ts.change24(t), range[0], range[1],
                st.frame, bars, posText, posPnl, ord, cancels, act, frames, ts.feePercent(), newsLines(ts.news(t.symbol, 3)));
    }

    private static List<NewsLine> newsLines(List<TradingService.News> ns) {
        List<NewsLine> out = new ArrayList<>();
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        for (TradingService.News n : ns) {
            String time = java.time.Instant.ofEpochMilli(n.t()).atZone(java.time.ZoneId.systemDefault()).format(hm);
            out.add(new NewsLine(time, n.symbol(), n.text(), n.pct()));
        }
        return out;
    }

    private List<Row> rowsKurse(Player p, List<Ticker> all) {
        TradingService ts = plugin.trading();
        List<Ticker> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble((Ticker k) -> -ts.change24(k)));
        List<Row> rows = new ArrayList<>();
        for (Ticker k : sorted) rows.add(new Row(k.symbol, k.name, k.price, ts.change24(k), null, 0, go(pl -> {
            state(pl).ticker = k.id;
            state(pl).tab = "markt";
        })));
        return rows;
    }

    private List<Row> rowsDepot(Player p) {
        TradingService ts = plugin.trading();
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Position> e : ts.positions(p.getUniqueId(), mode).entrySet()) {
            Ticker k = ts.ticker(e.getKey());
            if (k == null) continue;
            Position pos = e.getValue();
            long value = Math.round(pos.qty * k.price);
            long pnl = Math.round(pos.qty * (k.price - pos.avg));
            rows.add(new Row(k.symbol, TradingService.fmt(pos.qty) + " · Ø " + TradingService.money(pos.avg), k.price, ts.change24(k),
                    Msg.coins(value) + " $", pnl, go(pl -> {
                        state(pl).ticker = k.id;
                        state(pl).tab = "markt";
                    })));
        }
        return rows;
    }

    private List<Row> rowsOrders(Player p) {
        TradingService ts = plugin.trading();
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<Row> rows = new ArrayList<>();
        for (Order o : ts.orders(p.getUniqueId(), mode)) {
            Ticker k = ts.ticker(o.ticker());
            String sym = k == null ? o.ticker() : k.symbol;
            rows.add(new Row(sym, o.type().label + " bei " + TradingService.money(o.trigger()), k == null ? 0 : k.price, 0,
                    o.type() == OrderType.LIMIT_BUY ? Msg.coins(o.coins()) + " $" : TradingService.fmt(o.qty()) + " St.", Long.MIN_VALUE,
                    go(pl -> feedback(pl, ts.cancel(pl, o.id()), "Order storniert."))));
        }
        return rows;
    }

    private List<Row> rowsHistory(Player p) {
        TradingService ts = plugin.trading();
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<Row> rows = new ArrayList<>();
        for (Trade tr : ts.history(p.getUniqueId(), mode)) {
            rows.add(new Row(tr.ticker(), tr.side() + " · " + TIME.format(Instant.ofEpochMilli(tr.t())) + " · " + TradingService.fmt(tr.qty()) + " St.",
                    tr.price(), 0, Msg.coins(tr.coins()) + " $", tr.side().equals("Kauf") ? Long.MIN_VALUE : tr.pnl(), null));
        }
        return rows;
    }

    /** Zahl eingeben (Kurs oder Betrag, mit Komma). */
    private void ask(Player p, String title, String text, double initial, DoubleConsumer then) {
        DialogBase base = DialogBase.builder(Msg.parse("<#64D8FF><b>" + Msg.escape(title) + "</b>"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>" + Msg.escape(text)), 260)))
                .inputs(List.of(DialogInput.text("wert", Msg.parse("<gray>Wert")).width(160).maxLength(16)
                        .initial(String.format(Locale.GERMANY, initial >= 1000 ? "%.0f" : "%.2f", initial)).build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>OK</b>"), null, 100, DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String raw = response.getText("wert");
            double v;
            try {
                String s = raw == null ? "" : raw.trim().replace(" ", "").replace("$", "");
                if (s.endsWith("k")) v = Double.parseDouble(s.substring(0, s.length() - 1).replace(".", "").replace(',', '.')) * 1000;
                else v = Double.parseDouble(s.replace(".", s.contains(",") ? "" : ".").replace(',', '.'));
            } catch (NumberFormatException e) {
                feedback(pl, "Bitte eine Zahl eingeben.", "");
                open(pl);
                return;
            }
            if (v <= 0) {
                feedback(pl, "Bitte eine Zahl über 0 eingeben.", "");
                open(pl);
                return;
            }
            then.accept(v);
        }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) open(pl);
        }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(ok, no))));
    }

    // =================================================================================================================
    // Darstellung
    // =================================================================================================================

    static TextColor sign(double v) {
        return v > 0 ? UP : v < 0 ? DOWN : DIM;
    }

    static String pct(double v) {
        return String.format(Locale.GERMANY, "%+.2f %%", v);
    }

    /** Kopf mit Logo, Guthaben, Depot und Gewinn, darunter die Reiter. */
    private static int top(Canvas c, Head h, Tab active, Map<Tab, Consumer<Player>> tabs) {
        c.box(0, 0, W, 46, KitData.Box.TERMINAL);
        c.sprite(8 + (32 - KitData.Sprite.HB_TRADE.width) / 2, 7 + (32 - KitData.Sprite.HB_TRADE.height) / 2, KitData.Sprite.HB_TRADE);
        c.title(46, 7, "KubyTrade", false, CYAN);
        c.text(46, 31, "● Live", UP, true);
        c.text(46 + Canvas.width("● Live", true) + 4, 31, "Kurse bewegen sich", DIM, false);
        c.textRight(W - 8, 7, Msg.coins(h.cash()) + " $", GOLD, true);
        c.textRight(W - 8, 18, "Depot " + Msg.coins(h.depot()) + " $", TEXT, false);
        c.textRight(W - 8, 29, (h.pnl() >= 0 ? "+" : "") + Msg.coins(h.pnl()) + " $ (" + String.format(Locale.GERMANY, "%+.1f", h.pnlPct()) + " %)",
                sign(h.pnl()), true);
        int x = 0, y = 50, tw = (W - 4 * 3) / 5;
        for (Tab t : Tab.values()) {
            boolean on = t == active;
            c.smallButton(x, y, tw, on ? KitData.Small.GOLD : KitData.Small.DARK, t.label, on ? GOLD : DIM, on);
            if (!on && tabs != null && tabs.get(t) != null) c.action(x, y, tw, KitData.SMALL_H, tabs.get(t), t.label);
            x += tw + 3;
        }
        return y + KitData.SMALL_H + 6;
    }

    public static Canvas market(Market m, Map<Tab, Consumer<Player>> tabs) {
        Canvas c = new Canvas(W).heading("KubyTrade", CYAN);
        int y = top(c, m.head(), Tab.MARKT, tabs);

        // Ticker-Leiste
        int cw = 68, x = 0;
        c.smallButton(0, y + 4, 16, KitData.Small.DARK, "‹", m.prev() ? TEXT : GRID, true);
        if (m.prev()) c.action(0, y + 4, 16, KitData.SMALL_H, m.prevPage(), "Vorige Werte");
        x = 19;
        for (Chip ch : m.chips()) {
            c.box(x, y, cw, 24, ch.selected() ? KitData.Box.GOLD : KitData.Box.TERMINAL_ROW, Canvas.Z_BUTTON);
            c.text(x + 5, y + 4, ch.symbol(), ch.selected() ? GOLD : TEXT, true);
            c.textRight(x + cw - 4, y + 4, pctShort(ch.change()), sign(ch.change()), false);
            c.text(x + 5, y + 14, TradingService.money(ch.price()), DIM, false);
            if (!ch.selected()) c.action(x, y, cw, 24, ch.click(), ch.symbol());
            x += cw + 3;
        }
        c.smallButton(W - 16, y + 4, 16, KitData.Small.DARK, "›", m.next() ? TEXT : GRID, true);
        if (m.next()) c.action(W - 16, y + 4, 16, KitData.SMALL_H, m.nextPage(), "Weitere Werte");
        y += 28;

        // Kurs
        c.icon(2, y, m.icon(), false);
        int nw = c.text(22, y + 4, m.symbol(), CYAN, true);
        c.text(22 + nw + 6, y + 4, Canvas.fit(m.name(), false, 120), DIM, false);
        c.textRight(W - 2, y, TradingService.money(m.price()), TEXT, true);
        c.textRight(W - 2, y + 10, pct(m.change()) + " · 24h", sign(m.change()), false);
        y += 22;
        c.text(2, y, "Hoch " + TradingService.money(m.hi()) + " · Tief " + TradingService.money(m.lo()), DIM, false);
        int fx = W - 3 * 34;
        for (Frame f : Frame.values()) {
            boolean on = f == m.frame();
            c.smallButton(fx, y - 4, 32, on ? KitData.Small.GOLD : KitData.Small.DARK, f.label, on ? GOLD : DIM, on);
            if (!on && m.frames() != null) c.action(fx, y - 4, 32, KitData.SMALL_H, m.frames().get(f), "Zeitrahmen " + f.label);
            fx += 34;
        }
        y += 16;

        // Kerzen-Chart
        int ch = 112, plotX = 6, plotW = 300;
        c.box(0, y, W, ch, KitData.Box.TERMINAL);
        int top = y + 6, bottom = y + ch - 8, plotH = bottom - top;
        List<Bar> bars = m.bars();
        int n = Math.min(bars.size(), plotW / 5);
        List<Bar> vis = bars.subList(bars.size() - n, bars.size());
        double hi = m.price(), lo = m.price();
        for (Bar b : vis) {
            hi = Math.max(hi, b.h());
            lo = Math.min(lo, b.l());
        }
        double pad = (hi - lo) * 0.06 + 1e-9;
        hi += pad;
        lo -= pad;
        final double H = hi, L = lo;
        java.util.function.DoubleToIntFunction py = v -> top + (int) Math.round((H - v) / (H - L) * plotH);
        for (int g = 0; g <= 3; g++) {
            int gy = top + plotH * g / 3;
            for (int gx = plotX; gx < plotX + plotW; gx += 6) c.line(gx, gy, 3, GRID);
            double v = H - (H - L) * g / 3;
            c.text(plotX + plotW + 6, gy - 4, TradingService.money(v), DIM, false);
        }
        for (int i = 0; i < vis.size(); i++) {
            Bar b = vis.get(i);
            int bx = plotX + i * 5;
            boolean up = b.c() >= b.o();
            TextColor col = up ? UP : DOWN;
            int yh = py.applyAsInt(b.h()), yl = py.applyAsInt(b.l());
            c.vbar(bx + 1, yh, 1, Math.max(1, yl - yh + 1), up ? TextColor.color(0x2AA850) : TextColor.color(0xC03A40));
            int yo = py.applyAsInt(b.o()), yc = py.applyAsInt(b.c());
            c.vbar(bx, Math.min(yo, yc), 3, Math.max(1, Math.abs(yc - yo) + 1), col);
        }
        int cy = py.applyAsInt(m.price());
        for (int gx = plotX; gx < plotX + plotW; gx += 4) c.line(gx, cy, 2, CYAN);
        c.box(plotX + plotW + 2, cy - 6, W - plotX - plotW - 6, 13, KitData.Box.TERMINAL_ROW, Canvas.Z_ICON);
        c.text(plotX + plotW + 6, cy - 3, TradingService.money(m.price()), CYAN, true);
        y += ch + 6;

        // Position
        if (m.position() != null) {
            c.text(2, y, "Position: ", DIM, false);
            c.text(2 + Canvas.width("Position: ", false), y, Canvas.fit(m.position(), false, W - 60), sign(m.posPnl()), false);
        } else {
            c.text(2, y, "Keine Position in " + m.symbol() + " · Gebühr " + String.format(Locale.GERMANY, "%.1f", m.fee()) + " % je Trade", DIM, false);
        }
        y += 14;

        // Kaufen und Verkaufen
        Map<String, Consumer<Player>> a = m.actions();
        String[][] buy = {{"buy100", "100 $"}, {"buy1000", "1.000 $"}, {"buy10000", "10.000 $"}, {"buyX", "Betrag"}};
        String[][] sell = {{"sell25", "25 %"}, {"sell50", "50 %"}, {"sell100", "Alles"}};
        c.text(2, y + 4, "KAUFEN", UP, true);
        int bx = 58, bw = 54;
        for (String[] b : buy) {
            c.smallButton(bx, y, bw, KitData.Small.GREEN, b[1], TEXT, true);
            if (a.get(b[0]) != null) c.action(bx, y, bw, KitData.SMALL_H, a.get(b[0]), "Kaufen: " + b[1]);
            bx += bw + 3;
        }
        y += KitData.SMALL_H + 3;
        c.text(2, y + 4, "VERKAUFEN", DOWN, true);
        bx = 58;
        for (String[] b : sell) {
            c.smallButton(bx, y, bw, KitData.Small.DARK, b[1], DOWN, true);
            if (m.position() != null && a.get(b[0]) != null) c.action(bx, y, bw, KitData.SMALL_H, a.get(b[0]), "Verkaufen: " + b[1]);
            bx += bw + 3;
        }
        c.smallButton(W - 70, y, 70, KitData.Small.DARK, "↻ Neu", CYAN, false);
        if (a.get("refresh") != null) c.action(W - 70, y, 70, KitData.SMALL_H, a.get("refresh"), "Kurse aktualisieren");
        y += KitData.SMALL_H + 5;
        String[][] ord = {{"limitBuy", "Limit-Kauf"}, {"stop", "Stop-Loss"}, {"tp", "Take-Profit"}, {"limitSell", "Limit-Verkauf"}};
        int ow = (W - 9) / 4;
        bx = 0;
        for (String[] o : ord) {
            boolean needsPos = !o[0].equals("limitBuy");
            boolean on = !needsPos || m.position() != null;
            c.smallButton(bx, y, ow, KitData.Small.DARK, o[1], on ? CYAN : GRID, false);
            if (on && a.get(o[0]) != null) c.action(bx, y, ow, KitData.SMALL_H, a.get(o[0]), o[1]);
            bx += ow + 3;
        }
        y += KitData.SMALL_H + 6;

        // offene Orders zu diesem Wert
        for (int i = 0; i < m.orders().size(); i++) {
            String[] o = m.orders().get(i);
            c.box(0, y, W, 18, KitData.Box.TERMINAL_ROW);
            c.text(6, y + 5, o[0], o[0].equals("Stop-Loss") ? DOWN : CYAN, true);
            c.text(110, y + 5, "bei " + o[1], TEXT, false);
            c.text(220, y + 5, o[2], DIM, false);
            c.smallButton(W - 24, y + 1, 20, KitData.Small.DARK, "×", DOWN, true);
            if (m.cancels() != null && i < m.cancels().size()) c.action(W - 24, y + 1, 20, KitData.SMALL_H, m.cancels().get(i), "Order stornieren");
            y += 20;
        }

        // Nachrichten zu diesem Wert und zum ganzen Markt
        if (!m.news().isEmpty()) {
            if (!m.orders().isEmpty()) y += 2;
            c.box(0, y, W, 8 + 11 * m.news().size() + 4, KitData.Box.TERMINAL);
            int ny = y + 6;
            for (NewsLine nl : m.news()) {
                int nx = 6;
                nx += c.text(nx, ny, nl.time(), DIM, false) + 6;
                nx += c.text(nx, ny, nl.symbol().isEmpty() ? "MARKT" : nl.symbol(), CYAN, true) + 6;
                String pct = String.format(Locale.GERMANY, "%+.1f %%", nl.pct());
                nx += c.text(nx, ny, pct, sign(nl.pct()), true) + 6;
                c.text(nx, ny, Canvas.fit(nl.text(), false, W - nx - 6), TEXT, false);
                ny += 11;
            }
        }
        return c;
    }

    private static String pctShort(double v) {
        return String.format(Locale.GERMANY, "%+.1f%%", v);
    }

    /** Listen-Reiter (Kurse, Depot, Orders, Verlauf). */
    public static Canvas list(Head head, Tab tab, String sub, List<Row> rows, int page, Map<Tab, Consumer<Player>> tabs,
                              Consumer<Player> prev, Consumer<Player> next) {
        Canvas c = new Canvas(W).heading("KubyTrade", CYAN);
        int y = top(c, head, tab, tabs);
        c.text(2, y, sub, DIM, false);
        y += 13;
        int per = tab == Tab.KURSE ? 12 : 30;
        int pages = Math.max(1, (rows.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * per; i < Math.min(rows.size(), (page + 1) * per); i++) {
            Row r = rows.get(i);
            c.box(0, y, W, 22, KitData.Box.TERMINAL_ROW);
            c.text(6, y + 7, r.symbol(), CYAN, true);
            c.text(58, y + 7, Canvas.fit(r.name(), false, 150), TEXT, false);
            if (tab == Tab.ORDERS) {
                c.text(214, y + 7, r.extra(), DIM, false);
                c.smallButton(W - 76, y + 3, 70, KitData.Small.DARK, "Stornieren", DOWN, false);
                if (r.click() != null) c.action(W - 76, y + 3, 70, KitData.SMALL_H, r.click(), "Order stornieren");
            } else {
                c.textRight(276, y + 7, TradingService.money(r.price()), TEXT, false);
                if (r.extra() != null) c.textRight(W - 6, y + 2, r.extra(), TEXT, true);
                if (r.pnl() != Long.MIN_VALUE && tab != Tab.KURSE) {
                    c.textRight(W - 6, y + 12, (r.pnl() >= 0 ? "+" : "") + Msg.coins(r.pnl()) + " $", sign(r.pnl()), false);
                } else if (tab == Tab.KURSE) {
                    c.textRight(W - 6, y + 7, pct(r.change()), sign(r.change()), true);
                }
                if (r.click() != null) c.action(0, y, W, 22, r.click(), r.symbol() + " ansehen");
            }
            y += 24;
        }
        if (rows.isEmpty()) {
            c.box(0, y, W, 26, KitData.Box.TERMINAL_ROW);
            c.text(8, y + 9, switch (tab) {
                case DEPOT -> "Noch keine Positionen. Kauf im Reiter Markt deinen ersten Wert.";
                case ORDERS -> "Keine offenen Orders. Limit, Stop-Loss und Take-Profit setzt du im Reiter Markt.";
                case VERLAUF -> "Noch keine Trades.";
                default -> "Keine Werte.";
            }, DIM, false);
            y += 30;
        }
        if (pages > 1) {
            c.smallButton(W / 2 - 80, y + 2, 40, KitData.Small.DARK, "‹", page > 0 ? TEXT : GRID, true);
            if (page > 0 && prev != null) c.action(W / 2 - 80, y + 2, 40, KitData.SMALL_H, prev, "Vorige Seite");
            c.textCenter(W / 2, y + 6, (page + 1) + " / " + pages, DIM, false);
            c.smallButton(W / 2 + 40, y + 2, 40, KitData.Small.DARK, "›", page + 1 < pages ? TEXT : GRID, true);
            if (page + 1 < pages && next != null) c.action(W / 2 + 40, y + 2, 40, KitData.SMALL_H, next, "Nächste Seite");
        }
        return c;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        Head h = new Head(999_999_999L, 999_999_999L, -999_999_999L, -99.9);
        List<Chip> chips = new ArrayList<>();
        for (int i = 0; i < 5; i++) chips.add(new Chip("WWWW", 999_999.99, -99.9, i == 2, no));
        java.util.Random r = new java.util.Random(4);
        List<Bar> bars = new ArrayList<>();
        double p = 100;
        for (int i = 0; i < 96; i++) {
            double o = p, c = p * (1 + r.nextGaussian() * 0.03);
            bars.add(new Bar(o, Math.max(o, c) * 1.01, Math.min(o, c) * 0.99, c));
            p = c;
        }
        final double last = p;
        Map<String, Consumer<Player>> act = new HashMap<>();
        List<String[]> ord = new ArrayList<>();
        List<Consumer<Player>> can = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ord.add(new String[]{"Take-Profit", "999.999 $", "999.999.999 $"});
            can.add(no);
        }
        reg.accept("Börse Markt", () -> market(new Market(h, chips, true, true, no, no, KitData.Icon.DIAMOND, "WWWW", "W".repeat(30), last, 12.3, 999_999,
                0.01, Frame.H1, bars, "W".repeat(80), -5, ord, can, act, null, 0.5,
                List.of(new NewsLine("23:59", "WWWW", "W".repeat(60), -99.9), new NewsLine("00:00", "", "W".repeat(60), 99.9),
                        new NewsLine("12:00", "WWWW", "W".repeat(60), 5.5))), null));
        reg.accept("Börse Markt leer", () -> market(new Market(new Head(0, 0, 0, 0), List.of(), false, false, no, no, KitData.Icon.PAPER, "X", "X",
                1, 0, 1, 1, Frame.M5, List.of(), null, 0, List.of(), List.of(), act, null, 0.5), null));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) rows.add(new Row("WWWW", "W".repeat(40), 999_999.99, -99.9, "999.999.999 $", -999_999_999L, no));
        for (Tab t : Tab.values()) {
            if (t == Tab.MARKT) continue;
            reg.accept("Börse " + t.label, () -> list(h, t, "W".repeat(60), rows, 99, null, no, no));
            reg.accept("Börse " + t.label + " leer", () -> list(h, t, "", List.of(), 0, null, null, null));
        }
    }
}
