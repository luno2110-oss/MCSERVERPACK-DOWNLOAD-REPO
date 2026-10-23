package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopStorageService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Abholbox des Shops: drei Seiten. Klick auf ein Item nimmt es ins Inventar. */
public final class ShopStorageMenu extends GuiMenu {

    private static final int PAGE = ShopStorageService.PAGE;
    private final int page;

    public ShopStorageMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_gray>Abholbox <gray>" + (page + 1) + "/" + ShopStorageService.PAGES), 4);
        this.page = page;
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        int unlocked = plugin.shopStorage().unlockedPages(id);
        boolean open = page < unlocked;

        for (int i = 0; i < PAGE; i++) {
            if (!open) {
                set(i, Items.make(Material.IRON_BARS, "<dark_gray>Gesperrt", List.of(
                        "<gray>Schalte diese Seite mit XP frei")), null);
                continue;
            }
            int slot = page * PAGE + i;
            ItemStack it = plugin.shopStorage().get(id, slot);
            if (it == null) continue;
            set(i, it.clone(), e -> take(p, slot));
        }

        for (int i = 27; i < 36; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        if (page > 0) {
            set(27, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                    e -> new ShopStorageMenu(plugin, page - 1).open(p));
        }
        if (open) {
            set(31, Items.make(Material.HOPPER, "<green>Alles abholen", List.of("<gray>Packt alles von dieser Seite ein")),
                    e -> takeAll(p));
        }
        if (page + 1 < ShopStorageService.PAGES) {
            if (page + 1 < unlocked) {
                set(35, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                        e -> new ShopStorageMenu(plugin, page + 1).open(p));
            } else if (page + 1 == unlocked) {
                int cost = plugin.shopStorage().cost(page + 2);
                set(35, Items.make(Material.EXPERIENCE_BOTTLE, "<aqua>Seite " + (page + 2) + " freischalten", List.of(
                        "<gray>Kosten: <white>" + cost + " XP")),
                        e -> {
                            String err = plugin.shopStorage().unlock(p);
                            Sfx.play(plugin, p, err == null ? Sfx.Kind.LEVEL_UP : Sfx.Kind.ERROR);
                            Msg.send(p, err == null ? "<green>Seite freigeschaltet!" : "<red>" + err);
                            open(p);
                        });
            }
        }
    }

    private void take(Player p, int slot) {
        UUID id = p.getUniqueId();
        ItemStack it = plugin.shopStorage().get(id, slot);
        if (it == null) return;
        Map<Integer, ItemStack> rest = p.getInventory().addItem(it.clone());
        plugin.shopStorage().set(id, slot, rest.isEmpty() ? null : rest.values().iterator().next());
        Sfx.play(plugin, p, Sfx.Kind.TRADE);
        open(p);
    }

    private void takeAll(Player p) {
        UUID id = p.getUniqueId();
        int taken = 0;
        for (int i = 0; i < PAGE; i++) {
            int slot = page * PAGE + i;
            ItemStack it = plugin.shopStorage().get(id, slot);
            if (it == null) continue;
            Map<Integer, ItemStack> rest = p.getInventory().addItem(it.clone());
            plugin.shopStorage().set(id, slot, rest.isEmpty() ? null : rest.values().iterator().next());
            taken++;
        }
        Sfx.play(plugin, p, taken > 0 ? Sfx.Kind.TRADE : Sfx.Kind.CLICK);
        Msg.send(p, taken > 0 ? "<green>" + taken + " Stapel abgeholt." : "<gray>Nichts abzuholen.");
        open(p);
    }
}
