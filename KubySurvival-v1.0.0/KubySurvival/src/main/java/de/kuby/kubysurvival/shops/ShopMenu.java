package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Kistenladen aus Kundensicht: kaufen und verkaufen. */
public final class ShopMenu extends GuiMenu {

    private final KubySurvival addon;
    private final ChestShopService.Shop shop;

    public ShopMenu(KubySurvival addon, ChestShopService.Shop shop) {
        super(addon.core(), Msg.parse("<dark_blue>Laden"), 3);
        this.addon = addon;
        this.shop = shop;
    }

    @Override
    protected void build(Player p) {
        ChestShopService cs = addon.chestShops();
        if (shop.item == null) return;
        String owner = Bukkit.getOfflinePlayer(shop.owner).getName();
        int stock = cs.stock(shop);
        ItemStack show = shop.item.clone();
        show.setAmount(Math.max(1, Math.min(shop.amount, show.getMaxStackSize())));
        ItemMeta meta = show.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());
        lore.add(Msg.item(""));
        lore.add(Msg.item("<gray>Laden von <white>" + (owner == null ? "?" : owner)));
        lore.add(Msg.item("<gray>Menge pro Handel: <white>" + shop.amount));
        lore.add(Msg.item("<gray>Auf Lager: <white>" + stock));
        meta.lore(lore);
        show.setItemMeta(meta);
        set(13, show, null);

        if (shop.buyPrice > 0) {
            boolean ok = stock >= shop.amount;
            set(11, Items.make(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "<green>Kaufen", List.of(
                    "<white>" + shop.amount + "x " + ChestShopService.name(shop.item),
                    "<gray>für <gold>" + Msg.coins(shop.buyPrice) + " Coins", "",
                    ok ? "<yellow>Klick zum Kaufen" : "<red>Ausverkauft")), e -> {
                String err = cs.buy(p, shop);
                if (err != null) Msg.send(p, "<red>" + err);
                else p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                open(p);
            });
        }
        if (shop.sellPrice > 0) {
            boolean ok = shop.cash >= shop.sellPrice;
            set(15, Items.make(ok ? Material.RED_CONCRETE : Material.GRAY_CONCRETE, "<red>Verkaufen", List.of(
                    "<white>" + shop.amount + "x " + ChestShopService.name(shop.item),
                    "<gray>für <gold>" + Msg.coins(shop.sellPrice) + " Coins", "",
                    ok ? "<yellow>Klick zum Verkaufen" : "<red>Ladenkasse leer")), e -> {
                String err = cs.sell(p, shop);
                if (err != null) Msg.send(p, "<red>" + err);
                else p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f);
                open(p);
            });
        }
    }
}
