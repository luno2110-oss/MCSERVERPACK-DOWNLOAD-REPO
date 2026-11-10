package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.GeneratorService;
import de.kuby.kubyskyblock.service.GeneratorService.Gen;
import de.kuby.kubyskyblock.service.GeneratorService.Kind;
import de.kuby.kubyskyblock.service.GeneratorService.Quality;
import de.kuby.kubyskyblock.service.GeneratorService.Type;
import de.kuby.kubyskyblock.service.IslandService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialoge der Generatoren: ein Generator (Lager, Ertrag, Einsammeln, Verkaufen, Upgrade, Veredeln, Abbauen), alle
 * Generatoren der Insel, Generator-Shop (Coins) und der Skyblock-Juwelenshop mit Premium-Generatoren (Silber, Gold,
 * Diamant), Generator-Boostern und dem übrigen Juwelen-Angebot.
 */
public final class GeneratorScreen {

    static final int W = 390;
    static final TextColor STEEL = TextColor.color(0xC8D2E0), DIM = TextColor.color(0x8A96A8), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x7FE08A), XPC = TextColor.color(0xC8F870), JEWEL = TextColor.color(0x5FE3A0), RED = TextColor.color(0xF27A7A),
            CYAN = TextColor.color(0x7AE8FF);

    private GeneratorScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static GeneratorService gs() {
        return sb().generators();
    }

    public static KitData.Sprite art(String type) {
        return art(type, Quality.NORMAL);
    }

    /** Spawner-Käfig der Art in der Farbe der Qualität (gn_<art>[_silber|_gold|_diamant]); eigene Arten: Edelstein. */
    public static KitData.Sprite art(String type, Quality q) {
        String suffix = q == null || q == Quality.NORMAL ? "" : "_" + q.name();
        try {
            return KitData.Sprite.valueOf(("GN_" + type + suffix).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            try {
                return KitData.Sprite.valueOf(("GN_EDELSTEIN" + suffix).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e2) {
                return KitData.Sprite.GN_EDELSTEIN;
            }
        }
    }

    static TextColor color(Quality q) {
        return TextColor.fromHexString(q.color);
    }

    private static Consumer<Player> run(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(sb().core(), p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    // =================================================================================================================
    // Ein Generator
    // =================================================================================================================

    public record Stock(KitData.Icon icon, String name, long count) {}

    public record View(KitData.Sprite art, String name, TextColor color, int level, int max, String rate, double fill, String store,
                       List<Stock> stock, String premium, String boost, long upgrade, long refine, String refineTo, boolean items,
                       Consumer<Player> collect, Consumer<Player> sell, Consumer<Player> doUpgrade, Consumer<Player> doRefine,
                       Consumer<Player> remove, Consumer<Player> back) {}

    public static void open(Player p, Gen g) {
        GeneratorService s = gs();
        Type t = s.type(g.type);
        if (t == null) return;
        s.produce(g, System.currentTimeMillis(), true);
        double cap = s.capacity(g);
        String rate, store;
        double fill;
        List<Stock> stock = new ArrayList<>();
        switch (t.kind()) {
            case MONEY -> {
                rate = GeneratorService.fmt(round2(s.perSecond(g))) + " $ pro Sekunde";
                fill = g.quality.autoCollect ? 1 : g.money / Math.max(1, cap);
                store = g.quality.autoCollect ? "Wird direkt aufs Konto gezahlt" : Msg.coins((long) g.money) + " / " + Msg.coins((long) cap) + " $";
            }
            case XP -> {
                rate = GeneratorService.fmt(round2(s.perSecond(g))) + " XP pro Sekunde";
                fill = g.quality.autoCollect ? 1 : g.xp / Math.max(1, cap);
                store = g.quality.autoCollect ? "Geht direkt an dich (online)" : Msg.coins((long) g.xp) + " / " + Msg.coins((long) cap) + " XP";
            }
            default -> {
                rate = "1 Item alle " + GeneratorService.fmt(round2(s.interval(g))) + " s";
                fill = g.quality.autoSell ? 1 : g.itemCount() / Math.max(1, cap);
                store = g.quality.autoSell ? "Auto-Verkauf: " + Msg.coins((long) g.money) + " $ bereit" : Msg.coins(g.itemCount()) + " / " + Msg.coins((long) cap) + " Items";
                g.items.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(12)
                        .forEach(e -> stock.add(new Stock(Icons.of(e.getKey(), KitData.Icon.PAPER), nice(e.getKey()), e.getValue())));
            }
        }
        String premium = switch (g.quality) {
            case NORMAL -> "Offline 50 % · Premium im Juwelenshop";
            case SILBER -> "x1,5 Ertrag · Upgrades -20 % · Lager x1,5 · Offline 75 %";
            case GOLD -> "x2 Ertrag · Upgrades -35 % · Lager x2 · Offline 100 % · Auto-Verkauf";
            case DIAMANT -> "x3 Ertrag · Upgrades -50 % · Lager x3 · Auto-Verkauf · Auto-Einsammeln";
        };
        double[] b = s.boost(g.island);
        String boost = b == null ? null : "Booster x" + GeneratorService.fmt(b[0]) + " noch " + de.kuby.kubyplugins.service.KitService.format((long) b[1] - System.currentTimeMillis());
        Quality next = g.quality.next();
        UiDialog.show(p, canvas(new View(art(t.id(), g.quality), s.name(t, g.quality), color(g.quality), g.level, s.maxLevel(), rate, fill, store, stock,
                premium, boost, s.upgradePrice(g), next == null ? -1 : s.jewels(next, true), next == null ? null : next.label,
                t.kind() == Kind.ITEM,
                run(pl -> {
                    feedback(pl, s.collect(pl, g));
                    open(pl, g);
                }),
                t.kind() == Kind.ITEM ? run(pl -> {
                    feedback(pl, s.sell(pl, g));
                    open(pl, g);
                }) : null,
                run(pl -> {
                    feedback(pl, s.upgrade(pl, g));
                    open(pl, g);
                }),
                next == null ? null : run(pl -> {
                    feedback(pl, s.refine(pl, g));
                    open(pl, g);
                }),
                run(pl -> IslandScreen.confirm(pl, "Generator abbauen", "Der Generator kommt mit Stufe und Qualität in dein Inventar, das Lager wird ausgezahlt.",
                        "Abbauen", q -> {
                            var gw = org.bukkit.Bukkit.getWorld(g.world);
                            var bl = gw == null ? null : gw.getBlockAt(g.x, g.y, g.z);
                            if (bl != null && s.at(bl) == g) {
                                bl.setType(Material.AIR);
                                s.remove(q, g);
                            }
                        })),
                run(GeneratorScreen::island))), 330);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Deutscher Item-Name (aus der Namensliste des Kerns, z. B. „Rohkupfer“ statt „Raw copper“). */
    static String nice(Material m) {
        return de.kuby.kubyplugins.ui.ItemNames.de(m);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Generator", v.color());
        c.box(0, 0, W, 54, KitData.Box.STEEL);
        c.sprite(10 + (32 - v.art().width) / 2, 11 + (32 - v.art().height) / 2, v.art());
        c.text(50, 12, Canvas.fit(v.name(), true, 220), v.color(), true);
        c.text(50, 24, "Stufe " + v.level() + " / " + v.max() + " · " + v.rate(), STEEL, false);
        c.text(50, 36, Canvas.fit(v.premium(), false, 320), DIM, false);
        if (v.boost() != null) c.textRight(W - 8, 12, "⚡ " + v.boost(), GOLD, true);
        int y = 60;
        c.box(0, y, W, 34, KitData.Box.STEEL_CARD);
        c.text(10, y + 6, Canvas.caps("Lager"), STEEL, true);
        c.textRight(W - 10, y + 6, v.store(), v.fill() >= 0.999 && !v.store().startsWith("Wird") && !v.store().startsWith("Geht")
                && !v.store().startsWith("Auto") ? RED : STEEL, false);
        c.bar(10, y + 20, W - 20, Math.max(0, Math.min(1, v.fill())), v.fill() >= 0.999 ? KitData.Fill.GOLD : KitData.Fill.CYAN);
        y += 34 + 6;
        if (v.items()) {
            int cw = (W - 8) / 3;
            for (int i = 0; i < v.stock().size(); i++) {
                Stock s = v.stock().get(i);
                int x = (i % 3) * (cw + 4), sy = y + (i / 3) * 22;
                c.box(x, sy, cw, 20, KitData.Box.STEEL_CARD);
                c.icon(x + 3, sy + 2, s.icon(), false);
                c.text(x + 22, sy + 6, Canvas.fit(s.name(), false, cw - 70), STEEL, false);
                c.textRight(x + cw - 6, sy + 6, Msg.coins(s.count()), GOLD, true);
            }
            if (v.stock().isEmpty()) {
                c.text(4, y + 4, "Noch nichts produziert.", DIM, false);
                y += 16;
            } else {
                y += ((v.stock().size() + 2) / 3) * 22 + 4;
            }
        }
        int bw = (W - 12) / 4;
        c.smallButton(0, y, bw, KitData.Small.GREEN, "Einsammeln", STEEL, true);
        c.action(0, y, bw, KitData.SMALL_H, v.collect(), "Lager einsammeln");
        c.smallButton(bw + 4, y, bw, KitData.Small.GOLD, "Verkaufen", v.sell() == null ? DIM : GOLD, true);
        if (v.sell() != null) c.action(bw + 4, y, bw, KitData.SMALL_H, v.sell(), "Items verkaufen");
        String up = v.upgrade() < 0 ? "Max. Stufe" : "Upgrade " + Msg.coins(v.upgrade()) + " $";
        c.smallButton(2 * (bw + 4), y, 2 * bw + 4, KitData.Small.GOLD, up, v.upgrade() < 0 ? DIM : GOLD, true);
        if (v.upgrade() >= 0) c.action(2 * (bw + 4), y, 2 * bw + 4, KitData.SMALL_H, v.doUpgrade(), "Nächste Stufe");
        y += KitData.SMALL_H + 4;
        if (v.refineTo() != null) {
            c.smallButton(0, y, 2 * bw + 4, KitData.Small.DARK, "Veredeln: " + v.refineTo() + " · " + v.refine() + " Juwelen", JEWEL, false);
            c.action(0, y, 2 * bw + 4, KitData.SMALL_H, v.doRefine(), "Nächste Qualität (Stufe bleibt)");
        }
        c.smallButton(2 * (bw + 4), y, bw, KitData.Small.DARK, "Abbauen", RED, false);
        c.action(2 * (bw + 4), y, bw, KitData.SMALL_H, v.remove(), "Generator abbauen");
        c.smallButton(3 * (bw + 4), y, bw, KitData.Small.DARK, "Alle", STEEL, false);
        c.action(3 * (bw + 4), y, bw, KitData.SMALL_H, v.back(), "Alle Generatoren der Insel");
        return c;
    }

    // =================================================================================================================
    // Alle Generatoren der Insel
    // =================================================================================================================

    public record Row(KitData.Sprite art, String name, TextColor color, int level, String store, double fill, Consumer<Player> open) {}

    public static void island(Player p) {
        IslandService.Island i = sb().islands().islandOf(p.getUniqueId());
        if (i == null) {
            IslandScreen.choose(p);
            return;
        }
        GeneratorService s = gs();
        long now = System.currentTimeMillis();
        List<Row> rows = new ArrayList<>();
        for (Gen g : s.onIsland(i.slot)) {
            Type t = s.type(g.type);
            if (t == null) continue;
            s.produce(g, now, true);
            double cap = s.capacity(g);
            String store = switch (t.kind()) {
                case MONEY -> g.quality.autoCollect ? "aufs Konto" : Msg.coins((long) g.money) + " $";
                case XP -> g.quality.autoCollect ? "direkt" : Msg.coins((long) g.xp) + " XP";
                default -> g.quality.autoSell ? Msg.coins((long) g.money) + " $" : Msg.coins(g.itemCount()) + " Items";
            };
            double fill = switch (t.kind()) {
                case MONEY -> g.money / Math.max(1, cap);
                case XP -> g.xp / Math.max(1, cap);
                default -> g.itemCount() / Math.max(1, cap);
            };
            rows.add(new Row(art(t.id(), g.quality), s.name(t, g.quality), color(g.quality), g.level, store, fill, run(pl -> open(pl, g))));
        }
        double[] b = s.boost(i.slot);
        Consumer<Player> collectAll = run(pl -> {
            for (Gen g : s.onIsland(i.slot)) s.collect(pl, g);
            island(pl);
        });
        UiDialog.show(p, island(rows, s.slots(i), b == null ? null : "⚡ x" + GeneratorService.fmt(b[0]) + " · "
                + de.kuby.kubyplugins.service.KitService.format((long) b[1] - now), collectAll, run(GeneratorScreen::shop), run(GeneratorScreen::jewels),
                run(IslandScreen::open)), 330);
    }

    public static Canvas island(List<Row> rows, int slots, String boost, Consumer<Player> collectAll, Consumer<Player> shop,
                                Consumer<Player> premium, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading("Generatoren", STEEL);
        c.box(0, 0, W, 54, KitData.Box.STEEL);
        c.sprite(10 + (32 - KitData.Sprite.GN_GELD.width) / 2, 11 + (32 - KitData.Sprite.GN_GELD.height) / 2, KitData.Sprite.GN_GELD);
        c.headline(50, 12, "Generatoren", STEEL);
        c.text(50, 36, rows.size() + " von " + slots + " Plätzen · " + (boost == null ? "kein Booster" : boost), boost == null ? DIM : GOLD, false);
        int y = 60;
        for (Row r : rows) {
            c.box(0, y, W, 38, KitData.Box.STEEL_CARD);
            c.sprite(6 + (32 - r.art().width) / 2, y + 3 + (32 - r.art().height) / 2, r.art());
            c.text(44, y + 7, Canvas.fit(r.name(), true, 200), r.color(), true);
            c.text(44, y + 19, "Stufe " + r.level(), DIM, false);
            c.textRight(W - 10, y + 7, r.store(), GOLD, true);
            c.bar(250, y + 23, W - 260, Math.max(0, Math.min(1, r.fill())), r.fill() >= 0.999 ? KitData.Fill.GOLD : KitData.Fill.CYAN);
            c.action(0, y, W, 38, r.open(), "Generator öffnen");
            y += 41;
        }
        if (rows.isEmpty()) {
            c.box(0, y, W, 30, KitData.Box.STEEL_CARD);
            c.text(10, y + 11, "Noch keine Generatoren. Kauf einen im Generator-Shop und stell ihn auf deine Insel.", DIM, false);
            y += 34;
        }
        int bw = (W - 12) / 4;
        c.smallButton(0, y + 2, bw, KitData.Small.GREEN, "Alle einsammeln", STEEL, false);
        c.action(0, y + 2, bw, KitData.SMALL_H, collectAll, "Alle Lager einsammeln");
        c.smallButton(bw + 4, y + 2, bw, KitData.Small.GOLD, "Shop", GOLD, true);
        c.action(bw + 4, y + 2, bw, KitData.SMALL_H, shop, "Generatoren kaufen");
        c.smallButton(2 * (bw + 4), y + 2, bw, KitData.Small.DARK, "Premium", JEWEL, true);
        c.action(2 * (bw + 4), y + 2, bw, KitData.SMALL_H, premium, "Silber, Gold, Diamant und Booster");
        c.smallButton(3 * (bw + 4), y + 2, bw, KitData.Small.DARK, "Insel", STEEL, false);
        c.action(3 * (bw + 4), y + 2, bw, KitData.SMALL_H, back, "Zurück zur Insel");
        return c;
    }

    // =================================================================================================================
    // Generator-Shop (Coins)
    // =================================================================================================================

    public record Offer(KitData.Sprite art, String name, String desc, long price, String unlock, boolean ok, Consumer<Player> buy) {}

    private static final java.util.Map<java.util.UUID, Integer> SHOP_TAB = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<String> SHOP_TABS = List.of("Start", "Fortgeschritten", "Nether", "End");

    /** Reiter einer Art: Stufen-Generatoren unter Nether/End, sonst Start (sofort) oder Fortgeschritten (ab Insel-Level). */
    private static int tabOf(Type t) {
        String st = de.kuby.kubyskyblock.service.StageService.stageKey(t);
        if ("end".equals(st)) return 3;
        if (st != null && !st.isEmpty()) return 2;
        return t.unlockLevel() > 0 ? 1 : 0;
    }

    public static void shop(Player p) {
        shop(p, SHOP_TAB.getOrDefault(p.getUniqueId(), 0));
    }

    public static void shop(Player p, int tab) {
        SHOP_TAB.put(p.getUniqueId(), tab);
        GeneratorService s = gs();
        IslandService.Island i = sb().islands().islandOf(p.getUniqueId());
        long level = i == null ? 0 : sb().islands().levelOf(i);
        long coins = sb().core().economy().balance(p.getUniqueId());
        int max = s.maxLevel();
        List<ShopRow> rows = new ArrayList<>();
        Map<String, Integer> placed = new java.util.HashMap<>();
        if (i != null) for (GeneratorService.Gen g : s.onIsland(i.slot)) placed.merge(g.type, 1, Integer::sum);
        for (Type t : s.types()) {
            if (tabOf(t) != tab) continue;
            de.kuby.kubyskyblock.service.StageService.Stage st = de.kuby.kubyskyblock.service.StageService.Stage.of(
                    de.kuby.kubyskyblock.service.StageService.stageKey(t));
            boolean stageOk = st == null || (sb().stages() != null && sb().stages().unlocked(i, st));
            boolean unlocked = level >= t.unlockLevel() && stageOk;
            String lock = unlocked ? null : !stageOk ? "ab " + st.label + "-Stufe" : "ab Insel-Level " + t.unlockLevel();
            List<Material> products = new ArrayList<>();
            String rate, speed;
            if (t.kind() == Kind.ITEM) {
                Map<Material, Integer> first = t.yields().isEmpty() ? Map.of() : t.yields().firstEntry().getValue();
                List<Map.Entry<Material, Integer>> sorted = new ArrayList<>(first.entrySet());
                sorted.sort((a, b) -> b.getValue() - a.getValue());
                for (Map.Entry<Material, Integer> e : sorted) products.add(e.getKey());
                Integer more = t.yields().higherKey(t.yields().isEmpty() ? 0 : t.yields().firstKey());
                rate = more == null ? null : "mehr ab Stufe " + more;
                speed = "Stufe 1: 1 Item alle " + GeneratorService.fmt(s.intervalOf(t, 1)) + " s · Stufe " + max + ": alle "
                        + GeneratorService.fmt(s.intervalOf(t, max)) + " s";
            } else {
                String unit = t.kind() == Kind.MONEY ? " $" : " XP";
                rate = GeneratorService.fmt(s.perSecondOf(t, 1)) + unit + " pro Sekunde";
                speed = "Stufe " + max + ": " + GeneratorService.fmt(s.perSecondOf(t, max)) + unit + " pro Sekunde · Lager für Stunden";
            }
            boolean afford = coins >= t.price();
            rows.add(new ShopRow(art(t.id()), t.name(), t.kind(), products, rate, speed, t.price(), placed.getOrDefault(t.id(), 0), lock,
                    afford, unlocked ? run(pl -> {
                        if (!sb().core().economy().withdraw(pl.getUniqueId(), t.price())) {
                            feedback(pl, "Dafür brauchst du " + Msg.coins(t.price()) + " $.");
                        } else {
                            pl.getInventory().addItem(s.item(t, Quality.NORMAL, 1)).values().forEach(r -> sb().core().abholbox().add(pl.getUniqueId(), r));
                            feedback(pl, null);
                            Msg.send(pl, "<green>" + t.name() + " gekauft. <gray>Stell ihn auf deine Insel (Block setzen), dann sammelt er.");
                        }
                        shop(pl, tab);
                    }) : null));
        }
        int used = i == null ? 0 : s.count(i.slot);
        int slots = i == null ? 0 : s.slots(i);
        List<Consumer<Player>> tabs = new ArrayList<>();
        for (int k = 0; k < SHOP_TABS.size(); k++) {
            int kk = k;
            tabs.add(run(pl -> shop(pl, kk)));
        }
        UiDialog.show(p, shopCanvas(new ShopView(tab, rows, coins, used, slots, tabs, run(GeneratorScreen::island), run(GeneratorScreen::jewels))), 330);
    }

    /** Eine Zeile im Generator-Shop. products: Ertrag (Item-Generatoren), rate: Geld/XP oder „mehr ab Stufe …“. */
    public record ShopRow(KitData.Sprite art, String name, Kind kind, List<Material> products, String rate, String speed, long price,
                          int placed, String lock, boolean afford, Consumer<Player> buy) {}

    public record ShopView(int tab, List<ShopRow> rows, long coins, int used, int slots, List<Consumer<Player>> tabs,
                           Consumer<Player> back, Consumer<Player> premium) {}

    public static Canvas shopCanvas(ShopView v) {
        Canvas c = new Canvas(W).heading("Generator-Shop", GOLD);
        c.box(0, 0, W, 54, KitData.Box.STEEL);
        c.sprite(10 + (32 - KitData.Sprite.GN_ERZ.width) / 2, 11 + (32 - KitData.Sprite.GN_ERZ.height) / 2, KitData.Sprite.GN_ERZ);
        c.headline(50, 12, "Generator-Shop", GOLD);
        c.text(50, 36, List.of(Canvas.Span.bold("1 ", GOLD), Canvas.Span.of("Kaufen  ", DIM), Canvas.Span.bold("2 ", GOLD),
                Canvas.Span.of("Aufstellen  ", DIM), Canvas.Span.bold("3 ", GOLD), Canvas.Span.of("Einsammeln", DIM)));
        c.textRight(W - 8, 12, Msg.coins(v.coins) + " $", GOLD, true);
        boolean full = v.slots > 0 && v.used >= v.slots;
        c.textRight(W - 8, 24, "Plätze " + v.used + "/" + v.slots, full ? RED : GREEN, false);
        c.smallButton(W - 8 - 60, 33, 60, KitData.Small.DARK, "Zurück", STEEL, false);
        c.action(W - 8 - 60, 33, 60, KitData.SMALL_H, v.back, "Zurück zu deinen Generatoren");
        // Reiter
        int tx = 0, gap = 3;
        int tw = (W - gap * (SHOP_TABS.size() - 1)) / SHOP_TABS.size();
        for (int k = 0; k < SHOP_TABS.size(); k++) {
            boolean on = k == v.tab;
            boolean bold = on && Canvas.width(SHOP_TABS.get(k), true) <= tw - 8;
            c.smallButton(tx, 58, tw, on ? KitData.Small.GREEN : KitData.Small.DARK, SHOP_TABS.get(k), on ? TextColor.color(0x06240E) : STEEL, bold);
            if (!on) c.action(tx, 58, tw, 16, v.tabs.get(k), SHOP_TABS.get(k) + " zeigen");
            tx += tw + gap;
        }
        int y = 78;
        if (v.rows.isEmpty()) {
            c.box(0, y, W, 30, KitData.Box.STEEL_CARD);
            c.text(10, y + 11, "Hier gibt es noch keine Generatoren.", DIM, false);
            y += 34;
        }
        for (ShopRow r : v.rows) {
            boolean locked = r.lock() != null;
            c.box(0, y, W, 50, locked ? KitData.Box.GRAY : KitData.Box.STEEL_CARD);
            c.sprite(8 + (32 - r.art().width) / 2, y + 9 + (32 - r.art().height) / 2, r.art());
            int nw = c.text(48, y + 7, Canvas.fit(r.name(), true, 170), locked ? DIM : STEEL, true);
            if (r.placed() > 0) c.text(52 + nw, y + 7, "· " + r.placed() + " aufgestellt", GREEN, false);
            int px = 48;
            if (r.kind() == Kind.ITEM) {
                for (int k = 0; k < r.products().size() && k < 8; k++) {
                    c.item(px, y + 17, r.products().get(k), false, KitData.Icon.CHEST);
                    px += 18;
                }
                if (r.rate() != null) c.text(px + 3, y + 22, r.rate(), DIM, false);
            } else {
                c.icon(px, y + 17, r.kind() == Kind.MONEY ? KitData.Icon.GOLD_NUGGET : KitData.Icon.EXPERIENCE, false);
                c.text(px + 20, y + 22, r.rate(), r.kind() == Kind.MONEY ? GOLD : XPC, true);
            }
            c.text(48, y + 37, Canvas.fit(r.speed(), false, W - 48 - (locked ? 8 + Canvas.width(r.lock(), false) : 80) - 6), DIM, false);
            c.textRight(W - 10, y + 7, Msg.coins(r.price()) + " $", locked ? DIM : r.afford() ? GOLD : RED, true);
            if (locked) {
                c.textRight(W - 10, y + 22, "Gesperrt", RED, true);
                c.textRight(W - 10, y + 33, r.lock(), RED, false);
            } else {
                c.smallButton(W - 10 - 64, y + 27, 64, r.afford() ? KitData.Small.GOLD : KitData.Small.DARK, "Kaufen", r.afford() ? GOLD : DIM, true);
                if (r.afford()) c.action(0, y, W, 50, r.buy(), r.name() + " für " + Msg.coins(r.price()) + " $ kaufen");
                else c.action(0, y, W, 50, null, "Dir fehlen noch " + Msg.coins(r.price() - v.coins) + " $");
            }
            y += 53;
        }
        c.smallButton(0, y + 2, W, KitData.Small.DARK, "Premium-Generatoren und Booster (Juwelen) ›", JEWEL, true);
        c.action(0, y + 2, W, KitData.SMALL_H, v.premium, "Silber, Gold, Diamant: mehr Ertrag, Auto-Verkauf");
        return c;
    }

    private static String ores(Type t) {
        List<String> out = new ArrayList<>();
        Map<Material, Integer> first = t.yields().isEmpty() ? Map.of() : t.yields().firstEntry().getValue();
        for (Material m : first.keySet()) {
            if (out.size() >= 3) break;
            out.add(nice(m));
        }
        return String.join(", ", out) + (t.yields().size() > 1 ? " und mehr" : "");
    }

    public static Canvas shop(List<Offer> offers, String title, String balance, TextColor balColor, Consumer<Player> back) {
        Canvas c = new Canvas(W).heading(title, GOLD);
        c.box(0, 0, W, 54, KitData.Box.STEEL);
        c.sprite(10 + (32 - KitData.Sprite.GN_ERZ.width) / 2, 11 + (32 - KitData.Sprite.GN_ERZ.height) / 2, KitData.Sprite.GN_ERZ);
        c.headline(50, 12, title, GOLD);
        c.text(50, 36, "Kaufen, auf die Insel stellen, Lager einsammeln", DIM, false);
        c.textRight(W - 8, 14, balance, balColor, true);
        c.smallButton(W - 8 - 60, 30, 60, KitData.Small.DARK, "Zurück", STEEL, false);
        c.action(W - 8 - 60, 30, 60, KitData.SMALL_H, back, "Zurück");
        int y = 60, cw = (W - 4) / 2, ch = 62;
        for (int i = 0; i < offers.size(); i++) {
            Offer o = offers.get(i);
            int x = (i % 2) * (cw + 4), oy = y + (i / 2) * (ch + 4);
            c.box(x, oy, cw, ch, o.unlock() != null ? KitData.Box.GRAY : KitData.Box.STEEL_CARD);
            c.sprite(x + 6 + (32 - o.art().width) / 2, oy + 6 + (32 - o.art().height) / 2, o.art());
            c.text(x + 44, oy + 7, Canvas.fit(o.name(), true, cw - 50), o.unlock() != null ? DIM : STEEL, true);
            List<String> d = Canvas.wrap(o.desc(), cw - 50);
            for (int l = 0; l < Math.min(2, d.size()); l++) c.text(x + 44, oy + 19 + 10 * l, d.get(l), DIM, false);
            if (o.unlock() != null) {
                c.text(x + 8, oy + ch - 13, "Gesperrt: " + o.unlock(), RED, false);
            } else {
                c.text(x + 8, oy + ch - 13, o.price() > 0 ? Msg.coins(o.price()) + (title.contains("Juwel") ? " Juwelen" : " $") : "", o.ok() ? GOLD : RED, true);
                c.smallButton(x + cw - 6 - 60, oy + ch - 19, 60, o.ok() ? KitData.Small.GOLD : KitData.Small.DARK, o.price() > 0 ? "Kaufen" : "Öffnen",
                        o.ok() ? GOLD : DIM, true);
                if (o.buy() != null && o.ok()) c.action(x, oy, cw, ch, o.buy(), o.name() + " kaufen");
            }
        }
        return c;
    }

    // =================================================================================================================
    // Juwelenshop (Skyblock)
    // =================================================================================================================

    public static void jewels(Player p) {
        GeneratorService s = gs();
        long jewels = sb().core().jewels().balance(p.getUniqueId());
        List<Offer> offers = new ArrayList<>();
        for (Quality q : new Quality[]{Quality.SILBER, Quality.GOLD, Quality.DIAMANT}) {
            long price = s.jewels(q, false);
            String desc = switch (q) {
                case SILBER -> "x1,5 Ertrag, Upgrades -20 %, Lager x1,5, offline 75 %";
                case GOLD -> "x2 Ertrag, Upgrades -35 %, Lager x2, offline 100 %, Auto-Verkauf";
                default -> "x3 Ertrag, Upgrades -50 %, Lager x3, Auto-Verkauf, Auto-Einsammeln";
            };
            offers.add(new Offer(art(q == Quality.SILBER ? "erz" : q == Quality.GOLD ? "geld" : "edelstein", q),
                    q.label + "-Generator", desc, price, null, jewels >= price, run(pl -> chooseKind(pl, q))));
        }
        for (Map<?, ?> b : sb().getConfig().getMapList("generatoren.booster")) {
            double factor = b.get("faktor") instanceof Number n ? n.doubleValue() : 2;
            int minutes = b.get("minuten") instanceof Number n ? n.intValue() : 30;
            long price = b.get("juwelen") instanceof Number n ? n.longValue() : 100;
            String name = String.valueOf(b.get("name") == null ? "Booster x" + GeneratorService.fmt(factor) : b.get("name"));
            offers.add(new Offer(KitData.Sprite.HB_DUO_RTP, name, "Alle Generatoren deiner Insel x" + GeneratorService.fmt(factor) + " für "
                    + minutes + " Minuten", price, null, jewels >= price, run(pl -> {
                IslandService.Island i = sb().islands().islandOf(pl.getUniqueId());
                if (i == null) {
                    feedback(pl, "Du hast keine Insel.");
                } else if (!sb().core().jewels().take(pl.getUniqueId(), price)) {
                    feedback(pl, "Dafür brauchst du " + price + " Juwelen.");
                } else {
                    gs().addBoost(i.slot, factor, minutes);
                    feedback(pl, null);
                    Msg.send(pl, "<gold>⚡ Generator-Booster x" + GeneratorService.fmt(factor) + " aktiv für " + minutes + " Minuten.");
                }
                jewels(pl);
            })));
        }
        offers.add(new Offer(KitData.Sprite.JS_GIFT, "Weitere Angebote", "Ränge, Schlüssel und Pakete", 0, null, true,
                run(pl -> new de.kuby.kubyplugins.gui.JewelStoreMenu(sb().core(), de.kuby.kubyplugins.service.ModeService.SKYBLOCK, 0).open(pl))));
        UiDialog.show(p, shop(offers, "Juwelenshop", Msg.coins(jewels) + " Juwelen", JEWEL, run(GeneratorScreen::island)), 330);
    }

    /** Premium-Generator kaufen: Art wählen. */
    private static void chooseKind(Player p, Quality q) {
        GeneratorService s = gs();
        long price = s.jewels(q, false);
        List<Offer> offers = new ArrayList<>();
        for (Type t : s.types()) {
            offers.add(new Offer(art(t.id(), q), q.label + "-" + t.name(), t.kind() == Kind.ITEM ? ores(t) : t.kind() == Kind.MONEY
                    ? "ab " + GeneratorService.fmt(t.perSecond() * q.output) + " $ pro Sekunde" : "ab " + GeneratorService.fmt(t.perSecond() * q.output) + " XP pro Sekunde",
                    price, null, sb().core().jewels().balance(p.getUniqueId()) >= price, run(pl -> {
                        if (!sb().core().jewels().take(pl.getUniqueId(), price)) {
                            feedback(pl, "Dafür brauchst du " + price + " Juwelen.");
                        } else {
                            pl.getInventory().addItem(s.item(t, q, 1)).values().forEach(r -> sb().core().abholbox().add(pl.getUniqueId(), r));
                            feedback(pl, null);
                            Msg.send(pl, "<" + q.color + ">" + s.name(t, q) + " gekauft! <gray>Stell ihn auf deine Insel.");
                        }
                        jewels(pl);
                    })));
        }
        UiDialog.show(p, shop(offers, "Juwelenshop · " + q.label, Msg.coins(sb().core().jewels().balance(p.getUniqueId())) + " Juwelen", JEWEL,
                run(GeneratorScreen::jewels)), 330);
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Stock> stock = new ArrayList<>();
        for (int i = 0; i < 12; i++) stock.add(new Stock(KitData.Icon.DIAMOND, w, 999_999_999L));
        reg.accept("Generator", () -> canvas(new View(art("erz", Quality.DIAMANT), w, GOLD, 10, 10, w, 1, w, stock, w + w, w, 999_999_999L, 999_999L, "Diamant",
                true, no, no, no, no, no, no)));
        reg.accept("Generator Geld", () -> canvas(new View(KitData.Sprite.GN_GELD, "Geld-Generator", STEEL, 1, 10, "0,5 $ pro Sekunde", 0.3,
                "1.234 / 3.600 $", List.of(), "Offline 50 %", null, -1, -1, null, false, no, null, no, null, no, no)));
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 16; i++) rows.add(new Row(art("xp", Quality.values()[i % 4]), w, CYAN, 10, "999.999.999 XP", 0.5, no));
        reg.accept("Generatoren", () -> island(rows, 16, w, no, no, no, no));
        reg.accept("Generatoren leer", () -> island(List.of(), 3, null, no, no, no, no));
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 9; i++) offers.add(new Offer(art("mob", Quality.values()[i % 4]), w, w + w, 999_999_999L, i % 3 == 0 ? w : null, i % 2 == 0, no));
        List<ShopRow> srows = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            srows.add(new ShopRow(art("erz"), w, k == 1 ? Kind.MONEY : Kind.ITEM, List.of(Material.COAL, Material.RAW_IRON, Material.RAW_GOLD,
                    Material.DIAMOND, Material.EMERALD, Material.REDSTONE, Material.LAPIS_LAZULI, Material.NETHERITE_SCRAP, Material.QUARTZ),
                    k == 1 ? "999,9 $ pro Sekunde" : "mehr ab Stufe 10", w + w, 999_999_999L, 99, k == 3 ? "ab Insel-Level 999" : null, k % 2 == 0, no));
        }
        reg.accept("Generator-Shop", () -> shopCanvas(new ShopView(1, srows, 999_999_999L, 99, 99, List.of(no, no, no, no), no, no)));
        reg.accept("Generator-Shop leer", () -> shopCanvas(new ShopView(3, List.of(), 0, 0, 3, List.of(no, no, no, no), no, no)));
        reg.accept("Generator-Juwelenshop", () -> shop(offers, "Juwelenshop", "999.999 Juwelen", JEWEL, no));
    }
}
