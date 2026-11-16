package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cinema.Cinema;
import de.kuby.kubyplugins.cinema.V3;
import de.kuby.kubyplugins.cinema.scenes.CasinoTour;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Casino-Rundgang (v1.77): beim ersten Betreten der Casino-Region führt Gastgeber Vincent durch alle Stationen, die
 * im Casino wirklich stehen (Kasse, Blackjack, High-Roller-Tische, Poker, Roulette, Slots, Crash, Jukebox).
 * Wiederholen: /casino rundgang. Wer ihn gesehen hat, steht in casino-tour.yml.
 */
public final class CasinoTourService {

    private static final double RANGE = 90;
    private final KubyPlugins plugin;
    private final File file;
    private final Set<UUID> seen = new HashSet<>();

    public CasinoTourService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-tour.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("gesehen")) {
            try {
                seen.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("gesehen", seen.stream().map(UUID::toString).sorted().toList());
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-tour.yml: " + e.getMessage());
        }
    }

    /** Erstes Betreten der Casino-Region: Rundgang starten (einmal je Spieler). */
    private void tick() {
        if (!plugin.getConfig().getBoolean("casino.rundgang.aktiv", true) || plugin.cinema() == null) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (seen.contains(p.getUniqueId()) || Cinema.busy(p.getUniqueId()) || p.getVehicle() != null) continue;
            if (!plugin.casinoMusic().inCasino(p.getLocation())) continue;
            if (play(p)) {
                seen.add(p.getUniqueId());
                save();
            }
        }
    }

    /** Rundgang zeigen (auch zum Wiederholen). false, wenn es nichts zu zeigen gibt. */
    public boolean play(Player p) {
        if (plugin.cinema() == null || Cinema.busy(p.getUniqueId())) return false;
        List<CasinoTour.Station> st = stations(p);
        if (st.isEmpty()) return false;
        Location l = p.getLocation();
        V3 start = new V3(l.getX(), floorY(l), l.getZ());
        V3 dir = V3.dir(l.getYaw(), 0);
        Cinema.Options o = new Cinema.Options();
        o.anchor = l;
        o.skippable = true;
        o.onEnd = (pp, res) -> {
            if (res != Cinema.Result.ABORTED) CasinoText.send(pp, "<gold>Viel Glück!</gold> <gray>Rundgang wiederholen: <white>/casino rundgang");
        };
        return plugin.cinema().play(p, new CasinoTour(start, dir, st), o);
    }

    // =================================================================================================================
    // Stationen
    // =================================================================================================================

    private static double floorY(Location l) {
        Block b = l.getBlock();
        for (int i = 0; i < 6; i++) {
            Block below = b.getRelative(0, -1, 0);
            if (!below.isPassable()) return b.getY();
            b = below;
        }
        return l.getY();
    }

    private static V3 v(Location l) {
        return new V3(l.getX(), l.getY(), l.getZ());
    }

    private static V3 floor(Location l) {
        return new V3(l.getX(), floorY(l), l.getZ());
    }

    private static boolean near(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < RANGE * RANGE;
    }

    /** Ein Spieler (nicht der Zuschauer), der gerade an dieser Station steht: auf ihn schneidet die Kamera. */
    private Player playerAt(Player viewer, Location focus) {
        for (Player o : focus.getWorld().getPlayers()) {
            if (o.equals(viewer) || o.getLocation().distanceSquared(focus) > 16) continue;
            return o;
        }
        return null;
    }

    private CasinoTour.Station station(Player viewer, String name, Location at, V3 front, double dist, V3 bCam, V3 bLook,
                                       String a, String b) {
        V3 focus = floor(at);
        Player other = playerAt(viewer, at);
        if (other != null) {
            // mitten im Satz auf einen echten Spieler schneiden
            V3 eye = v(other.getEyeLocation());
            V3 f = front.flat();
            bCam = eye.add(f.mul(1.6)).add(f.right().mul(0.8)).up(0.15);
            bLook = eye.up(-0.15);
        }
        return new CasinoTour.Station(name, focus, front, dist, bCam, bLook, new String[]{a, b});
    }

    private <T> T nearest(List<T> list, java.util.function.Function<T, Location> loc, Location from) {
        T best = null;
        double bd = Double.MAX_VALUE;
        for (T t : list) {
            Location l = loc.apply(t);
            if (l == null || !near(l, from)) continue;
            double d = l.distanceSquared(from);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    private List<CasinoTour.Station> stations(Player p) {
        Location me = p.getLocation();
        List<CasinoTour.Station> out = new ArrayList<>();
        // Kasse
        Entity kasse = nearest(plugin.casinoNpc().find(CasinoNpc.Role.KASSE, me, RANGE), Entity::getLocation, me);
        if (kasse != null) {
            V3 f = V3.dir(kasse.getLocation().getYaw(), 0);
            V3 head = v(kasse.getLocation()).up(1.6);
            out.add(station(p, "kasse", kasse.getLocation(), f, 2.0, head.add(f.mul(1.4)).add(f.right().mul(0.5)), head,
                    "Hier an der Kasse tauschen Sie Coins gegen Chips – und Gewinne wieder zurück.",
                    "Unser Kassierer zählt schneller als jede Maschine. Fragen Sie ihn ruhig."));
        }
        // Blackjack (normal und weinrot)
        List<BlackjackGame> games = new ArrayList<>(plugin.blackjack().games());
        for (TableTier tt : TableTier.values()) {
            BlackjackGame g = nearest(games.stream().filter(x -> x.table.tier() == tt).toList(), x -> x.table.origin(), me);
            if (g == null) continue;
            Location o = g.table.origin();
            V3 f = V3.dir(g.table.yaw(), 0);
            V3 dealer = v(o).add(f.mul(-0.6)).up(1.55);
            V3 cam = v(o).add(f.mul(0.9)).add(f.right().mul(0.6)).up(1.45);
            out.add(switch (tt) {
                case ROT -> station(p, "highroller", o, f, 3.4, cam, dealer,
                        "Und hier, in Weinrot: unsere High-Roller-Tische. Höhere Einsätze für Mutige.",
                        "Jeder Sieg in Folge zahlt mehr – bis zum Achtfachen.");
                case VIOLETT -> station(p, "elite", o, f, 3.4, cam, dealer,
                        "Violett: der Elite-Tisch. Ab 50.000 Chips – bis zu einer Million je Runde.",
                        "Hier spielen nur die Mutigsten. Und die Serie zahlt bis zum Achtfachen.");
                default -> station(p, "blackjack", o, f, 3.4, cam, dealer,
                        "Blackjack: Kommen Sie näher an die 21 als die Bank, ohne sich zu überkaufen.",
                        "Siegesserien lohnen sich – jeder Sieg in Folge zahlt ein Stück mehr, bis x2,5.");
            });
        }
        // Poker (normal und weinrot)
        for (var pk : plugin.pokerTables()) {
            Location c = pk.tableLocation();
            if (c == null || !near(c, me)) continue;
            V3 f = V3.dir(pk.tableYaw(), 0).mul(-1);
            V3 dealer = v(c).add(f.mul(-1.9)).up(1.55);
            out.add(station(p, "poker", c, f, 2.8, v(c).add(f.mul(0.6)).up(1.5), dealer,
                    pk.tier() == TableTier.VIOLETT ? "Und ganz oben, in Violett: der Elite-Tisch. Buy-ins bis zu einer Million."
                            : pk.isHighRoller() ? "Texas Hold'em für High Roller – hohe Blinds, große Pots."
                            : "Texas Hold'em gegen andere Spieler – hier zählt nicht nur Glück.",
                    "Lesen Sie Ihre Gegner. Und manchmal gewinnt der, der zuerst passt."));
        }
        // Roulette
        RouletteTable rt = plugin.roulette().table();
        if (rt != null && rt.center() != null && near(rt.center(), me)) {
            Location c = rt.center();
            V3 f = V3.dir(rt.yaw(), 0);
            out.add(station(p, "roulette", c, f, 3.0, v(c).add(f.mul(0.9)).up(1.4), v(c).up(0.1),
                    "Rot oder Schwarz? Am Roulette entscheidet eine einzige Kugel.",
                    "Achten Sie auf die Blitz-Zahlen – die zahlen in manchen Runden ein Vielfaches."));
        }
        // Slots
        SlotMachine sm = nearest(plugin.slots().machineList(), SlotMachine::center, me);
        if (sm != null) {
            V3 f = V3.dir(sm.frontYaw(), 0);
            Location reels = sm.reelFront(1);
            out.add(station(p, "slots", sm.center().clone().add(0, -1.5, 0), f, 2.6, v(reels).add(f.mul(0.85)).up(0.05), v(reels),
                    "Die Automaten: Hebel ziehen, die Walzen laufen – drei gleiche gewinnen.",
                    "Und wenn zwei schon passen … halten Sie den Atem an. Dann wird es spannend."));
        }
        // Crash
        CrashScreen cs = plugin.crash().currentScreen();
        if (cs != null && near(cs.center(), me)) {
            V3 f = V3.dir(cs.frontYaw(), 0);
            Location c = cs.center();
            out.add(station(p, "crash", c, f, 4.0, v(c).add(f.mul(2.4)).up(-0.3), v(c),
                    "Crash: Der Kurs steigt – und je höher er steigt, desto schneller wird er.",
                    "Steigen Sie rechtzeitig aus. Wer zu gierig ist, verliert alles."));
        }
        // Jukebox
        Entity juke = nearest(plugin.casinoNpc().find(CasinoNpc.Role.MUSIK, me, RANGE), Entity::getLocation, me);
        if (juke != null) {
            V3 f = V3.dir(juke.getLocation().getYaw(), 0);
            V3 mid = v(juke.getLocation()).up(1.1);
            out.add(station(p, "musik", juke.getLocation(), f, 2.0, mid.add(f.mul(1.5)).add(f.right().mul(0.4)), mid,
                    "Und für die richtige Stimmung: unsere Jukebox.",
                    "Wünschen Sie sich ein Lied – hier spielt, was Ihnen gefällt."));
        }
        return out;
    }
}
