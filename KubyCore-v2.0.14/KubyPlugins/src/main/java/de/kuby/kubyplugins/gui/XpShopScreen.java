package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.listener.AreaToolListener;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.StatsService;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * XP-Shop (v2.05) im Stil des Juwelenshops: Übersicht mit Kategorie-Karten, Unterseiten mit Zeilen (Icon, Name,
 * Beschreibung, Preis in XP, Knopf „Kaufen“, zweiter Klick bestätigt). Nur in SMP und Skyblock (dort gibt es XP),
 * je Modus eigene Kategorien und Angebote aus der config.yml (xp-shop.kategorien / xp-shop.angebote):
 * Geld, Juwelen, Crate-Keys (Menge 1/5/10/25), Booster, OP-Tools (3x3- und 5x5-Spitzhacken), in SMP dazu PvP, Bauen,
 * Team und Survival, in Skyblock die Insel.
 *
 * <p>Ohne Grafik-Pack öffnet sich das Truhen-Menü (XpShopMenu) mit denselben Angeboten.
 */
public final class XpShopScreen {

    public enum State { MISSING, BUY, ARMED }

    static final int W = 390, HEIGHT = 280, PER_PAGE = 4;
    static final int[] AMOUNTS = {1, 5, 10, 25};
    private static final long ARM_MS = 8000;

    private static final TextColor SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5),
            LIME = TextColor.color(0xB5EC3C), GOLD = TextColor.color(0xFCD45D), BUY = TextColor.color(0x6DE288),
            DARK = TextColor.color(0x201400), WHITE = TextColor.color(0xFCFCFC), TIP_CLICK = TextColor.color(0xFCFC54),
            BOOST = TextColor.color(0xCD89FC);

    /** Kategorie: count = Anzahl Angebote. */
    public record Category(String id, String name, String nameEn, String icon, String style, TextColor color,
                           String line, String lineEn, int count) {
        public String name(boolean en) {
            return en ? nameEn : name;
        }

        public String line(boolean en) {
            return en ? lineEn : line;
        }
    }

    /** Angebot: kind COINS, JEWELS, KEY, BOOSTER, ITEM, TEAMBANK, TAG, COMMAND. */
    public record Offer(String id, String category, String name, String nameEn, String kind, String value, long price,
                        String icon, String line, String lineEn) {
        public String name(boolean en) {
            return en ? nameEn : name;
        }

        public String line(boolean en) {
            return en ? lineEn : line;
        }
    }

    /** Zeile: price = Preis mal Menge, info = zweite Zeile (Bestand, Restzeit …). */
    public record Row(Offer offer, long price, State state, int amount, String info) {}

    /** category null = Übersicht. boosters: laufende persönliche Booster (oder leer). */
    public record View(String mode, boolean english, long xp, Category category, List<Category> categories,
                       List<Row> rows, int page, int pages, String boosters) {}

    public interface Actions {
        Consumer<Player> home();

        Consumer<Player> category(String id);

        Consumer<Player> page(int page);

        Consumer<Player> buy(String id);

        Consumer<Player> amount(String id, int amount);
    }

    private final KubyPlugins plugin;
    private final Map<UUID, String> category = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> amounts = new HashMap<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();

    public XpShopScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Daten aus der Config
    // =================================================================================================================

    private static String part(String[] p, int i) {
        return i < p.length ? p[i].trim() : "";
    }

    private static String[] names(String s) {
        String[] n = s.split("\\|", 2);
        return new String[]{n[0].trim(), n.length > 1 ? n[1].trim() : n[0].trim()};
    }

    private static TextColor color(String hex, TextColor def) {
        if (hex == null || hex.isBlank()) return def;
        TextColor c = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
        return c == null ? def : c;
    }

    public static List<Category> categories(FileConfiguration cfg, String mode) {
        List<Category> out = new ArrayList<>();
        for (String raw : cfg.getStringList("xp-shop.kategorien." + mode)) {
            String[] p = raw.split(";");
            if (p.length < 2) continue;
            String id = part(p, 0).toLowerCase(Locale.ROOT);
            String[] n = names(part(p, 1)), l = names(part(p, 5));
            out.add(new Category(id, n[0], n[1], part(p, 2).isEmpty() ? "CHEST" : part(p, 2),
                    part(p, 3).isEmpty() ? "gold" : part(p, 3), color(part(p, 4), GOLD), l[0], l[1],
                    offers(cfg, mode, id).size()));
        }
        return out;
    }

    public static List<Offer> offers(FileConfiguration cfg, String mode, String cat) {
        List<Offer> out = new ArrayList<>();
        for (String raw : cfg.getStringList("xp-shop.angebote." + mode + "." + cat)) {
            String[] p = raw.split(";");
            if (p.length < 5) continue;
            long price;
            try {
                price = Long.parseLong(part(p, 4).replace(".", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            String[] n = names(part(p, 1)), l = names(part(p, 6));
            out.add(new Offer(part(p, 0).toLowerCase(Locale.ROOT), cat, n[0], n[1], part(p, 2).toUpperCase(Locale.ROOT),
                    part(p, 3), Math.max(0, price), part(p, 5).isEmpty() ? "CHEST" : part(p, 5), l[0], l[1]));
        }
        return out;
    }

    /** Alle Angebote eines Modus (alle Kategorien). */
    public static List<Offer> allOffers(FileConfiguration cfg, String mode) {
        List<Offer> out = new ArrayList<>();
        for (Category c : categories(cfg, mode)) out.addAll(offers(cfg, mode, c.id()));
        return out;
    }

    private Offer find(String mode, String id) {
        for (Offer o : allOffers(plugin.getConfig(), mode)) if (o.id().equals(id)) return o;
        return null;
    }

    /**
     * Items eines ITEM-Angebots: MATERIAL[:Anzahl][@verzauberung=stufe,...], mehrere mit +. area=3/5 macht ein
     * Flächen-Werkzeug. Wirft IllegalArgumentException mit Grund (SELFTEST prüft alle Angebote).
     */
    public static List<ItemStack> items(Offer o) {
        List<ItemStack> out = new ArrayList<>();
        String[] parts = o.value().split("\\+");
        for (String part : parts) {
            String[] e = part.split("@", 2);
            String[] m = e[0].split(":");
            Material mat = Material.matchMaterial(m[0].trim());
            if (mat == null || !mat.isItem()) throw new IllegalArgumentException("unbekanntes Item " + m[0].trim());
            int n;
            try {
                n = m.length > 1 ? Integer.parseInt(m[1].trim()) : 1;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Anzahl " + m[1]);
            }
            if (n < 1 || n > mat.getMaxStackSize()) throw new IllegalArgumentException("Anzahl " + n + " bei " + mat);
            ItemStack it = new ItemStack(mat, n);
            if (e.length > 1) {
                ItemMeta meta = it.getItemMeta();
                int area = 0;
                List<String> ench = new ArrayList<>();
                for (String en : e[1].split(",")) {
                    String[] kv = en.split("=", 2);
                    String k = kv[0].trim().toLowerCase(Locale.ROOT);
                    int lvl;
                    try {
                        lvl = kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 1;
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Stufe " + en);
                    }
                    if (k.equals("area")) {
                        if (lvl != 3 && lvl != 5) throw new IllegalArgumentException("area muss 3 oder 5 sein");
                        area = lvl;
                        continue;
                    }
                    Enchantment en2 = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k));
                    if (en2 == null) throw new IllegalArgumentException("unbekannte Verzauberung " + k);
                    meta.addEnchant(en2, lvl, true);
                    ench.add(k);
                }
                if (area > 0) meta.getPersistentDataContainer().set(AreaToolListener.KEY, PersistentDataType.INTEGER, area);
                if (parts.length == 1) {
                    meta.displayName(Msg.parse("<!italic><gold>" + o.name()));
                    List<Component> lore = new ArrayList<>();
                    if (area > 0) {
                        lore.add(Msg.parse("<!italic><gray>Baut <white>" + area + " × " + area + "</white> Blöcke auf einmal ab"));
                        lore.add(Msg.parse("<!italic><dark_gray>Schleichen: nur ein Block"));
                    }
                    lore.add(Msg.parse("<!italic><dark_gray>XP-Shop"));
                    meta.lore(lore);
                }
                it.setItemMeta(meta);
            }
            out.add(it);
        }
        return out;
    }

    /** Prüft ein Angebot ohne es zu geben: Fehlermeldung oder null (für den SELFTEST). */
    public static String check(KubyPlugins plugin, Offer o) {
        try {
            switch (o.kind()) {
                case "COINS", "JEWELS", "TEAMBANK" -> {
                    if (Long.parseLong(o.value().replace(".", "")) <= 0) return "Betrag <= 0";
                }
                case "KEY" -> {
                    if (plugin.keys().type(o.value()) == null) return "Schlüssel " + o.value() + " fehlt in keys.types";
                }
                case "BOOSTER" -> {
                    String[] b = o.value().split(":");
                    if (b.length != 3 || !(b[0].equals("xp") || b[0].equals("sell"))) return "Booster-Format art:Faktor:Minuten";
                    if (Double.parseDouble(b[1]) <= 1 || Integer.parseInt(b[2]) <= 0) return "Booster-Faktor/Minuten";
                }
                case "ITEM" -> items(o);
                case "TAG", "COMMAND" -> {
                    if (o.value().isBlank()) return "Wert fehlt";
                }
                default -> {
                    return "unbekannte Art " + o.kind();
                }
            }
        } catch (RuntimeException e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
        if (o.price() <= 0) return "Preis fehlt";
        return null;
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** /xpshop: Übersicht. Nur in SMP und Skyblock. */
    public void open(Player p) {
        category.remove(p.getUniqueId());
        pages.remove(p.getUniqueId());
        show(p);
    }

    private boolean allowed(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        if (ModeService.hasCurrency(mode, "xp") && !plugin.getConfig().getStringList("xp-shop.kategorien." + mode).isEmpty()) {
            return true;
        }
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
        Msg.send(p, ModeService.SURVIVAL.equals(mode)
                ? (en ? "<gray>There is no XP in Survival. Spend your tokens: <white>/tokenshop"
                : "<gray>In Survival gibt es keine XP. Deine Tokens: <white>/tokenshop")
                : (en ? "<gray>The XP shop is open in <white>SMP <gray>and <white>Skyblock<gray>."
                : "<gray>Den XP-Shop gibt es in <white>SMP <gray>und <white>Skyblock<gray>."));
        return false;
    }

    void show(Player p) {
        if (!allowed(p)) return;
        if (!plugin.uiPack().active(p)) {
            new XpShopMenu(plugin, category.get(p.getUniqueId())).open(p);
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
        amounts.remove(id);
        armed.remove(id);
        armedAt.remove(id);
    }

    View view(Player p) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        boolean en = plugin.playerSettings().english(id);
        long xp = plugin.stats().get(id).xp;
        List<Category> cats = categories(plugin.getConfig(), mode);
        Category cat = null;
        for (Category c : cats) if (c.id().equals(category.get(id))) cat = c;
        List<Row> rows = new ArrayList<>();
        int page = 0, pageCount = 1;
        if (cat != null) {
            List<Offer> all = offers(plugin.getConfig(), mode, cat.id());
            pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
            page = Math.max(0, Math.min(pageCount - 1, pages.getOrDefault(id, 0)));
            for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
                Offer o = all.get(i);
                int n = "KEY".equals(o.kind()) ? amounts.getOrDefault(id, Map.of()).getOrDefault(o.id(), 1) : 1;
                long price = o.price() * n;
                State st = xp < price ? State.MISSING : o.id().equals(armed.get(id)) ? State.ARMED : State.BUY;
                rows.add(new Row(o, price, st, n, info(p, mode, o, en)));
            }
        }
        return new View(mode, en, xp, cat, cats, rows, page, pageCount, boosters(id, en));
    }

    private String boosters(UUID id, boolean en) {
        List<String> out = new ArrayList<>();
        for (String kind : new String[]{"xp", "sell"}) {
            long min = plugin.booster().minutesLeft(id, kind);
            if (min <= 0) continue;
            out.add((kind.equals("xp") ? "XP" : en ? "Selling" : "Verkauf") + " x" + mult(plugin.booster().personal(id, kind))
                    + (en ? " · " + min + " min left" : " · noch " + min + " min"));
        }
        return String.join("   ", out);
    }

    private static String mult(double m) {
        return m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m).replace('.', ',');
    }

    private String info(Player p, String mode, Offer o, boolean en) {
        UUID id = p.getUniqueId();
        return switch (o.kind()) {
            case "COINS" -> (en ? "Your money: " : "Dein Geld: ") + Msg.coins(plugin.economy().balance(id)) + " $";
            case "JEWELS" -> (en ? "Your jewels: " : "Deine Juwelen: ") + Msg.coins(plugin.jewels().balance(id));
            case "BOOSTER" -> {
                String kind = o.value().split(":")[0];
                long min = plugin.booster().minutesLeft(id, kind);
                yield min > 0 ? (en ? "Running: x" + mult(plugin.booster().personal(id, kind)) + ", " + min + " min left · adds time"
                        : "Läuft: x" + mult(plugin.booster().personal(id, kind)) + ", noch " + min + " min · verlängert")
                        : en ? "Starts right away" : "Startet sofort";
            }
            case "TEAMBANK" -> plugin.teams().of(id) == null ? (en ? "You need a team (/team)" : "Du brauchst ein Team (/team)")
                    : (en ? "Team bank: " : "Teamkasse: ") + Msg.coins(plugin.teams().of(id).bank()) + " $";
            case "ITEM" -> o.value().contains("area=") ? (en ? "Claims and island protection apply to every block"
                    : "Grundstücke und Inselschutz gelten für jeden Block")
                    : en ? "Goes straight into your inventory" : "Kommt sofort in dein Inventar";
            default -> "";
        };
    }

    // =================================================================================================================
    // Klicks und Kauf
    // =================================================================================================================

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
                    category.put(pl.getUniqueId(), id);
                    pages.put(pl.getUniqueId(), 0);
                    armed.remove(pl.getUniqueId());
                    show(pl);
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
                    XpShopScreen.this.buy(pl, id, true);
                    show(pl);
                };
            }

            public Consumer<Player> amount(String id, int amount) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.TICK);
                    amounts.computeIfAbsent(pl.getUniqueId(), k -> new HashMap<>()).put(id, amount);
                    armed.remove(pl.getUniqueId());
                    show(pl);
                };
            }
        };
    }

    /**
     * Kauf: confirm = erster Klick schaltet scharf, zweiter (innerhalb von 8 s) kauft. Zuerst wird geliefert, erst
     * dann werden die XP abgezogen (scheitert die Lieferung, kostet es nichts). Gibt true bei Erfolg.
     */
    public boolean buy(Player p, String offerId, boolean confirm) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        boolean en = plugin.playerSettings().english(id);
        if (!ModeService.hasCurrency(mode, "xp")) {
            allowed(p);
            return false;
        }
        Offer o = find(mode, offerId);
        if (o == null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            return false;
        }
        int n = "KEY".equals(o.kind()) ? amounts.getOrDefault(id, Map.of()).getOrDefault(o.id(), 1) : 1;
        long price = o.price() * n;
        StatsService.Stats s = plugin.stats().get(id);
        if (s.xp < price) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, en ? "<red>You are missing " + Msg.coins(price - s.xp) + " XP."
                    : "<red>Dir fehlen noch " + Msg.coins(price - s.xp) + " XP.");
            armed.remove(id);
            return false;
        }
        if (confirm) {
            Long at = armedAt.get(id);
            if (!(o.id().equals(armed.get(id)) && at != null && System.currentTimeMillis() - at <= ARM_MS)) {
                armed.put(id, o.id());
                armedAt.put(id, System.currentTimeMillis());
                Sfx.play(plugin, p, Sfx.Kind.CLICK);
                return false;
            }
        }
        armed.remove(id);
        armedAt.remove(id);
        String err = give(p, mode, o, n);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
            return false;
        }
        s.xp -= price;
        plugin.stats().save();
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, (en ? "<green>Bought: <white>" : "<green>Gekauft: <white>") + (n > 1 ? n + "x " : "") + o.name(en)
                + " <gray>(-" + Msg.coins(price) + " XP)");
        return true;
    }

    /** Liefert ein Angebot. Fehlermeldung oder null. */
    private String give(Player p, String mode, Offer o, int n) {
        UUID id = p.getUniqueId();
        try {
            switch (o.kind()) {
                case "COINS" -> plugin.economy().deposit(id, Long.parseLong(o.value().replace(".", "")));
                case "JEWELS" -> plugin.jewels().add(id, Long.parseLong(o.value().replace(".", "")));
                case "KEY" -> {
                    if (!plugin.keys().give(id, mode, o.value(), n)) return "Diesen Schlüssel gibt es nicht (keys.types).";
                }
                case "BOOSTER" -> {
                    String[] b = o.value().split(":");
                    plugin.booster().grant(id, b[0], Double.parseDouble(b[1]), Integer.parseInt(b[2]));
                }
                case "TEAMBANK" -> {
                    String err = plugin.teams().addToBank(id, Long.parseLong(o.value().replace(".", "")));
                    if (err != null) return err;
                }
                case "ITEM" -> {
                    List<ItemStack> items = items(o);
                    Map<Integer, ItemStack> left = p.getInventory().addItem(items.toArray(new ItemStack[0]));
                    for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    if (!left.isEmpty()) Msg.send(p, "<yellow>Inventar voll: der Rest liegt vor dir.");
                }
                case "TAG" -> plugin.tags().grant(id, o.value());
                case "COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), o.value().replace("%player%", p.getName()));
                default -> {
                    return "Diese Ware ist in der config.yml falsch eingetragen.";
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("XP-Shop: Angebot " + o.id() + " fehlerhaft: " + e.getMessage());
            return "Diese Ware ist in der config.yml falsch eingetragen.";
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

    private static Material material(String name, Material def) {
        Material m = Material.matchMaterial(name);
        if (m == null) return def;
        try {
            return m.isItem() ? m : def;
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            return m; // ohne Server (Vorschau-Bilder): Registry fehlt
        }
    }

    /** Rechts im Kopf: XP-Flasche, „Deine / XP“, Anzahl. */
    private static void balance(Canvas c, long xp, boolean en) {
        String n = Msg.coins(xp);
        int nw = Canvas.width(n, true);
        int nx = 380 - nw;
        int lx = Math.min(334, nx - 32);
        c.icon(lx - 20, 12, KitData.Icon.EXPERIENCE, false);
        c.text(lx, 9, en ? "Your" : "Deine", SUB, false);
        c.text(lx, 19, "XP", SUB, false);
        c.text(nx, 14, n, LIME, true);
    }

    public static Canvas canvas(View v, Actions a) {
        return v.category() == null ? home(v, a) : rows(v, a);
    }

    private static Canvas home(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W).heading("XP-Shop", LIME);
        c.box(0, 0, W, 46, KitData.Box.JS_HEAD);
        c.item(6, 7, Material.EXPERIENCE_BOTTLE, true, KitData.Icon.EXPERIENCE);
        c.sprite(46, 8, KitData.Sprite.XS_TITLE);
        List<String> names = new ArrayList<>();
        for (Category cat : v.categories()) if (names.size() < 5) names.add(cat.name(en));
        c.text(47, 30, Canvas.fit("→ " + String.join(" · ", names) + (v.categories().size() > 5 ? " …" : ""), false, 270), SUB, false);
        balance(c, v.xp(), en);
        int n = v.categories().size();
        int cols = 3, cw = 126, ch = 62, gap = 6;
        for (int i = 0; i < n; i++) {
            Category cat = v.categories().get(i);
            int x = (i % cols) * (cw + gap), y = 50 + (i / cols) * (ch + 4);
            card(c, a, x, y, cw, ch, cat, en);
        }
        int y = 50 + ((n + cols - 1) / cols) * (ch + 4) + 2;
        if (!v.boosters().isEmpty()) {
            c.box(0, y, W, 22, KitData.Box.JS_VIOLET);
            c.icon(6, y + 3, KitData.Icon.EXPERIENCE, false);
            c.text(26, y + 7, List.of(Span.bold(en ? "Active boosters: " : "Aktive Booster: ", BOOST), Span.of(v.boosters(), DESC)));
        } else {
            center(c, W / 2, y + 6, en ? "XP comes from ores, mobs, kills, quests and votes."
                    : "XP gibt es für Erze, Mobs, Kills, Quests und Votes.", SUB, false);
        }
        return c;
    }

    private static void card(Canvas c, Actions a, int x, int y, int w, int h, Category cat, boolean en) {
        KitData.Box b = JewelShopScreen.box(cat.style());
        c.box(x, y, w, h, b);
        c.item(x + 6, y + 6, material(cat.icon(), Material.CHEST), true, KitData.Icon.CHEST);
        c.text(x + 43, y + 7, Canvas.fit(cat.name(en).toUpperCase(Locale.GERMAN), true, w - 48), cat.color(), true);
        List<String> lines = Canvas.wrap(cat.line(en), w - 49);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String l = i == 1 && lines.size() > 2 ? lines.get(1) + " " + lines.get(2) : lines.get(i);
            c.text(x + 43, y + 18 + 10 * i, Canvas.fit(l, false, w - 49), DESC, false);
        }
        c.box(x + 5, y + h - 21, w - 10, 17, b, 1);
        center(c, x + w / 2, y + h - 16, en ? "VIEW" : "ANSEHEN", cat.color(), true);
        c.text(x + w - 16, y + h - 16, "›", cat.color(), true);
        String count = cat.count() + (en ? (cat.count() == 1 ? " offer" : " offers") : cat.count() == 1 ? " Angebot" : " Angebote");
        if (a != null) c.action(x, y, w, h, a.category(cat.id()), tip(cat.name(en), cat.line(en) + " · " + count,
                en ? "Click: open" : "Klick: öffnen"));
    }

    private static Canvas rows(View v, Actions a) {
        boolean en = v.english();
        Category cat = v.category();
        Canvas c = new Canvas(W).heading("XP-Shop", LIME);
        c.box(0, 0, W, 46, KitData.Box.JS_HEAD);
        c.sprite(10, 9, KitData.Sprite.JS_BACK);
        if (a != null) c.action(10, 9, 22, 22, a.home(), Component.text(en ? "Back" : "Zurück", WHITE));
        c.text(42, 11, cat.name(en).toUpperCase(Locale.GERMAN), cat.color(), true);
        c.text(42, 26, Canvas.fit(cat.line(en), false, 240), SUB, false);
        balance(c, v.xp(), en);
        int y = 49;
        for (Row r : v.rows()) {
            Offer o = r.offer();
            KitData.Box b = JewelShopScreen.box(cat.style());
            c.box(0, y, W, 50, b);
            c.item(8, y + 9, material(o.icon(), Material.CHEST), true, KitData.Icon.CHEST);
            c.text(48, y + 9, Canvas.fit(o.name(en).toUpperCase(Locale.GERMAN), true, 216), cat.color(), true);
            c.text(48, y + 21, Canvas.fit(o.line(en), false, 216), DESC, false);
            if ("KEY".equals(o.kind())) {
                c.text(48, y + 34, en ? "Amount" : "Menge", SUB, false);
                for (int i = 0; i < AMOUNTS.length; i++) {
                    int px = 84 + 27 * i;
                    boolean on = AMOUNTS[i] == r.amount();
                    c.sprite(px, y + 32, on ? KitData.Sprite.JS_PILL_LEGENDARY : KitData.Sprite.JS_PILL_OFF);
                    center(c, px + 12, y + 35, String.valueOf(AMOUNTS[i]), on ? DARK : DESC, false);
                    if (a != null && !on) c.action(px, y + 32, 24, 14, a.amount(o.id(), AMOUNTS[i]),
                            Component.text((en ? "Amount " : "Menge ") + AMOUNTS[i], WHITE));
                }
            } else if (!r.info().isEmpty()) {
                c.text(48, y + 33, Canvas.fit(r.info(), false, 216), SUB, false);
            }
            price(c, 325, y + 7, r.price());
            button(c, a, 270, y + 23, 110, r, v.xp(), en);
            y += 54;
        }
        if (v.rows().isEmpty()) center(c, W / 2, y + 8, en ? "Nothing here right now." : "Hier gibt es gerade nichts.", SUB, false);
        if (v.pages() > 1) {
            int py = 264;
            boolean prev = v.page() > 0, next = v.page() < v.pages() - 1;
            String label = (en ? "Page " : "Seite ") + (v.page() + 1) + " / " + v.pages();
            center(c, W / 2, py + 4, label, SUB, false);
            int lw = Canvas.width(label, false);
            int lx = W / 2 - lw / 2 - 30, rx = W / 2 + lw / 2 + 6;
            c.smallButton(lx, py, 24, KitData.Small.DARK, "‹", prev ? WHITE : TextColor.color(0x54575E), true);
            c.smallButton(rx, py, 24, KitData.Small.DARK, "›", next ? WHITE : TextColor.color(0x54575E), true);
            if (a != null) {
                if (prev) c.action(lx, py, 24, 16, a.page(v.page() - 1), Component.text(en ? "Previous page" : "Vorherige Seite", WHITE));
                if (next) c.action(rx, py, 24, 16, a.page(v.page() + 1), Component.text(en ? "Next page" : "Nächste Seite", WHITE));
            }
        }
        return c;
    }

    /** XP-Symbol und Preis, zusammen mittig um cx. */
    private static void price(Canvas c, int cx, int y, long price) {
        String n = Msg.coins(price) + " XP";
        int w = Canvas.width(n, true);
        int x = cx - (18 + w + 1) / 2;
        c.icon(x, y, KitData.Icon.EXPERIENCE, false);
        c.text(x + 18, y + 4, n, LIME, true);
    }

    private static void button(Canvas c, Actions a, int x, int y, int w, Row r, long xp, boolean en) {
        int cx = x + w / 2;
        switch (r.state()) {
            case MISSING -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, List.of(Span.of(en ? "You need " : "Dir fehlen ", DESC),
                        Span.of(Msg.coins(r.price() - xp), LIME)));
            }
            case BUY -> {
                c.box(x, y, w, 18, KitData.Box.JS_GREEN, 1);
                center(c, cx, y + 5, en ? "BUY" : "KAUFEN", BUY, true);
            }
            case ARMED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GOLD, 1);
                center(c, cx, y + 5, en ? "CONFIRM" : "BESTÄTIGEN", GOLD, true);
            }
        }
        if (a != null) c.action(x, y, w, 18, a.buy(r.offer().id()), tipFor(r, en));
    }

    private static Component tipFor(Row r, boolean en) {
        Offer o = r.offer();
        TextComponent.Builder b = Component.text();
        b.append(Component.text((r.amount() > 1 ? r.amount() + "x " : "") + o.name(en), WHITE).decorate(TextDecoration.BOLD));
        for (String l : Canvas.wrap(o.line(en), 200)) b.append(Component.newline()).append(Component.text(l, DESC));
        if (!r.info().isEmpty()) b.append(Component.newline()).append(Component.text(r.info(), SUB));
        b.append(Component.newline()).append(Component.newline());
        b.append(Component.text(switch (r.state()) {
            case MISSING -> en ? "Not enough XP" : "Nicht genug XP";
            case BUY -> en ? "Click: buy (then confirm)" : "Klick: kaufen (danach bestätigen)";
            case ARMED -> en ? "Click again: buy now" : "Nochmal klicken: jetzt kaufen";
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

    // =================================================================================================================
    // Vorschau ohne Spieler (SELFTEST, Bilder)
    // =================================================================================================================

    /** Ansicht für den SELFTEST: Übersicht (cat null) oder eine Kategorie (Seite 0), xp als Kontostand. */
    public static View sample(FileConfiguration cfg, String mode, String cat, long xp, boolean en) {
        List<Category> cats = categories(cfg, mode);
        Category sel = null;
        for (Category c : cats) if (c.id().equals(cat)) sel = c;
        List<Row> rows = new ArrayList<>();
        int pageCount = 1;
        if (sel != null) {
            List<Offer> all = offers(cfg, mode, sel.id());
            pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
            for (int i = 0; i < Math.min(PER_PAGE, all.size()); i++) {
                Offer o = all.get(i);
                rows.add(new Row(o, o.price(), xp < o.price() ? State.MISSING : i == 1 ? State.ARMED : State.BUY, 1,
                        "KEY".equals(o.kind()) ? "" : en ? "Goes straight into your inventory" : "Kommt sofort in dein Inventar"));
            }
        }
        return new View(mode, en, xp, sel, cats, rows, 0, pageCount, cat == null ? "XP x2 · noch 23 min" : "");
    }
}
