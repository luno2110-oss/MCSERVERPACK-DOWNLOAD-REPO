package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.HomeService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /homes als Dialog in warmer Ziegel-Optik: Kopf mit belegten Plätzen, Knöpfe „Neues Home hier“ und (außer Survival)
 * „Platz kaufen“, Homes als Karten in zwei Spalten (Klick teleportiert, „×“ löscht nach Nachfrage), freie Plätze als
 * leere Karten und unten, wie man mehr Homes bekommt. Ohne Pack: HomesMenu.
 */
public final class HomesScreen {

    static final int W = 390;
    static final TextColor BRICK = TextColor.color(0xF09A7A), CREAM = TextColor.color(0xF6E6DA),
            ROSE = TextColor.color(0xC09080), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            RED = TextColor.color(0xFF7A7A), DARK = TextColor.color(0x1E0C08), DIM = TextColor.color(0x7E5E54),
            XP = TextColor.color(0x7FE8FF);

    /** Home: Name, Welt, Position, Löschen angefragt, Klicks. */
    public record Home(String name, String world, int x, int y, int z, boolean armed, Consumer<Player> teleport,
                       Consumer<Player> delete) {}

    /** buy: Text auf dem Kauf-Knopf (null = keiner). info: Zeilen „so bekommst du mehr“. */
    public record View(List<Home> homes, int limit, Consumer<Player> add, String buy, Consumer<Player> buyClick,
                       List<String> info) {}

    private final KubyPlugins plugin;
    /** Home, dessen Löschen gerade nachgefragt wird. */
    private static final Map<UUID, String> ARMED = new HashMap<>();

    public HomesScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.uiPack().active(p)) {
            new HomesMenu(plugin).open(p);
            return;
        }
        UiDialog.show(p, canvas(view(p)), 330);
    }

    private View view(Player p) {
        UUID id = p.getUniqueId();
        HomeService hs = plugin.homes();
        String armed = ARMED.get(id);
        List<Home> homes = new ArrayList<>();
        for (Map.Entry<String, Location> e : hs.homes(id).entrySet()) {
            String name = e.getKey();
            Location loc = e.getValue();
            homes.add(new Home(name, loc.getWorld() == null ? "?" : world(loc.getWorld().getName()), loc.getBlockX(), loc.getBlockY(),
                    loc.getBlockZ(), name.equals(armed), pl -> {
                ARMED.remove(pl.getUniqueId());
                de.kuby.kubyplugins.ui.UiDialog.close(pl);
                Teleports.delayed(plugin, pl, loc, name);
            }, pl -> {
                if (!name.equals(ARMED.get(pl.getUniqueId()))) {
                    ARMED.put(pl.getUniqueId(), name);
                    Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                } else {
                    ARMED.remove(pl.getUniqueId());
                    hs.delete(pl.getUniqueId(), name);
                    Sfx.play(plugin, pl, Sfx.Kind.FAIL);
                    Msg.send(pl, "<gray>Home <white>" + Msg.escape(name) + " <gray>gelöscht.");
                }
                open(pl);
            }));
        }
        boolean survival = ModeService.SURVIVAL.equals(plugin.modes().modeOf(id));
        var cfg = plugin.getConfig();
        List<String> info = new ArrayList<>();
        String buy = null;
        if (survival) {
            List<String> ranks = cfg.getStringList("homes.survival.ranks");
            info.add("Jeder hat " + cfg.getInt("homes.survival.base", 1) + " Home, ab Rang "
                    + (ranks.isEmpty() ? "Platin" : cap(ranks.get(0))) + " +1 je Rang.");
            info.add("Discord verlinken (/verify): +" + cfg.getInt("homes.survival.discord-bonus", 1)
                    + " · Farmpass: +" + cfg.getInt("farmpass.extra-homes", 2));
            info.add("Nicht am Spawn, im Casino und in der Farmwelt.");
        } else {
            int bought = hs.extraBought(id);
            buy = bought >= HomeService.MAX_BOUGHT ? null : "Platz kaufen · " + Msg.coins(hs.nextCost(id)) + " XP";
            info.add("Grundplätze: " + cfg.getInt("homes.default-limit", 1) + " · Voten: +1 · /verify mit Discord: +3");
            info.add("Ränge: bis 30 Plätze · gekauft: " + bought + " / " + HomeService.MAX_BOUGHT);
        }
        return new View(homes, hs.limit(p), this::newHome, buy, pl -> {
            String err = hs.buyExtra(pl);
            Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
            Msg.send(pl, err == null ? "<green>Ein Homeplatz mehr!" : "<red>" + err);
            open(pl);
        }, info);
    }

    private static String world(String w) {
        String l = w.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("nether")) return "Nether";
        if (l.endsWith("_end") || l.contains("the_end")) return "End";
        if (l.contains("bau")) return "Bauwelt";
        if (l.contains("farm")) return "Farmwelt";
        return "Oberwelt";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void newHome(Player p) {
        Map<String, Location> list = plugin.homes().homes(p.getUniqueId());
        int n = list.size() + 1;
        while (list.containsKey("home" + n)) n++;
        String name = "home" + n;
        String blocked = plugin.homes().blockedReason(p.getLocation());
        if (blocked != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + blocked);
        } else if (plugin.homes().set(p, name, p.getLocation())) {
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>Home <white>" + name + " <green>gesetzt. <gray>Eigener Name: <white>/sethome <Name>");
        } else {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Homelimit erreicht.");
        }
        open(p);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Homes", BRICK);
        c.box(0, 0, W, 54, KitData.Box.BRICK);
        c.icon(10, 11, KitData.Icon.RED_BED, true);
        c.headline(50, 12, "Homes", BRICK);
        c.text(50, 36, "Klick auf ein Home bringt dich hin", ROSE, false);
        String used = v.homes().size() + " / " + v.limit();
        c.textRight(378, 12, used, CREAM, true);
        c.textRight(378, 24, Canvas.caps("Plätze belegt"), ROSE, false);
        // Plätze als kleine Punkte (höchstens 15)
        int dots = Math.min(15, v.limit());
        for (int i = 0; i < dots; i++) {
            int dx = 378 - (dots - i) * 7;
            c.line(dx, 38, 5, i < v.homes().size() ? BRICK : DIM);
            c.line(dx, 39, 5, i < v.homes().size() ? BRICK : DIM);
        }

        int y = 60;
        int n = v.buy() == null ? 1 : 2;
        int bw = (W - (n - 1) * 3) / n;
        c.button(0, y, bw, KitData.Button.GREEN);
        int gw = 16 + 4 + Canvas.width("Neues Home hier", true);
        c.icon((bw - gw) / 2, y + 3, KitData.Icon.LIME_DYE, false);
        c.textFlat((bw - gw) / 2 + 20, y + 7, "Neues Home hier", DARK, true);
        c.action(0, y, bw, 22, v.add(), "Speichert deine Position als neues Home");
        if (v.buy() != null) {
            c.button(bw + 3, y, bw, KitData.Button.TEAL);
            String label = Canvas.fit(v.buy(), true, bw - 30);
            gw = 16 + 4 + Canvas.width(label, true);
            c.icon(bw + 3 + (bw - gw) / 2, y + 3, KitData.Icon.EXPERIENCE, false);
            c.textFlat(bw + 3 + (bw - gw) / 2 + 20, y + 7, label, DARK, true);
            c.action(bw + 3, y, bw, 22, v.buyClick(), v.buy());
        }
        y += 22 + 8;

        int cw = (W - 4) / 2, ch = 36;
        List<Home> hs = v.homes();
        int free = Math.max(0, v.limit() - hs.size());
        int shownFree = Math.min(free, hs.size() % 2 == 1 ? 3 : 2);
        int cells = hs.size() + shownFree;
        for (int i = 0; i < cells; i++) {
            int x = (i % 2) * (cw + 4), hy = y + (i / 2) * (ch + 4);
            if (i >= hs.size()) {
                c.box(x, hy, cw, ch, KitData.Box.GRAY);
                c.text(x + 10, hy + 9, "Freier Platz", DIM, true);
                c.text(x + 10, hy + 21, "„Neues Home hier“ oder /sethome", DIM, false);
                continue;
            }
            Home h = hs.get(i);
            c.box(x, hy, cw, ch, h.armed() ? KitData.Box.RED : KitData.Box.BRICK_CARD);
            c.icon(x + 7, hy + 10, KitData.Icon.RED_BED, false);
            c.text(x + 29, hy + 8, Canvas.fit(Canvas.lit(h.name()), true, cw - 29 - 30), CREAM, true);
            c.text(x + 29, hy + 20, Canvas.fit(h.world() + " · " + h.x() + ", " + h.y() + ", " + h.z(), false, cw - 29 - 8), ROSE, false);
            c.action(x, hy, cw - 26, ch, h.teleport(), "Zu " + h.name() + " teleportieren");
            String del = h.armed() ? "?" : "×";
            c.smallButton(x + cw - 22, hy + 4, 16, h.armed() ? KitData.Small.GOLD_ON : KitData.Small.DARK, del,
                    h.armed() ? DARK : RED, true);
            c.action(x + cw - 22, hy + 4, 16, KitData.SMALL_H, h.delete(), h.armed() ? "Nochmal klicken: wirklich löschen" : "Löschen");
        }
        y += ((cells + 1) / 2) * (ch + 4);
        if (free > shownFree) {
            c.text(4, y, "+" + (free - shownFree) + " weitere freie Plätze", DIM, false);
            y += 12;
        }
        if (hs.isEmpty() && free == 0) {
            c.text(4, y, "Du hast gerade keinen Platz für ein Home.", RED, false);
            y += 12;
        }
        // so bekommst du mehr
        y += 4;
        int ih = 24 + 10 * v.info().size();
        c.box(0, y, W, ih, KitData.Box.BRICK_CARD);
        c.text(9, y + 7, Canvas.caps("Mehr Homes"), BRICK, true);
        int ty = y + 19;
        for (String line : v.info()) {
            c.text(9, ty, Canvas.fit(line, false, W - 18), ROSE, false);
            ty += 10;
        }
        return c;
    }
}
