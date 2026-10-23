package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class AbholboxMenu extends GuiMenu {

    public AbholboxMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Abholbox")), 6);
    }

    @Override
    protected void build(Player p) {
        List<ItemStack> items = plugin.abholbox().items(p.getUniqueId());
        int slot = 0;
        for (ItemStack it : items) {
            if (slot >= 45) break;
            set(slot++, it.clone(), null);
        }
        if (items.isEmpty()) {
            set(22, Items.make(Material.BARRIER, "<gray>" + SmallCaps.of("Leer"), List.of(
                    "<gray>Käufe und Order-Lieferungen landen hier")), null);
        }
        set(51, Items.make(Material.CHEST, "<green>" + SmallCaps.of("Alles abholen"), List.of(
                        "<gray>Packt alle Items in dein Inventar")),
                e -> {
                    claim(plugin, p);
                    open(p);
                });
        addBack(p);
    }

    /** Packt alle Items aus der Abholbox ins Inventar. Was nicht passt, bleibt in der Abholbox. */
    public static int claim(KubyPlugins plugin, Player p) {
        List<ItemStack> items = plugin.abholbox().takeAll(p.getUniqueId());
        int left = 0;
        for (ItemStack it : items) {
            Map<Integer, ItemStack> rest = p.getInventory().addItem(it);
            for (ItemStack r : rest.values()) {
                plugin.abholbox().add(p.getUniqueId(), r);
                left++;
            }
        }
        int moved = items.size() - left;
        Msg.send(p, moved > 0 ? "<green>" + moved + " Stapel abgeholt." : "<gray>Nichts abzuholen.");
        if (moved > 0) Sfx.play(plugin, p, Sfx.Kind.TRADE);
        if (left > 0) Msg.send(p, "<red>" + left + " Stapel passen nicht ins Inventar und bleiben in der Abholbox.");
        return moved;
    }
}
