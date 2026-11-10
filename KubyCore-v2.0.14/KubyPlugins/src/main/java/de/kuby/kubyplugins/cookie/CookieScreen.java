package de.kuby.kubyplugins.cookie;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cookie.CookieContent.Achievement;
import de.kuby.kubyplugins.cookie.CookieContent.Building;
import de.kuby.kubyplugins.cookie.CookieContent.Node;
import de.kuby.kubyplugins.cookie.CookieContent.Upgrade;
import de.kuby.kubyplugins.cookie.CookieService.Board;
import de.kuby.kubyplugins.cookie.CookieService.Data;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dialoge des Cookie Clickers im Bäckerei-Look (dunkles Holz, goldene Ränder): Übersicht mit Kacheln, Gebäude (x1, x10,
 * x100, Max), Upgrades nach Preis, Erfolge in fünf Bereichen, Aufstieg mit Sternen-Baum, Keks-Laden (Vorteile und
 * Items), Ranglisten, Statistik und Hilfe. Ohne Pack eine Zusammenfassung im Chat.
 */
public final class CookieScreen {

    static final int W = 400;
    static final TextColor GOLD = TextColor.color(0xF2C443), CREAM = TextColor.color(0xF4E6C8), TAN = TextColor.color(0xC8A878),
            DIM = TextColor.color(0x8A7058), GREEN = TextColor.color(0x8AE06A), RED = TextColor.color(0xF27A7A), PINK = TextColor.color(0xF0A8E8),
            CYAN = TextColor.color(0x7AE8FF);

    /** Kaufmenge je Spieler (1, 10, 100, -1 = Max) und Seiten. */
    private static final Map<UUID, Integer> AMOUNT = new HashMap<>();
    private static final Map<UUID, Integer> PAGE = new HashMap<>();

    private CookieScreen() {
    }

    private static KubyPlugins core() {
        return KubyPlugins.get();
    }

    private static CookieService svc() {
        return core().cookies();
    }

    static String n(double v) {
        return CookieService.fmt(v);
    }

    private static Consumer<Player> run(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(core(), p, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    private static boolean ui(Player p) {
        if (core().uiPack().active(p)) return true;
        chat(p);
        return false;
    }

    // =================================================================================================================
    // Kopf
    // =================================================================================================================

    /** Kopf: Keks, Bereich, Titel und Stufe; rechts Kekse, pro Sekunde, je Klick. */
    public record Head(String area, String title, long level, double cookies, double cps, double click, String buff) {}

    static Head head(Data d, String area) {
        CookieService s = svc();
        long now = System.currentTimeMillis();
        String buff = d.prodName != null && now < d.prodUntil ? "⚡ " + d.prodName + " x" + n(d.prodMult) + " · " + (d.prodUntil - now) / 1000 + " s"
                : d.clickName != null && now < d.clickUntil ? "⚡ " + d.clickName + " x" + n(d.clickMult) + " · " + (d.clickUntil - now) / 1000 + " s" : null;
        return new Head(area, s.title(d), Math.max(d.level, CookieService.levelFor(d.allTime)), d.cookies, d.cps(), s.clickValue(d), buff);
    }

    static int head(Canvas c, Head h) {
        c.box(0, 0, W, 52, KitData.Box.WOOD);
        c.sprite(9 + (32 - KitData.Sprite.CK_COOKIE.width) / 2, 10 + (32 - KitData.Sprite.CK_COOKIE.height) / 2, KitData.Sprite.CK_COOKIE);
        c.headline(48, 8, h.area(), GOLD);
        c.text(48, 35, Canvas.fit(h.title() + " · Stufe " + n(h.level()), false, 170), TAN, false);
        c.textRight(W - 10, 8, Canvas.caps("Kekse"), DIM, false);
        c.textRight(W - 10, 19, n(h.cookies()), CREAM, true);
        int w = c.textRight(W - 10, 35, Canvas.fit("+" + n(h.click()) + " je Klick", false, 90), GOLD, false);
        c.textRight(W - 14 - w, 35, Canvas.fit("+" + n(h.cps()) + "/s ·", false, 100), GREEN, false);
        if (h.buff() != null) {
            c.box(0, 56, W, 16, KitData.Box.GOLD);
            c.textCenter(W / 2, 60, h.buff(), GOLD, true);
            return 76;
        }
        return 58;
    }

    private static void back(Canvas c, int y, Consumer<Player> back) {
        c.smallButton(0, y, 120, KitData.Small.DARK, "« Übersicht", TAN, false);
        c.action(0, y, 120, KitData.SMALL_H, back, "Zurück zur Übersicht");
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public record Tile(KitData.Sprite art, String name, String sub, boolean alert, Consumer<Player> open) {}

    public record MainView(Head head, List<Tile> tiles, String hint, Consumer<Player> station) {}

    public static void main(Player p) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        PAGE.remove(p.getUniqueId());
        int ab = s.affordableBuildings(d), au = s.affordableUpgrades(d);
        long stars = s.starsOnAscend(d);
        int rank = s.rank(p.getUniqueId(), Board.ALLTIME);
        List<Tile> tiles = List.of(
                new Tile(KitData.Sprite.CK_M_GEBAEUDE, "Gebäude", ab > 0 ? ab + " kaufbar" : d.totalBuildings() + " gebaut", ab > 0, run(q -> buildings(q, 0))),
                new Tile(KitData.Sprite.CK_M_UPGRADES, "Upgrades", au > 0 ? au + " kaufbar" : d.upgrades.size() + " gekauft", au > 0, run(q -> upgrades(q, 0))),
                new Tile(KitData.Sprite.CK_M_ERFOLGE, "Erfolge", d.achievements.size() + "/" + CookieContent.ACHIEVEMENTS.size(), false, run(q -> achievements(q, 0, 0))),
                new Tile(KitData.Sprite.CK_M_AUFSTIEG, "Aufstieg", stars > 0 ? "+" + n(stars) + " Backsterne" : "Stufe " + n(d.level), d.stars > 0 && s.nodeAffordable(d), run(CookieScreen::prestige)),
                new Tile(KitData.Sprite.CK_M_LADEN, "Keks-Laden", "Items und Vorteile", false, run(q -> shop(q, 0))),
                new Tile(KitData.Sprite.CK_M_RANGLISTE, "Rangliste", rank > 0 ? "Platz " + rank : "noch ohne Platz", false, run(q -> ranking(q, Board.ALLTIME))),
                new Tile(KitData.Sprite.CK_M_STATISTIK, "Statistik", n(d.allTime) + " gebacken", false, run(CookieScreen::stats)),
                new Tile(KitData.Sprite.CK_M_HILFE, "Hilfe", "So geht's", false, run(q -> help(q, 0))));
        Location st = core().cookieStation() == null ? null : core().cookieStation().spot();
        String hint = d.allTime < 1 ? "Klick an der Station mit Linksklick auf den Keks, um zu backen."
                : "Ohne dich backt die Bäckerei mit " + Math.round(s.offlineRate(d) * 100) + " % bis zu " + s.offlineHours(d) + " Stunden.";
        UiDialog.show(p, main(new MainView(head(d, "Cookie Clicker"), tiles, hint, st == null ? null : run(q -> {
            q.teleportAsync(core().cookieStation().spot());
            Msg.send(q, "<#F2C443>🍪 Willkommen an der Keks-Station. <gray>Linksklick backt, Rechtsklick öffnet das Menü.");
        }))), 330);
    }

    public static Canvas main(MainView v) {
        Canvas c = new Canvas(W).heading("Cookie Clicker", GOLD);
        int y = head(c, v.head());
        int tw = (W - 12) / 4, th = 62;
        for (int i = 0; i < v.tiles().size(); i++) {
            Tile t = v.tiles().get(i);
            int x = (i % 4) * (tw + 4), ty = y + (i / 4) * (th + 4);
            c.box(x, ty, tw, th, KitData.Box.WOOD_CARD, Canvas.Z_BUTTON);
            c.sprite(x + (tw - t.art().width) / 2, ty + 5 + (32 - t.art().height) / 2, t.art());
            c.textCenter(x + tw / 2, ty + 40, t.name(), GOLD, true);
            c.textCenter(x + tw / 2, ty + 50, Canvas.fit(t.sub(), false, tw - 6), t.alert() ? GREEN : TAN, false);
            if (t.alert()) c.sprite(x + tw - 17, ty + 3, KitData.Sprite.HB_ALERT_S);
            c.action(x, ty, tw, th, t.open(), t.name());
        }
        y += 2 * (th + 4) + 2;
        c.text(4, y, Canvas.fit(v.hint(), false, W - 8), DIM, false);
        y += 14;
        if (v.station() != null) {
            c.smallButton(W - 150, y, 150, KitData.Small.GOLD, "Zur Keks-Station", GOLD, true);
            c.action(W - 150, y, 150, KitData.SMALL_H, v.station(), "Zum großen Keks teleportieren");
        }
        return c;
    }

    // =================================================================================================================
    // Gebäude
    // =================================================================================================================

    public record BRow(KitData.Sprite art, String name, int count, String each, String total, String price, boolean affordable,
                       String buyLabel, Consumer<Player> buy) {}

    public record BuildView(Head head, int amount, List<BRow> rows, String locked, int page, int pages, Consumer<Player>[] modes,
                            Consumer<Player> prev, Consumer<Player> next, Consumer<Player> back) {}

    public static void buildings(Player p, int page) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        int amount = AMOUNT.getOrDefault(p.getUniqueId(), 1);
        List<BRow> rows = new ArrayList<>();
        String locked = null;
        for (int b = 0; b < CookieService.N; b++) {
            Building bd = CookieContent.BUILDINGS.get(b);
            if (!s.unlocked(d, b)) {
                if (locked == null) locked = "??? · nächstes Gebäude ab " + n(bd.cost()) + " Keksen, wenn du " + CookieContent.BUILDINGS.get(b - 1).name() + " hast";
                continue;
            }
            int cnt = amount < 0 ? Math.max(1, s.maxAffordable(d, b)) : amount;
            double price = s.price(d, b, cnt);
            int fb = b;
            double each = bd.cps() * s.tierMult(d, b) * s.globalMult(d);
            rows.add(new BRow(bd.art(), bd.name(), d.buildings[b], "+" + n(each) + "/s je Stück", d.buildings[b] > 0 ? "gesamt +" + n(s.buildingCps(d, b)) + "/s" : bd.desc(),
                    n(price), price <= d.cookies, "Kaufen x" + cnt, run(q -> {
                        feedback(q, s.buy(q, fb, AMOUNT.getOrDefault(q.getUniqueId(), 1)));
                        buildings(q, page);
                    })));
        }
        int per = 7, pages = Math.max(1, (rows.size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
        List<BRow> shown = rows.subList(pg * per, Math.min(rows.size(), pg * per + per));
        @SuppressWarnings("unchecked")
        Consumer<Player>[] modes = new Consumer[4];
        int[] am = {1, 10, 100, -1};
        for (int i = 0; i < 4; i++) {
            int a = am[i];
            modes[i] = run(q -> {
                AMOUNT.put(q.getUniqueId(), a);
                buildings(q, pg);
            });
        }
        UiDialog.show(p, buildings(new BuildView(head(d, "Gebäude"), amount, shown, pg == pages - 1 ? locked : null, pg, pages, modes,
                pg > 0 ? run(q -> buildings(q, pg - 1)) : null, pg < pages - 1 ? run(q -> buildings(q, pg + 1)) : null,
                run(CookieScreen::main))), 330);
    }

    public static Canvas buildings(BuildView v) {
        Canvas c = new Canvas(W).heading("Gebäude", GOLD);
        int y = head(c, v.head());
        String[] labels = {"x1", "x10", "x100", "Max"};
        int[] am = {1, 10, 100, -1};
        c.text(4, y + 4, Canvas.caps("Menge"), TAN, true);
        for (int i = 0; i < 4; i++) {
            boolean on = v.amount() == am[i];
            c.smallButton(60 + i * 58, y, 54, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, labels[i], on ? CREAM : TAN, true);
            c.action(60 + i * 58, y, 54, KitData.SMALL_H, v.modes()[i], "Kaufmenge " + labels[i]);
        }
        c.textRight(W - 4, y + 4, "Jedes Stück 15 % teurer", DIM, false);
        y += KitData.SMALL_H + 4;
        for (BRow r : v.rows()) {
            c.box(0, y, W, 38, KitData.Box.WOOD_CARD);
            c.sprite(4 + (32 - r.art().width) / 2, y + 3 + (32 - r.art().height) / 2, r.art());
            int nw = c.text(42, y + 6, Canvas.fit(r.name(), true, 120), CREAM, true);
            c.text(46 + nw, y + 6, "x" + r.count(), GOLD, true);
            c.text(42, y + 17, Canvas.fit(r.each(), false, 190), GREEN, false);
            c.text(42, y + 27, Canvas.fit(r.total(), false, 190), DIM, false);
            c.textRight(W - 8, y + 6, Canvas.fit(r.price() + " Kekse", true, 120), r.affordable() ? GOLD : RED, true);
            c.smallButton(W - 118, y + 18, 110, r.affordable() ? KitData.Small.GOLD : KitData.Small.DARK, r.buyLabel(), r.affordable() ? GOLD : DIM, true);
            c.action(W - 118, y + 18, 110, KitData.SMALL_H, r.buy(), r.buyLabel());
            y += 41;
        }
        if (v.locked() != null) {
            c.box(0, y, W, 24, KitData.Box.WOOD_CARD);
            c.text(10, y + 8, Canvas.fit(v.locked(), false, W - 20), DIM, false);
            y += 27;
        }
        pager(c, y + 2, v.page(), v.pages(), v.prev(), v.next());
        back(c, y + 2, v.back());
        return c;
    }

    private static void pager(Canvas c, int y, int page, int pages, Consumer<Player> prev, Consumer<Player> next) {
        if (pages <= 1) return;
        c.smallButton(W - 190, y, 60, KitData.Small.DARK, "«", prev == null ? DIM : CREAM, true);
        if (prev != null) c.action(W - 190, y, 60, KitData.SMALL_H, prev, "Vorige Seite");
        c.textCenter(W - 95, y + 4, "Seite " + (page + 1) + "/" + pages, TAN, false);
        c.smallButton(W - 60, y, 60, KitData.Small.DARK, "»", next == null ? DIM : CREAM, true);
        if (next != null) c.action(W - 60, y, 60, KitData.SMALL_H, next, "Nächste Seite");
    }

    // =================================================================================================================
    // Upgrades
    // =================================================================================================================

    public record UCard(KitData.Sprite art, String name, String desc, String price, boolean affordable, Consumer<Player> buy) {}

    public record UpView(Head head, List<UCard> cards, String footer, int page, int pages, Consumer<Player> prev, Consumer<Player> next,
                         Consumer<Player> back) {}

    static KitData.Sprite art(Upgrade u) {
        return switch (u.kind()) {
            case TIER -> CookieContent.BUILDINGS.get(CookieContent.buildingIndex(u.target())).art();
            case CLICK_X, CLICK_CPS -> KitData.Sprite.CK_M_KLICK;
            case GLOBAL -> KitData.Sprite.CK_M_UPGRADES;
            case OFEN -> KitData.Sprite.CK_B_OFEN;
            case GLUECK -> KitData.Sprite.CK_GOLD;
        };
    }

    public static void upgrades(Player p, int page) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        List<UCard> cards = new ArrayList<>();
        for (Upgrade u : s.available(d)) {
            cards.add(new UCard(art(u), u.name(), u.desc(), n(u.cost()), u.cost() <= d.cookies, run(q -> {
                feedback(q, s.buyUpgrade(q, u.id()));
                upgrades(q, page);
            })));
        }
        int per = 10, pages = Math.max(1, (cards.size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
        String footer = d.upgrades.size() + " gekauft · " + s.locked(d) + " noch gesperrt (???: mehr Gebäude, Klicks, Kekse oder Glückskekse)";
        UiDialog.show(p, upgrades(new UpView(head(d, "Upgrades"), cards.subList(pg * per, Math.min(cards.size(), pg * per + per)), footer, pg, pages,
                pg > 0 ? run(q -> upgrades(q, pg - 1)) : null, pg < pages - 1 ? run(q -> upgrades(q, pg + 1)) : null, run(CookieScreen::main))), 330);
    }

    public static Canvas upgrades(UpView v) {
        Canvas c = new Canvas(W).heading("Upgrades", GOLD);
        int y = head(c, v.head());
        int cw = (W - 4) / 2, ch = 46;
        for (int i = 0; i < v.cards().size(); i++) {
            UCard u = v.cards().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            c.box(x, cy, cw, ch, u.affordable() ? KitData.Box.WOOD : KitData.Box.WOOD_CARD);
            c.sprite(x + 4 + (32 - u.art().width) / 2, cy + 3 + (32 - u.art().height) / 2, u.art());
            c.text(x + 40, cy + 5, Canvas.fit(u.name(), true, cw - 46), u.affordable() ? GOLD : CREAM, true);
            c.text(x + 40, cy + 16, Canvas.fit(u.desc(), false, cw - 46), TAN, false);
            String label = u.price() + " Kekse";
            c.smallButton(x + 40, cy + ch - KitData.SMALL_H - 4, cw - 46, u.affordable() ? KitData.Small.GOLD : KitData.Small.DARK, label,
                    u.affordable() ? GOLD : RED, true);
            c.action(x + 40, cy + ch - KitData.SMALL_H - 4, cw - 46, KitData.SMALL_H, u.buy(), u.desc());
        }
        if (v.cards().isEmpty()) {
            c.box(0, y, W, 28, KitData.Box.WOOD_CARD);
            c.text(10, y + 10, Canvas.fit("Gerade kein Upgrade frei: mehr Gebäude kaufen, klicken oder backen.", false, W - 20), DIM, false);
            y += 32;
        } else {
            y += ((v.cards().size() + 1) / 2) * (ch + 4);
        }
        c.text(4, y, Canvas.fit(v.footer(), false, W - 8), DIM, false);
        y += 14;
        pager(c, y, v.page(), v.pages(), v.prev(), v.next());
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Keks-Laden
    // =================================================================================================================

    /** Karte im Laden: Item-Bild, Name, Beschreibung, Preis, heute noch frei / Limit. */
    public record OCard(org.bukkit.Material icon, String name, String desc, String price, boolean affordable, int left,
                        int daily, Consumer<Player> buy) {}

    public record ShopView(Head head, int tab, List<OCard> cards, String blocked, Consumer<Player>[] tabs, Consumer<Player> back) {}

    static final List<String> SHOP_TABS = List.of("Vorteile", "Items");

    @SuppressWarnings("unchecked")
    public static void shop(Player p, int tab) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        List<OCard> cards = new ArrayList<>();
        for (CookieContent.Offer o : CookieContent.OFFERS) {
            if (o.perk() != (tab == 0)) continue;
            double price = s.offerPrice(d, o);
            int left = Math.max(0, o.daily() - s.boughtToday(d, o.id()));
            cards.add(new OCard(o.icon(), o.name(), o.desc(), n(price), price <= d.cookies && left > 0, left, o.daily(), run(q -> {
                String err = s.buyOffer(q, o.id());
                feedback(q, err);
                if (err == null) Msg.send(q, "<#F2C443>🍪 <white>" + o.name() + " <gray>gekauft für <white>" + n(price) + " Kekse<gray>.");
                shop(q, tab);
            })));
        }
        Consumer<Player>[] tabs = new Consumer[SHOP_TABS.size()];
        for (int i = 0; i < tabs.length; i++) {
            int t = i;
            tabs[i] = run(q -> shop(q, t));
        }
        UiDialog.show(p, shop(new ShopView(head(d, "Keks-Laden"), tab, cards, s.shopBlocked(p), tabs, run(CookieScreen::main))), 330);
    }

    public static Canvas shop(ShopView v) {
        Canvas c = new Canvas(W).heading("Keks-Laden", GOLD);
        int y = head(c, v.head());
        int tw = (W - 4) / 2;
        for (int i = 0; i < SHOP_TABS.size(); i++) {
            boolean on = i == v.tab();
            c.smallButton(i * (tw + 4), y, tw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, SHOP_TABS.get(i), on ? CREAM : TAN, true);
            if (!on) c.action(i * (tw + 4), y, tw, KitData.SMALL_H, v.tabs()[i], SHOP_TABS.get(i));
        }
        y += KitData.SMALL_H + 4;
        int cw = (W - 4) / 2, ch = 46;
        for (int i = 0; i < v.cards().size(); i++) {
            OCard o = v.cards().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            boolean ok = o.affordable() && v.blocked() == null;
            c.box(x, cy, cw, ch, ok ? KitData.Box.WOOD : KitData.Box.WOOD_CARD);
            c.item(x + 4, cy + 7, o.icon(), true, KitData.Icon.CHEST);
            c.text(x + 40, cy + 5, Canvas.fit(o.name(), true, cw - 84), ok ? GOLD : CREAM, true);
            c.textRight(x + cw - 6, cy + 5, o.left() + "/" + o.daily(), o.left() > 0 ? TAN : RED, false);
            c.text(x + 40, cy + 16, Canvas.fit(o.desc(), false, cw - 46), TAN, false);
            String label = o.left() <= 0 ? "Heute ausverkauft" : o.price() + " Kekse";
            c.smallButton(x + 40, cy + ch - KitData.SMALL_H - 4, cw - 46, ok ? KitData.Small.GOLD : KitData.Small.DARK, label,
                    ok ? GOLD : RED, true);
            c.action(x + 40, cy + ch - KitData.SMALL_H - 4, cw - 46, KitData.SMALL_H, o.buy(), o.desc());
        }
        y += ((v.cards().size() + 1) / 2) * (ch + 4);
        c.text(4, y, Canvas.fit(v.blocked() != null ? v.blocked()
                : "Preise wachsen mit deiner Bäckerei. Die Limits gelten je Tag.", false, W - 8),
                v.blocked() != null ? RED : DIM, false);
        y += 14;
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Erfolge
    // =================================================================================================================

    public record ACell(String name, String desc, boolean done) {}

    public record AchView(Head head, int tab, int[] counts, int[] totals, List<ACell> cells, int page, int pages, Consumer<Player>[] tabs,
                          Consumer<Player> prev, Consumer<Player> next, Consumer<Player> back) {}

    public static void achievements(Player p, int tab, int page) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        int[] counts = new int[CookieContent.TABS.size()], totals = new int[CookieContent.TABS.size()];
        List<ACell> cells = new ArrayList<>();
        for (Achievement a : CookieContent.ACHIEVEMENTS) {
            int t = CookieContent.TABS.indexOf(a.tab());
            totals[t]++;
            boolean done = d.achievements.contains(a.id());
            if (done) counts[t]++;
            if (t == tab) cells.add(new ACell(a.name(), a.desc(), done));
        }
        @SuppressWarnings("unchecked")
        Consumer<Player>[] tabs = new Consumer[CookieContent.TABS.size()];
        for (int i = 0; i < tabs.length; i++) {
            int ti = i;
            tabs[i] = run(q -> achievements(q, ti, 0));
        }
        int per = 14, pages = Math.max(1, (cells.size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
        UiDialog.show(p, achievements(new AchView(head(d, "Erfolge"), tab, counts, totals, cells.subList(pg * per, Math.min(cells.size(), pg * per + per)),
                pg, pages, tabs, pg > 0 ? run(q -> achievements(q, tab, pg - 1)) : null, pg < pages - 1 ? run(q -> achievements(q, tab, pg + 1)) : null,
                run(CookieScreen::main))), 330);
    }

    public static Canvas achievements(AchView v) {
        Canvas c = new Canvas(W).heading("Erfolge", GOLD);
        int y = head(c, v.head());
        int tw = (W - 16) / 5;
        for (int i = 0; i < CookieContent.TABS.size(); i++) {
            boolean on = i == v.tab();
            String label = CookieContent.TABS.get(i) + " " + v.counts()[i] + "/" + v.totals()[i];
            c.smallButton(i * (tw + 4), y, tw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, Canvas.fit(label, true, tw - 6), on ? CREAM : TAN, true);
            c.action(i * (tw + 4), y, tw, KitData.SMALL_H, v.tabs()[i], CookieContent.TABS.get(i));
        }
        y += KitData.SMALL_H + 4;
        int cw = (W - 4) / 2, ch = 28;
        for (int i = 0; i < v.cells().size(); i++) {
            ACell a = v.cells().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 3);
            c.box(x, cy, cw, ch, a.done() ? KitData.Box.WOOD : KitData.Box.WOOD_CARD);
            c.icon(x + 5, cy + 6, a.done() ? KitData.Icon.STAR : KitData.Icon.BARRIER, false);
            c.text(x + 26, cy + 5, Canvas.fit(a.name(), true, cw - 32), a.done() ? GOLD : DIM, true);
            c.text(x + 26, cy + 16, Canvas.fit(a.desc(), false, cw - 32), a.done() ? TAN : DIM, false);
        }
        y += ((v.cells().size() + 1) / 2) * (ch + 3) + 2;
        c.text(4, y, "Jeder Erfolg: +2 % Kekse pro Sekunde, für immer.", DIM, false);
        y += 14;
        pager(c, y, v.page(), v.pages(), v.prev(), v.next());
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Aufstieg und Sternen-Baum
    // =================================================================================================================

    public record NCard(String name, String desc, long cost, int state, Consumer<Player> buy) {}

    public record PrestigeView(Head head, long level, long levelNow, long gain, double progress, String next, long stars, int ascensions,
                               List<NCard> nodes, Consumer<Player> ascend, Consumer<Player> back) {}

    public static void prestige(Player p) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        long now = CookieService.levelFor(d.allTime);
        double from = CookieService.cookiesForLevel(now), to = CookieService.cookiesForLevel(now + 1);
        List<NCard> nodes = new ArrayList<>();
        for (int i = 0; i < CookieContent.TREE.size(); i++) {
            Node nd = CookieContent.TREE.get(i);
            int state = d.tree.contains(nd.id()) ? 2 : s.nodeAvailable(d, i) ? (d.stars >= nd.cost() ? 1 : 0) : -1;
            nodes.add(new NCard(nd.name(), nd.desc(), nd.cost(), state, state == 1 ? run(q -> {
                feedback(q, s.buyNode(q, nd.id()));
                prestige(q);
            }) : null));
        }
        long gain = s.starsOnAscend(d);
        UiDialog.show(p, prestige(new PrestigeView(head(d, "Aufstieg"), d.level, now, gain, (d.allTime - from) / Math.max(1, to - from),
                n(to) + " Kekse insgesamt", d.stars, d.ascensions, nodes, gain > 0 ? run(q -> confirmAscend(q, gain)) : null, run(CookieScreen::main))), 330);
    }

    static void confirmAscend(Player p, long gain) {
        Canvas c = new Canvas(W).heading("Aufsteigen?", GOLD);
        c.box(0, 0, W, 86, KitData.Box.WOOD);
        c.sprite(10 + (32 - KitData.Sprite.CK_M_AUFSTIEG.width) / 2, 10, KitData.Sprite.CK_M_AUFSTIEG);
        c.text(50, 10, "Wirklich aufsteigen?", GOLD, true);
        int y = 24;
        for (String l : Canvas.wrap("Kekse, Gebäude und Upgrades starten neu. Du bekommst " + n(gain) + " Backsterne und jede Stufe gibt +2 % "
                + "Kekse für immer. Erfolge, Sternen-Baum und Titel bleiben.", W - 60)) {
            c.text(50, y, l, CREAM, false);
            y += 10;
        }
        c.smallButton(W - 140, 64, 130, KitData.Small.GOLD, "Aufsteigen", GOLD, true);
        c.action(W - 140, 64, 130, KitData.SMALL_H, run(q -> {
            feedback(q, svc().ascend(q));
            prestige(q);
        }), "Jetzt aufsteigen");
        c.smallButton(W - 280, 64, 130, KitData.Small.DARK, "Abbrechen", TAN, false);
        c.action(W - 280, 64, 130, KitData.SMALL_H, run(CookieScreen::prestige), "Abbrechen");
        UiDialog.show(p, c, 330);
    }

    public static Canvas prestige(PrestigeView v) {
        Canvas c = new Canvas(W).heading("Aufstieg", GOLD);
        int y = head(c, v.head());
        c.box(0, y, W, 66, KitData.Box.WOOD);
        c.sprite(8 + (32 - KitData.Sprite.CK_M_AUFSTIEG.width) / 2, y + 8, KitData.Sprite.CK_M_AUFSTIEG);
        c.text(46, y + 6, "Stufe " + n(v.level()) + " · +" + n(2 * v.level()) + " % Kekse für immer", GOLD, true);
        c.text(46, y + 18, Canvas.fit("Mit deinen Keksen: Stufe " + n(v.levelNow()) + " · Nächste bei " + v.next(), false, W - 54), TAN, false);
        c.bar(46, y + 31, W - 200, Math.max(0, Math.min(1, v.progress())), KitData.Fill.GOLD);
        c.text(46, y + 42, Canvas.fit("Backsterne: " + n(v.stars()) + " frei · " + v.ascensions() + " Aufstiege", false, 220), CREAM, false);
        String label = v.gain() > 0 ? "Aufsteigen · +" + n(v.gain()) + " ★" : "Noch kein Stern";
        c.smallButton(W - 146, y + 26, 138, v.ascend() != null ? KitData.Small.GOLD : KitData.Small.DARK, label, v.ascend() != null ? GOLD : DIM, true);
        if (v.ascend() != null) c.action(W - 146, y + 26, 138, KitData.SMALL_H, v.ascend(), "Neu starten mit Backsternen");
        y += 70;
        c.text(4, y, Canvas.caps("Sternen-Baum") + " · bleibt für immer", GOLD, true);
        y += 12;
        int cw = (W - 4) / 2, ch = 36;
        for (int i = 0; i < v.nodes().size(); i++) {
            NCard nd = v.nodes().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 3);
            c.box(x, cy, cw, ch, nd.state() == 2 ? KitData.Box.WOOD : KitData.Box.WOOD_CARD);
            c.text(x + 6, cy + 5, Canvas.fit(nd.name(), true, cw - 70), nd.state() == 2 ? GOLD : nd.state() < 0 ? DIM : CREAM, true);
            c.text(x + 6, cy + 17, Canvas.fit(nd.desc(), false, cw - 12), nd.state() < 0 ? DIM : TAN, false);
            String st = nd.state() == 2 ? "✔" : nd.cost() + " ★";
            if (nd.state() == 1) {
                c.smallButton(x + cw - 62, cy + 3, 56, KitData.Small.GOLD, st, GOLD, true);
                c.action(x + cw - 62, cy + 3, 56, KitData.SMALL_H, nd.buy(), nd.desc());
            } else {
                c.textRight(x + cw - 8, cy + 5, st, nd.state() == 2 ? GREEN : nd.state() == 0 ? RED : DIM, true);
            }
        }
        y += ((v.nodes().size() + 1) / 2) * (ch + 3) + 2;
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Rangliste
    // =================================================================================================================

    public record RRow(int place, String name, String value, boolean self) {}

    public record RankView(Head head, int board, List<RRow> rows, String own, Consumer<Player>[] boards, Consumer<Player> back) {}

    public static void ranking(Player p, Board b) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        List<RRow> rows = new ArrayList<>();
        int i = 0;
        for (Map.Entry<UUID, Data> e : s.top(b, 10)) {
            i++;
            rows.add(new RRow(i, e.getValue().name.isEmpty() ? "?" : e.getValue().name, n(s.value(e.getValue(), b)), e.getKey().equals(p.getUniqueId())));
        }
        int r = s.rank(p.getUniqueId(), b);
        @SuppressWarnings("unchecked")
        Consumer<Player>[] boards = new Consumer[Board.values().length];
        for (Board x : Board.values()) boards[x.ordinal()] = run(q -> ranking(q, x));
        UiDialog.show(p, ranking(new RankView(head(d, "Rangliste"), b.ordinal(), rows, (r > 0 ? "Dein Platz: #" + r : "Noch kein Platz") + " · "
                + n(s.value(d, b)), boards, run(CookieScreen::main))), 330);
    }

    public static Canvas ranking(RankView v) {
        Canvas c = new Canvas(W).heading("Rangliste", GOLD);
        int y = head(c, v.head());
        int tw = (W - 12) / 4;
        for (Board b : Board.values()) {
            boolean on = b.ordinal() == v.board();
            c.smallButton(b.ordinal() * (tw + 4), y, tw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, Canvas.fit(b.label, true, tw - 6), on ? CREAM : TAN, true);
            c.action(b.ordinal() * (tw + 4), y, tw, KitData.SMALL_H, v.boards()[b.ordinal()], b.label);
        }
        y += KitData.SMALL_H + 4;
        for (RRow r : v.rows()) {
            c.box(0, y, W, 20, r.self() ? KitData.Box.WOOD : KitData.Box.WOOD_CARD);
            TextColor pc = r.place() == 1 ? GOLD : r.place() == 2 ? TextColor.color(0xD8DEE8) : r.place() == 3 ? TextColor.color(0xD08A4A) : TAN;
            c.text(8, y + 6, "#" + r.place(), pc, true);
            c.text(40, y + 6, Canvas.fit(r.name(), true, 220), r.self() ? GOLD : CREAM, true);
            c.textRight(W - 8, y + 6, r.value(), GOLD, true);
            y += 22;
        }
        if (v.rows().isEmpty()) {
            c.box(0, y, W, 24, KitData.Box.WOOD_CARD);
            c.text(10, y + 8, "Noch niemand in dieser Liste.", DIM, false);
            y += 26;
        }
        c.text(4, y + 2, v.own(), CREAM, false);
        y += 16;
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Statistik
    // =================================================================================================================

    public record StatsView(Head head, List<String[]> lines, Consumer<Player> back) {}

    public static void stats(Player p) {
        if (!ui(p)) return;
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        long runMin = (System.currentTimeMillis() - d.runStart) / 60_000;
        List<String[]> lines = List.of(
                new String[]{"Kekse jetzt", n(d.cookies)}, new String[]{"In dieser Runde", n(d.baked)},
                new String[]{"Aller Zeiten", n(d.allTime)}, new String[]{"Mit Klicks gebacken", n(d.handmade)},
                new String[]{"Pro Sekunde", "+" + n(d.cps())}, new String[]{"Bester Wert", "+" + n(d.bestCps) + "/s"},
                new String[]{"Je Klick", "+" + n(s.clickValue(d))}, new String[]{"Klicks", n(d.clicks)},
                new String[]{"Gebäude", n(d.totalBuildings())}, new String[]{"Upgrades", d.upgrades.size() + "/" + CookieContent.UPGRADES.size()},
                new String[]{"Erfolge", d.achievements.size() + "/" + CookieContent.ACHIEVEMENTS.size()}, new String[]{"Erfolgs-Bonus", "+" + 2 * d.achievements.size() + " %"},
                new String[]{"Glückskekse gefangen", n(d.golden)}, new String[]{"Glückskekse verpasst", n(d.goldenMissed)},
                new String[]{"Keksregen gefangen", n(d.rainCaught)}, new String[]{"Ohne dich", Math.round(s.offlineRate(d) * 100) + " % · " + s.offlineHours(d) + " h"},
                new String[]{"Stufe", n(d.level) + " (+" + 2 * d.level + " %)"}, new String[]{"Aufstiege", String.valueOf(d.ascensions)},
                new String[]{"Backsterne", n(d.stars) + " frei / " + n(d.starsTotal)}, new String[]{"Runde läuft seit", runMin >= 60 ? runMin / 60 + " h " + runMin % 60 + " min" : runMin + " min"});
        UiDialog.show(p, stats(new StatsView(head(d, "Statistik"), lines, run(CookieScreen::main))), 330);
    }

    public static Canvas stats(StatsView v) {
        Canvas c = new Canvas(W).heading("Statistik", GOLD);
        int y = head(c, v.head());
        int cw = (W - 4) / 2;
        for (int i = 0; i < v.lines().size(); i++) {
            String[] l = v.lines().get(i);
            int x = (i % 2) * (cw + 4), ly = y + (i / 2) * 22;
            c.box(x, ly, cw, 20, KitData.Box.WOOD_CARD);
            c.text(x + 8, ly + 6, Canvas.fit(l[0], false, cw - 118), TAN, false);
            c.textRight(x + cw - 8, ly + 6, Canvas.fit(l[1], true, 100), CREAM, true);
        }
        y += ((v.lines().size() + 1) / 2) * 22 + 4;
        back(c, y, v.back());
        return c;
    }

    // =================================================================================================================
    // Hilfe
    // =================================================================================================================

    static final String[][] HELP = {
            {"Grundlagen", "So fängst du an", "Linksklick auf den großen Keks backt Kekse.", "Rechtsklick auf den Keks öffnet das Menü (auch /cookie).",
                    "Schnell klicken baut eine Combo auf: bis x2 je Klick.", "!Dann kaufst du Gebäude", "Gebäude backen von selbst, auch wenn du weg bist.",
                    "Upgrades und Erfolge machen alles schneller.", "!Gut zu wissen", "Dein Stand gilt in allen Modi.", "Im Keks-Laden tauschst du Kekse gegen Items und Vorteile."},
            {"Gebäude", "Gebäude", "14 Arten, vom Kochlöffel bis zur Sternenbäckerei.", "Das nächste erscheint, sobald du das vorige hast.",
                    "Kaufmenge: x1, x10, x100 oder so viele wie möglich.", "Jedes weitere Stück kostet 15 % mehr. Kein Verkauf."},
            {"Upgrades", "Upgrades", "Stufen: ein Gebäude backt doppelt (ab 1, 10, 25, 50, 100, 150, 200 Stück).", "Klick: Handschuhe verdoppeln Klicks, Zuckerfinger geben % deiner Kekse pro Sekunde.",
                    "Rezepte: alle Gebäude +5 bis +20 %.", "Ofen: mehr und länger backen, während du weg bist.", "Glücksbringer: Glückskekse öfter und länger.",
                    "Beim Aufstieg sind Upgrades weg."},
            {"Glückskekse", "Glückskekse", "Erscheinen neben dem großen Keks, nur für dich, und leuchten golden.", "Sichtbar 13 Sekunden: schnell anklicken!",
                    "Alle 4 bis 10 Minuten, mit Glücksbringern öfter.", "!Was sie bringen", "Keksschatz: bis zu 15 Minuten Produktion auf einmal.",
                    "Backrausch: Kekse pro Sekunde x5 für 60 s.", "Zuckerschub: x2 für 3 Minuten.", "Klickfieber: Klicks x10 für 15 s."},
            {"Keksregen", "Keksregen", "Alle 30 Minuten regnet es 20 Sekunden lang Kekse an der Station.", "Jeder kann fangen: einfach drüberlaufen.",
                    "Jeder Keks bringt 30 Sekunden deiner Produktion.", "Der Countdown steht über dem Keks."},
            {"Erfolge", "Erfolge", CookieContent.ACHIEVEMENTS.size() + " Erfolge in 5 Bereichen.", "Jeder Erfolg gibt +2 % Kekse pro Sekunde.",
                    "Erfolge bleiben für immer, auch nach dem Aufstieg."},
            {"Aufstieg", "Aufstieg", "Die Stufe wächst mit allen je gebackenen Keksen.", "Aufsteigen: Kekse, Gebäude und Upgrades starten neu.",
                    "Je Stufe +2 % Kekse pro Sekunde, für immer.", "!Backsterne", "Jede neue Stufe bringt einen Backstern.",
                    "Damit kaufst du den Sternen-Baum: 12 Sterne, bleiben für immer.", "Lohnt sich, wenn deine Stufe deutlich gestiegen ist."},
            {"Rangliste", "Rangliste", "Kekse aller Zeiten, Kekse pro Sekunde, Klicks und Stufe.", "Die besten 10 stehen auch an der Station.",
                    "!Titel", "Dein Titel wächst mit der Stufe: Krümel, Teigkneter, Bäckerlehrling, Bäcker, Konditor, Backmeister, Keksbaron, Zuckerfürst, Keksmagier, Sternenbäcker."}};

    public record HelpView(int page, Consumer<Player>[] pages, Consumer<Player> back) {}

    public static void help(Player p, int page) {
        if (!ui(p)) return;
        @SuppressWarnings("unchecked")
        Consumer<Player>[] pages = new Consumer[HELP.length];
        for (int i = 0; i < HELP.length; i++) {
            int pi = i;
            pages[i] = run(q -> help(q, pi));
        }
        UiDialog.show(p, help(new HelpView(Math.max(0, Math.min(page, HELP.length - 1)), pages, run(CookieScreen::main))), 330);
    }

    public static Canvas help(HelpView v) {
        Canvas c = new Canvas(W).heading("Cookie-Hilfe", GOLD);
        c.box(0, 0, W, 40, KitData.Box.WOOD);
        c.sprite(8 + (32 - KitData.Sprite.CK_COOKIE.width) / 2, 4, KitData.Sprite.CK_COOKIE);
        c.text(46, 8, Canvas.caps("Cookie-Hilfe"), GOLD, true);
        c.text(46, 22, HELP[v.page()][0], TAN, false);
        c.textRight(W - 10, 8, "Seite " + (v.page() + 1) + "/" + HELP.length, DIM, false);
        int y = 46, nw = 110;
        for (int i = 0; i < HELP.length; i++) {
            boolean on = i == v.page();
            c.smallButton(0, y + i * (KitData.SMALL_H + 3), nw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, HELP[i][0], on ? CREAM : TAN, true);
            c.action(0, y + i * (KitData.SMALL_H + 3), nw, KitData.SMALL_H, v.pages()[i], HELP[i][0]);
        }
        int px = nw + 4, pw = W - px, ph = Math.max(HELP.length * (KitData.SMALL_H + 3) - 3, 150);
        c.box(px, y, pw, ph, KitData.Box.WOOD);
        String[] page = HELP[v.page()];
        c.text(px + 8, y + 6, Canvas.caps(page[1]), GOLD, true);
        int ly = y + 20;
        for (int i = 2; i < page.length; i++) {
            if (page[i].startsWith("!")) {
                ly += 4;
                c.text(px + 8, ly, page[i].substring(1), TextColor.color(0xFFF070), false);
                ly += 11;
                continue;
            }
            List<String> lines = Canvas.wrap(page[i], pw - 26);
            for (int k = 0; k < lines.size(); k++) {
                if (k == 0) c.text(px + 8, ly, "▸", DIM, false);
                c.text(px + 16, ly, lines.get(k), CREAM, false);
                ly += 10;
            }
        }
        back(c, y + ph + 4, v.back());
        return c;
    }

    // =================================================================================================================
    // Ohne Pack
    // =================================================================================================================

    public static void chat(Player p) {
        CookieService s = svc();
        Data d = s.data(p.getUniqueId());
        Msg.send(p, "<#F2C443><b>🍪 Cookie Clicker</b> <gray>– " + Msg.escape(s.title(d)) + " · Stufe " + n(Math.max(d.level, CookieService.levelFor(d.allTime))));
        Msg.send(p, " <gray>Kekse: <white>" + n(d.cookies) + " <gray>· pro Sekunde: <green>+" + n(d.cps()) + " <gray>· je Klick: <yellow>+" + n(s.clickValue(d)));
        Msg.send(p, " <gray>Gebäude: <white>" + d.totalBuildings() + " <gray>· Upgrades: <white>" + d.upgrades.size() + " <gray>· Erfolge: <white>"
                + d.achievements.size() + "/" + CookieContent.ACHIEVEMENTS.size());
        Msg.send(p, " <gray>Das Menü braucht das Kuby-Ressourcenpaket. <white>/cookie kaufen <Gebäude> [Menge]<gray>, <white>/cookie aufstieg");
    }

    // =================================================================================================================
    // SelfTest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        Head h = new Head("Cookie Clicker", "Sternenbäcker", 9_999_999, 9.99e300, 9.99e300, 9.99e300, "⚡ Backrausch x5 · 60 s");
        Head h0 = new Head("Cookie Clicker", "Krümel", 0, 0, 0, 1, null);
        List<Tile> tiles = new ArrayList<>();
        KitData.Sprite[] arts = {KitData.Sprite.CK_M_GEBAEUDE, KitData.Sprite.CK_M_UPGRADES, KitData.Sprite.CK_M_ERFOLGE, KitData.Sprite.CK_M_AUFSTIEG,
                KitData.Sprite.CK_M_LADEN, KitData.Sprite.CK_M_RANGLISTE, KitData.Sprite.CK_M_STATISTIK, KitData.Sprite.CK_M_HILFE};
        for (int i = 0; i < 8; i++) tiles.add(new Tile(arts[i], i == 0 ? "Gebäude" : "Glückskekse", i % 2 == 0 ? "999 kaufbar" : w, i % 2 == 0, no));
        reg.accept("Cookie Übersicht", () -> main(new MainView(h, tiles, w + w, no)));
        reg.accept("Cookie Übersicht leer", () -> main(new MainView(h0, tiles, "Klick an der Station mit Linksklick auf den Keks, um zu backen.", null)));
        List<BRow> rows = new ArrayList<>();
        for (Building b : CookieContent.BUILDINGS.subList(0, 7)) rows.add(new BRow(b.art(), b.name(), 9999, "+" + n(9.99e300) + "/s je Stück", w, n(9.99e300), rows.size() % 2 == 0, "Kaufen x100", no));
        @SuppressWarnings("unchecked")
        Consumer<Player>[] four = new Consumer[]{no, no, no, no};
        reg.accept("Cookie Gebäude", () -> buildings(new BuildView(h, -1, rows, "??? · " + w, 1, 2, four, no, no, no)));
        List<UCard> cards = new ArrayList<>();
        for (Upgrade u : CookieContent.UPGRADES.subList(0, 10)) cards.add(new UCard(art(u), u.name() + w, u.desc() + w, n(9.99e300), cards.size() % 3 == 0, no));
        reg.accept("Cookie Upgrades", () -> upgrades(new UpView(h, cards, w + w, 0, 9, null, no, no)));
        reg.accept("Cookie Upgrades leer", () -> upgrades(new UpView(h0, List.of(), "0 gekauft", 0, 1, null, null, no)));
        List<ACell> cells = new ArrayList<>();
        for (int i = 0; i < 14; i++) cells.add(new ACell(w, w + w, i % 3 == 0));
        @SuppressWarnings("unchecked")
        Consumer<Player>[] five = new Consumer[]{no, no, no, no, no};
        reg.accept("Cookie Erfolge", () -> achievements(new AchView(h, 2, new int[]{8, 8, 45, 7, 8}, new int[]{12, 8, 45, 7, 8}, cells, 0, 4, five, null, no, no)));
        List<NCard> nodes = new ArrayList<>();
        for (int i = 0; i < CookieContent.TREE.size(); i++) nodes.add(new NCard(CookieContent.TREE.get(i).name(), CookieContent.TREE.get(i).desc(), 500, i % 4 - 1, no));
        reg.accept("Cookie Aufstieg", () -> prestige(new PrestigeView(h, 9_999_999, 9_999_999, 9_999_999, 0.5, n(9.99e300) + " Kekse insgesamt", 9_999_999, 999, nodes, no, no)));
        List<RRow> rr = new ArrayList<>();
        for (int i = 1; i <= 10; i++) rr.add(new RRow(i, w, n(9.99e300), i == 4));
        @SuppressWarnings("unchecked")
        Consumer<Player>[] fourB = new Consumer[]{no, no, no, no};
        reg.accept("Cookie Rangliste", () -> ranking(new RankView(h, 0, rr, "Dein Platz: #9999 · " + n(9.99e300), fourB, no)));
        // Keks-Laden: beide Reiter, riesige Preise, ausverkauft, in der Lobby gesperrt
        @SuppressWarnings("unchecked")
        Consumer<Player>[] two = new Consumer[]{no, no};
        for (int t = 0; t < 2; t++) {
            int tab = t;
            List<OCard> oc = new ArrayList<>();
            for (CookieContent.Offer o : CookieContent.OFFERS) {
                if (o.perk() == (tab == 0)) oc.add(new OCard(o.icon(), o.name() + w, o.desc() + w, n(9.99e300), oc.size() % 3 == 0,
                        oc.size() % 4, o.daily(), no));
            }
            reg.accept("Cookie Laden " + SHOP_TABS.get(tab), () -> shop(new ShopView(h, tab, oc, null, two, no)));
            reg.accept("Cookie Laden " + SHOP_TABS.get(tab) + " Lobby", () -> shop(new ShopView(h0, tab, oc,
                    "Einkaufen geht nur in einem Spielmodus (SMP, Survival, Skyblock), nicht in der Lobby.", two, no)));
        }
        List<String[]> lines = new ArrayList<>();
        for (int i = 0; i < 20; i++) lines.add(new String[]{w, n(9.99e300) + " / " + n(9.99e300)});
        reg.accept("Cookie Statistik", () -> stats(new StatsView(h, lines, no)));
        @SuppressWarnings("unchecked")
        Consumer<Player>[] hp = new Consumer[HELP.length];
        java.util.Arrays.fill(hp, no);
        for (int i = 0; i < HELP.length; i++) {
            int pi = i;
            reg.accept("Cookie Hilfe " + (i + 1), () -> help(new HelpView(pi, hp, no)));
        }
    }
}
