package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Auswahl der Spielmodi, geöffnet über den Kompass in der Lobby. */
public final class ModeSelectMenu extends GuiMenu {

    public ModeSelectMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("modus wählen")), 3);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        String current = plugin.modes().modeOf(p.getUniqueId());
        option(p, 11, Material.IRON_SWORD, "SMP", "PvP, Kopfgelder, Turniere", ModeService.SMP, current);
        option(p, 13, Material.GRASS_BLOCK, "Survival", "Ruhig, Farmen, Angeln", ModeService.SURVIVAL, current);
        option(p, 15, Material.OAK_SAPLING, "Skyblock", "Insel, Loot, One-Block", ModeService.SKYBLOCK, current);
    }

    private void option(Player p, int slot, Material icon, String name, String desc, String mode, String current) {
        boolean active = mode.equals(current);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + desc);
        lore.add("");
        lore.add(active ? "<green>Aktuell" : "<yellow>Klick: betreten");
        ItemStack item = Items.make(icon, (active ? "<green>" : "<white>") + name, lore);
        if (active) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        set(slot, item, e -> {
            p.closeInventory();
            if (active) return;
            String err = plugin.modes().switchTo(p, mode);
            if (err != null) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>" + err);
            }
        });
    }
}
