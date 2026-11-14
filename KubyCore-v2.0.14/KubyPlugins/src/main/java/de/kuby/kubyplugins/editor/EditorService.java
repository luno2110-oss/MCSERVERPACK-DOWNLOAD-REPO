package de.kuby.kubyplugins.editor;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Admin-Editor (/kp editor): Kategorien (Kisten, Modelle, Welt &amp; NPCs, Skyblock …) mit Werkzeugen. Ein Werkzeug gibt
 * entweder ein platzierbares Item (Kiste, Modell: Rechtsklick auf einen Block setzt es) oder führt eine Aktion aus.
 * Kern und Addons tragen ihre Kategorien mit {@link #registerCategory} und ihre Platzier-Arten mit
 * {@link #registerPlacer} ein.
 */
public final class EditorService implements Listener {

    /** Ein Eintrag in einer Kategorie. {@code confirm}: erst nach einem zweiten Klick ausführen (große Eingriffe). */
    public record Tool(String id, String name, String desc, Material icon, boolean confirm, Consumer<Player> action) {
        public static Tool of(String id, String name, String desc, Material icon, Consumer<Player> action) {
            return new Tool(id, name, desc, icon, false, action);
        }
    }

    public record Category(String id, String name, String desc, Material icon, Supplier<List<Tool>> tools) {}

    /** Platziert ein Editor-Item auf dem Block {@code at} (Luft oder ersetzbar). Rückgabe: Fehlertext oder null. */
    public interface Placer {
        String place(Player p, Block at, String value);
    }

    private final KubyPlugins plugin;
    private final NamespacedKey placeKey;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Placer> placers = new LinkedHashMap<>();

    public EditorService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.placeKey = new NamespacedKey(plugin, "editor_place");
    }

    public void registerCategory(Category c) {
        categories.put(c.id(), c);
    }

    public List<Category> categories() {
        return new ArrayList<>(categories.values());
    }

    public Category category(String id) {
        return categories.get(id);
    }

    public void registerPlacer(String kind, Placer placer) {
        placers.put(kind, placer);
    }

    /** Platzierbares Editor-Item: {@code kind:value}, z. B. {@code crate:survival:episch}. Wird beim Setzen nicht verbraucht. */
    public ItemStack placeable(String kind, String value, ItemStack base, String name, List<String> lore) {
        ItemStack it = base.clone();
        it.setAmount(1);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.item(s));
        l.add(Msg.item("<dark_gray>Rechtsklick auf einen Block: setzen"));
        m.lore(l);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(placeKey, PersistentDataType.STRING, kind + ":" + value);
        it.setItemMeta(m);
        return it;
    }

    /** Platzier-Wert eines Editor-Items ({@code kind:value}) oder null. */
    public String placeValue(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(placeKey, PersistentDataType.STRING);
    }

    public boolean isPlaceable(ItemStack it) {
        return placeValue(it) != null;
    }

    /** Gibt dem Spieler ein Editor-Item (in die Hand, wenn frei). */
    public void give(Player p, ItemStack it) {
        if (p.getInventory().getItemInMainHand().getType().isAir()) p.getInventory().setItemInMainHand(it);
        else p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String v = placeValue(e.getItem());
        if (v == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können Editor-Items setzen.");
            return;
        }
        int cut = v.indexOf(':');
        Placer placer = cut < 0 ? null : placers.get(v.substring(0, cut));
        if (placer == null) {
            Msg.send(p, "<red>Dieses Editor-Item kennt der Server nicht (mehr).");
            return;
        }
        Block clicked = e.getClickedBlock();
        Block at = clicked.isReplaceable() ? clicked : clicked.getRelative(e.getBlockFace());
        if (!at.isReplaceable()) {
            Msg.send(p, "<red>Dort ist kein Platz.");
            return;
        }
        String err = placer.place(p, at, v.substring(cut + 1));
        if (err != null) {
            Msg.send(p, "<red>" + err);
            Sfx.play(plugin, p, Sfx.Kind.FAIL);
        } else {
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        }
    }
}
