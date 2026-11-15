package de.kuby.kubyplugins.beta;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.UiClicks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Belastungstest (nur Konsole und Admins, kuby.admin):
 * <ul>
 *   <li>/kubytest klicks [n]: für jeden Spieler online n zufällige Klicks im gerade offenen Menü (wie ein echter Klick,
 *   danach geht es im Folge-Menü weiter); ist keins offen, öffnet er zuerst ein zufälliges Hauptmenü.</li>
 *   <li>/kubytest events: löst alle registrierten Test-Events aus (Quiz, Ziehungen, Event-Kapitel, Turnier …).</li>
 * </ul>
 * Fehler landen mit Stacktrace im Log ("Fehler bei Menü-Klick", "Test-Event fehlgeschlagen") und lassen den
 * Belastungstest in der CI scheitern.
 */
public final class LoadTestCommand implements CommandExecutor {

    /** Menüs, mit denen ein Klick-Durchgang beginnt, wenn gerade keins offen ist. */
    private static final String[] START = {"menu", "shop", "battlepass", "daily", "spielzeit", "settings", "stats", "guide", "friends",
            "team", "markt", "kits", "keys", "cosmetics", "angeln", "angeln pass", "angeln markt", "casino", "cookie", "quests", "jobs",
            "insel", "combo", "skyblock", "baltop", "ranking", "investments", "regeln", "hilfe", "feedback", "erfolge", "support"};

    private final KubyPlugins plugin;
    /** Spieler im Casino-Spieltest: keine Zufalls-Klicks in Menüs (die könnten sie vom Tisch wegholen). */
    public static final java.util.Set<java.util.UUID> CASINO = new java.util.HashSet<>();

    public LoadTestCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("kuby.admin")) return true;
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "klicks" -> {
                int n = 3;
                try {
                    if (args.length > 1) n = Math.max(1, Math.min(20, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                    // Standard
                }
                int clicks = 0, opened = 0;
                for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                    if (CASINO.contains(p.getUniqueId())) continue;
                    for (int i = 0; i < n; i++) {
                        if (!p.isOnline()) break;
                        if (UiClicks.randomClick(p)) {
                            clicks++;
                        } else {
                            String cmd = START[ThreadLocalRandom.current().nextInt(START.length)];
                            p.performCommand(cmd);
                            opened++;
                        }
                    }
                }
                plugin.getLogger().info("[Belastungstest] " + clicks + " Menü-Klicks, " + opened + " Menüs geöffnet");
            }
            case "events" -> {
                List<String> done = new ArrayList<>();
                for (Map.Entry<String, Runnable> e : plugin.testEvents().entrySet()) {
                    try {
                        e.getValue().run();
                        done.add(e.getKey());
                    } catch (RuntimeException ex) {
                        plugin.getLogger().severe("Test-Event fehlgeschlagen: " + e.getKey() + ": " + ex);
                        ex.printStackTrace();
                    }
                }
                plugin.getLogger().info("[Belastungstest] Events ausgelöst: " + String.join(", ", done));
            }
            case "casino" -> casino(args.length > 1 ? Integer.parseInt(args[1]) : 100);
            case "kamera" -> {
                if (sender instanceof Player p && args.length > 1) camera(p, args[1].toLowerCase(java.util.Locale.ROOT));
            }
            default -> sender.sendMessage("/kubytest klicks [n] | events | casino [Sekunden]");
        }
        return true;
    }

    private static Location camBj, camRl, camPk, camSlots;

    /** Kamera-Punkte für Screenshots aus dem echten Client (CI "Client-Bilder"). */
    private void camera(Player p, String where) {
        Location from, to;
        switch (where) {
            case "bj_oben" -> {
                from = camBj.clone().add(0, 3.0, 5.6);
                to = camBj.clone().add(0, 0.8, 1.3);
            }
            case "bj_seite" -> {
                from = camBj.clone().add(-3.6, 1.9, 3.2);
                to = camBj.clone().add(0, 0.8, 1.2);
            }
            case "poker" -> {
                from = camPk.clone().add(0, 3.2, -4.2);
                to = camPk.clone().add(0, 0.8, 0);
            }
            case "roulette" -> {
                from = camRl.clone().add(0, 2.6, 6.0);
                to = camRl.clone().add(0, 0, 0.5);
            }
            default -> {
                from = camSlots.clone().add(0, 1.7, 4.4);
                to = camSlots.clone().add(0, 1.3, 0);
            }
        }
        if (p.getVehicle() != null) p.leaveVehicle();
        from.setDirection(to.toVector().subtract(from.toVector()));
        p.setAllowFlight(true);
        p.setFlying(true);
        p.teleport(from);
        plugin.getLogger().info("[Kamera] " + p.getName() + " bei " + where);
    }

    /**
     * Casino-Spieltest mit echten Spielern auf dem Server: bis zu 3 setzen sich an einen Blackjack-Tisch (Chip aus dem
     * Rack, Aktion nach dem Tipp), bis zu 3 an den Pokertisch, die Blackjack-Spieler setzen nebenbei beim Roulette.
     * Jede Abrechnung steht als "[Casino-Test]"-Zeile im Log, am Ende eine Zusammenfassung.
     */
    private void casino(int seconds) {
        if (!CASINO.isEmpty()) return;
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        all.sort(java.util.Comparator.comparing(Player::getName));
        List<Player> players = all.subList(0, Math.min(6, all.size()));
        if (players.isEmpty()) {
            plugin.getLogger().info("[Casino-Test] keine Spieler online");
            return;
        }
        Location spawn = plugin.modes().spawnOf("survival");
        if (spawn == null) {
            plugin.getLogger().info("[Casino-Test] kein Survival-Spawn");
            return;
        }
        for (Player p : players) {
            CASINO.add(p.getUniqueId());
            p.sendMessage("[Casino-Test] Pause für den Spieltest");
            if (!"survival".equals(plugin.modes().modeOf(p.getUniqueId()))) plugin.modes().switchTo(p, "survival");
            plugin.casino().giveChips(p.getUniqueId(), 20000);
        }
        plugin.casinoPlay().testLog = true;
        org.bukkit.World w = spawn.getWorld();
        int gx = spawn.getBlockX(), gz = spawn.getBlockZ() + 4;
        double gy = w.getHighestBlockYAt(gx, gz) + 1;
        Location bjAt = new Location(w, gx + 0.5, gy, gz + 0.5);
        Location rlAt = new Location(w, gx + 9.5, gy + 1, gz + 0.5);
        Location pkAt = new Location(w, gx - 9.5, gy, gz + 0.5, 180f, 0f);
        Location slotA = new Location(w, gx + 0.5, gy + 1.5, gz - 7.5);
        Location slotB = new Location(w, gx + 4.5, gy + 1.5, gz - 7.5);
        camBj = bjAt;
        camRl = rlAt;
        camPk = pkAt;
        camSlots = new Location(w, gx + 2.5, gy, gz - 7.5);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            de.kuby.kubyplugins.casino.BlackjackGame bj = plugin.blackjack().testTable(bjAt, 0f);
            plugin.roulette().setTable(rlAt, 10, 8, 0f);
            plugin.poker().setTable(pkAt);
            // zwei neue Automaten freistehend (Hocker 1,6 davor, Blick nach +z zum Spieler)
            plugin.slots().testMachine("kgold", slotA, 0f, new Location(w, slotA.getX(), gy, slotA.getZ() + 1.6), "gold");
            plugin.slots().testMachine("kneon", slotB, 0f, new Location(w, slotB.getX(), gy, slotB.getZ() + 1.6), "neon");
            int bjSeat = 0, pkSeat = 0;
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                if (!p.isOnline()) continue;
                if (i % 2 == 0 && bj != null && bjSeat < 3) {
                    p.teleport(bj.table.seatLocation(bjSeat + 1));
                    bj.sit(p, bjSeat + 1);
                    bjSeat++;
                } else {
                    p.teleport(pkAt);
                    String err = plugin.poker().testSit(p, 2000, pkSeat++);
                    if (err != null) plugin.getLogger().info("[Casino-Test] Poker-Platz für " + p.getName() + ": " + err);
                }
            }
            long displays = bj == null ? 0 : w.getNearbyEntities(bjAt, 5, 3, 5).stream().filter(e -> e instanceof org.bukkit.entity.ItemDisplay).count();
            plugin.getLogger().info("[Casino-Test] Start: " + bjSeat + " am Blackjack-Tisch (" + displays + " Anzeigen), " + pkSeat
                    + " am Pokertisch, Roulette-Tisch steht");
            String[] choices = {"rot", "schwarz", "17", "gerade", "1-18", "hoch"};
            final int[] t = {0};
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                t[0]++;
                if (bj != null) bj.autoPlay(CASINO);
                plugin.poker().testAutoAct(CASINO);
                if (t[0] % 12 == 3) {
                    for (Player p : players) {
                        if (!p.isOnline() || plugin.poker().isSeated(p.getUniqueId())) continue;
                        String err = plugin.roulette().betByName(p, choices[ThreadLocalRandom.current().nextInt(choices.length)], 50);
                        if (err != null) plugin.getLogger().info("[Casino-Test] roulette " + p.getName() + ": " + err);
                    }
                }
                if (t[0] >= seconds) {
                    task.cancel();
                    plugin.getLogger().info("[Casino-Test] Ende: Poker-Hände " + plugin.poker().handsPlayed() + ", am Blackjack-Tisch noch "
                            + (bj == null ? 0 : bj.seated()));
                    for (Player p : players) {
                        plugin.blackjack().leave(p.getUniqueId());
                        if (plugin.poker().isSeated(p.getUniqueId())) plugin.poker().stand(p.getUniqueId());
                    }
                    plugin.casinoPlay().testLog = false;
                    CASINO.clear();
                }
            }, 20L, 20L);
        }, 50L);
    }
}
