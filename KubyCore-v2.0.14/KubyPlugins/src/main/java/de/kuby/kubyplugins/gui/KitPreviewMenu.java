package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Zeigt den Inhalt eines Kits, ohne es abzuholen. */
public final class KitPreviewMenu extends GuiMenu {

    private final String kitId;

    public KitPreviewMenu(KubyPlugins plugin, String kitId) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("vorschau")), 3);
        this.kitId = kitId;
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        KitService.Kit kit = null;
        for (KitService.Kit k : plugin.kits().kits()) {
            if (k.id().equals(kitId)) kit = k;
        }
        if (kit == null) {
            new KitMenu(plugin).open(p);
            return;
        }
        if (kit.items().isEmpty()) {
            set(13, Items.make(Material.BARRIER, "<gray>Dieses Kit ist noch leer", List.of()), null);
        }
        int slot = 9;
        for (ItemStack it : kit.items()) {
            if (slot > 17) break;
            set(slot++, it.clone(), null);
        }
        set(22, Items.make(Material.ARROW, "<gray>Zurück zu den Kits", List.of()),
                e -> new KitMenu(plugin).open(p));
    }
}
