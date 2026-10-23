package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enderchest mit 5 Reihen. Start: eine Reihe frei.
 * Die ersten drei Reihen werden Platz für Platz in Reihenfolge freigeschaltet (bezahlt mit XP),
 * Reihe 4 und 5 als Stufen. Der Sortier-Knopf fasst gleiche Items zusammen.
 */
public final class EnderChestService {

    public static final int SIZE = 45;
    public static final int ROWS = 5;
    public static final int SORT_SLOT = 47;
    public static final int STAGE_SLOT = 49;
    public static final int INFO_SLOT = 51;
    private static final int STEP_SLOTS = 27;

    public static final class Holder implements InventoryHolder {
        public final UUID owner;
        Inventory inv;

        public Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey marker;
    private final Map<UUID, boolean[]> unlocked = new HashMap<>();
    private final Map<UUID, ItemStack[]> stored = new HashMap<>();

    public EnderChestService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "enderchest.yml");
        this.marker = new NamespacedKey(plugin, "ec_locked");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            UUID id = UUID.fromString(k);
            boolean[] u = new boolean[SIZE];
            for (int idx : yml.getIntegerList(k + ".unlocked")) {
                if (idx >= 0 && idx < SIZE) u[idx] = true;
            }
            unlocked.put(id, u);
            ItemStack[] items = new ItemStack[SIZE];
            ConfigurationSection s = yml.getConfigurationSection(k + ".items");
            if (s != null) {
                for (String slot : s.getKeys(false)) {
                    int idx = Integer.parseInt(slot);
                    Object o = s.get(slot);
                    if (idx >= 0 && idx < SIZE && o instanceof ItemStack it) items[idx] = it;
                }
            }
            stored.put(id, items);
        }
    }

    private boolean[] unlockedOf(UUID id) {
        return unlocked.computeIfAbsent(id, k -> {
            boolean[] u = new boolean[SIZE];
            for (int i = 0; i < 9; i++) u[i] = true;
            return u;
        });
    }

    public synchronized boolean isUnlocked(UUID id, int slot) {
        return slot >= 0 && slot < SIZE && unlockedOf(id)[slot];
    }

    /** Anzahl vollständig freier Reihen von oben. */
    public synchronized int rowsDone(UUID id) {
        boolean[] u = unlockedOf(id);
        int rows = 0;
        while (rows < ROWS) {
            boolean full = true;
            for (int c = 0; c < 9; c++) {
                if (!u[rows * 9 + c]) {
                    full = false;
                    break;
                }
            }
            if (!full) break;
            rows++;
        }
        return rows;
    }

    /** Nächster freizuschaltender Platz in den ersten drei Reihen, -1 wenn alle frei sind. */
    private int nextSlot(UUID id) {
        boolean[] u = unlockedOf(id);
        for (int i = 0; i < STEP_SLOTS; i++) {
            if (!u[i]) return i;
        }
        return -1;
    }

    private long slotXp() { return plugin.getConfig().getLong("enderchest.slot-xp", 1000); }
    private long stage2Xp() { return plugin.getConfig().getLong("enderchest.stage2-xp", 20000); }
    private long stage3Xp() { return plugin.getConfig().getLong("enderchest.stage3-xp", 50000); }
    private long xpOf(UUID id) { return plugin.stats().get(id).xp; }

    public synchronized Inventory open(Player p) {
        UUID id = p.getUniqueId();
        Holder h = new Holder(id);
        h.inv = Bukkit.createInventory(h, 54, Msg.parse("<dark_gray>Enderchest"));
        boolean[] u = unlockedOf(id);
        ItemStack[] items = stored.computeIfAbsent(id, k -> new ItemStack[SIZE]);
        for (int slot = 0; slot < SIZE; slot++) {
            h.inv.setItem(slot, u[slot] ? items[slot] : lockedItem(id, slot));
        }
        for (int slot = SIZE; slot < 54; slot++) {
            h.inv.setItem(slot, filler());
        }
        setControls(id, h.inv);
        return h.inv;
    }

    /** Aktualisiert ein offenes Fenster nach einem Kauf oder Sortieren, ohne Inhalte zu verlieren. */
    public synchronized void refresh(UUID id, Inventory inv) {
        boolean[] u = unlockedOf(id);
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack cur = inv.getItem(slot);
            if (u[slot]) {
                if (isDisplay(cur)) inv.setItem(slot, null);
            } else {
                inv.setItem(slot, lockedItem(id, slot));
            }
        }
        setControls(id, inv);
    }

    private void setControls(UUID id, Inventory inv) {
        inv.setItem(SORT_SLOT, Items.make(Material.COMPARATOR, "<aqua>Sortieren", List.of(
                "<gray>Ordnet alle Items und fasst Stapel zusammen")));
        inv.setItem(STAGE_SLOT, stageItem(id));
        int free = 0;
        for (boolean b : unlockedOf(id)) if (b) free++;
        inv.setItem(INFO_SLOT, Items.make(Material.BOOK, "<aqua>Enderchest", List.of(
                "<gray>Freie Plätze: <white>" + free + " <dark_gray>/ <white>" + SIZE,
                "<gray>Deine XP: <white>" + Msg.coins(xpOf(id)))));
    }

    private ItemStack lockedItem(UUID id, int slot) {
        if (slot == nextSlot(id)) {
            return mark(Items.make(Material.ORANGE_STAINED_GLASS_PANE, "<gold>Slot freischalten", List.of(
                    "<gray>Schaltet Slot <white>#" + (slot + 1) + " <gray>frei",
                    "<gray>Immer der nächste Slot der Reihe",
                    "<gray>Kosten: <aqua>" + Msg.coins(slotXp()) + " XP",
                    "<yellow>Klick zum Kaufen")));
        }
        String hint = slot < STEP_SLOTS ? "<gray>Wird nach dem vorherigen Slot freigeschaltet"
                : "<gray>Wird über die Stufen freigeschaltet";
        return mark(Items.make(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Gesperrt", List.of(hint)));
    }

    private ItemStack stageItem(UUID id) {
        int rows = rowsDone(id);
        if (rows >= ROWS) {
            return Items.make(Material.BARRIER, "<green>Maximal ausgebaut", List.of());
        }
        if (rows < 3) {
            return Items.make(Material.GRAY_DYE, "<gray>Stufe gesperrt", List.of(
                    "<gray>Schalte zuerst die ersten 3 Reihen frei"));
        }
        long cost = rows == 3 ? stage2Xp() : stage3Xp();
        return Items.make(Material.NETHER_STAR, "<gold>Stufe " + (rows == 3 ? 2 : 3) + " freischalten", List.of(
                "<gray>Reihe " + (rows + 1) + " mit 9 Plätzen",
                "<gray>Kosten: <aqua>" + Msg.coins(cost) + " XP",
                "<yellow>Klick: kaufen"));
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

    /** Gibt null bei Erfolg zurück. */
    public synchronized String buySlot(Player p, int slot) {
        UUID id = p.getUniqueId();
        if (slot < 0 || slot >= STEP_SLOTS) return "Reihe 4 und 5 gibt es nur als Stufe.";
        boolean[] u = unlockedOf(id);
        if (u[slot]) return "Dieser Platz ist schon frei.";
        if (slot != nextSlot(id)) return "Schalte die Plätze nacheinander frei.";
        long cost = slotXp();
        if (xpOf(id) < cost) return "Dir fehlen " + Msg.coins(cost - xpOf(id)) + " XP.";
        plugin.stats().get(id).xp -= cost;
        plugin.stats().save();
        u[slot] = true;
        save(id, null);
        return null;
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String buyStage(Player p) {
        UUID id = p.getUniqueId();
        int rows = rowsDone(id);
        if (rows >= ROWS) return "Die Enderchest ist schon maximal ausgebaut.";
        if (rows < 3) return "Schalte zuerst alle Plätze der ersten drei Reihen frei.";
        long cost = rows == 3 ? stage2Xp() : stage3Xp();
        if (xpOf(id) < cost) return "Dir fehlen " + Msg.coins(cost - xpOf(id)) + " XP.";
        plugin.stats().get(id).xp -= cost;
        plugin.stats().save();
        boolean[] u = unlockedOf(id);
        for (int c = 0; c < 9; c++) u[rows * 9 + c] = true;
        save(id, null);
        return null;
    }

    /** Ordnet die Items der freien Plätze: gleiche Items werden zusammengefasst, sortiert nach Material. */
    public synchronized void sort(UUID id, Inventory inv) {
        boolean[] u = unlockedOf(id);
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < SIZE; slot++) {
            if (!u[slot]) continue;
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir() || isDisplay(it)) continue;
            items.add(it.clone());
            inv.setItem(slot, null);
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack it : items) {
            boolean placed = false;
            for (ItemStack m : merged) {
                if (m.isSimilar(it) && m.getAmount() + it.getAmount() <= m.getMaxStackSize()) {
                    m.setAmount(m.getAmount() + it.getAmount());
                    placed = true;
                    break;
                }
            }
            if (!placed) merged.add(it);
        }
        merged.sort(Comparator.comparing((ItemStack i) -> i.getType().name()).thenComparing(i -> -i.getAmount()));
        int slot = 0;
        for (ItemStack it : merged) {
            while (slot < SIZE && !u[slot]) slot++;
            if (slot >= SIZE) break;
            inv.setItem(slot++, it);
        }
    }

    /** Zustand für einen Spielmodus. */
    public record State(boolean[] unlocked, ItemStack[] items) {}

    public synchronized State exportState(UUID id) {
        boolean[] u = unlockedOf(id).clone();
        ItemStack[] it = stored.computeIfAbsent(id, k -> new ItemStack[SIZE]).clone();
        return new State(u, it);
    }

    /** Null setzt auf den Anfangszustand zurück. */
    public synchronized void importState(UUID id, State s) {
        if (s == null) {
            unlocked.remove(id);
            stored.remove(id);
        } else {
            unlocked.put(id, s.unlocked().clone());
            stored.put(id, s.items().clone());
        }
        writeFile();
    }

    /** Speichert den Inhalt der freien Plätze. Ohne Inventar wird nur der Freischaltstand geschrieben. */
    public synchronized void save(UUID id, Inventory inv) {
        boolean[] u = unlockedOf(id);
        ItemStack[] items = stored.computeIfAbsent(id, k -> new ItemStack[SIZE]);
        if (inv != null) {
            for (int slot = 0; slot < SIZE; slot++) {
                if (!u[slot]) continue;
                ItemStack it = inv.getItem(slot);
                items[slot] = isDisplay(it) ? null : it;
            }
        }
        writeFile();
    }

    private void writeFile() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, boolean[]> e : unlocked.entrySet()) {
            String k = e.getKey().toString();
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < SIZE; i++) {
                if (e.getValue()[i]) idx.add(i);
            }
            out.set(k + ".unlocked", idx);
            ItemStack[] items = stored.getOrDefault(e.getKey(), new ItemStack[SIZE]);
            for (int i = 0; i < SIZE; i++) {
                if (items[i] != null) out.set(k + ".items." + i, items[i]);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("enderchest.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
