package de.kuby.kubyplugins.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class Items {

    private Items() {}

    public static ItemStack make(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(name));
        meta.lore(lore.stream().map(Msg::item).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(Msg.item(name));
        meta.lore(lore.stream().map(Msg::item).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler() {
        return make(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
    }
}
