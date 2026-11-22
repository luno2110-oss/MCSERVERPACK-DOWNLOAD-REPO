package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.service.CasinoPlay;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pferderennen im Casino (v2.05): ein Bildschirm an der Wand, gemeinsame Runden wie beim Roulette. Vor jedem Rennen
 * stehen Form und Quoten auf der Toto-Tafel; gewettet wird auf Sieg oder Platz (unter den ersten zwei). Die erste Wette
 * startet den Countdown (casino.rennen.wait-seconds), dann öffnen die Startboxen und das Rennen läuft gut 15 Sekunden.
 * Abgerechnet wird, sobald der Zweite im Ziel ist (CasinoPlay: Serie, Goldene Zeit, Cashback, Rangliste). Danach
 * kommt ein neues Feld mit neuen Quoten.
 */
public final class RaceService implements Listener {

    public static final String GAME = "rennen";
    private static final SecureRandom RNG = new SecureRandom();
    public static final long[] CHIPS = {10, 50, 100, 500, 1000};

    public enum Phase { OPEN, BETTING, RUNNING, RESULT }

    public record Bet(UUID player, String name, int lane, boolean place, long amount) {
        public boolean wins(int[] order) {
            return order[0] == lane || (place && order[1] == lane);
        }

        public long payout(HorseRace.Field f, int[] order) {
            if (!wins(order)) return 0;
            return Math.round(amount * (place ? f.placeOdds()[lane] : f.winOdds()[lane]));
        }
    }

    private static final class Session {
        int chip = 2;
        long custom;
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private RaceBoard board;
    private String raw;
    private Phase phase = Phase.OPEN;
    private HorseRace.Field field;
    private HorseRace.Plan plan;
    private int countdown;
    private int t;
    private int lastLeader = -1;
    private boolean settled;
    private int resultWait;
    private final List<Bet> bets = new ArrayList<>();
    private final List<Integer> winners = new ArrayList<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;
    private int[] lastOrder;
    /** Für den Regeltest im Log. */
    public boolean testLog;

    public RaceService(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.casinoPlay().refundOpen(GAME);
        this.file = new File(plugin.getDataFolder(), "pferderennen.yml");
        this.tag = new NamespacedKey(plugin, "race");
        this.click = new NamespacedKey(plugin, "race_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        raw = yml.getString("bildschirm");
        for (int w : yml.getIntegerList("sieger")) if (w >= 0 && w < HorseRace.HORSES) winners.add(w);
        field = HorseRace.field(RNG);
    }

    private void loadBoard() {
        if (board != null || raw == null) return;
        String[] p = raw.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return;
        try {
            board = new RaceBoard("rennen", new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])),
                    Float.parseFloat(p[4]), Double.parseDouble(p[5]), tag, click);
        } catch (RuntimeException e) {
            board = null;
        }
    }

    private void build() {
        if (board == null) return;
        board.build();
        board.gates(phase != Phase.RUNNING && phase != Phase.RESULT);
        refresh();
    }

    public void respawn() {
        loadBoard();
        if (board != null) {
            Location c = board.center();
            if (c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4) && !board.ready()) build();
        }
        start();
    }

    public void start() {
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (board == null) return;
        Location c = board.center();
        if (c.getWorld().equals(e.getWorld()) && (c.getBlockX() >> 4) == e.getChunk().getX() && (c.getBlockZ() >> 4) == e.getChunk().getZ()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (board != null && !board.ready()) build();
            }, 5L);
        }
    }

    /** Beim Abschalten: offene Wetten zurück (das Rennen ist noch nicht entschieden), Anzeigen weg. */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        if (!settled) refundAll();
        if (board != null) board.removeAll();
        save();
    }

    private void refundAll() {
        Map<UUID, Long> sum = new LinkedHashMap<>();
        for (Bet b : bets) sum.merge(b.player(), b.amount(), Long::sum);
        for (Map.Entry<UUID, Long> e : sum.entrySet()) plugin.casinoPlay().refund(e.getKey(), GAME, e.getValue());
        bets.clear();
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("bildschirm", raw);
        yml.set("sieger", new ArrayList<>(winners));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("pferderennen.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public boolean hasBoard() {
        return raw != null;
    }

    public Phase phase() {
        return phase;
    }

    public HorseRace.Field field() {
        return field;
    }

    // =================================================================================================================
    // Wetten
    // =================================================================================================================

    private Session session(UUID id) {
        return sessions.computeIfAbsent(id, k -> new Session());
    }

    public long minBet() {
        return plugin.getConfig().getLong("casino.rennen.min-bet", 10);
    }

    public long maxRound(Player p) {
        return plugin.casinoPlay().maxFor(p.getUniqueId(), plugin.getConfig().getLong("casino.rennen.max-round", 20_000));
    }

    public long chipValue(Player p) {
        Session s = session(p.getUniqueId());
        return s.chip >= CHIPS.length ? s.custom : CHIPS[s.chip];
    }

    public long placed(UUID id) {
        long sum = 0;
        for (Bet b : bets) if (b.player().equals(id)) sum += b.amount();
        return sum;
    }

    public boolean near(Location l) {
        return board != null && l.getWorld().equals(board.center().getWorld()) && l.distanceSquared(board.center()) < 24 * 24;
    }

    /** Wette mit dem gewählten Chip. */
    public String bet(Player p, int lane, boolean place) {
        long amount = chipValue(p);
        if (amount <= 0) return "Gib zuerst einen eigenen Betrag ein.";
        return bet(p, lane, place, amount);
    }

    public String bet(Player p, int lane, boolean place, long amount) {
        return bet(p, lane, place, amount, false);
    }

    /** Regeltest: Wette ohne Entfernungsprüfung (der Testspieler steht irgendwo in der Arena). */
    public String testBet(Player p, int lane, boolean place, long amount) {
        return bet(p, lane, place, amount, true);
    }

    private String bet(Player p, int lane, boolean place, long amount, boolean anywhere) {
        if (board == null) return "Hier gibt es noch keine Rennbahn.";
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (!anywhere && !near(p.getLocation())) return "Geh näher an den Rennbildschirm.";
        if (phase == Phase.RUNNING || phase == Phase.RESULT) return "Das Rennen läuft – gleich gibt es neue Quoten.";
        if (lane < 0 || lane >= HorseRace.HORSES) return "Unbekanntes Pferd.";
        if (amount < minBet()) return "Mindesteinsatz " + CasinoText.num(minBet()) + " Chips.";
        long max = maxRound(p);
        if (placed(p.getUniqueId()) + amount > max) return "Höchstens " + CasinoText.num(max) + " Chips pro Rennen.";
        String err = plugin.casinoPlay().take(p, GAME, amount, CasinoPlay.Cur.CHIPS);
        if (err != null) return err;
        int same = -1;
        for (int i = 0; i < bets.size(); i++) {
            Bet b = bets.get(i);
            if (b.player().equals(p.getUniqueId()) && b.lane() == lane && b.place() == place) same = i;
        }
        if (same >= 0) bets.set(same, new Bet(p.getUniqueId(), p.getName(), lane, place, bets.get(same).amount() + amount));
        else bets.add(new Bet(p.getUniqueId(), p.getName(), lane, place, amount));
        Location at = board.center();
        at.getWorld().playSound(p.getLocation(), "kuby:casino.chip", org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.95f + RNG.nextFloat() * 0.1f);
        if (phase == Phase.OPEN) {
            phase = Phase.BETTING;
            countdown = (int) plugin.getConfig().getLong("casino.rennen.wait-seconds", 25) * 20;
            for (Player q : nearby(20)) {
                CasinoText.send(q, "<white>" + Msg.escape(p.getName()) + "</white> wettet – das Rennen startet in "
                        + countdown / 20 + " s. <yellow>Rechtsklick auf den Bildschirm</yellow> zum Mitwetten.");
            }
        }
        if (testLog) plugin.getLogger().info("[Casino-Test] rennen Wette: " + p.getName() + " " + (place ? "Platz" : "Sieg") + " #" + (lane + 1)
                + " " + amount + " zu " + HorseRace.fmt(place ? field.placeOdds()[lane] : field.winOdds()[lane]));
        refresh();
        return null;
    }

    public String clear(Player p) {
        if (phase == Phase.RUNNING || phase == Phase.RESULT) return "Das Rennen läuft schon.";
        long back = 0;
        for (Bet b : new ArrayList<>(bets)) {
            if (b.player().equals(p.getUniqueId())) {
                bets.remove(b);
                back += b.amount();
            }
        }
        if (back == 0) return "Du hast in diesem Rennen nichts gewettet.";
        plugin.casinoPlay().refund(p.getUniqueId(), GAME, back);
        if (bets.isEmpty()) phase = Phase.OPEN;
        refresh();
        CasinoText.send(p, "Wetten zurückgenommen: <#F2C94C>+" + CasinoText.chips(back));
        return null;
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    private List<Player> nearby(double r) {
        List<Player> out = new ArrayList<>();
        if (board == null) return out;
        Location c = board.center();
        for (Player p : c.getWorld().getPlayers()) if (p.getLocation().distanceSquared(c) <= r * r) out.add(p);
        return out;
    }

    private void tick() {
        if (board == null) return;
        switch (phase) {
            case OPEN -> {
            }
            case BETTING -> {
                countdown--;
                if (countdown % 20 == 0) {
                    int s = countdown / 20;
                    Location c = board.center();
                    if (s <= 5 && s > 0) c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.0f + (5 - s) * 0.18f);
                    for (Player p : nearby(20)) {
                        long mine = placed(p.getUniqueId());
                        p.sendActionBar(Msg.parse("<gray>Pferderennen · " + (mine > 0 ? "deine Wetten <#F2C94C>" + CasinoText.num(mine) + "</#F2C94C> · " : "")
                                + "Start in " + (s <= 5 ? "<#FF6B6B>" : "<white>") + s + " s"));
                    }
                    refresh();
                }
                if (countdown <= 0) startRace();
            }
            case RUNNING -> runTick();
            case RESULT -> {
                if (--resultWait <= 0) newField();
            }
        }
    }

    private void startRace() {
        phase = Phase.RUNNING;
        int[] order = HorseRace.order(field.win(), RNG);
        plan = HorseRace.plan(order, RNG);
        t = 0;
        lastLeader = -1;
        settled = false;
        board.gates(true);
        Location c = board.center();
        c.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> c.getWorld().playSound(c, Sound.BLOCK_BELL_USE, 0.8f, 1.2f), 4L);
        for (Player p : nearby(24)) p.sendActionBar(Msg.parse("<#7EE07A><b>Und sie sind los!</b>"));
        if (testLog) plugin.getLogger().info("[Casino-Test] rennen Start: Einlauf geplant " + java.util.Arrays.toString(order)
                + ", Foto-Finish " + plan.photo() + ", Führungswechsel " + plan.leadChanges());
        refresh();
    }

    private void runTick() {
        t++;
        int tt = Math.min(t, plan.length() - 1);
        double[] x = new double[HorseRace.HORSES];
        for (int h = 0; h < HorseRace.HORSES; h++) x[h] = plan.x()[h][tt];
        int[] standing = plan.standing(tt);
        board.positions(x, standing);
        Location c = board.center();
        // Galopp: Hufschlag in kurzen Abständen, zur Zielgeraden dichter
        int winT = plan.finish()[plan.winner()];
        if (t < winT + 20 && t % (t > winT * 0.75 ? 2 : 3) == 0) {
            c.getWorld().playSound(c, Sound.ENTITY_HORSE_GALLOP, 0.35f, 0.85f + RNG.nextFloat() * 0.4f);
        }
        int leader = plan.leader(tt);
        if (t <= winT && t % 5 == 0 && leader != lastLeader) {
            String msg = (lastLeader < 0 ? "" : t > winT * 0.75 ? "Zielgerade! " : "") + "<" + hex(leader) + "><b>#" + (leader + 1) + " "
                    + HorseRace.NAMES[leader] + "</b></" + hex(leader) + "> " + (lastLeader < 0 ? "führt" : "übernimmt die Führung!");
            for (Player p : nearby(24)) p.sendActionBar(Msg.parse("<gray>" + msg));
            if (lastLeader >= 0) c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.4f);
            lastLeader = leader;
        }
        if (t % 10 == 0 && t <= winT) refreshStatus();
        // abrechnen, sobald der Zweite im Ziel ist
        int second = plan.order()[1];
        if (!settled && t >= plan.finish()[second]) {
            settled = true;
            finish();
        }
        if (t >= plan.length() - 1) {
            phase = Phase.RESULT;
            resultWait = 140;
        }
    }

    private static String hex(int lane) {
        return String.format("#%06X", HorseRace.COLORS[lane]);
    }

    private void finish() {
        int[] order = plan.order();
        lastOrder = order.clone();
        int win = order[0];
        winners.add(0, win);
        while (winners.size() > 20) winners.remove(winners.size() - 1);
        Location fx = board.finishLocation(win);
        fx.getWorld().playSound(fx, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.0f);
        fx.getWorld().playSound(fx, Sound.ENTITY_HORSE_AMBIENT, 0.7f, 1.1f);
        de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.FIREWORK, 30, 0.3, 0.6, 0.1, 0.05);
        Map<UUID, long[]> sum = new LinkedHashMap<>();
        Map<UUID, List<Bet>> byPlayer = new LinkedHashMap<>();
        for (Bet b : bets) {
            long[] s = sum.computeIfAbsent(b.player(), k -> new long[2]);
            s[0] += b.amount();
            s[1] += b.payout(field, order);
            byPlayer.computeIfAbsent(b.player(), k -> new ArrayList<>()).add(b);
        }
        String winTag = "<" + hex(win) + "><b>#" + (win + 1) + " " + HorseRace.NAMES[win] + "</b></" + hex(win) + ">";
        String second = "<" + hex(order[1]) + ">#" + (order[1] + 1) + " " + HorseRace.NAMES[order[1]] + "</" + hex(order[1]) + ">";
        int winnersN = 0;
        long paidTotal = 0;
        for (Map.Entry<UUID, long[]> e : sum.entrySet()) {
            long staked = e.getValue()[0];
            CasinoPlay.Outcome o = plugin.casinoPlay().settle(e.getKey(), GAME, staked, e.getValue()[1]);
            long net = o.payout() - staked;
            paidTotal += o.payout();
            if (o.payout() > 0) winnersN++;
            if (testLog) plugin.getLogger().info("[Casino-Test] rennen Abrechnung: " + Bukkit.getOfflinePlayer(e.getKey()).getName()
                    + " Einsatz " + staked + ", Spiel zahlt " + e.getValue()[1] + ", ausgezahlt " + o.payout());
            CasinoFx.show(plugin, e.getKey(), fx, net > 0 ? (e.getValue()[1] >= staked * 8 ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN)
                    : net == 0 ? CasinoFx.Result.PUSH : CasinoFx.Result.LOSE, net);
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            String bl = CasinoPlay.bonusLine(o);
            CasinoText.send(p, "Sieger " + winTag + " vor " + second + (plan.photo() ? " <gray>(Foto-Finish!)" : "")
                    + (o.payout() > 0 ? " · <green>Du gewinnst +" + CasinoText.chips(o.payout()) + (bl.isEmpty() ? "" : " <dark_gray>| " + bl)
                    : " · <#FF5A5A>Leider verloren (-" + CasinoText.chips(staked) + ")"));
            plugin.casinoPlay().feedback(p, o, fx);
            if (o.payout() > 0) {
                p.showTitle(Title.title(Msg.parse("<" + hex(win) + "><b>#" + (win + 1) + " " + HorseRace.NAMES[win].toUpperCase() + "</b>"),
                        Msg.parse("<green><b>+" + CasinoText.num(net) + " Chips</b>"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            }
        }
        for (Player q : nearby(24)) {
            if (!sum.containsKey(q.getUniqueId())) q.sendActionBar(Msg.parse("<gray>Sieger " + winTag + " <gray>vor " + second));
        }
        if (testLog) plugin.getLogger().info("[Casino-Test] rennen Ziel: Sieger #" + (win + 1) + ", Zweiter #" + (order[1] + 1) + ", "
                + bets.size() + " Wetten, " + winnersN + " Gewinner, ausgezahlt " + paidTotal);
        bets.clear();
        save();
        int[] place = new int[HorseRace.HORSES];
        for (int i = 0; i < order.length; i++) place[order[i]] = i + 1;
        board.toto(field, place);
        board.history(winners);
        board.status(Msg.parse("<gray>Sieger " + winTag + " <gray>vor " + second + (plan.photo() ? " <#F2DA90>· Foto-Finish!" : "")
                + (winnersN > 0 ? " <gray>· " + winnersN + (winnersN == 1 ? " Gewinner" : " Gewinner") + " · " + CasinoText.chips(paidTotal) : "")));
    }

    /** Neues Feld: Pferde zurück in die Boxen, neue Quoten, Wetten offen. */
    private void newField() {
        field = HorseRace.field(RNG);
        phase = Phase.OPEN;
        plan = null;
        board.positions(new double[HorseRace.HORSES], null);
        board.gates(false);
        for (Player p : nearby(20)) p.sendActionBar(Msg.parse("<gray>Neues Rennen · neue Quoten · <yellow>Rechtsklick auf den Bildschirm"));
        refresh();
    }

    private void refresh() {
        if (board == null || !board.ready()) return;
        if (phase == Phase.OPEN || phase == Phase.BETTING) {
            board.toto(field, null);
            board.history(winners);
        }
        refreshStatus();
    }

    private void refreshStatus() {
        if (board == null) return;
        long pot = 0;
        for (Bet b : bets) pot += b.amount();
        switch (phase) {
            case OPEN -> board.status(Msg.parse("<white>Wetten offen <gray>· die erste Wette startet das Rennen · Sieg und Platz"));
            case BETTING -> board.status(Msg.parse("<white>Wetten offen <gray>· Start in <white>" + Math.max(0, countdown / 20) + " s"
                    + "<gray> · " + bets.size() + (bets.size() == 1 ? " Wette" : " Wetten") + " · " + CasinoText.chips(pot)));
            case RUNNING -> {
                if (plan == null || settled) return;
                int tt = Math.min(t, plan.length() - 1);
                int l = plan.leader(tt);
                board.status(Msg.parse("<#7EE07A>Rennen läuft <gray>· Führung <" + hex(l) + "><b>#" + (l + 1) + " " + HorseRace.NAMES[l] + "</b>"));
            }
            default -> {
            }
        }
    }

    // =================================================================================================================
    // Dialog und Klicks
    // =================================================================================================================

    public void open(Player p) {
        if (board == null) {
            CasinoText.send(p, "Hier gibt es noch keine Rennbahn.");
            return;
        }
        Session s = session(p.getUniqueId());
        List<CasinoScreens.RaceBetLine> mine = new ArrayList<>();
        long[][] stake = new long[HorseRace.HORSES][2];
        for (Bet b : bets) {
            if (!b.player().equals(p.getUniqueId())) continue;
            mine.add(new CasinoScreens.RaceBetLine(b.lane(), b.place(), b.amount(),
                    b.place() ? field.placeOdds()[b.lane()] : field.winOdds()[b.lane()]));
            stake[b.lane()][b.place() ? 1 : 0] += b.amount();
        }
        String status = switch (phase) {
            case OPEN -> "Wetten offen · die erste Wette startet das Rennen";
            case BETTING -> "Wetten offen · Start in " + Math.max(0, countdown / 20) + " s";
            case RUNNING -> "Das Rennen läuft";
            case RESULT -> "Gleich gibt es neue Quoten";
        };
        CasinoScreens.RaceView v = new CasinoScreens.RaceView(plugin.casino().chips(p.getUniqueId()), field, stake, mine, s.chip,
                chipValue(p), status, phase == Phase.OPEN || phase == Phase.BETTING, new ArrayList<>(winners), minBet(), maxRound(p));
        UiDialog.show(p, CasinoScreens.race(v, new CasinoScreens.RaceActions() {
            public java.util.function.Consumer<Player> bet(int lane, boolean place) {
                return pl -> {
                    String err = RaceService.this.bet(pl, lane, place);
                    if (err != null) CasinoText.error(pl, err);
                    open(pl);
                };
            }

            public java.util.function.Consumer<Player> chip(int index) {
                return pl -> {
                    session(pl.getUniqueId()).chip = index;
                    open(pl);
                };
            }

            public java.util.function.Consumer<Player> custom() {
                return pl -> CasinoInput.amount(pl, "Eigener Betrag", List.of("<gray>Wie viel soll jede Wette setzen?",
                                "<gray>Mindestens " + CasinoText.num(minBet()) + ", höchstens " + CasinoText.num(maxRound(pl)) + " Chips"),
                        minBet(), maxRound(pl), "Übernehmen", (pl2, val) -> {
                            Session q = session(pl2.getUniqueId());
                            q.custom = val;
                            q.chip = CHIPS.length;
                            open(pl2);
                        }, RaceService.this::open);
            }

            public java.util.function.Consumer<Player> clear() {
                return pl -> {
                    String err = RaceService.this.clear(pl);
                    if (err != null) CasinoText.error(pl, err);
                    open(pl);
                };
            }
        }));
    }

    private void handle(Player p) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        open(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        handle(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!e.getAttacked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        handle(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }

    // =================================================================================================================
    // Admin und Tests
    // =================================================================================================================

    /** Testbildschirm (Regeltest, Kamera). */
    public void testBoard(Location center, float yaw, double size) {
        setBoard(center, yaw, size);
    }

    private void setBoard(Location at, float yaw, double size) {
        if (board != null) board.removeAll();
        if (!settled && phase != Phase.OPEN) refundAll();
        phase = Phase.OPEN;
        board = null;
        raw = at.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + yaw + ";" + size;
        save();
        loadBoard();
        build();
        start();
    }

    /** Regeltest: Countdown auf eine Sekunde. */
    public void testFastForward() {
        if (phase == Phase.BETTING) countdown = Math.min(countdown, 20);
    }

    /** Einlauf des letzten Rennens (Spur je Platz) oder null. */
    public int[] lastOrder() {
        return lastOrder == null ? null : lastOrder.clone();
    }

    public Location boardCenter() {
        return board == null ? null : board.center();
    }

    /** /casino rennen [bildschirm [Größe] | entfernen] */
    public void command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (!p.hasPermission("kuby.admin") || sub.isEmpty()) {
            if (board != null && near(p.getLocation())) {
                handle(p);
            } else {
                CasinoText.send(p, "Das Pferderennen steht im Casino: <white>Rechtsklick auf den Rennbildschirm</white> öffnet die Wetten.");
            }
            return;
        }
        switch (sub) {
            case "bildschirm", "bahn" -> {
                double size = 0.7;
                if (args.length > 2) {
                    try {
                        size = Math.max(0.4, Math.min(1.2, Double.parseDouble(args[2].replace(',', '.'))));
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Nutzung: /casino rennen bildschirm [Größe 0,4–1,2]");
                        return;
                    }
                }
                RayTraceResult hit = p.rayTraceBlocks(10);
                if (hit == null || hit.getHitBlockFace() == null || hit.getHitBlock() == null) {
                    Msg.send(p, "<red>Schau auf die Wand, an die der Bildschirm soll (Mitte, eine Blockseite).");
                    return;
                }
                Vector n = hit.getHitBlockFace().getDirection();
                if (Math.abs(n.getY()) > 0.5) {
                    Msg.send(p, "<red>Das ist Boden oder Decke – schau auf eine Wand.");
                    return;
                }
                Vector at = hit.getHitPosition();
                Vector along = new Vector(-n.getZ(), 0, n.getX());
                double s = at.getX() * along.getX() + at.getZ() * along.getZ();
                at.add(along.clone().multiply(Math.round(s * 2) / 2.0 - s));
                double floorY = p.getLocation().getBlockY();
                at.setY(floorY + 0.9 - RaceBoard.BOTTOM * size);
                float yaw = (float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
                setBoard(at.toLocation(p.getWorld()), yaw, size);
                double w = (RaceBoard.RIGHT - RaceBoard.LEFT) * size, h = (RaceBoard.TOP - RaceBoard.BOTTOM) * size;
                Msg.send(p, "<green>Rennbildschirm aufgebaut (" + String.format(java.util.Locale.GERMAN, "%.1f x %.1f", w, h)
                        + " Blöcke, Unterkante 0,9 über dem Boden). Entfernen: /casino rennen entfernen");
            }
            case "entfernen" -> {
                if (board == null) {
                    Msg.send(p, "<red>Es gibt keinen Rennbildschirm.");
                    return;
                }
                if (!settled && phase != Phase.OPEN) refundAll();
                board.removeAll();
                board = null;
                raw = null;
                phase = Phase.OPEN;
                save();
                Msg.send(p, "<green>Rennbildschirm entfernt, offene Wetten zurückgezahlt.");
            }
            default -> Msg.send(p, "<gray>Pferderennen: auf eine Wand schauen (gut 7 Blöcke breit, 4 hoch) und <white>/casino rennen bildschirm"
                    + " [Größe 0,4–1,2]</white> (Standard 0,7). Weiter: entfernen. Einstellungen casino.rennen.wait-seconds / min-bet / max-round.");
        }
    }

    /** Für Kameras und Rundgang: Farbe einer Spur als Text-Farbe. */
    public static TextColor laneColor(int lane) {
        return TextColor.color(HorseRace.COLORS[lane]);
    }

    /** Kurzer Text für Status-Befehle. */
    public Component statusLine() {
        return Component.text("Pferderennen: " + phase.name().toLowerCase(java.util.Locale.ROOT), NamedTextColor.GRAY);
    }
}
