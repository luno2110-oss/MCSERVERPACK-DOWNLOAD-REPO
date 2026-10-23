package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Bearbeitet eine Textliste der config.yml: Eintrag hinzufügen, Shift-Klick entfernt. */
public final class ConfigListMenu extends GuiMenu {

    private static final int PER_PAGE = 45;
    private final String path;
    private final int page;

    public ConfigListMenu(KubyPlugins plugin, String path, int page) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("liste") + " <gray>" + path), 6);
        this.path = path;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        for (int i = 0; i < 54; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        List<String> all = plugin.getConfig().getStringList(path);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            final int idx = start + i;
            String text = all.get(idx);
            set(i, Items.make(Material.PAPER, "<white>" + Msg.escape(text), List.of(
                    "<gray>Eintrag " + (idx + 1),
                    "<red>Shift + Klick: entfernen")), e -> {
                if (!e.isShiftClick()) return;
                List<String> copy = new ArrayList<>(plugin.getConfig().getStringList(path));
                if (idx < copy.size()) copy.remove(idx);
                plugin.getConfig().set(path, copy);
                plugin.saveConfig();
                Sfx.play(plugin, p, Sfx.Kind.FAIL);
                open(p);
            });
        }
        set(45, Items.make(Material.ARROW, "<gray>Zurück", List.of()),
                e -> new ConfigEditorMenu(plugin, ConfigEditorMenu.parentOf(path), 0).open(p));
        set(49, Items.make(Material.EMERALD, "<green>Eintrag hinzufügen", List.of(
                "<gray>Text eingeben, wird als neuer Eintrag angehängt")),
                e -> plugin.prompts().ask(p, "Neuer Eintrag:", text -> {
                    List<String> copy = new ArrayList<>(plugin.getConfig().getStringList(path));
                    copy.add(text);
                    plugin.getConfig().set(path, copy);
                    plugin.saveConfig();
                    Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                    open(p);
                }));
    }
}
