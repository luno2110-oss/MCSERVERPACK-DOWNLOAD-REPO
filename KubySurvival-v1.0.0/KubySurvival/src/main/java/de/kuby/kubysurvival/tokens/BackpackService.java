package de.kuby.kubysurvival.tokens;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rucksack aus dem Token-Shop: 1 bis 6 Reihen, nur in Survival. Öffnen mit /backpack oder dem Rucksack-Item
 * (/backpack item). Pro Spieler gibt es genau ein Inventar-Objekt, mehrere Ansichten zeigen also denselben Inhalt.
 * Rucksack-Items und Shulkerkisten dürfen nicht hinein.
 */
public final class BackpackService implements Listener {

    /** Halter des Rucksack-Inventars. */
    public static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inv;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final NamespacedKey tag;
    private final Map<UUID, Integer> rows = new HashMap<>();
    private final Map<UUID, ItemStack[]> stored = new HashMap<>();
    private final Map<UUID, Holder> open = new HashMap<>();

    public BackpackService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "backpacks.yml");
        this.tag = new NamespacedKey(addon, "backpack");
        load();
    }

    public synchronized int rows(UUID id) {
        return rows.getOrDefault(id, 0);
    }

    /** Preis für die nächste Reihe oder -1, wenn schon 6 Reihen. */
    public long nextPrice(UUID id) {
        List<Long> prices = core.getConfig().getLongList("tokens.backpack-prices");
        if (prices.isEmpty()) prices = List.of(5L, 8L, 12L, 16L, 20L, 25L);
        int r = rows(id);
        return r >= Math.min(6, prices.size()) ? -1 : prices.get(r);
    }

    /** Schaltet eine Reihe frei (Tokens schon abgezogen). */
    public synchronized void addRow(UUID id) {
        int r = Math.min(6, rows(id) + 1);
        rows.put(id, r);
        Holder h = open.get(id);
        if (h != null) {
            // Inhalt in das größere Inventar umziehen.
            ItemStack[] content = h.inv.getContents();
            for (org.bukkit.entity.HumanEntity viewer : List.copyOf(h.inv.getViewers())) viewer.closeInventory();
            stored.put(id, content);
            open.remove(id);
        }
        save();
    }

    public void open(Player p) {
        UUID id = p.getUniqueId();
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(id))) {
            Msg.send(p, "<red>Den Rucksack gibt es nur in Survival.");
            return;
        }
        int r = rows(id);
        if (r <= 0) {
            Msg.send(p, "<gray>Du hast noch keinen Rucksack. Freischalten im <click:run_command:'/tokenshop'><light_purple>Token-Shop</click><gray>.");
            return;
        }
        Holder h;
        synchronized (this) {
            h = open.get(id);
            if (h == null) {
                h = new Holder(id);
                h.inv = Bukkit.createInventory(h, r * 9, Msg.parse("<dark_green>Rucksack"));
                ItemStack[] content = stored.get(id);
                if (content != null) {
                    for (int i = 0; i < content.length && i < h.inv.getSize(); i++) h.inv.setItem(i, content[i]);
                }
                open.put(id, h);
            }
        }
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1f);
    }

    public ItemStack item() {
        ItemStack it = Items.make(Material.LEATHER, "<dark_green>Rucksack", List.of("<gray>Rechtsklick: Rucksack öffnen"));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isItem(e.getItem())) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isItem(e.getItemInHand())) e.setCancelled(true);
    }

    /** Keine Rucksäcke, Bündel oder Shulkerkisten in den Rucksack. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder)) return;
        ItemStack moving = null;
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            moving = e.getCursor();
            if (e.getClick().isKeyboardClick() && e.getHotbarButton() >= 0) {
                moving = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            }
        } else if (e.isShiftClick()) {
            moving = e.getCurrentItem();
        }
        if (moving != null && forbidden(moving)) {
            e.setCancelled(true);
            e.getWhoClicked().sendActionBar(Msg.parse("<red>Das passt nicht in den Rucksack."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder)) return;
        int top = e.getView().getTopInventory().getSize();
        boolean intoTop = e.getRawSlots().stream().anyMatch(s -> s < top);
        if (intoTop && forbidden(e.getOldCursor())) e.setCancelled(true);
    }

    private boolean forbidden(ItemStack it) {
        if (it.getType().isAir()) return false;
        return isItem(it) || it.getType().name().endsWith("SHULKER_BOX") || it.getType().name().endsWith("BUNDLE");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h)) return;
        synchronized (this) {
            stored.put(h.owner, h.inv.getContents());
            if (h.inv.getViewers().size() <= 1) open.remove(h.owner);
        }
        save();
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                int r = Math.max(0, Math.min(6, ps.getInt(k + ".rows")));
                rows.put(id, r);
                ItemStack[] content = new ItemStack[54];
                ConfigurationSection items = ps.getConfigurationSection(k + ".items");
                if (items != null) {
                    for (String slot : items.getKeys(false)) {
                        int i = Integer.parseInt(slot);
                        if (i >= 0 && i < 54) content[i] = items.getItemStack(slot);
                    }
                }
                stored.put(id, content);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : rows.entrySet()) {
            UUID id = e.getKey();
            String b = "players." + id + ".";
            out.set(b + "rows", e.getValue());
            Holder h = open.get(id);
            ItemStack[] content = h != null ? h.inv.getContents() : stored.get(id);
            if (content == null) continue;
            for (int i = 0; i < content.length; i++) {
                if (content[i] != null && !content[i].getType().isAir()) out.set(b + "items." + i, content[i]);
            }
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("backpacks.yml: " + ex.getMessage());
        }
    }

    /** Beim Abschalten alle offenen Rucksäcke schließen und speichern. */
    public void shutdown() {
        for (Holder h : List.copyOf(open.values())) {
            for (org.bukkit.entity.HumanEntity viewer : List.copyOf(h.inv.getViewers())) viewer.closeInventory();
        }
        save();
    }
}
