package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.I18n;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.shops.WarpService.Category;
import de.kuby.kubysurvival.shops.WarpService.Warp;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /sw mit Pack: Spielerwarps als Dialog (WarpScreen). Merkt sich je Spieler Filter, Sortierung, Suche und Seite.
 * Suchen, Kaufen und Bearbeiten sind Dialoge mit Feldern unter dem Kopf; ohne Pack bleibt das alte Truhen-Menü.
 */
public final class WarpDialogs {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();
    private static final TextColor OK = TextColor.color(0x6DE288), BACK = TextColor.color(0x7CEFE3),
            RED = TextColor.color(0xFC7986), GRAY = TextColor.color(0x9FA1A7);

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final Map<UUID, State> states = new HashMap<>();

    /** Ansicht eines Spielers: Filter (null = alle), beliebt oder neueste zuerst, Suche, Seite. */
    private static final class State {
        Category filter;
        boolean popular = true;
        String search = "";
        int page;
    }

    private final WarpScreen.Actions actions = new WarpScreen.Actions() {
        public Consumer<Player> list() {
            return WarpDialogs.this::list;
        }

        public Consumer<Player> mine() {
            return WarpDialogs.this::mine;
        }

        public Consumer<Player> search() {
            return WarpDialogs.this::askSearch;
        }

        public Consumer<Player> sort() {
            return p -> {
                State s = state(p);
                s.popular = !s.popular;
                s.page = 0;
                WarpDialogs.this.list(p);
            };
        }

        public Consumer<Player> filter(Category c) {
            return p -> {
                State s = state(p);
                s.filter = c;
                s.page = 0;
                WarpDialogs.this.list(p);
            };
        }

        public Consumer<Player> page(int page) {
            return p -> {
                state(p).page = page;
                WarpDialogs.this.list(p);
            };
        }

        public Consumer<Player> pin(String warp) {
            return p -> {
                Warp w = addon.warps().get(warp);
                if (w != null) addon.warps().togglePin(p.getUniqueId(), w);
                WarpDialogs.this.list(p);
            };
        }

        public Consumer<Player> teleport(String warp) {
            return p -> {
                Warp w = addon.warps().get(warp);
                if (w == null) {
                    err(p, "Diesen Warp gibt es nicht mehr.");
                    WarpDialogs.this.list(p);
                    return;
                }
                p.closeInventory();
                addon.warps().travel(p, w);
            };
        }

        public Consumer<Player> buy(int slot) {
            return p -> askBuy(p, slot);
        }

        public Consumer<Player> edit(String warp) {
            return p -> askEdit(p, warp);
        }
    };

    public WarpDialogs(KubySurvival addon) {
        this.addon = addon;
        this.core = addon.core();
    }

    /** Öffnet die Liste, wenn der Spieler das Pack hat. Sonst false (dann das alte Menü). */
    public boolean open(Player p) {
        if (!core.uiPack().active(p)) return false;
        list(p);
        return true;
    }

    /** Öffnet „Meine Warps“, wenn der Spieler das Pack hat. */
    public boolean openMine(Player p) {
        if (!core.uiPack().active(p)) return false;
        mine(p);
        return true;
    }

    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new State());
    }

    private void err(Player p, String e) {
        Msg.send(p, "<red>" + Msg.escape(I18n.ctx(e)));
        Sfx.play(core, p, Sfx.Kind.ERROR);
    }

    // =================================================================================================================
    // Liste und Meine Warps
    // =================================================================================================================

    private static String ownerName(Warp w) {
        String n = Bukkit.getOfflinePlayer(w.owner).getName();
        return n == null ? "?" : n;
    }

    private static String coords(Location l) {
        return l == null ? "?" : l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    private WarpScreen.Card card(Player viewer, Warp w) {
        WarpService ws = addon.warps();
        return new WarpScreen.Card(w.name, WarpService.COLORS.getOrDefault(w.color, 0x54FC54), ownerName(w), w.category,
                WarpService.placeOf(w), coords(w.location), ws.weekVisits(w), w.visits, w.desc,
                ws.pinned(viewer.getUniqueId(), w));
    }

    private static boolean matches(Warp w, String owner, String q) {
        if (q.isEmpty()) return true;
        String s = q.toLowerCase(Locale.ROOT);
        return w.name.toLowerCase(Locale.ROOT).contains(s) || owner.toLowerCase(Locale.ROOT).contains(s)
                || w.desc.toLowerCase(Locale.ROOT).contains(s) || w.place.toLowerCase(Locale.ROOT).contains(s);
    }

    void list(Player p) {
        WarpService ws = addon.warps();
        State s = state(p);
        List<Warp> found = new ArrayList<>();
        for (Warp w : ws.all()) {
            if (s.filter != null && w.category != s.filter) continue;
            if (!matches(w, ownerName(w), s.search)) continue;
            found.add(w);
        }
        Comparator<Warp> order = s.popular
                ? Comparator.comparingLong((Warp w) -> -ws.weekVisits(w)).thenComparingLong(w -> -w.visits)
                : Comparator.comparingLong((Warp w) -> -w.created);
        found.sort(Comparator.comparing((Warp w) -> !ws.pinned(p.getUniqueId(), w)).thenComparing(order)
                .thenComparing(w -> w.name.toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (found.size() + WarpScreen.PER_PAGE - 1) / WarpScreen.PER_PAGE);
        s.page = Math.max(0, Math.min(s.page, pages - 1));
        List<WarpScreen.Card> cards = new ArrayList<>();
        for (int i = s.page * WarpScreen.PER_PAGE; i < Math.min(found.size(), (s.page + 1) * WarpScreen.PER_PAGE); i++) {
            cards.add(card(p, found.get(i)));
        }
        long coins = core.economy().balance(p.getUniqueId());
        UiDialog.show(p, WarpScreen.list(new WarpScreen.ListView(coins, s.filter, s.popular, s.search, cards, s.page, pages),
                actions), WarpScreen.HEIGHT);
    }

    void mine(Player p) {
        WarpService ws = addon.warps();
        List<Warp> own = ws.of(p.getUniqueId());
        own.sort(Comparator.comparingLong(w -> w.created));
        List<Long> prices = ws.prices();
        long coins = core.economy().balance(p.getUniqueId());
        List<WarpScreen.Slot> slots = new ArrayList<>();
        for (int i = 0; i < Math.max(prices.size(), own.size()); i++) {
            long price = i < prices.size() ? prices.get(i) : prices.get(prices.size() - 1);
            if (i < own.size()) slots.add(new WarpScreen.Slot(i + 1, price, card(p, own.get(i)), false, 0));
            else if (i == own.size()) slots.add(new WarpScreen.Slot(i + 1, price, null, true, Math.max(0, price - coins)));
            else slots.add(new WarpScreen.Slot(i + 1, price, null, false, 0));
        }
        UiDialog.show(p, WarpScreen.mine(new WarpScreen.MineView(coins, slots), actions), WarpScreen.HEIGHT);
    }

    // =================================================================================================================
    // Dialoge mit Feldern
    // =================================================================================================================

    private static Component label(String symbol, String text, TextColor color) {
        return Component.text(symbol + " ", color).append(Component.text(I18n.ctx(text), color));
    }

    private static ActionButton button(String symbol, String text, TextColor color, Consumer<Player> click) {
        return ActionButton.create(label(symbol, text, color), null, 149, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) click.accept(pl);
        }, ONCE));
    }

    private static DialogInput field(String key, String label, String initial, int max) {
        return DialogInput.text(key, Msg.parse(label.isEmpty() ? "" : "<#9FA1A7>" + Msg.escape(I18n.ctx(label))))
                .width(300).maxLength(max).initial(initial).labelVisible(!label.isEmpty()).build();
    }

    private void askSearch(Player p) {
        State s = state(p);
        DialogAction apply = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String q = response.getText("suche");
            State st = state(pl);
            st.search = q == null ? "" : q.trim();
            st.page = 0;
            list(pl);
        }, ONCE);
        ActionButton ok = ActionButton.create(label("✔", "Übernehmen", OK), null, 149, apply);
        de.kuby.kubyplugins.ui.UiDialog.open(p, UiDialog.dialog(p, WarpScreen.searchHead(), List.of(field("suche", "", s.search, 32)),
                List.of(ok, button("⇦", "Zurück", BACK, this::list)), 2));
    }

    private void askBuy(Player p, int slot) {
        WarpService ws = addon.warps();
        long price = ws.nextPrice(p.getUniqueId());
        if (price < 0) {
            err(p, "Du hast schon alle Warp-Plätze belegt.");
            mine(p);
            return;
        }
        long coins = core.economy().balance(p.getUniqueId());
        DialogAction buy = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String name = response.getText("name");
            String e = ws.create(pl, name == null ? "" : name.trim());
            if (e != null) {
                err(pl, e);
                mine(pl);
                return;
            }
            Sfx.play(core, pl, Sfx.Kind.SUCCESS);
            Msg.send(pl, "<green>" + Msg.escape(I18n.ctx("Warp gekauft. Stell noch Ort, Beschreibung, Kategorie und Farbe ein.")));
            askEdit(pl, name.trim());
        }, ONCE);
        String sub = "Platz " + slot + " · " + Msg.coins(price) + " Coins · genau hier, wo du stehst";
        ActionButton ok = ActionButton.create(label("✔", "Kaufen", OK), null, 149, buy);
        de.kuby.kubyplugins.ui.UiDialog.open(p, UiDialog.dialog(p, WarpScreen.formHead(coins, "Warp kaufen", sub),
                List.of(field("name", "Name · 3 bis 16 Zeichen: Buchstaben, Zahlen, _ oder -", "", 16)),
                List.of(ok, button("⇦", "Zurück", BACK, this::mine)), 2));
    }

    private void askEdit(Player p, String warp) {
        WarpService ws = addon.warps();
        Warp w = ws.get(warp);
        if (w == null || (!w.owner.equals(p.getUniqueId()) && !p.hasPermission("kuby.admin"))) {
            err(p, "Diesen Warp hast du nicht.");
            mine(p);
            return;
        }
        List<SingleOptionDialogInput.OptionEntry> cats = new ArrayList<>();
        for (Category c : Category.values()) {
            cats.add(SingleOptionDialogInput.OptionEntry.create(c.id, Component.text(I18n.ctx(c.label), WarpScreen.label(c)),
                    c == w.category));
        }
        List<SingleOptionDialogInput.OptionEntry> colors = new ArrayList<>();
        for (Map.Entry<String, Integer> e : WarpService.COLORS.entrySet()) {
            colors.add(SingleOptionDialogInput.OptionEntry.create(e.getKey(),
                    Component.text(I18n.ctx(e.getKey()), TextColor.color(e.getValue())), e.getKey().equals(w.color)));
        }
        List<DialogInput> inputs = List.of(
                field("ort", "Ort · z. B. Mein Shop (bis 24 Zeichen, leer: Grundstück)", w.place, 24),
                field("text", "Beschreibung · was es dort gibt (bis 60 Zeichen)", w.desc, 60),
                DialogInput.singleOption("kategorie", Component.text(I18n.ctx("Kategorie"), GRAY), cats).width(300).build(),
                DialogInput.singleOption("farbe", Component.text(I18n.ctx("Farbe des Namens"), GRAY), colors).width(300).build());
        DialogAction save = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            Warp cur = ws.get(warp);
            if (cur == null) {
                mine(pl);
                return;
            }
            String e = ws.edit(pl, cur, Category.of(response.getText("kategorie")), response.getText("farbe"),
                    response.getText("ort"), response.getText("text"));
            if (e != null) err(pl, e);
            else Sfx.play(core, pl, Sfx.Kind.SUCCESS);
            mine(pl);
        }, ONCE);
        ActionButton ok = ActionButton.create(label("✔", "Speichern", OK), null, 149, save);
        ActionButton move = button("⌖", "Hierher verschieben", BACK, pl -> {
            Warp cur = ws.get(warp);
            String e = cur == null ? "Diesen Warp gibt es nicht mehr." : ws.move(pl, cur);
            if (e != null) err(pl, e);
            else {
                Sfx.play(core, pl, Sfx.Kind.SUCCESS);
                Msg.send(pl, "<green>" + Msg.escape(I18n.ctx("Warp an deine Position verschoben.")));
            }
            mine(pl);
        });
        ActionButton delete = button("✖", "Löschen", RED, pl -> askDelete(pl, warp));
        long coins = core.economy().balance(p.getUniqueId());
        de.kuby.kubyplugins.ui.UiDialog.open(p, UiDialog.dialog(p, WarpScreen.formHead(coins, Canvas.lit(w.name), "Ort, Beschreibung, Kategorie und Farbe"), inputs,
                List.of(ok, move, delete, button("⇦", "Zurück", BACK, this::mine)), 2));
    }

    private void askDelete(Player p, String warp) {
        WarpService ws = addon.warps();
        Warp w = ws.get(warp);
        if (w == null) {
            mine(p);
            return;
        }
        ActionButton yes = button("✖", "Endgültig löschen", RED, pl -> {
            Warp cur = ws.get(warp);
            if (cur != null && (cur.owner.equals(pl.getUniqueId()) || pl.hasPermission("kuby.admin"))) {
                ws.delete(cur);
                Msg.send(pl, "<gray>" + Msg.escape(I18n.ctx("Warp gelöscht. Die Coins gibt es nicht zurück.")));
            }
            mine(pl);
        });
        long coins = core.economy().balance(p.getUniqueId());
        de.kuby.kubyplugins.ui.UiDialog.open(p, UiDialog.dialog(p, WarpScreen.formHead(coins, "Warp löschen?", "„" + w.name + "“ wirklich löschen? Die Coins gibt es nicht zurück."),
                List.of(), List.of(yes, button("⇦", "Zurück", BACK, pl -> askEdit(pl, warp))), 2));
    }
}
