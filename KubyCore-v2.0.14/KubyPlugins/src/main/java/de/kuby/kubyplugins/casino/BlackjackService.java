package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alle Blackjack-Tische: Aufbau, Klicks (Hocker, Chips, Aktionsfelder, Dealer), Aufstehen, Sicherung offener
 * Einsätze. Admin: /casino blackjack tisch [Radius] (an deiner Position, Blick zu den Spielern), entfernen, liste.
 */
public final class BlackjackService implements Placeable, Listener {

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Map<String, BlackjackGame> games = new LinkedHashMap<>();
    private final Map<String, String> raw = new LinkedHashMap<>();
    private BukkitTask timer;

    /** Alle geladenen Tische (für den Rundgang). */
    public java.util.Collection<BlackjackGame> games() {
        return games.values();
    }

    public BlackjackService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blackjack-tische.yml");
        this.tag = new NamespacedKey(plugin, "bj_table");
        this.click = new NamespacedKey(plugin, "bj_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection t = yml.getConfigurationSection("tische");
        if (t != null) for (String id : t.getKeys(false)) raw.put(id, t.getString(id));
        // offene Einsätze nach einem Absturz zurückgeben
        List<String> open = yml.getStringList("offen");
        for (String line : open) {
            String[] p = line.split(";");
            try {
                plugin.casinoPlay().refund(UUID.fromString(p[0]), "blackjack", Long.parseLong(p[1]));
            } catch (RuntimeException ignored) {
            }
        }
        if (!open.isEmpty()) {
            yml.set("offen", null);
            try {
                yml.save(file);
            } catch (IOException ignored) {
            }
        }
    }

    private BlackjackGame load(String id) {
        BlackjackGame g = games.get(id);
        if (g != null) return g;
        String line = raw.get(id);
        if (line == null) return null;
        String[] p = line.split(";");
        World w = p.length >= 6 ? Bukkit.getWorld(p[0]) : null;
        if (w == null) return null;
        try {
            Location o = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            TableTier tier = p.length >= 7 && TableTier.parse(p[6]) != null ? TableTier.parse(p[6]) : TableTier.GRUEN;
            BlackjackTable table = new BlackjackTable(plugin, id, o, Float.parseFloat(p[4]), Double.parseDouble(p[5]), tag, click)
                    .tier(tier);
            g = new BlackjackGame(plugin, table, tier.tableName + " "
                    + (new ArrayList<>(raw.keySet()).indexOf(id) + 1));
            games.put(id, g);
            return g;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Baut alle Tische auf, deren Chunk geladen ist, und startet den Takt. */
    public void respawn() {
        if (timer == null) {
            timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> games.values().forEach(BlackjackGame::tick), 20L, 20L);
            plugin.getServer().getScheduler().runTaskTimer(plugin, () -> games.values().forEach(BlackjackGame::hoverTick), 22L, 2L);
        }
        for (String id : raw.keySet()) {
            BlackjackGame g = load(id);
            if (g == null) continue;
            Location o = g.table.origin();
            if (o.getWorld().isChunkLoaded(o.getBlockX() >> 4, o.getBlockZ() >> 4) && !g.table.ready()) {
                g.table.build();
                g.refreshDisplays();
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (String id : raw.keySet()) {
            BlackjackGame g = load(id);
            if (g == null) continue;
            Location o = g.table.origin();
            if (!e.getWorld().equals(o.getWorld())) continue;
            if (Math.abs((o.getBlockX() >> 4) - e.getChunk().getX()) <= 1 && Math.abs((o.getBlockZ() >> 4) - e.getChunk().getZ()) <= 1) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!g.table.ready() && g.idle()) {
                        g.table.build();
                        g.refreshDisplays();
                    }
                }, 5L);
            }
        }
    }

    public void shutdown() {
        for (BlackjackGame g : games.values()) g.shutdown();
        saveEscrow();
    }

    public void saveEscrow() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : raw.entrySet()) yml.set("tische." + e.getKey(), e.getValue());
        List<String> open = new ArrayList<>();
        for (BlackjackGame g : games.values()) open.addAll(g.escrow());
        yml.set("offen", open);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("blackjack-tische.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private BlackjackGame seated(UUID id) {
        for (BlackjackGame g : games.values()) if (g.seatOf(id) >= 0) return g;
        return null;
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    private void handle(Player p, Entity e) {
        String v = e.getPersistentDataContainer().get(click, PersistentDataType.STRING);
        if (v == null) return;
        String[] parts = v.split(":");
        BlackjackGame g = load(parts[0]);
        if (g == null) return;
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        int seat = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        switch (parts[1]) {
            case "seat" -> {
                BlackjackGame other = seated(p.getUniqueId());
                if (other != null) {
                    if (other == g && other.seatOf(p.getUniqueId()) == seat) return;
                    CasinoText.error(p, "Steh erst auf (Schleichen).");
                    return;
                }
                g.sit(p, seat);
            }
            case "chip" -> g.chip(p, seat, Integer.parseInt(parts[3]));
            case "act" -> g.action(p, seat, Integer.parseInt(parts[3]));
            case "ctl" -> g.control(p, seat, parts[3]);
            case "dealer" -> {
                if (seated(p.getUniqueId()) != null) {
                    CasinoText.send(p, "Leg Chips aus dem Rack vor dir auf dein Feld. Aufstehen mit Schleichen.");
                    return;
                }
                int free = g.freeSeatNear(p.getLocation());
                if (free < 0) CasinoText.error(p, "Alle Plätze sind besetzt.");
                else g.sit(p, free);
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        handle(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!e.getAttacked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        handle(e.getPlayer(), e.getAttacked());
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String v = e.getDismounted().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (v == null || !v.endsWith(":vehicle")) return;
        // hinter dem Hocker auf den Boden stellen (nicht im Hocker oder Tisch stehen bleiben)
        Location stand = e.getDismounted().getLocation();
        Location floor = stand.clone().add(0, -0.58, 0);
        de.kuby.kubyplugins.util.SafeStand.later(plugin, p, floor,
                de.kuby.kubyplugins.util.SafeStand.facing(stand.getYaw()).multiply(-0.7));
        BlackjackGame g = seated(p.getUniqueId());
        if (g != null) g.leave(p.getUniqueId(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        leave(e.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        BlackjackGame g = seated(e.getPlayer().getUniqueId());
        if (g == null || e.getTo().getWorld() == null) return;
        if (!e.getTo().getWorld().equals(g.table.origin().getWorld()) || e.getTo().distanceSquared(g.table.origin()) > 64) {
            g.leave(e.getPlayer().getUniqueId(), true);
        }
    }

    public void leave(UUID id) {
        BlackjackGame g = seated(id);
        if (g != null) g.leave(id, false);
    }

    // =================================================================================================================
    // Alte Befehle und Admin
    // =================================================================================================================

    /** /casino hit|stand: Aktion am eigenen Platz. */
    public String action(Player p, int action) {
        BlackjackGame g = seated(p.getUniqueId());
        if (g == null) return "Du sitzt an keinem Blackjack-Tisch.";
        g.action(p, g.seatOf(p.getUniqueId()), action);
        return null;
    }

    /** Sitzt der Spieler an einem Tisch? (Moduswechsel ist dann gesperrt.) */
    public boolean inGame(UUID id) {
        return seated(id) != null;
    }

    public boolean hasTable() {
        return !raw.isEmpty();
    }

    /** Spieltest: Tisch "test" an dieser Stelle (einmal anlegen) und das Spiel dazu. */
    public BlackjackGame testTable(Location origin, float yaw) {
        return testTable("test", origin, yaw, TableTier.GRUEN);
    }

    /** Test-Tisch (Belastungstest, Client-Bilder) in einer Farbe aufbauen. */
    public BlackjackGame testTable(String id, Location origin, float yaw, TableTier tier) {
        if (!raw.containsKey(id)) {
            raw.put(id, origin.getWorld().getName() + ";" + origin.getX() + ";" + origin.getY() + ";" + origin.getZ() + ";" + yaw + ";3.0"
                    + (tier == TableTier.GRUEN ? "" : ";" + tier.key));
            saveEscrow();
        }
        BlackjackGame g = load(id);
        if (g != null && !g.table.ready()) {
            g.table.build();
            g.refreshDisplays();
        }
        if (timer == null) {
            timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> games.values().forEach(BlackjackGame::tick), 20L, 20L);
            plugin.getServer().getScheduler().runTaskTimer(plugin, () -> games.values().forEach(BlackjackGame::hoverTick), 22L, 2L);
        }
        return g;
    }

    // =================================================================================================================
    // Casino-Admin (v2.07): auflisten, verschieben, drehen, Größe, Farbe, entfernen
    // =================================================================================================================

    @Override
    public List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        for (String id : raw.keySet()) {
            BlackjackGame g = load(id);
            if (g == null) continue;
            out.add(new Piece(id, g.name, g.table.origin(), g.table.yaw(), g.table.radius(), g.idle() ? "frei" : "Runde läuft", !g.idle()));
        }
        return out;
    }

    /** Tisch neu aufbauen, nachdem raw geändert wurde. */
    private String rebuild(String id, String line) {
        BlackjackGame g = load(id);
        if (g == null) return "Tisch nicht gefunden.";
        if (!g.idle()) return "Am Tisch läuft eine Runde – nach der Runde noch einmal.";
        g.shutdown();
        games.remove(id);
        raw.put(id, line);
        saveEscrow();
        BlackjackGame n = load(id);
        if (n == null) return "Tisch ließ sich nicht neu laden.";
        n.table.build();
        n.refreshDisplays();
        return null;
    }

    @Override
    public String move(String id, double dx, double dy, double dz, float dyaw) {
        if (!raw.containsKey(id)) return "Tisch nicht gefunden.";
        return rebuild(id, Placeable.shift(raw.get(id), dx, dy, dz, dyaw));
    }

    @Override
    public String resize(String id, double delta) {
        if (!raw.containsKey(id)) return "Tisch nicht gefunden.";
        double r = Math.max(2.2, Math.min(4.5, Placeable.fieldValue(raw.get(id), 5, 3.0) + delta));
        return rebuild(id, Placeable.field(raw.get(id), 5, r));
    }

    @Override
    public double sizeStep() {
        return 0.25;
    }

    /** Farbe eines Tisches wechseln (grün, rot, violett): Filz, Limits und Chips der Farbe. */
    public String tier(String id, TableTier tier) {
        String line = raw.get(id);
        if (line == null) return "Tisch nicht gefunden.";
        String[] p = line.split(";", -1);
        List<String> f = new ArrayList<>(java.util.Arrays.asList(p));
        while (f.size() < 6) f.add("3.0");
        if (tier == TableTier.GRUEN) {
            while (f.size() > 6) f.remove(f.size() - 1);
        } else if (f.size() > 6) {
            f.set(6, tier.key);
        } else {
            f.add(tier.key);
        }
        return rebuild(id, String.join(";", f));
    }

    public TableTier tierOf(String id) {
        BlackjackGame g = load(id);
        return g == null ? TableTier.GRUEN : g.table.tier();
    }

    @Override
    public String remove(String id) {
        if (!raw.containsKey(id)) return "Tisch nicht gefunden.";
        BlackjackGame g = games.remove(id);
        if (g != null) g.shutdown();
        raw.remove(id);
        saveEscrow();
        return null;
    }

    /** /casino blackjack tisch [Radius] | entfernen | liste */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "tisch" -> {
                double radius = 3.0;
                TableTier tier = TableTier.GRUEN;
                for (int i = 2; i < args.length; i++) {
                    String a = args[i].toLowerCase();
                    if (TableTier.parse(a) != null) {
                        tier = TableTier.parse(a);
                        continue;
                    }
                    try {
                        radius = Math.max(2.2, Math.min(4.5, Double.parseDouble(a.replace(',', '.'))));
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Nutzung: /casino blackjack tisch [Radius 2,2–4,5] [gruen|rot|violett]");
                        return;
                    }
                }
                Location l = p.getLocation();
                float yaw = Math.round(l.getYaw() / 45f) * 45f;
                Location o = new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5);
                String id = "t" + System.currentTimeMillis() % 100000;
                raw.put(id, o.getWorld().getName() + ";" + o.getX() + ";" + o.getY() + ";" + o.getZ() + ";" + yaw + ";" + radius
                        + (tier == TableTier.GRUEN ? "" : ";" + tier.key));
                saveEscrow();
                BlackjackGame g = load(id);
                if (g != null) {
                    g.table.build();
                    g.refreshDisplays();
                }
                Msg.send(p, "<green>" + (g == null ? "Tisch" : g.name) + " aufgebaut. Du standest an der geraden Kante,"
                        + " die Spieler sitzen in deiner Blickrichtung. Entfernen: /casino blackjack entfernen");
            }
            case "entfernen" -> {
                String best = null;
                double bd = 100;
                for (String id : raw.keySet()) {
                    BlackjackGame g = load(id);
                    if (g == null || !g.table.origin().getWorld().equals(p.getWorld())) continue;
                    double d = g.table.origin().distanceSquared(p.getLocation());
                    if (d < bd) {
                        bd = d;
                        best = id;
                    }
                }
                if (best == null) {
                    Msg.send(p, "<red>Kein Blackjack-Tisch im Umkreis von 10 Blöcken.");
                    return;
                }
                BlackjackGame g = games.remove(best);
                if (g != null) g.shutdown();
                raw.remove(best);
                saveEscrow();
                Msg.send(p, "<green>Blackjack-Tisch entfernt.");
            }
            default -> {
                if (raw.isEmpty()) Msg.send(p, "<gray>Noch keine Blackjack-Tische. Aufbauen: /casino blackjack tisch [Radius]");
                int i = 1;
                for (String id : raw.keySet()) {
                    String[] q = raw.get(id).split(";");
                    Msg.send(p, "<gold>▪ <white>Blackjack-Tisch " + i++ + " <gray>(" + q[0] + " " + Math.round(Double.parseDouble(q[1]))
                            + ", " + Math.round(Double.parseDouble(q[2])) + ", " + Math.round(Double.parseDouble(q[3])) + ")");
                }
            }
        }
    }
}
