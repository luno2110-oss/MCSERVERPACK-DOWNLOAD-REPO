package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VIP-Lounge im Casino: eine Region (Standard-ID "casino-vip"). Rein darf, wer einen der Rang-Rechte hat
 * (Standard: LuckPerms-Gruppe legend) oder mit Plus unter den Top 10 der Rangliste "Am meisten Plus" steht.
 * Barkeeper (Dorfbewohner ohne KI) geben einmal am Tag Chips aus.
 */
public final class CasinoVip {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String SURVIVAL = "survival";

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<UUID, String> lastClaim = new HashMap<>();

    public CasinoVip(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-vip.yml");
        this.tag = new NamespacedKey(plugin, "kp_barkeeper");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("claimed");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    lastClaim.put(UUID.fromString(key), s.getString(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** Darf der Spieler in die Lounge? */
    public boolean allowed(Player p) {
        if (p.hasPermission("kuby.admin")) return true;
        List<String> perms = plugin.getConfig().getStringList("casino.vip.rank-permissions");
        if (perms.isEmpty()) perms = List.of("group.legend", "kuby.rank.legend");
        for (String perm : perms) {
            if (p.hasPermission(perm)) return true;
        }
        int top = plugin.getConfig().getInt("casino.vip.top", 10);
        int rank = plugin.casinoLog().rankOf(p.getUniqueId());
        return rank > 0 && rank <= top && plugin.casinoLog().netOf(p.getUniqueId()) > 0;
    }

    public boolean inLounge(Location l) {
        RegionService.Region r = plugin.regions().find(plugin.getConfig().getString("casino.vip.region", "casino-vip"));
        return r != null && r.contains(l);
    }

    public String denyMessage() {
        return "Die VIP-Lounge ist nur für Legend-Ränge und die Top " + plugin.getConfig().getInt("casino.vip.top", 10)
                + " der Casino-Rangliste.";
    }

    /** Stellt einen Barkeeper an die Position des Admins. */
    public void spawnBarkeeper(Player admin) {
        Location at = admin.getLocation();
        at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.customName(Msg.parse("<gold><b>Barkeeper</b>"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Entfernt den nächsten Barkeeper im Umkreis von 5 Blöcken. False, wenn keiner da ist. */
    public boolean removeBarkeeper(Player admin) {
        Villager nearest = null;
        double best = Double.MAX_VALUE;
        for (Villager v : admin.getWorld().getNearbyEntitiesByType(Villager.class, admin.getLocation(), 5)) {
            if (!isBarkeeper(v)) continue;
            double d = v.getLocation().distanceSquared(admin.getLocation());
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        if (nearest == null) return false;
        nearest.remove();
        return true;
    }

    public boolean isBarkeeper(Entity e) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    /** Barkeeper angeklickt: einmal am Tag Chips für VIPs. */
    public synchronized void serve(Player p) {
        if (!SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Der Barkeeper bedient nur in Survival.");
            return;
        }
        if (!allowed(p)) {
            Msg.send(p, "<red>" + denyMessage());
            return;
        }
        String today = LocalDate.now(ZONE).toString();
        if (today.equals(lastClaim.get(p.getUniqueId()))) {
            Msg.send(p, "<gold>Barkeeper: <white>Für heute hast du schon deine Chips. Komm morgen wieder!");
            return;
        }
        long amount = plugin.getConfig().getLong("casino.vip.daily-chips", 500);
        lastClaim.put(p.getUniqueId(), today);
        plugin.casino().giveChips(p.getUniqueId(), amount);
        save();
        Sfx.play(plugin, p, Sfx.Kind.COIN);
        Msg.send(p, "<gold>Barkeeper: <white>Hier, geht aufs Haus! <green>+" + amount + " Chips"
                + "  <gray>Chips: " + plugin.casino().chips(p.getUniqueId()));
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : lastClaim.entrySet()) out.set("claimed." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-vip.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
