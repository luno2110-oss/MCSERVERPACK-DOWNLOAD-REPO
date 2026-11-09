package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.SkyShopService;
import de.kuby.kubyskyblock.service.SkyShopService.Entry;
import de.kuby.kubyskyblock.service.SkyShopService.Section;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Skyblock-Shop im Himmels-Look: Bereiche als Reiter, je Item Kaufen (Menge aus der config) und Verkaufen (alle aus dem
 * Inventar), unten „Alles verkaufen“. Ohne Pack: Liste im Chat mit Klick-Befehlen.
 */
public final class SkyShopScreen {

    static final int W = 390;
    static final TextColor SKY = IslandScreen.SKY, CLOUD = IslandScreen.CLOUD, MIST = IslandScreen.MIST, GOLD = IslandScreen.GOLD,
            GREEN = IslandScreen.GREEN, RED = IslandScreen.RED, DIM = IslandScreen.DIM;

    private SkyShopScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static Consumer<Player> run(Consumer<Player> c) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            c.accept(p);
        };
    }

    public record Card(org.bukkit.Material material, String name, String buyLine, String sellLine, boolean canBuy, boolean canSell,
                       Consumer<Player> buy, Consumer<Player> sell) {}

    public record View(String section, long coins, List<String> tabs, int tab, List<Card> cards, int page, int pages,
                       Consumer<Player>[] tabActions, Consumer<Player> prev, Consumer<Player> next, Consumer<Player> sellAll, Consumer<Player> back) {}

    public static void open(Player p) {
        open(p, 0, 0);
    }

    @SuppressWarnings("unchecked")
    public static void open(Player p, int tab, int page) {
        SkyShopService s = sb().shop();
        List<Section> secs = s.sections();
        if (secs.isEmpty()) {
            Msg.send(p, "<red>Der Skyblock-Shop ist leer (config: shop.bereiche).");
            return;
        }
        if (!sb().core().uiPack().active(p)) {
            chat(p, Math.max(0, Math.min(tab, secs.size() - 1)));
            return;
        }
        int t = Math.max(0, Math.min(tab, secs.size() - 1));
        Section sec = secs.get(t);
        long coins = sb().core().economy().balance(p.getUniqueId());
        int per = 10, pages = Math.max(1, (sec.entries().size() + per - 1) / per), pg = Math.max(0, Math.min(page, pages - 1));
        List<Card> cards = new ArrayList<>();
        for (Entry e : sec.entries().subList(pg * per, Math.min(sec.entries().size(), pg * per + per))) {
            int have = s.count(p, e.material());
            cards.add(new Card(e.material(), e.name(),
                    e.buy() > 0 ? "x" + e.amount() + " · " + Msg.coins(e.buy()) + " $" : "nur Verkauf",
                    e.sell() > 0 ? Msg.coins(e.sell()) + " $/St. · du hast " + have : "kein Verkauf",
                    e.buy() > 0 && coins >= e.buy(), e.sell() > 0 && have > 0,
                    e.buy() > 0 ? run(pl -> {
                        String err = s.buy(pl, e, 1);
                        Sfx.play(sb().core(), pl, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                        if (err != null) Msg.send(pl, "<red>" + err);
                        open(pl, t, pg);
                    }) : null,
                    e.sell() > 0 ? run(pl -> {
                        long got = s.sell(pl, e.material());
                        Sfx.play(sb().core(), pl, got > 0 ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                        Msg.send(pl, got > 0 ? "<green>Verkauft für <gold>" + Msg.coins(got) + " $" : "<gray>Davon hast du nichts dabei.");
                        open(pl, t, pg);
                    }) : null));
        }
        List<String> tabs = new ArrayList<>();
        Consumer<Player>[] acts = new Consumer[secs.size()];
        for (int k = 0; k < secs.size(); k++) {
            tabs.add(secs.get(k).name());
            int kk = k;
            acts[k] = run(pl -> open(pl, kk, 0));
        }
        UiDialog.show(p, canvas(new View(sec.name(), coins, tabs, t, cards, pg, pages, acts,
                pg > 0 ? run(pl -> open(pl, t, pg - 1)) : null, pg < pages - 1 ? run(pl -> open(pl, t, pg + 1)) : null,
                run(pl -> {
                    long got = s.sellAll(pl);
                    Sfx.play(sb().core(), pl, got > 0 ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                    Msg.send(pl, got > 0 ? "<green>Alles verkauft für <gold>" + Msg.coins(got) + " $" : "<gray>Nichts Verkaufbares im Inventar.");
                    open(pl, t, pg);
                }), run(IslandScreen::open))), 330);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Skyblock-Shop", SKY);
        c.box(0, 0, W, 46, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_SHOP.width) / 2, 7, KitData.Sprite.HB_SHOP);
        c.headline(50, 6, "Shop", SKY);
        c.text(50, 31, Canvas.fit(v.section(), true, 200), CLOUD, true);
        c.textRight(W - 10, 10, Canvas.caps("Coins"), MIST, false);
        c.textRight(W - 10, 22, Msg.coins(v.coins()) + " $", GOLD, true);
        int y = 52, n = v.tabs().size(), tw = (W - 4 * (n - 1)) / Math.max(1, n);
        for (int k = 0; k < n; k++) {
            boolean on = k == v.tab();
            c.smallButton(k * (tw + 4), y, tw, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, Canvas.fit(v.tabs().get(k), true, tw - 6), on ? CLOUD : MIST, true);
            c.action(k * (tw + 4), y, tw, KitData.SMALL_H, v.tabActions()[k], v.tabs().get(k));
        }
        y += KitData.SMALL_H + 6;
        int cw = (W - 4) / 2, ch = 56;
        for (int i = 0; i < v.cards().size(); i++) {
            Card cd = v.cards().get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.CLOUD_CARD);
            c.item(x + 5, cy + 12, cd.material(), true, KitData.Icon.PAPER);
            c.text(x + 40, cy + 5, Canvas.fit(cd.name(), true, cw - 46), CLOUD, true);
            c.text(x + 40, cy + 16, Canvas.fit(cd.buyLine(), false, cw - 46), GOLD, false);
            c.text(x + 40, cy + 26, Canvas.fit(cd.sellLine(), false, cw - 46), GREEN, false);
            int bw = (cw - 46) / 2 - 2;
            if (cd.buy() != null) {
                c.smallButton(x + 40, cy + ch - KitData.SMALL_H - 2, bw, cd.canBuy() ? KitData.Small.GOLD : KitData.Small.DARK, "Kaufen", cd.canBuy() ? GOLD : DIM, true);
                c.action(x + 40, cy + ch - KitData.SMALL_H - 2, bw, KitData.SMALL_H, cd.buy(), "Kaufen: " + cd.buyLine());
            }
            if (cd.sell() != null) {
                c.smallButton(x + 44 + bw, cy + ch - KitData.SMALL_H - 2, bw, cd.canSell() ? KitData.Small.GREEN : KitData.Small.DARK, "Verkaufen",
                        cd.canSell() ? CLOUD : DIM, true);
                c.action(x + 44 + bw, cy + ch - KitData.SMALL_H - 2, bw, KitData.SMALL_H, cd.sell(), "Alle verkaufen: " + cd.sellLine());
            }
        }
        y += ((v.cards().size() + 1) / 2) * (ch + 4);
        if (v.cards().isEmpty()) {
            c.box(0, y, W, 24, KitData.Box.CLOUD_CARD);
            c.text(10, y + 8, "Dieser Bereich ist leer.", DIM, false);
            y += 28;
        }
        c.smallButton(0, y, 120, KitData.Small.DARK, "« Zur Insel", CLOUD, false);
        c.action(0, y, 120, KitData.SMALL_H, v.back(), "Zurück zur Insel");
        c.smallButton(124, y, 110, KitData.Small.GREEN, "Alles verkaufen", CLOUD, true);
        c.action(124, y, 110, KitData.SMALL_H, v.sellAll(), "Alles Verkaufbare aus dem Inventar verkaufen");
        if (v.pages() > 1) {
            c.smallButton(W - 150, y, 40, KitData.Small.DARK, "«", v.prev() == null ? DIM : CLOUD, true);
            if (v.prev() != null) c.action(W - 150, y, 40, KitData.SMALL_H, v.prev(), "Vorige Seite");
            c.textCenter(W - 80, y + 4, (v.page() + 1) + "/" + v.pages(), MIST, false);
            c.smallButton(W - 40, y, 40, KitData.Small.DARK, "»", v.next() == null ? DIM : CLOUD, true);
            if (v.next() != null) c.action(W - 40, y, 40, KitData.SMALL_H, v.next(), "Nächste Seite");
        }
        return c;
    }

    /** Ohne Pack: Bereich als Liste mit Klick-Befehlen. */
    static void chat(Player p, int tab) {
        SkyShopService s = sb().shop();
        Section sec = s.sections().get(tab);
        StringBuilder tabs = new StringBuilder();
        for (int k = 0; k < s.sections().size(); k++) {
            tabs.append(k == tab ? " <gold>[" : " <click:run_command:'/insel shop " + (k + 1) + "'><gray>[").append(Msg.escape(s.sections().get(k).name()))
                    .append(k == tab ? "]" : "]</click>");
        }
        Msg.send(p, "<#9AD8FF><b>Skyblock-Shop</b>" + tabs);
        for (Entry e : sec.entries()) {
            Msg.send(p, " <white>" + Msg.escape(e.name()) + (e.buy() > 0 ? " <gold>x" + e.amount() + " " + Msg.coins(e.buy()) + " $" : "")
                    + (e.sell() > 0 ? " <green>Verkauf " + Msg.coins(e.sell()) + " $/St." : ""));
        }
        Msg.send(p, " <gray>Verkaufen: <white>/sell alles");
    }

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Card> cards = new ArrayList<>();
        for (int k = 0; k < 10; k++) {
            cards.add(new Card(org.bukkit.Material.GRASS_BLOCK, w, "x64 · 999.999.999 $", "999.999 $/St. · du hast 9.999", k % 2 == 0, k % 3 == 0,
                    k % 4 == 3 ? null : no, k % 5 == 4 ? null : no));
        }
        List<String> tabs = List.of("Start & Natur", "Farmen", "Blöcke", "Erze", "Mob-Drops", "Werkzeug");
        @SuppressWarnings("unchecked")
        Consumer<Player>[] acts = new Consumer[]{no, no, no, no, no, no};
        reg.accept("Skyblock-Shop", () -> canvas(new View(w, 999_999_999L, tabs, 2, cards, 1, 3, acts, no, no, no, no)));
        reg.accept("Skyblock-Shop leer", () -> canvas(new View("Start & Natur", 0, tabs.subList(0, 1), 0, List.of(), 0, 1, acts, null, null, no, no)));
    }
}
