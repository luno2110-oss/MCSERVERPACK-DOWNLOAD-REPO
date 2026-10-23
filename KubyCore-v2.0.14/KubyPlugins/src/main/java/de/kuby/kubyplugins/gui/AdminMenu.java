package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Admin-Panel, nach Themen sortiert:
 * Spieler und Server, Spielinhalte, Verwaltung und Einstellungen. Oben eine Kurzübersicht.
 */
public final class AdminMenu extends GuiMenu {

    public AdminMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("admin-panel")), 5);
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        for (int i = 0; i < 45; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }

        set(4, Items.make(Material.NETHER_STAR, "<gold>Admin-Panel", List.of(
                "<gray>Version: <white>" + plugin.getPluginMeta().getVersion(),
                "<gray>Online: <white>" + plugin.getServer().getOnlinePlayers().size(),
                "<dark_gray>Wähle einen Bereich")), null);

        button(p, 10, Material.PLAYER_HEAD, "Spieler", "Coins, Juwelen, Stats, Teleport", () -> new AdminPlayersMenu(plugin).open(p));
        button(p, 12, Material.COMMAND_BLOCK, "Server", "Speichern, Quiz, Ankündigungen", () -> new AdminServerMenu(plugin).open(p));
        button(p, 14, Material.WHITE_BANNER, "Teams", "Teams ansehen und auflösen", () -> new AdminTeamsMenu(plugin).open(p));
        button(p, 16, Material.CHEST, "Auktionshaus", "Angebote entfernen", () -> new AdminMarketMenu(plugin).open(p));

        button(p, 23, Material.GOLD_INGOT, "Preisliste", "Verkaufspreise der Items", () -> new WorthEditorMenu(plugin).open(p));
        button(p, 25, Material.IRON_CHESTPLATE, "Kits", "Items und Cooldowns der Kits", () -> new ConfigEditorMenu(plugin, "kits", 0).open(p));

        button(p, 28, Material.EMERALD, "Shop", "Sektionen, Wochenende, Daily Shop", () -> new ShopEditorMenu(plugin).open(p));
        button(p, 30, Material.ENCHANTED_BOOK, "Battlepass", "Belohnungen für Free und Premium", () -> new BattlepassEditorMenu(plugin, 0).open(p));
        button(p, 32, Material.WRITABLE_BOOK, "Quiz", "Quiz-Start und Fragen", () -> new AdminServerMenu(plugin).open(p));

        button(p, 37, Material.COMPARATOR, "Einstellungen", "Die wichtigsten Werte, übersichtlich", () -> new SettingsMenu(plugin, -1).open(p));
        button(p, 39, Material.COMMAND_BLOCK, "Config-Editor", "Jeder Wert der config.yml", () -> new ConfigEditorMenu(plugin, "", 0).open(p));
        button(p, 41, Material.REPEATER, "Config neu laden", "Übernimmt manuelle Änderungen", () -> {
            plugin.reloadConfig();
            Msg.setPrefix(plugin.getConfig().getString("prefix", ""));
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>Config neu geladen.");
            open(p);
        });
        for (KubyPlugins.AdminButton b : plugin.adminButtons()) {
            button(p, b.slot(), b.icon(), b.name(), b.desc(), () -> b.action().accept(p));
        }
    }

    private void button(Player p, int slot, Material icon, String name, String desc, Runnable action) {
        set(slot, Items.make(icon, "<gold>" + name, List.of(
                "<gray>" + desc,
                "",
                "<yellow>Klick: öffnen")), e -> action.run());
    }

}
