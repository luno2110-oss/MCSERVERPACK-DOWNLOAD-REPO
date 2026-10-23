package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Juwelenshop pro Modus (Survival und SMP). Kategorien und Artikel stehen in der config.yml unter jewelshop.
 * Kategorietypen: ITEMS (Artikel kaufen mit Juwelen), PACKAGES (Juwelen-Pakete), PLACEHOLDER (Barriere).
 * Nur Ränge, Keys und Juwelen haben Inhalt. Alles andere ist vorbereitet und wird im Editor aktiviert.
 */
public final class JewelStoreService {

    public record Cat(String id, String name, Material icon, String type, boolean active) {}

    public record Item(String id, String name, Material icon, long price, String command, String lore) {}

    private final KubyPlugins plugin;

    public JewelStoreService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public static String normalize(String mode) {
        return ModeService.SMP.equals(mode) ? ModeService.SMP : ModeService.SURVIVAL;
    }

    private static Material icon(String raw) {
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.CHEST;
        }
    }

    public List<Cat> categories(String mode) {
        List<Cat> out = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("jewelshop." + normalize(mode) + ".categories")) {
            String[] p = raw.split(";", 5);
            if (p.length < 5) continue;
            out.add(new Cat(p[0].trim(), p[1].trim(), icon(p[2]), p[3].trim().toUpperCase(Locale.ROOT),
                    Boolean.parseBoolean(p[4].trim())));
        }
        return out;
    }

    public List<Item> items(String mode, String catId) {
        List<Item> out = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("jewelshop." + normalize(mode) + ".items." + catId)) {
            String[] p = raw.split(";", 6);
            if (p.length < 6) continue;
            try {
                out.add(new Item(p[0].trim(), p[1].trim(), icon(p[2]), Long.parseLong(p[3].trim()), p[4].trim(), p[5].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Gibt null bei Erfolg zurück. */
    public String buy(Player p, String mode, Item it) {
        if (!plugin.jewels().take(p.getUniqueId(), it.price())) {
            return "Dir fehlen " + (it.price() - plugin.jewels().balance(p.getUniqueId())) + " Juwelen.";
        }
        String cmd = it.command().replace("%player%", p.getName());
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd));
        return null;
    }

    public void toggle(String mode, int idx) {
        String key = "jewelshop." + normalize(mode) + ".categories";
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(key));
        if (idx < 0 || idx >= list.size()) return;
        String[] p = list.get(idx).split(";", 5);
        if (p.length < 5) return;
        boolean now = !Boolean.parseBoolean(p[4].trim());
        list.set(idx, p[0] + ";" + p[1] + ";" + p[2] + ";" + p[3] + ";" + now);
        plugin.getConfig().set(key, list);
        plugin.saveConfig();
    }

    /** Format: id;Name;ICON;Preis;Befehl;Lore. Gibt null bei Erfolg zurück. */
    public String addItem(String mode, String catId, String raw) {
        String[] p = raw.split(";", 6);
        if (p.length < 6) return "Format: id;Name;ICON;Preis;Befehl;Lore";
        try {
            Long.parseLong(p[3].trim());
        } catch (NumberFormatException e) {
            return "Der Preis muss eine Zahl sein.";
        }
        String key = "jewelshop." + normalize(mode) + ".items." + catId;
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(key));
        list.add(raw.trim());
        plugin.getConfig().set(key, list);
        plugin.saveConfig();
        return null;
    }

    public void removeItem(String mode, String catId, int idx) {
        String key = "jewelshop." + normalize(mode) + ".items." + catId;
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(key));
        if (idx < 0 || idx >= list.size()) return;
        list.remove(idx);
        plugin.getConfig().set(key, list);
        plugin.saveConfig();
    }
}
