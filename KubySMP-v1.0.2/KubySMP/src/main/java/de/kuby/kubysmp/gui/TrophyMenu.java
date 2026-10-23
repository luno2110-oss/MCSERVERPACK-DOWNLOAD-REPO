package de.kuby.kubysmp.gui;

import de.kuby.kubyplugins.gui.GuiMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.League;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Trophäen-Übersicht: aktuelle Liga, Fortschritt und Liga-Belohnungen zum Abholen. */
public final class TrophyMenu extends GuiMenu {

    private static final int[] LEAGUE_SLOTS = {19, 20, 21, 22, 23, 24};

    public TrophyMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Trophäen")), 5);
    }

    @Override
    protected void build(Player p) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        League current = League.of(s.trophies);
        League next = current.next();

        List<String> info = new ArrayList<>();
        info.add("<gray>Trophäen: <gold>" + s.trophies);
        info.add("<gray>Liga: " + current.display());
        info.add(next == null
                ? "<gray>Maximale Liga erreicht"
                : "<gray>Nächste Liga: " + next.display() + " <gray>ab <white>" + next.min());
        info.add("<gray>Fortschritt: " + progressBar(s.trophies, current, next));
        info.add("<gray>Kills: <white>" + s.kills + " <dark_gray>| <gray>Tode: <white>" + s.deaths);
        set(4, Items.make(Material.NETHER_STAR, "<gold>" + SmallCaps.of("Deine Trophäen"), info), null);

        League[] leagues = League.values();
        for (int i = 0; i < leagues.length; i++) {
            League l = leagues[i];
            boolean reached = s.trophies >= l.min();
            boolean claimed = s.claimed.contains(l.name());

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Ab: <white>" + l.min() + " Trophäen");
            lore.add("<gray>Belohnung: <gold>" + Msg.coins(l.coins()) + "$"
                    + (l.jewels() > 0 ? " <dark_gray>+ <aqua>" + l.jewels() + " Juwelen" : ""));
            if (l == current) lore.add("<green>✔ Deine Liga");
            if (!reached) lore.add("<red>✘ Noch nicht erreicht");
            else if (claimed) lore.add("<gray>Belohnung bereits erhalten");
            else lore.add("<yellow>Klick: Belohnung abholen!");

            ItemStack item = Items.make(l.icon(), l.display(), lore);
            if (reached && !claimed) glint(item);
            set(LEAGUE_SLOTS[i], item, e -> claim(p, l));
        }

        addBack(p);
    }

    private void claim(Player p, League l) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        if (s.trophies < l.min()) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            done(p, "<red>Diese Liga hast du noch nicht erreicht.");
            return;
        }
        if (s.claimed.contains(l.name())) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            done(p, "<gray>Diese Belohnung hast du schon erhalten.");
            return;
        }
        s.claimed.add(l.name());
        plugin.economy().deposit(p.getUniqueId(), l.coins());
        if (l.jewels() > 0) plugin.jewels().add(p.getUniqueId(), l.jewels());
        plugin.stats().save();
        Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
        done(p, "<green>Liga-Belohnung " + l.display() + " <green>erhalten!");
    }

    private void done(Player p, String message) {
        Msg.send(p, message);
        open(p);
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private static String progressBar(int trophies, League current, League next) {
        if (next == null) return "<gold>▰▰▰▰▰▰▰▰▰▰ <gray>MAX";
        double ratio = (double) (trophies - current.min()) / (next.min() - current.min());
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        return "<green>" + "▰".repeat(filled) + "<dark_gray>" + "▱".repeat(10 - filled)
                + " <gray>" + trophies + "/" + next.min();
    }
}
