package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyAllTimer;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminServerMenu extends AdminBase {

    public AdminServerMenu(KubyPlugins plugin) {
        super(plugin, "Server", 3);
    }

    @Override
    protected void build(Player p) {
        set(10, Items.make(Material.WRITABLE_BOOK, "<green>Alles speichern", List.of(
                "<gray>Schreibt alle Daten sofort auf die Festplatte")),
                e -> { plugin.saveAll(); done(p, "<green>Gespeichert."); });

        set(11, Items.make(Material.REPEATER, "<yellow>Config neu laden", List.of(
                "<gray>Übernimmt Änderungen aus der config.yml")),
                e -> {
                    plugin.reloadConfig();
                    Msg.setPrefix(plugin.getConfig().getString("prefix", ""));
                    done(p, "<green>Config neu geladen.");
                });

        set(12, Items.make(Material.BOOK, "<aqua>Quiz jetzt starten", List.of(
                "<gray>Stellt sofort eine Frage")),
                e -> { plugin.quiz().ask(); done(p, "<gray>Quiz gestartet."); });

        set(13, Items.make(Material.TRIAL_KEY, "<gold>Universal-Schlüssel jetzt", List.of(
                "<gray>Verteilt jetzt einen keyall_key an alle Online-Spieler")),
                e -> { new KeyAllTimer(plugin).giveAll(); done(p, "<gray>Schlüssel verteilt."); });

        set(14, Items.make(Material.OAK_SIGN, "<white>Ankündigung senden", List.of(
                "<gray>Nachricht im Chat eingeben")),
                e -> plugin.prompts().ask(p, "Ankündigung eingeben:", text ->
                        {
                            plugin.getServer().broadcast(Msg.parse("<gold>[Admin] <white>" + Msg.escape(text)));
                            open(p);
                        }));

        set(16, Items.make(Material.COMPARATOR, "<gray>Keyall-Befehl", List.of(
                "<gray>" + plugin.getConfig().getString("keyall.command", "(nicht gesetzt)"))), null);

        adminBack(p);
    }
}
