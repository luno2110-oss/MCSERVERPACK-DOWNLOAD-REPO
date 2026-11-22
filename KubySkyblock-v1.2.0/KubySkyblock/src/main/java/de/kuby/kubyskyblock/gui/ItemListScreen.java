package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.ProgressService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CreativeCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Itemliste (Checkliste) von Skyblock als Fenster (v2.05, ersetzt die Truhe): Fortschritt mit Meilensteinen,
 * Reiter je Kategorie (wie im Kreativ-Inventar), Filter Alle/Offen/Gesammelt und ein Raster mit den echten
 * Item-Bildern. Gesammelte Items in Farbe mit grünem Punkt, offene als dunkle Silhouette (man sieht, was noch fehlt).
 * Tooltip: Name in der Sprache des Spielers, gesammelt oder offen. Ohne Pack bleibt die Truhe (ChecklistMenu).
 */
public final class ItemListScreen {

    /** Reiter: Kategorie des Kreativ-Inventars (null = alle), Name, Icon. */
    public enum Tab {
        ALL(null, "Alle", "All", Material.BOOK),
        BUILDING(CreativeCategory.BUILDING_BLOCKS, "Bauen", "Building", Material.BRICKS),
        DECO(CreativeCategory.DECORATIONS, "Deko", "Decoration", Material.POPPY),
        REDSTONE(CreativeCategory.REDSTONE, "Redstone", "Redstone", Material.REDSTONE),
        TRANSPORT(CreativeCategory.TRANSPORTATION, "Transport", "Transport", Material.MINECART),
        FOOD(CreativeCategory.FOOD, "Essen", "Food", Material.APPLE),
        TOOLS(CreativeCategory.TOOLS, "Werkzeug", "Tools", Material.IRON_PICKAXE),
        COMBAT(CreativeCategory.COMBAT, "Kampf", "Combat", Material.IRON_SWORD),
        BREWING(CreativeCategory.BREWING, "Tränke", "Brewing", Material.POTION),
        MISC(CreativeCategory.MISC, "Sonstiges", "Misc", Material.LAVA_BUCKET);

        final CreativeCategory cat;
        final String de, en;
        final Material icon;

        Tab(CreativeCategory cat, String de, String en, Material icon) {
            this.cat = cat;
            this.de = de;
            this.en = en;
            this.icon = icon;
        }

        public String label(boolean english) {
            return english ? en : de;
        }
    }

    public enum Filter { ALL, OPEN, DONE }

    static final int W = 390, HEIGHT = 300, COLS = 16, ROWS = 6, PER_PAGE = COLS * ROWS, CELL = 24;
    static final TextColor SKY = TextColor.color(0x9AD8FF), CLOUD = TextColor.color(0xF2FAFF), MIST = TextColor.color(0x8AA8C0),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), XP = TextColor.color(0xC8F870),
            DIM = TextColor.color(0x5E7488), TIP_CLICK = TextColor.color(0xFCFC54);
    /** Offene Items: Textur stark abgedunkelt (Silhouette). */
    static final TextColor SHADOW = TextColor.color(0x2C3A48), FULL = TextColor.color(0xFFFFFF), TAB_OFF = TextColor.color(0x8C9CB0);

    /** Ein Feld: Item und ob gesammelt. */
    public record Cell(Material item, boolean done) {}

    /** Zähler je Reiter: gesammelt / alle. */
    public record TabCount(Tab tab, int done, int all) {}

    public record View(boolean english, int collected, int items, int percent, Tab tab, Filter filter, List<TabCount> tabs,
                       List<Cell> cells, int page, int pages, int nextMilestone, int missingForNext, long rewardMoney,
                       long rewardXp) {}

    public interface Actions {
        Consumer<Player> tab(Tab tab);

        Consumer<Player> filter(Filter filter);

        Consumer<Player> page(int page);

        Consumer<Player> achievements();
    }

    private record State(Tab tab, Filter filter, int page) {}

    private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();
    private static Map<Material, Tab> tabs;

    private final KubyPlugins core;

    public ItemListScreen(KubyPlugins core) {
        this.core = core;
    }

    public static void forget(UUID id) {
        STATE.remove(id);
    }

    /** Reiter eines Items (Kreativ-Kategorie; ohne Kategorie: Sonstiges). Einmal berechnet. */
    static synchronized Tab tabOf(Material m) {
        if (tabs == null) {
            Map<Material, Tab> map = new EnumMap<>(Material.class);
            for (Material x : KubySkyblock.get().progress().allItems()) {
                Tab t = Tab.MISC;
                try {
                    CreativeCategory c = x.getCreativeCategory();
                    for (Tab tab : Tab.values()) if (tab.cat != null && tab.cat == c) t = tab;
                } catch (RuntimeException ignored) {
                    // ohne Kategorie: Sonstiges
                }
                map.put(x, t);
            }
            tabs = map;
        }
        return tabs.getOrDefault(m, Tab.MISC);
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    public void open(Player p) {
        if (!core.uiPack().active(p)) {
            new ChecklistMenu(core, 0).open(p);
            return;
        }
        UiDialog.show(p, canvas(view(p), actions()), HEIGHT);
    }

    View view(Player p) {
        UUID id = p.getUniqueId();
        ProgressService prog = KubySkyblock.get().progress();
        boolean en = core.playerSettings().english(id);
        State st = STATE.getOrDefault(id, new State(Tab.ALL, Filter.ALL, 0));
        Set<Material> got = prog.collected(id);
        List<Material> all = prog.allItems();
        Map<Tab, int[]> counts = new EnumMap<>(Tab.class);
        for (Tab t : Tab.values()) counts.put(t, new int[2]);
        List<Cell> list = new ArrayList<>();
        for (Material m : all) {
            Tab t = tabOf(m);
            boolean done = got.contains(m);
            for (Tab c : new Tab[]{Tab.ALL, t}) {
                counts.get(c)[1]++;
                if (done) counts.get(c)[0]++;
            }
            if (st.tab() != Tab.ALL && st.tab() != t) continue;
            if (st.filter() == Filter.OPEN && done || st.filter() == Filter.DONE && !done) continue;
            list.add(new Cell(m, done));
        }
        List<TabCount> tc = new ArrayList<>();
        for (Tab t : Tab.values()) tc.add(new TabCount(t, counts.get(t)[0], counts.get(t)[1]));
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(pages - 1, st.page()));
        List<Cell> cells = list.subList(page * PER_PAGE, Math.min(list.size(), (page + 1) * PER_PAGE));
        int pct = prog.percent(id);
        int next = 0;
        for (int th : ProgressService.milestones()) {
            if (pct < th) {
                next = th;
                break;
            }
        }
        int missing = next == 0 ? 0 : Math.max(0, (int) Math.ceil(all.size() * next / 100.0) - got.size());
        return new View(en, got.size(), all.size(), pct, st.tab(), st.filter(), tc, new ArrayList<>(cells), page, pages,
                next, missing, 500L * next, 25L * next);
    }

    private Actions actions() {
        return new Actions() {
            public Consumer<Player> tab(Tab tab) {
                return pl -> {
                    State s = STATE.getOrDefault(pl.getUniqueId(), new State(Tab.ALL, Filter.ALL, 0));
                    STATE.put(pl.getUniqueId(), new State(tab, s.filter(), 0));
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    open(pl);
                };
            }

            public Consumer<Player> filter(Filter filter) {
                return pl -> {
                    State s = STATE.getOrDefault(pl.getUniqueId(), new State(Tab.ALL, Filter.ALL, 0));
                    STATE.put(pl.getUniqueId(), new State(s.tab(), filter, 0));
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    open(pl);
                };
            }

            public Consumer<Player> page(int page) {
                return pl -> {
                    State s = STATE.getOrDefault(pl.getUniqueId(), new State(Tab.ALL, Filter.ALL, 0));
                    STATE.put(pl.getUniqueId(), new State(s.tab(), s.filter(), Math.max(0, page)));
                    Sfx.play(core, pl, Sfx.Kind.TICK);
                    open(pl);
                };
            }

            public Consumer<Player> achievements() {
                return pl -> new SkyScreen(core).open(pl);
            }
        };
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    /** Name eines Items für den Tooltip: übersetzt vom Client, ohne Server ein lesbarer Name. */
    static Component name(Material m) {
        try {
            return Component.translatable(m.translationKey());
        } catch (RuntimeException | LinkageError e) {
            String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return Component.text(Character.toUpperCase(n.charAt(0)) + n.substring(1));
        }
    }

    public static Canvas canvas(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W).heading(en ? "Item list" : "Itemliste", SKY);
        // Kopf
        c.box(0, 0, W, 46, KitData.Box.CLOUD);
        c.item(8, 7, Material.KNOWLEDGE_BOOK, true, KitData.Icon.BOOK_PLAIN);
        c.headline(48, 9, en ? "Item list" : "Itemliste", SKY);
        c.text(48, 31, en ? "Collect every item once – milestones give rewards"
                : "Sammle jedes Item einmal – Meilensteine geben Belohnungen", MIST, false);
        c.textRight(380, 9, Msg.coins(v.collected()) + " / " + Msg.coins(v.items()), CLOUD, true);
        c.textRight(380, 21, Canvas.caps(en ? "collected" : "gesammelt"), MIST, false);
        // Fortschritt mit Meilensteinen
        int by = 52, bx = 8, bw = 270;
        c.box(0, 48, W, 26, KitData.Box.CLOUD_CARD);
        c.bar(bx, by + 4, bw, v.items() == 0 ? 0 : v.collected() / (double) v.items(), KitData.Fill.CYAN);
        for (int th : ProgressService.milestones()) {
            int mx = bx + (int) Math.round(bw * th / 100.0);
            boolean reached = v.percent() >= th;
            String label = th + "%";
            int lw = Canvas.width(label, false);
            c.text(Math.min(bx + bw - lw, Math.max(bx, mx - lw / 2)), by + 13, label, reached ? GOLD : DIM, false);
        }
        c.textRight(380, by + 3, v.percent() + " %", CLOUD, true);
        c.textRight(380, by + 13, v.nextMilestone() == 0 ? (en ? "all done!" : "alles!") : (en ? "next " : "nächster ") + v.nextMilestone() + " %",
                MIST, false);
        if (a != null) {
            c.action(0, 48, W, 26, null, tip(en ? "Milestones" : "Meilensteine", v.nextMilestone() == 0
                    ? (en ? "You reached every milestone." : "Du hast alle Meilensteine erreicht.")
                    : (en ? "At " + v.nextMilestone() + " %: " + Msg.coins(v.rewardMoney()) + " $, " + Msg.coins(v.rewardXp())
                    + " XP and 3 loot rolls. Missing: " + v.missingForNext() + " items."
                    : "Bei " + v.nextMilestone() + " %: " + Msg.coins(v.rewardMoney()) + " $, " + Msg.coins(v.rewardXp())
                    + " XP und 3 Loot-Lose. Es fehlen noch " + v.missingForNext() + " Items."), null));
        }
        // Reiter: Icons in einer Leiste, der aktive hell mit Strich darunter, die anderen gedämpft
        int ty = 78;
        c.box(0, ty, 298, 27, KitData.Box.CLOUD_CARD);
        int tx = 4;
        for (TabCount t : v.tabs()) {
            boolean on = t.tab() == v.tab();
            c.item(tx + 6, ty + 2, t.tab().icon, false, KitData.Icon.BOOK_PLAIN, on ? FULL : TAB_OFF);
            if (on) c.barFlat(tx + 4, ty + 18, 20, 1, KitData.Fill.CYAN);
            if (a != null) {
                c.action(tx, ty, 29, 27, on ? null : a.tab(t.tab()), tip(t.tab().label(en),
                        (en ? "Collected " : "Gesammelt ") + Msg.coins(t.done()) + " / " + Msg.coins(t.all()),
                        on ? null : en ? "Click: show" : "Klick: anzeigen"));
            }
            tx += 29;
        }
        // Filter
        int fx = 304;
        String[] fl = en ? new String[]{"All", "Open", "Got"} : new String[]{"Alle", "Offen", "Hab"};
        for (int i = 0; i < 3; i++) {
            Filter f = Filter.values()[i];
            boolean on = v.filter() == f;
            int w = i == 0 ? 26 : 30;
            c.smallButton(fx, ty + 5, w, on ? KitData.Small.SET_BLUE : KitData.Small.DARK, fl[i], on ? CLOUD : MIST, on);
            if (a != null && !on) {
                c.action(fx, ty + 5, w, 16, a.filter(f), Component.text(switch (f) {
                    case ALL -> en ? "Show all items" : "Alle Items zeigen";
                    case OPEN -> en ? "Only items you are missing" : "Nur Items, die dir fehlen";
                    case DONE -> en ? "Only collected items" : "Nur gesammelte Items";
                }, CLOUD));
            }
            fx += w + 2;
        }
        // Raster
        int gy = 109, gx = (W - COLS * CELL) / 2;
        c.box(0, gy - 2, W, ROWS * CELL + 4, KitData.Box.CLOUD_CARD);
        for (int i = 0; i < v.cells().size(); i++) {
            Cell cell = v.cells().get(i);
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            c.item(x + 4, y + 4, cell.item(), false, KitData.Icon.BUNDLE, cell.done() ? FULL : SHADOW);
            if (a != null) {
                TextComponent.Builder b = Component.text();
                b.append(name(cell.item()).color(cell.done() ? CLOUD : MIST).decorate(TextDecoration.BOLD));
                b.append(Component.newline()).append(Component.text(cell.done() ? (en ? "✔ Collected" : "✔ Gesammelt")
                        : en ? "Not collected yet: pick it up or craft it" : "Noch offen: aufheben oder herstellen",
                        cell.done() ? GREEN : MIST));
                c.action(x, y, CELL, CELL, null, b.build());
            }
        }
        if (v.cells().isEmpty()) {
            String t = v.filter() == Filter.OPEN ? (en ? "Nothing missing here – well done!" : "Hier fehlt nichts mehr – stark!")
                    : en ? "Nothing collected here yet." : "Hier ist noch nichts gesammelt.";
            int tw = Canvas.width(t, false);
            c.text(W / 2 - tw / 2, gy + ROWS * CELL / 2 - 4, t, MIST, false);
        }
        // Fuß: Seiten und Erfolge
        int fy = gy + ROWS * CELL + 6;
        boolean prev = v.page() > 0, next = v.page() < v.pages() - 1;
        c.smallButton(0, fy, 24, KitData.Small.DARK, "‹", prev ? CLOUD : DIM, true);
        String label = (en ? "Page " : "Seite ") + (v.page() + 1) + " / " + v.pages();
        c.text(30, fy + 4, label, MIST, false);
        int lx = 30 + Canvas.width(label, false) + 6;
        c.smallButton(lx, fy, 24, KitData.Small.DARK, "›", next ? CLOUD : DIM, true);
        c.smallButton(W - 80, fy, 80, KitData.Small.SET_BLUE, en ? "Achievements" : "Erfolge", CLOUD, false);
        if (a != null) {
            if (prev) c.action(0, fy, 24, 16, a.page(v.page() - 1), Component.text(en ? "Previous page" : "Vorherige Seite", CLOUD));
            if (next) c.action(lx, fy, 24, 16, a.page(v.page() + 1), Component.text(en ? "Next page" : "Nächste Seite", CLOUD));
            c.action(W - 80, fy, 80, 16, a.achievements(), Component.text(en ? "Achievements and milestones" : "Erfolge und Meilensteine", CLOUD));
        }
        return c;
    }

    private static Component tip(String title, String text, String click) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, CLOUD).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(l, MIST));
        if (click != null) b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    /** Selbsttest: volle Seite halb gesammelt, englisch, leere Liste. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Actions no = new Actions() {
            public Consumer<Player> tab(Tab tab) { return p -> { }; }
            public Consumer<Player> filter(Filter filter) { return p -> { }; }
            public Consumer<Player> page(int page) { return p -> { }; }
            public Consumer<Player> achievements() { return p -> { }; }
        };
        reg.accept("Itemliste", () -> canvas(sample(false, Filter.ALL), no));
        reg.accept("Itemliste englisch", () -> canvas(sample(true, Filter.OPEN), no));
        reg.accept("Itemliste leer", () -> canvas(new View(false, 0, 0, 0, Tab.ALL, Filter.DONE, List.of(), List.of(), 0, 1, 10, 0, 5000, 250), no));
    }

    /** Beispiel-Ansicht ohne Spieler: die ersten Items, jedes dritte offen. */
    public static View sample(boolean en, Filter filter) {
        List<Cell> cells = new ArrayList<>();
        int i = 0;
        for (Material m : Material.values()) {
            if (cells.size() >= PER_PAGE) break;
            if (m.isLegacy() || m.name().endsWith("AIR")) continue;
            String n = m.name();
            if (n.endsWith("_WALL_SIGN") || n.endsWith("_WALL_BANNER") || n.startsWith("POTTED_") || n.endsWith("_WALL_HEAD")
                    || n.endsWith("_WALL_FAN") || n.endsWith("_WALL_TORCH") || n.endsWith("_WALL_HANGING_SIGN")) continue;
            cells.add(new Cell(m, i++ % 3 != 0));
        }
        List<TabCount> tc = new ArrayList<>();
        for (Tab t : Tab.values()) tc.add(new TabCount(t, 123, 456));
        return new View(en, 640, 1310, 48, Tab.ALL, filter, tc, cells, 0, 14, 50, 15, 25_000, 1_250);
    }
}
