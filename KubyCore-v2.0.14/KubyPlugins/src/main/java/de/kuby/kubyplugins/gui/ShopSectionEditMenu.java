package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Items einer Sektion ansehen, bearbeiten und neue aus der Hand hinzufügen. */
public final class ShopSectionEditMenu extends GuiMenu {

    private static final int PER = 45;
    private final String sec;
    private final int page;

    public ShopSectionEditMenu(KubyPlugins plugin, String sec, int page) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("editor") + " <dark_gray>» <white>"
                + ShopService.TITLES.get(sec)), 6);
        this.sec = sec;
        this.page = page;
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        List<ShopService.Item> all = plugin.shop().items(sec);
        int start = page * PER;
        for (int i = 0; i < PER; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            ShopService.Item it = all.get(idx);
            String name = it.name() != null ? it.name() : it.material().name().toLowerCase(java.util.Locale.ROOT);
            set(i, Items.make(it.material(), "<white>" + name + " <dark_gray>x" + it.amount(), List.of(
                    "<gray>Preis: <gold>" + Msg.coins(it.price()) + "$",
                    "<gray>ID: " + it.id(),
                    "<yellow>Klick: bearbeiten")),
                    e -> new ShopItemEditMenu(plugin, it.id()).open(p));
        }

        for (int i = 45; i < 54; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        if (page > 0) {
            set(45, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                    e -> new ShopSectionEditMenu(plugin, sec, page - 1).open(p));
        }
        set(47, Items.make(Material.ARROW, "<gray>Zurück zum Editor", List.of()),
                e -> new ShopEditorMenu(plugin).open(p));
        set(49, Items.make(Material.EMERALD, "<green>Item aus der Hand hinzufügen", List.of(
                "<gray>Startpreis: 100$, danach anpassen")), e -> addFromHand(p));
        if (start + PER < all.size()) {
            set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                    e -> new ShopSectionEditMenu(plugin, sec, page + 1).open(p));
        }
    }

    private void addFromHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Halte das Item in deiner Haupthand.");
        } else {
            int amount = Math.min(hand.getAmount(), hand.getMaxStackSize());
            plugin.shop().add(sec, new ShopService.Item(ShopService.newId(), hand.getType(), amount, 100, null, List.of()));
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>Item hinzugefügt. Passe Preis und Menge an.");
        }
        open(p);
    }
}
