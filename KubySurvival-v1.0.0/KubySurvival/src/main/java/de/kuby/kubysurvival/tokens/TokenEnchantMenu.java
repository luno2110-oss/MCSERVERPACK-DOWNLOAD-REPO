package de.kuby.kubysurvival.tokens;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.TokenShopMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Besondere Verzauberungen: hebt eine Verzauberung des Items in der Hand eine Stufe über das normale Maximum.
 * Voraussetzung: Das Item hat die Verzauberung schon auf der höchsten normalen Stufe.
 */
public final class TokenEnchantMenu extends GuiMenu {

    private record Upgrade(Enchantment ench, String name, Material icon, long price) {}

    private static final List<Upgrade> UPGRADES = List.of(
            new Upgrade(Enchantment.EFFICIENCY, "Effizienz", Material.DIAMOND_PICKAXE, 10),
            new Upgrade(Enchantment.UNBREAKING, "Haltbarkeit", Material.ANVIL, 10),
            new Upgrade(Enchantment.FORTUNE, "Glück", Material.DIAMOND, 15),
            new Upgrade(Enchantment.LOOTING, "Plünderung", Material.GOLDEN_SWORD, 15),
            new Upgrade(Enchantment.SHARPNESS, "Schärfe", Material.DIAMOND_SWORD, 12),
            new Upgrade(Enchantment.PROTECTION, "Schutz", Material.DIAMOND_CHESTPLATE, 12),
            new Upgrade(Enchantment.POWER, "Stärke", Material.BOW, 12),
            new Upgrade(Enchantment.LURE, "Köder", Material.FISHING_ROD, 8),
            new Upgrade(Enchantment.LUCK_OF_THE_SEA, "Glück des Meeres", Material.PRISMARINE_CRYSTALS, 8));

    private final KubySurvival addon;

    public TokenEnchantMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<light_purple>Besondere Verzauberungen"), 4);
        this.addon = addon;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    @Override
    protected void build(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        set(4, Items.make(Material.BOOK, "<white>So geht's", List.of(
                "<gray>Nimm das Werkzeug, die Waffe oder", "<gray>Rüstung in die Hand. Die Verzauberung",
                "<gray>muss schon auf der höchsten Stufe sein.", "<gray>Dann eine Stufe darüber kaufen.")), null);
        for (int i = 0; i < UPGRADES.size(); i++) {
            Upgrade u = UPGRADES.get(i);
            int max = u.ench().getMaxLevel();
            int have = hand.getType().isAir() ? 0 : hand.getEnchantmentLevel(u.ench());
            boolean ready = have == max;
            boolean done = have > max;
            String state = done ? "<green>Hat dein Item schon" : ready ? "<yellow>Klick: für " + u.price() + " Tokens"
                    : "<red>Item braucht erst " + u.name() + " " + roman(max);
            set(i < 7 ? 19 + i : 21 + i, Items.make(u.icon(), "<light_purple>" + u.name() + " " + roman(max + 1), List.of(
                    "<gray>Preis: <light_purple>" + u.price() + " Tokens", "", state)), e -> {
                ItemStack now = p.getInventory().getItemInMainHand();
                if (now.getType().isAir() || now.getEnchantmentLevel(u.ench()) != max) {
                    Msg.send(p, "<red>Dein Item in der Hand braucht " + u.name() + " " + roman(max) + ".");
                    return;
                }
                if (!addon.jobs().takeTokens(p.getUniqueId(), u.price())) {
                    Msg.send(p, "<red>Dafür brauchst du " + u.price() + " Tokens.");
                    return;
                }
                ItemMeta meta = now.getItemMeta();
                meta.addEnchant(u.ench(), max + 1, true);
                now.setItemMeta(meta);
                p.getInventory().setItemInMainHand(now);
                Msg.send(p, "<green>Verzaubert: <light_purple>" + u.name() + " " + roman(max + 1));
                p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                open(p);
            });
        }
        set(31, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new TokenShopMenu(addon, addon.core()).open(p));
    }
}
