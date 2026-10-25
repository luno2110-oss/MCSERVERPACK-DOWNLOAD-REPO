package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.service.CosmeticService.Currency;
import de.kuby.kubyplugins.service.CosmeticService.Item;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /cosmetics auf dem Dialog-Kit (Kopf wie die Hilfe-Seiten): Reiter je Art (Hüte, Trails, Kill-Effekte, Teleport)
 * und, wenn es /pet gibt, „Haustiere ›“. Kacheln in der Farbe der Seltenheit mit Icon (Hut-Rendering bzw. Partikel),
 * Name und Status; Klick kauft (zweimal klicken) oder legt an/ab. Ohne Pack gibt es die Truhe (CosmeticsMenu).
 */
public final class CosmeticsScreen {

    private static final int W = 390, HEIGHT = 330, TW = 127, TH = 44, GAP = 4;
    private static final TextColor ACCENT = Ui.Theme.PINK.accent, GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x6CE86E), JEWEL = TextColor.color(0x5FE3A0), PURPLE = TextColor.color(0xD08CFF),
            NAV = TextColor.color(0xC0C4CB), DIM = TextColor.color(0x80808A);

    /** Reiter: Art -> Beschriftung. */
    private static final Map<String, String> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("HAT", "Hüte");
        KINDS.put("TRAIL", "Trails");
        KINDS.put("KILL", "Kill-Effekte");
        KINDS.put("TELEPORT", "Teleport");
    }

    public enum State { ACTIVE, OWNED, BUY, ARMED, LOCKED }

    public record Tile(String id, String name, CosmeticService.Rarity rarity, KitData.Icon icon, boolean bigIcon, State state,
                       String status) {}

    public record View(List<String[]> tabs, String tab, List<Tile> tiles, long jewels, boolean pets, int page, String hint) {}

    public interface Actions {
        Consumer<Player> tab(String kind);

        Consumer<Player> click(String id);

        Consumer<Player> pets();

        Consumer<Player> page(int page);
    }

    private final KubyPlugins plugin;
    private final Map<UUID, String> armed = new HashMap<>();

    public CosmeticsScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (plugin.uiPack().active(p)) show(p, null, 0);
        else new CosmeticsMenu(plugin).open(p);
    }

    static KitData.Icon iconOf(Item it) {
        String name = it.kind().equals("HAT") ? "HAT_" + it.model() : "P_" + (it.particle() == null ? "" : it.particle().name());
        try {
            return KitData.Icon.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return KitData.Icon.STAR;
        }
    }

    private static String price(Item it) {
        if (it.currency() == Currency.CRATE) return "nur aus Kisten";
        if (it.price() <= 0 || it.currency() == Currency.NONE) return it.bpLevel() > 0 ? "Battlepass Stufe " + it.bpLevel() : "gratis";
        return switch (it.currency()) {
            case MONEY -> Msg.coins(it.price()) + " Coins";
            case XP -> Msg.coins(it.price()) + " XP";
            case JEWELS -> Msg.coins(it.price()) + " Juwelen";
            default -> "gratis";
        };
    }

    public void show(Player p, String tab, int page) {
        UUID me = p.getUniqueId();
        CosmeticService cs = plugin.cosmetics();
        List<Item> all = cs.catalogue(me);
        List<String[]> tabs = new ArrayList<>();
        for (Map.Entry<String, String> k : KINDS.entrySet()) {
            if (all.stream().anyMatch(i -> i.kind().equals(k.getKey()))) tabs.add(new String[]{k.getKey(), k.getValue()});
        }
        String t = tab != null ? tab : tabs.isEmpty() ? "TRAIL" : tabs.get(0)[0];
        String sure = armed.get(me);
        List<Tile> tiles = new ArrayList<>();
        for (Item it : all) {
            if (!it.kind().equals(t)) continue;
            boolean own = cs.owns(me, it);
            boolean active = it.id().equals(cs.activeId(me, it.kind()));
            boolean locked = !own && (it.currency() == Currency.CRATE || plugin.battlepass().level(me) < it.bpLevel());
            State st = active ? State.ACTIVE : own ? State.OWNED : locked ? State.LOCKED : it.id().equals(sure) ? State.ARMED : State.BUY;
            String status = switch (st) {
                case ACTIVE -> "angelegt · Klick: ablegen";
                case OWNED -> "im Besitz · Klick: anlegen";
                case ARMED -> "Nochmal klicken: kaufen";
                default -> price(it);
            };
            tiles.add(new Tile(it.id(), it.label(), it.rarity(), iconOf(it), true, st, status));
        }
        boolean pets = plugin.getServer().getPluginCommand("pet") != null
                && de.kuby.kubyplugins.service.ModeService.SURVIVAL.equals(plugin.modes().modeOf(me));
        String hint = t.equals("HAT") ? "Hüte sitzen auf deinem Kopf und drehen sich mit. Selbst sehen: /settings" : null;
        View v = new View(tabs, t, tiles, plugin.jewels().balance(me), pets, page, hint);
        String cur = t;
        UiDialog.show(p, canvas(v, new Actions() {
            public Consumer<Player> tab(String kind) {
                return pl -> {
                    armed.remove(pl.getUniqueId());
                    show(pl, kind, 0);
                };
            }

            public Consumer<Player> click(String id) {
                return pl -> {
                    handle(pl, id);
                    show(pl, cur, page);
                };
            }

            public Consumer<Player> pets() {
                return pl -> {
                    pl.closeDialog();
                    pl.performCommand("pet");
                };
            }

            public Consumer<Player> page(int n) {
                return pl -> show(pl, cur, n);
            }
        }), HEIGHT);
    }

    private void handle(Player p, String id) {
        UUID me = p.getUniqueId();
        CosmeticService cs = plugin.cosmetics();
        Item it = cs.find(id);
        if (it == null) return;
        if (cs.owns(me, it)) {
            armed.remove(me);
            boolean was = it.id().equals(cs.activeId(me, it.kind()));
            cs.toggle(me, it);
            Sfx.play(plugin, p, Sfx.Kind.CLICK);
            Msg.send(p, "<#FF7AC0>" + it.label() + "</#FF7AC0> <gray>" + (was ? "abgelegt." : "angelegt."));
            return;
        }
        if (it.currency() == Currency.CRATE) {
            Msg.send(p, "<gray>" + it.label() + " gibt es nur aus Kisten. <white>/keys");
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            return;
        }
        if (!id.equals(armed.get(me)) && it.price() > 0) {
            armed.put(me, id);
            Sfx.play(plugin, p, Sfx.Kind.CLICK);
            return;
        }
        armed.remove(me);
        String err = cs.buy(p, it);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
        } else {
            cs.toggle(me, it);
            Msg.send(p, "<green>" + it.label() + " gekauft und angelegt!");
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        }
    }

    private static KitData.Box boxOf(CosmeticService.Rarity r) {
        return switch (r) {
            case COMMON -> KitData.Box.GRAY;
            case RARE -> KitData.Box.TEAL;
            case EPIC -> KitData.Box.PURPLE;
            case LEGENDARY -> KitData.Box.GOLD;
        };
    }

    private static TextColor colorOf(CosmeticService.Rarity r) {
        return switch (r) {
            case COMMON -> LIGHT;
            case RARE -> TextColor.color(0x64D8FF);
            case EPIC -> PURPLE;
            case LEGENDARY -> GOLD;
        };
    }

    /** Nur Darstellung (auch für Vorschauen ohne Server). */
    public static Canvas canvas(View v, Actions a) {
        Canvas c = new Canvas(W).heading("Kosmetik", net.kyori.adventure.text.format.TextColor.color(0xD08CFF));
        c.box(0, 0, W, 51, KitData.Box.PURPLE);
        c.smallIcon(13, 18, KitData.SmallIcon.H_HAT);
        c.text(41, 15, "KOSMETIK", Ui.Theme.PINK.title, true);
        c.text(41, 28, "Hüte, Trails und Haustiere", GRAY, false);
        c.icon(W - 12 - 16 - Canvas.width(Msg.coins(v.jewels()), true) - 3, 18, KitData.Icon.EMERALD, false);
        c.textRight(378, 21, List.of(Span.bold(Msg.coins(v.jewels()), JEWEL)));
        int y = 53;

        // Reiter
        int x = 0;
        for (String[] t : v.tabs()) {
            boolean on = t[0].equals(v.tab());
            int w = Canvas.width(t[1], true) + 14;
            c.smallButton(x, y, w, on ? KitData.Small.GREEN : KitData.Small.DARK, t[1], on ? WHITE : LIGHT, true);
            if (!on) c.action(x, y, w, KitData.SMALL_H, a.tab(t[0]), t[1]);
            x += w + 3;
        }
        if (v.pets()) {
            String label = "Haustiere ›";
            int w = Canvas.width(label, true) + 14;
            c.smallButton(W - w, y, w, KitData.Small.GOLD, label, GOLD, true);
            c.action(W - w, y, w, KitData.SMALL_H, a.pets(), "Haustier-Menü öffnen (/pet)");
        }
        y += 20;

        // Kacheln
        int per = 12;
        List<Tile> tiles = v.tiles();
        int pages = Math.max(1, (tiles.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(v.page(), pages - 1));
        for (int i = pg * per; i < Math.min(tiles.size(), pg * per + per); i++) {
            Tile t = tiles.get(i);
            int k = i - pg * per;
            int tx = (k % 3) * (TW + GAP), ty = y + (k / 3) * (TH + 3);
            c.box(tx, ty, TW, TH, boxOf(t.rarity()));
            if (t.bigIcon()) c.icon(tx + 5, ty + 6, t.icon(), true);
            else c.icon(tx + 13, ty + 14, t.icon(), false);
            int tx2 = tx + 42, room = TW - 42 - 6;
            c.text(tx2, ty + 9, Canvas.fit(t.name(), true, room), colorOf(t.rarity()), true);
            c.text(tx2, ty + 20, t.rarity().label, DIM, false);
            TextColor sc = switch (t.state()) {
                case ACTIVE -> GREEN;
                case OWNED -> LIGHT;
                case ARMED -> GOLD;
                case LOCKED -> GRAY;
                case BUY -> t.status().contains("Juwelen") ? JEWEL : GOLD;
            };
            String st = t.state() == State.ACTIVE ? "✔ angelegt" : t.state() == State.OWNED ? "im Besitz"
                    : t.state() == State.ARMED ? "Kaufen?" : t.status();
            c.text(tx2, ty + 31, Canvas.fit(st, t.state() == State.ACTIVE || t.state() == State.ARMED, room), sc,
                    t.state() == State.ACTIVE || t.state() == State.ARMED);
            c.action(tx, ty, TW, TH, a.click(t.id()), t.name() + ": " + t.status());
        }
        if (tiles.isEmpty()) {
            c.box(0, y, W, 34, KitData.Box.PURPLE);
            c.text(12, y + 13, "Hier gibt es in diesem Modus nichts.", LIGHT, false);
        }
        int rows = Math.max(1, (Math.min(tiles.size() - pg * per, per) + 2) / 3);
        y += rows * (TH + 3) + 1;

        if (v.hint() != null) c.text(4, y + 7, v.hint(), DIM, false);
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
