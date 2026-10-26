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
    /** Gewichte der Symbole auf jeder Walze (Reihenfolge wie SlotMachine.SYMBOLS). */
    private static final int[] WEIGHTS = {6, 8, 8, 4, 5, 4, 5, 3, 2, 1};
    /** Auszahlung für drei gleiche (Vielfaches des Einsatzes); Siebener = Jackpot. */
    public static final int[] THREE = {10, 8, 8, 20, 12, 20, 12, 40, 80, 0};
    public static final String[] NAMES = {"Kirsche", "Zitrone", "Banane", "Glocke", "Stern", "Kleeblatt", "Chip", "Münze",
            "Diamant", "Sieben"};
    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOTAL;

    static {
        int t = 0;
        for (int w : WEIGHTS) t += w;
        TOTAL = t;
    }

    private static final class State {
        final SlotMachine machine;
        final String[][] shown = new String[3][3];
        boolean busy;
        UUID seated;

        State(SlotMachine m) {
            this.machine = m;
            for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) shown[i][k] = SlotMachine.SYMBOLS[pick()];
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

    public SlotsService(KubyPlugins plugin) {
        this.plugin = plugin;
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

    private static int pick() {
        int r = RNG.nextInt(TOTAL);
        for (int i = 0; i < WEIGHTS.length; i++) {
            r -= WEIGHTS[i];
            if (r < 0) return i;
        }
        return 0;
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
            s = new State(new SlotMachine(id, c, Float.parseFloat(p[4]), Double.parseDouble(p[5]), desk, stool, tag, click));
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
        return level.getOrDefault(p.getUniqueId(), 0);
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
        long bet = LEVELS[levelOf(p)];
        if (!plugin.casino().takeChips(p.getUniqueId(), bet)) {
            CasinoText.error(p, "So viele Chips hast du nicht (Einsatz " + CasinoText.num(bet) + "). Tausch sie an der Kasse.");
            return;
        }
        long feed = Math.round(bet * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        jackpot += Math.max(1, Math.round(bet * plugin.getConfig().getDouble("casino.slots.jackpot-share", 0.02)));
        s.busy = true;
        int[] result = {pick(), pick(), pick()};
        s.machine.lever(true);
        s.machine.lamps(true);
        Location fx = s.machine.effectLocation();
        fx.getWorld().playSound(fx, Sound.BLOCK_LEVER_CLICK, 0.8f, 0.8f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> s.machine.lever(false), 12L);
        int[] stops = {24, 36, 48};
        boolean[] stopped = new boolean[3];
        plugin.getServer().getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
            int t;

            @Override
            public void accept(org.bukkit.scheduler.BukkitTask task) {
                t += 2;
                for (int i = 0; i < 3; i++) {
                    if (stopped[i]) continue;
                    if (t >= stops[i]) {
                        stopped[i] = true;
                        s.shown[i][1] = SlotMachine.SYMBOLS[result[i]];
                        s.shown[i][0] = SlotMachine.SYMBOLS[pick()];
                        s.shown[i][2] = SlotMachine.SYMBOLS[pick()];
                        for (int k = 0; k < 3; k++) s.machine.symbol(i, k, s.shown[i][k], false);
                        fx.getWorld().playSound(fx, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.9f, 0.7f + 0.15f * i);
                        continue;
                    }
                    s.shown[i][2] = s.shown[i][1];
                    s.shown[i][1] = s.shown[i][0];
                    s.shown[i][0] = SlotMachine.SYMBOLS[pick()];
                    for (int k = 0; k < 3; k++) s.machine.symbol(i, k, s.shown[i][k], true);
                }
                if (t % 4 == 0) fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.6f);
                if (stopped[0] && stopped[1] && stopped[2]) {
                    task.cancel();
                    s.busy = false;
                    s.machine.lamps(false);
                    finish(p, s, result, bet);
                }
            }
        }, 2L, 2L);
    }

    private void finish(Player p, State s, int[] r, long bet) {
        long pay;
        String what;
        boolean jack = false;
        int cherries = 0;
        for (int x : r) if (x == 0) cherries++;
        if (r[0] == r[1] && r[1] == r[2]) {
            if (r[0] == 9) {
                long share = Math.max(bet * 100, jackpot * bet / LEVELS[LEVELS.length - 1]);
                share = Math.min(share, jackpot);
                pay = share;
                jackpot -= share;
                if (jackpot < startJackpot()) jackpot = startJackpot();
                what = "JACKPOT! Drei Siebener";
                jack = true;
            } else {
                pay = bet * THREE[r[0]];
                what = "Drei " + plural(r[0]) + "!";
            }
        } else if (cherries == 2) {
            pay = bet * 4;
            what = "Zwei Kirschen!";
        } else if (cherries == 1) {
            pay = bet * 2;
            what = "Eine Kirsche!";
        } else {
            pay = 0;
            what = null;
        }
        if (pay > 0) plugin.casino().giveChips(p.getUniqueId(), pay);
        plugin.casinoLog().record("Slots", p.getUniqueId(), bet, pay);
        Location fx = s.machine.effectLocation();
        if (pay > 0) {
            de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.HAPPY_VILLAGER, 14 + (int) Math.min(30, pay / bet * 2), 0.5, 0.3, 0.2, 0);
            de.kuby.kubyplugins.util.Fx.particle(plugin, fx, Particle.FIREWORK, pay >= bet * 10 ? 30 : 6, 0.4, 0.3, 0.2, 0.05);
            p.playSound(fx, pay >= bet * 10 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            p.sendActionBar(Msg.parse("<green>+" + CasinoText.chips(pay) + " <gray>· <white>" + what));
        } else {
            p.sendActionBar(Msg.parse("<gray>Einsatz <white>" + CasinoText.num(bet) + "</white> · Chips <#F2C94C>"
                    + CasinoText.num(plugin.casino().chips(p.getUniqueId())) + "</#F2C94C> · Jackpot <yellow>" + CasinoText.num(jackpot)));
        }
        if (jack) {
            p.showTitle(Title.title(Msg.parse("<b><gradient:#FFE070:#F2A21C>JACKPOT!</gradient></b>"),
                    Msg.parse("<yellow>+" + CasinoText.chips(pay)), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
            CasinoFx.jackpot(plugin, p.getUniqueId(), fx, pay);
            plugin.notifications().send("notify.jackpot", CasinoText.PREFIX + "<yellow><b>" + Msg.escape(p.getName()) + "</b> knackt den Slots-Jackpot: <gold>"
                    + CasinoText.chips(pay) + "</gold>!");
        } else if (pay >= bet * 20) {
            CasinoFx.show(plugin, p.getUniqueId(), fx, CasinoFx.Result.BIG_WIN, pay - bet);
        }
        updateHolos();
        save();
    }

    private static String plural(int i) {
        return switch (i) {
            case 0 -> "Kirschen";
            case 1 -> "Zitronen";
            case 2 -> "Bananen";
            case 3 -> "Glocken";
            case 4 -> "Sterne";
            case 5 -> "Kleeblätter";
            case 6 -> "Chips";
            case 7 -> "Münzen";
            case 8 -> "Diamanten";
            default -> "Siebener";
        };
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
                l = p.isSneaking() ? Math.max(0, l - 1) : Math.min(LEVELS.length - 1, l + 1);
                level.put(p.getUniqueId(), l);
                s.machine.panel(LEVELS, l);
                p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.4f);
                p.sendActionBar(Msg.parse("<gray>Einsatz: <white>" + CasinoText.chips(LEVELS[l])
                        + "</white> · Knopf: höher, Schleichen + Knopf: tiefer"));
            }
            case "panel", "sign" -> UiDialog.show(p, CasinoScreens.slotsPaytable(jackpot, LEVELS[levelOf(p)]));
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
                s.machine.panel(LEVELS, levelOf(p));
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

    /** /casino slots automat [Breite] | entfernen | liste */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "automat" -> {
                double width = 2.7;
                try {
                    if (args.length > 2) width = Math.max(1.2, Math.min(3.6, Double.parseDouble(args[2].replace(',', '.'))));
                } catch (NumberFormatException ex) {
                    Msg.send(p, "<red>Nutzung: /casino slots automat [Breite 1,2–3,6]");
                    return;
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
                // Pult: feste Blöcke vor der Wand unter dem Fenster
                int desk = 0;
                org.bukkit.block.Block wall = hit.getHitBlock();
                for (int k = 1; k <= 3; k++) {
                    org.bukkit.block.Block b = wall.getRelative(n.getBlockX() * k, -1, n.getBlockZ() * k);
                    if (!b.getType().isSolid()) break;
                    desk = k;
                }
                Location stool = new Location(p.getWorld(), p.getLocation().getBlockX() + 0.5, p.getLocation().getBlockY(),
                        p.getLocation().getBlockZ() + 0.5);
                String id = "s" + System.currentTimeMillis() % 100000;
                raw.put(id, p.getWorld().getName() + ";" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ() + ";" + yaw + ";" + width
                        + ";" + stool.getX() + ";" + stool.getY() + ";" + stool.getZ() + ";" + desk);
                save();
                State s = load(id);
                if (s != null) build(s);
                Msg.send(p, "<green>Slot-Automat aufgebaut (Breite " + String.format(java.util.Locale.GERMAN, "%.1f", width) + ", Pult "
                        + desk + " Block tief), Hocker an deiner Position. Entfernen: /casino slots entfernen");
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
            default -> Msg.send(p, "<gray>" + raw.size() + " Slot-Automaten. Aufbauen: Pult (1 Block hoch, 3 breit) vor eine Wand bauen, dort"
                    + " hinstellen, wo der Hocker hin soll, auf die Wand direkt über der Pult-Mitte schauen und"
                    + " <white>/casino slots automat [Breite]</white> (Standard 2,7). Weiter: entfernen, jackpot [Betrag]");
        }
    }
}
