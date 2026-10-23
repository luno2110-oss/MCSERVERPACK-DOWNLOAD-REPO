package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Shop-Startseite: drei Reihen, oben und unten dunkelgraues Glas. */
public final class ShopMenu extends GuiMenu {

    public ShopMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Shop")), 3);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }

        set(10, Items.make(Material.BRICKS, "<gold>" + SmallCaps.of("Blöcke"), List.of("<gray>Bausteine und Baumaterial")),
                e -> section(p, "blocks"));
        set(11, Items.make(Material.COOKED_BEEF, "<green>" + SmallCaps.of("Essen"), List.of("<gray>Nahrung für unterwegs")),
                e -> section(p, "food"));
        set(12, Items.make(Material.REDSTONE, "<red>" + SmallCaps.of("Redstone"), List.of("<gray>Schaltungen und Mechanik")),
                e -> section(p, "redstone"));
        set(13, Items.make(Material.FLOWER_POT, "<light_purple>" + SmallCaps.of("Dekoration"), List.of("<gray>Für dein Zuhause")),
                e -> section(p, "deco"));
        set(14, Items.make(Material.NETHER_STAR, "<gold>" + SmallCaps.of("Wochenende"), List.of(
                "<gray>Jedes Wochenende neue Angebote",
                "<gray>Jeder Spieler sieht eigene Items")),
                e -> section(p, ShopService.WEEKEND));
        set(15, Items.make(Material.CLOCK, "<aqua>" + SmallCaps.of("Daily Shop"), List.of(
                "<gray>Jeden Tag neue Angebote",
                "<gray>Für alle gleich, bezahlt mit <aqua>Juwelen")),
                e -> section(p, ShopService.DAILY));
        set(17, Items.make(Material.EMERALD, "<green>" + SmallCaps.of("Verkaufen"), List.of(
                "<gray>Items in das Verkaufs-Menü legen")),
                e -> SellMenu.open(plugin, p));

        set(20, Items.make(Material.COMPASS, "<aqua>" + SmallCaps.of("Suche"), List.of(
                "<gray>Nach Name, ID, Material oder Nummer")),
                e -> plugin.prompts().ask(p, "Name, ID, Material oder Nummer eingeben:", text ->
                        new ShopSectionMenu(plugin, "Suche", "search", plugin.shop().search(text), 0).open(p)));
        set(24, Items.make(Material.CHEST, "<gold>" + SmallCaps.of("Abholbox"), List.of("<gray>Gekaufte Items abholen")),
                e -> new ShopStorageMenu(plugin, 0).open(p));
        if (p.hasPermission("kuby.admin")) {
            set(22, Items.make(Material.COMMAND_BLOCK, "<red>" + SmallCaps.of("Shop bearbeiten"), List.of("<gray>Nur Admins")),
                    e -> new ShopEditorMenu(plugin).open(p));
        }
    }

    private void section(Player p, String sec) {
        List<ShopService.Item> list;
        if (ShopService.WEEKEND.equals(sec)) {
            list = plugin.shop().weekendPicks(p.getUniqueId());
        } else if (ShopService.DAILY.equals(sec)) {
            list = plugin.shop().dailyItems(p.getUniqueId());
        } else {
            list = plugin.shop().items(sec);
        }
        new ShopSectionMenu(plugin, ShopService.TITLES.get(sec), sec, list, 0).open(p);
    }
}
