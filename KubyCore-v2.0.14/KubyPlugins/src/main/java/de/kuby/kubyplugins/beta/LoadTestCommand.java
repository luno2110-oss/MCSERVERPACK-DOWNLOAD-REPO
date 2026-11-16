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
            case "regeln" -> rules();
            case "dreh" -> {
                // /kubytest dreh <Spieler>: Hebel am Gold-Automaten ziehen (für das Slot-Video im echten Client)
                Player dp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (dp != null) {
                    if (dp.getGameMode() == org.bukkit.GameMode.SPECTATOR) dp.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    dp.setAllowFlight(true);
                    dp.setFlying(true);
                    plugin.casino().giveChips(dp.getUniqueId(), 10_000);
                    plugin.casinoPlay().testLog = true;
                    plugin.getLogger().info("[Kamera] Dreh am Gold-Automaten: " + plugin.slots().testPull(dp, "kgold"));
                }
            }
            case "kino" -> {
                // /kubytest kino <Spieler>: zweimal eine kurze Kino-Szene, Leisten oben vorher/nachher zählen
                Player kp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (kp != null) cinemaBars(kp, 0, new ArrayList<>());
            }
            case "kamera" -> {
                // /kubytest kamera <wo> [Spieler] – auch von der Konsole (CI "Client-Bilder" mit echtem Client)
                Player cam = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if (cam != null && args.length > 1) camera(cam, args[1].toLowerCase(java.util.Locale.ROOT));
            }
            default -> sender.sendMessage("/kubytest klicks [n] | events | casino [Sekunden] | regeln");
        }
        return true;
    }

    private static Location camBj, camRl, camPk, camSlots, camCrash, camBjRot, camBjVio, camPkRot, camPkVio;

    /** Kamera-Punkte für Screenshots aus dem echten Client (CI "Client-Bilder"). */
    private void camera(Player p, String where) {
        if (camBj == null) {
            plugin.getLogger().info("[Kamera] Casino-Spieltest läuft noch nicht");
            return;
        }
        if (!"survival".equals(plugin.modes().modeOf(p.getUniqueId()))) plugin.modes().switchTo(p, "survival");
        Location from, to;
        switch (where) {
            case "bj_oben" -> {
                from = camBj.clone().add(0, 3.0, 5.6);
                to = camBj.clone().add(0, 0.8, 1.3);
            }
            case "bj_platz" -> {
                // Blick eines Spielers auf dem mittleren Platz: eigene Karten, Summe, Einsatz, Dealer
                from = camBj.clone().add(0, 1.75, 2.4);
                to = camBj.clone().add(0, 0.8, 0.2);
            }
            case "bj_rot", "bj_violett" -> {
                Location o = where.equals("bj_rot") ? camBjRot : camBjVio;
                if (o == null) return;
                from = o.clone().add(0, 3.0, 4.6);
                to = o.clone().add(0, 0.8, 0.8);
            }
            case "poker_rot", "poker_violett" -> {
                Location o = where.equals("poker_rot") ? camPkRot : camPkVio;
                if (o == null) return;
                from = o.clone().add(0, 2.8, 3.4);
                to = o.clone().add(0, 0.8, -0.2);
            }
            case "poker_nah" -> {
                // nah über dem Pokertisch: Gemeinschaftskarten, Pot, Einsätze, eigene Karten
                from = camPk.clone().add(0, 2.1, 2.3);
                to = camPk.clone().add(0, 0.85, -0.2);
            }
            case "crash" -> {
                if (camCrash == null) return;
                from = camCrash.clone().add(0, -1.2, -6.5);
                to = camCrash.clone();
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
        p.setGameMode(org.bukkit.GameMode.SPECTATOR); // keine Hand, keine Hotbar, fällt nicht
        p.teleport(from);
        plugin.getLogger().info("[Kamera] " + p.getName() + " bei " + where);
    }

    /**
     * Netzwerk-IDs aller Töne aus dem Register des laufenden Servers nach plugins/KubyCore/sound-ids.txt ("id name"):
     * damit übersetzt die CI die Ton-Pakete, die die Bots am Tisch bekommen, in Namen.
     */
    private void dumpSoundIds() {
        try {
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("SOUND_EVENT").get(null);
            java.lang.reflect.Method toNms = Class.forName("org.bukkit.craftbukkit.CraftSound").getMethod("bukkitToMinecraft", org.bukkit.Sound.class);
            java.lang.reflect.Method getId = null;
            for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount() == 1 && m.getReturnType() == int.class) getId = m;
            }
            StringBuilder out = new StringBuilder();
            int n = 0;
            for (org.bukkit.Sound s : org.bukkit.Registry.SOUNDS) {
                Object ev = toNms.invoke(null, s);
                int id = (int) getId.invoke(registry, ev);
                out.append(id).append(' ').append(org.bukkit.Registry.SOUNDS.getKey(s).getKey()).append('\n');
                n++;
            }
            java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), "sound-ids.txt").toPath(), out);
            plugin.getLogger().info("[Casino-Test] " + n + " Ton-IDs nach sound-ids.txt geschrieben");
        } catch (Throwable t) {
            plugin.getLogger().info("[Casino-Test] Ton-IDs nicht lesbar: " + t);
        }
    }

    private static int bars(Player p) {
        int n = 0;
        for (var ignored : p.activeBossBars()) n++;
        return n;
    }

    /** Spielt zweimal eine kurze Kino-Szene und zählt die Leisten oben (Geld, Juwelen, Quests): sie dürfen nicht mehr werden. */
    private void cinemaBars(Player p, int round, List<Integer> counts) {
        if (!p.isOnline() || plugin.cinema() == null) return;
        counts.add(bars(p));
        if (round == 2) {
            boolean ok = counts.get(1).equals(counts.get(0)) && counts.get(2).equals(counts.get(0));
            plugin.getLogger().info("[Kino-Test] " + (ok ? "OK" : "FEHLER") + " Leisten oben vorher " + counts.get(0) + ", nach 1. Szene "
                    + counts.get(1) + ", nach 2. Szene " + counts.get(2));
            return;
        }
        Location base = p.getLocation();
        de.kuby.kubyplugins.cinema.Script script = new de.kuby.kubyplugins.cinema.Script() {
            @Override
            public int length() {
                return 60;
            }

            @Override
            public void frame(int t, de.kuby.kubyplugins.cinema.Frame f) {
                f.cam = new de.kuby.kubyplugins.cinema.V3(base.getX() + 3, base.getY() + 2, base.getZ() + 3);
                f.look(new de.kuby.kubyplugins.cinema.V3(base.getX(), base.getY() + 1, base.getZ()));
            }
        };
        de.kuby.kubyplugins.cinema.Cinema.Options o = new de.kuby.kubyplugins.cinema.Cinema.Options();
        o.anchor = base;
        o.onEnd = (pp, res) -> plugin.getServer().getScheduler().runTaskLater(plugin, () -> cinemaBars(p, round + 1, counts), 40L);
        if (!plugin.cinema().play(p, script, o)) plugin.getLogger().info("[Kino-Test] Szene startet nicht");
    }

    /** Ebene Testfläche: Boden aus poliertem Andesit auf Höhe y-1, darüber 12 Blöcke Luft (keine Bäume vor der Kamera). */
    private static void flatten(org.bukkit.World w, int x0, int x1, int z0, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                w.getBlockAt(x, y - 1, z).setType((x + z) % 7 == 0 ? org.bukkit.Material.POLISHED_DIORITE
                        : org.bukkit.Material.POLISHED_ANDESITE, false);
                for (int k = 0; k < 12; k++) w.getBlockAt(x, y + k, z).setType(org.bukkit.Material.AIR, false);
            }
        }
    }

    /**
     * Casino-Spieltest mit echten Spielern auf dem Server: bis zu 3 setzen sich an einen Blackjack-Tisch (Chip aus dem
     * Rack, Aktion nach dem Tipp), bis zu 3 an den Pokertisch, die Blackjack-Spieler setzen nebenbei beim Roulette.
     * Jede Abrechnung steht als "[Casino-Test]"-Zeile im Log, am Ende eine Zusammenfassung.
     */
    private void casino(int seconds) {
        if (!CASINO.isEmpty()) return;
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        all.removeIf(x -> x.getName().startsWith("Kamera"));
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
            p.sendMessage("[Casino-Test] Pause für den Spieltest (" + seconds + " s)");
            // Eichton für die Ton-Auswertung der CI (eindeutig, spielt sonst nirgends im Casino)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1f), 10L);
            if (!"survival".equals(plugin.modes().modeOf(p.getUniqueId()))) plugin.modes().switchTo(p, "survival");
            plugin.casino().giveChips(p.getUniqueId(), 20000);
        }
        plugin.casinoPlay().testLog = true;
        dumpSoundIds();
        org.bukkit.World w = spawn.getWorld();
        int gx = spawn.getBlockX(), gz = spawn.getBlockZ() + 4;
        double gy = w.getHighestBlockYAt(gx, gz) + 1;
        flatten(w, gx - 15, gx + 15, gz - 14, gz + 18, (int) gy);
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
            // die anderen Tisch-Farben daneben (rot, violett) – für die Bilder aus dem echten Client
            camBjRot = new Location(w, gx - 5.5, gy, gz + 9.5);
            camBjVio = new Location(w, gx + 6.5, gy, gz + 9.5);
            camPkRot = new Location(w, gx - 10.5, gy, gz - 9.5, 180f, 0f);
            camPkVio = new Location(w, gx + 11.5, gy, gz - 9.5, 180f, 0f);
            plugin.blackjack().testTable("test_rot", camBjRot, 0f, de.kuby.kubyplugins.casino.TableTier.ROT);
            plugin.blackjack().testTable("test_violett", camBjVio, 0f, de.kuby.kubyplugins.casino.TableTier.VIOLETT);
            plugin.pokerHr().setTable(camPkRot);
            plugin.pokerVio().setTable(camPkVio);
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

    // =================================================================================================================
    // Casino-Regeltest: echte Spieler-Konten, echte Abrechnung, echte Crash-Runde, echter Slot-Dreh
    // =================================================================================================================

    private int ruleOk, ruleBad;

    private void check(String what, boolean ok, String detail) {
        if (ok) ruleOk++;
        else ruleBad++;
        plugin.getLogger().info("[Casino-Regel] " + (ok ? "OK " : "FEHLER ") + what + (detail == null ? "" : " – " + detail));
    }

    /**
     * Prüft die Casino-Regeln mit einem echten Spieler auf dem Server: Juwelen nur für VIP und nur am Blackjack-Tisch,
     * 50 bis 10.000 je Runde, Volumen 50.000; Siegesserie +0,2 bis x2,5 (High Roller +0,5 bis x8); Goldene Zeit
     * verdoppelt Gewinne; eine echte Crash-Runde (Kurs je Sekunde gemessen) und ein echter Slot-Dreh.
     * Jede Prüfung steht als "[Casino-Regel] OK|FEHLER"-Zeile im Log, am Ende das Ergebnis.
     */
    private void rules() {
        ruleOk = 0;
        ruleBad = 0;
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        all.removeIf(x -> x.getName().startsWith("Kamera"));
        all.sort(java.util.Comparator.comparing(Player::getName));
        if (all.isEmpty()) {
            plugin.getLogger().info("[Casino-Regel] keine Spieler online");
            return;
        }
        // der letzte Spieler (nach Namen) sitzt nicht am Casino-Spieltest (der nimmt die ersten 6): keine offenen Wetten
        Player p = all.get(all.size() - 1);
        java.util.UUID id = p.getUniqueId();
        var cp = plugin.casinoPlay();
        if (!"survival".equals(plugin.modes().modeOf(id))) plugin.modes().switchTo(p, "survival");
        CASINO.add(id); // keine Zufallsklicks während des Tests
        p.sendMessage("[Casino-Test] Pause für den Regeltest");
        cp.testLog = true;
        cp.testReset(id);
        cp.testGolden = false;
        plugin.getLogger().info("[Casino-Regel] Start mit " + p.getName());

        // --- Juwelen ---
        org.bukkit.permissions.PermissionAttachment vip = null;
        if (!cp.vip(p)) {
            check("Juwelen ohne VIP gesperrt", cp.setMode(p, de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS) != null, null);
            vip = p.addAttachment(plugin, "kuby.casino.juwelen", true);
        }
        check("VIP darf auf Juwelen umstellen", cp.setMode(p, de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS) == null, null);
        plugin.jewels().add(id, 200_000);
        check("Juwelen nur am Blackjack-Tisch (Roulette spielt in Chips)",
                cp.cur(id, "roulette") == de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS
                        && cp.cur(id, "blackjack") == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS, null);
        long j0 = plugin.jewels().balance(id);
        String e49 = cp.take(p, "blackjack", 49);
        check("49 Juwelen abgelehnt (min 50)", e49 != null && plugin.jewels().balance(id) == j0, e49);
        String e50 = cp.take(p, "blackjack", 50);
        check("50 Juwelen angenommen", e50 == null && plugin.jewels().balance(id) == j0 - 50, e50);
        cp.refund(id, "blackjack", 50);
        String eMax = cp.take(p, "blackjack", 10_001);
        check("10.001 Juwelen abgelehnt (max 10.000)", eMax != null, eMax);
        int rounds = 0;
        for (int i = 0; i < 5; i++) {
            if (cp.take(p, "blackjack", 10_000) == null) {
                cp.settle(id, "blackjack", 10_000, 0);
                rounds++;
            }
        }
        check("5 Runden à 10.000 Juwelen gespielt", rounds == 5, "Volumen frei: " + cp.volumeLeft(id));
        String eVol = cp.take(p, "blackjack", 50);
        check("Volumen 50.000 erreicht: weiterer Einsatz abgelehnt", eVol != null && cp.volumeLeft(id) == 0, eVol);
        cp.setMode(p, de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS);
        if (vip != null) p.removeAttachment(vip);

        // --- Siegesserie ---
        cp.testReset(id);
        plugin.casino().giveChips(id, 100_000);
        StringBuilder seq = new StringBuilder();
        boolean seqOk = true;
        for (int i = 0; i < 10; i++) {
            cp.take(p, "blackjack", 100);
            var o = cp.settle(id, "blackjack", 100, 200);
            double want = Math.min(2.5, 1.0 + 0.2 * i);
            seqOk &= Math.abs(o.mult() - want) < 1e-9 && o.payout() == 200 + Math.round(100 * (want - 1));
            seq.append("x").append(de.kuby.kubyplugins.service.CasinoPlay.fmt(o.mult())).append(' ');
        }
        check("Siegesserie +0,2 je Sieg bis x2,5 (Gewinn 100)", seqOk, seq.toString().trim());
        cp.take(p, "blackjack", 100);
        cp.settle(id, "blackjack", 100, 0);
        cp.take(p, "blackjack", 100);
        var after = cp.settle(id, "blackjack", 100, 200);
        check("Niederlage setzt die Serie zurück", after.mult() == 1.0 && after.streak() == 1, "x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(after.mult()));
        cp.testReset(id);
        StringBuilder hr = new StringBuilder();
        boolean hrOk = true;
        for (int i = 0; i < 16; i++) {
            cp.take(p, "blackjack_hr", 100);
            var o = cp.settle(id, "blackjack_hr", 100, 200);
            hrOk &= Math.abs(o.mult() - Math.min(8.0, 1.0 + 0.5 * i)) < 1e-9;
            if (i % 3 == 0 || i == 15) hr.append("x").append(de.kuby.kubyplugins.service.CasinoPlay.fmt(o.mult())).append(' ');
        }
        check("High Roller: +0,5 je Sieg bis x8", hrOk, hr.toString().trim());

        // --- Goldene Zeit ---
        cp.testReset(id);
        cp.testGolden = true;
        cp.take(p, "blackjack", 100);
        var g = cp.settle(id, "blackjack", 100, 200);
        check("Goldene Zeit verdoppelt den Gewinn", g.golden() && g.mult() == 2.0 && g.payout() == 300,
                "Einsatz 100, Spiel zahlt 200, ausgezahlt " + g.payout());
        cp.testGolden = false;
        cp.take(p, "blackjack", 100);
        var ng = cp.settle(id, "blackjack", 100, 200);
        check("außerhalb der Goldenen Zeit kein Bonus (Serie 1: x1,2)", !ng.golden() && Math.abs(ng.mult() - 1.2) < 1e-9, null);
        // Goldene Zeit nach der echten Uhr (ohne Test-Schalter): Stunde in der eingestellten Zeitzone entscheidet
        cp.testGolden = null;
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(cp.goldenZone());
        boolean expect = now.getHour() >= plugin.getConfig().getInt("casino.goldene-zeit.von", 17)
                && now.getHour() < plugin.getConfig().getInt("casino.goldene-zeit.bis", 22);
        cp.testReset(id);
        cp.take(p, "blackjack", 100);
        var real = cp.settle(id, "blackjack", 100, 200);
        check("Goldene Zeit nach echter Uhr (" + cp.goldenZone() + " " + String.format("%02d:%02d", now.getHour(), now.getMinute()) + " → "
                        + (expect ? "aktiv" : "nicht aktiv") + ")",
                real.golden() == expect && real.payout() == (expect ? 300 : 200), "Einsatz 100, Spiel zahlt 200, ausgezahlt " + real.payout());
        cp.testGolden = false;
        cp.testReset(id);

        // --- echte Crash-Runde (Goldene Zeit bleibt aus, damit die Auszahlung genau 100 x Kurs ist) ---
        Location spawn = plugin.modes().spawnOf("survival");
        org.bukkit.World w = spawn.getWorld();
        Location scr;
        if (camBj != null) {
            // dunkle Wand hinter dem Bildschirm am Rand des Testgeländes, Bild zeigt zu den Tischen (-z)
            int bx = camBj.getBlockX(), by = camBj.getBlockY(), bz = camBj.getBlockZ() + 14;
            for (int x = bx - 4; x <= bx + 4; x++)
                for (int y = by; y <= by + 5; y++) w.getBlockAt(x, y, bz).setType(org.bukkit.Material.BLACK_CONCRETE, false);
            scr = new Location(w, bx + 0.5, by + 2.5, bz - 0.01);
        } else {
            scr = new Location(w, spawn.getBlockX() + 0.5, w.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ() + 18) + 3.0,
                    spawn.getBlockZ() + 18.0);
        }
        plugin.crash().testScreen(scr, 180f);
        camCrash = scr;
        plugin.crash().testCrashAt = 8.0;
        long chips0 = plugin.casino().chips(id);
        String bet = plugin.crash().bet(p, 100);
        check("Crash: Einsatz 100 gesetzt", bet == null, bet);
        List<double[]> samples = new ArrayList<>();
        final boolean[] cashed = {false};
        final double[] cashAt = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            var ph = plugin.crash().phase();
            if (ph == de.kuby.kubyplugins.casino.CrashGame.Phase.RUNNING) {
                samples.add(new double[]{samples.size() / 20.0, plugin.crash().current()});
                if (!cashed[0] && plugin.crash().current() >= 5.0) {
                    cashAt[0] = plugin.crash().current();
                    cashed[0] = plugin.crash().cashout(p) == null;
                }
            } else if (!samples.isEmpty()) {
                task.cancel();
                crashReport(p, samples, cashed[0], cashAt[0], chips0);
            }
        }, 1L, 1L);
    }

    private void crashReport(Player p, List<double[]> s, boolean cashed, double cashAt, long chips0) {
        StringBuilder line = new StringBuilder();
        double[] marks = {1.5, 2, 3, 5, 7};
        double prevRate = 0;
        boolean faster = true;
        for (double m : marks) {
            int i = -1;
            for (int k = 0; k < s.size(); k++) if (s.get(k)[1] >= m) { i = k; break; }
            if (i < 0 || i + 20 >= s.size()) continue;
            double rate = Math.log(s.get(i + 20)[1] / s.get(i)[1]) * 100; // % pro Sekunde
            line.append("x").append(de.kuby.kubyplugins.service.CasinoPlay.fmt(m)).append(" nach ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", s.get(i)[0])).append(" s: +")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", rate)).append(" %/s; ");
            faster &= rate > prevRate;
            prevRate = rate;
        }
        check("Crash: Kurs steigt mit der Höhe immer schneller (echte Runde, je Tick gemessen)", faster && prevRate > 0, line.toString().trim());
        // die echte Abrechnung der Crash-Runde (nicht der Kontostand: andere Spieler und Belohnungen ändern ihn nebenbei)
        var o = plugin.casinoPlay().testLast.get(p.getUniqueId() + "|crash");
        long gross = (long) Math.floor(100 * cashAt);
        check("Crash: Auszahlung bei x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(cashAt), cashed && o != null && o.stake() == 100
                        && Math.abs(o.gross() - gross) <= 1 && o.payout() == o.gross(),
                o == null ? "keine Abrechnung" : "Einsatz " + o.stake() + ", Spiel zahlt " + o.gross() + " (100 × Kurs), ausgezahlt " + o.payout());
        // --- echter Slot-Dreh am Gold-Automaten aus dem Casino-Spieltest ---
        plugin.casino().giveChips(p.getUniqueId(), 10_000);
        boolean pulled = plugin.slots().testPull(p, "kgold");
        check("Slots: Hebel am Gold-Automaten gezogen", pulled, null);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.casinoPlay().testLog = false;
            plugin.casinoPlay().testGolden = null;
            CASINO.remove(p.getUniqueId());
            plugin.getLogger().info("[Casino-Regel] Ergebnis: " + ruleOk + " OK, " + ruleBad + " Fehler");
        }, 200L);
    }
}
