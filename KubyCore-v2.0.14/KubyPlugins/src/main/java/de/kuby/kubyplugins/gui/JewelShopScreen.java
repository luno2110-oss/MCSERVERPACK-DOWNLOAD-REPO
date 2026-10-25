package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Juwelenshop im SMP auf dem Dialog-Kit wie in der Referenz: Übersicht mit vier Karten (Ränge, Battlepass, Crates,
 * Vorteilspakete), „Als Geschenk“ und „Zum Store“; Unterseiten mit Zeilen (Rang, Battle Pass, Schlüssel mit Menge
 * 1/5/10/25) bzw. drei Paket-Karten. Fehlen Juwelen, steht „Dir fehlen …“ auf dem Knopf; sonst „Kaufen“, ein
 * zweiter Klick bestätigt. Ränge kosten beim Aufstieg nur den Unterschied. Im Geschenk-Modus geht alles an einen
 * anderen Spieler, bezahlt mit den eigenen Juwelen.
 *
 * <p>Inhalt aus der config.yml (jewelshop-smp). Survival und Skyblock behalten ihren eigenen Shop (JewelStoreMenu).
 */
public final class JewelShopScreen {

    public enum Page { HOME, RANKS, PASS, CRATES, PACKS }

    /** Knopf einer Zeile oder Paket-Karte. */
    public enum State { MISSING, BUY, ARMED, OWNED, INCLUDED }

    static final int W = 390, HEIGHT = 280;
    private static final int[] AMOUNTS = {1, 5, 10, 25};
    private static final long ARM_MS = 8000;

    private static final TextColor SUB = TextColor.color(0x9FA1A7), DESC = TextColor.color(0xD1D2D5),
            GREEN = TextColor.color(0x5BED79), GOLD = TextColor.color(0xFCD45D), BLUE = TextColor.color(0x62CAFC),
            RED = TextColor.color(0xFC7986), VIOLET = TextColor.color(0xCD89FC), GIFT = TextColor.color(0x6DE288),
            STORE = TextColor.color(0x6EB6FC), BUY = TextColor.color(0x6DE288), DARK = TextColor.color(0x201400),
            WHITE = TextColor.color(0xFCFCFC), TIP_CLICK = TextColor.color(0xFCFC54);

    /** Ein Angebot (Rang, Battle Pass oder Schlüssel). key: Schlüssel-Art bei Kisten, sonst leer. */
    public record Offer(String id, String name, String style, String icon, TextColor color, List<String> lines,
                        long price, String command, String key) {}

    /** Inhalt eines Pakets: Schlüssel-Art, Anzahl, Anzeige. */
    public record PackItem(String key, int amount, String label, TextColor color) {}

    public record Pack(String id, String name, String style, String icon, TextColor color, long price, List<PackItem> items) {}

    /** Eine Zeile: price = was jetzt zu zahlen ist (Ränge: nur der Unterschied; Kisten: mal Menge). */
    public record Row(Offer offer, long price, State state, int amount) {}

    public record PackRow(Pack pack, State state) {}

    /** giftTo: Name des Beschenkten oder null. */
    public record View(Page page, long jewels, String giftTo, boolean english, String storeText, List<Row> rows,
                       List<PackRow> packs) {}

    public interface Actions {
        Consumer<Player> page(Page page);

        Consumer<Player> buy(String id);

        Consumer<Player> amount(String id, int amount);

        Consumer<Player> gift();

        String storeUrl();
    }

    // =================================================================================================================
    // Daten aus der Config
    // =================================================================================================================

    private final KubyPlugins plugin;
    /** Je Spieler: gewählte Menge je Schlüssel, scharf geschalteter Kauf, Geschenk-Empfänger. */
    private final Map<UUID, Map<String, Integer>> amounts = new HashMap<>();
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();
    private final Map<UUID, UUID> giftTo = new HashMap<>();

    public JewelShopScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg(String path) {
        return plugin.getConfig().getConfigurationSection("jewelshop-smp." + path);
    }

    private static TextColor color(String hex, TextColor def) {
        TextColor c = hex == null ? null : TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
        return c == null ? def : c;
    }

    private Offer offer(ConfigurationSection s, String id, String style, String icon, TextColor def) {
        List<String> lines = s.getStringList("lines");
        if (lines.isEmpty() && s.isString("line")) lines = List.of(s.getString("line"));
        return new Offer(id, s.getString("name", id), s.getString("style", style), s.getString("icon", icon),
                color(s.getString("color"), def), lines, Math.max(0, s.getLong("price")), s.getString("command", ""),
                s.getString("key", id));
    }

    public List<Offer> ranks() {
        List<Offer> out = new ArrayList<>();
        ConfigurationSection s = cfg("ranks");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r != null) out.add(offer(r, id, "gold", "rank_vip", GOLD));
        }
        return out;
    }

    public Offer pass() {
        ConfigurationSection s = cfg("battlepass");
        if (s == null) return null;
        return offer(s, "battlepass", "pass", "pass", TextColor.color(0xD645EC));
    }

    public List<Offer> crates() {
        List<Offer> out = new ArrayList<>();
        ConfigurationSection s = cfg("crates");
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            ConfigurationSection r = s.getConfigurationSection(id);
            if (r != null) out.add(offer(r, id, "legendary", "key_legendary", GOLD));
        }
        return out;
    }

    public List<Pack> packs() {
        List<Pack> out = new ArrayList<>();
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
                    KeyService.KeyType t = plugin.keys().type(key);
                    String label = p.length > 2 ? p[2].trim() : (t == null ? key : t.name());
                    TextColor col = color(p.length > 3 ? p[3].trim() : (t == null ? null : t.color()), GOLD);
                    items.add(new PackItem(key, Integer.parseInt(p[1].trim()), label, col));
                } catch (NumberFormatException ignored) {
                }
            }
            out.add(new Pack(id, r.getString("name", id), r.getString("style", "gold"), r.getString("icon", "pack_starter"),
                    color(r.getString("color"), GOLD), Math.max(0, r.getLong("price")), items));
        }
        return out;
    }

    private String storeUrl() {
        return plugin.getConfig().getString("jewels.store-url", "");
    }

    private String storeText() {
        String t = plugin.getConfig().getString("jewelshop-smp.store-text", "");
        if (!t.isBlank()) return t;
        String url = storeUrl();
        return url.isBlank() ? "Store" : url.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** /juwelenshop im SMP. Ohne Grafik-Pack: der Truhen-Shop. */
    public void open(Player p, Page page) {
        if (!plugin.uiPack().active(p)) {
            new JewelStoreMenu(plugin, ModeService.SMP, 0).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        Long at = armedAt.get(id);
        if (at != null && System.currentTimeMillis() - at > ARM_MS) {
            armed.remove(id);
            armedAt.remove(id);
        }
        UiDialog.show(p, canvas(view(p, page), actions(page)), HEIGHT);
    }

    /** Geschenk-Modus für target (online), dann die Übersicht. */
    public void openGift(Player p, Player target) {
        giftTo.put(p.getUniqueId(), target.getUniqueId());
        open(p, Page.HOME);
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

    private int amountOf(UUID id, String crate) {
        return amounts.getOrDefault(id, Map.of()).getOrDefault(crate, 1);
    }

    View view(Player p, Page page) {
        UUID id = p.getUniqueId();
        Player to = recipient(p);
        long jewels = plugin.jewels().balance(id);
        String armedId = armed.get(id);
        boolean en = plugin.playerSettings().english(id);
        List<Row> rows = new ArrayList<>();
        List<PackRow> packRows = new ArrayList<>();
        switch (page) {
            case RANKS -> {
                List<Offer> ranks = ranks();
                int owned = ownedRank(to, ranks);
                long paid = owned >= 0 ? ranks.get(owned).price() : 0;
                for (int i = 0; i < ranks.size(); i++) {
                    Offer o = ranks.get(i);
                    if (i <= owned) {
                        rows.add(new Row(o, o.price(), i == owned ? State.OWNED : State.INCLUDED, 1));
                        continue;
                    }
                    long price = Math.max(0, o.price() - paid);
                    rows.add(new Row(o, price, state(jewels, price, armedId, o.id()), 1));
                }
            }
            case PASS -> {
                Offer o = pass();
                if (o != null) {
                    boolean has = plugin.battlepass().hasPremium(to.getUniqueId());
                    rows.add(new Row(o, o.price(), has ? State.OWNED : state(jewels, o.price(), armedId, o.id()), 1));
                }
            }
            case CRATES -> {
                for (Offer o : crates()) {
                    int n = amountOf(id, o.id());
                    long price = o.price() * n;
                    rows.add(new Row(o, price, state(jewels, price, armedId, o.id()), n));
                }
            }
            case PACKS -> {
                for (Pack k : packs()) packRows.add(new PackRow(k, state(jewels, k.price(), armedId, "pack:" + k.id())));
            }
            default -> {
            }
        }
        return new View(page, jewels, to == p ? null : to.getName(), en, storeText(), rows, packRows);
    }

    /** Ansicht ohne Spieler (Selbsttest): Inhalt aus der Config, 0 Juwelen. */
    public View sample(Page page) {
        List<Row> rows = new ArrayList<>();
        List<PackRow> packRows = new ArrayList<>();
        switch (page) {
            case RANKS -> ranks().forEach(o -> rows.add(new Row(o, o.price(), State.MISSING, 1)));
            case PASS -> {
                Offer o = pass();
                if (o != null) rows.add(new Row(o, o.price(), State.MISSING, 1));
            }
            case CRATES -> crates().forEach(o -> rows.add(new Row(o, o.price(), State.MISSING, 1)));
            case PACKS -> packs().forEach(k -> packRows.add(new PackRow(k, State.MISSING)));
            default -> {
            }
        }
        return new View(page, 0, null, false, storeText(), rows, packRows);
    }

    private static State state(long jewels, long price, String armedId, String id) {
        if (jewels < price) return State.MISSING;
        return id.equals(armedId) ? State.ARMED : State.BUY;
    }

    /** Index des höchsten Rangs aus der Liste, den der Spieler hat (LuckPerms group.x oder kuby.rank.x), sonst -1. */
    private static int ownedRank(Player p, List<Offer> ranks) {
        for (int i = ranks.size() - 1; i >= 0; i--) {
            String r = ranks.get(i).id().toLowerCase(Locale.ROOT);
            if (p.hasPermission("group." + r) || p.hasPermission("kuby.rank." + r)) return i;
        }
        return -1;
    }

    private Actions actions(Page page) {
        return new Actions() {
            public Consumer<Player> page(Page target) {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    armed.remove(pl.getUniqueId());
                    open(pl, target);
                };
            }

            public Consumer<Player> buy(String id) {
                return pl -> click(pl, page, id);
            }

            public Consumer<Player> amount(String id, int n) {
                return pl -> {
                    amounts.computeIfAbsent(pl.getUniqueId(), k -> new HashMap<>()).put(id, n);
                    armed.remove(pl.getUniqueId());
                    Sfx.play(plugin, pl, Sfx.Kind.TICK);
                    open(pl, page);
                };
            }

            public Consumer<Player> gift() {
                return pl -> {
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                    if (giftTo.remove(pl.getUniqueId()) != null) {
                        open(pl, Page.HOME);
                        return;
                    }
                    askGift(pl);
                };
            }

            public String storeUrl() {
                return JewelShopScreen.this.storeUrl();
            }
        };
    }

    // =================================================================================================================
    // Kaufen
    // =================================================================================================================

    private void click(Player p, Page page, String id) {
        UUID me = p.getUniqueId();
        View v = view(p, page);
        long price = -1;
        State st = null;
        for (Row r : v.rows()) {
            if (r.offer().id().equals(id)) {
                price = r.price();
                st = r.state();
            }
        }
        for (PackRow r : v.packs()) {
            if (("pack:" + r.pack().id()).equals(id)) {
                price = r.pack().price();
                st = r.state();
            }
        }
        if (st == null || st == State.OWNED || st == State.INCLUDED) return;
        if (st == State.MISSING) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Dir fehlen <white>" + Msg.coins(price - plugin.jewels().balance(me)) + "</white> Juwelen. "
                    + "<gray>Juwelen gibt es im Store" + (storeUrl().isBlank() ? "." : ": <white>" + storeText()));
            return;
        }
        if (st == State.BUY) {
            armed.put(me, id);
            armedAt.put(me, System.currentTimeMillis());
            Sfx.play(plugin, p, Sfx.Kind.TICK);
            open(p, page);
            return;
        }
        armed.remove(me);
        Player to = recipient(p);
        String err = deliver(p, to, page, id, price);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        }
        open(p, page);
    }

    /** Zieht die Juwelen ab und liefert. Gibt eine Fehlermeldung zurück oder null. */
    private String deliver(Player payer, Player to, Page page, String id, long price) {
        String what;
        Runnable give;
        switch (page) {
            case RANKS -> {
                Offer o = ranks().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
                if (o == null || o.command().isBlank()) return "Dieser Rang ist nicht eingerichtet.";
                what = o.name();
                give = () -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        o.command().replace("%player%", to.getName()));
            }
            case PASS -> {
                Offer o = pass();
                if (o == null) return "Der Battle Pass ist nicht eingerichtet.";
                what = o.name();
                give = () -> plugin.battlepass().grantPremium(to.getUniqueId());
            }
            case CRATES -> {
                Offer o = crates().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
                if (o == null || plugin.keys().type(o.key()) == null) return "Diesen Schlüssel gibt es nicht.";
                int n = amountOf(payer.getUniqueId(), id);
                what = n + "x " + o.name();
                give = () -> plugin.keys().give(to.getUniqueId(), ModeService.SMP, o.key(), n);
            }
            case PACKS -> {
                Pack k = packs().stream().filter(r -> ("pack:" + r.id()).equals(id)).findFirst().orElse(null);
                if (k == null) return "Dieses Paket gibt es nicht.";
                what = k.name() + "-Paket";
                give = () -> {
                    for (PackItem it : k.items()) plugin.keys().give(to.getUniqueId(), ModeService.SMP, it.key(), it.amount());
                };
            }
            default -> {
                return null;
            }
        }
        if (!plugin.jewels().take(payer.getUniqueId(), price)) return "Dir fehlen Juwelen.";
        give.run();
        Sfx.play(plugin, payer, Sfx.Kind.SUCCESS_BIG);
        if (to.equals(payer)) {
            Msg.send(payer, "<green>Gekauft: <white>" + Msg.escape(what) + " <gray>(" + Msg.coins(price) + " Juwelen)");
        } else {
            Msg.send(payer, "<green>Du hast <white>" + to.getName() + "</white> " + Msg.escape(what) + " geschenkt <gray>("
                    + Msg.coins(price) + " Juwelen).");
            Msg.send(to, "<light_purple><b>Geschenk!</b> <white>" + payer.getName() + " <gray>hat dir <white>"
                    + Msg.escape(what) + " <gray>geschenkt.");
            Sfx.play(plugin, to, Sfx.Kind.SUCCESS_BIG);
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

    public void forget(UUID id) {
        amounts.remove(id);
        armed.remove(id);
        armedAt.remove(id);
        giftTo.remove(id);
    }

    // =================================================================================================================
    // Zeichnen
    // =================================================================================================================

    static KitData.Box box(String style) {
        try {
            return KitData.Box.valueOf("JS_" + style.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return KitData.Box.JS_GOLD;
        }
    }

    static KitData.Sprite sprite(String name) {
        try {
            return KitData.Sprite.valueOf("JS_" + name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return KitData.Sprite.JS_EMERALD;
        }
    }

    /** Text mittig um cx (bei ungerader Breite 1 px weiter links, wie in der Referenz). */
    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        int w = Canvas.width(Canvas.clean(text), bold);
        c.text(cx - (w + 1) / 2, y, text, color, bold);
    }

    private static void center(Canvas c, int cx, int y, List<Span> spans) {
        int w = 0;
        for (Span s : spans) w += Canvas.width(Canvas.clean(s.text()), s.bold());
        c.text(cx - (w + 1) / 2, y, spans);
    }

    /** Rechts im Kopf: Smaragd, „Deine / Juwelen“, Anzahl. */
    private static void balance(Canvas c, long jewels, boolean en) {
        String n = Msg.coins(jewels);
        int nw = Canvas.width(n, true);
        int nx = 380 - nw;
        int lx = Math.min(328, nx - 45);
        c.sprite(lx - 20, 11, KitData.Sprite.JS_EMERALD);
        c.text(lx, 9, en ? "Your" : "Deine", SUB, false);
        c.text(lx, 19, en ? "jewels" : "Juwelen", SUB, false);
        c.text(nx, 14, n, GREEN, true);
    }

    private static void subHead(Canvas c, View v, String title, TextColor color, String sub, Actions a) {
        c.box(0, 0, W, 46, KitData.Box.JS_HEAD);
        c.sprite(10, 9, KitData.Sprite.JS_BACK);
        if (a != null) c.action(10, 9, 22, 22, a.page(Page.HOME), Component.text(v.english() ? "Back" : "Zurück", WHITE));
        c.text(42, 11, title.toUpperCase(Locale.GERMAN), color, true);
        c.text(42, 26, v.giftTo() != null ? (v.english() ? "Gift for " : "Geschenk für ") + v.giftTo() : sub, SUB, false);
        balance(c, v.jewels(), v.english());
    }

    public static Canvas canvas(View v, Actions a) {
        return switch (v.page()) {
            case HOME -> home(v, a);
            case PACKS -> packs(v, a);
            default -> rows(v, a);
        };
    }

    private static Canvas home(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        c.box(0, 0, W, 46, KitData.Box.JS_HEAD);
        c.sprite(4, 7, KitData.Sprite.JS_EMERALD_BIG);
        c.sprite(46, 8, KitData.Sprite.JS_TITLE);
        c.text(47, 30, v.giftTo() != null ? (en ? "→ Gift for " : "→ Geschenk für ") + v.giftTo()
                : en ? "→ Ranks · Battlepass · Crates · Bundles" : "→ Ränge · Battlepass · Crates · Vorteilspakete", SUB, false);
        balance(c, v.jewels(), en);
        card(c, a, 0, 50, "gold", KitData.Sprite.JS_CROWN, 10, 9, en ? "RANKS" : "RÄNGE", GOLD,
                en ? "Exclusive features" : "Exklusive Features", en ? "& strong perks" : "& starke Vorteile",
                en ? "VIEW RANKS" : "RÄNGE ANSEHEN", Page.RANKS);
        card(c, a, 198, 50, "blue", KitData.Sprite.JS_TICKET, 8, 11, "BATTLEPASS", BLUE,
                en ? "Exclusive rewards" : "Exklusive Belohnungen", en ? "& cool items" : "& coole Items",
                en ? "VIEW BATTLEPASS" : "BATTLEPASS ANSEHEN", Page.PASS);
        card(c, a, 0, 140, "red", KitData.Sprite.JS_CRATE, 8, 10, "CRATES", RED,
                en ? "Cool keys for" : "Coole Keys für", en ? "exclusive items" : "exklusive Items",
                en ? "VIEW CRATES" : "CRATES ANSEHEN", Page.CRATES);
        card(c, a, 198, 140, "violet", KitData.Sprite.JS_CHEST, 8, 7, en ? "BUNDLES" : "VORTEILSPAKETE", VIOLET,
                en ? "The best deals" : "Die besten Deals", en ? "& exclusive bundles" : "& exklusive Pakete",
                en ? "VIEW BUNDLES" : "PAKETE ANSEHEN", Page.PACKS);
        // unten: Geschenk und Store
        boolean gift = v.giftTo() != null;
        c.box(0, 234, 192, 30, KitData.Box.JS_GREEN);
        c.sprite(4, 237, KitData.Sprite.JS_GIFT);
        c.text(34, 238, gift ? (en ? "Gift for " : "Geschenk an ") + v.giftTo() : en ? "As a gift" : "Als Geschenk", GIFT, true);
        c.text(34, 250, gift ? (en ? "Click: back to you" : "Klick: wieder für dich") : en ? "for other players" : "für andere Spieler", SUB, false);
        c.text(178, 245, "›", GIFT, true);
        if (a != null) c.action(0, 234, 192, 30, a.gift(), tip(gift ? (en ? "End gift mode" : "Geschenk beenden") : en ? "As a gift" : "Als Geschenk",
                gift ? (en ? "Purchases are for you again." : "Käufe sind wieder für dich.")
                        : en ? "Buy ranks, the pass, keys and bundles for another player, paid with your jewels."
                        : "Ränge, Battle Pass, Schlüssel und Pakete für einen anderen Spieler kaufen, bezahlt mit deinen Juwelen.",
                en ? "Click: choose player" : "Klick: Spieler wählen"));
        c.box(198, 234, 192, 30, KitData.Box.JS_STORE);
        c.sprite(202, 237, KitData.Sprite.JS_CART);
        c.text(232, 238, en ? "To the store" : "Zum Store", STORE, true);
        c.text(232, 250, Canvas.fit(v.storeText(), false, 140), SUB, false);
        c.text(376, 245, "›", STORE, true);
        if (a != null && !a.storeUrl().isBlank()) {
            c.actionUrl(198, 234, 192, 30, a.storeUrl(), tip(en ? "To the store" : "Zum Store",
                    en ? "Get jewels in the store." : "Juwelen gibt es im Store.", en ? "Click: open link" : "Klick: Link öffnen"));
        }
        return c;
    }

    private static void card(Canvas c, Actions a, int x, int y, String style, KitData.Sprite icon, int ix, int iy,
                             String title, TextColor color, String l1, String l2, String button, Page target) {
        KitData.Box b = box(style);
        c.box(x, y, 192, 80, b);
        c.sprite(x + ix, y + iy, icon);
        c.text(x + 62, y + 10, title, color, true);
        c.text(x + 62, y + 25, l1, DESC, false);
        c.text(x + 62, y + 36, l2, DESC, false);
        c.box(x + 6, y + 58, 180, 18, b, 1);
        center(c, x + 96, y + 63, button, color, true);
        c.text(x + 174, y + 63, "›", color, true);
        if (a != null) c.action(x, y, 192, 80, a.page(target), tip(title, l1 + " " + l2, "Klick: öffnen"));
    }

    private static Canvas rows(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        String footer = null;
        switch (v.page()) {
            case RANKS -> {
                subHead(c, v, en ? "Ranks" : "Ränge", GOLD, en ? "Exclusive features & strong perks"
                        : "Exklusive Features & starke Vorteile", a);
                footer = en ? "With a rank you only pay the difference when upgrading."
                        : "Mit Rang zahlst du beim Aufstieg nur den Unterschied.";
            }
            case PASS -> subHead(c, v, "Battlepass", BLUE, en ? "Exclusive rewards & cool items"
                    : "Exklusive Belohnungen & coole Items", a);
            default -> {
                subHead(c, v, "Crates", RED, en ? "Cool keys for exclusive items" : "Coole Keys für exklusive Items", a);
                footer = en ? "The keys arrive at once." : "Die Schlüssel landen sofort bei dir.";
            }
        }
        int y = 49;
        for (Row r : v.rows()) {
            Offer o = r.offer();
            KitData.Box b = box(o.style());
            c.box(0, y, W, 50, b);
            KitData.Sprite icon = sprite(o.icon());
            // Icon mittig im Feld links (wie in der Referenz: Mitte bei x 24, y 25)
            c.sprite(24 - (icon.width + 1) / 2, y + 25 - (icon.height + 1) / 2, icon);
            c.text(48, y + 9, o.name().toUpperCase(Locale.GERMAN), o.color(), true);
            if (v.page() == Page.CRATES) {
                if (!o.lines().isEmpty()) c.text(48, y + 21, Canvas.fit(o.lines().get(0), false, 216), DESC, false);
                c.text(48, y + 34, en ? "Amount" : "Menge", SUB, false);
                for (int i = 0; i < AMOUNTS.length; i++) {
                    int px = 84 + 27 * i;
                    boolean on = AMOUNTS[i] == r.amount();
                    c.sprite(px, y + 32, on ? pill(o.style()) : KitData.Sprite.JS_PILL_OFF);
                    center(c, px + 12, y + 35, String.valueOf(AMOUNTS[i]), on ? DARK : DESC, false);
                    if (a != null && !on) c.action(px, y + 32, 24, 14, a.amount(o.id(), AMOUNTS[i]),
                            Component.text((en ? "Amount " : "Menge ") + AMOUNTS[i], WHITE));
                }
            } else {
                for (int i = 0; i < Math.min(2, o.lines().size()); i++) {
                    c.text(48, y + 21 + 11 * i, Canvas.fit(o.lines().get(i), false, 216), DESC, false);
                }
            }
            price(c, 325, y + 7, r.price());
            button(c, a, 270, y + 23, 110, r.state(), r.price(), v.jewels(), en, o.id(), tipFor(v, r, en));
            y += 54;
        }
        if (footer != null) center(c, W / 2, y + 5, footer, SUB, false);
        return c;
    }

    private static KitData.Sprite pill(String style) {
        return switch (style) {
            case "platin", "blue", "store" -> KitData.Sprite.JS_PILL_AURAN;
            case "cyborg", "green" -> KitData.Sprite.JS_PILL_CYBORG;
            default -> KitData.Sprite.JS_PILL_LEGENDARY;
        };
    }

    /** Smaragd und Preis, zusammen mittig um cx. */
    private static void price(Canvas c, int cx, int y, long price) {
        String n = Msg.coins(price);
        int w = Canvas.width(n, true);
        int x = cx - (17 + w + 1) / 2;
        c.sprite(x, y, KitData.Sprite.JS_EMERALD_PRICE);
        c.text(x + 17, y + 4, n, GREEN, true);
    }

    private static void button(Canvas c, Actions a, int x, int y, int w, State st, long price, long jewels, boolean en,
                               String id, Component tip) {
        int cx = x + w / 2;
        switch (st) {
            case MISSING -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, List.of(Span.of(en ? "You need " : "Dir fehlen ", DESC),
                        Span.of(Msg.coins(price - jewels), GREEN)));
            }
            case BUY -> {
                c.box(x, y, w, 18, KitData.Box.JS_GREEN, 1);
                center(c, cx, y + 5, en ? "BUY" : "KAUFEN", BUY, true);
            }
            case ARMED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GOLD, 1);
                center(c, cx, y + 5, en ? "CONFIRM" : "BESTÄTIGEN", GOLD, true);
            }
            case OWNED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, en ? "Owned" : "Besitzt du", GREEN, false);
            }
            case INCLUDED -> {
                c.box(x, y, w, 18, KitData.Box.JS_GRAY, 1);
                center(c, cx, y + 5, en ? "Included" : "Enthalten", SUB, false);
            }
        }
        if (a != null && st != State.OWNED && st != State.INCLUDED) c.action(x, y, w, 18, a.buy(id), tip);
    }

    private static Component tipFor(View v, Row r, boolean en) {
        Offer o = r.offer();
        TextComponent.Builder b = Component.text();
        b.append(Component.text(o.name(), o.color()).decorate(TextDecoration.BOLD));
        for (String l : o.lines()) b.append(Component.newline()).append(Component.text(l, DESC));
        b.append(Component.newline()).append(Component.newline());
        if (v.page() == Page.RANKS && r.price() < o.price()) {
            b.append(Component.text((en ? "Upgrade: " : "Aufstieg: ") + Msg.coins(r.price()) + (en ? " instead of " : " statt ")
                    + Msg.coins(o.price()) + (en ? " jewels" : " Juwelen"), GREEN)).append(Component.newline());
        }
        if (v.giftTo() != null) {
            b.append(Component.text((en ? "Gift for " : "Geschenk für ") + v.giftTo(), GIFT)).append(Component.newline());
        }
        b.append(Component.text(switch (r.state()) {
            case MISSING -> en ? "Not enough jewels" : "Nicht genug Juwelen";
            case BUY -> en ? "Click: buy (then confirm)" : "Klick: kaufen (danach bestätigen)";
            case ARMED -> en ? "Click again: buy now" : "Nochmal klicken: jetzt kaufen";
            case OWNED -> en ? "You already have it" : "Hast du schon";
            case INCLUDED -> en ? "Included in your rank" : "In deinem Rang enthalten";
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

    private static Canvas packs(View v, Actions a) {
        boolean en = v.english();
        Canvas c = new Canvas(W).heading("Juwelenshop", TextColor.color(0x54FC54));
        subHead(c, v, en ? "Bundles" : "Vorteilspakete", VIOLET, en ? "The best deals & exclusive bundles"
                : "Die besten Deals & exklusive Pakete", a);
        int x = 0;
        for (PackRow pr : v.packs()) {
            Pack k = pr.pack();
            KitData.Box b = box(k.style());
            c.box(x, 53, 126, 157, b);
            KitData.Sprite icon = sprite(k.icon());
            c.sprite(x + 63 - (icon.width + 1) / 2, 60, icon);
            center(c, x + 63, 104, k.name().toUpperCase(Locale.GERMAN), k.color(), true);
            center(c, x + 63, 114, en ? "Bundle" : "Vorteils-Paket", SUB, false);
            int ly = 126;
            for (PackItem it : k.items()) {
                if (ly > 153) break;
                c.text(x + 16, ly, List.of(Span.of("■ ", it.color()), Span.of(it.amount() + "x ", WHITE), Span.of(it.label(), it.color())));
                ly += 9;
            }
            // Preis: Smaragd, Zahl fett, "Juwelen", zusammen mittig
            String n = Msg.coins(k.price()) + " ";
            String unit = en ? "jewels" : "Juwelen";
            int w = 18 + Canvas.width(n, true) + Canvas.width(unit, false);
            int px = x + 63 - (w + 1) / 2;
            c.sprite(px, 165, KitData.Sprite.JS_EMERALD_PRICE);
            c.text(px + 18, 169, List.of(Span.bold(n, GREEN), Span.of(unit, SUB)));
            Row asRow = new Row(new Offer("pack:" + k.id(), k.name(), k.style(), k.icon(), k.color(), packLines(k), k.price(), "", ""),
                    k.price(), pr.state(), 1);
            button(c, a, x + 6, 189, 114, pr.state(), k.price(), v.jewels(), en, "pack:" + k.id(), tipFor(v, asRow, en));
            x += 132;
        }
        center(c, W / 2, 217, en ? "The keys arrive at once." : "Die Schlüssel landen sofort bei dir.", SUB, false);
        return c;
    }

    private static List<String> packLines(Pack k) {
        List<String> out = new ArrayList<>();
        for (PackItem it : k.items()) out.add(it.amount() + "x " + it.label());
        return out;
    }
}
