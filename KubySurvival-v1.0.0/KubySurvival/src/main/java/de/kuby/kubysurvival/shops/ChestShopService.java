package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kistenläden: Schild mit [Shop] in der ersten Zeile an eine Kiste oder ein Fass (oder obendrauf) setzen.
 * Erlaubt in jedem Grundstück, in dem man bauen darf. Der Besitzer stellt per Klick aufs Schild Ware, Menge,
 * Verkaufs- und Ankaufspreis ein. Alles Geld läuft über die Ladenkasse: Kunden zahlen hinein, beim Ankauf wird daraus
 * bezahlt, der Besitzer hebt ab oder zahlt ein. Nur der Besitzer kann Kiste und Schild öffnen oder abbauen.
 */
public final class ChestShopService implements Listener {

    /** Ein Laden. */
    public static final class Shop {
        public final UUID owner;
        public final Location sign;
        public final Location container;
        public ItemStack item;
        public int amount = 1;
        public long buyPrice;
        public long sellPrice;
        public long cash;

        Shop(UUID owner, Location sign, Location container) {
            this.owner = owner;
            this.sign = sign;
            this.container = container;
        }
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<String, Shop> bySign = new HashMap<>();
    private final Map<String, Shop> byContainer = new HashMap<>();

    public ChestShopService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "chestshops.yml");
        load();
    }

    static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public synchronized List<Shop> of(UUID owner) {
        List<Shop> out = new ArrayList<>();
        for (Shop s : bySign.values()) if (s.owner.equals(owner)) out.add(s);
        return out;
    }

    private int max() {
        return core.getConfig().getInt("shops.max-chestshops", 20);
    }

    // ---------- Kiste ----------

    /** Alle Blöcke eines Behälters (bei Doppelkisten beide Hälften). */
    private static List<Block> parts(Block b) {
        List<Block> out = new ArrayList<>();
        BlockState st = b.getState(false);
        if (st instanceof Chest c && c.getInventory().getHolder() instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof Chest l) out.add(l.getBlock());
            if (dc.getRightSide() instanceof Chest r) out.add(r.getBlock());
        }
        if (out.isEmpty()) out.add(b);
        return out;
    }

    synchronized Shop atContainer(Block b) {
        for (Block part : parts(b)) {
            Shop s = byContainer.get(key(part.getLocation()));
            if (s != null) return s;
        }
        return null;
    }

    public synchronized Shop atSign(Block b) {
        return bySign.get(key(b.getLocation()));
    }

    private static Block attached(Block sign) {
        if (sign.getBlockData() instanceof WallSign ws) return sign.getRelative(ws.getFacing().getOppositeFace());
        return sign.getRelative(0, -1, 0);
    }

    private static boolean shopContainer(Block b) {
        BlockState st = b.getState(false);
        return st instanceof Chest || st instanceof Barrel;
    }

    /** Inventar des Behälters oder null, wenn er nicht mehr da ist. */
    Inventory inventory(Shop s) {
        Block b = s.container.getBlock();
        return b.getState(false) instanceof Container c ? c.getInventory() : null;
    }

    int stock(Shop s) {
        Inventory inv = inventory(s);
        if (inv == null || s.item == null) return 0;
        int n = 0;
        for (ItemStack it : inv.getContents()) if (it != null && s.item.isSimilar(it)) n += it.getAmount();
        return n;
    }

    static int space(Inventory inv, ItemStack template) {
        int free = 0;
        int max = template.getMaxStackSize();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType().isAir()) free += max;
            else if (template.isSimilar(it)) free += Math.max(0, max - it.getAmount());
        }
        return free;
    }

    static int count(Inventory inv, ItemStack template) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && template.isSimilar(it)) n += it.getAmount();
        return n;
    }

    static void take(Inventory inv, ItemStack template, int amount) {
        ItemStack[] items = inv.getStorageContents();
        for (int i = 0; i < items.length && amount > 0; i++) {
            ItemStack it = items[i];
            if (it == null || !template.isSimilar(it)) continue;
            int t = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - t);
            amount -= t;
            items[i] = it.getAmount() <= 0 ? null : it;
        }
        inv.setStorageContents(items);
    }

    static void give(Inventory inv, ItemStack template, int amount) {
        int max = template.getMaxStackSize();
        while (amount > 0) {
            ItemStack it = template.clone();
            it.setAmount(Math.min(max, amount));
            amount -= it.getAmount();
            inv.addItem(it);
        }
    }

    // ---------- Erstellen ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        Component first = e.line(0);
        if (first == null || !PlainTextComponentSerializer.plainText().serialize(first).trim().equalsIgnoreCase("[shop]")) return;
        Player p = e.getPlayer();
        Block sign = e.getBlock();
        Block box = attached(sign);
        String err = null;
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))
                || !sign.getWorld().getName().equals(core.modes().worldOf(ModeService.SURVIVAL))) {
            err = "Läden gibt es nur in der Survival-Welt.";
        } else if (!shopContainer(box)) {
            err = "Das Schild muss an einer Kiste oder einem Fass hängen (oder obendrauf stehen).";
        } else if (addon.claims().at(sign.getLocation()) == null || !addon.claims().canBuild(p, sign.getLocation())
                || !addon.claims().canBuild(p, box.getLocation())) {
            err = "Läden gehen nur auf Grundstücken, auf denen du bauen darfst.";
        } else if (atContainer(box) != null) {
            err = "An dieser Kiste hängt schon ein Laden.";
        } else if (of(p.getUniqueId()).size() >= max()) {
            err = "Du hast schon " + max() + " Läden.";
        }
        if (err != null) {
            e.line(0, Msg.parse("<red>[Shop]"));
            Msg.send(p, "<red>" + err);
            return;
        }
        Shop s = new Shop(p.getUniqueId(), sign.getLocation(), box.getLocation());
        synchronized (this) {
            bySign.put(key(s.sign), s);
            byContainer.put(key(s.container), s);
        }
        save();
        e.line(0, Msg.parse("<dark_blue><b>[Shop]"));
        e.line(1, Msg.parse("<black>" + p.getName()));
        e.line(2, Msg.parse("<gray>wird eingerichtet"));
        e.line(3, Component.empty());
        addon.getServer().getScheduler().runTask(addon, () -> {
            if (sign.getState(false) instanceof Sign st) {
                st.setWaxed(true);
                st.update();
            }
        });
        Msg.send(p, "<green>Laden erstellt! <gray>Nimm die Ware in die Hand und klick das Schild an, um ihn einzurichten.");
    }

    /** Schreibt Preise und Ware aufs Schild. */
    public void updateSign(Shop s) {
        if (!(s.sign.getBlock().getState(false) instanceof Sign st)) return;
        var side = st.getSide(Side.FRONT);
        String owner = Bukkit.getOfflinePlayer(s.owner).getName();
        side.line(0, Msg.parse("<dark_blue><b>[Shop]"));
        side.line(1, Msg.parse("<black>" + (owner == null ? "?" : owner)));
        String prices;
        if (s.item == null || (s.buyPrice <= 0 && s.sellPrice <= 0)) prices = "<gray>wird eingerichtet";
        else prices = (s.buyPrice > 0 ? "<dark_green>K " + Msg.coins(s.buyPrice) : "")
                + (s.buyPrice > 0 && s.sellPrice > 0 ? "<gray> | " : "")
                + (s.sellPrice > 0 ? "<dark_red>V " + Msg.coins(s.sellPrice) : "");
        side.line(2, Msg.parse(prices));
        String item = s.item == null ? "" : s.amount + "x " + name(s.item);
        side.line(3, Msg.parse("<black>" + Msg.escape(item.length() > 15 ? item.substring(0, 15) : item)));
        st.setWaxed(true);
        st.update();
    }

    static String name(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
        }
        String s = it.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Benutzen ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Shop s = atSign(e.getClickedBlock());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!valid(s)) {
            remove(s);
            Msg.send(p, "<red>Dieser Laden ist kaputt (Kiste fehlt) und wurde aufgelöst.");
            return;
        }
        if (s.owner.equals(p.getUniqueId())) {
            if (s.item == null && !p.getInventory().getItemInMainHand().getType().isAir()) setItem(s, p.getInventory().getItemInMainHand());
            new ShopManageMenu(addon, s).open(p);
        } else {
            if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
            if (s.item == null || (s.buyPrice <= 0 && s.sellPrice <= 0)) {
                Msg.send(p, "<gray>Dieser Laden wird noch eingerichtet.");
                return;
            }
            new ShopMenu(addon, s).open(p);
        }
    }

    boolean valid(Shop s) {
        return s.sign.getBlock().getState(false) instanceof Sign && shopContainer(s.container.getBlock());
    }

    synchronized void setItem(Shop s, ItemStack hand) {
        ItemStack t = hand.clone();
        t.setAmount(1);
        s.item = t;
        s.amount = Math.min(s.amount, t.getMaxStackSize() * 9);
        save();
        updateSign(s);
    }

    /** Kunde kauft (Kiste → Kunde). Null bei Erfolg, sonst eine Meldung. */
    public synchronized String buy(Player p, Shop s) {
        if (s.buyPrice <= 0 || s.item == null) return "Hier wird nichts verkauft.";
        Inventory box = inventory(s);
        if (box == null) return "Der Laden ist gerade geschlossen.";
        if (count(box, s.item) < s.amount) return "Ausverkauft.";
        if (space(p.getInventory(), s.item) < s.amount) return "Dein Inventar ist zu voll.";
        if (!core.economy().withdraw(p.getUniqueId(), s.buyPrice)) return "Dafür brauchst du " + Msg.coins(s.buyPrice) + " Coins.";
        take(box, s.item, s.amount);
        give(p.getInventory(), s.item, s.amount);
        s.cash += s.buyPrice;
        save();
        notifyOwner(s, p.getName() + " hat " + s.amount + "x " + name(s.item) + " gekauft (+" + Msg.coins(s.buyPrice) + " in der Kasse)");
        return null;
    }

    /** Kunde verkauft (Kunde → Kiste). */
    public synchronized String sell(Player p, Shop s) {
        if (s.sellPrice <= 0 || s.item == null) return "Hier wird nichts angekauft.";
        Inventory box = inventory(s);
        if (box == null) return "Der Laden ist gerade geschlossen.";
        if (count(p.getInventory(), s.item) < s.amount) return "Du hast nicht genug davon.";
        if (space(box, s.item) < s.amount) return "Die Kiste des Ladens ist voll.";
        if (s.cash < s.sellPrice) return "In der Ladenkasse ist nicht genug Geld.";
        take(p.getInventory(), s.item, s.amount);
        give(box, s.item, s.amount);
        s.cash -= s.sellPrice;
        core.economy().deposit(p.getUniqueId(), s.sellPrice);
        save();
        notifyOwner(s, p.getName() + " hat " + s.amount + "x " + name(s.item) + " verkauft (-" + Msg.coins(s.sellPrice) + " aus der Kasse)");
        return null;
    }

    private void notifyOwner(Shop s, String text) {
        Player o = Bukkit.getPlayer(s.owner);
        if (o != null && ModeService.SURVIVAL.equals(core.modes().modeOf(o.getUniqueId()))) o.sendActionBar(Msg.parse("<gold>Laden: <white>" + Msg.escape(text)));
    }

    /** Kasse ganz abheben (Besitzer ist online in Survival). */
    public synchronized long withdrawCash(Player p, Shop s) {
        long c = s.cash;
        if (c <= 0) return 0;
        s.cash = 0;
        core.economy().deposit(p.getUniqueId(), c);
        save();
        return c;
    }

    public synchronized boolean depositCash(Player p, Shop s, long amount) {
        if (amount <= 0 || !core.economy().withdraw(p.getUniqueId(), amount)) return false;
        s.cash += amount;
        save();
        return true;
    }

    /** Laden auflösen: Kasse geht an den Besitzer (im Survival-Konto), das Schild wird leer. */
    public synchronized void remove(Shop s) {
        bySign.remove(key(s.sign));
        byContainer.remove(key(s.container));
        if (s.cash > 0) core.modes().depositIn(s.owner, ModeService.SURVIVAL, s.cash);
        s.cash = 0;
        if (s.sign.getBlock().getState(false) instanceof Sign st) {
            var side = st.getSide(Side.FRONT);
            side.line(0, Msg.parse("<gray>[Geschlossen]"));
            for (int i = 1; i < 4; i++) side.line(i, Component.empty());
            st.setWaxed(false);
            st.update();
        }
        save();
    }

    // ---------- Schutz ----------

    private boolean mayManage(Player p, Shop s) {
        return s.owner.equals(p.getUniqueId()) || p.hasPermission("kuby.admin");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Shop s = atSign(b);
        if (s == null && shopContainer(b)) s = atContainer(b);
        if (s == null) return;
        Player p = e.getPlayer();
        if (!mayManage(p, s)) {
            e.setCancelled(true);
            p.sendActionBar(Msg.parse("<red>Das ist ein Laden von jemand anderem."));
            return;
        }
        remove(s);
        Msg.send(p, "<gray>Laden aufgelöst." + (p.getUniqueId().equals(s.owner) ? " Die Kasse ist auf deinem Konto." : ""));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Block b = block(e.getInventory().getHolder(false));
        if (b == null) return;
        Shop s = atContainer(b);
        if (s != null && !mayManage(p, s)) {
            e.setCancelled(true);
            p.sendActionBar(Msg.parse("<red>Das ist das Lager eines Ladens. Kaufen und verkaufen über das Schild."));
        }
    }

    /** Trichter dürfen nichts aus Läden ziehen (hinein schon, zum Auffüllen). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        Block b = block(e.getSource().getHolder(false));
        if (b != null && atContainer(b) != null) e.setCancelled(true);
    }

    private static Block block(InventoryHolder h) {
        if (h instanceof DoubleChest dc && dc.getLeftSide() instanceof Chest c) return c.getBlock();
        if (h instanceof Container c) return c.getBlock();
        return null;
    }

    // ---------- Speichern ----------

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("shops");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            try {
                Shop shop = new Shop(UUID.fromString(s.getString("owner", "")),
                        new Location(w, s.getInt("sx"), s.getInt("sy"), s.getInt("sz")),
                        new Location(w, s.getInt("cx"), s.getInt("cy"), s.getInt("cz")));
                shop.item = s.getItemStack("item");
                shop.amount = Math.max(1, s.getInt("amount", 1));
                shop.buyPrice = s.getLong("buy");
                shop.sellPrice = s.getLong("sell");
                shop.cash = s.getLong("cash");
                bySign.put(key(shop.sign), shop);
                byContainer.put(key(shop.container), shop);
            } catch (IllegalArgumentException e) {
                addon.getLogger().warning("chestshops.yml: Laden " + k + " ungültig.");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        int i = 0;
        for (Shop s : bySign.values()) {
            String b = "shops." + i++ + ".";
            out.set(b + "owner", s.owner.toString());
            out.set(b + "world", s.sign.getWorld().getName());
            out.set(b + "sx", s.sign.getBlockX());
            out.set(b + "sy", s.sign.getBlockY());
            out.set(b + "sz", s.sign.getBlockZ());
            out.set(b + "cx", s.container.getBlockX());
            out.set(b + "cy", s.container.getBlockY());
            out.set(b + "cz", s.container.getBlockZ());
            out.set(b + "item", s.item);
            out.set(b + "amount", s.amount);
            out.set(b + "buy", s.buyPrice);
            out.set(b + "sell", s.sellPrice);
            out.set(b + "cash", s.cash);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("chestshops.yml: " + ex.getMessage());
        }
    }
}
