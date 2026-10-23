package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raid-Versicherung für Kisten. Der Besitzer zahlt eine Prämie (Anteil am Wert des Inhalts) für eine begrenzte Laufzeit.
 * Wird die Kiste geraidet oder von einer Explosion zerstört, bekommt er eine Auszahlung (Anteil am versicherten Wert).
 * Versicherte Kisten werden mit einem Schutzrahmen aus Partikeln markiert.
 */
public final class RaidInsuranceService {

    public record Policy(UUID owner, long expires, long value, String world, int x, int y, int z) {}

    private static final long DAY_MS = 86_400_000L;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<String, Policy> policies = new HashMap<>();

    public RaidInsuranceService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "insurance.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            try {
                Policy pol = new Policy(UUID.fromString(yml.getString(k + ".owner")),
                        yml.getLong(k + ".expires"), yml.getLong(k + ".value"),
                        yml.getString(k + ".world"), yml.getInt(k + ".x"), yml.getInt(k + ".y"), yml.getInt(k + ".z"));
                policies.put(key(pol.world(), pol.x(), pol.y(), pol.z()), pol);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Versicherung " + k + " übersprungen: " + e.getMessage());
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::showFrames, 40L, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expire, 1200L, 1200L);
    }

    private static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    private static String keyOf(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public synchronized Policy at(Block b) {
        return policies.get(keyOf(b));
    }

    public synchronized List<Policy> ownedBy(UUID id) {
        List<Policy> list = new ArrayList<>();
        for (Policy pol : policies.values()) {
            if (pol.owner().equals(id) && pol.world().equals(plugin.modes().worldOf(plugin.modes().modeOf(id)))) list.add(pol);
        }
        return list;
    }

    public int premiumPercent() { return plugin.getConfig().getInt("insurance.premium-percent", 10); }
    public int payoutPercent() { return plugin.getConfig().getInt("insurance.payout-percent", 50); }
    public int days() { return plugin.getConfig().getInt("insurance.days", 1); }

    /** Auszahlung für eine Police. */
    public long payoutOf(Policy pol) {
        return Math.round(pol.value() * payoutPercent() / 100.0);
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String insure(Player p, Block b) {
        if (!(b.getState() instanceof Chest chest)) return "Das ist keine Kiste.";
        if (policies.containsKey(keyOf(b))) return "Diese Kiste ist schon versichert.";
        int max = plugin.getConfig().getInt("insurance.max-chests", 3);
        if (ownedBy(p.getUniqueId()).size() >= max) return "Du hast schon " + max + " versicherte Kisten.";

        long value = valueOf(chest.getInventory());
        long minPremium = plugin.getConfig().getLong("insurance.min-premium", 50);
        long premium = Math.max(minPremium, Math.round(value * premiumPercent() / 100.0));
        if (!plugin.economy().withdraw(p.getUniqueId(), premium)) {
            return "Die Prämie kostet " + Msg.coins(premium) + "$.";
        }
        long expires = System.currentTimeMillis() + days() * DAY_MS;
        policies.put(keyOf(b), new Policy(p.getUniqueId(), expires, value,
                b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        save();
        Msg.send(p, "<gray>Prämie: <gold>" + Msg.coins(premium) + "$ <gray>für <white>" + days()
                + " Tag(e) <gray>bei einem versicherten Wert von <gold>" + Msg.coins(value) + "$");
        return null;
    }

    /** Gibt null bei Erfolg zurück. Keine Erstattung der Prämie. */
    public synchronized String cancel(UUID owner, Policy pol) {
        String k = key(pol.world(), pol.x(), pol.y(), pol.z());
        Policy current = policies.get(k);
        if (current == null || !current.owner().equals(owner)) return "Diese Versicherung existiert nicht mehr.";
        policies.remove(k);
        save();
        return null;
    }

    /** Wert des Inhalts nach Verkaufspreisen. */
    public long valueOf(Inventory inv) {
        Map<Material, Long> prices = plugin.sell().worth();
        long sum = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null) sum += prices.getOrDefault(it.getType(), 0L) * it.getAmount();
        }
        return sum;
    }

    /** Spieler hat die Kiste zerstört. Der Besitzer selbst bekommt nichts. */
    public void onBreak(Block b, Player breaker) {
        claim(b, breaker);
    }

    /** Explosion hat die Kiste zerstört. */
    public void onExplode(Block b) {
        claim(b, null);
    }

    private synchronized void claim(Block b, Player breaker) {
        Policy pol = policies.remove(keyOf(b));
        if (pol == null) return;
        save();
        if (breaker != null && breaker.getUniqueId().equals(pol.owner())) return;

        long amount = payoutOf(pol);
        plugin.economy().deposit(pol.owner(), amount);
        String where = pol.x() + " / " + pol.y() + " / " + pol.z();

        Player owner = Bukkit.getPlayer(pol.owner());
        if (owner != null) {
            Msg.send(owner, "<red>Deine versicherte Kiste bei " + where + " wurde geraidet! <gold>+"
                    + Msg.coins(amount) + "$ <gray>Auszahlung.");
            Sfx.play(plugin, owner, Sfx.Kind.DRUM_LOW);
        }
        if (breaker != null) {
            Msg.send(breaker, "<gray>Du hast eine versicherte Kiste geraidet. Der Besitzer bekommt eine Auszahlung.");
        }
        plugin.getServer().broadcast(Msg.parse("<red>Raid-Alarm <gray>bei einer versicherten Kiste ("
                + Msg.escape(where) + ")"));
    }

    /** Partikelrahmen um jede versicherte Kiste, sichtbar für Spieler in der Nähe. */
    private synchronized void showFrames() {
        for (Policy pol : policies.values()) {
            World w = Bukkit.getWorld(pol.world());
            if (w == null) continue;
            Location center = new Location(w, pol.x() + 0.5, pol.y(), pol.z() + 0.5);
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(center) < 24 * 24) { near = true; break; }
            }
            if (!near) continue;
            double y = pol.y() + 1.1;
            for (double dx : new double[]{-0.1, 1.1}) {
                for (double dz : new double[]{-0.1, 1.1}) {
                    w.spawnParticle(Particle.HAPPY_VILLAGER, pol.x() + dx, y, pol.z() + dz, 2, 0, 0, 0, 0);
                }
            }
        }
    }

    /** Entfernt abgelaufene Policen und benachrichtigt den Besitzer. */
    private synchronized void expire() {
        long now = System.currentTimeMillis();
        List<Policy> gone = new ArrayList<>();
        for (Map.Entry<String, Policy> e : new ArrayList<>(policies.entrySet())) {
            if (e.getValue().expires() <= now) {
                gone.add(e.getValue());
                policies.remove(e.getKey());
            }
        }
        if (gone.isEmpty()) return;
        save();
        for (Policy pol : gone) {
            Player owner = Bukkit.getPlayer(pol.owner());
            if (owner != null) {
                Msg.send(owner, "<gray>Die Versicherung für die Kiste bei " + pol.x() + " / " + pol.y() + " / "
                        + pol.z() + " ist abgelaufen.");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        int i = 0;
        for (Policy pol : policies.values()) {
            String k = "p" + (i++);
            out.set(k + ".owner", pol.owner().toString());
            out.set(k + ".expires", pol.expires());
            out.set(k + ".value", pol.value());
            out.set(k + ".world", pol.world());
            out.set(k + ".x", pol.x());
            out.set(k + ".y", pol.y());
            out.set(k + ".z", pol.z());
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("insurance.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
