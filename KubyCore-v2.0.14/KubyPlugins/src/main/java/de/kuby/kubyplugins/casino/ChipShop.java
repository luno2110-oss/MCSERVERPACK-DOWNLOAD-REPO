package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.JewelShopScreen;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chip-Shop im Casino (v2.07, Wunsch des Besitzers): mit Casino-Chips besondere Kosmetik (eigene Casino-Hüte), Effekte
 * (Spuren, Kill- und Teleport-Effekte), Booster, Ränge und Titel kaufen, von 30.000 bis über 1,5 Mio. Chips.
 * Aufbau wie Juwelen- und XP-Shop: Übersicht mit Kategorie-Karten, Unterseiten mit „Kaufen“ → „Bestätigen“.
 *
 * <p>Kosmetik und Titel aus dem Chip-Shop gelten in allen Modi (eigene Liste chipshop.yml, nicht je Modus wie die
 * übrigen Freischaltungen). Ränge: wie im Juwelenshop über den Befehl aus jewelshop-smp.ranks, beim Aufstieg zahlt
 * man nur den Unterschied. Inhalt aus der config.yml (casino.chip-shop).
 */
public final class ChipShop {

    public enum State { MISSING, BUY, ARMED, OWNED, INCLUDED }

    static final int W = 390, HEIGHT = 280, PER_PAGE = 4;
    private static final long ARM_MS = 8000;

    private static final TextColor SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5),
            CHIP = TextColor.color(0xF2C94C), BUY = TextColor.color(0x6DE288), DARK = TextColor.color(0x201400),
            WHITE = TextColor.color(0xFCFCFC), TIP_CLICK = TextColor.color(0xFCFC54), GREEN = TextColor.color(0x5BED79);

    public record Category(String id, String name, String icon, String style, TextColor color, String line, int count) {}

    /** kind: COSMETIC, TAG, BOOSTER, RANK, COMMAND, MUSIC (Song für die Casino-Jukebox, v2.07). icon: hat:&lt;id&gt;, particle:&lt;name&gt;, sprite:&lt;JS_…&gt;, item:&lt;MATERIAL&gt;. */
    public record Offer(String id, String category, String name, String kind, String value, long price, String icon,
                        String line) {}

    /** price: was jetzt zu zahlen ist (Ränge beim Aufstieg nur der Unterschied); info: zweite Zeile. */
    public record Row(Offer offer, long price, State state, String info) {}

    public record View(long chips, Category category, List<Category> categories, List<Row> rows, int page, int pages,
                       int owned, int total) {}

    public interface Actions {
        Consumer<Player> home();

        Consumer<Player> category(String id);

        Consumer<Player> page(int page);

        Consumer<Player> buy(String id);
    }

    private final KubyPlugins plugin;
    private final File file;
    /** Freischaltungen (cosmetic:&lt;id&gt;, tag:&lt;id&gt;), für alle Modi gemeinsam. */
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, String> category = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();

    public ChipShop(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chipshop.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                owned.put(UUID.fromString(key), new HashSet<>(yml.getStringList(key)));
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
    }

    public synchronized boolean owns(UUID id, String key) {
        return owned.getOrDefault(id, Set.of()).contains(key);
    }

    public synchronized void grant(UUID id, String key) {
        owned.computeIfAbsent(id, k -> new HashSet<>()).add(key);
        save();
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        owned.forEach((id, set) -> {
            if (!set.isEmpty()) out.set(id.toString(), new ArrayList<>(set));
        });
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("chipshop.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // =================================================================================================================
    // Inhalt aus der Config
    // =================================================================================================================

    private static String part(String[] p, int i) {
        return i < p.length ? p[i].trim() : "";
    }

    private static TextColor color(String hex, TextColor def) {
        if (hex == null || hex.isBlank()) return def;
        TextColor c = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
        return c == null ? def : c;
    }

    public static List<Category> categories(FileConfiguration cfg) {
        List<Category> out = new ArrayList<>();
        for (String raw : cfg.getStringList("casino.chip-shop.kategorien")) {
            String[] p = raw.split(";");
            if (p.length < 2) continue;
            String id = part(p, 0).toLowerCase(Locale.ROOT);
            out.add(new Category(id, part(p, 1), part(p, 2).isEmpty() ? "item:CHEST" : part(p, 2),
                    part(p, 3).isEmpty() ? "gold" : part(p, 3), color(part(p, 4), CHIP), part(p, 5), offers(cfg, id).size()));
        }
        return out;
    }

    public static List<Offer> offers(FileConfiguration cfg, String cat) {
        List<Offer> out = new ArrayList<>();
        for (String raw : cfg.getStringList("casino.chip-shop.angebote." + cat)) {
            String[] p = raw.split(";");
            if (p.length < 5) continue;
            long price;
            try {
                price = Long.parseLong(part(p, 4).replace(".", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new Offer(part(p, 0).toLowerCase(Locale.ROOT), cat, part(p, 1), part(p, 2).toUpperCase(Locale.ROOT),
                    part(p, 3), Math.max(0, price), part(p, 5), part(p, 6)));
        }
        return out;
    }

    public static List<Offer> allOffers(FileConfiguration cfg) {
        List<Offer> out = new ArrayList<>();
        for (Category c : categories(cfg)) out.addAll(offers(cfg, c.id()));
        return out;
    }

    private Offer find(String id) {
        for (Offer o : allOffers(plugin.getConfig())) if (o.id().equals(id)) return o;
        return null;
    }

    /** Rang-Angebote in der Reihenfolge der Config (aufsteigend). */
    private List<Offer> ranks() {
        List<Offer> out = new ArrayList<>();
        for (Offer o : allOffers(plugin.getConfig())) if ("RANK".equals(o.kind())) out.add(o);
        return out;
    }

    /** Höchster Rang des Spielers unter den Rang-Angeboten (Index) oder -1. */
    private int ownedRank(Player p, List<Offer> ranks) {
        for (int i = ranks.size() - 1; i >= 0; i--) {
            String r = ranks.get(i).value().toLowerCase(Locale.ROOT);
            if (p.hasPermission("group." + r) || p.hasPermission("kuby.rank." + r)) return i;
        }
        return -1;
    }

    /**
     * Befehle eines Rangs: aus jewelshop-survival.ranks (commands, das Casino liegt in Survival), sonst aus
     * jewelshop-smp.ranks (command).
     */
    static List<String> rankCommands(FileConfiguration cfg, String rank) {
        ConfigurationSection s = cfg.getConfigurationSection("jewelshop-survival.ranks." + rank);
        if (s != null && !s.getStringList("commands").isEmpty()) return s.getStringList("commands");
        String one = cfg.getString("jewelshop-smp.ranks." + rank + ".command", "");
        return one.isBlank() ? List.of() : List.of(one);
    }

    /** Prüft ein Angebot (SELFTEST): Fehlermeldung oder null. */
    public static String check(KubyPlugins plugin, Offer o) {
        FileConfiguration cfg = plugin.getConfig();
        if (o.price() <= 0) return "Preis fehlt";
        switch (o.kind()) {
            case "COSMETIC" -> {
                var it = plugin.cosmetics().find(o.value());
                if (it == null) return "Kosmetik " + o.value() + " fehlt";
                if (it.currency() != de.kuby.kubyplugins.service.CosmeticService.Currency.CASINO)
                    return "Kosmetik " + o.value() + " ist nicht als CASINO eingetragen";
            }
            case "TAG" -> {
                if (plugin.tags().displayOf(o.value()) == null) return "Titel " + o.value() + " fehlt";
            }
            case "BOOSTER" -> {
                for (String b : o.value().split("\\+")) {
                    String[] x = b.split(":");
                    if (x.length != 3 || !(x[0].equals("xp") || x[0].equals("sell") || x[0].equals("cashback")))
                        return "Booster-Format art:Faktor:Minuten (art xp, sell oder cashback)";
                    try {
                        if (Double.parseDouble(x[1]) <= 1 || Integer.parseInt(x[2]) <= 0) return "Booster-Faktor/Minuten";
                    } catch (NumberFormatException e) {
                        return "Booster-Zahl " + b;
                    }
                }
            }
            case "RANK" -> {
                if (rankCommands(cfg, o.value()).isEmpty()) return "Rang " + o.value() + " fehlt in jewelshop-survival.ranks/jewelshop-smp.ranks";
            }
            case "COMMAND" -> {
                if (o.value().isBlank()) return "Befehl fehlt";
            }
            case "MUSIC" -> {
                if (plugin.casinoMusic() == null || !plugin.casinoMusic().exists(o.value())) return "Song " + o.value() + " fehlt";
            }
            default -> {
                return "unbekannte Art " + o.kind();
            }
        }
        return null;
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    public void open(Player p) {
        category.remove(p.getUniqueId());
        pages.remove(p.getUniqueId());
        show(p);
    }

    public void open(Player p, String cat) {
        category.put(p.getUniqueId(), cat);
        pages.put(p.getUniqueId(), 0);
        show(p);
    }

    private void show(Player p) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        UUID id = p.getUniqueId();
        Long at = armedAt.get(id);
        if (at != null && System.currentTimeMillis() - at > ARM_MS) {
            armed.remove(id);
            armedAt.remove(id);
        }
        UiDialog.show(p, canvas(view(p), actions()), HEIGHT);
    }

    public void forget(UUID id) {
        category.remove(id);
        pages.remove(id);
        armed.remove(id);
        armedAt.remove(id);
    }

    View view(Player p) {
        UUID id = p.getUniqueId();
        long chips = plugin.casino().chips(id);
        List<Category> cats = categories(plugin.getConfig());
        Category cat = null;
        for (Category c : cats) if (c.id().equals(category.get(id))) cat = c;
        List<Row> rows = new ArrayList<>();
        int page = 0, pageCount = 1;
        List<Offer> ranks = ranks();
        int myRank = ownedRank(p, ranks);
        if (cat != null) {
            List<Offer> all = offers(plugin.getConfig(), cat.id());
            pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
            page = Math.max(0, Math.min(pageCount - 1, pages.getOrDefault(id, 0)));
            for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
                rows.add(row(p, all.get(i), chips, ranks, myRank));
            }
        }
        int have = 0, total = 0;
        for (Offer o : allOffers(plugin.getConfig())) {
            if (!"COSMETIC".equals(o.kind()) && !"TAG".equals(o.kind())) continue;
            total++;
            if (owns(id, key(o))) have++;
        }
        return new View(chips, cat, cats, rows, page, pageCount, have, total);
    }

    private static String key(Offer o) {
        return ("COSMETIC".equals(o.kind()) ? "cosmetic:" : "tag:") + o.value();
    }

    private Row row(Player p, Offer o, long chips, List<Offer> ranks, int myRank) {
        UUID id = p.getUniqueId();
        long price = o.price();
        State st;
        String info;
        switch (o.kind()) {
            case "COSMETIC", "TAG" -> {
                boolean has = owns(id, key(o));
                st = has ? State.OWNED : null;
                info = has ? ("COSMETIC".equals(o.kind()) ? "Anlegen: /cosmetics · gilt in allen Modi" : "Aktivieren: /tags · gilt in allen Modi")
                        : "Gilt in allen Modi";
            }
            case "RANK" -> {
                int idx = ranks.indexOf(o);
                if (myRank >= 0 && idx == myRank) {
                    st = State.OWNED;
                    info = "Dein Rang";
                } else if (myRank > idx) {
                    st = State.INCLUDED;
                    info = "In deinem Rang enthalten";
                } else {
                    st = null;
                    if (myRank >= 0) price = Math.max(0, o.price() - ranks.get(myRank).price());
                    info = myRank >= 0 ? "Aufstieg: nur der Unterschied" : "Dauerhaft, für alle Modi";
                }
            }
            case "MUSIC" -> {
                boolean has = plugin.casinoMusic() != null && plugin.casinoMusic().owns(id, o.value());
                st = has ? State.OWNED : null;
                info = has ? "Abspielen: /music im Casino" : "Für die Casino-Jukebox (/music)";
            }
            case "BOOSTER" -> {
                st = null;
                String kind = o.value().split(":")[0];
                long min = plugin.booster().minutesLeft(id, kind);
                info = min > 0 ? "Läuft noch " + min + " min · verlängert" : "Startet sofort";
            }
            default -> {
                st = null;
                info = "";
            }
        }
        if (st == null) st = chips < price ? State.MISSING : o.id().equals(armed.get(id)) ? State.ARMED : State.BUY;
        return new Row(o, price, st, info);
    }

    private Actions actions() {
        return new Actions() {
            public Consumer<Player> home() {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    category.remove(pl.getUniqueId());
                    armed.remove(pl.getUniqueId());
                    show(pl);
                };
            }

            public Consumer<Player> category(String id) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    armed.remove(pl.getUniqueId());
                    open(pl, id);
                };
            }

            public Consumer<Player> page(int page) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.TICK);
                    pages.put(pl.getUniqueId(), Math.max(0, page));
                    armed.remove(pl.getUniqueId());
                    show(pl);
                };
            }

            public Consumer<Player> buy(String id) {
                return pl -> {
                    ChipShop.this.buy(pl, id);
                    show(pl);
                };
            }
        };
    }

    /** Erster Klick schaltet scharf, zweiter (innerhalb von 8 s) kauft: Chips weg, dann liefern (sonst zurück). */
    private void buy(Player p, String offerId) {
        UUID id = p.getUniqueId();
        Offer o = find(offerId);
        if (o == null) return;
        List<Offer> ranks = ranks();
        Row r = row(p, o, plugin.casino().chips(id), ranks, ownedRank(p, ranks));
        switch (r.state()) {
            case OWNED, INCLUDED -> {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                CasinoText.error(p, "Das hast du schon.");
                return;
            }
            case MISSING -> {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                CasinoText.error(p, "Dir fehlen noch " + CasinoText.num(r.price() - plugin.casino().chips(id)) + " Chips.");
                armed.remove(id);
                return;
            }
            case BUY -> {
                armed.put(id, o.id());
                armedAt.put(id, System.currentTimeMillis());
                Sfx.play(plugin, p, Sfx.Kind.CLICK);
                return;
            }
            default -> {
                // ARMED: jetzt kaufen
            }
        }
        armed.remove(id);
        armedAt.remove(id);
        if (!plugin.casino().spendChips(id, r.price())) {
            CasinoText.error(p, "So viele Chips hast du nicht.");
            return;
        }
        String err = give(p, o);
        if (err != null) {
            plugin.casino().giveChips(id, r.price());
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            CasinoText.error(p, err + " Deine Chips sind zurück.");
            return;
        }
        Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
        CasinoText.send(p, "<green>Gekauft: <white>" + o.name() + " <gray>(-" + CasinoText.num(r.price()) + " Chips)");
        if (r.price() >= plugin.getConfig().getLong("casino.chip-shop.ansage-ab", 500_000)) {
            Bukkit.broadcast(Msg.parse("<#F2C94C>✦ <white>" + p.getName() + " <gray>hat im Chip-Shop <#F2C94C>" + o.name()
                    + " <gray>gekauft!"));
        }
    }

    private String give(Player p, Offer o) {
        UUID id = p.getUniqueId();
        try {
            switch (o.kind()) {
                case "COSMETIC" -> {
                    if (plugin.cosmetics().find(o.value()) == null) return "Diese Kosmetik gibt es nicht.";
                    grant(id, "cosmetic:" + o.value());
                    Msg.send(p, "<gray>Anlegen unter <white>/cosmetics<gray> – gilt in allen Modi.");
                }
                case "TAG" -> {
                    if (plugin.tags().displayOf(o.value()) == null) return "Diesen Titel gibt es nicht.";
                    grant(id, "tag:" + o.value());
                    plugin.tags().setActive(id, o.value());
                    Msg.send(p, "<gray>Titel ist jetzt aktiv: " + plugin.tags().displayOf(o.value()) + " <gray>(ändern: <white>/tags<gray>)");
                }
                case "BOOSTER" -> {
                    for (String b : o.value().split("\\+")) {
                        String[] x = b.split(":");
                        plugin.booster().grant(id, x[0], Double.parseDouble(x[1]), Integer.parseInt(x[2]));
                    }
                }
                case "RANK" -> {
                    List<String> cmds = rankCommands(plugin.getConfig(), o.value());
                    if (cmds.isEmpty()) return "Für diesen Rang fehlt der Befehl.";
                    for (String cmd : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                }
                case "COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), o.value().replace("%player%", p.getName()));
                case "MUSIC" -> {
                    if (plugin.casinoMusic() == null || !plugin.casinoMusic().exists(o.value())) return "Diesen Song gibt es nicht.";
                    if (!plugin.casinoMusic().unlock(p, o.value(), "Chip-Shop")) return "Diesen Song hast du schon.";
                }
                default -> {
                    return "Diese Ware ist falsch eingetragen.";
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Chip-Shop: Angebot " + o.id() + " fehlerhaft: " + e.getMessage());
            return "Diese Ware ist falsch eingetragen.";
        }
        return null;
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    private static void center(Canvas c, int cx, int y, List<Span> spans) {
        int w = 0;
        for (Span s : spans) w += Canvas.width(Canvas.clean(s.text()), s.bold());
        c.text(cx - (w + 1) / 2, y, spans);
    }

    /** Bild 32 x 32 mit linker oberer Ecke (x, y): Hut, Partikel, Sprite (mittig) oder Item. */
    static void icon(Canvas c, int x, int y, String spec) {
        String[] p = spec.split(":", 2);
        String kind = p.length > 1 ? p[0].toLowerCase(Locale.ROOT) : "item";
        String name = (p.length > 1 ? p[1] : p[0]).toUpperCase(Locale.ROOT);
        try {
            switch (kind) {
                case "hat" -> c.icon(x, y, KitData.Icon.valueOf("HAT_" + name), true);
                case "particle" -> c.icon(x, y, KitData.Icon.valueOf("P_" + name), true);
                case "sprite" -> {
                    KitData.Sprite s = KitData.Sprite.valueOf(name);
                    c.sprite(x + 16 - (s.width + 1) / 2, y + 16 - (s.height + 1) / 2, s);
                }
                default -> {
                    Material m = Material.matchMaterial(name);
                    c.item(x, y, m == null ? Material.CHEST : m, true, KitData.Icon.CHEST);
                }
            }
        } catch (IllegalArgumentException e) {
            c.icon(x, y, KitData.Icon.STAR, true);
        }
    }

    /** Rechts im Kopf: Chip, „Deine / Chips“, Anzahl. */
    private static void balance(Canvas c, long chips) {
        String n = CasinoText.num(chips);
        int nw = Canvas.width(n, true);
        int nx = 380 - nw;
        int lx = Math.min(330, nx - 38);
        c.smallIcon(lx - 19, 11, KitData.SmallIcon.C_PINK);
        c.text(lx, 9, "Deine", SUB, false);
        c.text(lx, 19, "Chips", SUB, false);
        c.text(nx, 14, n, CHIP, true);
    }

    public static Canvas canvas(View v, Actions a) {
        return v.category() == null ? home(v, a) : rows(v, a);
    }

    private static Canvas home(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Chip-Shop", CHIP);
        c.box(0, 0, W, 46, KitData.Box.CASINO_DARK);
        icon(c, 6, 7, "item:GOLD_BLOCK");
        c.sprite(46, 8, KitData.Sprite.XS_CHIP_TITLE);
        c.text(47, 30, "→ Besonderes für deine Chips · gilt in allen Modi", SUB, false);
        balance(c, v.chips());
        int n = v.categories().size();
        int cols = 3, cw = 126, ch = 62, gap = 6;
        for (int i = 0; i < n; i++) {
            Category cat = v.categories().get(i);
            int x = (i % cols) * (cw + gap), y = 50 + (i / cols) * (ch + 4);
            if (n == 5 && i >= 3) x += (cw + gap) / 2;   // zweite Reihe mittig
            card(c, a, x, y, cw, ch, cat);
        }
        int y = 50 + ((n + cols - 1) / cols) * (ch + 4) + 2;
        c.box(0, y, W, 22, KitData.Box.CASINO_DARK);
        c.text(9, y + 7, List.of(Span.bold("Sammlung: ", CHIP), Span.of(v.owned() + " / " + v.total() + " Casino-Kosmetik und Titel", DESC)));
        c.textRight(381, y + 7, "Chips gibt es an der Kasse", SUB, false);
        return c;
    }

    private static void card(Canvas c, Actions a, int x, int y, int w, int h, Category cat) {
        KitData.Box b = JewelShopScreen.box(cat.style());
        c.box(x, y, w, h, b);
        icon(c, x + 6, y + 6, cat.icon());
        c.text(x + 43, y + 7, Canvas.fit(cat.name().toUpperCase(Locale.GERMAN), true, w - 48), cat.color(), true);
        List<String> lines = Canvas.wrap(cat.line(), w - 49);
        for (int i = 0; i < Math.min(2, lines.size()); i++) c.text(x + 43, y + 18 + 10 * i, Canvas.fit(lines.get(i), false, w - 49), DESC, false);
        c.box(x + 5, y + h - 21, w - 10, 17, b, 1);
        center(c, x + w / 2, y + h - 16, "ANSEHEN", cat.color(), true);
        c.text(x + w - 16, y + h - 16, "›", cat.color(), true);
        if (a != null) c.action(x, y, w, h, a.category(cat.id()), tip(cat.name(), cat.line() + " · " + cat.count() + " Angebote", "Klick: öffnen"));
    }

    private static Canvas rows(View v, Actions a) {
        Category cat = v.category();
        Canvas c = new Canvas(W).heading("Chip-Shop", CHIP);
        c.box(0, 0, W, 46, KitData.Box.CASINO_DARK);
        c.sprite(10, 9, KitData.Sprite.JS_BACK);
        if (a != null) c.action(10, 9, 22, 22, a.home(), Component.text("Zurück", WHITE));
        c.text(42, 11, cat.name().toUpperCase(Locale.GERMAN), cat.color(), true);
        c.text(42, 26, Canvas.fit(cat.line(), false, 240), SUB, false);
        balance(c, v.chips());
        int y = 49;
        for (Row r : v.rows()) {
            Offer o = r.offer();
            KitData.Box b = JewelShopScreen.box(cat.style());
            c.box(0, y, W, 50, b);
            icon(c, 8, y + 9, o.icon());
            c.text(48, y + 9, Canvas.fit(o.name().toUpperCase(Locale.GERMAN), true, 216), cat.color(), true);
            c.text(48, y + 21, Canvas.fit(o.line(), false, 216), DESC, false);
            if (!r.info().isEmpty()) c.text(48, y + 33, Canvas.fit(r.info(), false, 216), SUB, false);
            if (r.state() != State.OWNED && r.state() != State.INCLUDED) price(c, 325, y + 7, r.price());
            button(c, a, 270, y + 23, 110, r, v.chips());
            y += 54;
        }
        if (v.pages() > 1) {
            int py = 264;
            boolean prev = v.page() > 0, next = v.page() < v.pages() - 1;
            String label = "Seite " + (v.page() + 1) + " / " + v.pages();
            center(c, W / 2, py + 4, label, SUB, false);
            int lw = Canvas.width(label, false);
            int lx = W / 2 - lw / 2 - 30, rx = W / 2 + lw / 2 + 6;
            c.smallButton(lx, py, 24, KitData.Small.DARK, "‹", prev ? WHITE : TextColor.color(0x54575E), true);
            c.smallButton(rx, py, 24, KitData.Small.DARK, "›", next ? WHITE : TextColor.color(0x54575E), true);
            if (a != null) {
                if (prev) c.action(lx, py, 24, 16, a.page(v.page() - 1), Component.text("Vorherige Seite", WHITE));
                if (next) c.action(rx, py, 24, 16, a.page(v.page() + 1), Component.text("Nächste Seite", WHITE));
            }
        }
        return c;
    }

    /** Chip und Preis, zusammen mittig um cx. */
    private static void price(Canvas c, int cx, int y, long price) {
        String n = CasinoText.num(price);
        int w = Canvas.width(n, true);
        int adv = KitData.SmallIcon.C_PINK.advance + 3;
        int x = cx - (adv + w + 1) / 2;
        c.smallIcon(x, y + 1, KitData.SmallIcon.C_PINK);
        c.text(x + adv, y + 4, n, CHIP, true);
    }

    private static void button(Canvas c, Actions a, int x, int y, int w, Row r, long chips) {
        int cx = x + w / 2;
        switch (r.state()) {
            case MISSING -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, List.of(Span.of("Dir fehlen ", DESC), Span.of(CasinoText.num(r.price() - chips), CHIP)));
            }
            case BUY -> {
                c.box(x, y, w, 18, KitData.Box.JS_GREEN, 1);
                center(c, cx, y + 5, "KAUFEN", BUY, true);
            }
            case ARMED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GOLD, 1);
                center(c, cx, y + 5, "BESTÄTIGEN", CHIP, true);
            }
            case OWNED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, "✔ Besitzt du", GREEN, false);
            }
            case INCLUDED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, "Enthalten", SUB, false);
            }
        }
        if (a != null && r.state() != State.OWNED && r.state() != State.INCLUDED) c.action(x, y, w, 18, a.buy(r.offer().id()), tipFor(r));
    }

    private static Component tipFor(Row r) {
        Offer o = r.offer();
        TextComponent.Builder b = Component.text();
        b.append(Component.text(o.name(), WHITE).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(o.line(), 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        if (!r.info().isEmpty()) b.append(Component.newline()).append(Component.text(r.info(), SUB));
        b.append(Component.newline()).append(Component.newline());
        b.append(Component.text(switch (r.state()) {
            case MISSING -> "Nicht genug Chips";
            case BUY -> "Klick: kaufen (danach bestätigen)";
            case ARMED -> "Nochmal klicken: jetzt kaufen";
            default -> "";
        }, TIP_CLICK));
        return b.build();
    }

    private static Component tip(String title, String text, String click) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(title, WHITE).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(text, 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        b.append(Component.newline()).append(Component.newline()).append(Component.text(click, TIP_CLICK));
        return b.build();
    }

    /** Ansicht für den SELFTEST und Vorschau-Bilder: Übersicht (cat null) oder eine Kategorie, Seite 0. */
    public static View sample(FileConfiguration cfg, String cat, long chips) {
        List<Category> cats = categories(cfg);
        Category sel = null;
        for (Category c : cats) if (c.id().equals(cat)) sel = c;
        List<Row> rows = new ArrayList<>();
        int pageCount = 1;
        if (sel != null) {
            List<Offer> all = offers(cfg, sel.id());
            pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
            for (int i = 0; i < Math.min(PER_PAGE, all.size()); i++) {
                Offer o = all.get(i);
                State st = i == 0 && ("COSMETIC".equals(o.kind()) || "TAG".equals(o.kind()) || "RANK".equals(o.kind())) ? State.OWNED
                        : chips < o.price() ? State.MISSING : i == 1 ? State.ARMED : State.BUY;
                rows.add(new Row(o, o.price(), st, st == State.OWNED ? "Gilt in allen Modi" : "Startet sofort"));
            }
        }
        return new View(chips, sel, cats, rows, 0, pageCount, 3, 14);
    }
}
