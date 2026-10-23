package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /sw: alle Spieler-Warps (beliebteste zuerst) oder mit manage nur die eigenen zum Verwalten. */
public final class WarpMenu extends GuiMenu {

    private static final int PER_PAGE = 36;

    private final KubySurvival addon;
    private final boolean manage;
    private final int page;

    public WarpMenu(KubySurvival addon, boolean manage, int page) {
        super(addon.core(), Msg.parse(manage ? "<dark_purple>Deine Warps" : "<dark_purple>Spieler-Warps"), 6);
        this.addon = addon;
        this.manage = manage;
        this.page = page;
    }

    @Override
    protected void build(Player p) {
        WarpService ws = addon.warps();
        UUID id = p.getUniqueId();
        List<WarpService.Warp> list = manage ? ws.of(id) : ws.all();
        int from = page * PER_PAGE;
        for (int i = from; i < list.size() && i < from + PER_PAGE; i++) {
            WarpService.Warp w = list.get(i);
            String owner = Bukkit.getOfflinePlayer(w.owner).getName();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Von <white>" + (owner == null ? "?" : owner));
            lore.add("<gray>Ort: <white>" + w.location.getBlockX() + ", " + w.location.getBlockY() + ", " + w.location.getBlockZ());
            lore.add("<gray>Besuche: <white>" + Msg.coins(w.visits));
            lore.add("");
            lore.add("<yellow>Linksklick: hinreisen");
            if (manage) {
                lore.add("<yellow>Shift + Linksklick: hierher verschieben");
                lore.add("<yellow>Rechtsklick: Item in der Hand als Symbol");
                lore.add("<red>Shift + Rechtsklick: löschen (ohne Erstattung)");
            }
            ItemStack icon = Items.make(w.icon, "<light_purple>" + w.name, lore);
            set(i - from, icon, e -> {
                ClickType c = e.getClick();
                if (manage && c == ClickType.SHIFT_LEFT) {
                    String err = ws.move(p, w);
                    Msg.send(p, err == null ? "<green>Warp " + w.name + " steht jetzt hier." : "<red>" + err);
                    open(p);
                } else if (manage && c == ClickType.RIGHT) {
                    Material m = p.getInventory().getItemInMainHand().getType();
                    if (m.isAir() || !m.isItem()) {
                        Msg.send(p, "<red>Nimm das gewünschte Symbol in die Hand.");
                    } else {
                        ws.setIcon(w, m);
                    }
                    open(p);
                } else if (manage && c == ClickType.SHIFT_RIGHT) {
                    ws.delete(w);
                    Msg.send(p, "<gray>Warp " + w.name + " gelöscht.");
                    open(p);
                } else {
                    p.closeInventory();
                    ws.travel(p, w);
                }
            });
        }
        if (list.isEmpty()) {
            set(22, Items.make(Material.BARRIER, manage ? "<gray>Du hast noch keinen Warp" : "<gray>Noch keine Warps", List.of(
                    "<gray>Kaufen: <white>/sw create <Name>")), null);
        }
        if (page > 0) set(45, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()), e -> new WarpMenu(addon, manage, page - 1).open(p));
        if (from + PER_PAGE < list.size()) set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()), e -> new WarpMenu(addon, manage, page + 1).open(p));

        long price = ws.nextPrice(id);
        set(47, Items.make(Material.NETHER_STAR, "<gold>Eigenen Warp kaufen", List.of(
                "<gray>Du hast <white>" + ws.of(id).size() + "/" + ws.prices().size() + " <gray>Warps.",
                price < 0 ? "<red>Mehr gehen nicht." : "<gray>Nächster: <gold>" + Msg.coins(price) + " Coins",
                "", "<white>/sw create <Name> <gray>dort, wo du stehst")), null);
        set(51, Items.make(manage ? Material.COMPASS : Material.WRITABLE_BOOK, manage ? "<white>Alle Warps" : "<white>Deine Warps verwalten",
                List.of("", "<yellow>Klick")), e -> new WarpMenu(addon, !manage, 0).open(p));
        addBack(p);
    }
}
