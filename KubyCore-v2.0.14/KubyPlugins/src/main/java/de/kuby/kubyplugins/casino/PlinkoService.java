package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.service.CasinoPlay;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plinko-Bretter im Casino (v2.05). Ein Spieler hat das Brett für sich (frei nach 30 s ohne Kugel oder wenn er
 * weggeht), wählt Risiko und Einsatz am Bedienfeld oder im Dialog (Klick aufs Brett) und lässt mit „BALL“ Kugeln
 * fallen, bis zu zehn gleichzeitig. Jede Kugel kostet beim Abwurf den Einsatz und wird abgerechnet, sobald sie im Fach
 * liegt (CasinoPlay: Serie, Goldene Zeit, Cashback, Statistik). Das Fach steht beim Abwurf fest (Zufallsweg), die
 * Flugbahn wird dazu passend geplant ({@link Plinko#flight}).
 */
public final class PlinkoService implements Placeable, Listener {

    public static final long[] LEVELS = {10, 50, 100, 500, 1000, 5000};
    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_BALLS = 10;
    private static final long LOCK_MS = 30_000;
    private static final String GAME = "plinko";

    private static final class Ball {
        final UUID owner;
        final String ownerName;
        final long bet;
        final Plinko.Risk risk;
        final Plinko.Flight flight;
        ItemDisplay entity;
        int t;
        boolean settled;
        int gone = -1;

        Ball(UUID owner, String ownerName, long bet, Plinko.Risk risk, Plinko.Flight flight) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.bet = bet;
            this.risk = risk;
            this.flight = flight;
        }
    }

    private static final class Board {
        final PlinkoBoard board;
        final List<Ball> balls = new ArrayList<>();
        final Deque<double[]> recent = new ArrayDeque<>();
        final List<TextDisplay[]> popups = new ArrayList<>();
        final List<int[]> popupAge = new ArrayList<>();
        UUID active;
        String activeName;
        long lastUse;

        Board(PlinkoBoard b) {
            this.board = b;
        }
    }

    /** Einstellungen je Spieler (bleiben bis zum Neustart). */
    private static final class Pref {
        Plinko.Risk risk = Plinko.Risk.MITTEL;
        int level = 2;
        long custom;
        final Deque<long[]> history = new ArrayDeque<>();   // {Faktor × 100, Fach, Netto}
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Map<String, String> raw = new LinkedHashMap<>();
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<UUID, Pref> prefs = new HashMap<>();
    private BukkitTask task;
    /** Für den Regeltest im Log: jede Abrechnung als Zeile. */
    public boolean testLog;

    public PlinkoService(KubyPlugins plugin) {
        this.plugin = plugin;
        // Kugeln überleben keinen Absturz: offene Plinko-Einsätze von vorher zurück
        plugin.casinoPlay().refundOpen(GAME);
        this.file = new File(plugin.getDataFolder(), "plinko-bretter.yml");
        this.tag = new NamespacedKey(plugin, "plinko");
        this.click = new NamespacedKey(plugin, "plinko_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection m = yml.getConfigurationSection("bretter");
        if (m != null) for (String id : m.getKeys(false)) raw.put(id, m.getString(id));
    }

    private Board load(String id) {
        Board b = boards.get(id);
        if (b != null) return b;
        String line = raw.get(id);
        if (line == null) return null;
        String[] p = line.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            Location c = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            b = new Board(new PlinkoBoard(id, c, Float.parseFloat(p[4]), Double.parseDouble(p[5]), tag, click));
            boards.put(id, b);
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void build(Board b) {
        b.board.build();
        refresh(b);
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void respawn() {
        for (String id : raw.keySet()) {
            Board b = load(id);
            if (b == null) continue;
            Location c = b.board.center();
            if (c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4) && !b.board.ready()) build(b);
        }
        start();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (String id : raw.keySet()) {
            Board b = load(id);
            if (b == null) continue;
            Location c = b.board.center();
            if (c.getWorld().equals(e.getWorld()) && (c.getBlockX() >> 4) == e.getChunk().getX() && (c.getBlockZ() >> 4) == e.getChunk().getZ()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!b.board.ready()) build(b);
                }, 5L);
            }
        }
    }

    /** Beim Abschalten: Kugeln in der Luft sofort abrechnen (ihr Fach steht fest), Anzeigen weg. */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Board b : boards.values()) {
            for (Ball ball : b.balls) if (!ball.settled) settle(b, ball, false);
            b.balls.clear();
            b.board.removeAll();
        }
        save();
    }

    public boolean hasBoard() {
        return !raw.isEmpty();
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : raw.entrySet()) yml.set("bretter." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("plinko-bretter.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // =================================================================================================================
    // Einsatz und Einstellungen
    // =================================================================================================================

    private Pref pref(UUID id) {
        return prefs.computeIfAbsent(id, k -> new Pref());
    }

    public long maxBet(Player p) {
        return plugin.casinoPlay().maxFor(p.getUniqueId(), plugin.getConfig().getLong("casino.plinko.max-bet", 5000));
    }

    public long minBet() {
        return plugin.getConfig().getLong("casino.plinko.min-bet", 10);
    }

    private long[] levels(Player p) {
        long[] base = LEVELS;
        if (plugin.casinoPlay().hasHighLimit(p.getUniqueId())) {
            base = java.util.Arrays.copyOf(LEVELS, LEVELS.length + 1);
            base[LEVELS.length] = maxBet(p);
        }
        return plugin.casinoPlay().rack(p.getUniqueId(), GAME, base);
    }

    private long bet(Player p) {
        Pref pr = pref(p.getUniqueId());
        if (pr.custom > 0) return Math.min(pr.custom, maxBet(p));
        long[] l = levels(p);
        return l[Math.max(0, Math.min(pr.level, l.length - 1))];
    }

    private String betText(Player p) {
        return CasinoText.num(bet(p));
    }

    /** Anzeige am Brett auf den aktiven Spieler (oder frei) bringen. */
    private void refresh(Board b) {
        if (!b.board.ready()) return;
        Player p = b.active == null ? null : Bukkit.getPlayer(b.active);
        Plinko.Risk risk = p == null ? Plinko.Risk.MITTEL : pref(p.getUniqueId()).risk;
        b.board.multipliers(risk);
        b.board.controls(p == null ? null : p.getName(), risk, p == null ? "10–5.000" : betText(p), b.balls.size());
        b.board.history(new ArrayList<>(b.recent));
    }

    /** Darf p das Brett gerade benutzen? Sonst Meldung und false. Übernimmt ein freies Brett. */
    private boolean claim(Player p, Board b) {
        UUID id = p.getUniqueId();
        if (b.active != null && !b.active.equals(id)) {
            Player o = Bukkit.getPlayer(b.active);
            boolean near = o != null && o.getWorld().equals(b.board.center().getWorld())
                    && o.getLocation().distanceSquared(b.board.center()) < 144;
            boolean busy = near && (System.currentTimeMillis() - b.lastUse < LOCK_MS || ownBalls(b, b.active) > 0);
            if (busy) {
                CasinoText.error(p, "An diesem Plinko-Brett spielt gerade " + o.getName() + ".");
                return false;
            }
        }
        if (!id.equals(b.active)) {
            b.active = id;
            b.activeName = p.getName();
            b.recent.clear();
        }
        b.lastUse = System.currentTimeMillis();
        return true;
    }

    private static int ownBalls(Board b, UUID id) {
        int n = 0;
        for (Ball ball : b.balls) if (ball.owner.equals(id) && !ball.settled) n++;
        return n;
    }

    // =================================================================================================================
    // Spielen
    // =================================================================================================================

    /** Kugel abwerfen. Rückgabe: Fehlermeldung oder null. */
    public String drop(Player p, Board b) {
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (!b.board.ready()) return "Das Brett wird gerade aufgebaut.";
        if (!claim(p, b)) return "";
        if (b.balls.size() >= MAX_BALLS) return "Höchstens " + MAX_BALLS + " Kugeln gleichzeitig – warte, bis eine liegt.";
        long bet = bet(p);
        if (bet < minBet()) return "Mindesteinsatz " + CasinoText.num(minBet()) + " Chips.";
        String err = plugin.casinoPlay().take(p, GAME, bet);
        if (err != null) return err;
        Pref pr = pref(p.getUniqueId());
        Plinko.Flight f = Plinko.flight(Plinko.path(RNG), RNG);
        Ball ball = new Ball(p.getUniqueId(), p.getName(), bet, pr.risk, f);
        ball.entity = b.board.spawnBall(f.u()[0], f.v()[0]);
        b.balls.add(ball);
        b.board.press();
        Location at = b.board.at(0, Plinko.DROP_V, 0.2);
        at.getWorld().playSound(at, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.3f);
        at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.6f);
        refresh(b);
        return null;
    }

    private void tick() {
        for (Board b : boards.values()) {
            b.board.tick();
            // freigeben: Spieler weg oder 30 s nichts mehr
            if (b.active != null && b.balls.isEmpty()) {
                Player o = Bukkit.getPlayer(b.active);
                boolean gone = o == null || !o.getWorld().equals(b.board.center().getWorld())
                        || o.getLocation().distanceSquared(b.board.center()) > 144;
                if (gone || System.currentTimeMillis() - b.lastUse > LOCK_MS) {
                    b.active = null;
                    b.activeName = null;
                    refresh(b);
                }
            }
            tickPopups(b);
            if (b.balls.isEmpty()) continue;
            boolean changed = false;
            for (Iterator<Ball> it = b.balls.iterator(); it.hasNext(); ) {
                Ball ball = it.next();
                if (!b.board.ready()) {
                    if (!ball.settled) settle(b, ball, false);
                    if (ball.entity != null) ball.entity.remove();
                    it.remove();
                    changed = true;
                    continue;
                }
                if (ball.gone >= 0) {
                    if (--ball.gone <= 0) {
                        if (ball.entity != null) ball.entity.remove();
                        it.remove();
                        changed = true;
                    }
                    continue;
                }
                ball.t++;
                Plinko.Flight f = ball.flight;
                int t = Math.min(ball.t, f.length() - 1);
                b.board.moveBall(ball.entity, f.u()[t], f.v()[t]);
                for (Plinko.Event e : f.events()) {
                    if (e.tick() != ball.t) continue;
                    if (e.row() >= 0) {
                        b.board.hitPeg(e.row(), e.peg());
                        Location l = b.board.pegLocation(e.row(), e.peg());
                        float pitch = (float) (0.85 + 0.07 * e.row() + RNG.nextDouble() * 0.06);
                        l.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, Math.min(2f, pitch));
                    } else if (e.row() == -1) {
                        settle(b, ball, true);
                        changed = true;
                    } else {
                        b.board.fadeBall(ball.entity);
                        ball.gone = 6;
                    }
                }
            }
            if (changed) refresh(b);
        }
    }

    private void tickPopups(Board b) {
        for (int i = b.popups.size() - 1; i >= 0; i--) {
            int[] age = b.popupAge.get(i);
            age[0]++;
            TextDisplay t = b.popups.get(i)[0];
            if (age[0] > 30 || t == null || !t.isValid()) {
                if (t != null) t.remove();
                b.popups.remove(i);
                b.popupAge.remove(i);
            } else if (age[0] == 1) {
                Transformation tr = t.getTransformation();
                t.setInterpolationDelay(0);
                t.setInterpolationDuration(28);
                t.setTransformation(new Transformation(new Vector3f(0, (float) (0.45 * b.board.size()), 0), tr.getLeftRotation(),
                        tr.getScale(), tr.getRightRotation()));
            } else if (age[0] == 22) {
                t.setTextOpacity((byte) 60);
            }
        }
    }

    /** Kugel liegt im Fach: abrechnen, Fach leuchtet, Ton nach Gewinnhöhe, schwebende Zahl. */
    private void settle(Board b, Ball ball, boolean effects) {
        if (ball.settled) return;
        ball.settled = true;
        int slot = ball.flight.slot();
        double mult = ball.risk.mult(slot);
        long gross = Math.round(ball.bet * mult);
        CasinoPlay.Outcome o = plugin.casinoPlay().settle(ball.owner, GAME, ball.bet, gross);
        Pref pr = pref(ball.owner);
        pr.history.addFirst(new long[]{Math.round(mult * 100), slot, o.net()});
        while (pr.history.size() > 12) pr.history.removeLast();
        if (ball.owner.equals(b.active)) {
            b.recent.addFirst(new double[]{mult, Plinko.tier(slot)});
            while (b.recent.size() > 6) b.recent.removeLast();
        }
        if (testLog) {
            plugin.getLogger().info("[Casino-Test] plinko Kugel: Risiko " + ball.risk.label + ", Fach " + slot + ", Faktor "
                    + Plinko.mult(mult) + ", Einsatz " + ball.bet + ", Spiel zahlt " + gross + ", ausgezahlt " + o.payout());
        }
        if (!effects) return;
        b.board.hitSlot(slot);
        Location at = b.board.slotLocation(slot);
        Player p = Bukkit.getPlayer(ball.owner);
        long net = o.payout() + o.cashback() - ball.bet;
        TextColor col = net > 0 ? TextColor.color(0x6BF07A) : net == 0 ? TextColor.color(0xE8E0D8) : TextColor.color(0xFF7A7A);
        TextDisplay pop = b.board.popup(slot, Component.text((net > 0 ? "+" : net < 0 ? "−" : "±") + CasinoText.num(Math.abs(net)), col,
                TextDecoration.BOLD));
        b.popups.add(new TextDisplay[]{pop});
        b.popupAge.add(new int[]{0});
        if (mult >= 10) {
            at.getWorld().playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            de.kuby.kubyplugins.util.Fx.particle(plugin, at, Particle.FIREWORK, 30, 0.25, 0.2, 0.1, 0.06);
            de.kuby.kubyplugins.util.Fx.particle(plugin, at, Particle.END_ROD, 12, 0.3, 0.3, 0.1, 0.02);
        } else if (mult >= 2) {
            at.getWorld().playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
            de.kuby.kubyplugins.util.Fx.particle(plugin, at, Particle.HAPPY_VILLAGER, 8, 0.2, 0.15, 0.1, 0);
        } else if (mult > 1) {
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
        } else {
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, mult >= 1 ? 1.0f : 0.7f);
        }
        if (p == null) return;
        String unit = " Chips";
        String b2 = CasinoPlay.bonusLine(o);
        p.sendActionBar(Msg.parse("<#F2DA90>" + Plinko.mult(mult) + " <gray>· " + (net >= 0 ? "<green>+" : "<red>−")
                + CasinoText.num(Math.abs(net)) + unit + (b2.isEmpty() ? "" : " <dark_gray>| " + b2)));
        if (mult < 1) plugin.casinoPlay().feedback(p, o, at);
        if (mult >= 50) {
            CasinoFx.show(plugin, ball.owner, at, CasinoFx.Result.BIG_WIN, net);
            plugin.notifications().send("notify.jackpot", CasinoText.PREFIX + "<yellow><b>" + Msg.escape(ball.ownerName)
                    + "</b> trifft beim Plinko " + Plinko.mult(mult) + ": <gold>+" + CasinoText.chips(net) + "</gold>!");
        }
    }

    // =================================================================================================================
    // Klicks und Dialog
    // =================================================================================================================

    private void handle(Player p, Entity e) {
        String v = e.getPersistentDataContainer().get(click, PersistentDataType.STRING);
        if (v == null) return;
        int k = v.lastIndexOf(':');
        Board b = load(v.substring(0, k));
        if (b == null) return;
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        String what = v.substring(k + 1);
        switch (what) {
            case "drop" -> {
                String err = drop(p, b);
                if (err != null && !err.isEmpty()) CasinoText.error(p, err);
            }
            case "bet" -> {
                if (!claim(p, b)) return;
                Pref pr = pref(p.getUniqueId());
                long[] l = levels(p);
                if (pr.custom > 0) {
                    // vom eigenen Einsatz zur nächsten Stufe
                    int i = 0;
                    while (i < l.length - 1 && l[i] < pr.custom) i++;
                    pr.level = p.isSneaking() ? Math.max(0, i - 1) : i;
                    pr.custom = 0;
                } else {
                    pr.level = p.isSneaking() ? Math.max(0, pr.level - 1) : Math.min(l.length - 1, pr.level + 1);
                }
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.4f);
                p.sendActionBar(Msg.parse("<gray>Einsatz: <white>" + betText(p) + " Chips</white> · Klick: höher, Schleichen + Klick: tiefer"));
                refresh(b);
            }
            case "board" -> openDialog(p, b);
            default -> {
                if (what.startsWith("risk")) {
                    if (!claim(p, b)) return;
                    int i = Math.max(0, Math.min(2, what.charAt(4) - '0'));
                    pref(p.getUniqueId()).risk = Plinko.Risk.values()[i];
                    p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.2f + 0.15f * i);
                    Plinko.Risk r = Plinko.Risk.values()[i];
                    p.sendActionBar(Msg.parse("<gray>Risiko: <white>" + r.label + "</white> · bis <#F2DA90>" + Plinko.mult(r.max())
                            + "</#F2DA90> · Mitte " + Plinko.mult(r.mult(6))));
                    refresh(b);
                }
            }
        }
    }

    private void openDialog(Player p, Board b) {
        Pref pr = pref(p.getUniqueId());
        long[] l = levels(p);
        int idx = pr.custom > 0 ? -1 : Math.max(0, Math.min(pr.level, l.length - 1));
        List<CasinoScreens.PlinkoResult> recent = new ArrayList<>();
        for (long[] h : pr.history) recent.add(new CasinoScreens.PlinkoResult(h[0] / 100.0, (int) h[1], h[2]));
        String status;
        if (b.active != null && !b.active.equals(p.getUniqueId())) {
            status = "Gerade spielt " + b.activeName;
        } else {
            int mine = ownBalls(b, p.getUniqueId());
            status = mine > 0 ? mine + (mine == 1 ? " Kugel" : " Kugeln") + " im Fall" : "Wähle Risiko und Einsatz, dann BALL";
        }
        CasinoScreens.PlinkoView view = new CasinoScreens.PlinkoView(plugin.casino().chips(p.getUniqueId()), pr.risk, bet(p), l, idx,
                status, recent, minBet(), maxBet(p));
        UiDialog.show(p, CasinoScreens.plinko(view, new CasinoScreens.PlinkoActions() {
            public java.util.function.Consumer<Player> risk(int i) {
                return pl -> {
                    if (!claim(pl, b)) return;
                    pref(pl.getUniqueId()).risk = Plinko.Risk.values()[i];
                    refresh(b);
                    openDialog(pl, b);
                };
            }

            public java.util.function.Consumer<Player> level(int i) {
                return pl -> {
                    if (!claim(pl, b)) return;
                    Pref q = pref(pl.getUniqueId());
                    q.level = i;
                    q.custom = 0;
                    refresh(b);
                    openDialog(pl, b);
                };
            }

            public java.util.function.Consumer<Player> custom() {
                return pl -> CasinoInput.amount(pl, "Eigener Einsatz", List.of("<gray>Wie viel soll jede Kugel kosten?",
                                "<gray>Mindestens " + CasinoText.num(minBet()) + ", höchstens " + CasinoText.num(maxBet(pl)) + " Chips"),
                        minBet(), maxBet(pl), "Übernehmen", (pl2, val) -> {
                            if (!claim(pl2, b)) return;
                            pref(pl2.getUniqueId()).custom = val;
                            refresh(b);
                            openDialog(pl2, b);
                        }, pl2 -> openDialog(pl2, b));
            }

            public java.util.function.Consumer<Player> drop() {
                return pl -> {
                    String err = PlinkoService.this.drop(pl, b);
                    if (err != null && !err.isEmpty()) CasinoText.error(pl, err);
                };
            }
        }));
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
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Board b : boards.values()) {
            if (id.equals(b.active) && ownBalls(b, id) == 0) {
                b.active = null;
                refresh(b);
            }
        }
    }

    // =================================================================================================================
    // Admin und Tests
    // =================================================================================================================

    /** Testbrett (Regeltest, Kamera): Mitte auf der Wandfläche, Blickrichtung zum Spieler. */
    public void testBoard(String id, Location center, float yaw, double size) {
        raw.put(id, center.getWorld().getName() + ";" + center.getX() + ";" + center.getY() + ";" + center.getZ() + ";" + yaw + ";" + size);
        save();
        Board old = boards.remove(id);
        if (old != null) old.board.removeAll();
        Board b = load(id);
        if (b != null) build(b);
        start();
    }

    /** Regeltest: Kugeln wie ein Spieler abwerfen (Risiko, Anzahl); Rückgabe: Fehlermeldung oder null. */
    public String testDrop(Player p, String id, Plinko.Risk risk, int n) {
        Board b = load(id);
        if (b == null) return "kein Brett " + id;
        pref(p.getUniqueId()).risk = risk;
        for (int i = 0; i < n; i++) {
            String err = drop(p, b);
            if (err != null) return err.isEmpty() ? "Brett besetzt" : err;
        }
        return null;
    }

    /** Mitte des Bretts id (für die Kamera), sonst null. */
    public Location boardCenter(String id) {
        Board b = load(id);
        return b == null ? null : b.board.center();
    }

    public PlinkoBoard board(String id) {
        Board b = load(id);
        return b == null ? null : b.board;
    }

    /** /casino plinko [automat [Größe] | entfernen | liste] */
    // ---------- Casino-Admin (v2.07) ----------

    @Override
    public List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        int i = 1;
        for (String id : raw.keySet()) {
            Board b = load(id);
            if (b == null) continue;
            boolean busy = !b.balls.isEmpty();
            out.add(new Piece(id, "Plinko-Brett " + i++, b.board.center(), (float) Placeable.fieldValue(raw.get(id), 4, 0), b.board.size(),
                    busy ? b.balls.size() + " Kugeln unterwegs" : b.active != null ? "besetzt" : "frei", busy));
        }
        return out;
    }

    private String rebuild(String id, String line) {
        Board b = load(id);
        if (b == null) return "Brett nicht gefunden.";
        if (!b.balls.isEmpty()) return "Es fallen noch Kugeln – gleich noch einmal.";
        b.board.removeAll();
        boards.remove(id);
        raw.put(id, line);
        save();
        Board n = load(id);
        if (n == null) return "Brett ließ sich nicht neu laden.";
        build(n);
        return null;
    }

    @Override
    public String move(String id, double dx, double dy, double dz, float dyaw) {
        if (!raw.containsKey(id)) return "Brett nicht gefunden.";
        return rebuild(id, Placeable.shift(raw.get(id), dx, dy, dz, dyaw));
    }

    @Override
    public String resize(String id, double delta) {
        if (!raw.containsKey(id)) return "Brett nicht gefunden.";
        double v = Math.max(0.6, Math.min(1.4, Placeable.fieldValue(raw.get(id), 5, 0.8) + delta));
        return rebuild(id, Placeable.field(raw.get(id), 5, v));
    }

    @Override
    public double sizeStep() {
        return 0.1;
    }

    @Override
    public String remove(String id) {
        Board b = load(id);
        if (b == null) return "Brett nicht gefunden.";
        for (Ball ball : b.balls) if (!ball.settled) settle(b, ball, false);
        b.balls.clear();
        b.board.removeAll();
        boards.remove(id);
        raw.remove(id);
        save();
        return null;
    }

    public void command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (!p.hasPermission("kuby.admin") || sub.isEmpty()) {
            // Spieler: Dialog des nächsten Bretts öffnen
            Board best = nearest(p, 10);
            if (best == null) {
                Msg.send(p, CasinoText.PREFIX + "<gray>Plinko findest du im Casino an der Wand. Klick aufs Brett öffnet Risiko und Einsatz,"
                        + " <white>BALL</white> lässt eine Kugel fallen.");
                return;
            }
            openDialog(p, best);
            return;
        }
        switch (sub) {
            case "automat", "brett" -> {
                double size = 0.8;
                if (args.length > 2) {
                    try {
                        size = Math.max(0.6, Math.min(1.4, Double.parseDouble(args[2].replace(',', '.'))));
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Nutzung: /casino plinko automat [Größe 0,6–1,4]");
                        return;
                    }
                }
                RayTraceResult hit = p.rayTraceBlocks(8);
                if (hit == null || hit.getHitBlockFace() == null || hit.getHitBlock() == null) {
                    Msg.send(p, "<red>Schau auf die Wand, an die das Brett soll (eine Blockseite, etwa in Brusthöhe).");
                    return;
                }
                Vector n = hit.getHitBlockFace().getDirection();
                if (Math.abs(n.getY()) > 0.5) {
                    Msg.send(p, "<red>Das ist Boden oder Decke – schau auf eine Wand.");
                    return;
                }
                Vector at = hit.getHitPosition();
                // entlang der Wand auf halbe Blöcke einrasten, Höhe vom Boden unter dir: Unterkante 0,75 über dem Boden
                Vector along = new Vector(-n.getZ(), 0, n.getX());
                double s = at.getX() * along.getX() + at.getZ() * along.getZ();
                double snapped = Math.round(s * 2) / 2.0;
                at.add(along.clone().multiply(snapped - s));
                double floorY = p.getLocation().getBlockY();
                at.setY(floorY + 0.75 - PlinkoBoard.BOTTOM * size);
                float yaw = (float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
                String id = "p" + System.currentTimeMillis() % 100000;
                raw.put(id, p.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + yaw + ";" + size);
                save();
                Board b = load(id);
                if (b != null) build(b);
                start();
                double h = (PlinkoBoard.TOP - PlinkoBoard.BOTTOM) * size;
                Msg.send(p, "<green>Plinko-Brett aufgebaut (Größe " + String.format(java.util.Locale.GERMAN, "%.2f", size) + ", "
                        + String.format(java.util.Locale.GERMAN, "%.1f x %.1f", 3.8 * size, h) + " Blöcke, bis "
                        + String.format(java.util.Locale.GERMAN, "%.1f", 0.75 + h) + " über dem Boden). Entfernen: /casino plinko entfernen");
            }
            case "entfernen" -> {
                Board best = nearest(p, 8);
                if (best == null) {
                    Msg.send(p, "<red>Kein Plinko-Brett im Umkreis von 8 Blöcken.");
                    return;
                }
                for (Ball ball : best.balls) if (!ball.settled) settle(best, ball, false);
                best.balls.clear();
                best.board.removeAll();
                boards.remove(best.board.id());
                raw.remove(best.board.id());
                save();
                Msg.send(p, "<green>Plinko-Brett entfernt.");
            }
            default -> Msg.send(p, "<gray>" + raw.size() + " Plinko-Bretter. Aufbauen: an eine Wand (gut 5 Blöcke hoch, 3 breit) stellen,"
                    + " darauf schauen und <white>/casino plinko automat [Größe 0,6–1,4]</white> (Standard 0,8). Weiter: entfernen."
                    + " Einstellungen: casino.plinko.min-bet / max-bet.");
        }
    }

    private Board nearest(Player p, double range) {
        Board best = null;
        double bd = range * range;
        for (String id : raw.keySet()) {
            Board b = load(id);
            if (b == null || !b.board.center().getWorld().equals(p.getWorld())) continue;
            double d = b.board.center().distanceSquared(p.getLocation());
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    /** Alle aufgebauten Bretter (für den Rundgang). */
    public List<PlinkoBoard> boardList() {
        List<PlinkoBoard> l = new ArrayList<>();
        for (String id : raw.keySet()) {
            Board b = load(id);
            if (b != null) l.add(b.board);
        }
        return l;
    }

    /** Kurzinfo zu Kugeln in der Luft (Status). */
    public int ballsInFlight() {
        int n = 0;
        for (Board b : boards.values()) n += b.balls.size();
        return n;
    }
}
