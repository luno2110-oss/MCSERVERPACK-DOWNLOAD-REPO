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
        // Testbefehle verändern die Welt (Testgelände am Survival-Spawn wird planiert) und geben Chips aus:
        // nur auf Testservern (kubytest.erlaubt: true, setzen die CI-Tests), nie aus Versehen auf dem echten Server
        if (!plugin.getConfig().getBoolean("kubytest.erlaubt", false)) {
            sender.sendMessage("/kubytest ist nur für Testserver (kubytest.erlaubt: true in der config.yml).");
            return true;
        }
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
            case "rundgang" -> {
                // /kubytest rundgang <Spieler>: Casino-Rundgang abspielen (Video aus dem echten Client)
                Player rp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (rp != null) {
                    if (rp.getGameMode() == org.bukkit.GameMode.SPECTATOR) rp.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    rp.setAllowFlight(true);
                    rp.setFlying(false);
                    if (camBj != null) rp.teleport(camBj.clone().add(0, 0, 6.5).setDirection(new org.bukkit.util.Vector(0, 0, -1)));
                    boolean ok = plugin.casinoTour() != null && plugin.casinoTour().play(rp);
                    plugin.getLogger().info("[Kamera] Rundgang gestartet: " + ok);
                }
            }
            case "modelle" -> {
                // /kubytest modelle: alle Item-Modelle, die gerade irgendwo angezeigt werden (Tische, Karten, Automaten,
                // NPC-Figuren, Effekte) – die CI prüft jedes gegen das Pack
                java.util.TreeSet<String> keys = new java.util.TreeSet<>();
                int n = 0;
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    for (org.bukkit.entity.ItemDisplay d : w.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class)) {
                        n++;
                        var it = d.getItemStack();
                        if (it != null && it.hasItemMeta() && it.getItemMeta().hasItemModel()) keys.add(it.getItemMeta().getItemModel().toString());
                    }
                    for (org.bukkit.entity.Player pl : w.getPlayers()) {
                        for (var it : pl.getInventory().getContents()) {
                            if (it != null && it.hasItemMeta() && it.getItemMeta().hasItemModel()) keys.add(it.getItemMeta().getItemModel().toString());
                        }
                    }
                }
                plugin.getLogger().info("[Modell-Test] " + n + " Anzeigen, " + keys.size() + " Modelle: " + String.join(",", keys));
            }
            case "inventar" -> {
                // /kubytest inventar <Spieler>: echte Klick-, Kreativ- und Drop-Events auslösen und prüfen, ob ein Plugin sie
                // abbricht (Fehlerbericht: Items sprangen in den Slot zurück, nichts ließ sich droppen)
                Player ip = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (ip != null) inventoryTest(ip);
            }
            case "einrichten" -> {
                // /kubytest einrichten <Spieler>: Casino-Bereich über das Testgelände und Spawnpunkt wie mit den Werkzeugen
                Player ep = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (ep == null || arenaA == null) {
                    plugin.getLogger().info("[Einrichten-Test] FEHLER Spieltest läuft nicht oder Spieler fehlt");
                } else {
                    if (!"survival".equals(plugin.modes().modeOf(ep.getUniqueId()))) plugin.modes().switchTo(ep, "survival");
                    if (ep.getGameMode() == org.bukkit.GameMode.SPECTATOR) ep.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    ep.setAllowFlight(true);
                    ep.setFlying(false);
                    Location sp = camBj.clone().add(0, 0, 6.5);
                    sp.setYaw(180f);
                    sp.setPitch(10f);
                    // erst auf den Boden neben den Spawnpunkt stellen (sonst bricht der Teleport mit Wartezeit ab)
                    ep.teleport(sp.clone().add(3, 0, 2));
                    String err = plugin.casinoSetup().test(ep, arenaA, arenaB, sp);
                    Player fp = ep;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.casino().travel(fp), 20L);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        String e2 = err;
                        if (e2 == null && fp.getLocation().distanceSquared(sp) > 1) e2 = "/casino bringt nicht zum Spawnpunkt (" + fp.getLocation().toVector() + ")";
                        if (e2 == null && !plugin.casinoMusic().inCasino(fp.getLocation())) e2 = "Spieler gilt am Spawnpunkt nicht als im Casino";
                        if (e2 == null && !plugin.casinoMusic().playing(fp.getUniqueId())) e2 = "keine Casino-Musik am Spawnpunkt";
                        var r = plugin.regions().find(de.kuby.kubyplugins.service.CasinoService.REGION);
                        plugin.getLogger().info("[Einrichten-Test] " + (e2 == null ? "OK" : "FEHLER") + " "
                                + (e2 == null ? "Region " + (r.x2() - r.x1() + 1) + "x" + (r.z2() - r.z1() + 1) + " Höhe " + r.y1() + "-" + r.y2()
                                + ", /casino bringt zum Spawnpunkt, Musik läuft" : e2));
                    }, 160L);
                }
            }
            case "kino" -> {
                // /kubytest kino <Spieler>: zweimal eine kurze Kino-Szene, Leisten oben vorher/nachher zählen
                Player kp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (kp != null) cinemaBars(kp, 0, new ArrayList<>());
            }
            case "aufstehen" -> {
                // /kubytest aufstehen <Spieler>: wie /sit (Ständer 0,1 unter den Füßen) und wie ein Blackjack-Hocker
                // hinsetzen, absteigen, dann muss der Spieler mit den Füßen auf dem Boden stehen und darf nirgends stecken
                Player ap = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (ap == null || arenaA == null) plugin.getLogger().info("[Aufstehen-Test] FEHLER Spieltest läuft nicht oder Spieler fehlt");
                else standUpTest(ap);
            }
            case "crash" -> {
                Player xp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (xp != null) crashShow(xp);
            }
            case "rennen" -> {
                // /kubytest rennen <Spieler>: Kamera-Spieler wettet (Sieg auf drei Pferde, Platz auf den Favoriten),
                // Countdown springt auf 1 s – ein ganzes Rennen für Bilder und Video
                Player rp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (rp != null) {
                    plugin.casino().giveChips(rp.getUniqueId(), 20_000);
                    plugin.casinoPlay().testLog = true;
                    plugin.race().testLog = true;
                    var rf = plugin.race().field();
                    int fav = 0;
                    for (int i = 1; i < 6; i++) if (rf.win()[i] > rf.win()[fav]) fav = i;
                    StringBuilder res = new StringBuilder();
                    for (int lane : new int[]{0, 2, 4}) {
                        String err = plugin.race().testBet(rp, lane, false, 100);
                        res.append("Sieg #").append(lane + 1).append(err == null ? " ok" : " " + err).append(", ");
                    }
                    String err = plugin.race().testBet(rp, fav, true, 200);
                    res.append("Platz #").append(fav + 1).append(err == null ? " ok" : " " + err);
                    plugin.race().testFastForward();
                    plugin.getLogger().info("[Rennen-Test] Wetten: " + res);
                }
            }
            case "plinko" -> {
                // /kubytest plinko <Spieler> [niedrig|mittel|hoch] [Anzahl]: Kamera-Spieler wirft Kugeln am Test-Brett
                // (alle 8 Ticks eine, Einsatz 100), jede Abrechnung als "[Casino-Test] plinko"-Zeile im Log
                Player pp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                de.kuby.kubyplugins.casino.Plinko.Risk rk = args.length > 2 ? de.kuby.kubyplugins.casino.Plinko.Risk.byName(args[2]) : null;
                int n = args.length > 3 ? Math.max(1, Math.min(10, Integer.parseInt(args[3]))) : 6;
                if (pp != null) {
                    plugin.casino().giveChips(pp.getUniqueId(), 20_000);
                    plugin.casinoPlay().testLog = true;
                    plugin.plinko().testLog = true;
                    de.kuby.kubyplugins.casino.Plinko.Risk risk = rk == null ? de.kuby.kubyplugins.casino.Plinko.Risk.HOCH : rk;
                    for (int i = 0; i < n; i++) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            String err = plugin.plinko().testDrop(pp, "kplinko", risk, 1);
                            plugin.getLogger().info("[Plinko-Test] Kugel " + risk.label + ": " + (err == null ? "fällt" : err));
                        }, 1L + 8L * i);
                    }
                }
            }
            case "gluecksrad" -> {
                // /kubytest gluecksrad <Spieler>: Kamera-Spieler dreht das große Rad auf der Test-Bühne (ohne Wartezeit)
                Player wp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (wp != null) {
                    String err = plugin.wheel().testSpin(wp, "krad");
                    plugin.getLogger().info("[Glücksrad-Test] Dreh: " + (err == null ? "läuft" : err));
                }
            }
            case "gluecksrad-ui" -> {
                // /kubytest gluecksrad-ui <Spieler>: Dreh im Fenster (Bild über den Bildschirm), danach öffnet sich das Fenster
                Player wp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (wp != null) {
                    String err = plugin.wheel().testUiSpin(wp);
                    plugin.getLogger().info("[Glücksrad-Test] Fenster-Dreh: " + (err == null ? "läuft" : err));
                }
            }
            case "schliessen" -> {
                // /kubytest schliessen <Spieler>: offenes Fenster (Dialog) schließen
                Player wp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (wp != null) de.kuby.kubyplugins.ui.UiDialog.close(wp);
            }
            case "gluecksrad-fenster" -> {
                // /kubytest gluecksrad-fenster <Spieler>: nur das Fenster öffnen
                Player wp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (wp != null) plugin.wheel().open(wp);
            }
            case "roulette" -> {
                // /kubytest roulette <Spieler>: Spieler setzt (Rot + eine Zahl), Countdown springt auf 1 – ein ganzer Wurf
                Player rp = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (rp != null) {
                    plugin.casino().giveChips(rp.getUniqueId(), 5000);
                    // läuft gerade eine Runde der Test-Spieler (Kugel rollt, Ergebnis liegt), erst warten (höchstens 30 s)
                    Player fp = rp;
                    plugin.getServer().getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
                        int tries;

                        @Override
                        public void accept(org.bukkit.scheduler.BukkitTask task) {
                            if (!fp.isOnline()) {
                                task.cancel();
                                return;
                            }
                            if (!plugin.roulette().acceptsBets() && ++tries < 60) return;
                            task.cancel();
                            String e1 = plugin.roulette().betByName(fp, "rot", 100);
                            String e2 = plugin.roulette().betByName(fp, "17", 50);
                            String e3 = plugin.roulette().betByName(fp, "gerade", 100);
                            plugin.roulette().testFastForward();
                            plugin.getLogger().info("[Roulette-Test] Einsätze: " + (e1 == null ? "rot ok" : e1) + ", " + (e2 == null ? "17 ok" : e2)
                                    + ", " + (e3 == null ? "gerade ok" : e3) + "; gewartet " + tries / 2.0 + " s; Tableau-Treffer: "
                                    + plugin.roulette().testHits());
                        }
                    }, 0L, 10L);
                }
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

    private static Location arenaA, arenaB;

    /** Drei Lagen: Survival (frei), Hub im Kreativmodus (frei), Hub im Survival-Modus (Lobby-Sperre). */
    private void inventoryTest(Player p) {
        org.bukkit.GameMode before = p.getGameMode();
        Location back = p.getLocation();
        List<String> res = new ArrayList<>();
        boolean ok = true;
        Location surv = plugin.modes().spawnOf("survival"), hub = plugin.modes().spawnOf("hub");
        // teilt sich der Hub die Welt mit einem Modus (wie hier im Test: alles in "world"), gilt keine Lobby-Sperre
        String hubWorld = plugin.modes().worldOf("hub");
        boolean shared = false;
        var sec = plugin.getConfig().getConfigurationSection("modes");
        if (sec != null) for (String m : sec.getKeys(false)) if (!m.equals("hub") && hubWorld.equals(plugin.modes().worldOf(m))) shared = true;
        Object[][] cases = {{"Survival-Welt, Survival", surv, org.bukkit.GameMode.SURVIVAL, false},
                {"Survival-Welt, Kreativ", surv, org.bukkit.GameMode.CREATIVE, false},
                {"Hub, Kreativ", hub, org.bukkit.GameMode.CREATIVE, false},
                {shared ? "Hub teilt die Welt mit einem Modus, Survival (keine Sperre)" : "Hub, Survival (Lobby-Sperre)", hub,
                        org.bukkit.GameMode.SURVIVAL, !shared}};
        for (Object[] c : cases) {
            Location at = (Location) c[1];
            if (at == null) {
                res.add(c[0] + ": Welt fehlt");
                continue;
            }
            p.teleport(at);
            p.setGameMode((org.bukkit.GameMode) c[2]);
            p.getInventory().setItem(9, new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_PLANKS, 8));
            var view = p.getOpenInventory();
            var click = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                    9, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(click);
            var creative = new org.bukkit.event.inventory.InventoryCreativeEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR,
                    36, new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE, 64));
            Bukkit.getPluginManager().callEvent(creative);
            org.bukkit.entity.Item drop = p.getWorld().dropItem(p.getLocation(), new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_PLANKS));
            var dropEv = new org.bukkit.event.player.PlayerDropItemEvent(p, drop);
            Bukkit.getPluginManager().callEvent(dropEv);
            drop.remove();
            p.getInventory().setItem(9, null);
            boolean want = (Boolean) c[3];
            boolean got = click.isCancelled() || dropEv.isCancelled() || (p.getGameMode() == org.bukkit.GameMode.CREATIVE && creative.isCancelled());
            boolean pass = got == want;
            ok &= pass;
            res.add((pass ? "✅ " : "❌ ") + c[0] + ": Klick " + (click.isCancelled() ? "gesperrt" : "frei") + ", Kreativ-Menü "
                    + (creative.isCancelled() ? "gesperrt" : "frei") + ", Droppen " + (dropEv.isCancelled() ? "gesperrt" : "frei"));
        }
        p.setGameMode(before);
        p.teleport(back);
        plugin.getLogger().info("[Inventar-Test] " + (ok ? "OK" : "FEHLER") + " " + String.join(" | ", res));
    }
    private static Location camBj, camRl, camPk, camSlots, camSlotsKlein, camPlinko, camRennen, camCrash, camBjRot, camBjVio, camPkRot, camPkVio,
            camRad, camLeute;

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
            case "rennen" -> {
                // Pferderennen-Bildschirm (v2.05) an der Westwand, Blick nach Westen, ganzer Bildschirm im Bild
                if (camRennen == null) return;
                from = camRennen.clone().add(4.4, -0.55, 0);
                to = camRennen.clone().add(0, -0.1, 0);
            }
            case "rennen_nah" -> {
                // Zielgerade und Toto-Tafel (rechts aus Sicht der Zuschauer = Norden)
                if (camRennen == null) return;
                from = camRennen.clone().add(2.3, -0.7, -1.2);
                to = camRennen.clone().add(0, -0.1, -1.6);
            }
            case "gluecksrad" -> {
                // Glücksrad-Bühne (v2.07) von vorn aus Spielerhöhe: Rad, Schild, Podest und Gewinner-Tafel im Bild
                if (camRad == null) return;
                from = camRad.clone().add(-8.2, 2.1, 0.3);
                to = camRad.clone().add(0, 2.55, 0);
            }
            case "leute" -> {
                // Leute-Ecke (v2.07) von Osten: Gruppe, Trinkende, Security, Bank, Wand, Theke
                if (camLeute == null) return;
                from = camLeute.clone().add(0, 2.4, 0);
                to = camLeute.clone().add(-8.5, 0.9, -0.5);
            }
            case "leute_nah" -> {
                // Gruppe nah: Sprechblasen, Gesten, Gesichter
                if (camLeute == null) return;
                from = camLeute.clone().add(-4.2, 1.75, -3.2);
                to = camLeute.clone().add(-7.5, 1.35, -4.0);
            }
            case "leute_bar" -> {
                // Theke: Bestellen, Barkeeper, Losbude mit Strohhut
                if (camLeute == null) return;
                from = camLeute.clone().add(-0.8, 1.9, 3.0);
                to = camLeute.clone().add(-5.0, 1.2, 5.5);
            }
            case "leute_sitz" -> {
                // Bank, Boden, Wand: Sitzen und Anlehnen von nah
                if (camLeute == null) return;
                from = camLeute.clone().add(-7.0, 1.7, 0.5);
                to = camLeute.clone().add(-11.2, 0.8, 0.5);
            }
            case "leute_pech" -> {
                // Pechvogel und Streife
                if (camLeute == null) return;
                from = camLeute.clone().add(-7.5, 1.7, -5.0);
                to = camLeute.clone().add(-12.0, 0.7, -7.0);
            }
            case "gluecksrad_nah" -> {
                // Glücksrad nah: Nadel und Stifte oben am Rad
                if (camRad == null) return;
                from = camRad.clone().add(-2.6, 3.7, 0.9);
                to = camRad.clone().add(0, 3.75, 0);
            }
            case "plinko" -> {
                // Plinko-Brett (v2.05) von vorn aus Spielerhöhe, ganzes Brett im Bild
                if (camPlinko == null) return;
                from = camPlinko.clone().add(0.7, -0.75, 3.6);
                to = camPlinko.clone().add(0, -0.15, 0);
            }
            case "plinko_nah" -> {
                // unten: Fächer mit Faktoren, Bedienfeld (Risiko, Einsatz, BALL)
                if (camPlinko == null) return;
                from = camPlinko.clone().add(-0.5, -1.0, 1.9);
                to = camPlinko.clone().add(0.1, -1.55, 0);
            }
            case "slots_klein" -> {
                // kleiner Tiefsee-Automat (v2.05) schräg von vorn, Hocker im Bild
                if (camSlotsKlein == null) return;
                from = camSlotsKlein.clone().add(1.3, 1.9, 3.1);
                to = camSlotsKlein.clone().add(0, 1.15, 0);
            }
            case "bj_hocker" -> {
                // Nahaufnahme eines freien Hockers am violetten Tisch (Fußstütze mit Strebe am Mittelrohr)
                if (camBjVio == null) return;
                var g = plugin.blackjack().games().stream()
                        .filter(x -> x.table.tier() == de.kuby.kubyplugins.casino.TableTier.VIOLETT).findFirst().orElse(null);
                if (g == null) return;
                Location seat = g.table.seatLocation(2);
                org.bukkit.util.Vector d = seat.toVector().subtract(camBjVio.toVector()).setY(0).normalize();
                org.bukkit.util.Vector side = new org.bukkit.util.Vector(-d.getZ(), 0, d.getX());
                from = seat.clone().add(d.clone().multiply(1.3)).add(side.clone().multiply(0.9)).add(0, 0.75, 0);
                to = seat.clone().add(0, 0.3, 0);
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
            case "roulette", "roulette_rot", "roulette_violett" -> {
                // Las-Vegas-Tisch von der Spielerseite: Tableau, Kessel, Croupier und Tafel (rot/violett ab v2.07)
                var rg = where.equals("roulette_rot") ? plugin.rouletteHr() : where.equals("roulette_violett") ? plugin.rouletteVio() : plugin.roulette();
                if (!rg.hasTable()) return;
                var rt = rg.table();
                from = rt.local(-0.4, -3.3, 2.5);
                to = rt.local(-0.4, 0.1, 0.85);
            }
            case "roulette_rad" -> {
                // nah auf den Kessel (Rotor, Kugel, Rauten)
                var rt = plugin.roulette().table();
                from = rt.local(-2.3, -1.05, 1.75);
                to = rt.local(-2.3, 0.05, 0.9);
            }
            case "roulette_tableau" -> {
                // von oben auf das Tableau (Chips in Spielerfarbe, Marker auf der Gewinnzahl)
                var rt = plugin.roulette().table();
                from = rt.local(0.6, -1.7, 2.2);
                to = rt.local(0.4, -0.05, 0.86);
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
    /**
     * Leute-Ecke (v2.07) westlich hinter der Rennen-Wand: Rückwand, Bank (Stufen-Platten), Theke, dazu jede Tätigkeit
     * einmal, Personal-Varianten (neue Losbude mit Strohhut, Kasse 2, Barkeeper). Alle schauen nach Osten zur Kamera.
     */
    private void leute(org.bukkit.World w, int gx, double gy, int gz) {
        flatten(w, gx - 30, gx - 16, gz - 5, gz + 13, (int) gy);
        int y = (int) gy;
        for (int z = gz - 5; z <= gz + 13; z++) {
            for (int k = 0; k < 4; k++) w.getBlockAt(gx - 30, y + k, z).setType(org.bukkit.Material.DARK_OAK_PLANKS, false);
        }
        for (int z = gz + 2; z <= gz + 3; z++) w.getBlockAt(gx - 28, y, z).setType(org.bukkit.Material.DARK_OAK_SLAB, false);
        for (int z = gz + 7; z <= gz + 11; z++) w.getBlockAt(gx - 20, y, z).setType(org.bukkit.Material.POLISHED_BLACKSTONE_BRICKS, false);
        camLeute = new Location(w, gx - 16.5, gy, gz + 4.5);
        var pe = plugin.people();
        java.util.function.BiFunction<Double, Double, Location> at = (dx, dz) -> new Location(w, gx + dx, gy, gz + dz, -90f, 0f);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.GRUPPE, at.apply(-24.0, 0.5), "viktor", 3);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.TRINKEN, at.apply(-22.5, 4.5), "elena", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.TELEFON, at.apply(-25.5, 12.5), "lukas", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.BESTELLEN, at.apply(-21.2, 9.5), "sophie", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.WARTEN, at.apply(-21.5, -2.5), "mei", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.BANK, new Location(w, gx - 27.5, gy + 0.5, gz + 2.5, -90f, 0f), "frank", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.BODEN, at.apply(-26.5, 9.0), "nina", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.WAND, at.apply(-28.7, 6.5), "mateo", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.VERZWEIFELT, at.apply(-28.5, -2.5), "paul", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.ZUSCHAUER, at.apply(-18.5, 4.5), "amara", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.SECURITY, at.apply(-19.5, -3.5), "darnell", 0);
        pe.spawn(de.kuby.kubyplugins.casino.People.Act.STREIFE, at.apply(-27.5, -4.5), "svenja", 0);
        plugin.casinoNpc().spawn(de.kuby.kubyplugins.casino.CasinoNpc.Role.LOSBUDE, at.apply(-23.5, 12.5), "1");
        plugin.casinoNpc().spawn(de.kuby.kubyplugins.casino.CasinoNpc.Role.KASSE, at.apply(-28.0, 12.5), "2");
        plugin.casinoVip().barkeeperAt(new Location(w, gx - 18.5, gy, gz + 9.5, 90f, 0f), null);
        plugin.getLogger().info("[Casino-Test] Leute-Ecke: " + pe.count() + " animierte Figuren");
    }

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
        arenaA = new Location(w, gx - 15, gy - 1, gz - 14);
        arenaB = new Location(w, gx + 15, gy - 1, gz + 18);
        Location bjAt = new Location(w, gx + 0.5, gy, gz + 0.5);
        Location rlAt = new Location(w, gx + 9.5, gy, gz + 0.5);
        Location pkAt = new Location(w, gx - 9.5, gy, gz + 0.5, 180f, 0f);
        Location slotA = new Location(w, gx + 0.5, gy + 1.5, gz - 7.5);
        Location slotB = new Location(w, gx + 4.5, gy + 1.5, gz - 7.5);
        camBj = bjAt;
        camRl = rlAt;
        camPk = pkAt;
        camSlots = new Location(w, gx + 2.5, gy, gz - 7.5);
        Location slotC = new Location(w, gx - 3.5, gy + 1.5, gz - 7.5);
        // Plinko (v2.05): Wand aus Schwarzstein (6 breit, 6 hoch) hinten rechts, Brett davor, Blick nach +z
        for (int x = gx + 8; x <= gx + 13; x++) {
            for (int y = 0; y < 6; y++) w.getBlockAt(x, (int) gy + y, gz - 12).setType(org.bukkit.Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        camPlinko = new Location(w, gx + 11.0, gy + 0.75 + 2.45 * 0.8, gz - 11);
        // Pferderennen (v2.05): Wand an der Westkante (10 breit, 5 hoch), Bildschirm davor, Blick nach Osten. Nicht an der
        // Südseite: dort baut der Crash-Test später seine Wand (z + 14) genau vor die Kamera
        for (int z = gz + 3; z <= gz + 12; z++) {
            for (int y = 0; y < 5; y++) w.getBlockAt(gx - 15, (int) gy + y, z).setType(org.bukkit.Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        camRennen = new Location(w, gx - 14.0, gy + 0.9 + 1.95 * 0.7, gz + 8.0);
        camSlotsKlein = new Location(w, gx - 3.5, gy, gz - 7.5);
        // Glücksrad (v2.07): Bühne östlich hinter dem Roulette auf eigener Fläche (gut 7 breit, Rad 5,4 hoch), dunkle Wand
        // dahinter, Vorderseite nach Westen; die Kamera steht zwischen Roulette und Bühne
        flatten(w, gx + 16, gx + 27, gz - 6, gz + 9, (int) gy);
        for (int z = gz - 5; z <= gz + 8; z++) {
            for (int y = 0; y < 7; y++) w.getBlockAt(gx + 25, (int) gy + y, z).setType(org.bukkit.Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        camRad = new Location(w, gx + 21.5, gy, gz + 1.5);
        // Roulette rot und violett (v2.07): östlich hinter der Glücksrad-Wand auf eigener Fläche, Spielerseite im Süden
        flatten(w, gx + 27, gx + 40, gz - 7, gz + 10, (int) gy);
        Location rlRot = new Location(w, gx + 33.5, gy, gz - 2.5), rlVio = new Location(w, gx + 33.5, gy, gz + 6.5);
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
            // Kasse und Jukebox (für den Rundgang): an der Rückwand, Blick zu den Tischen (-z)
            if (plugin.casinoNpc().find(de.kuby.kubyplugins.casino.CasinoNpc.Role.KASSE, bjAt, 30).isEmpty()) {
                plugin.casinoNpc().spawn(de.kuby.kubyplugins.casino.CasinoNpc.Role.KASSE, new Location(w, gx - 12.5, gy, gz + 13.5, 180f, 0f));
                plugin.casinoNpc().spawn(de.kuby.kubyplugins.casino.CasinoNpc.Role.MUSIK, new Location(w, gx + 12.5, gy, gz + 13.5, 180f, 0f));
            }
            plugin.roulette().setTable(rlAt, 0f);
            plugin.rouletteHr().setTable(rlRot, 0f);
            plugin.rouletteVio().setTable(rlVio, 0f);
            plugin.poker().setTable(pkAt);
            // zwei neue Automaten freistehend (Hocker 1,6 davor, Blick nach +z zum Spieler)
            plugin.slots().testMachine("kgold", slotA, 0f, new Location(w, slotA.getX(), gy, slotA.getZ() + 1.6), "gold");
            plugin.slots().testMachine("kneon", slotB, 0f, new Location(w, slotB.getX(), gy, slotB.getZ() + 1.6), "neon");
            // kleine Ausführung (v2.05): halbe Breite, eigenes Thema Tiefsee (Muscheln, Fische, Dreizack)
            plugin.slots().testMachine("kozean", slotC, 0f, new Location(w, slotC.getX(), gy, slotC.getZ() + 1.6), "ozean",
                    1.35, de.kuby.kubyplugins.casino.SlotTheme.OZEAN);
            plugin.plinko().testBoard("kplinko", camPlinko, 0f, 0.8);
            plugin.race().testBoard(camRennen, -90f, 0.7);
            plugin.wheel().testBoard("krad", camRad, 90f, 0.9);
            leute(w, gx, gy, gz);
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

        // --- Exploit-Schutz (v1.97): Serie mit Mini-Einsätzen, kleine Gewinne, Tagesdeckel Goldene Zeit ---
        cp.testReset(id);
        plugin.casino().giveChips(id, 3_000_000);
        for (int i = 0; i < 3; i++) {
            cp.take(p, "blackjack", 10);
            cp.settle(id, "blackjack", 10, 20);
        }
        cp.take(p, "blackjack", 10_000);
        var big = cp.settle(id, "blackjack", 10_000, 20_000);
        check("Serie mit 10-Chip-Wetten aufgebaut: Bonus beim Großeinsatz nur auf 10 Chips", big.bonus() <= 10,
                "Serie " + (big.streak() - 1) + ", Einsatz 10.000, Bonus " + big.bonus() + " (ohne Schutz wären es 6.000)");
        cp.testReset(id);
        for (int i = 0; i < 3; i++) {
            cp.take(p, "crash", 100);
            cp.settle(id, "crash", 100, 101);
        }
        check("Kleine Gewinne (Crash x1,01) bauen keine Serie auf", cp.streak(id) == 0, "Serie " + cp.streak(id));
        cp.testReset(id);
        cp.testGolden = true;
        cp.take(p, "blackjack", 400_000);
        var g1 = cp.settle(id, "blackjack", 400_000, 800_000);
        cp.take(p, "blackjack", 400_000);
        var g2 = cp.settle(id, "blackjack", 400_000, 800_000);
        long cap = plugin.getConfig().getLong("casino.goldene-zeit.max-bonus-tag", 250_000);
        check("Goldene Zeit: Tagesdeckel " + cap + " Chips Bonus je Spieler", g1.bonus() <= cap && g1.bonus() + g2.bonus() <= cap + Math.round(400_000 * 0.2) + 1,
                "1. Gewinn Bonus " + g1.bonus() + ", 2. Gewinn Bonus " + g2.bonus());
        cp.testGolden = false;
        cp.testReset(id);

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
        crashScreenForTest();
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

    /** Crash-Bildschirm am Rand des Testgeländes (dunkle Wand, Bild zeigt zu den Tischen), Kamera-Punkt merken. */
    private void crashScreenForTest() {
        Location spawn = plugin.modes().spawnOf("survival");
        org.bukkit.World w = spawn.getWorld();
        Location scr;
        if (camBj != null) {
            int bx = camBj.getBlockX(), by = camBj.getBlockY(), bz = camBj.getBlockZ() + 14;
            for (int x = bx - 4; x <= bx + 4; x++)
                for (int y = by; y <= by + 5; y++) w.getBlockAt(x, y, bz).setType(org.bukkit.Material.BLACK_CONCRETE, false);
            scr = new Location(w, bx + 0.5, by + 2.5, bz - 0.01);
        } else {
            scr = new Location(w, spawn.getBlockX() + 0.5, w.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ() + 18) + 3.0,
                    spawn.getBlockZ() + 18.0);
        }
        if (camCrash == null || !camCrash.equals(scr)) plugin.crash().testScreen(scr, 180f);
        camCrash = scr;
    }

    /**
     * /kubytest crash &lt;Spieler&gt;: Crash-Runde für die Bilder aus dem echten Client (der Regeltest läuft dort ohne Bots
     * nicht): Bildschirm aufbauen, Spieler setzt 100, Runde steigt bis x7 und crasht; Kurs jede Sekunde ins Log.
     */
    /** Ein Sitz für den Aufstehen-Test: unsichtbarer Marker-Ständer wie bei /sit, Möbeln und Casino-Hockern. */
    private org.bukkit.entity.ArmorStand testSeat(Location l, org.bukkit.NamespacedKey key, String value,
                                                  org.bukkit.NamespacedKey floorKey, double floor) {
        return l.getWorld().spawn(l, org.bukkit.entity.ArmorStand.class, e -> {
            e.setInvisible(true);
            e.setMarker(true);
            e.setGravity(false);
            e.setSmall(true);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, value);
            if (floorKey != null) e.getPersistentDataContainer().set(floorKey, org.bukkit.persistence.PersistentDataType.DOUBLE, floor);
        });
    }

    private void standUpTest(Player p) {
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) p.setGameMode(org.bukkit.GameMode.ADVENTURE);
        p.setFlying(false);
        // freie Stelle im Testgelände (zwischen Slots und Rand), Boden = Oberkante der eingeebneten Fläche
        Location base = arenaA.clone().add(13.5, 1, 1.5);
        base.setYaw(0f);
        base.setPitch(20f);
        p.teleport(base);
        List<String> res = new ArrayList<>();
        boolean[] bad = {false};
        java.util.function.BiConsumer<String, Location> check = (name, want) -> {
            Location at = p.getLocation();
            org.bukkit.util.BoundingBox box = p.getBoundingBox().clone().expand(-0.02, -0.02, -0.02);
            boolean stuck = p.getWorld().hasCollisionsIn(box);
            boolean ok = !p.isInsideVehicle() && !stuck && Math.abs(at.getY() - want.getY()) < 0.01
                    && (want.getX() != want.getX() || Math.hypot(at.getX() - want.getX(), at.getZ() - want.getZ()) < 0.2);
            if (!ok) bad[0] = true;
            res.add(name + (ok ? " ok" : " FALSCH") + String.format(java.util.Locale.ROOT, " (Füße y=%.3f, Boden y=%.3f%s)",
                    at.getY(), want.getY(), stuck ? ", steckt im Block" : ""));
        };
        org.bukkit.plugin.Plugin surv = Bukkit.getPluginManager().getPlugin("KubySurvival");
        var sched = plugin.getServer().getScheduler();
        // 1) /sit: Ständer 0,1 unter den Füßen (früher: nach dem Aufstehen 0,1 im Boden)
        sched.runTaskLater(plugin, () -> {
            if (surv == null || !surv.isEnabled()) {
                res.add("sit übersprungen (KubySurvival fehlt)");
                return;
            }
            Location at = p.getLocation();
            var a = testSeat(at.clone().add(0, -0.1, 0), new org.bukkit.NamespacedKey(surv, "seat"), "boden",
                    new org.bukkit.NamespacedKey(surv, "seat_floor"), at.getY());
            a.addPassenger(p);
            sched.runTaskLater(plugin, () -> {
                if (!p.isInsideVehicle()) res.add("sit: Spieler saß nicht");
                p.leaveVehicle();
                Location want = at.clone();
                sched.runTaskLater(plugin, () -> check.accept("sit", want), 6L);
            }, 20L);
        }, 10L);
        // 2) Blackjack-Hocker: Ständer 0,58 über dem Boden, Aufstehen hinter den Hocker (Blick +Z, also nach -Z)
        sched.runTaskLater(plugin, () -> {
            Location stool = base.clone().add(3, 0, 0);
            p.teleport(stool);
            Location st = stool.clone().add(0, 0.58, 0);
            st.setYaw(0f);
            var a = testSeat(st, new org.bukkit.NamespacedKey(plugin, "bj_table"), "aufstehtest:vehicle", null, 0);
            sched.runTaskLater(plugin, () -> {
                a.addPassenger(p);
                sched.runTaskLater(plugin, () -> {
                    if (!p.isInsideVehicle()) res.add("hocker: Spieler saß nicht");
                    p.leaveVehicle();
                    sched.runTaskLater(plugin, () -> {
                        a.remove();
                        check.accept("hocker", stool.clone().add(0, 0, -0.7));
                        plugin.getLogger().info("[Aufstehen-Test] " + (bad[0] ? "FEHLER " : "OK ") + String.join("; ", res));
                    }, 6L);
                }, 20L);
            }, 4L);
        }, 50L);
    }

    private void crashShow(Player p) {
        crashScreenForTest();
        plugin.casino().giveChips(p.getUniqueId(), 5000);
        plugin.crash().testCrashAt = 7.0;
        String err = plugin.crash().bet(p, 100);
        plugin.getLogger().info("[Crash-Test] Einsatz: " + (err == null ? "ok" : err));
        final int[] t = {0};
        final double[] last = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            var ph = plugin.crash().phase();
            t[0]++;
            if (ph == de.kuby.kubyplugins.casino.CrashGame.Phase.RUNNING) {
                last[0] = plugin.crash().current();
                if (t[0] % 20 == 0) plugin.getLogger().info("[Crash-Test] Kurs x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(last[0]));
            } else if (last[0] > 0) {
                task.cancel();
                plugin.getLogger().info("[Crash-Test] gecrasht bei x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(last[0]));
            } else if (t[0] > 20 * 60) {
                task.cancel();
                plugin.getLogger().info("[Crash-Test] Runde startete nicht");
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
        // --- Pferderennen (v2.05): Sieg auf #1, Platz auf #2 (je 100), Countdown auf 1 s, nach dem Rennen prüfen:
        // Auszahlung = Einsatz × Quote für jede gewonnene Wette (Sieger / unter den ersten zwei) ---
        plugin.race().testLog = true;
        var rf = plugin.race().field();
        String re1 = plugin.race().testBet(p, 0, false, 100);
        String re2 = plugin.race().testBet(p, 1, true, 100);
        check("Pferderennen: Sieg- und Platzwette angenommen", re1 == null && re2 == null, re1 != null ? re1 : re2);
        plugin.race().testFastForward();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var ro = plugin.casinoPlay().testLast.get(p.getUniqueId() + "|rennen");
            int[] order = plugin.race().lastOrder();
            long want = -1;
            if (order != null) {
                want = (order[0] == 0 ? Math.round(100 * rf.winOdds()[0]) : 0)
                        + (order[0] == 1 || order[1] == 1 ? Math.round(100 * rf.placeOdds()[1]) : 0);
            }
            check("Pferderennen: Rennen gelaufen und abgerechnet (Einsatz × Quote)", ro != null && order != null && ro.stake() == 200
                            && ro.gross() == want && ro.payout() >= ro.gross(),
                    ro == null || order == null ? "keine Abrechnung nach 25 s" : "Einlauf #" + (order[0] + 1) + " vor #" + (order[1] + 1)
                            + ", Quoten Sieg #1 " + de.kuby.kubyplugins.casino.HorseRace.fmt(rf.winOdds()[0]) + " / Platz #2 "
                            + de.kuby.kubyplugins.casino.HorseRace.fmt(rf.placeOdds()[1]) + ", Spiel zahlt " + ro.gross() + " (erwartet " + want
                            + "), ausgezahlt " + ro.payout());
        }, 500L);
        // --- Plinko (v2.05): eine Kugel am Test-Brett, Risiko Hoch, Einsatz 100; Auszahlung = 100 × Faktor eines Fachs ---
        plugin.plinko().testLog = true;
        String perr = plugin.plinko().testDrop(p, "kplinko", de.kuby.kubyplugins.casino.Plinko.Risk.HOCH, 1);
        check("Plinko: Kugel am Test-Brett abgeworfen", perr == null, perr);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var po = plugin.casinoPlay().testLast.get(p.getUniqueId() + "|plinko");
            boolean known = false;
            if (po != null) {
                for (int sl = 0; sl < de.kuby.kubyplugins.casino.Plinko.SLOTS; sl++) {
                    if (po.gross() == Math.round(po.stake() * de.kuby.kubyplugins.casino.Plinko.Risk.HOCH.mult(sl))) known = true;
                }
            }
            check("Plinko: Kugel liegt im Fach und ist abgerechnet (Auszahlung = Einsatz × Faktor)", po != null && known && po.payout() >= po.gross(),
                    po == null ? "keine Abrechnung nach 6 s" : "Einsatz " + po.stake() + ", Spiel zahlt " + po.gross() + ", ausgezahlt " + po.payout());
        }, 120L);
        // --- Glücksrad (v2.07): Dreh ohne Wartezeit am Test-Rad; das Rad hält auf dem gezogenen Feld, Gewinn kommt an ---
        plugin.wheel().testLast = null;
        long chipsBefore = plugin.casino().chips(p.getUniqueId());
        String werr = plugin.wheel().testSpin(p, "krad");
        check("Glücksrad: Dreh am Test-Rad gestartet", werr == null, werr);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String wl = plugin.wheel().testLast;
            String[] wp = wl == null ? new String[0] : wl.split(";");
            boolean same = wp.length == 4 && wp[0].equals(wp[1]);
            check("Glücksrad: Rad hält auf dem gezogenen Feld, Gewinn ausgezahlt", same && "ok".equals(wp[3]),
                    wl == null ? "kein Ergebnis nach 8 s" : "gezogen Feld " + wp[0] + ", am Zeiger Feld " + wp[1] + ", Gewinn " + wp[2] + " (" + wp[3]
                            + "), Chips vorher " + chipsBefore + ", nachher " + plugin.casino().chips(p.getUniqueId()));
        }, 160L);
        // Abrechnungs-Protokoll (testLast) erst nach der Rennprüfung abschalten: das Rennen wird nach gut 20 s abgerechnet
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.casinoPlay().testLog = false, 540L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.casinoPlay().testGolden = null;
            CASINO.remove(p.getUniqueId());
            plugin.getLogger().info("[Casino-Regel] Ergebnis: " + ruleOk + " OK, " + ruleBad + " Fehler");
        }, 200L);
    }
}
