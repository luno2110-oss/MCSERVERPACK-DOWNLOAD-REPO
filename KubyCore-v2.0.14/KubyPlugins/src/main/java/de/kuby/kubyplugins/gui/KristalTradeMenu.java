package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Tauscht Ancient Debris gegen End-Kristalle: 4 Debris ergeben 1 Kristall. */
public final class KristalTradeMenu extends GuiMenu {

    private static final int PRICE = 4;

    public KristalTradeMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Kristall-Tausch")), 3);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        int debris = count(p, Material.ANCIENT_DEBRIS);
        int possible = debris / PRICE;
        set(13, Items.make(Material.END_CRYSTAL, "<light_purple>End-Crystal tauschen", List.of(
                "<gray>Preis: <white>" + PRICE + "x Ancient Debris",
                "<gray>Du hast: <white>" + debris + " Debris <dark_gray>(= <white>" + possible + " Crystals<dark_gray>)",
                "",
                "<yellow>Linksklick: <white>1x tauschen",
                "<yellow>Rechtsklick: <white>so viele wie möglich")),
                e -> trade(p, e.isRightClick() ? -1 : 1));
        set(22, Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()),
                e -> new MainMenu(plugin).open(p));
    }

    /** amount -1 = so viele wie möglich. */
    private void trade(Player p, int amount) {
        int debris = count(p, Material.ANCIENT_DEBRIS);
        int crystals = amount < 0 ? debris / PRICE : Math.min(amount, debris / PRICE);
        if (crystals <= 0) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Du brauchst mindestens " + PRICE + " Ancient Debris.");
            open(p);
            return;
        }
        p.getInventory().removeItem(new ItemStack(Material.ANCIENT_DEBRIS, crystals * PRICE));
        Map<Integer, ItemStack> rest = p.getInventory().addItem(new ItemStack(Material.END_CRYSTAL, crystals));
        rest.values().forEach(it -> plugin.abholbox().add(p.getUniqueId(), it));
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>" + crystals + "x End-Crystal erhalten" + (rest.isEmpty() ? "" : " <gray>(Rest in der Abholbox)"));
        open(p);
    }

    private static int count(Player p, Material m) {
        int sum = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) sum += it.getAmount();
        }
        return sum;
    }
}
