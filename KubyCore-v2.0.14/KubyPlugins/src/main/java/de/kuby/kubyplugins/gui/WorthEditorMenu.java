package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Verkaufspreise bearbeiten: Klick ändert den Preis, 0 entfernt das Item. */
public final class WorthEditorMenu extends GuiMenu {

    public WorthEditorMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Preisliste")), 6);
    }

    @Override
    protected void build(Player p) {
        int slot = 0;
        for (Map.Entry<Material, Long> entry : plugin.sell().worth().entrySet()) {
            if (slot >= 45) break;
            Material mat = entry.getKey();
            String name = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            set(slot++, Items.make(mat, "<gold>" + name, List.of(
                            "<gray>Preis: <gold>" + Msg.coins(entry.getValue()) + "$",
                            "<yellow>Klick: Preis ändern (0 = entfernen)")),
                    e -> plugin.prompts().ask(p, "Neuer Preis für " + name + " (0 = entfernen):", text -> {
                        try {
                            long value = Long.parseLong(text);
                            plugin.getConfig().set("worth." + mat.name(), value <= 0 ? null : value);
                            plugin.saveConfig();
                            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                        } catch (NumberFormatException ex) {
                            Sfx.play(plugin, p, Sfx.Kind.ERROR);
                            Msg.send(p, "<red>Keine gültige Zahl.");
                        }
                        new WorthEditorMenu(plugin).open(p);
                    }));
        }

        set(49, Items.make(Material.EMERALD, "<green>Neues Item hinzufügen", List.of(
                        "<yellow>Klick: Material und Preis eingeben",
                        "<gray>Format: DIAMOND 200")),
                e -> plugin.prompts().ask(p, "Material und Preis (z. B. DIAMOND 200):", text -> {
                    String[] parts = text.trim().split("[ ;]+");
                    Material mat = parts.length == 2 ? Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT)) : null;
                    if (mat == null || !mat.isItem()) {
                        Sfx.play(plugin, p, Sfx.Kind.ERROR);
                        Msg.send(p, "<red>Unbekanntes Item oder falsches Format.");
                    } else {
                        try {
                            long value = Long.parseLong(parts[1]);
                            plugin.getConfig().set("worth." + mat.name(), value);
                            plugin.saveConfig();
                            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                        } catch (NumberFormatException ex) {
                            Sfx.play(plugin, p, Sfx.Kind.ERROR);
                            Msg.send(p, "<red>Der Preis muss eine Zahl sein.");
                        }
                    }
                    new WorthEditorMenu(plugin).open(p);
                }));

        set(45, Items.make(Material.ARROW, "<gray>Zurück zu den Einstellungen", List.of()),
                e -> new SettingsMenu(plugin, -1).open(p));
    }
}
