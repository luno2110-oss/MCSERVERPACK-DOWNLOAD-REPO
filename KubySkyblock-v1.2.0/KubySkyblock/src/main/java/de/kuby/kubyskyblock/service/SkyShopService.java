package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Eigener Shop für Skyblock (/shop, /sell und die Shop-Kachel im Skyblock-Menü): Bereiche aus der config
 * (shop.bereiche: "MATERIAL;Menge;Kaufpreis;Verkaufspreis je Stück;Name"). Kaufpreis 0 = nur verkaufen, Verkaufspreis 0 =
 * nur kaufen. Die Verkaufspreise gelten auch für Generator-Ertrag (Verkaufen und Auto-Verkauf). Items mit eigenem Bild
 * oder eigenen Daten (Generatoren, Event-Items) werden nie verkauft.
 */
public final class SkyShopService {

    public record Entry(Material material, int amount, long buy, long sell, String name) {}

    public record Section(String id, String name, Material icon, List<Entry> entries) {}

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final List<Section> sections = new ArrayList<>();
    private final Map<Material, Long> sellPrices = new EnumMap<>(Material.class);

    public SkyShopService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        reload();
    }

    public void reload() {
        sections.clear();
        sellPrices.clear();
        ConfigurationSection root = addon.getConfig().getConfigurationSection("shop.bereiche");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            List<Entry> entries = new ArrayList<>();
            for (String raw : s.getStringList("items")) {
                String[] p = raw.split(";");
                try {
                    Material m = Material.matchMaterial(p[0].trim());
                    if (m == null || !m.isItem()) {
                        addon.getLogger().warning("shop." + id + ": unbekanntes Item " + p[0]);
                        continue;
                    }
                    Entry e = new Entry(m, Math.max(1, Integer.parseInt(p[1].trim())), Long.parseLong(p[2].trim()), Long.parseLong(p[3].trim()),
                            p.length > 4 ? p[4].trim() : nice(m));
                    entries.add(e);
                    if (e.sell() > 0) sellPrices.merge(m, e.sell(), Math::max);
                } catch (RuntimeException ex) {
                    addon.getLogger().warning("shop." + id + ": ungültig: " + raw);
                }
            }
            sections.add(new Section(id, s.getString("name", id), icon == null ? Material.CHEST : icon, entries));
        }
    }

    public List<Section> sections() {
        return sections;
    }

    /** Verkaufspreis je Stück (0 = nicht verkaufbar). */
    public long sellPrice(Material m) {
        return sellPrices.getOrDefault(m, 0L);
    }

    static String nice(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Eigene Items (Generatoren, Event-Items, umbenannte Sachen) nie verkaufen. */
    static boolean plain(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return true;
        ItemMeta m = it.getItemMeta();
        return !m.hasItemModel() && !m.hasCustomModelData() && !m.hasDisplayName() && m.getPersistentDataContainer().isEmpty();
    }

    /** Kaufen: packs mal die Menge des Eintrags. */
    public String buy(Player p, Entry e, int packs) {
        if (e.buy() <= 0) return "Das kann man hier nur verkaufen.";
        long price = e.buy() * packs;
        if (!core.economy().withdraw(p.getUniqueId(), price)) return "Dafür brauchst du " + Msg.coins(price) + " $.";
        int left = e.amount() * packs;
        while (left > 0) {
            int n = Math.min(left, e.material().getMaxStackSize());
            p.getInventory().addItem(new ItemStack(e.material(), n)).values().forEach(r -> core.abholbox().add(p.getUniqueId(), r));
            left -= n;
        }
        return null;
    }

    /** Ein Item komplett aus dem Inventar verkaufen. Gibt den Erlös zurück (0 = nichts verkauft). */
    public long sell(Player p, Material m) {
        long unit = sellPrice(m);
        if (unit <= 0) return 0;
        long count = 0;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] != null && c[i].getType() == m && plain(c[i])) {
                count += c[i].getAmount();
                c[i] = null;
            }
        }
        if (count == 0) return 0;
        p.getInventory().setStorageContents(c);
        long sum = unit * count;
        core.economy().deposit(p.getUniqueId(), sum);
        core.questProgress(p, "verkaufen", m, count);
        return sum;
    }

    /** Alles Verkaufbare aus dem Inventar verkaufen. */
    public long sellAll(Player p) {
        long sum = 0;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || !plain(c[i])) continue;
            long unit = sellPrice(c[i].getType());
            if (unit <= 0) continue;
            sum += unit * c[i].getAmount();
            core.questProgress(p, "verkaufen", c[i].getType(), c[i].getAmount());
            c[i] = null;
        }
        if (sum > 0) {
            p.getInventory().setStorageContents(c);
            core.economy().deposit(p.getUniqueId(), sum);
        }
        return sum;
    }

    /** Wie viele Stück davon hat der Spieler (ohne eigene Items)? */
    public int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m && plain(it)) n += it.getAmount();
        return n;
    }
}
