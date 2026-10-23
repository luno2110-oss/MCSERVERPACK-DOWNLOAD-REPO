package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Basis für alle Admin-Menüs: rotes Titel-Präfix und Zurück zum Admin-Panel. */
public abstract class AdminBase extends GuiMenu {

    protected final int rows;

    protected AdminBase(KubyPlugins plugin, String title, int rows) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("admin") + " <dark_gray>» <white>" + title), rows);
        this.rows = rows;
    }

    protected void adminBack(Player p) {
        set(rows * 9 - 5, Items.make(Material.ARROW, "<gray>Zurück", List.of()),
                e -> new AdminMenu(plugin).open(p));
    }

    /** Zeigt eine Meldung und öffnet das Menü neu. */
    protected void done(Player p, String message) {
        Msg.send(p, message);
        Sfx.play(plugin, p, message.startsWith("<red>") ? Sfx.Kind.ERROR : Sfx.Kind.SUCCESS);
        open(p);
    }
}
