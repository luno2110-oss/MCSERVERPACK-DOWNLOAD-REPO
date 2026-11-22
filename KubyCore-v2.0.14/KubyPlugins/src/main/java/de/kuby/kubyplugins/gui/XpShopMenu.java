package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * XP-Shop als Truhen-Menü (ohne Grafik-Pack, v2.05): erst die Kategorien des Modus, dann ihre Angebote. Gleiche
 * Angebote und gleicher Kauf wie im Fenster (XpShopScreen); Kaufen mit Shift-Klick, damit nichts aus Versehen
 * gekauft wird.
 */
public final class XpShopMenu extends GuiMenu {

    private final String category;

    public XpShopMenu(KubyPlugins plugin) {
        this(plugin, null);
    }

    public XpShopMenu(KubyPlugins plugin, String category) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("XP-Shop")), 6);
        this.category = category;
    }

    private static Material material(String name, Material def) {
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? def : m;
    }

    @Override
    protected void build(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        long xp = plugin.stats().get(p.getUniqueId()).xp;
        set(4, Items.make(Material.EXPERIENCE_BOTTLE, "<green>" + SmallCaps.of("Deine XP"), List.of(
                "<gray>Verfügbar: <white>" + Msg.coins(xp))), null);
        if (category == null) {
            int slot = 19;
            for (XpShopScreen.Category c : XpShopScreen.categories(plugin.getConfig(), mode)) {
                if (slot > 43) break;
                String id = c.id();
                set(slot, Items.make(material(c.icon(), Material.CHEST), "<green>" + SmallCaps.of(c.name()), List.of(
                        "<gray>" + c.line(), "<dark_gray>" + c.count() + " Angebote", "", "<yellow>Klick: öffnen")),
                        e -> new XpShopMenu(plugin, id).open(p));
                slot += (slot % 9 == 7) ? 3 : 1;
            }
            addBack(p);
            return;
        }
        int slot = 9;
        for (XpShopScreen.Offer o : XpShopScreen.offers(plugin.getConfig(), mode, category)) {
            if (slot > 44) break;
            String id = o.id();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + o.line());
            lore.add("<gray>Preis: " + (xp >= o.price() ? "<green>" : "<red>") + Msg.coins(o.price()) + " XP");
            lore.add("");
            lore.add(xp >= o.price() ? "<yellow>Shift-Klick: kaufen" : "<red>Dir fehlen " + Msg.coins(o.price() - xp) + " XP");
            set(slot++, Items.make(material(o.icon(), Material.CHEST), "<green>" + o.name(), lore), e -> {
                if (!e.isShiftClick()) {
                    Msg.send(p, "<gray>Zum Kaufen <white>Shift-Klick</white> auf das Angebot.");
                    return;
                }
                plugin.xpShopScreen().buy(p, id, false);
                new XpShopMenu(plugin, category).open(p);
            });
        }
        set(49, Items.make(Material.ARROW, "<gray>" + SmallCaps.of("Zurück"), List.of()), e -> new XpShopMenu(plugin).open(p));
    }
}
