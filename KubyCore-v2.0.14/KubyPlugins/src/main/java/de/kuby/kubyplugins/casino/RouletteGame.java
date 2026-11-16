package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Roulette (europäisch, 0 bis 36) am Las-Vegas-Tisch (RouletteTable, v1.98). Der erste Einsatz startet die Runde, nach
 * dem Countdown ruft der Croupier „Nichts geht mehr“, stößt den Rotor an und wirft die Kugel; sie landet nach 7,5 s im
 * Fach. Gesetzt wird direkt auf dem Tableau (Rechtsklick legt den gewählten Chip, Linksklick nimmt die eigenen Chips
 * zurück, Schleichen + Rechtsklick öffnet den Dialog) oder über den Dialog (/roulette, Croupier, Kessel). Jeder Spieler
 * hat in der Runde seine eigene Chip-Farbe. Offene Einsätze stehen in roulette-tisch.yml und kommen nach einem Absturz
 * zurück.
 */
public final class RouletteGame implements org.bukkit.event.Listener {

    public enum Kind {
        ROT("Rot", 2), SCHWARZ("Schwarz", 2), GERADE("Gerade", 2), UNGERADE("Ungerade", 2), NIEDRIG("1–18", 2),
        HOCH("19–36", 2), DUTZEND("Dutzend", 3), KOLONNE("Kolonne", 3), ZAHL("Zahl", 32); // 31:1 statt 35:1, weil Blitz-Zahlen x2 bis x5 zahlen (wie Lightning Roulette); Hausvorteil ~3 %

        public final String label;
        /** Auszahlung inklusive Einsatz (Rot = 2, also 1:1). */
        public final int multiplier;

        Kind(String label, int multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }
    }

    public record Bet(UUID player, String name, Kind kind, int param, long amount) {
        public boolean wins(int n) {
            return switch (kind) {
                case ROT -> n != 0 && RouletteTable.red(n);
                case SCHWARZ -> n != 0 && !RouletteTable.red(n);
                case GERADE -> n != 0 && n % 2 == 0;
                case UNGERADE -> n % 2 == 1;
                case NIEDRIG -> n >= 1 && n <= 18;
                case HOCH -> n >= 19;
                case DUTZEND -> n != 0 && (n - 1) / 12 + 1 == param;
                case KOLONNE -> n != 0 && (n - 1) % 3 + 1 == param;
                case ZAHL -> n == param;
            };
        }

        public String spot() {
            return switch (kind) {
                case ZAHL -> "zahl";
                case DUTZEND -> "dutzend" + param;
                case KOLONNE -> "kolonne" + param;
                default -> kind.name().toLowerCase();
            };
        }

        public String label() {
            return switch (kind) {
                case ZAHL -> param == 0 ? "Grün · 0" : "Zahl " + param;
                case DUTZEND -> param + ". Dutzend";
                case KOLONNE -> param + ". Kolonne";
                default -> kind.label;
            };
        }

        public boolean same(Bet o) {
            return o.kind == kind && o.param == param;
        }
    }

    private enum Phase { IDLE, BETTING, SPINNING, RESULT }

    /** Auswahl je Spieler: Chip, eigener Betrag, Modus, letzte Runde. */
    private static final class Session {
        int chip = 2;
        long custom;
        boolean expert;
        List<Bet> last = new ArrayList<>();
    }

    private static final SecureRandom RNG = new SecureRandom();

    private final KubyPlugins plugin;
    private final File file;
    private final RouletteTable table;
    private final List<Bet> bets = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Phase phase = Phase.IDLE;
    private int countdown;
    private int lastResult = -1;
    /** Blitz-Zahlen dieser Runde: Zahl -> Multiplikator auf Volltreffer (Zahl-Einsätze). */
    private final Map<Integer, Integer> blitz = new LinkedHashMap<>();
    /** Wer die Kugel werfen darf (größter Einsatz) und wie lange noch (Sekunden). */
    private UUID thrower;
    private int throwWait;
    private String resultLine = "";
    private BukkitTask timer;
    private BukkitTask spin;
    private BukkitTask hoverTask;
    /** Chip-Farbe je Spieler in dieser Runde (Index in RouletteTable.COLORS). */
    private final Map<UUID, Integer> colors = new LinkedHashMap<>();
    /** Feld unter dem Fadenkreuz je Spieler (für Hinweis und Rahmen). */
    private final Map<UUID, RouletteTable.Spot> hovering = new HashMap<>();
    /** Läuft der Kessel gerade (vom Anstoßen bis die Kugel liegt)? */
    private boolean wheelBusy;
    /** Tisch aus der Datei, solange die Welt noch nicht geladen ist. */
    private String tableRaw;

    /** Der Roulette-Tisch (für den Rundgang). */
    public RouletteTable table() {
        return table;
    }

    public RouletteGame(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "roulette-tisch.yml");
        this.table = new RouletteTable(plugin, new NamespacedKey(plugin, "roulette_table"));
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String t = yml.getString("tisch");
        tableRaw = t;
        boolean legacy = false;
        if (t != null) {
            String[] p = t.split(";");
            World w = p.length >= 5 ? Bukkit.getWorld(p[0]) : null;
            if (w != null) {
                try {
                    double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]), z = Double.parseDouble(p[3]);
                    if (p.length >= 7) {
                        // alter Tisch (bis v1.97): Mitte auf einer Block-Tischplatte, Boden etwa einen Block tiefer
                        table.geometry(new Location(w, x, y - 1, z), Float.parseFloat(p[6]));
                        legacy = true;
                    } else {
                        table.geometry(new Location(w, x, y, z), Float.parseFloat(p[4]));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (legacy) {
            plugin.getLogger().warning("Roulette: neuer Las-Vegas-Tisch (v1.98) steht jetzt an der Stelle des alten Tischs. "
                    + "Alte Block-Tischplatte entfernen und mit /casino roulette-tisch neu ausrichten.");
        }
        for (Object o : yml.getList("verlauf", List.of())) {
            if (o instanceof Number n) history.add(n.intValue());
        }
        lastResult = history.isEmpty() ? -1 : history.get(0);
        // offene Einsätze nach einem Absturz zurückgeben
        for (String line : yml.getStringList("offen")) {
            String[] p = line.split(";");
            try {
                plugin.casinoPlay().refund(UUID.fromString(p[0]), "roulette", Long.parseLong(p[1]));
            } catch (RuntimeException ignored) {
            }
        }
        if (!yml.getStringList("offen").isEmpty()) save();
    }

    /** Baut den Tisch auf, sobald sein Chunk geladen ist (die Anzeigen werden nicht gespeichert). */
    public void respawn() {
        if (timer == null) timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Location c = table.center();
        if (c == null || c.getWorld() == null) return;
        if (!c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) return;
        table.build();
        for (Bet b : bets) table.chip(spotOf(b), chipColor(b.player()));
        table.history(history);
        if (phase == Phase.BETTING && throwWait > 0) table.blitz(blitz);
        updateTable();
        if (hoverTask == null) hoverTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::hoverTick, 5L, 3L);
    }

    private boolean rebuildQueued;

    @org.bukkit.event.EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        Location c = table.center();
        if (c == null || rebuildQueued || !e.getWorld().equals(c.getWorld())) return;
        double r = 5;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        if (cx < ((int) Math.floor(c.getX() - r) >> 4) || cx > ((int) Math.floor(c.getX() + r) >> 4)) return;
        if (cz < ((int) Math.floor(c.getZ() - r) >> 4) || cz > ((int) Math.floor(c.getZ() + r) >> 4)) return;
        rebuildQueued = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            rebuildQueued = false;
            if (phase != Phase.SPINNING && !wheelBusy) respawn();
        }, 5L);
    }

    public boolean hasTable() {
        return table.center() != null;
    }

    /** /casino roulette-tisch: Tischmitte am Boden, yaw = Blick von der Spielerseite zum Croupier. */
    public void setTable(Location floorCenter, float yaw) {
        abortRound();
        table.removeAll();
        table.geometry(floorCenter, yaw);
        tableRaw = null;
        save();
        respawn();
    }

    public void removeTable() {
        abortRound();
        table.removeAll();
    }

    /** Tisch wird mitten in der Runde abgebaut: alle Einsätze zurück, Runde neu. */
    private void abortRound() {
        if (bets.isEmpty() && !wheelBusy && phase != Phase.SPINNING) return;
        refundAll();
        wheelBusy = false;
        throwWait = 0;
        phase = Phase.IDLE;
        colors.clear();
    }

    public void shutdown() {
        refundAll();
        if (timer != null) timer.cancel();
        if (spin != null) spin.cancel();
        if (hoverTask != null) hoverTask.cancel();
        table.removeAll();
    }

    private void refundAll() {
        for (Bet b : bets) plugin.casinoPlay().refund(b.player(), "roulette", b.amount());
        bets.clear();
        save();
    }

    /** True, wenn der Block unter dem Tisch liegt (Grundfläche des Tischs). */
    public boolean isTable(Location block) {
        return table.contains(block);
    }

    /** /casino setzen &lt;Einsatz&gt; &lt;rot|schwarz|gruen|gerade|ungerade|niedrig|hoch|0-36&gt; */
    public String betByName(Player p, String choice, long amount) {
        String c = choice.toLowerCase();
        Kind kind;
        int param = 0;
        switch (c) {
            case "rot", "red" -> kind = Kind.ROT;
            case "schwarz", "black" -> kind = Kind.SCHWARZ;
            case "grün", "gruen", "green", "null" -> kind = Kind.ZAHL;
            case "gerade" -> kind = Kind.GERADE;
            case "ungerade" -> kind = Kind.UNGERADE;
            case "niedrig", "1-18" -> kind = Kind.NIEDRIG;
            case "hoch", "19-36" -> kind = Kind.HOCH;
            default -> {
                try {
                    param = Integer.parseInt(c);
                } catch (NumberFormatException e) {
                    return "Wähle rot, schwarz, gruen, gerade, ungerade, niedrig, hoch oder eine Zahl von 0 bis 36.";
                }
                if (param < 0 || param > 36) return "Zahlen gehen von 0 bis 36.";
                kind = Kind.ZAHL;
            }
        }
        return place(p, kind, param, amount);
    }

    public boolean near(Location l) {
        Location c = table.center();
        if (c == null || l.getWorld() == null || !l.getWorld().equals(c.getWorld())) return false;
        double r = plugin.getConfig().getDouble("casino.roulette.range", 12);
        return l.distanceSquared(c) <= r * r;
    }

    private Session session(UUID id) {
        return sessions.computeIfAbsent(id, k -> new Session());
    }

    // =================================================================================================================
    // Einsätze
    // =================================================================================================================

    private long minBet() {
        return plugin.getConfig().getLong("casino.roulette.min-bet", 10);
    }

    private long maxRound() {
        return plugin.getConfig().getLong("casino.roulette.max-round", 100_000);
    }

    private long placed(UUID id) {
        long s = 0;
        for (Bet b : bets) if (b.player().equals(id)) s += b.amount();
        return s;
    }

    /** Betrag des gewählten Chips (ALLES = alle Chips bis zur Rundengrenze). */
    private long chipValue(Player p) {
        Session s = session(p.getUniqueId());
        if (s.chip == 6) {
            long left = plugin.casinoPlay().maxFor(p.getUniqueId(), maxRound()) - placed(p.getUniqueId());
            return Math.min(plugin.casino().chips(p.getUniqueId()), left);
        }
        if (s.chip == 5) return s.custom;
        return CasinoScreens.CHIPS.get(s.chip).value();
    }

    /** Setzt den gewählten Chip auf ein Feld. Null bei Erfolg, sonst Meldung. */
    public String bet(Player p, Kind kind, int param) {
        long amount = chipValue(p);
        if (amount <= 0) return session(p.getUniqueId()).chip == 5 ? "Gib zuerst einen eigenen Betrag ein." : "Du hast keine Chips.";
        return place(p, kind, param, amount);
    }

    private String place(Player p, Kind kind, int param, long amount) {
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (!near(p.getLocation())) return "Geh näher an den Roulette-Tisch.";
        if (phase == Phase.SPINNING || phase == Phase.RESULT) return "Die Kugel rollt – gleich geht's weiter.";
        if (throwWait > 0) return "Nichts geht mehr – gleich rollt die Kugel.";
        boolean jw = plugin.casinoPlay().cur(p.getUniqueId(), "roulette") == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS;
        if (!jw && amount < minBet()) return "Mindesteinsatz " + CasinoText.num(minBet()) + " Chips.";
        long maxR = plugin.casinoPlay().maxFor(p.getUniqueId(), maxRound());
        if (!jw && placed(p.getUniqueId()) + amount > maxR) {
            return "Höchstens " + CasinoText.num(maxR) + " Chips pro Runde.";
        }
        String err = plugin.casinoPlay().take(p, "roulette", amount);
        if (err != null) return err;
        Bet bet = new Bet(p.getUniqueId(), p.getName(), kind, param, amount);
        bets.add(bet);
        table.chip(spotOf(bet), chipColor(p.getUniqueId()));
        Location at = table.spotCenter(spotOf(bet));
        if (at != null) at.getWorld().playSound(at, "kuby:casino.chip", org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.95f + RNG.nextFloat() * 0.1f);
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            countdown = (int) plugin.getConfig().getLong("casino.roulette.wait-seconds", 20);
            nearby(15).forEach(q -> CasinoText.send(q, "<white>" + Msg.escape(p.getName()) + "</white> setzt – die Runde startet in "
                    + countdown + " s. <yellow>/roulette"));
        }
        save();
        updateTable();
        return null;
    }

    public String repeat(Player p) {
        Session s = session(p.getUniqueId());
        if (s.last.isEmpty()) return "Noch keine letzte Runde.";
        long sum = 0;
        for (Bet b : s.last) sum += b.amount();
        if (plugin.casinoPlay().balance(p.getUniqueId(), "roulette") < sum) return "Für alle Einsätze der letzten Runde reicht dein Guthaben nicht.";
        for (Bet b : s.last) {
            String err = place(p, b.kind(), b.param(), b.amount());
            if (err != null) return err;
        }
        return null;
    }

    public String clear(Player p) {
        if (phase == Phase.SPINNING || phase == Phase.RESULT || throwWait > 0) return "Die Kugel rollt schon.";
        long back = 0;
        for (Bet b : new ArrayList<>(bets)) {
            if (b.player().equals(p.getUniqueId())) {
                bets.remove(b);
                back += b.amount();
            }
        }
        if (back == 0) return "Du hast in dieser Runde nichts gesetzt.";
        plugin.casinoPlay().refund(p.getUniqueId(), "roulette", back);
        table.clearChips();
        for (Bet b : bets) table.chip(spotOf(b), chipColor(b.player()));
        if (bets.isEmpty()) {
            phase = Phase.IDLE;
            colors.clear();
        }
        save();
        updateTable();
        CasinoText.send(p, "Einsätze zurückgenommen: <#F2C94C>+" + plugin.casinoPlay().unit(p.getUniqueId(), "roulette", back));
        return null;
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    private void tick() {
        DealerAnim croupier = table.croupier();
        if (croupier != null) {
            List<Location> eyes = new ArrayList<>();
            for (Player p : nearby(8)) eyes.add(p.getEyeLocation());
            croupier.idle(eyes);
        }
        if (phase != Phase.BETTING) return;
        if (throwWait > 0) {
            if (--throwWait <= 0) {
                Player t = thrower == null ? null : Bukkit.getPlayer(thrower);
                if (t != null) CasinoText.send(t, "Der Croupier wirft für dich.");
                startSpin();
            }
            return;
        }
        countdown--;
        if (countdown <= 0) {
            rienNeVaPlus();
            return;
        }
        if (countdown == 4 && croupier != null) croupier.sweep();   // „Letzte Einsätze, bitte!“
        Location c = table.layoutCenter();
        if (c != null && (countdown <= 5 || countdown % 5 == 0)) {
            c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, countdown <= 5 ? 1.0f + (5 - countdown) * 0.18f : 1.2f);
        }
        for (Player p : bettors()) {
            if (hovering.containsKey(p.getUniqueId())) continue;   // der Feld-Hinweis hat Vorrang
            String col = countdown <= 5 ? "<#FF6B6B>" : "<white>";
            p.sendActionBar(Msg.parse("<gray>Roulette · Einsatz <#F2C94C>" + plugin.casinoPlay().unit(p.getUniqueId(), "roulette", placed(p.getUniqueId()))
                    + "</#F2C94C> · Runde in " + col + countdown + " s"
                    + (plugin.casinoPlay().golden() ? " <gold>· ✦ Gewinne x" + de.kuby.kubyplugins.service.CasinoPlay.fmt(plugin.casinoPlay().goldenFactor()) : "")));
        }
        updateTable();
    }

    /** "Nichts geht mehr": Glocke, Blitz-Zahlen, und wer am meisten gesetzt hat, darf die Kugel werfen. */
    private void rienNeVaPlus() {
        Location c = table.layoutCenter();
        if (table.croupier() != null) table.croupier().sweep();
        if (c != null) {
            c.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 0.9f, 1.2f);
            c.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 0.6f, 1.6f);
        }
        // Blitz-Zahlen: 1 bis 3 zufällige Zahlen, Volltreffer darauf zahlen x2 bis x5 mehr
        blitz.clear();
        int n = 1 + RNG.nextInt(3);
        while (blitz.size() < n) blitz.put(RNG.nextInt(37), new int[]{2, 2, 3, 3, 4, 5}[RNG.nextInt(6)]);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : blitz.entrySet()) {
            if (sb.length() > 0) sb.append("<gray>, ");
            sb.append(e.getKey() == 0 ? "<#3FD55C>" : RouletteTable.red(e.getKey()) ? "<#FF5A5A>" : "<#D8D8D8>").append("<b>").append(e.getKey()).append("</b> <yellow>x").append(e.getValue());
        }
        String blitzLine = sb.toString();
        if (c != null) {
            c.getWorld().playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.25f, 1.6f);
            de.kuby.kubyplugins.util.Fx.particle(plugin, c.clone().add(0, 0.5, 0), org.bukkit.Particle.ELECTRIC_SPARK, 40, 1.6, 0.2, 0.6, 0.05);
        }
        table.blitz(blitz);
        table.center(centerText("<#FFE070><b>⚡ BLITZ-ZAHLEN</b>", blitzLine));
        long top = 0;
        thrower = null;
        Map<UUID, Long> per = new LinkedHashMap<>();
        for (Bet b : bets) per.merge(b.player(), b.amount(), Long::sum);
        for (Map.Entry<UUID, Long> e : per.entrySet()) if (e.getValue() > top) {
            top = e.getValue();
            thrower = e.getKey();
        }
        for (Player p : nearby(15)) {
            p.showTitle(Title.title(Msg.parse("<gold><b>Nichts geht mehr!</b>"),
                    Msg.parse("<#FFE070>⚡ " + blitzLine), Title.Times.times(
                            java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        }
        Player t = thrower == null ? null : Bukkit.getPlayer(thrower);
        if (t != null) {
            throwWait = 4;
            t.sendMessage(Msg.parse(CasinoText.PREFIX + "<white>Du hast am meisten gesetzt: "
                    + "<click:run_command:'/casino werfen'><hover:show_text:'Kugel jetzt werfen'><green><b>[KUGEL WERFEN]</b></green></hover></click>"));
            t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::startSpin, 30L);
            throwWait = 0;
            phase = Phase.SPINNING;
        }
    }

    /** /casino werfen: der Spieler mit dem größten Einsatz wirft die Kugel selbst. */
    public String throwBall(Player p) {
        if (phase != Phase.BETTING || throwWait <= 0) return "Gerade gibt es keine Kugel zu werfen.";
        if (!p.getUniqueId().equals(thrower)) return "Die Kugel wirft, wer am meisten gesetzt hat.";
        throwWait = 0;
        Location c = table.wheel();
        if (c != null) {
            c.getWorld().playSound(c, Sound.ENTITY_SNOWBALL_THROW, 0.5f, 1.4f);
            for (Player q : nearby(15)) q.sendActionBar(Msg.parse("<white>" + Msg.escape(p.getName()) + " <gray>wirft die Kugel!"));
        }
        startSpin();
        return null;
    }

    private void startSpin() {
        if (spin != null || wheelBusy || phase == Phase.RESULT) return;
        throwWait = 0;
        phase = Phase.SPINNING;
        wheelBusy = true;
        int result = RNG.nextInt(37);
        updateTable();
        if (table.ready()) {
            // Croupier stößt den Rotor an und wirft die Kugel; sie liegt nach T_LAND Ticks im Fach
            table.spin(result, () -> finish(result), () -> {
                wheelBusy = false;
                if (!table.ready()) respawn();
            });
        } else {
            // Tisch gerade nicht geladen: ohne Kessel nach 2 s auswerten
            spin = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                spin = null;
                wheelBusy = false;
                finish(result);
            }, 40L);
        }
    }

    private void finish(int result) {
        if (phase == Phase.RESULT) return;
        phase = Phase.RESULT;
        lastResult = result;
        List<Bet> round = new ArrayList<>(bets);
        if (plugin.casinoPlay().testLog) plugin.getLogger().info("[Roulette] Kugel liegt auf " + result + " (" + round.size() + " Einsätze)");
        history.add(0, result);
        while (history.size() > 100) history.remove(history.size() - 1);
        // Gewinne je Spieler zusammenfassen
        Map<UUID, long[]> sum = new LinkedHashMap<>();
        Map<UUID, de.kuby.kubyplugins.service.CasinoPlay.Outcome> outcomes = new LinkedHashMap<>();
        Map<UUID, List<Bet>> byPlayer = new LinkedHashMap<>();
        for (Bet b : bets) {
            long paid = b.wins(result) ? b.amount() * b.kind().multiplier : 0;
            if (paid > 0 && b.kind() == Kind.ZAHL && blitz.containsKey(result)) paid *= blitz.get(result);
            long[] s = sum.computeIfAbsent(b.player(), k -> new long[2]);
            s[0] += b.amount();
            s[1] += paid;
            byPlayer.computeIfAbsent(b.player(), k -> new ArrayList<>()).add(b);
        }
        long paidTotal = 0;
        int winners = 0;
        Location fx = table.layoutCenter() == null ? null : table.layoutCenter().add(0, 0.6, 0);
        String colorName = result == 0 ? "Grün" : RouletteTable.red(result) ? "Rot" : "Schwarz";
        String colorTag = result == 0 ? "#3FD55C" : RouletteTable.red(result) ? "#FF5A5A" : "#D8D8D8";
        for (Map.Entry<UUID, long[]> e : sum.entrySet()) {
            long staked = e.getValue()[0];
            de.kuby.kubyplugins.service.CasinoPlay.Outcome o = plugin.casinoPlay().settle(e.getKey(), "roulette", staked, e.getValue()[1]);
            long paid = o.payout();
            long net = paid - staked;
            paidTotal += paid;
            if (paid > 0) winners++;
            outcomes.put(e.getKey(), o);
            session(e.getKey()).last = new ArrayList<>(byPlayer.get(e.getKey()));
            CasinoFx.Result r = net > 0 ? (byPlayer.get(e.getKey()).stream().anyMatch(b -> b.kind() == Kind.ZAHL && b.wins(result))
                    ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN) : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE;
            CasinoFx.show(plugin, e.getKey(), fx, r, net);
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                String unitWin = de.kuby.kubyplugins.service.CasinoPlay.unit(o.cur(), paid);
                String bl = de.kuby.kubyplugins.service.CasinoPlay.bonusLine(o);
                CasinoText.send(p, "Die Kugel liegt auf <" + colorTag + "><b>" + result + " " + colorName + "</b></" + colorTag + ">"
                        + (paid > 0 ? " · <green>Du gewinnst +" + unitWin + (bl.isEmpty() ? "" : " <dark_gray>| " + bl)
                        : " · <#FF5A5A>Leider verloren (-" + de.kuby.kubyplugins.service.CasinoPlay.unit(o.cur(), staked) + ")"));
                plugin.casinoPlay().feedback(p, o, fx);
                boolean straight = byPlayer.get(e.getKey()).stream().anyMatch(b -> b.kind() == Kind.ZAHL && b.wins(result));
                if (paid > 0) {
                    p.showTitle(Title.title(Msg.parse("<" + colorTag + "><b>" + result + " " + colorName.toUpperCase() + "</b>"),
                            Msg.parse("<green><b>+" + de.kuby.kubyplugins.service.CasinoPlay.unit(o.cur(), net) + "</b>"
                                    + (straight && blitz.containsKey(result) ? " <#FFE070>⚡ Blitz x" + blitz.get(result) : straight ? " <gold>Volltreffer!" : ""))));
                    if (straight && fx != null) de.kuby.kubyplugins.util.Fx.particle(plugin, fx, org.bukkit.Particle.TOTEM_OF_UNDYING, 60, 0.6, 0.6, 0.6, 0.3);
                } else {
                    // knapp daneben: eine eigene Zahl lag direkt neben der Kugel
                    int at = RouletteTable.position(result);
                    Integer near = null;
                    for (Bet b : byPlayer.get(e.getKey())) {
                        if (b.kind() != Kind.ZAHL) continue;
                        int bp = RouletteTable.position(b.param());
                        if (Math.floorMod(bp - at, 37) == 1 || Math.floorMod(at - bp, 37) == 1) near = b.param();
                    }
                    if (near != null) {
                        Integer nn = near;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.sendActionBar(Msg.parse(
                                "<yellow><b>Knapp!</b></yellow> <gray>Deine <white>" + nn + "</white> lag direkt neben der " + result)), 40L);
                    }
                }
            }
        }
        for (Player q : nearby(15)) {
            if (!sum.containsKey(q.getUniqueId())) {
                q.sendActionBar(Msg.parse("<gray>Roulette: <" + colorTag + "><b>" + result + " " + colorName));
            }
        }
        resultLine = "<" + colorTag + "><b>" + result + " " + colorName.toUpperCase() + "</b>";
        String paidLine = winners == 0 ? "Diesmal gewinnt die Bank" : (winners == 1 ? "1 Gewinner" : winners + " Gewinner")
                + " · " + CasinoText.chips(paidTotal);
        bets.clear();
        save();
        StringBuilder last = new StringBuilder();
        for (int i = 0; i < Math.min(8, history.size()); i++) {
            int h = history.get(i);
            last.append(h == 0 ? "<#3FD55C>" : RouletteTable.red(h) ? "<#FF5A5A>" : "<#D8D8D8>").append(h).append(" ");
        }
        table.center(centerText("<" + colorTag + "><b>" + result + " " + colorName.toUpperCase(), "<gray>" + paidLine
                + "\n<dark_gray>Letzte: " + last.toString().trim()));
        if (fx != null) {
            fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            if (winners > 0) de.kuby.kubyplugins.util.Fx.particle(plugin, fx, org.bukkit.Particle.FIREWORK, 30, 0.8, 0.4, 0.8, 0.05);
        }
        table.bets(Component.empty());
        table.history(history);
        presentResult(result, round);
    }

    /** Croupier am Tisch: Marker auf die Zahl, Verlierer einziehen, Gewinne dazulegen, am Ende alles zu den Spielern. */
    private void presentResult(int result, List<Bet> round) {
        DealerAnim c = table.croupier();
        RouletteTable.Spot win = new RouletteTable.Spot("ZAHL", result);
        Location cell = table.spotCenter(win);
        later(8, () -> {
            if (c != null && cell != null) c.deal(cell);
        });
        later(15, () -> {
            table.dolly(result);
            if (cell != null) cell.getWorld().playSound(cell, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6f, 1.5f);
        });
        later(38, () -> {
            Location from = table.collect(s -> round.stream().noneMatch(b -> spotOf(b).equals(s) && b.wins(result)));
            if (from != null) {
                if (c != null) c.collect(from);
                from.getWorld().playSound(from, Sound.ITEM_BUNDLE_INSERT, 0.8f, 0.7f);
            }
        });
        later(64, () -> {
            Location to = null;
            java.util.Set<String> done = new java.util.HashSet<>();
            for (Bet b : round) {
                if (!b.wins(result)) continue;
                RouletteTable.Spot s = spotOf(b);
                String color = chipColor(b.player());
                if (!done.add(s.key() + color)) continue;
                table.pay(s, color, b.kind() == Kind.ZAHL ? 10 : b.kind().multiplier - 1);
                if (to == null) to = table.spotCenter(s);
            }
            if (to != null) {
                if (c != null) c.push(to);
                to.getWorld().playSound(to, "kuby:casino.chip", org.bukkit.SoundCategory.PLAYERS, 0.9f, 0.85f);
            }
        });
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Consumer<BukkitTask>() {
            int n;

            @Override
            public void accept(BukkitTask task) {
                table.winnerBlink(n % 2 == 0);
                if (++n >= 14) {
                    task.cancel();
                    table.winnerBlink(true);
                }
            }
        }, 20L, 6L);
        later(132, () -> {
            table.sweepToPlayers();
            table.clearWinner();
            table.blitz(Map.of());
            colors.clear();
            phase = bets.isEmpty() ? Phase.IDLE : Phase.BETTING;
            updateTable();
        });
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    /** Platz auf dem Tableau zu einem Einsatz. */
    static RouletteTable.Spot spotOf(Bet b) {
        int param = switch (b.kind()) {
            case ZAHL, DUTZEND, KOLONNE -> b.param();
            default -> 0;
        };
        return new RouletteTable.Spot(b.kind().name(), param);
    }

    /** Chip-Farbe eines Spielers in dieser Runde: die erste freie Farbe. */
    private String chipColor(UUID id) {
        Integer c = colors.get(id);
        if (c == null) {
            boolean[] used = new boolean[RouletteTable.COLORS.length];
            for (int v : colors.values()) used[v] = true;
            int k = 0;
            while (k < used.length - 1 && used[k]) k++;
            c = k;
            colors.put(id, c);
        }
        return RouletteTable.COLORS[c];
    }

    // =================================================================================================================
    // Setzen direkt auf dem Tableau
    // =================================================================================================================

    /** Linksklick auf ein Feld: eigene Chips davon zurücknehmen (nur solange gesetzt werden darf). */
    public String takeBack(Player p, RouletteTable.Spot s) {
        if (phase == Phase.SPINNING || phase == Phase.RESULT || throwWait > 0) return "Nichts geht mehr – die Chips bleiben liegen.";
        long back = 0;
        for (Bet b : new ArrayList<>(bets)) {
            if (b.player().equals(p.getUniqueId()) && spotOf(b).equals(s)) {
                bets.remove(b);
                back += b.amount();
            }
        }
        if (back == 0) return null;
        plugin.casinoPlay().refund(p.getUniqueId(), "roulette", back);
        table.take(s, chipColor(p.getUniqueId()));
        if (bets.isEmpty()) {
            phase = Phase.IDLE;
            colors.clear();
        }
        save();
        updateTable();
        p.sendActionBar(Msg.parse("<gray>Zurückgenommen: <#F2C94C>+" + plugin.casinoPlay().unit(p.getUniqueId(), "roulette", back)));
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.7f, 1.2f);
        return null;
    }

    private static String odds(Kind k) {
        return (k.multiplier - 1) + ":1";
    }

    private static String spotLabel(RouletteTable.Spot s) {
        return switch (s.kind()) {
            case "ZAHL" -> s.param() == 0 ? "Null" : "Zahl " + s.param();
            case "DUTZEND" -> s.param() + ". Dutzend";
            case "KOLONNE" -> s.param() + ". Kolonne";
            default -> Kind.valueOf(s.kind()).label;
        };
    }

    /** Klick auf Tableau, Kessel oder Croupier (Interaction-Flächen des Tischs). */
    public void onTableClick(Player p, String what, boolean attack) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        if (!what.equals("layout") || (!attack && p.isSneaking())) {
            if (attack) return;
            if (p.isSneaking() && !what.equals("layout")) de.kuby.kubyplugins.service.CasinoRules.open(plugin, p, "roulette");
            else open(p);
            return;
        }
        RouletteTable.Spot s = table.spotAt(p.getEyeLocation(), p.getEyeLocation().getDirection());
        if (s == null) {
            if (!attack) open(p);
            return;
        }
        String err = attack ? takeBack(p, s) : bet(p, Kind.valueOf(s.kind()), s.param());
        if (err != null) {
            p.sendActionBar(Msg.parse("<#FF6B6B>" + Msg.escape(err)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
        }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        String what = e.getRightClicked().getPersistentDataContainer().get(table.clickKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (what == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        onTableClick(e.getPlayer(), what, false);
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onAttackEntity(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        String what = e.getAttacked().getPersistentDataContainer().get(table.clickKey(), org.bukkit.persistence.PersistentDataType.STRING);
        if (what == null) return;
        e.setCancelled(true);
        onTableClick(e.getPlayer(), what, true);
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        hovering.remove(e.getPlayer().getUniqueId());
        table.hoverOff(e.getPlayer().getUniqueId());
    }

    /** Alle 3 Ticks: Feld unter dem Fadenkreuz einrahmen und Quote/Klick-Hinweis zeigen (nur für Spieler am Tisch). */
    private void hoverTick() {
        Location lc = table.layoutCenter();
        if (lc == null || !table.ready()) return;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Player p : lc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(lc) > 36 || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            RouletteTable.Spot s = table.spotAt(p.getEyeLocation(), p.getEyeLocation().getDirection());
            if (s == null) continue;
            seen.add(p.getUniqueId());
            RouletteTable.Spot before = hovering.put(p.getUniqueId(), s);
            table.hover(p, s);
            if (s.equals(before) && plugin.getServer().getCurrentTick() % 15 != 0) continue;
            boolean open = phase == Phase.IDLE || (phase == Phase.BETTING && throwWait <= 0);
            long mine = 0;
            for (Bet b : bets) if (b.player().equals(p.getUniqueId()) && spotOf(b).equals(s)) mine += b.amount();
            String head = "<white><b>" + spotLabel(s) + "</b></white> <gray>zahlt</gray> <#F2C94C>" + odds(Kind.valueOf(s.kind()))
                    + (s.kind().equals("ZAHL") && blitz.containsKey(s.param()) ? " <#FFE070>⚡x" + blitz.get(s.param()) : "");
            String tail = !open ? " <dark_gray>· Nichts geht mehr"
                    : " <dark_gray>· <gray>Rechtsklick <#F2C94C>+" + CasinoText.num(Math.max(0, chipValue(p))) + "</#F2C94C>"
                    + (mine > 0 ? " <dark_gray>· <gray>Linksklick zurück <white>(" + CasinoText.num(mine) + ")" : "");
            p.sendActionBar(Msg.parse(head + tail));
        }
        for (UUID id : new ArrayList<>(hovering.keySet())) {
            if (seen.contains(id)) continue;
            hovering.remove(id);
            table.hoverOff(id);
        }
    }

    // =================================================================================================================
    // Anzeige am Tisch
    // =================================================================================================================

    private Component centerText(String line2, String line3) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("ROULETTE", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)).append(Component.newline());
        b.append(Msg.parse(line2)).append(Component.newline()).append(Msg.parse(line3));
        return b.build();
    }

    private void updateTable() {
        if (!table.ready()) return;
        switch (phase) {
            case IDLE -> table.center(centerText("<yellow>Faites vos jeux!", "<gray>Rechtsklick aufs Tableau setzt"));
            case BETTING -> table.center(centerText("<yellow>Einsätze <white>— " + countdown + "s",
                    "<gray>Im Topf <#F2C94C>" + CasinoText.chips(pot())));
            case SPINNING -> table.center(centerText("<yellow>Die Kugel rollt..", "<gray>Keine Einsätze mehr"));
            case RESULT -> {
            }
        }
        if (phase == Phase.RESULT) return;
        if (bets.isEmpty()) {
            table.bets(Component.empty());
            return;
        }
        Map<UUID, Long> per = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (Bet b : bets) {
            per.merge(b.player(), b.amount(), Long::sum);
            names.put(b.player(), b.name());
        }
        TextComponent.Builder t = Component.text();
        t.append(Component.text("Einsätze", TextColor.color(0xF2B829)).decorate(TextDecoration.BOLD));
        int shown = 0;
        for (Map.Entry<UUID, Long> e : per.entrySet()) {
            if (shown++ >= 6) break;
            Integer ci = colors.get(e.getKey());
            t.append(Component.newline())
                    .append(Component.text("● ", TextColor.color(ci == null ? 0xFFFFFF : RouletteTable.COLOR_RGB[ci])))
                    .append(Component.text(names.get(e.getKey()) + " ", NamedTextColor.WHITE))
                    .append(Component.text(CasinoText.chips(e.getValue()), TextColor.color(0xF2C94C)));
        }
        if (per.size() > 6) t.append(Component.newline()).append(Component.text("+" + (per.size() - 6) + " weitere", NamedTextColor.GRAY));
        t.append(Component.newline()).append(Component.text("Im Topf ", NamedTextColor.GRAY))
                .append(Component.text(CasinoText.chips(pot()), TextColor.color(0xF2C94C)));
        table.bets(t.build());
    }

    private long pot() {
        long s = 0;
        for (Bet b : bets) s += b.amount();
        return s;
    }

    private List<Player> bettors() {
        List<Player> out = new ArrayList<>();
        for (Bet b : bets) {
            Player p = Bukkit.getPlayer(b.player());
            if (p != null && !out.contains(p)) out.add(p);
        }
        return out;
    }

    private List<Player> nearby(double r) {
        List<Player> out = new ArrayList<>();
        Location c = table.center();
        if (c == null) return out;
        for (Player p : c.getWorld().getPlayers()) if (p.getLocation().distanceSquared(c) <= r * r) out.add(p);
        return out;
    }

    // =================================================================================================================
    // Dialog
    // =================================================================================================================

    /** Öffnet den Roulette-Dialog (prüft Modus und Nähe). */
    public void open(Player p) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked == null && !hasTable()) blocked = "Der Roulette-Tisch ist noch nicht aufgebaut.";
        if (blocked == null && !near(p.getLocation())) blocked = "Geh zum Roulette-Tisch.";
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        show(p);
    }

    private void show(Player p) {
        Session s = session(p.getUniqueId());
        List<CasinoScreens.BetLine> lines = new ArrayList<>();
        for (Bet b : bets) {
            if (!b.player().equals(p.getUniqueId())) continue;
            boolean merged = false;
            for (int i = 0; i < lines.size(); i++) {
                CasinoScreens.BetLine l = lines.get(i);
                if (l.label().equals(b.label())) {
                    lines.set(i, new CasinoScreens.BetLine(l.label(), l.color(), l.amount() + b.amount()));
                    merged = true;
                }
            }
            if (!merged) lines.add(new CasinoScreens.BetLine(b.label(), colorOf(b), b.amount()));
        }
        String status = switch (phase) {
            case IDLE -> "Dein erster Einsatz startet die Runde";
            case BETTING -> "Runde startet in " + countdown + " s · Im Topf " + CasinoText.num(pot());
            case SPINNING -> "Die Kugel rollt – gleich geht's weiter";
            case RESULT -> "Ergebnis: " + lastResult + " " + (lastResult == 0 ? "Grün" : RouletteTable.red(lastResult) ? "Rot" : "Schwarz");
        };
        long value = s.chip == 6 ? plugin.casino().chips(p.getUniqueId()) : s.chip == 5 ? s.custom
                : CasinoScreens.CHIPS.get(s.chip).value();
        Map<String, Long> mine = new HashMap<>();
        for (Bet b : bets) if (b.player().equals(p.getUniqueId())) mine.merge(b.spot() + ":" + b.param(), b.amount(), Long::sum);
        CasinoScreens.RouletteView v = new CasinoScreens.RouletteView(plugin.casino().chips(p.getUniqueId()), s.expert,
                history.size(), value, s.chip, status, lines, placed(p.getUniqueId()), !s.last.isEmpty(), mine,
                List.copyOf(history.subList(0, Math.min(history.size(), 12))));
        UiDialog.show(p, CasinoScreens.roulette(v, actions));
    }

    private static TextColor colorOf(Bet b) {
        if (b.kind() == Kind.ROT) return TextColor.color(0xFF5A5A);
        if (b.kind() == Kind.SCHWARZ) return TextColor.color(0xD8D8D8);
        if (b.kind() == Kind.ZAHL) {
            return b.param() == 0 ? TextColor.color(0x3FD55C) : RouletteTable.red(b.param()) ? TextColor.color(0xFF5A5A)
                    : TextColor.color(0xD8D8D8);
        }
        return TextColor.color(0xF2DA90);
    }

    private void after(Player p, String err) {
        if (err != null) CasinoText.error(p, err);
        show(p);
    }

    private final CasinoScreens.RouletteActions actions = new CasinoScreens.RouletteActions() {
        @Override
        public Consumer<Player> mode(boolean expert) {
            return p -> {
                session(p.getUniqueId()).expert = expert;
                show(p);
            };
        }

        @Override
        public Consumer<Player> history() {
            return p -> UiDialog.show(p, CasinoScreens.rouletteHistory(List.copyOf(history), RouletteGame.this::open));
        }

        @Override
        public Consumer<Player> color(String color) {
            return p -> after(p, switch (color) {
                case "rot" -> bet(p, Kind.ROT, 0);
                case "schwarz" -> bet(p, Kind.SCHWARZ, 0);
                default -> bet(p, Kind.ZAHL, 0);
            });
        }

        @Override
        public Consumer<Player> field(String kind, int param) {
            return p -> after(p, bet(p, Kind.valueOf(kind), param));
        }

        @Override
        public Consumer<Player> chip(int index) {
            return p -> {
                session(p.getUniqueId()).chip = index;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
                show(p);
            };
        }

        @Override
        public Consumer<Player> custom() {
            return p -> CasinoInput.amount(p, "Eigener Betrag", List.of("<gray>Wie viel soll jeder Klick setzen?",
                            "<gray>Mindestens " + CasinoText.num(minBet()) + ", höchstens " + CasinoText.num(maxRound()) + " Chips"),
                    minBet(), maxRound(), "Übernehmen", (pl, v) -> {
                        Session s = session(pl.getUniqueId());
                        s.custom = v;
                        s.chip = 5;
                        show(pl);
                    }, RouletteGame.this::show);
        }

        @Override
        public Consumer<Player> repeat() {
            return p -> after(p, RouletteGame.this.repeat(p));
        }

        @Override
        public Consumer<Player> clear() {
            return p -> after(p, RouletteGame.this.clear(p));
        }
    };

    // =================================================================================================================
    // Test (kubytest roulette)
    // =================================================================================================================

    /** Countdown sofort ablaufen lassen. */
    public void testFastForward() {
        if (phase == Phase.BETTING && throwWait <= 0) countdown = 1;
    }

    /** Trifft der Blick senkrecht von oben auf jede Platzmitte genau diesen Platz? „49/49“ oder mit Fehlerliste. */
    public String testHits() {
        List<RouletteTable.Spot> all = new ArrayList<>();
        for (int n = 0; n <= 36; n++) all.add(new RouletteTable.Spot("ZAHL", n));
        for (int k = 1; k <= 3; k++) {
            all.add(new RouletteTable.Spot("DUTZEND", k));
            all.add(new RouletteTable.Spot("KOLONNE", k));
        }
        for (String o : List.of("ROT", "SCHWARZ", "GERADE", "UNGERADE", "NIEDRIG", "HOCH")) all.add(new RouletteTable.Spot(o, 0));
        int ok = 0;
        List<String> bad = new ArrayList<>();
        for (RouletteTable.Spot sp : all) {
            Location c = table.spotCenter(sp);
            if (c == null) {
                bad.add(sp.key() + " fehlt");
                continue;
            }
            RouletteTable.Spot got = table.spotAt(c.clone().add(0, 1.5, 0), new org.bukkit.util.Vector(0.0001, -1, 0.0001));
            if (sp.equals(got)) ok++;
            else bad.add(sp.key() + "->" + (got == null ? "nichts" : got.key()));
        }
        return ok + "/" + all.size() + (bad.isEmpty() ? "" : " " + String.join(", ", bad));
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        Location c = table.center();
        if (c != null && c.getWorld() != null) {
            // Format ab v1.98: Welt;x;y;z;yaw;v (Boden in der Tischmitte)
            out.set("tisch", c.getWorld().getName() + ";" + c.getX() + ";" + c.getY() + ";" + c.getZ() + ";" + table.yaw() + ";v");
        } else if (tableRaw != null) {
            out.set("tisch", tableRaw);
        }
        out.set("verlauf", history);
        List<String> open = new ArrayList<>();
        for (Bet b : bets) open.add(b.player() + ";" + b.amount());
        out.set("offen", open);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("roulette-tisch.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

}
