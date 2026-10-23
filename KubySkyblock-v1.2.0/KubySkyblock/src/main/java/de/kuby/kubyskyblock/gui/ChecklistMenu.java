package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Checkliste aller Items, 45 pro Seite. Gesammelte Items zeigen ihr Bild, offene ein Schloss. */
public final class ChecklistMenu extends de.kuby.kubyplugins.gui.GuiMenu {

    private static final int PER_PAGE = 45;
    private final int page;

    public ChecklistMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_gray>Checkliste"), 6);
        this.page = page;
    }

    @Override
    protected void build(Player p) {
        var prog = KubySkyblock.get().progress();
        var all = prog.allItems();
        var got = prog.collected(p.getUniqueId());
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Material m = all.get(start + i);
            boolean has = got.contains(m);
            set(i, has
                    ? Items.make(m, "<green>" + m.name(), List.of("<gray>Gesammelt"))
                    : Items.make(Material.BARRIER, "<gray>???", List.of("<dark_gray>Noch nicht gesammelt")), null);
        }
        for (int i = 45; i < 54; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        set(45, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                e -> { if (page > 0) new ChecklistMenu(plugin, page - 1).open(p); });
        set(48, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new SkyblockMenu(plugin).open(p));
        set(49, Items.make(Material.BOOK, "<gold>Seite " + (page + 1) + "/" + pages, List.of(
                "<gray>Gesammelt: <white>" + got.size() + "<dark_gray>/<white>" + all.size(),
                "<gray>Fortschritt: <white>" + prog.percent(p.getUniqueId()) + "%")), null);
        set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                e -> { if (page + 1 < pages) new ChecklistMenu(plugin, page + 1).open(p); });
    }
}
