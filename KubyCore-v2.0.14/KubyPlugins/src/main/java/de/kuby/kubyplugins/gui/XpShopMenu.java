package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** XP eintauschen gegen$, Juwelen oder Titel. Format pro Eintrag: ART;Wert;Kosten */
public final class XpShopMenu extends GuiMenu {

    public XpShopMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("XP-Shop")), 3);
    }

    @Override
    protected void build(Player p) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        set(4, Items.make(Material.EXPERIENCE_BOTTLE, "<green>" + SmallCaps.of("Deine XP"), List.of(
                "<gray>Verfügbar: <white>" + Msg.coins(s.xp))), null);

        int slot = 10;
        for (String raw : plugin.getConfig().getStringList("xp.shop")) {
            if (slot > 16) break;
            String[] parts = raw.split(";");
            if (parts.length < 3) continue;
            String kind = parts[0].trim().toUpperCase(Locale.ROOT);
            String value = parts[1].trim();
            long cost;
            try {
                cost = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            final String k = kind;
            final String v = value;
            final long price = cost;

            Material icon;
            String label;
            if (kind.equals("COINS")) {
                icon = Material.GOLD_NUGGET;
                label = value + "$";
            } else if (kind.equals("JEWELS")) {
                icon = Material.EMERALD;
                label = value + " Juwelen";
            } else {
                icon = Material.NAME_TAG;
                label = "Titel " + value;
            }
            set(slot++, Items.make(icon, "<green>" + SmallCaps.of(label), List.of(
                            "<gray>Kosten: <yellow>" + Msg.coins(price) + " XP",
                            "<gray>Klick: kaufen")),
                    e -> buy(p, k, v, price));
        }
        addBack(p);
    }

    private void buy(Player p, String kind, String value, long cost) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        if (s.xp < cost) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            done(p, "<red>Dir fehlen noch " + Msg.coins(cost - s.xp) + " XP.");
            return;
        }
        boolean ok = true;
        try {
            if (kind.equals("COINS")) plugin.economy().deposit(p.getUniqueId(), Long.parseLong(value));
            else if (kind.equals("JEWELS")) plugin.jewels().add(p.getUniqueId(), Long.parseLong(value));
            else if (kind.equals("TAG")) plugin.tags().grant(p.getUniqueId(), value);
            else ok = false;
        } catch (NumberFormatException ex) {
            ok = false;
        }
        if (!ok) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            done(p, "<red>Diese Ware ist in der config.yml falsch eingetragen.");
            return;
        }
        s.xp -= cost;
        plugin.stats().save();
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        done(p, "<green>Gekauft! <gray>-" + Msg.coins(cost) + " XP");
    }

    private void done(Player p, String message) {
        Msg.send(p, message);
        open(p);
    }
}
