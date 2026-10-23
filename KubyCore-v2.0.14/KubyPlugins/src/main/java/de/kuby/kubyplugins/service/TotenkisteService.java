package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Totenkiste: eine Kiste, die man selbst setzt. Beim Tod wandert das komplette Inventar
 * (Hauptinventar, Rüstung, Offhand) hinein. Ausbau platzweise per XP bis 41 Plätze.
 * Stufe 1: jeder darf öffnen und abbauen. Stufe 2: andere nehmen nur ungeschützte Items, nicht abbaubar.
 * Stufe 3: nur der Besitzer darf öffnen (einstellbar), nicht abbaubar, nicht sprengbar.
 */
public final class TotenkisteService {

    public static final int MAX_SLOTS = 41;
    private static final int GUI_STORAGE = 45;

    public static final class Box {
        public final UUID owner;
        public final String world;
        public final int x;
        public final int y;
        public final int z;
        public int level = 1;
        public int unlocked = 9;
        public final ItemStack[] items = new ItemStack[MAX_SLOTS];

        Box(UUID owner, String world, int x, int y, int z) {
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String key() {
            return keyOf(world, x, y, z);
        }
    }

    public static final class Holder implements InventoryHolder {
        public final String key;
        Inventory inv;

        public Holder(String key) {
            this.key = key;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey marker;
    private final Map<String, Box> boxes = new HashMap<>();

    public TotenkisteService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "totenkisten.yml");
        this.marker = new NamespacedKey(plugin, "tk_locked");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            try {
                Box b = new Box(UUID.fromString(yml.getString(k + ".owner")), yml.getString(k + ".world"),
                        yml.getInt(k + ".x"), yml.getInt(k + ".y"), yml.getInt(k + ".z"));
                b.level = yml.getInt(k + ".level", 1);
                b.unlocked = Math.max(9, Math.min(MAX_SLOTS, yml.getInt(k + ".unlocked", 9)));
                ConfigurationSection s = yml.getConfigurationSection(k + ".items");
                if (s != null) {
                    for (String slot : s.getKeys(false)) {
                        int idx = Integer.parseInt(slot);
                        Object o = s.get(slot);
                        if (idx >= 0 && idx < MAX_SLOTS && o instanceof ItemStack it) b.items[idx] = it;
                    }
                }
                boxes.put(b.key(), b);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Totenkiste " + k + " übersprungen: " + e.getMessage());
            }
        }
    }

    public static String keyOf(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public synchronized Box at(Block b) {
        return boxes.get(keyOf(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public synchronized Box byKey(String key) {
        return boxes.get(key);
    }

    public synchronized Box ownedBy(UUID id) {
        for (Box b : boxes.values()) {
            if (b.owner.equals(id) && b.world.equals(plugin.modes().worldOf(plugin.modes().modeOf(id)))) return b;
        }
        return null;
    }

    public long slotXp() { return plugin.getConfig().getLong("totenkiste.slot-xp", 500); }
    public long level2Jewels() { return plugin.getConfig().getLong("totenkiste.level2-jewels", 100); }
    public long level3Jewels() { return plugin.getConfig().getLong("totenkiste.level3-jewels", 300); }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String place(Player p) {
        if (ownedBy(p.getUniqueId()) != null) return "Du hast schon eine Totenkiste.";
        Block target = p.getTargetBlockExact(5);
        if (target == null) return "Schau auf einen Block, neben dem die Totenkiste stehen soll.";
        BlockFace face = p.getTargetBlockFace(5);
        if (face == null) face = BlockFace.UP;
        Block dest = target.getRelative(face);
        if (!dest.getType().isAir()) return "Dort ist kein Platz.";
        dest.setType(Material.CHEST, true);
        Box b = new Box(p.getUniqueId(), dest.getWorld().getName(), dest.getX(), dest.getY(), dest.getZ());
        boxes.put(b.key(), b);
        save();
        return null;
    }

    /** Stufe setzen. Senken ist kostenlos, erhöhen kostet Juwelen und geht nur Schritt für Schritt. */
    public synchronized String setLevel(Player p, Box b, int target) {
        if (target < 1 || target > 3) return "Es gibt nur die Stufen 1 bis 3.";
        if (target == b.level) return "Diese Stufe ist schon aktiv.";
        if (target < b.level) {
            b.level = target;
            save();
            return null;
        }
        if (target != b.level + 1) return "Erhöhe die Stufe Schritt für Schritt.";
        long cost = target == 2 ? level2Jewels() : level3Jewels();
        if (!plugin.jewels().take(p.getUniqueId(), cost)) return "Du brauchst " + cost + " Juwelen für Stufe " + target + ".";
        b.level = target;
        save();
        return null;
    }

    /** Gibt null bei Erfolg zurück. Kauft den nächsten Platz. */
    public synchronized String buySlot(Player p, Box b) {
        if (b.unlocked >= MAX_SLOTS) return "Alle Plätze sind schon frei.";
        long cost = slotXp();
        var st = plugin.stats().get(p.getUniqueId());
        if (st.xp < cost) return "Dir fehlen " + Msg.coins(cost - st.xp) + " XP.";
        st.xp -= cost;
        plugin.stats().save();
        b.unlocked++;
        save();
        return null;
    }

    public synchronized boolean protectedItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        return plugin.itemValues().value(it) >= plugin.getConfig().getDouble("totenkiste.protect-min-value", 50);
    }

    public synchronized boolean canOpen(UUID viewer, Box b) {
        if (b.level == 3) {
            return viewer.equals(b.owner) && plugin.getConfig().getBoolean("totenkiste.owner-access-level3", true);
        }
        return true;
    }

    public synchronized Inventory open(Box b) {
        Holder h = new Holder(b.key());
        h.inv = Bukkit.createInventory(h, 54, Msg.parse("<dark_gray>Totenkiste"));
        render(b, h.inv);
        return h.inv;
    }

    private void render(Box b, Inventory inv) {
        for (int i = 0; i < GUI_STORAGE; i++) {
            if (i >= MAX_SLOTS) {
                inv.setItem(i, filler());
            } else if (i < b.unlocked) {
                inv.setItem(i, b.items[i]);
            } else {
                inv.setItem(i, lockedItem(b, i));
            }
        }
        for (int i = GUI_STORAGE; i < 54; i++) inv.setItem(i, filler());
        setControls(b, inv);
    }

    /** Nach Kauf oder Stufenwechsel: freie Plätze übernehmen, Sperren aktualisieren. */
    public synchronized void refresh(Box b, Inventory inv) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            ItemStack cur = inv.getItem(i);
            if (i < b.unlocked) {
                if (isDisplay(cur)) inv.setItem(i, null);
            } else {
                inv.setItem(i, lockedItem(b, i));
            }
        }
        setControls(b, inv);
    }

    private void setControls(Box b, Inventory inv) {
        String owner = Bukkit.getOfflinePlayer(b.owner).getName();
        inv.setItem(45, Items.make(Material.BOOK, "<gold>Totenkiste", List.of(
                "<gray>Besitzer: <white>" + (owner == null ? "?" : owner),
                "<gray>Plätze: <white>" + b.unlocked + " <dark_gray>/ <white>" + MAX_SLOTS,
                "<gray>Position: <white>" + b.x + " / " + b.y + " / " + b.z)));
        inv.setItem(47, Items.make(Material.IRON_BLOCK, "<aqua>Sicherung: Stufe " + b.level + " <gray>/ 3", List.of(
                "<gray>Stufe 1: jeder darf alles",
                "<gray>Stufe 2: nur ungeschützte Items entnehmbar, nicht abbaubar",
                "<gray>Stufe 3: nur Besitzer, nicht abbaubar, nicht sprengbar",
                "<yellow>Linksklick: Stufe erhöhen <dark_gray>(Juwelen)",
                "<yellow>Rechtsklick: Stufe senken <dark_gray>(kostenlos)")));
        if (b.unlocked >= MAX_SLOTS) {
            inv.setItem(49, Items.make(Material.BARRIER, "<green>Alle Plätze frei", List.of()));
        } else {
            inv.setItem(49, Items.make(Material.EXPERIENCE_BOTTLE, "<green>Nächsten Platz freischalten", List.of(
                    "<gray>Kosten: <aqua>" + Msg.coins(slotXp()) + " XP",
                    "<yellow>Klick: kaufen")));
        }
    }

    private ItemStack lockedItem(Box b, int slot) {
        if (slot == b.unlocked) {
            return mark(Items.make(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Platz freischalten", List.of(
                    "<gray>Schaltet Platz <white>#" + (slot + 1) + " <gray>frei",
                    "<gray>Kosten: <aqua>" + Msg.coins(slotXp()) + " XP",
                    "<yellow>Klick: kaufen")));
        }
        return mark(Items.make(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Gesperrt", List.of(
                "<gray>Wird nacheinander freigeschaltet")));
    }

    private ItemStack filler() {
        return Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private ItemStack mark(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isDisplay(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    /** Speichert die Inhalte der freigeschalteten Plätze aus dem offenen Fenster. */
    public synchronized void save(Box b, Inventory inv) {
        for (int i = 0; i < b.unlocked; i++) {
            ItemStack cur = inv.getItem(i);
            b.items[i] = isDisplay(cur) ? null : cur;
        }
        save();
    }

    /** Nimmt alle Items aus der Kiste. */
    public synchronized List<ItemStack> emptyAll(Box b) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (b.items[i] != null) out.add(b.items[i]);
            b.items[i] = null;
        }
        return out;
    }

    /** Legt die Items eines Todes in die Kiste. Was nicht passt, wird zurückgegeben. */
    public synchronized List<ItemStack> depositDeath(Box b, ItemStack[] contents) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack it : contents) {
            if (it == null || it.getType().isAir()) continue;
            boolean placed = false;
            for (int i = 0; i < b.unlocked && !placed; i++) {
                if (b.items[i] == null) {
                    b.items[i] = it.clone();
                    placed = true;
                }
            }
            if (!placed) left.add(it.clone());
        }
        save();
        return left;
    }

    public synchronized void remove(Box b) {
        boxes.remove(b.key());
        save();
    }

    public synchronized void saveNow() {
        save();
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        int i = 0;
        for (Box b : boxes.values()) {
            String k = "t" + (i++);
            out.set(k + ".owner", b.owner.toString());
            out.set(k + ".world", b.world);
            out.set(k + ".x", b.x);
            out.set(k + ".y", b.y);
            out.set(k + ".z", b.z);
            out.set(k + ".level", b.level);
            out.set(k + ".unlocked", b.unlocked);
            for (int s = 0; s < MAX_SLOTS; s++) {
                if (b.items[s] != null) out.set(k + ".items." + s, b.items[s]);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("totenkisten.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
