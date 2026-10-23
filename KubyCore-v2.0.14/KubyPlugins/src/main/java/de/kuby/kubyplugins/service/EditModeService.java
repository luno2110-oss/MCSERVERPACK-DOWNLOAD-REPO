package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bearbeitungsmodus in der Hub-Welt. Blaze Rod: Aktion wählen, Spawn, Region oder Marker setzen.
 * Redstone Torch: Markerfarbe wechseln. Barrier: Modus verlassen.
 */
public final class EditModeService {

    private final KubyPlugins plugin;
    private final Set<UUID> editing = new HashSet<>();
    private final Map<UUID, String> action = new HashMap<>();
    private final Map<UUID, Integer> color = new HashMap<>();
    private final Map<UUID, Location[]> corners = new HashMap<>();

    public EditModeService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public boolean isEditing(UUID id) {
        return editing.contains(id);
    }

    /** Gibt null bei Erfolg zurück. */
    public String enter(Player p) {
        if (!plugin.modes().isHub(p.getWorld())) return "Der Bearbeitungsmodus ist nur in der Hub-Welt verfügbar.";
        if (editing.contains(p.getUniqueId())) return null;
        editing.add(p.getUniqueId());
        action.putIfAbsent(p.getUniqueId(), "spawn");
        p.getInventory().clear();
        p.getInventory().setItem(0, item(Material.BLAZE_ROD, "<gold>Blaze Rod",
                "<gray>Rechtsklick auf Block: Aktion ausführen", "<gray>Shift + Rechtsklick: Aktion wählen"));
        p.getInventory().setItem(1, item(Material.REDSTONE_TORCH, "<red>Marker-Farbe",
                "<gray>Shift + Linksklick: Farbe wechseln"));
        p.getInventory().setItem(8, item(Material.BARRIER, "<red>Bearbeitung beenden",
                "<gray>Klick: zurück zur Lobby"));
        Sfx.play(plugin, p, Sfx.Kind.OPEN);
        Msg.send(p, "<light_purple>Bearbeitungsmodus aktiv. Aktion: <white>" + actionLabel(p.getUniqueId()));
        return null;
    }

    public void exit(Player p) {
        if (!editing.remove(p.getUniqueId())) return;
        plugin.modes().lobbyItems(p);
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        Msg.send(p, "<gray>Bearbeitungsmodus beendet.");
    }

    /** Ohne Inventar-Änderung, z. B. beim Verlassen der Welt. */
    public void forget(Player p) {
        editing.remove(p.getUniqueId());
    }

    public String action(UUID id) {
        return action.getOrDefault(id, "spawn");
    }

    public void setAction(Player p, String value) {
        action.put(p.getUniqueId(), value);
        Msg.send(p, "<gray>Aktion: <white>" + actionLabel(p.getUniqueId()));
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
    }

    public String actionLabel(UUID id) {
        return switch (action(id)) {
            case "region" -> "Region (Ecken setzen)";
            case "marker" -> "Marker";
            default -> "Spawn";
        };
    }

    public String color(UUID id) {
        return MarkerService.COLORS.get(color.getOrDefault(id, 0));
    }

    public void cycleColor(Player p) {
        int next = (color.getOrDefault(p.getUniqueId(), 0) + 1) % MarkerService.COLORS.size();
        color.put(p.getUniqueId(), next);
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        p.sendActionBar(Msg.parse("<gray>Marker-Farbe: <" + color(p.getUniqueId()) + ">" + color(p.getUniqueId())));
    }

    public void setCorner(Player p, int which, Location loc) {
        Location[] c = corners.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
        c[which - 1] = loc;
        Sfx.play(plugin, p, Sfx.Kind.PING);
        Msg.send(p, "<gray>Ecke " + which + ": <white>" + loc.getBlockX() + " / " + loc.getBlockY() + " / " + loc.getBlockZ());
    }

    public Location[] corners(UUID id) {
        return corners.get(id);
    }

    private static ItemStack item(Material m, String name, String... lore) {
        return de.kuby.kubyplugins.util.Items.make(m, name, List.of(lore));
    }
}
