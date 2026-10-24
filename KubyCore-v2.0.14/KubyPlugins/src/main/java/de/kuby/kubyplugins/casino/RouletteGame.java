package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
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
 * Roulette (europäisch, 0 bis 36) am Tisch aus Anzeigen. Der erste Einsatz startet die Runde, nach einem Countdown
 * läuft das Licht über die Felder, bremst ab und bleibt auf dem Ergebnis stehen. Einsätze über den Dialog
 * (/roulette oder Rechtsklick auf den Croupier): Einfach (Rot, Grün · 0, Schwarz) oder Experte (Zahlen, Dutzende,
 * Kolonnen, Gerade/Ungerade, 1–18/19–36). Offene Einsätze stehen in roulette.yml und kommen nach einem Absturz zurück.
 */
public final class RouletteGame implements org.bukkit.event.Listener {

    public enum Kind {
        ROT("Rot", 2), SCHWARZ("Schwarz", 2), GERADE("Gerade", 2), UNGERADE("Ungerade", 2), NIEDRIG("1–18", 2),
        HOCH("19–36", 2), DUTZEND("Dutzend", 3), KOLONNE("Kolonne", 3), ZAHL("Zahl", 36);

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
    private String resultLine = "";
    private BukkitTask timer;
    private BukkitTask spin;
    /** Tisch aus der Datei, solange die Welt noch nicht geladen ist. */
    private String tableRaw;

    public RouletteGame(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "roulette-tisch.yml");
        this.table = new RouletteTable(new NamespacedKey(plugin, "roulette_table"));
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String t = yml.getString("tisch");
        tableRaw = t;
        if (t != null) {
            String[] p = t.split(";");
            World w = p.length >= 7 ? Bukkit.getWorld(p[0]) : null;
            if (w != null) {
                try {
                    table.geometry(new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])),
                            Double.parseDouble(p[4]), Double.parseDouble(p[5]), Float.parseFloat(p[6]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Object o : yml.getList("verlauf", List.of())) {
            if (o instanceof Number n) history.add(n.intValue());
        }
        lastResult = history.isEmpty() ? -1 : history.get(0);
        // offene Einsätze nach einem Absturz zurückgeben
        for (String line : yml.getStringList("offen")) {
            String[] p = line.split(";");
            try {
                plugin.casino().giveChips(UUID.fromString(p[0]), Long.parseLong(p[1]));
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
        updateTable();
    }

    private boolean rebuildQueued;

    @org.bukkit.event.EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        Location c = table.center();
        if (c == null || rebuildQueued || !e.getWorld().equals(c.getWorld())) return;
        double r = Math.max(table.rx(), table.rz()) + 2;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        if (cx < ((int) Math.floor(c.getX() - r) >> 4) || cx > ((int) Math.floor(c.getX() + r) >> 4)) return;
        if (cz < ((int) Math.floor(c.getZ() - r) >> 4) || cz > ((int) Math.floor(c.getZ() + r) >> 4)) return;
        rebuildQueued = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            rebuildQueued = false;
            if (phase != Phase.SPINNING) respawn();
        }, 5L);
    }

    public boolean hasTable() {
        return table.center() != null;
    }

    /** /casino roulette tisch [breite] [tiefe]: Tischmitte = Block, auf den der Admin schaut. */
    public void setTable(Location surfaceCenter, double width, double depth, float yaw) {
        table.removeAll();
        table.geometry(surfaceCenter, Math.max(1.5, width / 2 - 0.75), Math.max(1.2, depth / 2 - 0.75), yaw);
        save();
        respawn();
    }

    public void removeTable() {
        table.removeAll();
    }

    public void shutdown() {
        refundAll();
        if (timer != null) timer.cancel();
        if (spin != null) spin.cancel();
        table.removeAll();
    }

    private void refundAll() {
        for (Bet b : bets) plugin.casino().giveChips(b.player(), b.amount());
        bets.clear();
        save();
    }

    /** True, wenn der Block zum Tisch gehört (innerhalb des Ovals, bis 2 Blöcke unter der Oberfläche). */
    public boolean isTable(Location block) {
        Location c = table.center();
        if (c == null || block.getWorld() == null || !block.getWorld().equals(c.getWorld())) return false;
        double dy = c.getY() - (block.getBlockY() + 1);
        if (dy < -0.1 || dy > 2) return false;
        double r = Math.toRadians(table.yaw());
        double fx = -Math.sin(r), fz = Math.cos(r);
        double x = block.getBlockX() + 0.5 - c.getX(), z = block.getBlockZ() + 0.5 - c.getZ();
        double v = x * fx + z * fz, u = x * -fz + z * fx;
        double a = table.rx() + 0.9, b = table.rz() + 0.9;
        return (u * u) / (a * a) + (v * v) / (b * b) <= 1.0;
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
            long left = maxRound() - placed(p.getUniqueId());
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
        if (amount < minBet()) return "Mindesteinsatz " + CasinoText.num(minBet()) + " Chips.";
        if (placed(p.getUniqueId()) + amount > maxRound()) {
            return "Höchstens " + CasinoText.num(maxRound()) + " Chips pro Runde.";
        }
        if (!plugin.casino().takeChips(p.getUniqueId(), amount)) {
            return "So viele Chips hast du nicht. Tausch sie an der Kasse.";
        }
        Bet bet = new Bet(p.getUniqueId(), p.getName(), kind, param, amount);
        bets.add(bet);
        long feed = Math.round(amount * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        table.chip(bet.spot(), param, amount);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.7f, 1.6f);
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
        if (plugin.casino().chips(p.getUniqueId()) < sum) return "Für alle Einsätze der letzten Runde fehlen dir Chips.";
        for (Bet b : s.last) {
            String err = place(p, b.kind(), b.param(), b.amount());
            if (err != null) return err;
        }
        return null;
    }

    public String clear(Player p) {
        if (phase == Phase.SPINNING || phase == Phase.RESULT) return "Die Kugel rollt schon.";
        long back = 0;
        for (Bet b : new ArrayList<>(bets)) {
            if (b.player().equals(p.getUniqueId())) {
                bets.remove(b);
                back += b.amount();
            }
        }
        if (back == 0) return "Du hast in dieser Runde nichts gesetzt.";
        plugin.casino().giveChips(p.getUniqueId(), back);
        table.clearChips();
        for (Bet b : bets) table.chip(b.spot(), b.param(), b.amount());
        if (bets.isEmpty()) phase = Phase.IDLE;
        save();
        updateTable();
        CasinoText.send(p, "Einsätze zurückgenommen: <#F2C94C>+" + CasinoText.chips(back));
        return null;
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    private void tick() {
        if (phase != Phase.BETTING) return;
        countdown--;
        if (countdown <= 0) {
            startSpin();
            return;
        }
        if (countdown <= 5 || countdown % 5 == 0) {
            Location c = table.center();
            if (c != null) c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, countdown <= 3 ? 1.6f : 1.2f);
        }
        for (Player p : bettors()) {
            p.sendActionBar(Msg.parse("<gray>Roulette · Einsatz <#F2C94C>" + CasinoText.chips(placed(p.getUniqueId()))
                    + "</#F2C94C> · Runde in <white>" + countdown + " s"));
        }
        updateTable();
    }

    private void startSpin() {
        phase = Phase.SPINNING;
        int result = RNG.nextInt(37);
        int start = lastResult < 0 ? 0 : RouletteTable.position(lastResult);
        int target = RouletteTable.position(result);
        int steps = 37 + Math.floorMod(target - start, 37) + 37;
        updateTable();
        Location c = table.center();
        spin = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int step;
            int wait;
            int pos = start;

            @Override
            public void run() {
                if (wait > 0) {
                    wait--;
                    return;
                }
                table.light(pos, false);
                pos = (pos + 1) % 37;
                step++;
                table.light(pos, true);
                if (c != null) {
                    float pitch = 1.9f - 0.9f * step / steps;
                    c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, pitch);
                }
                double f = (double) step / steps;
                wait = (int) Math.round(6.5 * Math.pow(f, 2.4));
                if (step >= steps) {
                    spin.cancel();
                    spin = null;
                    finish(result, pos);
                }
            }
        }, 1L, 1L);
    }

    private void finish(int result, int pos) {
        phase = Phase.RESULT;
        lastResult = result;
        history.add(0, result);
        while (history.size() > 100) history.remove(history.size() - 1);
        // Gewinne je Spieler zusammenfassen
        Map<UUID, long[]> sum = new LinkedHashMap<>();
        Map<UUID, List<Bet>> byPlayer = new LinkedHashMap<>();
        for (Bet b : bets) {
            long paid = b.wins(result) ? b.amount() * b.kind().multiplier : 0;
            if (paid > 0) plugin.casino().giveChips(b.player(), paid);
            long[] s = sum.computeIfAbsent(b.player(), k -> new long[2]);
            s[0] += b.amount();
            s[1] += paid;
            byPlayer.computeIfAbsent(b.player(), k -> new ArrayList<>()).add(b);
        }
        long paidTotal = 0;
        int winners = 0;
        Location fx = table.center() == null ? null : table.center().add(0, 1.2, 0);
        String colorName = result == 0 ? "Grün" : RouletteTable.red(result) ? "Rot" : "Schwarz";
        String colorTag = result == 0 ? "#3FD55C" : RouletteTable.red(result) ? "#FF5A5A" : "#D8D8D8";
        for (Map.Entry<UUID, long[]> e : sum.entrySet()) {
            long staked = e.getValue()[0], paid = e.getValue()[1];
            long net = paid - staked;
            paidTotal += paid;
            if (paid > 0) winners++;
            plugin.casinoLog().record("Roulette", e.getKey(), staked, paid);
            session(e.getKey()).last = new ArrayList<>(byPlayer.get(e.getKey()));
            CasinoFx.Result r = net > 0 ? (byPlayer.get(e.getKey()).stream().anyMatch(b -> b.kind() == Kind.ZAHL && b.wins(result))
                    ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN) : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE;
            CasinoFx.show(plugin, e.getKey(), fx, r, net);
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                CasinoText.send(p, "Die Kugel liegt auf <" + colorTag + "><b>" + result + " " + colorName + "</b></" + colorTag + ">"
                        + (paid > 0 ? " · <green>Du gewinnst +" + CasinoText.chips(paid) : " · <#FF5A5A>Leider verloren (-"
                        + CasinoText.chips(staked) + ")"));
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
        table.center(centerText("<" + colorTag + "><b>" + result + " " + colorName.toUpperCase(), "<gray>" + paidLine));
        table.bets(Component.empty());
        // Ergebnis blinken lassen, dann aufräumen
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Consumer<BukkitTask>() {
            int n;

            @Override
            public void accept(BukkitTask task) {
                table.light(pos, n % 2 == 1);
                if (++n >= 9) {
                    task.cancel();
                    table.light(pos, false);
                }
            }
        }, 6L, 6L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            table.clearChips();
            phase = bets.isEmpty() ? Phase.IDLE : Phase.BETTING;
            updateTable();
        }, 120L);
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
            case IDLE -> table.center(centerText("<yellow>Warte auf Einsätze..", "<gray>/roulette oder Croupier"));
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
        Map<String, Long> per = new LinkedHashMap<>();
        for (Bet b : bets) per.merge(b.name(), b.amount(), Long::sum);
        TextComponent.Builder t = Component.text();
        t.append(Component.text("Einsätze", TextColor.color(0xF2B829)).decorate(TextDecoration.BOLD));
        int shown = 0;
        for (Map.Entry<String, Long> e : per.entrySet()) {
            if (shown++ >= 6) break;
            t.append(Component.newline()).append(Component.text(e.getKey() + " ", NamedTextColor.WHITE))
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
    // Speichern
    // =================================================================================================================

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        Location c = table.center();
        if (c != null && c.getWorld() != null) {
            out.set("tisch", c.getWorld().getName() + ";" + c.getX() + ";" + c.getY() + ";" + c.getZ() + ";"
                    + table.rx() + ";" + table.rz() + ";" + table.yaw());
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
