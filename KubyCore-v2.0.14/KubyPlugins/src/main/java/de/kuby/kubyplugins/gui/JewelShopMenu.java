package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Zeigt Juwelen-Pakete. Gekauft wird im Store, hier nur die Übersicht und der Link. */
public final class JewelShopMenu extends GuiMenu {

    public JewelShopMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Juwelenshop")), 3);
    }

    @Override
    protected void build(Player p) {
        int slot = 10;
        for (String raw : plugin.getConfig().getStringList("jewels.packages")) {
            String[] parts = raw.split(";");
            if (parts.length < 3 || slot > 16) continue;
            set(slot++, Items.make(Material.EMERALD, "<green>" + SmallCaps.of(parts[0]), List.of(
                            "<gray>Inhalt: <aqua>" + parts[2] + " Juwelen",
                            "<gray>Preis: <gold>" + parts[1],
                            "<gray>Klick: Store-Link anzeigen")),
                    e -> {
                        String url = plugin.getConfig().getString("jewels.store-url", "");
                        Msg.send(p, url.isBlank()
                                ? "<red>Der Store-Link fehlt in der config.yml (jewels.store-url)."
                                : "<gray>Store: <white>" + url);
                    });
        }
        addBack(p);
    }
}
