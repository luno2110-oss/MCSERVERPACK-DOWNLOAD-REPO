package de.kuby.kubyplugins.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class Items {

    private Items() {}

    /** Anzahl unveränderter Items dieser Sorte im Hauptinventar (ohne Namen, Verzauberung, eigene Daten). Gegenstück zu
     *  {@link #takePlain}: beide zählen genau dieselben Stapel, so kann nichts gezählt, aber nicht entnommen werden. */
    public static int countPlain(org.bukkit.inventory.PlayerInventory inv, Material m) {
        ItemStack plain = new ItemStack(m);
        int sum = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && it.isSimilar(plain)) sum += it.getAmount();
        return sum;
    }

    /** Genau n unveränderte Items dieser Sorte aus dem Hauptinventar nehmen. Nur wenn genug da sind, sonst passiert
     *  nichts und es kommt false zurück. */
    public static boolean takePlain(org.bukkit.inventory.PlayerInventory inv, Material m, int n) {
        if (n <= 0) return true;
        if (countPlain(inv, m) < n) return false;
        ItemStack plain = new ItemStack(m);
        ItemStack[] c = inv.getStorageContents();
        int left = n;
        for (int i = 0; i < c.length && left > 0; i++) {
            ItemStack it = c[i];
            if (it == null || !it.isSimilar(plain)) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (take >= it.getAmount()) c[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        inv.setStorageContents(c);
        return left == 0;
    }

    /** Passt das Item komplett ins Hauptinventar? (vorher prüfen statt hinterher Reste zu erstatten) */
    public static boolean fits(org.bukkit.inventory.PlayerInventory inv, ItemStack item) {
        int need = item.getAmount(), max = item.getMaxStackSize();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType().isAir()) need -= max;
            else if (it.isSimilar(item)) need -= Math.max(0, max - it.getAmount());
            if (need <= 0) return true;
        }
        return need <= 0;
    }

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
