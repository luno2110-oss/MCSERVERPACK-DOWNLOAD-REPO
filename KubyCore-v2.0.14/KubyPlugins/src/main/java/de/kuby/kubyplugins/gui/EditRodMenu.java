package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Auswahl, was die Blaze Rod beim Rechtsklick auf einen Block macht. */
public final class EditRodMenu extends GuiMenu {

    public EditRodMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>Blaze Rod"), 3);
    }

    @Override
    protected void build(Player p) {
        String current = plugin.edit().action(p.getUniqueId());
        for (int i = 0; i < 27; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        option(p, 11, Material.BEACON, "Spawn", "Rechtsklick setzt den /hub-Spawn", "spawn", current);
        option(p, 13, Material.STRUCTURE_BLOCK, "Region", "Linksklick Ecke 1, Rechtsklick Ecke 2", "region", current);
        option(p, 15, Material.NAME_TAG, "Marker", "Rechtsklick öffnet die Texteingabe", "marker", current);
    }

    private void option(Player p, int slot, Material m, String name, String desc, String value, String current) {
        boolean active = value.equals(current);
        ItemStack item = Items.make(m, (active ? "<green>" : "<white>") + name, List.of(
                "<gray>" + desc,
                "",
                active ? "<green>Aktiv" : "<yellow>Klick: auswählen"));
        if (active) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        set(slot, item, e -> {
            plugin.edit().setAction(p, value);
            p.closeInventory();
        });
    }
}
