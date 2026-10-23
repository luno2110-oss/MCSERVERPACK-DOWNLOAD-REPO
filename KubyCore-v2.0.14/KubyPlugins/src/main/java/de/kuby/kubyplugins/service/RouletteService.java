package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Roulette am Casino-Tisch (europäisch, 0 bis 36). Einsätze per /casino setzen, danach dreht sich das Rad
 * nach einer Wartezeit von selbst. Das Rad ist eine Anzeige aus Wolle-Blöcken über dem Tisch.
 * Wer vor der Drehung ausloggt, behält seinen Einsatz. Beim Herunterfahren bekommen offene Einsätze ihre Chips zurück.
 */
public final class RouletteService {

    private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final String SURVIVAL = "survival";
    private static final double RANGE = 8;
    private static final long RESULT_SHOWN_TICKS = 200L;

    /** kind ist "rot", "schwarz" oder "zahl". Bei "zahl" steht die Zahl in number. */
    private record Bet(UUID player, String kind, int number, long amount) {}

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final List<Bet> bets = new ArrayList<>();
    private final List<Entity> displays = new ArrayList<>();
    private String tisch;
    private BukkitTask spinTask;

    public RouletteService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "roulette.yml");
        this.tag = new NamespacedKey(plugin, "kp_roulette");
        tisch = YamlConfiguration.loadConfiguration(file).getString("tisch");
    }

    /** Stellt die Anzeige nach einem Neustart wieder her und entfernt Reste der letzten Sitzung. */
    public synchronized void respawn() {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        for (TextDisplay d : t.getWorld().getNearbyEntitiesByType(TextDisplay.class, t, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
        for (BlockDisplay d : t.getWorld().getNearbyEntitiesByType(BlockDisplay.class, t, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
        showIdle();
    }

    /** Setzt den Roulette-Tisch auf den angegebenen Block. */
    public synchronized void setTable(Location block) {
        tisch = block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
        save();
        showIdle();
    }

    public boolean hasTable() {
        return table() != null;
    }

    public boolean isTable(Location l) {
        Location t = table();
        return t != null && l.getWorld() != null && l.getWorld().equals(t.getWorld())
                && l.getBlockX() == t.getBlockX() && l.getBlockY() == t.getBlockY() && l.getBlockZ() == t.getBlockZ();
    }

    /** Prüft Modus und Nähe zum Tisch. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String checkTable(Player p) {
        Location t = table();
        if (t == null) return "Der Roulette-Tisch ist noch nicht eingerichtet.";
        if (!SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))) return "Roulette gibt es nur in Survival.";
        if (p.getLocation().getWorld() == null || !p.getLocation().getWorld().equals(t.getWorld())
                || p.getLocation().distanceSquared(t) > RANGE * RANGE) {
            return "Geh näher an den Roulette-Tisch.";
        }
        return null;
    }

    /** Setzt einen Einsatz. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String bet(Player p, String choice, long amount) {
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        long max = plugin.getConfig().getLong("casino.roulette.max-bet", 50);
        if (amount <= 0 || amount > max) return "Einsatz zwischen 1 und " + max + " Chips.";
        String c = choice.toLowerCase();
        Bet bet;
        if (c.equals("rot") || c.equals("red")) {
            bet = new Bet(p.getUniqueId(), "rot", -1, amount);
        } else if (c.equals("schwarz") || c.equals("black")) {
            bet = new Bet(p.getUniqueId(), "schwarz", -1, amount);
        } else {
            int number;
            try {
                number = Integer.parseInt(c);
            } catch (NumberFormatException e) {
                return "Wähle rot, schwarz oder eine Zahl von 0 bis 36.";
            }
            if (number < 0 || number > 36) return "Wähle rot, schwarz oder eine Zahl von 0 bis 36.";
            bet = new Bet(p.getUniqueId(), "zahl", number, amount);
        }
        if (!plugin.casino().takeChips(p.getUniqueId(), amount)) {
            return "Du hast nicht genug Chips. Tausch sie am Kassentisch.";
        }
        bets.add(bet);
        if (spinTask == null) {
            long wait = plugin.getConfig().getLong("casino.roulette.wait-seconds", 20);
            spinTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::spin, wait * 20L);
        }
        showIdle();
        return null;
    }

    /** Gibt alle offenen Einsätze zurück, bevor der Server stoppt. */
    public synchronized void shutdown() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
        for (Bet b : bets) plugin.casino().giveChips(b.player(), b.amount());
        bets.clear();
    }

    private synchronized void spin() {
        spinTask = null;
        int result = ThreadLocalRandom.current().nextInt(37);
        String color = result == 0 ? "grün" : RED.contains(result) ? "rot" : "schwarz";
        // Pro Spieler zusammenfassen, damit bei mehreren Einsätzen nur ein Effekt kommt.
        Map<UUID, Long> netOf = new LinkedHashMap<>();
        Map<UUID, Long> stakeOf = new LinkedHashMap<>();
        Set<UUID> numberHit = new HashSet<>();
        for (Bet b : bets) {
            long payout = payout(b, result, color);
            if (payout > 0) plugin.casino().giveChips(b.player(), payout);
            netOf.merge(b.player(), payout - b.amount(), Long::sum);
            stakeOf.merge(b.player(), b.amount(), Long::sum);
            if (payout > 0 && b.kind().equals("zahl")) numberHit.add(b.player());
            Player online = Bukkit.getPlayer(b.player());
            if (online != null) {
                long net = payout - b.amount();
                String text = net >= 0
                        ? "<green>Gewonnen! +" + net + " Chips"
                        : "<red>Verloren. " + net + " Chips";
                Msg.send(online, "<gold>Roulette: " + result + " " + color + "  " + text);
            }
        }
        bets.clear();
        Location t = table();
        Location fxAt = t == null ? null : t.clone().add(0, 2.8, 0);
        for (Map.Entry<UUID, Long> e : netOf.entrySet()) {
            long net = e.getValue();
            CasinoFx.Result fx = net > 0
                    ? (numberHit.contains(e.getKey()) ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN)
                    : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE;
            CasinoFx.show(plugin, e.getKey(), fxAt, fx, net);
            long stake = stakeOf.getOrDefault(e.getKey(), 0L);
            plugin.casinoLog().record("Roulette", e.getKey(), stake, stake + net);
        }
        showResult(result, color);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::resetIdle, RESULT_SHOWN_TICKS);
    }

    private long payout(Bet b, int result, String color) {
        return switch (b.kind()) {
            case "zahl" -> b.number() == result ? b.amount() * 36 : 0;
            case "rot" -> color.equals("rot") ? b.amount() * 2 : 0;
            case "schwarz" -> color.equals("schwarz") ? b.amount() * 2 : 0;
            default -> 0;
        };
    }

    private synchronized void resetIdle() {
        if (spinTask == null && bets.isEmpty()) showIdle();
    }

    private void showIdle() {
        render("<gold>ROULETTE <gray>· <white>Warte auf Einsätze", null);
    }

    private void showResult(int result, String color) {
        Material wool = switch (color) {
            case "rot" -> Material.RED_WOOL;
            case "schwarz" -> Material.BLACK_WOOL;
            default -> Material.GREEN_WOOL;
        };
        render("<gold>Ergebnis <white>" + result + " <gray>" + color, wool);
    }

    /** Baut die Anzeige neu auf: Text über dem Tisch und optional ein Wolle-Block als Rad-Anzeige. */
    private void render(String status, Material wool) {
        Location t = table();
        if (t == null || t.getWorld() == null) return;
        if (!t.getWorld().isChunkLoaded(t.getBlockX() >> 4, t.getBlockZ() >> 4)) return;
        for (Entity e : displays) e.remove();
        displays.clear();
        Location base = t.clone().add(0, 1.0, 0);
        TextDisplay text = t.getWorld().spawn(base.clone().add(0, 1.2, 0), TextDisplay.class, e -> {
            e.text(Msg.parse(status));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        displays.add(text);
        if (wool != null) {
            BlockDisplay block = t.getWorld().spawn(base.clone().add(-0.5, 0.2, -0.5), BlockDisplay.class, e -> {
                e.setBlock(Bukkit.createBlockData(wool));
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
            displays.add(block);
        }
    }

    private Location table() {
        if (tisch == null) return null;
        String[] p = tisch.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        if (tisch != null) out.set("tisch", tisch);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("roulette.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
