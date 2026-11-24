package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Slot-Automaten wie im Clip: Hebel ziehen dreht drei Walzen, die nacheinander stehen bleiben. Gewertet wird die
 * Mittellinie. Einsatz 10 bis 5.000 (Knopf: höher, Schleichen + Knopf: tiefer). Drei Siebener knacken den
 * Slots-Jackpot (wächst mit jedem Dreh). Rückfluss ohne Jackpot rund 95 %.
 */
public final class SlotsService implements Listener {

    public static final long[] LEVELS = {10, 50, 100, 500, 1000, 5000};
    /** Symbole, Gewichte und Gewinne kommen je Automat aus seinem Thema (SlotTheme, v2.05). */
    private static final SecureRandom RNG = new SecureRandom();

    private static final class State {
        final SlotMachine machine;
        final String[][] shown = new String[3][3];
        boolean busy;
        UUID seated;

        State(SlotMachine m) {
            this.machine = m;
            for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) shown[i][k] = m.theme().symbols[m.theme().pick(RNG)];
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Map<String, String> raw = new LinkedHashMap<>();
    private final Map<String, State> machines = new LinkedHashMap<>();
    private final Map<UUID, Integer> level = new HashMap<>();
    private long jackpot;

    /** Alle aufgebauten Automaten (für den Rundgang). */
    public java.util.List<SlotMachine> machineList() {
        java.util.List<SlotMachine> l = new java.util.ArrayList<>();
        for (State st : machines.values()) l.add(st.machine);
        return l;
    }

    public SlotsService(KubyPlugins plugin) {
        this.plugin = plugin;
        // Drehungen überleben keinen Neustart: offene Slot-Einsätze von vorher zurück
        plugin.casinoPlay().refundOpen("slots");
        this.file = new File(plugin.getDataFolder(), "slots-automaten.yml");
        this.tag = new NamespacedKey(plugin, "slot_machine");
        this.click = new NamespacedKey(plugin, "slot_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection m = yml.getConfigurationSection("automaten");
        if (m != null) for (String id : m.getKeys(false)) raw.put(id, m.getString(id));
        jackpot = yml.getLong("jackpot", startJackpot());
    }

    private long startJackpot() {
        return plugin.getConfig().getLong("casino.slots.jackpot-start", 20000);
    }

    /**
     * Hocker vor das Pult auf die Mittelachse des Automaten (v2.05 „Hocker näher dran“, v2.07 „die Hocker sind zu nah“):
     * die Sitzfläche (0,5 breit) endet casino.slots.hocker-luft vor der Pultkante (Standard 0,3: Platz für die Beine, der
     * Sitzende steckt nicht im Pult). Neuer Schlüssel, damit der alte gespeicherte Wert 0,07 (hocker-abstand) nicht mehr
     * greift. Höhe bleibt die gespeicherte (Boden). Gilt beim nächsten Laden auch für schon aufgestellte Automaten.
     */
    private Location closeStool(Location center, float yaw, int desk, double floorY) {
        double r = Math.toRadians(yaw);
        double gap = Math.max(0, Math.min(1.5, plugin.getConfig().getDouble("casino.slots.hocker-luft", 0.3)));
        double dist = desk > 0 ? desk - 0.03 + 0.25 + gap : 0.85;
        return new Location(center.getWorld(), center.getX() - Math.sin(r) * dist, floorY, center.getZ() + Math.cos(r) * dist);
    }

    private State load(String id) {
        State s = machines.get(id);
        if (s != null) return s;
        String line = raw.get(id);
        if (line == null) return null;
        String[] p = line.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null || p.length < 6) return null;
        try {
            Location c = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            Location stool = p.length >= 9 ? new Location(w, Double.parseDouble(p[6]), Double.parseDouble(p[7]), Double.parseDouble(p[8])) : null;
            int desk = p.length >= 10 ? Integer.parseInt(p[9]) : 1;
            String style = p.length >= 11 ? p[10] : "klassik";
            SlotTheme theme = p.length >= 12 ? SlotTheme.byName(p[11]) : null;
            float yaw = Float.parseFloat(p[4]);
            if (stool != null) stool = closeStool(c, yaw, desk, stool.getY());
            s = new State(new SlotMachine(id, c, yaw, Double.parseDouble(p[5]), desk, stool, tag, click, style, theme));
            machines.put(id, s);
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void respawn() {
        for (String id : raw.keySet()) {
            State s = load(id);
            if (s == null) continue;
            Location c = s.machine.center();
            if (c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4) && !s.machine.ready()) build(s);
        }
    }

    private void build(State s) {
        s.machine.build(s.shown);
        s.machine.panel(LEVELS, 0);
        s.machine.holo(jackpot, LEVELS[0], LEVELS[LEVELS.length - 1]);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (String id : raw.keySet()) {
            State s = load(id);
            if (s == null || s.busy) continue;
            Location c = s.machine.center();
            if (c.getWorld().equals(e.getWorld()) && (c.getBlockX() >> 4) == e.getChunk().getX() && (c.getBlockZ() >> 4) == e.getChunk().getZ()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!s.machine.ready()) build(s);
                }, 5L);
            }
        }
    }

    public void shutdown() {
        for (State s : machines.values()) s.machine.removeAll();
        save();
    }

    public boolean hasMachine() {
        return !raw.isEmpty();
    }

    public long jackpot() {
        return jackpot;
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : raw.entrySet()) yml.set("automaten." + e.getKey(), e.getValue());
        yml.set("jackpot", jackpot);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("slots-automaten.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void updateHolos() {
        for (State s : machines.values()) s.machine.holo(jackpot, LEVELS[0], LEVELS[LEVELS.length - 1]);
    }

    // =================================================================================================================
    // Spielen
    // =================================================================================================================

    private int levelOf(Player p) {
        // Juwelen haben weniger Stufen als Chips nach dem ersten Jackpot: nie über die letzte Stufe hinaus
        return Math.min(level.getOrDefault(p.getUniqueId(), 0), levels(p).length - 1);
    }

    /** Einsatz-Stufen in der Währung des Spielers (Juwelen für VIP: 50 bis 10.000). */
    private long[] levels(Player p) {
        long[] base = LEVELS;
        if (plugin.casinoPlay().hasHighLimit(p.getUniqueId())) {
            // nach dem ersten Jackpot: dreifacher Höchsteinsatz (eine Stufe mehr)
            base = java.util.Arrays.copyOf(LEVELS, LEVELS.length + 1);
            base[LEVELS.length] = plugin.casinoPlay().maxFor(p.getUniqueId(), LEVELS[LEVELS.length - 1]);
        }
        return plugin.casinoPlay().rack(p.getUniqueId(), "slots", base);
    }

    private boolean jewels(Player p) {
        return plugin.casinoPlay().cur(p.getUniqueId(), "slots") == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS;
    }

    private void pull(Player p, State s) {
        if (s.busy) return;
        if (s.seated != null && !s.seated.equals(p.getUniqueId())) {
            CasinoText.error(p, "An diesem Automaten sitzt schon jemand.");
            return;
        }
        String closed = plugin.casino().closedMessage();
        if (closed != null) {
            CasinoText.error(p, closed);
            return;
        }
        long bet = levels(p)[levelOf(p)];
        boolean jw = jewels(p);
        String err = plugin.casinoPlay().take(p, "slots", bet);
        if (err != null) {
            CasinoText.error(p, err);
            return;
        }
        if (!jw) jackpot += Math.max(1, Math.round(bet * plugin.getConfig().getDouble("casino.slots.jackpot-share", 0.02)));
        s.busy = true;
        SlotTheme th = s.machine.theme();
        int[] result = {th.pick(RNG), th.pick(RNG), th.pick(RNG)};
        s.machine.lever(true);
        s.machine.lamps(true);
        Location fx = s.machine.effectLocation();
        fx.getWorld().playSound(fx, Sound.BLOCK_LEVER_CLICK, 0.8f, 0.8f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> s.machine.lever(false), 12L);
        // Echte Walzen (v1.73): jede Walze dreht, bremst und rastet mit Bounce ein; zwei gleiche → die letzte Walze
        // kriecht Symbol für Symbol (Kino-Kamera, Herzschlag, Münzgeklimper) und rastet dann plötzlich ein.
        boolean tension = result[0] == result[1];
        int cherries = 0;
        for (int x : result) if (x == 0) cherries++;
        boolean three = result[0] == result[1] && result[1] == result[2];
        boolean win = three || cherries > 0;
        // Dreh-Tempo je Runde zufällig (kurz, normal, lang, einzeln, zusammen), unabhängig vom Ergebnis
        SlotSpin.Tempo tempo = plugin.getConfig().getBoolean("casino.slots.tempo-wechsel", true)
                ? SlotSpin.pickTempo(RNG) : SlotSpin.Tempo.NORMAL;
        int[] arrive = SlotSpin.arrivals(tempo, RNG, tension);
        SlotSpin[] spin = new SlotSpin[3];
        for (int i = 0; i < 3; i++) {
            double from = s.machine.reelAngle(i);
            double to = s.machine.stopAngle(i, th.symbols[result[i]]);
            spin[i] = SlotSpin.plan(from, to, arrive[i], tension && i == 2 ? arrive[1] + 6 : -1);
        }
        int len = 0;
        for (SlotSpin sp : spin) len = Math.max(len, sp.length());
        final int end = len;
        java.util.UUID pid = p.getUniqueId();
        plugin.getServer().getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
            int t;

            @Override
            public void accept(org.bukkit.scheduler.BukkitTask task) {
                if (!s.machine.ready()) {
                    // Automat mitten in der Drehung abgebaut/entladen: Einsatz zurück (vorher ging er verloren)
                    task.cancel();
                    s.busy = false;
                    plugin.casinoPlay().refund(pid, "slots", bet);
                    return;
                }
                boolean fast = false;
                for (int i = 0; i < 3; i++) {
                    SlotSpin sp = spin[i];
                    if (t >= sp.length()) continue;
                    double v = sp.speed[t];
                    fast |= Math.abs(v) > 14;
                    s.machine.reel(i, sp.angle[t], Math.abs(v) > 14, 1);
                    // Ratsche: im langsamen Lauf klickt jedes Symbol, das an der Linie vorbeikommt
                    if (t > 0 && Math.abs(v) > 0.5 && Math.abs(v) <= 14
                            && Math.floor((sp.angle[t - 1] + 18) / 36) != Math.floor((sp.angle[t] + 18) / 36)) {
                        fx.getWorld().playSound(fx, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.55f, 1.5f + 0.1f * i);
                    }
                    if (t == sp.arrive) {
                        // Einrasten: Klonk, Bass, kurzer Ruck im Gehäuse
                        fx.getWorld().playSound(fx, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 0.7f + 0.12f * i);
                        fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.7f);
                        s.machine.shake(plugin, 4, 0.012);
                        de.kuby.kubyplugins.util.Fx.particle(plugin, s.machine.reelFront(i), Particle.CRIT, 6, 0.15, 0.1, 0.05, 0.05);
                    }
                    if (t == sp.length() - 1) s.shown[i] = s.machine.visible(i, sp.angle[t]);
                }
                if (fast && t % 3 == 0) fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.7f);
                // Spannung: Kamera auf die letzte Walze, sobald die zweite das zweite gleiche Symbol zeigt
                if (tension && t == spin[1].arrive) tensionCam(p, s, spin[2].arrive - t, th.symbols[result[0]], win, pid, spin[2], t);
                if (t == spin[2].arrive && win) {
                    s.machine.coinBurst(plugin, three ? 70 : 14);
                    s.machine.shake(plugin, three ? 28 : 10, three ? 0.06 : 0.022);
                    fx.getWorld().playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, three ? 0.5f : 0.15f, 1.5f);
                }
                t++;
                if (t >= end) {
                    task.cancel();
                    s.busy = false;
                    s.machine.lamps(false);
                    if (plugin.casinoPlay().testLog) {
                        StringBuilder want = new StringBuilder(), got = new StringBuilder();
                        for (int i = 0; i < 3; i++) {
                            want.append(th.symbols[result[i]]).append(' ');
                            got.append(s.shown[i][1]).append(' ');
                        }
                        plugin.getLogger().info("[Casino-Test] slots Walzen: Ergebnis " + want.toString().trim() + " | Mittellinie nach dem Einrasten "
                                + got.toString().trim() + " | Tempo " + tempo + " (" + spin[0].arrive + "/" + spin[1].arrive + "/"
                                + spin[2].arrive + " Ticks)" + (tension ? " | Spannung" : ""));
                    }
                    finish(p, s, result, bet, jw);
                }
            }
        }, 1L, 1L);
    }

    /** Kino-Kamera auf die letzte Walze (nur für den Spieler am Automaten); danach wieder auf den Hocker. */
    private void tensionCam(Player p, State s, int snap, String sym, boolean win, java.util.UUID pid, SlotSpin last, int tNow) {
        if (!p.isOnline() || plugin.cinema() == null || de.kuby.kubyplugins.cinema.Cinema.busy(pid)) return;
        if (!plugin.getConfig().getBoolean("casino.slots.kino", true)) return;
        de.kuby.kubyplugins.cinema.V3[] faces = new de.kuby.kubyplugins.cinema.V3[3];
        for (int i = 0; i < 3; i++) {
            Location f = s.machine.reelFront(i);
            faces[i] = new de.kuby.kubyplugins.cinema.V3(f.getX(), f.getY(), f.getZ());
        }
        org.bukkit.util.Vector n = s.machine.frontVector();
        // was die letzte Walze in jedem Bild der Szene auf der Gewinnlinie zeigt (das Licht folgt dem Symbol, v2.02);
        // die Szene beginnt einen Tick später als jetzt
        int n2 = Math.max(20, snap) + 40;
        String[] reelSym = new String[n2];
        double[] reelCenter = new double[n2];
        for (int k = 0; k < n2; k++) {
            int gt = Math.min(last.length() - 1, tNow + 1 + k);
            double a = last.angle[Math.max(0, gt)];
            reelSym[k] = s.machine.visible(2, a)[1];
            double x = -a / 36.0;
            reelCenter[k] = 1 - 2 * Math.abs(x - Math.round(x));
        }
        var script = new de.kuby.kubyplugins.cinema.scenes.SlotTension(faces,
                new de.kuby.kubyplugins.cinema.V3(n.getX(), 0, n.getZ()), snap, sym, win, null, System.nanoTime(), reelSym, reelCenter);
        de.kuby.kubyplugins.cinema.Cinema.Options o = new de.kuby.kubyplugins.cinema.Cinema.Options();
        o.anchor = s.machine.center();
        o.skippable = false;
        o.onEnd = (pp, res) -> {
            // zurück auf den Hocker
            if (!pp.isOnline() || pp.getVehicle() != null) return;
            if (s.seated != null && !s.seated.equals(pp.getUniqueId())) return;
            var seat = s.machine.seat();
            if (seat == null) return;
            seat.addPassenger(pp);
            s.seated = pp.getUniqueId();
        };
        plugin.cinema().play(p, script, o);
    }

    /** Kino-Szene beim Geschäftsführer (Büro als Kulisse 90 Blöcke über dem Automaten). */
    private void managerVisit(Player p, Location near) {
        if (!p.isOnline() || plugin.cinema() == null || de.kuby.kubyplugins.cinema.Cinema.busy(p.getUniqueId())) {
            if (p.isOnline()) CasinoText.send(p, "<gold>Der Geschäftsführer gratuliert: ab heute gelten für dich <b>dreifache Höchsteinsätze</b> im ganzen Casino.");
            return;
        }
        var office = new de.kuby.kubyplugins.cinema.V3(near.getBlockX() + 0.5, Math.min(near.getWorld().getMaxHeight() - 12, near.getBlockY() + 90),
                near.getBlockZ() + 0.5);
        de.kuby.kubyplugins.cinema.Cinema.Options o = new de.kuby.kubyplugins.cinema.Cinema.Options();
        o.anchor = near;
        o.skippable = true;
        o.onEnd = (pp, res) -> CasinoText.send(pp, "<gold>High Roller: <white>ab heute dreifache Höchsteinsätze an jedem Tisch und Automaten"
                + " <gray>(Chips; Juwelen-Grenzen bleiben).");
        plugin.cinema().play(p, new de.kuby.kubyplugins.cinema.scenes.ManagerVisit(office), o);
    }

    private void finish(Player p, State s, int[] r, long bet, boolean jw) {
        long pay;
        String what;
        boolean jack = false;
        SlotTheme th = s.machine.theme();
        int cherries = 0;
        for (int x : r) if (x == 0) cherries++;
        if (r[0] == r[1] && r[1] == r[2]) {
            if (r[0] == 9 && jw) {
                pay = bet * 100;
                what = "Drei " + th.plurals[9] + "!";
            } else if (r[0] == 9) {
                long share = Math.max(bet * 100, jackpot * bet / LEVELS[LEVELS.length - 1]);
                share = Math.min(share, jackpot);
                pay = share;
                jackpot -= share;
                if (jackpot < startJackpot()) jackpot = startJackpot();
                what = "JACKPOT! Drei " + th.plurals[9];
                jack = true;
            } else {
                pay = Math.round(bet * th.three[r[0]]);
                what = "Drei " + th.plurals[r[0]] + "!";
            }
        } else if (cherries == 2) {
            pay = Math.round(bet * th.two);
            what = "Zwei " + th.plurals[0] + "!";
        } else if (cherries == 1) {
            pay = Math.round(bet * th.one);
            what = th.names[0] + " auf der Linie!";
        } else {
            pay = 0;
            what = null;
        }
        de.kuby.kubyplugins.service.CasinoPlay.Outcome o = plugin.casinoPlay().settle(p.getUniqueId(), "slots", bet, pay);
        Location fx = s.machine.effectLocation();
        String unit = jw ? " Juwelen" : " Chips";
        if (pay > 0) {
            de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.HAPPY_VILLAGER, 14 + (int) Math.min(30, pay / bet * 2), 0.5, 0.3, 0.2, 0);
            de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.FIREWORK, pay >= bet * 10 ? 30 : 6, 0.4, 0.3, 0.2, 0.05);
            p.playSound(fx, pay >= bet * 10 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            String b = de.kuby.kubyplugins.service.CasinoPlay.bonusLine(o);
            p.sendActionBar(Msg.parse("<green>+" + CasinoText.num(o.payout()) + unit + " <gray>· <white>" + what
                    + (b.isEmpty() ? "" : " <dark_gray>| " + b)));
            if (o.streak() >= 3) de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.FLAME, 6 + o.streak(), 0.4, 0.3, 0.2, 0.01);
        } else {
            // knapp daneben: zwei gleiche Symbole auf der Linie
            boolean near = r[0] == r[1] || r[1] == r[2] || r[0] == r[2];
            plugin.casinoPlay().feedback(p, o, fx);
            if (near) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.sendActionBar(Msg.parse(
                        "<yellow>Knapp!</yellow> <gray>Zwei gleiche · Einsatz <white>" + CasinoText.num(bet) + unit + "</white> · Jackpot <yellow>"
                                + CasinoText.num(jackpot))), 30L);
                p.playSound(fx, Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.9f);
            }
        }
        if (jack) {
            p.showTitle(Title.title(Msg.parse("<b><gradient:#FFE070:#F2A21C>JACKPOT!</gradient></b>"),
                    Msg.parse("<yellow>+" + CasinoText.chips(o.payout())), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
            CasinoFx.jackpot(plugin, p.getUniqueId(), fx, pay);
            plugin.notifications().send("notify.jackpot", CasinoText.PREFIX + "<yellow><b>" + Msg.escape(p.getName()) + "</b> knackt den Slots-Jackpot: <gold>"
                    + CasinoText.chips(o.payout()) + "</gold>!");
            // erster Jackpot: der Gastgeber begleitet zum Geschäftsführer, danach dreifache Höchsteinsätze
            if (plugin.casinoPlay().firstJackpot(p.getUniqueId())) {
                Location c = s.machine.center();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> managerVisit(p, c), 100L);
            }
        } else if (pay >= bet * 20) {
            CasinoFx.show(plugin, p.getUniqueId(), fx, CasinoFx.Result.BIG_WIN, o.payout() - bet);
        }
        updateHolos();
        save();
    }


    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    private void handle(Player p, Entity e) {
        String v = e.getPersistentDataContainer().get(click, PersistentDataType.STRING);
        if (v == null) return;
        String[] parts = v.split(":");
        State s = load(parts[0]);
        if (s == null) return;
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        switch (parts[1]) {
            case "lever" -> pull(p, s);
            case "button" -> {
                int l = levelOf(p);
                l = p.isSneaking() ? Math.max(0, l - 1) : Math.min(levels(p).length - 1, l + 1);
                level.put(p.getUniqueId(), l);
                s.machine.panel(levels(p), l);
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.4f);
                p.sendActionBar(Msg.parse("<gray>Einsatz: <white>" + plugin.casinoPlay().unit(p.getUniqueId(), "slots", levels(p)[l])
                        + "</white> · Knopf: höher, Schleichen + Knopf: tiefer"));
            }
            case "panel", "sign" -> UiDialog.show(p, CasinoScreens.slotsPaytable(s.machine.theme(), jackpot, levels(p)[levelOf(p)]));
            case "seat" -> {
                if (s.seated != null && !s.seated.equals(p.getUniqueId())) {
                    CasinoText.error(p, "Der Hocker ist besetzt.");
                    return;
                }
                if (p.getVehicle() != null) return;
                var seat = s.machine.seat();
                if (seat == null) return;
                seat.addPassenger(p);
                s.seated = p.getUniqueId();
                s.machine.panel(levels(p), levelOf(p));
                p.sendActionBar(Msg.parse("<gray>Du sitzt — aufstehen mit Schleichen."));
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
        Location floor = stand.clone().add(0, -0.6, 0);
        de.kuby.kubyplugins.util.SafeStand.later(plugin, p, floor,
                de.kuby.kubyplugins.util.SafeStand.facing(stand.getYaw()).multiply(-0.7));
        for (State s : machines.values()) if (p.getUniqueId().equals(s.seated)) s.seated = null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (State s : machines.values()) if (e.getPlayer().getUniqueId().equals(s.seated)) s.seated = null;
        level.remove(e.getPlayer().getUniqueId());
    }

    // =================================================================================================================
    // Admin
    // =================================================================================================================

    /** Spieltest/Kamera: freistehender Automat (id, Fenstermitte, Blickrichtung zum Spieler, Hocker, Stil). */
    /** Nur für den Regeltest: Hebel an Automat id ziehen wie ein Spieler. */
    public boolean testPull(Player p, String id) {
        State s = machines.get(id);
        if (s == null || s.busy) return false;
        pull(p, s);
        return s.busy;
    }

    public void testMachine(String id, Location center, float yaw, Location stool, String style) {
        testMachine(id, center, yaw, stool, style, 2.7, null);
    }

    public void testMachine(String id, Location center, float yaw, Location stool, String style, double width, SlotTheme theme) {
        SlotTheme th = theme != null ? theme : SlotTheme.forStyle(style);
        raw.put(id, center.getWorld().getName() + ";" + center.getX() + ";" + center.getY() + ";" + center.getZ() + ";" + yaw + ";" + width + ";"
                + stool.getX() + ";" + stool.getY() + ";" + stool.getZ() + ";1;" + style + ";" + th.key);
        save();
        machines.remove(id);
        State s = load(id);
        if (s != null) build(s);
    }

    /** /casino slots automat [Breite|klein] [Stil] [symbole=Thema] | reihe <Anzahl> … | entfernen | jackpot */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "automat", "reihe" -> {
                boolean row = sub.equals("reihe");
                int count = 1;
                double width = 2.7;
                String style = row ? null : "klassik";
                SlotTheme theme = null;
                int ai = 2;
                if (row) {
                    try {
                        count = Math.max(1, Math.min(8, Integer.parseInt(args[2])));
                        ai = 3;
                    } catch (RuntimeException ex) {
                        Msg.send(p, "<red>Nutzung: /casino slots reihe <Anzahl 1–8> [Breite|klein] [gold|neon|ozean|klassik] [symbole=Thema]");
                        return;
                    }
                    width = 1.35;
                }
                for (; ai < args.length; ai++) {
                    String a0 = args[ai].toLowerCase(java.util.Locale.ROOT);
                    if (a0.startsWith("symbole=") || a0.startsWith("thema=")) {
                        theme = SlotTheme.byName(a0.substring(a0.indexOf('=') + 1));
                        if (theme == null) {
                            Msg.send(p, "<red>Unbekanntes Thema. Möglich: frucht, gold, neon, ozean");
                            return;
                        }
                    } else if (a0.equals("klein") || a0.equals("kompakt") || a0.equals("mini")) width = 1.35;
                    else if (a0.equals("gold") || a0.equals("goldrausch")) style = "gold";
                    else if (a0.equals("neon")) style = "neon";
                    else if (a0.equals("ozean") || a0.equals("tiefsee")) style = "ozean";
                    else if (a0.equals("klassik")) style = "klassik";
                    else {
                        try {
                            width = Math.max(1.2, Math.min(3.6, Double.parseDouble(a0.replace(',', '.'))));
                        } catch (NumberFormatException ex) {
                            Msg.send(p, "<red>Nutzung: /casino slots automat [Breite 1,2–3,6|klein] [klassik|gold|neon|ozean] [symbole=Thema]");
                            return;
                        }
                    }
                }
                RayTraceResult hit = p.rayTraceBlocks(6);
                if (hit == null || hit.getHitBlockFace() == null || hit.getHitBlock() == null) {
                    Msg.send(p, "<red>Schau auf die Wand direkt über dem Pult, mittig (eine Blockseite).");
                    return;
                }
                Vector n = hit.getHitBlockFace().getDirection();
                if (Math.abs(n.getY()) > 0.5) {
                    Msg.send(p, "<red>Schau auf eine senkrechte Blockseite.");
                    return;
                }
                // Mitte auf halbe Blöcke einrasten: quer zur Wand und in der Höhe
                Vector pos = hit.getHitPosition().add(n.clone().multiply(0.03));
                Vector along = new Vector(n.getZ(), 0, -n.getX());
                double a = pos.getX() * along.getX() + pos.getZ() * along.getZ();
                double snapped = Math.round(a * 2) / 2.0;
                pos.add(along.clone().multiply(snapped - a));
                pos.setY(Math.floor(pos.getY()) + 0.5);
                float yaw = (float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
                // Reihe: Gehäuse breit = Walzen + Säulen (0,48) + 0,22 Luft, auf halbe Blöcke gerundet; Themen wechseln
                double step = Math.ceil((width + 0.7) * 2) / 2.0;
                String[] cycle = {"gold", "neon", "ozean"};
                StringBuilder made = new StringBuilder();
                int built = 0;
                for (int k = 0; k < count; k++) {
                    Vector at = pos.clone().add(along.clone().multiply(k * step));
                    String st = style != null ? style : cycle[k % cycle.length];
                    // Pult: feste Blöcke vor der Wand unter dem Fenster (je Automat neu gemessen)
                    org.bukkit.block.Block wall = p.getWorld().getBlockAt(at.clone().subtract(n.clone().multiply(0.5)).toLocation(p.getWorld()));
                    int desk = 0;
                    for (int d = 1; d <= 3; d++) {
                        org.bukkit.block.Block bl = wall.getRelative(n.getBlockX() * d, -1, n.getBlockZ() * d);
                        if (!bl.getType().isSolid()) break;
                        desk = d;
                    }
                    if (row && desk == 0 && !wall.getType().isSolid()) {
                        Msg.send(p, "<yellow>Automat " + (k + 1) + ": keine Wand – Reihe hier beendet.");
                        break;
                    }
                    SlotTheme th = theme != null ? theme : SlotTheme.forStyle(st);
                    String id = "s" + (System.currentTimeMillis() + k) % 100000;
                    double floorY = p.getLocation().getBlockY();
                    Location stool = closeStool(at.toLocation(p.getWorld()), yaw, desk, floorY);
                    raw.put(id, p.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + yaw + ";" + width
                            + ";" + stool.getX() + ";" + stool.getY() + ";" + stool.getZ() + ";" + desk + ";" + st + ";" + th.key);
                    State s = load(id);
                    if (s != null) build(s);
                    if (made.length() > 0) made.append(", ");
                    made.append(th.title.charAt(0)).append(th.title.substring(1).toLowerCase(java.util.Locale.ROOT));
                    built++;
                }
                save();
                Msg.send(p, "<green>" + (built == 1 ? "Slot-Automat" : built + " Slot-Automaten") + " aufgebaut (Breite "
                        + String.format(java.util.Locale.GERMAN, "%.2f", width) + "): <white>" + made + "<green>. Hocker direkt vor dem Pult."
                        + " Entfernen: /casino slots entfernen");
            }
            case "entfernen" -> {
                String best = null;
                double bd = 36;
                for (String id : raw.keySet()) {
                    State s = load(id);
                    if (s == null || !s.machine.center().getWorld().equals(p.getWorld())) continue;
                    double d = s.machine.center().distanceSquared(p.getLocation());
                    if (d < bd) {
                        bd = d;
                        best = id;
                    }
                }
                if (best == null) {
                    Msg.send(p, "<red>Kein Slot-Automat im Umkreis von 6 Blöcken.");
                    return;
                }
                State s = machines.remove(best);
                if (s != null) s.machine.removeAll();
                raw.remove(best);
                save();
                Msg.send(p, "<green>Slot-Automat entfernt.");
            }
            case "jackpot" -> {
                if (args.length > 2) {
                    try {
                        jackpot = Math.max(0, Long.parseLong(args[2]));
                        save();
                        updateHolos();
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Ungültiger Betrag.");
                        return;
                    }
                }
                Msg.send(p, "<gold>Slots-Jackpot: <yellow>" + CasinoText.chips(jackpot));
            }
            default -> Msg.send(p, "<gray>" + raw.size() + " Slot-Automaten. Aufbauen: Pult (1 Block hoch) vor eine Wand bauen, auf die"
                    + " Wand direkt über der Pult-Mitte schauen und <white>/casino slots automat [Breite|klein] [klassik|gold|neon|ozean]"
                    + " [symbole=frucht|gold|neon|ozean]</white> (Standard 2,7, klassik; Hocker kommt selbst vors Pult). Mehrere nebeneinander:"
                    + " <white>/casino slots reihe <Anzahl> [klein|Breite] [Stil]</white> (ohne Stil wechseln Goldrausch, Neon 7, Tiefsee)."
                    + " Weiter: entfernen, jackpot [Betrag]");
        }
    }
}
