package de.kuby.kubysurvival.shop;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.farmpass.FarmpassMenu;
import de.kuby.kubysurvival.farmpass.FarmpassPetMenu;
import de.kuby.kubysurvival.furniture.FurnitureCatalog;
import de.kuby.kubysurvival.pets.PetService;
import de.kuby.kubysurvival.shop.ShopCanvas.Card;
import de.kuby.kubysurvival.shop.ShopCanvas.Farmpass;
import de.kuby.kubysurvival.shop.ShopCanvas.Pack;
import de.kuby.kubysurvival.shop.ShopCanvas.Page;
import de.kuby.kubysurvival.shop.ShopCanvas.Row;
import de.kuby.kubysurvival.shop.ShopCanvas.State;
import de.kuby.kubysurvival.shop.ShopCanvas.Tab;
import de.kuby.kubysurvival.shop.ShopCanvas.TokenPack;
import de.kuby.kubysurvival.shop.ShopCanvas.View;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Juwelenshop in Survival (/juwelenshop mit dem Pack KubyUI): Übersicht, Ränge, Farmpass, Crates, Vorteilspakete,
 * Möbel, Pets und Tokens auf dem Dialog-Kit (Zeichnen: {@link ShopCanvas}). Kaufen in zwei Klicks (Kaufen, dann
 * Bestätigen innerhalb von 8 Sekunden), bezahlt mit Juwelen. Im Geschenk-Modus geht alles an einen anderen Spieler.
 * Inhalt und Preise in der Kern-Config unter jewelshop-survival; Möbel und Pets aus ihren Katalogen.
 */
public final class SurvivalShop implements Listener {

    private static final long ARM_MS = 8000;
    private static final int PER_PAGE = 8;
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final TextColor GOLD = TextColor.color(0xFCD45D);
    /** Seltenheiten der Pets: Reiter, Kartenstil und Farbe des Namens. */
    static final String[] RARITIES = {"common", "rare", "epic", "legendary", "mythic"};
    static final String[] RARITY_TABS = {"Gewöhnlich", "Selten", "Episch", "Legendär", "Mythisch"};
    static final int[] RARITY_COLORS = {0xC6CAD1, 0x54DD79, 0xB25BFC, 0xFCAE20, 0xFC99DA};

    private final KubySurvival addon;
    private final KubyPlugins core;
    /** Je Spieler: Reiter und Seite (Möbel, Pets), Menge je Kiste, scharf geschalteter Kauf, Geschenk-Empfänger. */
    private final Map<UUID, int[]> furniturePos = new HashMap<>();
    private final Map<UUID, int[]> petPos = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> amounts = new HashMap<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();
    private final Map<UUID, UUID> giftTo = new HashMap<>();

    public SurvivalShop(KubySurvival addon) {
        this.addon = addon;
        this.core = addon.core();
    }

    // =================================================================================================================
    // Config
    // =================================================================================================================

    /** Abschnitt aus jewelshop-survival; fehlt er in einer älteren config.yml, gilt der aus dem Plugin-Jar. */
    private ConfigurationSection cfg(String path) {
        String full = "jewelshop-survival." + path;
        Object own = core.getConfig().get(full, null);
        if (own instanceof ConfigurationSection cs) return cs;
        var defaults = core.getConfig().getDefaults();
        return defaults == null ? null : defaults.getConfigurationSection(full);
    }

    private static TextColor color(String hex, int def) {
        TextColor c = hex == null ? null : TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
        return c == null ? TextColor.color(def) : c;
    }

    /** Rang aus der Config: Zeile im Shop und Befehle beim Kauf. */
    record Rank(String id, String name, String box, String icon, TextColor color, List<String> lines, long price,
                List<String> commands) {}

    /** Kiste: count Schlüssel je Kauf zum Preis price, amounts = wählbare Anzahl Käufe. */
    record Crate(String id, String name, String box, String icon, TextColor color, List<String> lines, long price,
                 String key, int count, int[] amounts) {}

    record PackItem(String key, int amount, String label, TextColor color) {}

    record PackDef(String id, String name, String box, String icon, TextColor color, long price, List<PackItem> items) {}

    record Tokens(String id, int tokens, long price, long instead, String box, String icon, TextColor color, boolean best) {}

    List<Rank> ranks() {
        List<Rank> out = new ArrayList<>();
        ConfigurationSection s = cfg("ranks");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null) continue;
            out.add(new Rank(id, r.getString("name", id), r.getString("style", "js_gray"), r.getString("icon", "rank_platin"),
                    color(r.getString("color"), 0xD1D2D5), r.getStringList("lines"), Math.max(0, r.getLong("price")),
                    r.getStringList("commands")));
        }
        return out;
    }

    List<Crate> crates() {
        List<Crate> out = new ArrayList<>();
        ConfigurationSection s = cfg("crates");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null) continue;
            List<Integer> am = r.getIntegerList("amounts");
            int[] amounts = am.isEmpty() ? new int[]{1} : am.stream().mapToInt(Integer::intValue).filter(i -> i > 0).toArray();
            List<String> lines = r.getStringList("lines");
            if (lines.isEmpty() && r.isString("line")) lines = List.of(r.getString("line"));
            out.add(new Crate(id, r.getString("name", id), r.getString("style", "r_epic"), r.getString("icon", "key_epic"),
                    color(r.getString("color"), 0xB25BFC), lines, Math.max(0, r.getLong("price")),
                    r.getString("key", id).toLowerCase(Locale.ROOT), Math.max(1, r.getInt("count", 1)), amounts));
        }
        return out;
    }

    List<PackDef> packs() {
        List<PackDef> out = new ArrayList<>();
        ConfigurationSection s = cfg("packages");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null) continue;
            List<PackItem> items = new ArrayList<>();
            for (String raw : r.getStringList("contents")) {
                String[] p = raw.split(";");
                if (p.length < 2) continue;
                try {
                    String key = p[0].trim().toLowerCase(Locale.ROOT);
                    var t = core.keys().type(key);
                    String label = p.length > 2 ? p[2].trim() : (t == null ? key : t.name());
                    TextColor col = color(p.length > 3 ? p[3].trim() : (t == null ? null : t.color()), 0xFCD45D);
                    items.add(new PackItem(key, Integer.parseInt(p[1].trim()), label, col));
                } catch (NumberFormatException ignored) {
                }
            }
            out.add(new PackDef(id, r.getString("name", id), r.getString("style", "js_gold"), r.getString("icon", "js_pack_starter"),
                    color(r.getString("color"), 0xFCD45D), Math.max(0, r.getLong("price")), items));
        }
        return out;
    }

    List<Tokens> tokenPacks() {
        List<Tokens> out = new ArrayList<>();
        ConfigurationSection s = cfg("tokens");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r == null || r.getInt("tokens") <= 0) continue;
            out.add(new Tokens(id, r.getInt("tokens"), Math.max(0, r.getLong("price")), Math.max(0, r.getLong("instead")),
                    r.getString("style", "t_blue"), r.getString("icon", "tok_25"), color(r.getString("color"), 0x62CAFC),
                    r.getBoolean("best")));
        }
        return out;
    }

    private String storeUrl() {
        return core.getConfig().getString("jewels.store-url", "");
    }

    private String aboUrl() {
        String u = core.getConfig().getString("jewelshop-survival.abo-url", "");
        return u.isBlank() ? storeUrl() : u;
    }

    private String storeText() {
        String t = core.getConfig().getString("jewelshop-survival.store-text", "");
        if (!t.isBlank()) return t;
        String url = storeUrl();
        return url.isBlank() ? "Store" : url.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /**
     * /juwelenshop in Survival mit Pack. sub: raenge, farmpass, crates, pakete, moebel, pets, tokens (leer = Übersicht).
     * Gibt false zurück, wenn der Spieler das Pack nicht hat (dann der Truhen-Shop des Kerns).
     */
    public boolean open(Player p, String sub) {
        if (!core.uiPack().active(p)) return false;
        Page page = switch (sub == null ? "" : sub.toLowerCase(Locale.ROOT)) {
            case "raenge", "ränge", "ranks" -> Page.RANKS;
            case "farmpass", "pass" -> Page.FARMPASS;
            case "crates", "kisten", "keys" -> Page.CRATES;
            case "pakete", "packages" -> Page.PACKS;
            case "moebel", "möbel", "furniture" -> Page.FURNITURE;
            case "pets", "haustiere" -> Page.PETS;
            case "tokens" -> Page.TOKENS;
            default -> Page.HOME;
        };
        open(p, page);
        return true;
    }

    public void open(Player p, Page page) {
        UUID id = p.getUniqueId();
        Long at = armedAt.get(id);
        if (at != null && System.currentTimeMillis() - at > ARM_MS) {
            armed.remove(id);
            armedAt.remove(id);
        }
        UiDialog.show(p, ShopCanvas.canvas(view(p, page), actions(page)), ShopCanvas.height(page));
    }

    /** Geschenk-Modus für target (online), dann die Übersicht. False ohne Pack. */
    public boolean openGift(Player p, Player target) {
        if (!core.uiPack().active(p)) return false;
        giftTo.put(p.getUniqueId(), target.getUniqueId());
        open(p, Page.HOME);
        return true;
    }

    private Player recipient(Player p) {
        UUID t = giftTo.get(p.getUniqueId());
        if (t == null) return p;
        Player other = Bukkit.getPlayer(t);
        if (other == null) {
            giftTo.remove(p.getUniqueId());
            return p;
        }
        return other;
    }

    private int amountOf(UUID id, String crate, Crate c) {
        int n = amounts.getOrDefault(id, Map.of()).getOrDefault(crate, c.amounts()[0]);
        for (int a : c.amounts()) if (a == n) return n;
        return c.amounts()[0];
    }

    private static State state(long jewels, long price, String armedId, String id) {
        if (jewels < price) return State.MISSING;
        return id.equals(armedId) ? State.ARMED : State.BUY;
    }

    /** Index des höchsten Rangs aus der Liste, den der Spieler hat (LuckPerms group.x oder kuby.rank.x), sonst -1. */
    private static int ownedRank(Player p, List<Rank> ranks) {
        for (int i = ranks.size() - 1; i >= 0; i--) {
            String r = ranks.get(i).id().toLowerCase(Locale.ROOT);
            if (p.hasPermission("group." + r) || p.hasPermission("kuby.rank." + r)) return i;
        }
        return -1;
    }

    // =================================================================================================================
    // Ansicht
    // =================================================================================================================

    View view(Player p, Page page) {
        UUID id = p.getUniqueId();
        Player to = recipient(p);
        long jewels = core.jewels().balance(id);
        String armedId = armed.getOrDefault(id, "");
        String gift = to == p ? null : to.getName();
        long tokens = addon.tokens() == null ? 0 : addon.tokens().balance(id);
        List<Row> rows = new ArrayList<>();
        List<Tab> tabs = new ArrayList<>();
        List<Card> cards = new ArrayList<>();
        List<TokenPack> tps = new ArrayList<>();
        List<Pack> packs = new ArrayList<>();
        int tab = 0, pageNo = 0, pages = 1;
        String sub = "";
        Farmpass fp = null;
        switch (page) {
            case RANKS -> {
                List<Rank> ranks = ranks();
                int owned = ownedRank(to, ranks);
                long paid = owned >= 0 ? ranks.get(owned).price() : 0;
                for (int i = 0; i < ranks.size(); i++) {
                    Rank r = ranks.get(i);
                    long price = i <= owned ? r.price() : Math.max(0, r.price() - paid);
                    State st = i < owned ? State.INCLUDED : i == owned ? State.OWNED : state(jewels, price, armedId, "rank:" + r.id());
                    rows.add(new Row("rank:" + r.id(), r.name(), r.box(), r.icon(), r.color(), r.lines(), price, st, 1, 1, null));
                }
                sub = "Pets, Homes & Plätze für immer";
            }
            case CRATES -> {
                for (Crate c : crates()) {
                    int n = amountOf(id, c.id(), c);
                    long price = c.price() * n;
                    rows.add(new Row("crate:" + c.id(), c.name(), c.box(), c.icon(), c.color(), c.lines(), price,
                            state(jewels, price, armedId, "crate:" + c.id()), c.count(), n, c.amounts()));
                }
                sub = core.getConfig().getString("jewelshop-survival.crates-sub", "Keys in jeder Menge");
            }
            case PACKS -> {
                for (PackDef k : packs()) {
                    List<Span> lines = new ArrayList<>();
                    for (PackItem it : k.items()) lines.add(Span.of(it.amount() + "× " + it.label(), it.color()));
                    packs.add(new Pack("pack:" + k.id(), k.name(), k.box(), k.icon(), k.color(), lines, k.price(),
                            state(jewels, k.price(), armedId, "pack:" + k.id())));
                }
                sub = "Viele Keys zum Sparpreis";
            }
            case TOKENS -> {
                for (Tokens t : tokenPacks()) {
                    long per = t.tokens() == 0 ? 0 : t.price() / t.tokens();
                    tps.add(new TokenPack("tokens:" + t.id(), t.tokens(), t.price(), per, t.instead(), t.box(), t.icon(), t.color(),
                            t.best(), state(jewels, t.price(), armedId, "tokens:" + t.id())));
                }
            }
            case FARMPASS -> fp = farmpass(to);
            case FURNITURE -> {
                List<FurnitureCatalog.Tab> ft = FurnitureCatalog.tabs();
                int[] pos = furniturePos.getOrDefault(id, new int[]{0, 0});
                tab = Math.max(0, Math.min(pos[0], ft.size() - 1));
                for (FurnitureCatalog.Tab t : ft) tabs.add(new Tab(t.id(), t.name()));
                List<FurnitureCatalog.Def> list = ft.isEmpty() ? List.of() : FurnitureCatalog.inTab(ft.get(tab).id());
                pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
                pageNo = Math.max(0, Math.min(pos[1], pages - 1));
                for (int i = pageNo * PER_PAGE; i < Math.min(list.size(), (pageNo + 1) * PER_PAGE); i++) {
                    FurnitureCatalog.Def d = list.get(i);
                    cards.add(new Card("furniture:" + d.id(), d.name(), "f_" + d.id(), "k_furniture", TextColor.color(0xEFB16A),
                            d.price(), state(jewels, d.price(), armedId, "furniture:" + d.id())));
                }
                if (!ft.isEmpty()) sub = ft.get(tab).sub() + " · " + list.size() + " Möbel";
            }
            case PETS -> {
                int[] pos = petPos.getOrDefault(id, new int[]{0, 0});
                tab = Math.max(0, Math.min(pos[0], RARITIES.length));
                tabs.add(new Tab("all", "Alle"));
                for (int i = 0; i < RARITIES.length; i++) tabs.add(new Tab(RARITIES[i], RARITY_TABS[i]));
                List<PetService.Kind> list = pets(tab);
                pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
                pageNo = Math.max(0, Math.min(pos[1], pages - 1));
                for (int i = pageNo * PER_PAGE; i < Math.min(list.size(), (pageNo + 1) * PER_PAGE); i++) {
                    PetService.Kind k = list.get(i);
                    int r = Math.max(0, List.of(RARITIES).indexOf(k.rarity()));
                    State st = addon.pets().owns(to, k) ? State.OWNED : state(jewels, k.jewels(), armedId, "pet:" + k.id());
                    cards.add(new Card("pet:" + k.id(), k.name(), "p_" + k.id(), "k_" + RARITIES[r],
                            TextColor.color(RARITY_COLORS[r]), k.jewels(), st));
                }
                sub = (tab == 0 ? "Alle Seltenheiten" : RARITY_TABS[tab - 1]) + " · " + list.size() + " Haustiere";
            }
            default -> {
            }
        }
        return new View(page, jewels, tokens, gift, storeText(), rows, tabs, tab, sub, cards, pageNo, pages, tps, packs, fp);
    }

    /** Pets im Shop: alle (Reiter 0) oder eine Seltenheit, in der Reihenfolge aus pets.yml. */
    private List<PetService.Kind> pets(int tab) {
        List<PetService.Kind> out = new ArrayList<>();
        if (addon.pets() == null) return out;
        for (PetService.Kind k : addon.pets().shopKinds()) {
            if (tab == 0 || RARITIES[tab - 1].equals(k.rarity())) out.add(k);
        }
        return out;
    }

    /**
     * Farmpass-Seite: die Vorteile mit den Werten aus der Config (stimmen so immer mit dem Spiel überein). Kopf mit
     * Ablaufdatum, wenn to (Käufer oder Beschenkter, null im Selbsttest) den Pass schon hat.
     */
    private Farmpass farmpass(Player to) {
        var c = core.getConfig();
        String price = c.getString("jewelshop-survival.farmpass-price", "9,99 € im Monat");
        int daily = addon.quests() == null ? 3 : addon.quests().dailyCount();
        int extra = addon.quests() == null ? 0 : addon.quests().farmpassExtraDaily();
        List<String[]> left = new ArrayList<>();
        if (extra > 0) left.add(new String[]{(daily + extra) + " Daily-Quests", "statt " + daily});
        left.add(new String[]{"Fliegen", "Spawn, Casino, Claim"});
        left.add(new String[]{"Nebenjob", "zweiter Beruf"});
        left.add(new String[]{"Keine Gebühren", ""});
        left.add(new String[]{"Casino steuerfrei", ""});
        left.add(new String[]{"Mehr Plätze", "AH, Homes"});
        List<String[]> right = new ArrayList<>();
        right.add(new String[]{"Grab", "hält " + c.getLong("graves.farmpass-minutes", 60) + " statt "
                + c.getLong("graves.minutes", 30) + " Minuten"});
        right.add(new String[]{"Werkbank & Endertruhe", "überall"});
        right.add(new String[]{"Wochenkiste", "jede Woche Loot"});
        right.add(new String[]{"Pets", "jeden Monat eins"});
        right.add(new String[]{"Claim-Blöcke", "+" + c.getLong("farmpass.claim-blocks-per-month", 250) + " pro Monat"});
        right.add(new String[]{"Blatt-Symbol", "grün im Tab & Chat"});
        String head = "Alle Vorteile sofort, " + price + " im Store";
        if (to != null && addon.farmpass() != null && addon.farmpass().active(to.getUniqueId())) {
            head = "Aktiv bis " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(
                    Instant.ofEpochMilli(addon.farmpass().until(to.getUniqueId())).atZone(ZONE)) + ", verlängern im Store";
        }
        return new Farmpass(price, head, left, right);
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    private ShopCanvas.Actions actions(Page page) {
        return new ShopCanvas.Actions() {
            public Consumer<Player> page(Page target) {
                return pl -> {
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    armed.remove(pl.getUniqueId());
                    open(pl, target);
                };
            }

            public Consumer<Player> buy(String id) {
                return pl -> click(pl, page, id);
            }

            public Consumer<Player> amount(String id, int n) {
                return pl -> {
                    amounts.computeIfAbsent(pl.getUniqueId(), k -> new HashMap<>()).put(id.substring(id.indexOf(':') + 1), n);
                    armed.remove(pl.getUniqueId());
                    Sfx.play(core, pl, Sfx.Kind.TICK);
                    open(pl, page);
                };
            }

            public Consumer<Player> tab(int tab) {
                return pl -> {
                    (page == Page.PETS ? petPos : furniturePos).put(pl.getUniqueId(), new int[]{tab, 0});
                    armed.remove(pl.getUniqueId());
                    Sfx.play(core, pl, Sfx.Kind.TICK);
                    open(pl, page);
                };
            }

            public Consumer<Player> turn(int pageNo) {
                return pl -> {
                    Map<UUID, int[]> m = page == Page.PETS ? petPos : furniturePos;
                    int[] pos = m.getOrDefault(pl.getUniqueId(), new int[]{0, 0});
                    m.put(pl.getUniqueId(), new int[]{pos[0], pageNo});
                    armed.remove(pl.getUniqueId());
                    Sfx.play(core, pl, Sfx.Kind.TICK);
                    open(pl, page);
                };
            }

            public Consumer<Player> gift() {
                return pl -> {
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    if (giftTo.remove(pl.getUniqueId()) != null) {
                        open(pl, Page.HOME);
                        return;
                    }
                    askGift(pl);
                };
            }

            public Consumer<Player> farmpassMenu() {
                return pl -> {
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    pl.closeInventory();
                    new FarmpassMenu(addon).open(pl);
                };
            }

            public Consumer<Player> monthPets() {
                return pl -> {
                    Sfx.play(core, pl, Sfx.Kind.CLICK);
                    pl.closeInventory();
                    new FarmpassPetMenu(addon).open(pl);
                };
            }

            public String storeUrl() {
                return SurvivalShop.this.storeUrl();
            }

            public String aboUrl() {
                return SurvivalShop.this.aboUrl();
            }
        };
    }

    private State stateOf(View v, String id) {
        for (Row r : v.rows()) if (r.id().equals(id)) return r.state();
        for (Card c : v.cards()) if (c.id().equals(id)) return c.state();
        for (TokenPack t : v.tokenPacks()) if (t.id().equals(id)) return t.state();
        for (Pack k : v.packs()) if (k.id().equals(id)) return k.state();
        return null;
    }

    private long priceOf(View v, String id) {
        for (Row r : v.rows()) if (r.id().equals(id)) return r.price();
        for (Card c : v.cards()) if (c.id().equals(id)) return c.price();
        for (TokenPack t : v.tokenPacks()) if (t.id().equals(id)) return t.price();
        for (Pack k : v.packs()) if (k.id().equals(id)) return k.price();
        return -1;
    }

    private void click(Player p, Page page, String id) {
        UUID me = p.getUniqueId();
        View v = view(p, page);
        State st = stateOf(v, id);
        long price = priceOf(v, id);
        if (st == null || st == State.OWNED || st == State.INCLUDED) return;
        if (st == State.MISSING) {
            Sfx.play(core, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Dir fehlen <white>" + Msg.coins(price - core.jewels().balance(me)) + "</white> Juwelen. "
                    + "<gray>Juwelen gibt es im Store" + (storeUrl().isBlank() ? "." : ": <white>" + storeText()));
            return;
        }
        if (st == State.BUY) {
            armed.put(me, id);
            armedAt.put(me, System.currentTimeMillis());
            Sfx.play(core, p, Sfx.Kind.TICK);
            open(p, page);
            return;
        }
        armed.remove(me);
        String err = deliver(p, recipient(p), id, price);
        if (err != null) {
            Sfx.play(core, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        }
        open(p, page);
    }

    /** Zieht die Juwelen ab und liefert an to. Gibt eine Fehlermeldung zurück oder null. */
    String deliver(Player payer, Player to, String id, long price) {
        String kind = id.substring(0, id.indexOf(':')), key = id.substring(id.indexOf(':') + 1);
        String what;
        Runnable give;
        switch (kind) {
            case "rank" -> {
                Rank r = ranks().stream().filter(x -> x.id().equals(key)).findFirst().orElse(null);
                if (r == null || r.commands().isEmpty()) return "Dieser Rang ist nicht eingerichtet.";
                what = r.name();
                give = () -> {
                    for (String cmd : r.commands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", to.getName()));
                    }
                };
            }
            case "crate" -> {
                Crate c = crates().stream().filter(x -> x.id().equals(key)).findFirst().orElse(null);
                if (c == null || core.keys().type(c.key()) == null) return "Diesen Schlüssel gibt es nicht.";
                int n = amountOf(payer.getUniqueId(), key, c) * c.count();
                what = n + "× " + c.name();
                give = () -> core.keys().give(to.getUniqueId(), ModeService.SURVIVAL, c.key(), n);
            }
            case "pack" -> {
                PackDef k = packs().stream().filter(x -> x.id().equals(key)).findFirst().orElse(null);
                if (k == null) return "Dieses Paket gibt es nicht.";
                what = k.name() + "-Paket";
                give = () -> {
                    for (PackItem it : k.items()) core.keys().give(to.getUniqueId(), ModeService.SURVIVAL, it.key(), it.amount());
                };
            }
            case "tokens" -> {
                Tokens t = tokenPacks().stream().filter(x -> x.id().equals(key)).findFirst().orElse(null);
                if (t == null || addon.tokens() == null) return "Dieses Token-Paket gibt es nicht.";
                what = t.tokens() + " Tokens";
                give = () -> addon.tokens().give(to.getUniqueId(), t.tokens(), "Juwelenshop");
            }
            case "furniture" -> {
                FurnitureCatalog.Def d = FurnitureCatalog.get(key);
                if (d == null || addon.furniture() == null) return "Dieses Möbel gibt es nicht.";
                what = d.name();
                give = () -> {
                    ItemStack item = addon.furniture().item(d);
                    if (ModeService.SURVIVAL.equals(core.modes().modeOf(to.getUniqueId()))) addon.furniture().give(to, d);
                    else core.modes().abholboxIn(to.getUniqueId(), ModeService.SURVIVAL, item);
                };
            }
            case "pet" -> {
                PetService.Kind k = addon.pets() == null ? null : addon.pets().kind(key);
                if (k == null || k.jewels() <= 0) return "Dieses Haustier gibt es nicht.";
                if (addon.pets().owns(to, k)) return to == payer ? "Das hast du schon." : to.getName() + " hat " + k.name() + " schon.";
                what = k.name();
                give = () -> addon.pets().grant(to.getUniqueId(), k.id());
            }
            default -> {
                return null;
            }
        }
        if (!core.jewels().take(payer.getUniqueId(), price)) return "Dir fehlen Juwelen.";
        give.run();
        Sfx.play(core, payer, Sfx.Kind.SUCCESS_BIG);
        String hint = switch (kind) {
            case "furniture" -> " <dark_gray>· <gray>Rechtsklick auf einen Block stellt es auf";
            case "pet" -> " <dark_gray>· <gray>/pet";
            case "crate", "pack" -> " <dark_gray>· <gray>Öffnen am Spawn";
            default -> "";
        };
        if (to.equals(payer)) {
            Msg.send(payer, "<green>Gekauft: <white>" + Msg.escape(what) + " <gray>(" + Msg.coins(price) + " Juwelen)" + hint);
        } else {
            Msg.send(payer, "<green>Du hast <white>" + to.getName() + "</white> " + Msg.escape(what) + " geschenkt <gray>("
                    + Msg.coins(price) + " Juwelen).");
            Msg.send(to, "<light_purple><b>Geschenk!</b> <white>" + payer.getName() + " <gray>hat dir <white>"
                    + Msg.escape(what) + " <gray>geschenkt." + hint);
            Sfx.play(core, to, Sfx.Kind.SUCCESS_BIG);
        }
        return null;
    }

    private void askGift(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#6DE288><b>Als Geschenk"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Für wen? (Spieler online)")).width(220)
                        .maxLength(16).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Weiter</b>"), null, 120,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String n = response.getText("name");
                    Player t = n == null ? null : Bukkit.getPlayerExact(n.trim());
                    if (t == null) {
                        Msg.send(pl, "<red>" + (n == null || n.isBlank() ? "Kein Name." : n.trim() + " ist gerade nicht online."));
                        open(pl, Page.HOME);
                    } else if (t.equals(pl)) {
                        Msg.send(pl, "<red>An dich selbst kannst du nichts verschenken.");
                        open(pl, Page.HOME);
                    } else {
                        openGift(pl, t);
                    }
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) open(pl, Page.HOME);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        forget(e.getPlayer().getUniqueId());
    }

    public void forget(UUID id) {
        furniturePos.remove(id);
        petPos.remove(id);
        amounts.remove(id);
        armed.remove(id);
        armedAt.remove(id);
        giftTo.remove(id);
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    /** Ansicht ohne Spieler (Selbsttest): Inhalt aus Config und Katalogen, 0 Juwelen, erster Reiter. */
    public View sample(Page page) {
        List<Row> rows = new ArrayList<>();
        List<Tab> tabs = new ArrayList<>();
        List<Card> cards = new ArrayList<>();
        List<TokenPack> tps = new ArrayList<>();
        List<Pack> packs = new ArrayList<>();
        int pages = 1;
        switch (page) {
            case RANKS -> ranks().forEach(r -> rows.add(new Row("rank:" + r.id(), r.name(), r.box(), r.icon(), r.color(),
                    r.lines(), r.price(), State.MISSING, 1, 1, null)));
            case CRATES -> crates().forEach(c -> rows.add(new Row("crate:" + c.id(), c.name(), c.box(), c.icon(), c.color(),
                    c.lines(), c.price(), State.MISSING, c.count(), c.amounts()[0], c.amounts())));
            case PACKS -> packs().forEach(k -> {
                List<Span> lines = new ArrayList<>();
                for (PackItem it : k.items()) lines.add(Span.of(it.amount() + "× " + it.label(), it.color()));
                packs.add(new Pack("pack:" + k.id(), k.name(), k.box(), k.icon(), k.color(), lines, k.price(), State.MISSING));
            });
            case TOKENS -> tokenPacks().forEach(t -> tps.add(new TokenPack("tokens:" + t.id(), t.tokens(), t.price(),
                    t.tokens() == 0 ? 0 : t.price() / t.tokens(), t.instead(), t.box(), t.icon(), t.color(), t.best(), State.MISSING)));
            case FURNITURE -> {
                List<FurnitureCatalog.Tab> ft = FurnitureCatalog.tabs();
                for (FurnitureCatalog.Tab t : ft) tabs.add(new Tab(t.id(), t.name()));
                List<FurnitureCatalog.Def> list = ft.isEmpty() ? List.of() : FurnitureCatalog.inTab(ft.get(0).id());
                pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
                for (FurnitureCatalog.Def d : list.subList(0, Math.min(PER_PAGE, list.size()))) {
                    cards.add(new Card("furniture:" + d.id(), d.name(), "f_" + d.id(), "k_furniture", TextColor.color(0xEFB16A),
                            d.price(), State.MISSING));
                }
            }
            case PETS -> {
                tabs.add(new Tab("all", "Alle"));
                for (int i = 0; i < RARITIES.length; i++) tabs.add(new Tab(RARITIES[i], RARITY_TABS[i]));
                List<PetService.Kind> list = pets(0);
                pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
                for (PetService.Kind k : list.subList(0, Math.min(PER_PAGE, list.size()))) {
                    int r = Math.max(0, List.of(RARITIES).indexOf(k.rarity()));
                    cards.add(new Card("pet:" + k.id(), k.name(), "p_" + k.id(), "k_" + RARITIES[r],
                            TextColor.color(RARITY_COLORS[r]), k.jewels(), State.MISSING));
                }
            }
            default -> {
            }
        }
        Farmpass fp = page == Page.FARMPASS ? farmpass(null) : null;
        return new View(page, 0, 0, null, storeText(), rows, tabs, 0, "", cards, 0, pages, tps, packs, fp);
    }

    /** Für den Selbsttest: die Vorteile der Farmpass-Seite mit den Werten aus der Config. */
    public List<String[]> farmpassPerks() {
        Farmpass f = farmpass(null);
        List<String[]> all = new ArrayList<>(f.left());
        all.addAll(f.right());
        return all;
    }
}
