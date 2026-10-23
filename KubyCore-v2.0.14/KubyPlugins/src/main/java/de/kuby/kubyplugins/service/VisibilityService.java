package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sichtbarkeit der Spieler in der Lobby, umgeschaltet über einen Farbstoff. */
public final class VisibilityService {

    public enum Mode {
        ALL(Material.LIME_DYE, "<green>Alle Spieler sichtbar", 1.4f),
        FRIENDS(Material.PINK_DYE, "<light_purple>Nur Freunde sichtbar", 1.1f),
        TEAM(Material.ORANGE_DYE, "<gold>Nur Team sichtbar", 0.8f),
        NONE(Material.GRAY_DYE, "<gray>Keine Spieler sichtbar", 0.5f);

        public final Material dye;
        public final String label;
        public final float pitch;

        Mode(Material dye, String label, float pitch) {
            this.dye = dye;
            this.label = label;
            this.pitch = pitch;
        }
    }

    private final KubyPlugins plugin;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();

    public VisibilityService(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public Mode modeOf(UUID id) {
        return modes.getOrDefault(id, Mode.ALL);
    }

    public ItemStack dyeItem(UUID id) {
        Mode m = modeOf(id);
        return Items.make(m.dye, m.label, List.of(
                "<gray>Klick: Sichtbarkeit wechseln",
                "<dark_gray>Grün, Pink, Orange, Grau"));
    }

    public void cycle(Player p) {
        Mode current = modeOf(p.getUniqueId());
        Mode[] all = Mode.values();
        Mode next = all[(current.ordinal() + 1) % all.length];
        modes.put(p.getUniqueId(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, next.pitch);
        p.sendActionBar(Msg.parse(next.label));
        plugin.modes().refreshLobby(p);
        tick();
    }

    /** Macht einen Spieler für alle wieder sichtbar, z. B. beim Verlassen der Lobby. */
    public void clear(Player p) {
        UUID id = p.getUniqueId();
        hidden.remove(id);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            other.showPlayer(plugin, p);
            p.showPlayer(plugin, other);
            Set<UUID> h = hidden.get(other.getUniqueId());
            if (h != null) h.remove(id);
        }
    }

    public void forget(Player p) {
        clear(p);
        modes.remove(p.getUniqueId());
    }

    private void tick() {
        List<Player> lobby = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.modes().isHub(p.getWorld())) lobby.add(p);
        }
        for (Player viewer : lobby) {
            Set<UUID> h = hidden.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
            for (Player other : lobby) {
                if (other.equals(viewer)) continue;
                boolean visible = visible(viewer, other);
                boolean isHidden = h.contains(other.getUniqueId());
                if (!visible && !isHidden) {
                    viewer.hidePlayer(plugin, other);
                    h.add(other.getUniqueId());
                } else if (visible && isHidden) {
                    viewer.showPlayer(plugin, other);
                    h.remove(other.getUniqueId());
                }
            }
        }
    }

    private boolean visible(Player viewer, Player other) {
        switch (modeOf(viewer.getUniqueId())) {
            case ALL:
                return true;
            case NONE:
                return false;
            case FRIENDS:
                return plugin.friends().areFriends(viewer.getUniqueId(), other.getUniqueId());
            default:
                var a = plugin.teams().of(viewer.getUniqueId());
                var b = plugin.teams().of(other.getUniqueId());
                return a != null && b != null && a.name().equals(b.name());
        }
    }
}
