package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bestenlisten als Ruhmeshalle in Königs-Violett: Podest für die ersten drei (Platz 1 in der Mitte, höher und golden),
 * darunter Plätze 4 bis 10 als Zeilen, ganz unten der eigene Wert. Für /baltop (Wirtschaft) und /ranking (Trophäen).
 * Ohne Pack: LeaderboardMenu bzw. RankingMenu.
 */
public final class TopListScreen {

    static final int W = 390;
    static final TextColor LILAC = TextColor.color(0xD7B0FF), CREAM = TextColor.color(0xF2E8FF), MAUVE = TextColor.color(0x9C88B8),
            GOLD = TextColor.color(0xF8D35A), SILVER = TextColor.color(0xD8DDE6), BRONZE = TextColor.color(0xE0A060),
            WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x6C5C88);

    /** Platz: Nummer, Name, Wert, Zusatz (null = keiner). */
    public record Entry(int place, String name, String value, String extra) {}

    public record View(String title, String sub, KitData.Icon icon, TextColor valueColor, List<Entry> entries, String me) {}

    private final KubyPlugins plugin;

    public TopListScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static String name(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "Unbekannt" : op.getName();
    }

    /** /baltop: die reichsten Spieler. */
    public void money(Player p) {
        if (!plugin.uiPack().active(p)) {
            new LeaderboardMenu(plugin).open(p);
            return;
        }
        List<Entry> list = new ArrayList<>();
        int i = 1;
        for (Map.Entry<UUID, Long> e : plugin.economy().top(10)) list.add(new Entry(i++, name(e.getKey()), Msg.coins(e.getValue()) + "$", null));
        UiDialog.show(p, canvas(new View("Vermögen", "Die zehn reichsten Spieler", KitData.Icon.GOLD_INGOT, GOLD, list,
                "Dein Vermögen: " + Msg.coins(plugin.economy().balance(p.getUniqueId())) + "$")), 330);
    }

    /** /ranking: Trophäen mit Liga, Kills und Toden. */
    public void trophies(Player p) {
        if (!plugin.uiPack().active(p)) {
            new RankingMenu(plugin).open(p);
            return;
        }
        List<Entry> list = new ArrayList<>();
        int i = 1;
        for (Map.Entry<UUID, StatsService.Stats> e : plugin.stats().topTrophies(10)) {
            StatsService.Stats s = e.getValue();
            list.add(new Entry(i++, name(e.getKey()), Msg.coins(s.trophies), StatsService.league(s.trophies) + " · " + s.kills + "/" + s.deaths));
        }
        StatsService.Stats me = plugin.stats().get(p.getUniqueId());
        UiDialog.show(p, canvas(new View("Trophäen", "Liga · Kills/Tode", KitData.Icon.STAR, GOLD, list,
                "Du: " + Msg.coins(me.trophies) + " Trophäen · " + StatsService.league(me.trophies))), 330);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading(v.title(), LILAC);
        c.box(0, 0, W, 54, KitData.Box.ROYAL);
        c.icon(10, 11, v.icon(), true);
        c.headline(50, 12, v.title(), LILAC);
        c.text(50, 36, v.sub(), MAUVE, false);
        c.icon(352, 11, KitData.Icon.TOTEM, true);

        // Podest: 2 | 1 | 3
        int y = 60;
        int pw = (W - 6) / 3;
        int[][] pod = {{1, 0, 16}, {0, pw + 3, 0}, {2, 2 * (pw + 3), 24}};
        TextColor[] medal = {GOLD, SILVER, BRONZE};
        KitData.Box[] boxes = {KitData.Box.GOLD, KitData.Box.GRAY, KitData.Box.WOOD_CARD};
        int tall = 84;
        for (int[] pd : pod) {
            int rank = pd[0], x = pd[1], top = y + pd[2], h = tall - pd[2];
            Entry e = rank < v.entries().size() ? v.entries().get(rank) : null;
            c.box(x, top, pw, h, boxes[rank]);
            String num = "#" + (rank + 1);
            c.title(x + (pw - Canvas.titleWidth(num)) / 2, top + 8, num, rank == 0, rank == 0 ? null : medal[rank]);
            String n = e == null ? "—" : Canvas.fit(Canvas.lit(e.name()), true, pw - 12);
            c.textCenter(x + pw / 2, top + 31, n, CREAM, true);
            if (e != null) {
                c.textCenter(x + pw / 2, top + 43, Canvas.fit(e.value(), true, pw - 12), v.valueColor(), true);
                if (e.extra() != null && h > 64) c.textCenter(x + pw / 2, top + 55, Canvas.fit(e.extra(), false, pw - 12), MAUVE, false);
            }
        }
        y += tall + 6;

        // Plätze 4 bis 10
        for (int i = 3; i < 10; i++) {
            Entry e = i < v.entries().size() ? v.entries().get(i) : null;
            c.box(0, y, W, 22, i % 2 == 0 ? KitData.Box.ROYAL : KitData.Box.ROYAL_ROW);
            c.textRight(28, y + 7, "#" + (i + 1), MAUVE, true);
            if (e != null) {
                int vw = c.textRight(380, y + 7, e.value(), v.valueColor(), true);
                int ew = 0;
                if (e.extra() != null) ew = c.textRight(380 - vw - 10, y + 7, Canvas.fit(e.extra(), false, 120), MAUVE, false) + 10;
                c.text(38, y + 7, Canvas.fit(Canvas.lit(e.name()), false, 380 - vw - 10 - ew - 38), CREAM, false);
            } else {
                c.text(38, y + 7, "—", DIM, false);
            }
            y += 22 + 2;
        }
        y += 4;
        if (v.me() != null) {
            c.box(0, y, W, 24, KitData.Box.GOLD);
            c.textCenter(W / 2, y + 8, Canvas.fit(v.me(), true, W - 20), GOLD, true);
        }
        return c;
    }
}
