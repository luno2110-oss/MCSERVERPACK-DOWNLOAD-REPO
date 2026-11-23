package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Glücksrad im Casino (v2.07, Wunsch des Besitzers: „ein Glücksrad, das man alle 6 h drehen kann“). Jeder Spieler darf
 * alle casino.gluecksrad.stunden Stunden einmal gratis drehen: Rechtsklick aufs Rad an der Wand. Das Ergebnis wird
 * nach Gewicht gezogen, das Rad dreht sich 6 bis 9 Sekunden auslaufend genau auf dieses Feld (Wheel.plan), am Zeiger
 * klickt jedes Feld, das Lauflicht rennt im Kreis, die Gewinn-Tafel zeigt das Feld am Zeiger. Gewinne: Chips, Coins,
 * Juwelen, Kisten-Schlüssel, Booster, Casino-Titel und -Kosmetik (wie im Chip-Shop, in allen Modi).
 *
 * <p>Ein Rad dreht immer nur für einen Spieler; wer dran ist, wenn es gerade läuft, bekommt einen Hinweis. Sobald der
 * nächste Dreh bereit ist, kommt (einmal) eine Nachricht. Räder und letzte Drehs in gluecksrad.yml.
 */
public final class WheelService implements Listener {

    private static final SecureRandom RNG = new SecureRandom();

    private static final class Spin {
        final UUID player;
        final String name;
        final int target;
        final double[] plan;
        int t;
        int lastField = -1;

        Spin(UUID player, String name, int target, double[] plan) {
            this.player = player;
            this.name = name;
            this.target = target;
            this.plan = plan;
        }
    }

    private static final class Board {
        final WheelBoard board;
        Spin spin;
        int idle;
        int winBlink;

        Board(WheelBoard board) {
            this.board = board;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Map<String, String> raw = new LinkedHashMap<>();
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();
    private final Set<UUID> reminded = new HashSet<>();
    private BukkitTask task;
    private int tick;
    /** Test: letztes Ergebnis (gezogenes Feld, Feld am Zeiger nach dem Halt, Gewinn). */
    public volatile String testLast;

    public WheelService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gluecksrad.yml");
        this.tag = new NamespacedKey(plugin, "gluecksrad");
        this.click = new NamespacedKey(plugin, "gluecksrad_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection r = yml.getConfigurationSection("raeder");
        if (r != null) for (String id : r.getKeys(false)) raw.put(id, r.getString(id));
        ConfigurationSection z = yml.getConfigurationSection("zeit");
        if (z != null) {
            for (String k : z.getKeys(false)) {
                try {
                    last.put(UUID.fromString(k), z.getLong(k));
                } catch (IllegalArgumentException ignored) {
                    // kaputter Eintrag
                }
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        raw.forEach((k, v) -> yml.set("raeder." + k, v));
        long cut = System.currentTimeMillis() - 30L * 86_400_000L;
        last.forEach((k, v) -> {
            if (v > cut) yml.set("zeit." + k, v);
        });
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("gluecksrad.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // =================================================================================================================
    // Räder laden und aufbauen
    // =================================================================================================================

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
            b = new Board(new WheelBoard(id, c, Float.parseFloat(p[4]), Double.parseDouble(p[5]), tag, click));
            boards.put(id, b);
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void build(Board b) {
        b.board.build();
        idle(b);
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

    /** Beim Abschalten: laufende Drehs sofort auszahlen (das Feld steht fest), Anzeigen weg. */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Board b : boards.values()) {
            if (b.spin != null) finish(b, b.spin, false);
            b.board.removeAll();
        }
        save();
    }

    public boolean hasWheel() {
        return !raw.isEmpty();
    }

    // =================================================================================================================
    // Wartezeit
    // =================================================================================================================

    public long cooldownMs() {
        return Math.max(1, plugin.getConfig().getLong("casino.gluecksrad.stunden", 6)) * 3_600_000L;
    }

    /** Millisekunden bis zum nächsten Gratis-Dreh (0 = bereit). */
    public synchronized long left(UUID id) {
        Long t = last.get(id);
        return t == null ? 0 : Math.max(0, t + cooldownMs() - System.currentTimeMillis());
    }

    public static String duration(long ms) {
        long m = (ms + 59_999) / 60_000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }

    /** /gluecksrad: bereit oder Restzeit, dazu der Weg zum Rad. */
    public void info(Player p) {
        long l = left(p.getUniqueId());
        if (l == 0) {
            CasinoText.send(p, "<#F2C94C>Dein Glücksrad ist bereit!</#F2C94C> <gray>Im Casino drehst du es gratis: Rechtsklick aufs Rad."
                    + (hasWheel() ? " <click:run_command:'/casino'><white>[Zum Casino]</white></click>" : ""));
        } else {
            java.time.LocalTime at = java.time.LocalTime.now().plusSeconds(l / 1000);
            CasinoText.send(p, "<gray>Nächster Gratis-Dreh am Glücksrad in <white>" + duration(l) + "</white> (ab <white>"
                    + String.format("%02d:%02d", at.getHour(), at.getMinute()) + " Uhr</white>).");
        }
    }

    // =================================================================================================================
    // Drehen
    // =================================================================================================================

    private void handle(Player p, Entity e) {
        String v = e.getPersistentDataContainer().get(click, PersistentDataType.STRING);
        if (v == null) return;
        Board b = load(v.split(":")[0]);
        if (b == null) return;
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        spin(p, b, false);
    }

    /** Dreh starten. test = Wartezeit übergehen (Belastungstest, Bilder). Fehlermeldung oder null. */
    private String spin(Player p, Board b, boolean test) {
        UUID id = p.getUniqueId();
        if (b.spin != null) {
            if (!b.spin.player.equals(id)) CasinoText.error(p, "Das Glücksrad dreht gerade für " + b.spin.name + ". Gleich bist du dran!");
            return "dreht schon";
        }
        long l = left(id);
        if (l > 0 && !test) {
            p.sendActionBar(Msg.parse("<gray>Nächster Gratis-Dreh in <white>" + duration(l)));
            info(p);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return "Wartezeit";
        }
        List<Wheel.Field> fields = Wheel.fields(plugin.getConfig());
        int target = Wheel.pick(fields, RNG);
        int ticks = test ? 120 : 130 + RNG.nextInt(50);
        double[] plan = Wheel.plan(b.board.angle(), target, (RNG.nextDouble() * 2 - 1) * 11, 3 + RNG.nextInt(2), ticks);
        synchronized (this) {
            last.put(id, System.currentTimeMillis());
            reminded.remove(id);
        }
        save();
        b.spin = new Spin(id, p.getName(), target, plan);
        b.board.info(Component.text(p.getName() + " dreht …", TextColor.color(0xE8E4DC)));
        Location fx = b.board.effectLocation();
        fx.getWorld().playSound(fx, Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.7f);
        fx.getWorld().playSound(fx, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.6f);
        start();
        return null;
    }

    private void tick() {
        tick++;
        for (Board b : boards.values()) {
            if (!b.board.ready()) continue;
            Spin s = b.spin;
            if (s == null) {
                if (b.winBlink > 0) {
                    b.winBlink--;
                    boolean[] lit = new boolean[WheelBoard.BULBS];
                    for (int i = 0; i < lit.length; i++) lit[i] = (b.winBlink / 4) % 2 == 0;
                    if (b.winBlink % 4 == 0) b.board.bulbs(lit);
                    if (b.winBlink == 0) idle(b);
                } else if (tick % 20 == 0) {
                    // ruhig: jedes zweite Lämpchen, langsam im Wechsel
                    boolean[] lit = new boolean[WheelBoard.BULBS];
                    for (int i = 0; i < lit.length; i++) lit[i] = (i + tick / 20) % 2 == 0;
                    b.board.bulbs(lit);
                }
                continue;
            }
            s.t++;
            int t = Math.min(s.t, s.plan.length - 1);
            if (s.t % 2 == 0 || t == s.plan.length - 1) b.board.spinTo(s.plan[t], 2);
            int field = Wheel.fieldAt(s.plan[t]);
            if (field != s.lastField) {
                s.lastField = field;
                List<Wheel.Field> fs = Wheel.fields(plugin.getConfig());
                b.board.prize(WheelBoard.prizeText(fs.get(field).name(), field == Wheel.jackpot(fs)));
                Location fx = b.board.effectLocation();
                float speed = (float) Math.min(1, Math.abs(s.plan[t] - s.plan[Math.max(0, t - 1)]) / 30.0);
                fx.getWorld().playSound(fx, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 1.2f + 0.6f * speed);
                b.board.flick(-10);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> b.board.flick(0), 2L);
            }
            if (s.t % 2 == 0) {
                // Lauflicht: drei helle Lämpchen rennen im Kreis, mit dem Rad langsamer werdend
                int head = (int) (s.plan[t] / 360.0 * WheelBoard.BULBS * 1.5);
                boolean[] lit = new boolean[WheelBoard.BULBS];
                for (int k = 0; k < 3; k++) lit[Math.floorMod(head - k, WheelBoard.BULBS)] = true;
                b.board.bulbs(lit);
            }
            if (s.t >= s.plan.length + 6) {
                b.spin = null;
                finish(b, s, true);
            }
        }
        // einmal je Minute: wer wieder drehen darf, bekommt eine Nachricht
        if (tick % 1200 == 0 && plugin.getConfig().getBoolean("casino.gluecksrad.erinnerung", true) && hasWheel()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                synchronized (this) {
                    if (!last.containsKey(id) || reminded.contains(id) || left(id) > 0) continue;
                    reminded.add(id);
                }
                CasinoText.send(p, "<#F2C94C>Dein Glücksrad ist wieder bereit!</#F2C94C> <gray>Gratis drehen im Casino."
                        + " <click:run_command:'/casino'><white>[Zum Casino]</white></click>");
            }
        }
    }

    private void idle(Board b) {
        b.board.prize(WheelBoard.prizeText("Gratis-Dreh alle " + plugin.getConfig().getLong("casino.gluecksrad.stunden", 6) + " Stunden", false));
        b.board.info(Component.text("Rechtsklick aufs Rad · /gluecksrad zeigt deine Wartezeit", TextColor.color(0xACA48E)));
    }

    /** Gewinn auszahlen (effects: Feuerwerk, Ansage, Töne). */
    private void finish(Board b, Spin s, boolean effects) {
        List<Wheel.Field> fs = Wheel.fields(plugin.getConfig());
        Wheel.Field f = fs.get(s.target);
        int landed = Wheel.fieldAt(s.plan[s.plan.length - 1]);
        String err = give(s.player, f.prize());
        boolean jackpot = s.target == Wheel.jackpot(fs);
        long chips = Wheel.chips(f.prize());
        testLast = s.target + ";" + landed + ";" + f.prize() + ";" + (err == null ? "ok" : err);
        Player p = Bukkit.getPlayer(s.player);
        b.board.prize(WheelBoard.prizeText(f.name(), jackpot));
        b.board.info(Component.text(s.name + " gewinnt " + f.name() + "!", TextColor.color(0xF2C94C)));
        b.winBlink = 80;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (b.spin == null && b.winBlink == 0) idle(b);
        }, 120L);
        if (p != null) {
            if (err != null) CasinoText.error(p, "Gewinn " + f.name() + " konnte nicht ausgezahlt werden: " + err);
            else CasinoText.send(p, "<green>Glücksrad: <white>" + f.name() + "</white>! <gray>Nächster Gratis-Dreh in "
                    + duration(cooldownMs()) + ".");
        }
        if (!effects) return;
        Location fx = b.board.effectLocation();
        World w = fx.getWorld();
        w.playSound(fx, Sound.BLOCK_ANVIL_LAND, 0.25f, 1.8f);
        if (jackpot || chips >= plugin.getConfig().getLong("casino.gluecksrad.ansage-ab", 25_000)) {
            w.playSound(fx, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, fx, 120, 0.8, 0.8, 0.3, 0.4);
            w.spawnParticle(Particle.FIREWORK, fx, 60, 0.9, 0.9, 0.3, 0.12);
            Bukkit.broadcast(Msg.parse("<#F2C94C>✦ Glücksrad: <white>" + s.name + "</white> gewinnt <#FFE870><b>" + f.name() + "</b></#FFE870>!"));
            if (p != null) p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(jackpot ? "HAUPTGEWINN!" : "GROSSER GEWINN!", TextColor.color(0xFFE870)),
                    Component.text(f.name(), NamedTextColor.WHITE)));
        } else {
            w.playSound(fx, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            w.spawnParticle(Particle.WAX_ON, fx, 30, 0.7, 0.7, 0.2, 0.5);
        }
    }

    /** Gewinn geben. Fehlermeldung oder null. */
    private String give(UUID id, String prize) {
        String[] p = prize.split(":");
        try {
            switch (p[0]) {
                case "chips" -> plugin.casino().giveChips(id, Long.parseLong(p[1]));
                case "coins" -> plugin.economy().deposit(id, Long.parseLong(p[1]));
                case "jewels" -> plugin.jewels().add(id, Long.parseLong(p[1]));
                case "key" -> {
                    if (!plugin.keys().give(id, plugin.modes().modeOf(id), p[1], p.length > 2 ? Integer.parseInt(p[2]) : 1))
                        return "Schlüssel " + p[1] + " fehlt in keys.types";
                }
                case "booster" -> plugin.booster().grant(id, p[1], Double.parseDouble(p[2]), Integer.parseInt(p[3]));
                case "tag" -> {
                    if (plugin.tags().displayOf(p[1]) == null) return "Titel " + p[1] + " fehlt";
                    plugin.chipShop().grant(id, "tag:" + p[1]);
                }
                case "cosmetic" -> {
                    if (plugin.cosmetics().find(p[1]) == null) return "Kosmetik " + p[1] + " fehlt";
                    plugin.chipShop().grant(id, "cosmetic:" + p[1]);
                }
                default -> {
                    return "unbekannte Art " + p[0];
                }
            }
        } catch (RuntimeException e) {
            return "falsch eingetragen (" + prize + ")";
        }
        return null;
    }

    /** Prüft die Felder (SELFTEST): Fehler oder leere Liste. */
    public static List<String> checkFields(KubyPlugins plugin) {
        List<String> err = new java.util.ArrayList<>();
        List<Wheel.Field> fs = Wheel.fields(plugin.getConfig());
        for (int i = 0; i < fs.size(); i++) {
            String[] p = fs.get(i).prize().split(":");
            String bad = switch (p[0]) {
                case "chips", "coins", "jewels" -> p.length == 2 && p[1].matches("\\d+") ? null : "Betrag";
                case "key" -> p.length >= 2 && plugin.keys().type(p[1]) != null ? null : "Schlüssel fehlt in keys.types";
                case "booster" -> p.length == 4 && p[2].matches("[0-9.]+") && p[3].matches("\\d+") ? null : "Format booster:art:faktor:min";
                case "tag" -> p.length == 2 && plugin.tags().displayOf(p[1]) != null ? null : "Titel fehlt";
                case "cosmetic" -> p.length == 2 && plugin.cosmetics().find(p[1]) != null ? null : "Kosmetik fehlt";
                default -> "unbekannte Art";
            };
            if (bad != null) err.add("Feld " + i + " (" + fs.get(i).prize() + "): " + bad);
        }
        return err;
    }

    // =================================================================================================================
    // Klicks, Aufbau, Test
    // =================================================================================================================

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

    /** Belastungstest: Rad aufbauen (ohne Speichern). */
    public void testBoard(String id, Location center, float yaw, double size) {
        raw.put(id, center.getWorld().getName() + ";" + center.getX() + ";" + center.getY() + ";" + center.getZ() + ";" + yaw + ";" + size);
        boards.remove(id);
        Board b = load(id);
        if (b != null) build(b);
        start();
    }

    /** Belastungstest: Dreh ohne Wartezeit. Fehlermeldung oder null. */
    public String testSpin(Player p, String id) {
        Board b = load(id);
        if (b == null || !b.board.ready()) return "kein Glücksrad " + id;
        return spin(p, b, true);
    }

    public Location wheelCenter(String id) {
        Board b = load(id);
        return b == null ? null : b.board.center();
    }

    /** /casino gluecksrad aufstellen [Größe] | entfernen | zuruecksetzen <Spieler> */
    public void command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        if (!p.hasPermission("kuby.admin") || sub.isEmpty()) {
            info(p);
            return;
        }
        switch (sub) {
            case "aufstellen", "aufbauen", "rad" -> {
                double size = 1.0;
                if (args.length > 2) {
                    try {
                        size = Math.max(0.6, Math.min(1.6, Double.parseDouble(args[2].replace(',', '.'))));
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Nutzung: /casino gluecksrad aufstellen [Größe 0,6–1,6]");
                        return;
                    }
                }
                RayTraceResult hit = p.rayTraceBlocks(8);
                if (hit == null || hit.getHitBlockFace() == null || hit.getHitBlock() == null) {
                    Msg.send(p, "<red>Schau auf die Wand, an die das Glücksrad soll.");
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
                at.setY(p.getLocation().getBlockY() + 0.5 - WheelBoard.BOTTOM * size);
                float yaw = (float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
                String id = "g" + System.currentTimeMillis() % 100000;
                raw.put(id, p.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + yaw + ";" + size);
                save();
                Board b = load(id);
                if (b != null) build(b);
                start();
                double h = (WheelBoard.TOP - WheelBoard.BOTTOM) * size;
                Msg.send(p, "<green>Glücksrad aufgestellt (" + String.format(java.util.Locale.GERMAN, "%.1f x %.1f", 3.7 * size, h)
                        + " Blöcke). Spieler drehen es alle " + plugin.getConfig().getLong("casino.gluecksrad.stunden", 6)
                        + " Stunden gratis. Entfernen: /casino gluecksrad entfernen");
            }
            case "entfernen" -> {
                Board best = null;
                double bd = 64;
                for (String id : raw.keySet()) {
                    Board b = load(id);
                    if (b == null || !b.board.center().getWorld().equals(p.getWorld())) continue;
                    double d = b.board.center().distanceSquared(p.getLocation());
                    if (d < bd) {
                        bd = d;
                        best = b;
                    }
                }
                if (best == null) {
                    Msg.send(p, "<red>Kein Glücksrad im Umkreis von 8 Blöcken.");
                    return;
                }
                if (best.spin != null) finish(best, best.spin, false);
                best.spin = null;
                best.board.removeAll();
                boards.remove(best.board.id());
                raw.remove(best.board.id());
                save();
                Msg.send(p, "<green>Glücksrad entfernt.");
            }
            case "zuruecksetzen", "reset" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                if (t == null) {
                    Msg.send(p, "<red>Spieler nicht online.");
                    return;
                }
                synchronized (this) {
                    last.remove(t.getUniqueId());
                }
                save();
                Msg.send(p, "<green>Wartezeit von " + t.getName() + " zurückgesetzt.");
            }
            default -> Msg.send(p, "<gray>" + raw.size() + " Glücksräder. Aufstellen: auf eine Wand schauen (gut 5 Blöcke hoch, 4 breit),"
                    + " <white>/casino gluecksrad aufstellen [Größe 0,6–1,6]</white>. Weiter: entfernen, zuruecksetzen <Spieler>."
                    + " Gewinne: casino.gluecksrad.felder.");
        }
    }
}
