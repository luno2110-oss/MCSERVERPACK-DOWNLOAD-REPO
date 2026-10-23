package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Token-Shop: Items aus jobs.token-shop (MATERIAL;Anzahl;Preis) und Jobwechsel ohne Wartezeit. */
public final class TokenShopMenu extends GuiMenu {

    private final KubySurvival addon;

    public TokenShopMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<light_purple>Token-Shop"), 4);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        JobService jobs = addon.jobs();
        long tokens = jobs.worker(p.getUniqueId()).tokens;
        set(4, Items.make(Material.AMETHYST_SHARD, "<light_purple>" + tokens + " Tokens", List.of(
                "<gray>Tokens gibt es bei jedem", "<gray>Job- und Spieler-Aufstieg.")), null);

        int slot = 10;
        for (String line : plugin.getConfig().getStringList("jobs.token-shop")) {
            String[] parts = line.split(";");
            if (parts.length < 3 || slot > 25) continue;
            Material m = Material.matchMaterial(parts[0].trim());
            int amount;
            long price;
            try {
                amount = Integer.parseInt(parts[1].trim());
                price = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (m == null || !m.isItem() || amount <= 0 || price <= 0) continue;
            ItemStack shown = Items.make(m, "<white>" + amount + "x " + nice(m), List.of(
                    "<gray>Preis: <light_purple>" + price + " Tokens", "",
                    tokens >= price ? "<yellow>Klick zum Kaufen" : "<red>Zu wenig Tokens"));
            shown.setAmount(Math.min(amount, m.getMaxStackSize()));
            set(slot, shown, e -> {
                if (!jobs.takeTokens(p.getUniqueId(), price)) {
                    Msg.send(p, "<red>Dafür brauchst du " + price + " Tokens.");
                    return;
                }
                ItemStack give = new ItemStack(m, amount);
                p.getInventory().addItem(give).values()
                        .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                Msg.send(p, "<green>Gekauft: " + amount + "x " + nice(m));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                open(p);
            });
            slot += slot % 9 == 7 ? 3 : 1;
        }

        long skip = plugin.getConfig().getLong("jobs.skip-cooldown-tokens", 2);
        set(31, Items.make(Material.CLOCK, "<yellow>Jobwechsel ohne Wartezeit", List.of(
                "<gray>Preis: <light_purple>" + skip + " Tokens", "", "<yellow>Klick zum Kaufen")), e -> {
            String err = jobs.skipCooldown(p);
            Msg.send(p, err == null ? "<green>Du kannst deinen Job jetzt sofort wechseln." : "<red>" + err);
            open(p);
        });
        addBack(p);
    }

    private static String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
