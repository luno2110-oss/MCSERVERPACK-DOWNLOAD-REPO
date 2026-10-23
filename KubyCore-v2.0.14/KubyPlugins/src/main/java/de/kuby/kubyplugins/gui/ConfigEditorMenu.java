package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Editor für jeden Wert der config.yml. Bereiche öffnen sich per Klick, Zahlen werden verstellt,
 * Ja/Nein umgeschaltet, Texte im Chat eingegeben und Listen in einem eigenen Menü bearbeitet.
 */
public final class ConfigEditorMenu extends GuiMenu {

    private static final int PER_PAGE = 45;
    private final String path;
    private final int page;

    public ConfigEditorMenu(KubyPlugins plugin, String path, int page) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("config") + " <gray>" + (path.isEmpty() ? "/" : path)), 6);
        this.path = path;
        this.page = Math.max(0, page);
    }

    public static String parentOf(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? "" : path.substring(0, i);
    }

    private ConfigurationSection section() {
        return path.isEmpty() ? plugin.getConfig() : plugin.getConfig().getConfigurationSection(path);
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
        set(45, Items.make(Material.ARROW, "<gray>Zurück", List.of()), e -> back(p));

        ConfigurationSection cs = section();
        if (cs == null) {
            set(22, Items.make(Material.BARRIER, "<gray>Dieser Bereich existiert nicht mehr", List.of()), null);
            return;
        }
        List<String> keys = new ArrayList<>(cs.getKeys(false));
        Collections.sort(keys);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < keys.size(); i++) {
            entry(p, cs, keys.get(start + i), i);
        }
        if (keys.isEmpty()) {
            set(22, Items.make(Material.BARRIER, "<gray>Keine Einträge", List.of()), null);
        }
        if (page > 0) {
            set(46, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                    e -> new ConfigEditorMenu(plugin, path, page - 1).open(p));
        }
        int pages = Math.max(1, (keys.size() + PER_PAGE - 1) / PER_PAGE);
        set(49, Items.make(Material.PAPER, "<white>" + (path.isEmpty() ? "Hauptbereich" : path), List.of(
                "<gray>Seite " + (page + 1) + " / " + pages,
                "<dark_gray>Änderungen wirken sofort,",
                "<dark_gray>einige Werte brauchen einen Neustart")), null);
        if (start + PER_PAGE < keys.size()) {
            set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                    e -> new ConfigEditorMenu(plugin, path, page + 1).open(p));
        }
    }

    private void back(Player p) {
        if (path.isEmpty()) new AdminMenu(plugin).open(p);
        else new ConfigEditorMenu(plugin, parentOf(path), 0).open(p);
    }

    private void entry(Player p, ConfigurationSection cs, String key, int slot) {
        String full = path.isEmpty() ? key : path + "." + key;

        if (cs.isConfigurationSection(key)) {
            ConfigurationSection sub = cs.getConfigurationSection(key);
            int n = sub == null ? 0 : sub.getKeys(false).size();
            set(slot, Items.make(Material.CHEST, "<gold>" + key, List.of(
                    "<gray>Bereich mit " + n + " Einträgen",
                    "<yellow>Klick: öffnen")), e -> new ConfigEditorMenu(plugin, full, 0).open(p));
            return;
        }

        Object v = cs.get(key);
        if (v instanceof Boolean b) {
            set(slot, Items.make(b ? Material.LIME_DYE : Material.GRAY_DYE, "<white>" + key, List.of(
                    "<gray>Aktuell: " + (b ? "<green>an" : "<red>aus"),
                    "<yellow>Klick: umschalten")), e -> {
                write(full, !b);
                open(p);
            });
        } else if (v instanceof Number num) {
            boolean isFloat = v instanceof Double || v instanceof Float;
            double step = isFloat ? stepFloat(num.doubleValue()) : stepInt(num.longValue());
            set(slot, Items.make(Material.IRON_NUGGET, "<white>" + key, List.of(
                    "<gray>Aktuell: <white>" + num,
                    "<yellow>Links: +" + fmt(step) + "  Rechts: -" + fmt(step),
                    "<yellow>Shift: zehnfacher Schritt")), e -> {
                double delta = (e.isRightClick() ? -1 : 1) * step * (e.isShiftClick() ? 10 : 1);
                double nv = num.doubleValue() + delta;
                if (num.doubleValue() >= 0 && nv < 0) nv = 0;
                Object out;
                if (isFloat) {
                    out = Math.round(nv * 100) / 100.0;
                } else if (v instanceof Integer) {
                    out = (int) Math.round(nv);
                } else {
                    out = Math.round(nv);
                }
                write(full, out);
                open(p);
            });
        } else if (v instanceof String s) {
            set(slot, Items.make(Material.NAME_TAG, "<white>" + key, List.of(
                    "<gray>Aktuell: <white>" + Msg.escape(s),
                    "<yellow>Klick: neuen Text eingeben")), e -> plugin.prompts().ask(p, key + " (neuer Wert):", text -> {
                write(full, text);
                open(p);
            }));
        } else if (v instanceof List<?> list) {
            set(slot, Items.make(Material.BOOK, "<white>" + key, List.of(
                    "<gray>" + list.size() + " Einträge",
                    "<yellow>Klick: Liste bearbeiten")), e -> new ConfigListMenu(plugin, full, 0).open(p));
        } else {
            set(slot, Items.make(Material.PAPER, "<gray>" + key, List.of(
                    "<dark_gray>Nicht direkt editierbar")), null);
        }
    }

    private static double stepInt(long v) {
        long a = Math.abs(v);
        if (a >= 1000) return 100;
        if (a >= 100) return 10;
        return 1;
    }

    private static double stepFloat(double v) {
        double a = Math.abs(v);
        if (a >= 10) return 1;
        if (a >= 1) return 0.1;
        return 0.01;
    }

    private static String fmt(double step) {
        return step >= 1 ? String.valueOf((long) step) : String.format(Locale.ROOT, "%.2f", step);
    }

    private void write(String full, Object value) {
        plugin.getConfig().set(full, value);
        plugin.saveConfig();
        Sfx.play(plugin, null, Sfx.Kind.CLICK);
    }
}
