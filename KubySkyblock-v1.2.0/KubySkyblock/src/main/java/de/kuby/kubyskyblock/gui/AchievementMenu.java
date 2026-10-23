package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Erfolge mit Fortschritt. */
public final class AchievementMenu extends de.kuby.kubyplugins.gui.GuiMenu {

    public AchievementMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>Erfolge"), 4);
    }

    @Override
    protected void build(Player p) {
        var prog = KubySkyblock.get().progress();
        var done = prog.achieved(p.getUniqueId());
        int slot = 0;
        for (var a : prog.achievements()) {
            if (slot >= 27) break;
            long value = switch (a.type()) {
                case "items" -> prog.collected(p.getUniqueId()).size();
                case "oneblock" -> prog.counter(p.getUniqueId(), 1);
                case "loot" -> prog.counter(p.getUniqueId(), 2);
                default -> prog.counter(p.getUniqueId(), 0);
            };
            boolean ok = done.contains(a.id());
            set(slot++, Items.make(ok ? Material.NETHER_STAR : Material.COAL,
                    (ok ? "<green>" : "<white>") + a.name(), List.of(
                    "<gray>" + a.desc(),
                    "<gray>Fortschritt: <white>" + Math.min(value, a.target()) + "<dark_gray>/<white>" + a.target(),
                    ok ? "<green>Erreicht" : "<yellow>Noch offen",
                    "<gray>Belohnung: <gold>" + a.money() + "$ <green>" + a.xp() + " XP")), null);
        }
        set(31, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> new SkyblockMenu(plugin).open(p));
    }
}
