package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

/** Ersatz für die Vorschau: merkt sich nur Material und Meta (ohne Server). */
public class ItemStack implements Cloneable {
    private Material type;
    private int amount = 1;
    private ItemMeta meta;

    public ItemStack(Material type) {
        this.type = type;
    }

    public ItemStack(Material type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public Material getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int a) {
        amount = a;
    }

    public ItemMeta getItemMeta() {
        if (meta == null) meta = h3d.Mock.meta();
        return meta;
    }

    public boolean hasItemMeta() {
        return meta != null;
    }

    public boolean setItemMeta(ItemMeta m) {
        meta = m;
        return true;
    }

    @Override
    public ItemStack clone() {
        ItemStack c = new ItemStack(type, amount);
        c.meta = meta;
        return c;
    }
}
