package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Kit-Übersicht: Linksklick holt das Kit ab, Rechtsklick zeigt die Vorschau. */
public final class KitMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14};

    public KitMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("kits")), 3);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        List<KitService.Kit> kits = plugin.kits().kits();
        for (int i = 0; i < kits.size() && i < SLOTS.length; i++) {
            KitService.Kit kit = kits.get(i);
            long left = plugin.kits().remaining(p.getUniqueId(), kit);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + KitService.describe(kit) + " <dark_gray>· <gray>Cooldown " + kit.cooldownMinutes() + " min");
            if (!kit.rank().isEmpty()) {
                lore.add((plugin.kits().allowed(p, kit) ? "<green>" : "<red>") + "Ab Rang " + KitService.rankName(kit.rank()));
            }
            if (kit.items().isEmpty()) {
                lore.add("<dark_gray>Status: leer");
            } else if (left > 0) {
                lore.add("<red>Status: " + KitService.format(left));
            } else {
                lore.add("<green>Status: ✔ Verfügbar");
            }
            lore.add("");
            lore.add("<yellow>Linksklick: abholen");
            lore.add("<yellow>Rechtsklick: Vorschau");
            set(SLOTS[i], Items.make(kit.icon(), "<gold>" + kit.name(), lore), e -> {
                if (e.isRightClick()) {
                    new KitPreviewMenu(plugin, kit.id()).open(p);
                    return;
                }
                String err = plugin.kits().claim(p, kit);
                Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                Msg.send(p, err == null ? "<green>" + kit.name() + " abgeholt!" : "<red>" + err);
                open(p);
            });
        }
        addBack(p);
    }
}
