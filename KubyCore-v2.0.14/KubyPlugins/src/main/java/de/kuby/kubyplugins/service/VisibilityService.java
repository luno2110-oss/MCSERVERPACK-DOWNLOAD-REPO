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

    private final java.io.File file;

    public VisibilityService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new java.io.File(plugin.getDataFolder(), "sichtbarkeit.yml");
        // gewählte Stufe bleibt über Logout und Neustart erhalten
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                modes.put(UUID.fromString(k), Mode.valueOf(y.getString(k, "ALL")));
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void save() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        modes.forEach((id, m) -> {
            if (m != Mode.ALL) y.set(id.toString(), m.name());
        });
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("sichtbarkeit.yml: " + e.getMessage());
        }
    }

    public Mode modeOf(UUID id) {
        return modes.getOrDefault(id, Mode.ALL);
    }

    public ItemStack dyeItem(UUID id) {
        Mode m = modeOf(id);
        return Items.make(m.dye, m.label, List.of(
                "<gray>Links- oder Rechtsklick: Sichtbarkeit wechseln",
                "<dark_gray>Alle · Nur Freunde · Nur Team · Niemand"));
    }

    public void cycle(Player p) {
        Mode current = modeOf(p.getUniqueId());
        Mode[] all = Mode.values();
        Mode next = all[(current.ordinal() + 1) % all.length];
        modes.put(p.getUniqueId(), next);
        save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, next.pitch);
        plugin.modes().refreshLobby(p);
        tick();
        // wie viele andere sieht man jetzt? (sofortige Rückmeldung, ob es gewirkt hat)
        int seen = 0, total = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p) || !plugin.modes().isHub(o.getWorld())) continue;
            total++;
            if (visible(p, o)) seen++;
        }
        p.sendActionBar(Msg.parse(next.label + " <dark_gray>· <gray>" + seen + " von " + total + " Spielern in der Lobby"));
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

    /** Beim Ausloggen: Sichtbarkeit zurücksetzen (die gewählte Stufe bleibt gespeichert). */
    public void forget(Player p) {
        clear(p);
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
            // Freunde und Teams gibt es nur in den Spielmodi: in der Lobby zählen sie aus jedem Modus (früher wurde in
            // der Lobby nach Lobby-Freunden gesucht, die es nicht gibt, „Nur Freunde“ und „Nur Team“ zeigten niemanden)
            case FRIENDS:
                return plugin.friends().areFriendsAnyMode(viewer.getUniqueId(), other.getUniqueId());
            default:
                return plugin.teams().sameTeamAnyMode(viewer.getUniqueId(), other.getUniqueId());
        }
    }
}
