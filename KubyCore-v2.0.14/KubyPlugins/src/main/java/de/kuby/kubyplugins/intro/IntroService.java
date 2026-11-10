package de.kuby.kubyplugins.intro;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cinema.Cinema;
import de.kuby.kubyplugins.cinema.V3;
import de.kuby.kubyplugins.cinema.scenes.HubFlight;
import de.kuby.kubyplugins.gui.ModeSelectScreen;
import de.kuby.kubyplugins.intro.IntroScreen.Line;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Einführung für neue Spieler (ersetzt die alte Survival-Tour).
 * <p>
 * Beim ersten Join: Kamerafahrt über den Hub (Kino-Engine, Wegpunkte per /kp einfuehrung kamera, sonst Rundflug um den
 * Spawn), danach vier Dialog-Schritte (Willkommen, Spielmodi, Menü und Hilfe, Los geht's) und die Modus-Wahl; wer bis
 * zum Ende geht, bekommt ein Willkommensgeschenk (Juwelen).
 * <p>
 * Beim ersten Betreten von Survival, SMP und Skyblock: kurze Einführung des Modus mit drei Aufgaben (z. B. Menü öffnen,
 * Home setzen, Job wählen; in Skyblock Blöcke abbauen, Generator kaufen, Lager einsammeln). Jede Aufgabe bringt Coins im
 * Modus, alle drei einen Bonus. Erkannt werden die Aufgaben über die Quest-Ereignisse (KubyPlugins.questProgress).
 * <p>
 * Überspringen geht jederzeit (nur diese oder alle Einführungen), /einfuehrung startet sie neu bzw. zeigt den Stand.
 * Belohnungen gibt es je Aufgabe nur einmal. Bestehende Spieler bekommen keine automatische Einführung, können sie aber
 * mit /einfuehrung starten. Ohne Grafik-Pack läuft alles im Chat (ohne Kamerafahrt). Daten: einfuehrung.yml.
 */
public final class IntroService implements Listener {

    enum St { NEU, AKTIV, FERTIG, WEG }

    static final class ModeRec {
        St st = St.NEU;
        final Map<String, Long> progress = new LinkedHashMap<>();
        final Set<String> paid = new HashSet<>();
        boolean bonus;
    }

    static final class Rec {
        St hub = St.NEU;
        boolean hubPaid, allOff;
        final Map<String, ModeRec> modes = new HashMap<>();

        ModeRec mode(String m) {
            return modes.computeIfAbsent(m, k -> new ModeRec());
        }
    }

    /** Aufgabe: Schlüssel, Ereignisse (Quest-Typen), Menge, Symbol, Text. Belohnung aus der config. */
    public record TaskDef(String key, Set<String> events, long need, KitData.Icon icon, String text, long reward) {}

    public record ModeDef(String mode, String label, KitData.Icon icon, TextColor color, String sub, List<Line> lines, List<TaskDef> tasks,
                          long bonus, List<Line> tips) {}

    /** Belohnung je Modus und Aufgabe (aus der config, sonst Vorgabe). */
    @FunctionalInterface
    public interface Rewards {
        long get(String mode, String key, long def);
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Rec> recs = new HashMap<>();
    private final Map<UUID, Integer> hubStep = new HashMap<>();
    /** Spieler, denen in dieser Sitzung schon an den offenen Stand erinnert wurde (je Modus). */
    private final Set<String> reminded = new HashSet<>();
    private boolean dirty;

    public IntroService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "einfuehrung.yml");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.registerQuestHook(this::progress);
        plugin.modes().onEnterAny(this::entered);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("einfuehrung.aktiv", true);
    }

    private String server() {
        return plugin.getConfig().getString("einfuehrung.servername", "Kuby");
    }

    private Rec rec(UUID id) {
        return recs.get(id);
    }

    private Rec recOrNew(UUID id) {
        dirty = true;
        return recs.computeIfAbsent(id, k -> new Rec());
    }

    private boolean pack(Player p) {
        return plugin.uiPack() != null && plugin.uiPack().active(p);
    }

    /** Klick: erst den Dialog schließen, dann weiter. */
    private static java.util.function.Consumer<Player> close(java.util.function.Consumer<Player> then) {
        return p -> {
            UiDialog.close(p);
            if (then != null) then.accept(p);
        };
    }

    // =================================================================================================================
    // Erster Join: Willkommens-Tour
    // =================================================================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || p.hasPlayedBefore()) return;
        Rec r = recOrNew(p.getUniqueId());
        r.hub = St.AKTIV;
        // warten, bis das Grafik-Pack geladen ist (höchstens 45 s, sonst im Chat)
        int[] tries = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || r.hub != St.AKTIV || !ModeService.HUB.equals(plugin.modes().modeOf(p.getUniqueId()))) {
                task.cancel();
                return;
            }
            boolean ready = pack(p) && tries[0] >= 3;
            if (ready || ++tries[0] > 45) {
                task.cancel();
                startHub(p, true);
            }
        }, 40L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hubStep.remove(e.getPlayer().getUniqueId());
        reminded.removeIf(k -> k.startsWith(e.getPlayer().getUniqueId().toString()));
    }

    /** Willkommens-Tour starten (mit Kamerafahrt, wenn möglich). */
    public void startHub(Player p, boolean flight) {
        Rec r = recOrNew(p.getUniqueId());
        r.hub = St.AKTIV;
        r.allOff = false;
        hubStep.put(p.getUniqueId(), 0);
        Location spawn = plugin.modes().spawnOf(ModeService.HUB);
        boolean inHub = ModeService.HUB.equals(plugin.modes().modeOf(p.getUniqueId()));
        if (flight && inHub && spawn != null && pack(p) && plugin.cinema() != null && !plugin.cinema().active(p)) {
            Cinema.Options o = new Cinema.Options();
            o.anchor = spawn;
            o.endAt = spawn;
            o.skippable = true;
            o.onEnd = (pl, res) -> {
                if (res != Cinema.Result.ABORTED && pl.isOnline()) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> showHub(pl, 0), 6L);
                }
            };
            HubFlight s = new HubFlight(new V3(spawn.getX(), spawn.getY(), spawn.getZ()), spawn.getYaw(), cameraPath(spawn), server());
            if (plugin.cinema().play(p, s, o)) return;
        }
        showHub(p, 0);
    }

    /** Wegpunkte der Kamera aus der config ("x;y;z;yaw;pitch" in der Hub-Welt). */
    private List<HubFlight.Point> cameraPath(Location spawn) {
        List<HubFlight.Point> out = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("einfuehrung.kamera")) {
            String[] a = s.split(";");
            if (a.length < 5) continue;
            try {
                out.add(new HubFlight.Point(new V3(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2])),
                        Float.parseFloat(a[3]), Float.parseFloat(a[4])));
            } catch (NumberFormatException ignored) {
                // kaputter Eintrag
            }
        }
        return out;
    }

    static final int HUB_STEPS = 4;

    List<Object[]> hubSteps() {
        return hubSteps(plugin.getConfig().getLong("einfuehrung.juwelen", 25));
    }

    /** Inhalt der Schritte der Willkommens-Tour: Symbol, Titel, Text, Zeilen, Knopf. */
    public static List<Object[]> hubSteps(long jewels) {
        return List.of(
                new Object[]{KitData.Icon.STAR, "Willkommen!", "Schön, dass du da bist! In zwei Minuten zeigen wir dir das Wichtigste. "
                        + "Überspringen geht jederzeit, mit /einfuehrung startest du die Einführung später neu.",
                        List.of(new Line(KitData.Icon.COMPASS, "Drei Spielmodi", "SMP, Survival und Skyblock, alle auf einem Server"),
                                new Line(KitData.Icon.GOLD_INGOT, "Eigenes Geld je Modus", "Coins und Inventar gelten im Modus, Juwelen überall"),
                                new Line(KitData.Icon.EMERALD, "Belohnungen für Neue", "Für die Tour und deine ersten Aufgaben gibt es Coins und Juwelen")),
                        "Weiter ›"},
                new Object[]{KitData.Icon.COMPASS, "Die Spielmodi", "Du kannst jederzeit wechseln, dein Stand bleibt im Modus gespeichert.",
                        List.of(new Line(KitData.Icon.IRON_SWORD, "SMP", "PvP, Kopfgelder, Teams und Turniere"),
                                new Line(KitData.Icon.GRASS_BLOCK, "Survival", "Ruhig bauen, Jobs, Angeln, Grundstücke"),
                                new Line(KitData.Icon.OAK_SAPLING, "Skyblock", "Eigene Insel im Himmel, Generatoren, One-Block"),
                                new Line(KitData.Icon.COMPASS, "Hub", "Hier in der Lobby: Kompass (Rechtsklick) wählt den Modus")),
                        "Weiter ›"},
                new Object[]{KitData.Icon.BOOK, "Menü und Hilfe", "Diese Befehle brauchst du am häufigsten:",
                        List.of(new Line(KitData.Icon.CHEST, "/menu", "Alles an einem Ort: Shop, Quests, Homes, Freunde und mehr"),
                                new Line(KitData.Icon.BOOK, "/guide und /hilfe", "Erklärungen zu jedem Modus"),
                                new Line(KitData.Icon.COMPARATOR, "/settings", "Anzeige, Chat, Sprache und Info-Leiste einstellen"),
                                new Line(KitData.Icon.BELL, "/support und /discord", "Fragen an das Team, Community auf Discord")),
                        "Weiter ›"},
                new Object[]{KitData.Icon.FIREWORK_ROCKET, "Los geht's!", "Wähle jetzt deinen ersten Modus. Beim ersten Betreten zeigt dir eine kurze "
                        + "Einführung drei Aufgaben, jede bringt eine Belohnung.",
                        List.of(new Line(KitData.Icon.EMERALD, "Willkommensgeschenk", "+" + jewels + " Juwelen, wenn du jetzt einen Modus wählst"),
                                new Line(KitData.Icon.COMPASS, "Später wechseln", "Mit dem Kompass im Hub oder /hub")),
                        "Modus wählen ›"});
    }

    public void showHub(Player p, int i) {
        Rec r = recOrNew(p.getUniqueId());
        if (r.hub != St.AKTIV) r.hub = St.AKTIV;
        List<Object[]> steps = hubSteps();
        i = Math.max(0, Math.min(steps.size() - 1, i));
        hubStep.put(p.getUniqueId(), i);
        Object[] s = steps.get(i);
        int idx = i;
        @SuppressWarnings("unchecked") List<Line> lines = (List<Line>) s[3];
        if (!pack(p)) {
            chat(p, (String) s[1], (String) s[2], lines, "Schritt " + (i + 1) + " von " + steps.size(), (String) s[4]);
            return;
        }
        Sfx.play(plugin, p, i == 0 ? Sfx.Kind.CHIME : Sfx.Kind.PING);
        UiDialog.show(p, IntroScreen.step(new IntroScreen.Step((KitData.Icon) s[0], (String) s[1], "Einführung · Schritt " + (i + 1) + " von "
                + steps.size(), (String) s[2], lines, i, steps.size(), (String) s[4],
                close(pl -> nextHub(pl, idx)), idx > 0 ? close(pl -> showHub(pl, idx - 1)) : null,
                close(pl -> askSkip(pl, null)))), 250);
    }

    private void nextHub(Player p, int i) {
        if (i + 1 < HUB_STEPS) {
            showHub(p, i + 1);
            return;
        }
        finishHub(p);
    }

    private void finishHub(Player p) {
        Rec r = recOrNew(p.getUniqueId());
        r.hub = St.FERTIG;
        hubStep.remove(p.getUniqueId());
        long jewels = plugin.getConfig().getLong("einfuehrung.juwelen", 25);
        if (!r.hubPaid && jewels > 0) {
            r.hubPaid = true;
            plugin.jewels().add(p.getUniqueId(), jewels);
            Msg.send(p, "<green>Willkommensgeschenk: <#5FE3A0>+" + jewels + " Juwelen<green>!");
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        }
        dirty = true;
        if (ModeService.HUB.equals(plugin.modes().modeOf(p.getUniqueId()))) ModeSelectScreen.open(plugin, p);
    }

    // =================================================================================================================
    // Modus-Einführungen
    // =================================================================================================================

    long reward(String mode, String key, long def) {
        return plugin.getConfig().getLong("einfuehrung.belohnungen." + mode + "." + key, def);
    }

    ModeDef def(String mode) {
        return def(mode, this::reward);
    }

    /** Einführung eines Modus: Texte, Aufgaben, Tipps (null für den Hub). */
    public static ModeDef def(String mode, Rewards rw) {
        return switch (mode) {
            case ModeService.SURVIVAL -> new ModeDef(mode, "Survival", KitData.Icon.GRASS_BLOCK, TextColor.color(0x6CE086),
                    "Ruhig bauen, Geld verdienen, eine eigene Ecke",
                    List.of(new Line(KitData.Icon.GOLDEN_AXE, "Grundstücke", "Schütze dein Haus mit der Claim-Axt: /claim"),
                            new Line(KitData.Icon.IRON_PICKAXE, "Jobs", "Coins für Holzfällen, Bergbau, Farmen und mehr: /job"),
                            new Line(KitData.Icon.WRITABLE, "Quests", "Tages- und Wochenaufgaben mit Belohnungen: /quest")),
                    List.of(new TaskDef("menu", Set.of("menu"), 1, KitData.Icon.CHEST, "Öffne das Menü mit /menu", rw.get(mode, "menu", 150)),
                            new TaskDef("home", Set.of("home"), 1, KitData.Icon.RED_BED, "Setz dein erstes Home mit /sethome", rw.get(mode, "home", 200)),
                            new TaskDef("job", Set.of("job"), 1, KitData.Icon.IRON_PICKAXE, "Such dir einen Job aus: /job", rw.get(mode, "job", 300))),
                    rw.get(mode, "bonus", 500),
                    List.of(new Line(KitData.Icon.BOOK, "/hilfe", "Erklärungen zu allem in Survival"),
                            new Line(KitData.Icon.GOLDEN_AXE, "/claim", "Grundstück schützen, bevor du baust"),
                            new Line(KitData.Icon.WRITABLE, "/quest", "Tagesaufgaben für Coins und Tokens"),
                            new Line(KitData.Icon.ENDER_PEARL, "/rtp", "Raus in die Welt, weg vom Spawn")));
            case ModeService.SMP -> new ModeDef(mode, "SMP", KitData.Icon.IRON_SWORD, TextColor.color(0xFF6A5A),
                    "PvP, Kopfgelder, Teams und Turniere",
                    List.of(new Line(KitData.Icon.IRON_SWORD, "Kämpfen", "PvP außerhalb des Spawns, Trophäen für Siege"),
                            new Line(KitData.Icon.WHITE_BANNER, "Teams", "Gründe ein Team oder tritt einem bei: /team"),
                            new Line(KitData.Icon.BP_KEY, "Battle-Pass", "Belohnungen für jedes Level: /battlepass")),
                    List.of(new TaskDef("menu", Set.of("menu"), 1, KitData.Icon.CHEST, "Öffne das Menü mit /menu", rw.get(mode, "menu", 150)),
                            new TaskDef("home", Set.of("home"), 1, KitData.Icon.RED_BED, "Setz dein erstes Home mit /sethome", rw.get(mode, "home", 200)),
                            new TaskDef("battlepass", Set.of("battlepass"), 1, KitData.Icon.BP_KEY, "Schau in den Battle-Pass: /battlepass",
                                    rw.get(mode, "battlepass", 300))),
                    rw.get(mode, "bonus", 500),
                    List.of(new Line(KitData.Icon.BOOK, "/guide", "Alles über das SMP auf einen Blick"),
                            new Line(KitData.Icon.WHITE_BANNER, "/team", "Mit Freunden zusammen spielen"),
                            new Line(KitData.Icon.ENDER_PEARL, "/rtp", "Raus in die Welt, weg vom Spawn"),
                            new Line(KitData.Icon.EMERALD, "/juwelenshop", "Ränge, Kisten und Pakete")));
            case ModeService.SKYBLOCK -> new ModeDef(mode, "Skyblock", KitData.Icon.OAK_SAPLING, TextColor.color(0x6FE3D2),
                    "Eigene Insel im Himmel, Generatoren, One-Block",
                    List.of(new Line(KitData.Icon.GRASS_BLOCK, "Deine Insel", "Mitglieder, Upgrades und Level: /insel"),
                            new Line(KitData.Icon.SPAWNER, "Generatoren", "Geld, XP und Items von selbst, laufen im Menü"),
                            new Line(KitData.Icon.CHEST_BLOCK, "One-Block", "Ein Block, der immer wieder kommt: /oneblock")),
                    List.of(new TaskDef("abbau", Set.of("abbau"), 20, KitData.Icon.IRON_PICKAXE, "Bau 20 Blöcke auf deiner Insel ab",
                                    rw.get(mode, "abbau", 200)),
                            new TaskDef("generator", Set.of("gen_place"), 1, KitData.Icon.SPAWNER, "Kauf einen Generator: /insel → Generatoren",
                                    rw.get(mode, "generator", 400)),
                            new TaskDef("einsammeln", Set.of("gen_collect", "gen_geld", "gen_xp"), 1, KitData.Icon.CHEST,
                                    "Sammle das Lager eines Generators ein", rw.get(mode, "einsammeln", 300))),
                    rw.get(mode, "bonus", 750),
                    List.of(new Line(KitData.Icon.BOOK, "/guide", "Alles über Skyblock auf einen Blick"),
                            new Line(KitData.Icon.GRASS_BLOCK, "/insel", "Upgrades, Mitglieder, Top-Inseln"),
                            new Line(KitData.Icon.CHEST_BLOCK, "/oneblock", "Mit dem Team einen One-Block beanspruchen"),
                            new Line(KitData.Icon.ENDER_EYE, "/insel stufen", "Später: Nether und End mit neuen Generatoren")));
            default -> null;
        };
    }

    /** Modus betreten: beim ersten Mal die Einführung zeigen, sonst einmal je Sitzung an offene Aufgaben erinnern. */
    private void entered(String mode, Player p) {
        if (!enabled() || def(mode) == null) return;
        Rec r = rec(p.getUniqueId());
        if (r == null || r.allOff) return;
        ModeRec m = r.mode(mode);
        if (m.st == St.NEU) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> autoStart(p, mode, 0), 50L);
        } else if (m.st == St.AKTIV && reminded.add(p.getUniqueId() + ";" + mode)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> remind(p, mode), 80L);
        }
    }

    /** Einführung eines Modus automatisch zeigen, sobald der Spieler bereit ist (Skyblock: erst mit Insel). */
    private void autoStart(Player p, String mode, int tries) {
        if (!p.isOnline() || !mode.equals(plugin.modes().modeOf(p.getUniqueId()))) return;
        Rec r = rec(p.getUniqueId());
        if (r == null || r.allOff || r.mode(mode).st != St.NEU) return;
        boolean waitIsland = ModeService.SKYBLOCK.equals(mode) && plugin.hasStat("island_members")
                && plugin.statValue("island_members", p.getUniqueId()) <= 0;
        if (waitIsland || (plugin.cinema() != null && plugin.cinema().active(p))) {
            // Insel wird gerade ausgewählt/angelegt: später nochmal (höchstens 5 Minuten)
            if (tries < 60) plugin.getServer().getScheduler().runTaskLater(plugin, () -> autoStart(p, mode, tries + 1), 100L);
            return;
        }
        startMode(p, mode);
    }

    /** Einführung eines Modus starten bzw. den Stand zeigen. */
    public void startMode(Player p, String mode) {
        ModeDef d = def(mode);
        if (d == null) return;
        Rec r = recOrNew(p.getUniqueId());
        r.allOff = false;
        ModeRec m = r.mode(mode);
        boolean started = m.st == St.AKTIV || m.st == St.FERTIG;
        if (m.st != St.FERTIG) m.st = St.AKTIV;
        dirty = true;
        showMode(p, d, m, started);
    }

    private void showMode(Player p, ModeDef d, ModeRec m, boolean started) {
        List<IntroScreen.Task> tasks = new ArrayList<>();
        for (TaskDef t : d.tasks()) {
            long have = m.progress.getOrDefault(t.key(), 0L);
            String text = t.need() > 1 && have < t.need() ? t.text() + " (" + have + "/" + t.need() + ")" : t.text();
            tasks.add(new IntroScreen.Task(t.icon(), text, t.reward(), have >= t.need()));
        }
        if (!pack(p)) {
            List<Line> lines = new ArrayList<>(d.lines());
            for (IntroScreen.Task t : tasks) {
                lines.add(new Line(t.icon(), (t.done() ? "✔ " : "• ") + t.text(), "+" + Msg.coins(t.reward()) + " Coins"));
            }
            chat(p, "Willkommen in " + d.label(), d.sub(), lines, null, null);
            return;
        }
        Sfx.play(plugin, p, started ? Sfx.Kind.OPEN : Sfx.Kind.CHIME);
        UiDialog.show(p, IntroScreen.mode(new IntroScreen.ModeView(d.icon(), d.label(), d.color(), d.sub(), d.lines(), tasks, d.bonus(), "Coins",
                started, close(null), m.st == St.FERTIG ? null : close(pl -> askSkip(pl, d.mode())))), 250);
    }

    private void remind(Player p, String mode) {
        if (!p.isOnline() || !mode.equals(plugin.modes().modeOf(p.getUniqueId()))) return;
        Rec r = rec(p.getUniqueId());
        ModeDef d = def(mode);
        if (r == null || d == null) return;
        ModeRec m = r.mode(mode);
        if (m.st != St.AKTIV) return;
        int done = 0;
        TaskDef next = null;
        for (TaskDef t : d.tasks()) {
            if (m.progress.getOrDefault(t.key(), 0L) >= t.need()) done++;
            else if (next == null) next = t;
        }
        if (next == null) return;
        Msg.send(p, "<gold>Einführung " + d.label() + ": <white>" + done + "/" + d.tasks().size() + " <gray>· Nächste Aufgabe: <white>"
                + Msg.escape(next.text()) + " <dark_gray>(<click:run_command:'/einfuehrung'><gold>/einfuehrung</gold></click>)");
    }

    /** Ereignisse, die Aufgaben der Einführung zählen (alles andere wird sofort ignoriert). */
    static final Set<String> EVENTS = Set.of("menu", "home", "job", "battlepass", "abbau", "gen_place", "gen_collect", "gen_geld", "gen_xp");

    /** Quest-Ereignis: passende Aufgabe der laufenden Modus-Einführung zählen. */
    private void progress(Player p, String type, Object subject, long amount) {
        if (!EVENTS.contains(type) || recs.isEmpty()) return;
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> progress(p, type, subject, amount));
            return;
        }
        Rec r = rec(p.getUniqueId());
        if (r == null) return;
        String mode = plugin.modes().modeOf(p.getUniqueId());
        ModeRec m = r.modes.get(mode);
        if (m == null || m.st != St.AKTIV) return;
        ModeDef d = def(mode);
        if (d == null) return;
        boolean changed = false;
        for (TaskDef t : d.tasks()) {
            if (!t.events().contains(type)) continue;
            long have = m.progress.getOrDefault(t.key(), 0L);
            if (have >= t.need()) continue;
            long now = Math.min(t.need(), have + Math.max(1, amount));
            m.progress.put(t.key(), now);
            changed = true;
            if (now >= t.need() && m.paid.add(t.key())) taskDone(p, d, t);
        }
        if (!changed) return;
        dirty = true;
        boolean all = true;
        for (TaskDef t : d.tasks()) all &= m.progress.getOrDefault(t.key(), 0L) >= t.need();
        if (all) {
            m.st = St.FERTIG;
            boolean bonus = !m.bonus && d.bonus() > 0;
            m.bonus = true;
            if (bonus) plugin.modes().depositIn(p.getUniqueId(), mode, d.bonus());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> showDone(p, d, bonus), 30L);
        }
    }

    private void taskDone(Player p, ModeDef d, TaskDef t) {
        if (t.reward() > 0) plugin.modes().depositIn(p.getUniqueId(), d.mode(), t.reward());
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        p.showTitle(Title.title(net.kyori.adventure.text.Component.empty(), Msg.parse("<green>✔ Aufgabe geschafft <gray>· <gold>+"
                + Msg.coins(t.reward()) + " Coins"), Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
        Msg.send(p, "<green>✔ Einführung: <white>" + Msg.escape(t.text()) + " <gold>+" + Msg.coins(t.reward()) + " Coins");
        Rec r = rec(p.getUniqueId());
        ModeRec m = r == null ? null : r.modes.get(d.mode());
        if (m == null) return;
        for (TaskDef o : d.tasks()) {
            if (m.progress.getOrDefault(o.key(), 0L) < o.need() && o != t) {
                Msg.send(p, "<gray>Nächste Aufgabe: <white>" + Msg.escape(o.text()));
                return;
            }
        }
    }

    private void showDone(Player p, ModeDef d, boolean bonus) {
        if (!p.isOnline()) return;
        String reward = bonus ? "Bonus: +" + Msg.coins(d.bonus()) + " Coins · alle Aufgaben geschafft" : "Alle Aufgaben geschafft";
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        if (!pack(p)) {
            chat(p, "Einführung " + d.label() + " geschafft!", reward, d.tips(), null, null);
            return;
        }
        if (plugin.cinema() != null && plugin.cinema().active(p)) return;
        UiDialog.show(p, IntroScreen.done(new IntroScreen.Done(KitData.Icon.FIREWORK_ROCKET, d.label() + " geschafft!", reward, d.tips(),
                close(null))), 250);
    }

    // =================================================================================================================
    // Überspringen
    // =================================================================================================================

    /** Rückfrage: nur diese Einführung oder alle (mode null = Willkommens-Tour). */
    void askSkip(Player p, String mode) {
        String what = mode == null ? "die Willkommens-Tour" : "die " + ModeService.label(mode) + "-Einführung";
        java.util.function.Consumer<Player> back = mode == null
                ? pl -> showHub(pl, hubStep.getOrDefault(pl.getUniqueId(), 0))
                : pl -> startMode(pl, mode);
        if (!pack(p)) {
            skip(p, mode, false);
            return;
        }
        UiDialog.show(p, IntroScreen.skip(what, close(pl -> skip(pl, mode, false)), close(pl -> skip(pl, mode, true)), close(back)), 120);
    }

    void skip(Player p, String mode, boolean all) {
        Rec r = recOrNew(p.getUniqueId());
        if (mode == null) {
            r.hub = r.hub == St.FERTIG ? St.FERTIG : St.WEG;
            hubStep.remove(p.getUniqueId());
        } else {
            ModeRec m = r.mode(mode);
            if (m.st != St.FERTIG) m.st = St.WEG;
        }
        if (all) {
            r.allOff = true;
            for (String k : List.of(ModeService.SMP, ModeService.SURVIVAL, ModeService.SKYBLOCK)) {
                ModeRec m = r.mode(k);
                if (m.st == St.NEU || m.st == St.AKTIV) m.st = St.WEG;
            }
        }
        dirty = true;
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        Msg.send(p, "<gray>Einführung " + (all ? "komplett " : "") + "übersprungen. Mit <gold><click:run_command:'/einfuehrung'>/einfuehrung</click></gold> "
                + "startest du sie jederzeit neu.");
        if (mode == null && ModeService.HUB.equals(plugin.modes().modeOf(p.getUniqueId()))) ModeSelectScreen.open(plugin, p);
    }

    // =================================================================================================================
    // Befehle
    // =================================================================================================================

    /** /einfuehrung [weiter|zurueck|ueberspringen|tour]: im Hub die Willkommens-Tour, sonst die Einführung des Modus. */
    public void command(Player p, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        String mode = plugin.modes().modeOf(p.getUniqueId());
        switch (sub) {
            case "weiter" -> {
                Integer i = hubStep.get(p.getUniqueId());
                if (i != null) nextHub(p, i);
                else open(p);
            }
            case "zurueck", "zurück" -> {
                Integer i = hubStep.get(p.getUniqueId());
                if (i != null && i > 0) showHub(p, i - 1);
            }
            case "ueberspringen", "überspringen", "skip" -> skip(p, hubStep.containsKey(p.getUniqueId()) || def(mode) == null ? null : mode,
                    args.length > 1 && args[1].equalsIgnoreCase("alle"));
            case "tour" -> startHub(p, true);
            default -> open(p);
        }
    }

    /** Einführung passend zum Ort öffnen: im Hub die Tour, im Modus dessen Einführung. */
    public void open(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        if (def(mode) == null) startHub(p, true);
        else startMode(p, mode);
    }

    /** /kp einfuehrung kamera add|liste|loeschen · test · reset <Spieler>. */
    public void admin(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "kamera" -> {
                String op = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                List<String> l = new ArrayList<>(plugin.getConfig().getStringList("einfuehrung.kamera"));
                Location spawn = plugin.modes().spawnOf(ModeService.HUB);
                switch (op) {
                    case "add" -> {
                        if (spawn != null && !p.getWorld().equals(spawn.getWorld())) {
                            Msg.send(p, "<red>Wegpunkte setzt du in der Hub-Welt.");
                            return;
                        }
                        Location e = p.getEyeLocation();
                        l.add(String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%.1f;%.1f", e.getX(), e.getY(), e.getZ(), e.getYaw(), e.getPitch()));
                        plugin.getConfig().set("einfuehrung.kamera", l);
                        plugin.saveConfig();
                        Msg.send(p, "<green>Wegpunkt " + l.size() + " gesetzt (Ort und Blickrichtung). Ab 2 Punkten fliegt die Kamera sie ab.");
                    }
                    case "loeschen", "clear" -> {
                        plugin.getConfig().set("einfuehrung.kamera", new ArrayList<>());
                        plugin.saveConfig();
                        Msg.send(p, "<green>Wegpunkte gelöscht, die Kamera kreist wieder um den Hub-Spawn.");
                    }
                    default -> Msg.send(p, "<gold>Kamera-Wegpunkte: <white>" + l.size() + (l.size() < 2 ? " <gray>(Rundflug um den Spawn)" : "")
                            + "\n<white>/kp einfuehrung kamera add <gray>Punkt an deiner Position und Blickrichtung"
                            + "\n<white>/kp einfuehrung kamera loeschen <gray>alle Punkte weg");
                }
            }
            case "test" -> startHub(p, true);
            case "reset" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                if (t == null) {
                    Msg.send(p, "<red>Spieler nicht online.");
                    return;
                }
                Rec fresh = new Rec();
                Rec old = recs.get(t.getUniqueId());
                if (old != null) {
                    fresh.hubPaid = old.hubPaid;
                    old.modes.forEach((k, v) -> {
                        ModeRec m = fresh.mode(k);
                        m.paid.addAll(v.paid);
                        m.bonus = v.bonus;
                    });
                }
                recs.put(t.getUniqueId(), fresh);
                dirty = true;
                Msg.send(p, "<green>Einführung für " + t.getName() + " zurückgesetzt (Belohnungen gibt es nicht doppelt).");
            }
            default -> Msg.send(p, "<white>/kp einfuehrung kamera <gray>Wegpunkte der Kamerafahrt"
                    + "\n<white>/kp einfuehrung test <gray>Willkommens-Tour abspielen"
                    + "\n<white>/kp einfuehrung reset [Spieler] <gray>Einführung neu (wie beim ersten Join)");
        }
    }

    // =================================================================================================================
    // Ohne Grafik-Pack: im Chat
    // =================================================================================================================

    private void chat(Player p, String title, String text, List<Line> lines, String step, String next) {
        p.sendMessage(net.kyori.adventure.text.Component.empty());
        Msg.send(p, "<gold><b>" + Msg.escape(title) + "</b>" + (step == null ? "" : " <dark_gray>· " + step));
        if (text != null && !text.isBlank()) Msg.send(p, "<gray>" + Msg.escape(text));
        for (Line l : lines) Msg.send(p, " <gold>" + Msg.escape(l.head()) + " <dark_gray>- <white>" + Msg.escape(l.text()));
        String buttons = (next == null ? "" : "<click:run_command:'/einfuehrung weiter'><green>[" + Msg.escape(next) + "]</green></click> ")
                + (step != null && !step.startsWith("Schritt 1 ") ? "<click:run_command:'/einfuehrung zurueck'><gray>[Zurück]</gray></click> " : "")
                + "<click:run_command:'/einfuehrung ueberspringen'><dark_gray>[Überspringen]</dark_gray></click>";
        Msg.send(p, buttons);
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("spieler");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Rec r = new Rec();
                r.hub = St.valueOf(c.getString("hub", "NEU"));
                r.hubPaid = c.getBoolean("hub-bezahlt");
                r.allOff = c.getBoolean("alle-aus");
                ConfigurationSection ms = c.getConfigurationSection("modi");
                if (ms != null) {
                    for (String mk : ms.getKeys(false)) {
                        ConfigurationSection mc = ms.getConfigurationSection(mk);
                        if (mc == null) continue;
                        ModeRec m = r.mode(mk);
                        m.st = St.valueOf(mc.getString("stand", "NEU"));
                        m.bonus = mc.getBoolean("bonus");
                        m.paid.addAll(mc.getStringList("bezahlt"));
                        ConfigurationSection pr = mc.getConfigurationSection("fortschritt");
                        if (pr != null) for (String t : pr.getKeys(false)) m.progress.put(t, pr.getLong(t));
                    }
                }
                recs.put(UUID.fromString(k), r);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("einfuehrung.yml: " + k + " übersprungen (" + ex.getMessage() + ")");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        recs.forEach((id, r) -> {
            String b = "spieler." + id + ".";
            y.set(b + "hub", r.hub.name());
            if (r.hubPaid) y.set(b + "hub-bezahlt", true);
            if (r.allOff) y.set(b + "alle-aus", true);
            r.modes.forEach((k, m) -> {
                if (m.st == St.NEU && m.progress.isEmpty() && m.paid.isEmpty()) return;
                String mb = b + "modi." + k + ".";
                y.set(mb + "stand", m.st.name());
                if (m.bonus) y.set(mb + "bonus", true);
                if (!m.paid.isEmpty()) y.set(mb + "bezahlt", new ArrayList<>(m.paid));
                m.progress.forEach((t, v) -> y.set(mb + "fortschritt." + t, v));
            });
        });
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("einfuehrung.yml: " + e.getMessage());
        }
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    /** Alle echten Inhalte als Dialog (prüft, dass alles in die Kästen passt). */
    public void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        java.util.function.Consumer<Player> no = p -> { };
        List<Object[]> steps = hubSteps();
        for (int i = 0; i < steps.size(); i++) {
            Object[] s = steps.get(i);
            int idx = i;
            @SuppressWarnings("unchecked") List<Line> lines = (List<Line>) s[3];
            reg.accept("Einführung Tour " + (i + 1), () -> IntroScreen.step(new IntroScreen.Step((KitData.Icon) s[0], (String) s[1],
                    "Einführung · Schritt " + (idx + 1) + " von " + steps.size(), (String) s[2], lines, idx, steps.size(), (String) s[4], no,
                    idx > 0 ? no : null, no)));
        }
        for (String mode : List.of(ModeService.SURVIVAL, ModeService.SMP, ModeService.SKYBLOCK)) {
            ModeDef d = def(mode);
            List<IntroScreen.Task> tasks = new ArrayList<>();
            for (TaskDef t : d.tasks()) tasks.add(new IntroScreen.Task(t.icon(), t.need() > 1 ? t.text() + " (12/" + t.need() + ")" : t.text(),
                    t.reward(), false));
            reg.accept("Einführung " + d.label(), () -> IntroScreen.mode(new IntroScreen.ModeView(d.icon(), d.label(), d.color(), d.sub(), d.lines(),
                    tasks, d.bonus(), "Coins", false, no, no)));
            reg.accept("Einführung " + d.label() + " geschafft", () -> IntroScreen.done(new IntroScreen.Done(KitData.Icon.FIREWORK_ROCKET,
                    d.label() + " geschafft!", "Bonus: +" + Msg.coins(d.bonus()) + " Coins · alle Aufgaben geschafft", d.tips(), no)));
        }
        IntroScreen.samples(reg);
    }
}
