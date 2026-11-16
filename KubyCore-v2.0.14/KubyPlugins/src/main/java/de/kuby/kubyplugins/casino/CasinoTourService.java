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
        try (var in = plugin.getResource("casino/tour-voice.txt")) {
            if (in != null) {
                for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    if (!l.isBlank()) CasinoTour.VOICES.add(l.trim());
                }
            }
        } catch (IOException ignored) {
            // ohne Liste: Rundgang ohne Stimme (nur Untertitel)
        }
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
        hideNearCams(p, st, start, o);
        o.onEnd = (pp, res) -> {
            if (res != Cinema.Result.ABORTED) CasinoText.send(pp, "<gold>Viel Glück!</gold> <gray>Rundgang wiederholen: <white>/casino rundgang");
        };
        return plugin.cinema().play(p, new CasinoTour(start, dir, st), o);
    }

    /**
     * Schwebende Schriften (Tisch- und NPC-Hinweise wie „Rechtsklick · Spielen“), die direkt vor einer Kamera stehen,
     * für den Zuschauer ausblenden: im echten Client hingen sie riesig oben im Bild. Was die Kamera zeigen soll (der
     * Crash-Bildschirm, das Schild der Station), bleibt sichtbar. Am Ende der Szene ist alles wieder da.
     */
    private static void hideNearCams(Player p, List<CasinoTour.Station> st, V3 start, Cinema.Options o) {
        List<V3[]> cams = new ArrayList<>();      // {Kamera, Ziel}
        for (CasinoTour.Station s : st) {
            cams.add(new V3[]{s.brollCam(), s.brollLook()});
            // Zweier-Einstellung zeigt Vincent und den Spieler, keine Schilder: dort alles in Kameranähe ausblenden
            if (s.twoCam() != null) cams.add(new V3[]{s.twoCam(), null});
            // Blackjack-Tische: Dealer- und Platz-Schilder („Rechtsklick · Spielen“, „Freier Platz“) hängen sonst groß
            // hinter Vincent im Bild (im echten Client am Elite-Tisch gesehen); der Schnitt zeigt den Dealer selbst
            if (s.name() != null && java.util.Set.of("blackjack", "highroller", "elite").contains(s.name())) {
                cams.add(new V3[]{s.brollLook(), null});
            }
        }
        cams.add(new V3[]{start.up(1.7), null});
        for (org.bukkit.entity.TextDisplay td : p.getWorld().getEntitiesByClass(org.bukkit.entity.TextDisplay.class)) {
            Location l = td.getLocation();
            if (l.distanceSquared(p.getLocation()) > (RANGE + 10) * (RANGE + 10)) continue;
            V3 t = v(l);
            for (V3[] c : cams) {
                if (t.dist(c[0]) < 3.4 && (c[1] == null || t.dist(c[1]) > 1.3)) {
                    o.shared.add(td);
                    break;
                }
            }
        }
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

    /** Ist an dieser Stelle (mit etwas Abstand rundherum) kein undurchsichtiger Block? Sonst sieht die Kamera schwarz. */
    private static boolean clear(org.bukkit.World w, V3 c) {
        for (double dx : new double[]{-0.25, 0, 0.25}) {
            for (double dz : new double[]{-0.25, 0, 0.25}) {
                for (double dy : new double[]{-0.2, 0, 0.2}) {
                    Block b = w.getBlockAt((int) Math.floor(c.x() + dx), (int) Math.floor(c.y() + dy), (int) Math.floor(c.z() + dz));
                    if (b.getType().isOccluding() || b.getType().isSolid() && !b.isPassable()) return false;
                }
            }
        }
        return true;
    }

    /**
     * Kamera aus Wänden und Decken holen (im echten Client gesehen: bei Tischen an der Wand war das halbe Bild schwarz).
     * Erst die Gegenseite (gespiegelt um die Blickachse), sonst schrittweise zum Ziel hin.
     */
    private static V3 safeCam(org.bukkit.World w, V3 cam, V3 look, V3 mirror) {
        if (clear(w, cam)) return cam;
        if (mirror != null && clear(w, mirror)) return mirror;
        for (int i = 1; i <= 16; i++) {
            V3 c = cam.lerp(look, i / 20.0);
            if (clear(w, c)) return c;
        }
        return cam;
    }

    private CasinoTour.Station station(Player viewer, String name, Location at, V3 front, double dist, V3 bCam, V3 bLook,
                                       String a, String b) {
        V3 focus = floor(at);
        org.bukkit.World w = at.getWorld();
        V3 f = front.flat();
        V3 r = f.right();
        Player other = playerAt(viewer, at);
        if (other != null) {
            // mitten im Satz auf einen echten Spieler schneiden
            V3 eye = v(other.getEyeLocation());
            bCam = eye.add(f.mul(1.6)).add(r.mul(0.8)).up(0.15);
            bLook = eye.up(-0.15);
        }
        bCam = safeCam(w, bCam, bLook, null);
        // Zweier-Einstellung (Vincent und Spieler, Station dahinter): erst rechts, sonst links, sonst näher heran
        V3 spot = focus.add(f.mul(dist));
        V3 lookTwo = spot.up(1.45);
        V3 two = safeCam(w, spot.add(f.mul(2.6)).add(r.mul(2.2)).up(1.75), lookTwo, spot.add(f.mul(2.6)).add(r.mul(-2.2)).up(1.75));
        return new CasinoTour.Station(name, focus, front, dist, bCam, bLook, new String[]{a, b}, two);
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
                        "Siegesserien lohnen sich – jeder Sieg in Folge zahlt ein Stück mehr, bis zum Zweieinhalbfachen.");
            });
        }
        // Poker (normal und weinrot)
        for (var pk : plugin.pokerTables()) {
            Location c = pk.tableLocation();
            if (c == null || !near(c, me)) continue;
            V3 f = V3.dir(pk.tableYaw(), 0).mul(-1);
            V3 dealer = v(c).add(f.mul(-1.9)).up(1.55);
            // Schnitt: schräg von oben über den Filz (Karten, Pot, Dealer), nicht aus Tischhöhe auf leere Hocker
            out.add(station(p, switch (pk.tier()) {
                case ROT -> "poker_rot";
                case VIOLETT -> "poker_violett";
                default -> "poker";
            }, c, f, 2.8, v(c).add(f.mul(2.2)).up(2.6), v(c).add(f.mul(-0.3)).up(0.7),
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
            // Schnitt: Totale von schräg oben auf das ganze Rad (nicht nah am Text in der Mitte)
            out.add(station(p, "roulette", c, f, 3.0, v(c).add(f.mul(3.4)).up(3.0), v(c).up(0.2),
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
            out.add(station(p, "crash", c, f, 4.0, v(c).add(f.mul(Math.max(5.5, cs.width() * 1.1))).add(f.right().mul(1.4)).up(-0.4), v(c).up(-0.2),
                    "Crash: Der Kurs steigt – und je höher er steigt, desto schneller wird er.",
                    "Steigen Sie rechtzeitig aus. Wer zu gierig ist, verliert alles."));
        }
        // Jukebox
        Entity juke = nearest(plugin.casinoNpc().find(CasinoNpc.Role.MUSIK, me, RANGE), Entity::getLocation, me);
        if (juke != null) {
            V3 f = V3.dir(juke.getLocation().getYaw(), 0);
            // Halbnah von schräg vorn auf Kopf und Oberkörper (im echten Client war 1,5 Blöcke nur noch Stoff im Bild)
            V3 head = v(juke.getLocation()).up(1.45);
            out.add(station(p, "musik", juke.getLocation(), f, 2.0, head.add(f.mul(2.8)).add(f.right().mul(1.1)).up(0.15), head.up(-0.2),
                    "Und für die richtige Stimmung: unsere Jukebox.",
                    "Wünschen Sie sich ein Lied – hier spielt, was Ihnen gefällt."));
        }
        return out;
    }
}
