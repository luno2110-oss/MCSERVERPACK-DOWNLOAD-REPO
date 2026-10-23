package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Wochen-Meta: abstimmen, welches Item nächste Woche +25 % Verkaufspreis bekommt. */
public final class MetaMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public MetaMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Wochen-Meta")), 4);
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        Map<Material, Integer> tally = plugin.weekly().tally();
        Material mine = plugin.weekly().myVote(id);
        Material active = plugin.weekly().activeMeta();

        set(4, Items.make(Material.BOOK, "<gold>" + SmallCaps.of("Aktuelles Meta"), List.of(
                active == null ? "<gray>Kein Meta aktiv" : "<gray>Aktiv: <white>" + pretty(active) + " <gray>(+25 % Verkaufspreis)",
                "<gray>Neue Wahl: jeden Montag um 00:05 Uhr",
                "<gray>Stimme abgeben: Klick auf ein Item")), null);

        List<Material> cands = plugin.weekly().candidates();
        for (int i = 0; i < cands.size() && i < SLOTS.length; i++) {
            Material m = cands.get(i);
            boolean voted = m == mine;
            int votes = tally.getOrDefault(m, 0);
            ItemStack item = Items.make(m, "<gold>" + SmallCaps.of(pretty(m)), List.of(
                    "<gray>Stimmen: <white>" + votes,
                    voted ? "<green>✔ Deine Stimme" : "<gray>Klick: abstimmen"));
            if (m == active) glint(item);
            set(SLOTS[i], item, e -> {
                String err = plugin.weekly().vote(id, m);
                Sfx.play(plugin, p, err == null ? Sfx.Kind.PING : Sfx.Kind.ERROR);
                done(p, err == null ? "<green>Stimme für " + pretty(m) + " gespeichert." : "<red>" + err);
            });
        }
        addBack(p);
    }

    private void done(Player p, String message) {
        Msg.send(p, message);
        open(p);
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
