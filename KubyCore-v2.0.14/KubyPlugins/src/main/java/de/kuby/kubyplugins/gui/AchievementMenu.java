package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.AchievementService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /erfolge: alle Erfolge mit Fortschrittsbalken. Erreichte leuchten, offene zeigen, wie weit man ist. */
public final class AchievementMenu extends GuiMenu {

    private static final int PER_PAGE = 28;
    private static final int BAR = 10;

    private final int page;

    public AchievementMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_gray>Erfolge"), 6);
        this.page = page;
    }

    @Override
    protected void build(Player p) {
        AchievementService svc = plugin.achievements();
        svc.check(p);
        List<AchievementService.Achievement> all = svc.all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        set(4, Items.make(Material.NETHER_STAR, "<gold>Erfolge", List.of(
                "<gray>Erreicht: <white>" + svc.doneCount(p.getUniqueId()) + " <gray>von <white>" + all.size(),
                "<gray>Belohnungen gibt es automatisch.")), null);
        int slot = 10;
        for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
            AchievementService.Achievement a = all.get(i);
            boolean ok = svc.isDone(p.getUniqueId(), a.id());
            long have = Math.min(svc.progress(p.getUniqueId(), a), a.target());
            int filled = a.target() <= 0 ? BAR : (int) (have * BAR / a.target());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + a.desc());
            lore.add("");
            lore.add((ok ? "<green>" : "<yellow>") + "▮".repeat(filled) + "<dark_gray>" + "▮".repeat(BAR - filled)
                    + " <white>" + have + "<gray>/" + a.target());
            if (a.jewels() > 0) lore.add("<gray>Belohnung: <light_purple>" + a.jewels() + " Juwelen");
            lore.add(ok ? "<green>✔ Erreicht" : "<gray>Noch offen");
            ItemStack item = Items.make(ok ? a.icon() : Material.GRAY_DYE, (ok ? "<green>" : "<white>") + a.name(), lore);
            if (ok) {
                ItemMeta meta = item.getItemMeta();
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            set(slot, item, null);
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        if (page > 0) set(45, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()), e -> new AchievementMenu(plugin, page - 1).open(p));
        if (page < pages - 1) set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()), e -> new AchievementMenu(plugin, page + 1).open(p));
        addBack(p);
    }
}
