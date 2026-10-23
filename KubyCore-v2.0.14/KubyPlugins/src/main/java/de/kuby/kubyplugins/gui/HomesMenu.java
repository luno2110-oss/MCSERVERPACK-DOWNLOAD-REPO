package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Homes: bis zu 30 Plätze, unten neues Home, Platz kaufen und Zurück. Shift-Klick löscht. */
public final class HomesMenu extends GuiMenu {

    public HomesMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("meine homes")), 5);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 45; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        Map<String, Location> list = plugin.homes().homes(p.getUniqueId());
        int slot = 0;
        for (Map.Entry<String, Location> entry : list.entrySet()) {
            if (slot >= 36) break;
            String name = entry.getKey();
            Location loc = entry.getValue();
            set(slot++, Items.make(Material.RED_BED, "<red>" + SmallCaps.of(name), List.of(
                    "<gray>Welt: <white>" + loc.getWorld().getName(),
                    "<gray>Position: <white>" + loc.getBlockX() + " / " + loc.getBlockY() + " / " + loc.getBlockZ(),
                    "",
                    "<yellow>Klick: teleportieren",
                    "<red>Shift + Klick: löschen")), e -> {
                if (e.isShiftClick()) {
                    plugin.homes().delete(p.getUniqueId(), name);
                    Sfx.play(plugin, p, Sfx.Kind.FAIL);
                    Msg.send(p, "<gray>Home <white>" + name + " <gray>gelöscht.");
                    open(p);
                } else {
                    p.closeInventory();
                    Teleports.delayed(plugin, p, loc, name);
                }
            });
        }
        if (list.isEmpty()) {
            set(13, Items.make(Material.BARRIER, "<gray>Noch keine Homes", List.of(
                    "<gray>Setze dein erstes Home mit dem Button unten")), null);
        }

        int used = list.size();
        int limit = plugin.homes().limit(p);
        set(36, Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()),
                e -> new MainMenu(plugin).open(p));
        set(40, Items.make(Material.LIME_DYE, "<green>Neues Home", List.of(
                "<gray>Speichert deine Position",
                "<gray>Belegt: <white>" + used + " <dark_gray>/ <white>" + limit)), e -> newHome(p));
        int bought = plugin.homes().extraBought(p.getUniqueId());
        set(42, Items.make(Material.EXPERIENCE_BOTTLE, "<aqua>Platz kaufen", List.of(
                "<gray>Erweiterungen: <white>" + bought + " <dark_gray>/ <white>" + de.kuby.kubyplugins.service.HomeService.MAX_BOUGHT,
                bought >= de.kuby.kubyplugins.service.HomeService.MAX_BOUGHT
                        ? "<green>Alle Erweiterungen gekauft"
                        : "<gray>Kosten: <aqua>" + Msg.coins(plugin.homes().nextCost(p.getUniqueId())) + " XP",
                "<yellow>Klick: kaufen")), e -> {
            String err = plugin.homes().buyExtra(p);
            Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
            Msg.send(p, err == null ? "<green>Ein Homeplatz mehr!" : "<red>" + err);
            open(p);
        });
        set(44, Items.make(Material.BOOK, "<gold>Homes", List.of(
                "<gray>Grundplätze: <white>" + plugin.getConfig().getInt("homes.default-limit", 2),
                "<gray>Voten: <white>+1 Platz",
                "<gray>/verify mit Discord: <white>+3 Plätze",
                "<gray>Ränge: <white>bis 30 Plätze")), null);
    }

    private void newHome(Player p) {
        Map<String, Location> list = plugin.homes().homes(p.getUniqueId());
        int n = list.size() + 1;
        while (list.containsKey("home" + n)) n++;
        String name = "home" + n;
        if (plugin.homes().set(p, name, p.getLocation())) {
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>Home <white>" + name + " <green>gesetzt.");
        } else {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Homelimit erreicht. Kaufe Plätze oder hole /verify.");
        }
        open(p);
    }
}
