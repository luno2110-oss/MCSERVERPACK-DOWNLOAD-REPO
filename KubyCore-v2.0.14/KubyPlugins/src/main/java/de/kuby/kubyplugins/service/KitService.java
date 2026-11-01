package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Kits: Items, Icon, Abklingzeit, Rang und Modi je Kit. Admins legen sie im Spiel an und bearbeiten sie (/kits bearbeiten,
 * KitAdminMenu und KitEditMenu); gespeichert in kits-config.yml, die Items vollständig (Verzauberungen, Namen, eigene
 * Modelle). Beim ersten Start werden die Kits aus config.yml (kits:) übernommen. Leere Kits werden angezeigt, lassen
 * sich aber nicht abholen. Abholzeiten je Spieler in kits.yml.
 */
public final class KitService implements ModeData {

    /** rank: ab welchem Rang das Kit erlaubt ist, leer = für alle; modes: in welchen Modi es zu sehen ist, leer = allen. */
    public record Kit(String id, String name, Material icon, long cooldownMinutes, List<ItemStack> items, String rank,
                      Set<String> modes) {
        public Kit(String id, String name, Material icon, long cooldownMinutes, List<ItemStack> items, String rank) {
            this(id, name, icon, cooldownMinutes, items, rank, Set.of());
        }

        public boolean visibleIn(String mode) {
            return modes.isEmpty() || modes.contains(mode);
        }
    }

    private final KubyPlugins plugin;
    private final File file, defFile;
    private final Map<UUID, Map<String, Long>> lastClaim = new HashMap<>();
    /** Kits in Anzeige-Reihenfolge. */
    private final Map<String, Kit> defs = new LinkedHashMap<>();

    public KitService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        this.defFile = new File(plugin.getDataFolder(), "kits-config.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            Map<String, Long> m = new HashMap<>();
            for (String kit : s.getKeys(false)) m.put(kit, s.getLong(kit));
            lastClaim.put(UUID.fromString(k), m);
        }
        loadDefs();
    }

    /** Alle Kits (Reihenfolge wie in kits-config.yml). */
    public synchronized List<Kit> kits() {
        return new ArrayList<>(defs.values());
    }

    /** Kits, die im Modus des Spielers zu sehen sind. */
    public List<Kit> kitsFor(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<Kit> out = new ArrayList<>();
        for (Kit k : kits()) if (k.visibleIn(mode)) out.add(k);
        return out;
    }

    public synchronized Kit kit(String id) {
        return defs.get(id);
    }

    /** Kit anlegen oder ersetzen und speichern. */
    public synchronized void put(Kit kit) {
        defs.put(kit.id(), kit);
        saveDefs();
    }

    public synchronized void delete(String id) {
        defs.remove(id);
        saveDefs();
    }

    /** Neues leeres Kit mit freier ID. */
    public synchronized Kit create() {
        int n = defs.size() + 1;
        while (defs.containsKey("kit" + n)) n++;
        Kit k = new Kit("kit" + n, "Neues Kit", Material.CHEST, 60, List.of(), "", Set.of());
        put(k);
        return k;
    }

    private synchronized void loadDefs() {
        defs.clear();
        if (!defFile.exists()) {
            // erster Start: Kits aus der config.yml übernehmen
            ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
            if (root != null) {
                Map<String, Kit> sorted = new TreeMap<>();
                for (String id : root.getKeys(false)) {
                    Kit k = read(id, root.getConfigurationSection(id));
                    if (k != null) sorted.put(id, k);
                }
                defs.putAll(sorted);
            }
            saveDefs();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(defFile);
        ConfigurationSection root = yml.getConfigurationSection("kits");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            Kit k = read(id, root.getConfigurationSection(id));
            if (k != null) defs.put(id, k);
        }
    }

    private static Kit read(String id, ConfigurationSection s) {
        if (s == null) return null;
        Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
        List<ItemStack> items = new ArrayList<>();
        for (String raw : s.getStringList("items")) {
            ItemStack it = raw.startsWith("b64:") ? fromBase64(raw.substring(4)) : parse(raw);
            if (it != null) items.add(it);
        }
        Set<String> modes = new LinkedHashSet<>();
        for (String m : s.getString("modus", "").split("[,; ]+")) if (!m.isBlank()) modes.add(m.trim().toLowerCase(Locale.ROOT));
        return new Kit(id, s.getString("name", "Kit " + id), icon == null || !icon.isItem() ? Material.CHEST : icon,
                Math.max(0, s.getLong("cooldown-minutes", 30)), items, s.getString("rank", "").trim().toLowerCase(Locale.ROOT), modes);
    }

    private synchronized void saveDefs() {
        YamlConfiguration out = new YamlConfiguration();
        out.options().setHeader(List.of(
                "Kits, im Spiel bearbeitet mit /kits bearbeiten (Admins). Items stehen vollständig als b64:… (mit Verzauberungen,",
                "Namen und eigenen Modellen); von Hand gehen auch Zeilen wie \"IRON_SWORD:1:SHARPNESS=2\".",
                "rank: ab welchem Rang (leer = alle; vip, ultimate, warrior, legend oder platin, diamant, smaragd, netherit)",
                "modus: in welchen Modi das Kit zu sehen ist (leer = alle; survival, smp, skyblock, mit Komma getrennt)"));
        for (Kit k : defs.values()) {
            String b = "kits." + k.id() + ".";
            out.set(b + "name", k.name());
            out.set(b + "icon", k.icon().name());
            out.set(b + "rank", k.rank());
            out.set(b + "cooldown-minutes", k.cooldownMinutes());
            out.set(b + "modus", String.join(",", k.modes()));
            List<String> items = new ArrayList<>();
            for (ItemStack it : k.items()) if (it != null && !it.getType().isAir()) items.add("b64:" + toBase64(it));
            out.set(b + "items", items);
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(defFile);
        } catch (IOException e) {
            plugin.getLogger().warning("kits-config.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static String toBase64(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack fromBase64(String s) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s.trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Format: MATERIAL:MENGE:ENCHANTMENT=STUFE,ENCHANTMENT=STUFE */
    private static ItemStack parse(String raw) {
        String[] p = raw.split(":", 3);
        Material m = Material.matchMaterial(p[0].trim().toUpperCase(Locale.ROOT));
        if (m == null) return null;
        int amount = 1;
        try {
            if (p.length > 1) amount = Integer.parseInt(p[1].trim());
        } catch (NumberFormatException ignored) {
            amount = 1;
        }
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        if (p.length > 2) {
            for (String e : p[2].split(",")) {
                String[] kv = e.split("=");
                if (kv.length != 2) continue;
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(kv[0].trim().toLowerCase(Locale.ROOT)));
                try {
                    if (ench != null) it.addUnsafeEnchantment(ench, Integer.parseInt(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                    // Ungültige Stufe wird übersprungen
                }
            }
        }
        return it;
    }

    /** Rang-Reihenfolge, niedrigster zuerst (ranks.order in der config.yml). */
    public List<String> rankOrder() {
        List<String> order = plugin.getConfig().getStringList("ranks.order");
        return order.isEmpty() ? List.of("vip", "ultimate", "warrior", "legend") : order;
    }

    /** Survival-Ränge (homes.survival.ranks in der config.yml), niedrigster zuerst. */
    public List<String> survivalRanks() {
        List<String> order = plugin.getConfig().getStringList("homes.survival.ranks");
        return order.isEmpty() ? List.of("platin", "diamant", "smaragd", "netherit") : order;
    }

    /** Alle Ränge, die ein Kit verlangen kann (für den Editor): SMP/Skyblock, dann Survival. */
    public List<String> allRanks() {
        List<String> all = new ArrayList<>(rankOrder());
        for (String r : survivalRanks()) if (!all.contains(r)) all.add(r);
        return all;
    }

    /** Höchster Rang des Spielers als Index in rankOrder(), -1 ohne Rang. Erkennt LuckPerms (group.x) und kuby.rank.x. */
    public int rankLevel(Player p) {
        return rankLevel(p, rankOrder());
    }

    private static int rankLevel(Player p, List<String> order) {
        for (int i = order.size() - 1; i >= 0; i--) {
            String r = order.get(i).toLowerCase(Locale.ROOT);
            if (p.hasPermission("group." + r) || p.hasPermission("kuby.rank." + r)) return i;
        }
        return -1;
    }

    /** Darf der Spieler das Kit nehmen? Höhere Ränge dürfen auch die Kits der niedrigeren (je Rang-Liste). */
    public boolean allowed(Player p, Kit kit) {
        if (kit.rank().isEmpty() || p.hasPermission("kuby.admin")) return true;
        List<String> order = rankOrder().contains(kit.rank()) ? rankOrder() : survivalRanks();
        int needed = order.indexOf(kit.rank());
        return needed < 0 || rankLevel(p, order) >= needed;
    }

    /** Rangname für die Anzeige, z. B. "VIP". */
    public static String rankName(String rank) {
        return rank.equals("vip") ? "VIP" : rank.isEmpty() ? "" : Character.toUpperCase(rank.charAt(0)) + rank.substring(1);
    }

    /** Verbleibende Cooldown-Zeit in Millisekunden. 0 = verfügbar. */
    public synchronized long remaining(UUID id, Kit kit) {
        long last = lastClaim.getOrDefault(id, Map.of()).getOrDefault(kit.id(), 0L);
        long end = last + kit.cooldownMinutes() * 60_000L;
        return Math.max(0, end - System.currentTimeMillis());
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String claim(Player p, Kit kit) {
        if (!allowed(p, kit)) return "Dieses Kit gibt es ab Rang " + rankName(kit.rank()) + ".";
        if (kit.items().isEmpty()) return "Dieses Kit ist noch leer.";
        long left = remaining(p.getUniqueId(), kit);
        if (left > 0) return "Cooldown: " + format(left) + ".";
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : kit.items()) {
            ItemStack give = it.clone();
            String n = give.getType().name();
            if (n.endsWith("_HELMET") && inv.getHelmet() == null) inv.setHelmet(give);
            else if (n.endsWith("_CHESTPLATE") && inv.getChestplate() == null) inv.setChestplate(give);
            else if (n.endsWith("_LEGGINGS") && inv.getLeggings() == null) inv.setLeggings(give);
            else if (n.endsWith("_BOOTS") && inv.getBoots() == null) inv.setBoots(give);
            else inv.addItem(give).values().forEach(rest -> plugin.abholbox().add(p.getUniqueId(), rest));
        }
        lastClaim.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(kit.id(), System.currentTimeMillis());
        save();
        return null;
    }

    public static String format(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        return m + "m " + s + "s";
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        lastClaim.forEach((id, m) -> m.forEach((kit, t) -> out.set(id + "." + kit, t)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("kits.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public static String describe(Kit kit) {
        return kit.items().isEmpty() ? "leer" : kit.items().size() + " Items";
    }

    public static String msgNone() {
        return Msg.escape("");
    }

    @Override
    public String key() { return "kits"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        lastClaim.getOrDefault(id, Map.of()).forEach((kit, t) -> m.put("kit|" + kit, t));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        lastClaim.remove(id);
        if (m != null && !m.isEmpty()) {
            Map<String, Long> c = new java.util.HashMap<>();
            m.forEach((k, v) -> {
                if (k.startsWith("kit|")) c.put(k.substring(4), ModeData.lng(v));
            });
            lastClaim.put(id, c);
        }
        save();
    }
}
