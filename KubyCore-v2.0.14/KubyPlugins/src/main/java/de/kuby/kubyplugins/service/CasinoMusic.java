package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoScreens;
import de.kuby.kubyplugins.casino.CasinoText;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Casino-Musik (v2.07 neu, Wunsch des Besitzers: „mehr Musik im Casino, die man zufällig durchs Spielen, durch Quests im
 * Casino und im Chip-Shop bekommt; unterteilt in Klassik und Special; ein eigener Knopf für Musik aus; die alten Songs
 * aus Notenblöcken entfernen“).
 *
 * <p>Alle Songs sind echte Aufnahmen aus dem Pack (tools/build_casino_jazz.py, tools/build_casino_songs.py); die
 * Beschreibung liegt in resources/casino/songs/&lt;id&gt;.audio: „Name|Länge in Ticks|Sound|Kategorie|Freischalten“.
 * Freischalten: <b>frei</b> (alle haben ihn), <b>drop</b> (zufällig nach einer Casino-Runde), <b>quest:&lt;art&gt;:&lt;n&gt;</b>
 * (runden = gespielte Runden, siege = gewonnene Runden, serie = Siegesserie), <b>shop:&lt;Chips&gt;</b> (Chip-Shop, Art MUSIC).
 * Überschreibbar mit casino.musik.freischalten.&lt;id&gt;.
 *
 * <p>Wahl je Spieler: „alle:klassik“, „alle:special“, „aus“ oder eine Song-ID (Dauerschleife).
 */
public final class CasinoMusic {

    public record Track(String id, String name, int length, String audio, String category, String unlock) {
        public String time() {
            int s = length / 20;
            return (s / 60) + ":" + String.format("%02d", s % 60);
        }
    }

    public static final String ALL_KLASSIK = "alle:klassik", ALL_SPECIAL = "alle:special", OFF = "aus";
    public static final String KLASSIK = "klassik", SPECIAL = "special";
    /** Reihenfolge im Fenster; alte Notenblock-Songs (brisk, midnight, latenight) gibt es nicht mehr. */
    private static final String[] IDS = {"velvet", "golden", "plaza", "rumba", "neon", "pixel", "sternenstaub", "lagerfeuer"};
    /** Alte Wahl (Zahl) in der Reihenfolge von v2.06. */
    private static final String[] OLD = {"velvet", "golden"};

    private final KubyPlugins plugin;
    private final File file;
    private final List<Track> songs = new ArrayList<>();
    private final Map<UUID, String> choice = new HashMap<>();
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    /** Zähler für die Musik-Quests: gespielte Runden, Siege, beste Serie. */
    private final Map<UUID, int[]> stats = new HashMap<>();
    /** Wer gerade hört: Song-Index und Position (Tick). */
    private final Map<UUID, int[]> listening = new LinkedHashMap<>();
    /** Wer gerade im Casino steht (für die Begrüßung beim Betreten). */
    private final Set<UUID> present = new java.util.HashSet<>();
    /** Reiter im Fenster je Spieler. */
    private final Map<UUID, String> tab = new HashMap<>();
    private boolean dirty;

    public CasinoMusic(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-music.yml");
        for (String id : IDS) {
            Track t = load(id);
            if (t != null) songs.add(t);
        }
        // eigene Songs (z. B. lizenzierte Titel): Datei assets/kuby/sounds/music/<id>.ogg ins Pack legen, in sounds.json
        // „music.<id>“ eintragen und hier „id;Name;Länge in Sekunden;klassik|special;Freischalten“ angeben
        for (String line : plugin.getConfig().getStringList("casino.musik.eigene")) {
            String[] q = line.split(";");
            if (q.length < 3 || track(q[0].trim()) != null) continue;
            try {
                songs.add(new Track(q[0].trim(), q[1].trim(), (int) (Double.parseDouble(q[2].trim().replace(',', '.')) * 20) + 20,
                        "kuby:music." + q[0].trim(), q.length > 3 ? q[3].trim() : KLASSIK, q.length > 4 ? q[4].trim() : "frei"));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("casino.musik.eigene: Länge in Sekunden fehlt bei „" + line + "“");
            }
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = yml.getConfigurationSection("wahl");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    choice.put(UUID.fromString(key), migrate(s.get(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection o = yml.getConfigurationSection("songs");
        if (o != null) {
            for (String key : o.getKeys(false)) {
                try {
                    owned.put(UUID.fromString(key), new LinkedHashSet<>(o.getStringList(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection st = yml.getConfigurationSection("zaehler");
        if (st != null) {
            for (String key : st.getKeys(false)) {
                try {
                    List<Integer> l = st.getIntegerList(key);
                    stats.put(UUID.fromString(key), new int[]{l.size() > 0 ? l.get(0) : 0, l.size() > 1 ? l.get(1) : 0, l.size() > 2 ? l.get(2) : 0});
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    /** Wahl aus v2.06 (Zahl: -1 alle, -2 aus, 0.. Song) in die neue Form. */
    private static String migrate(Object raw) {
        if (raw instanceof Number n) {
            int c = n.intValue();
            if (c == -2) return OFF;
            if (c >= 0 && c < OLD.length) return OLD[c];
            return ALL_KLASSIK;
        }
        String s = String.valueOf(raw);
        return s.isBlank() ? ALL_KLASSIK : s;
    }

    private Track load(String id) {
        try (InputStream in = plugin.getResource("casino/songs/" + id + ".audio")) {
            if (in == null) return null;
            String[] head = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine().split("\\|");
            String cat = head.length > 3 ? head[3].trim() : KLASSIK;
            String unlock = head.length > 4 ? head[4].trim() : "frei";
            return new Track(id, head[0].trim(), Integer.parseInt(head[1].trim()), head[2].trim(), cat, unlock);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Casino-Song " + id + " konnte nicht geladen werden: " + e.getMessage());
            return null;
        }
    }

    public List<Track> songs() {
        return List.copyOf(songs);
    }

    public boolean exists(String id) {
        return track(id) != null;
    }

    private Track track(String id) {
        for (Track t : songs) if (t.id().equals(id)) return t;
        return null;
    }

    /** Freischalt-Art (casino.musik.freischalten.&lt;id&gt; überschreibt die Angabe im Song). */
    public String unlockOf(Track t) {
        return plugin.getConfig().getString("casino.musik.freischalten." + t.id(), t.unlock());
    }

    public synchronized boolean owns(UUID id, String song) {
        Track t = track(song);
        if (t == null) return false;
        return "frei".equals(unlockOf(t)) || owned.getOrDefault(id, Set.of()).contains(song);
    }

    public boolean inCasino(Location l) {
        RegionService.Region r = plugin.regions().find(CasinoService.REGION);
        return r != null && r.contains(l);
    }

    public synchronized String choiceOf(UUID id) {
        String c = choice.getOrDefault(id, ALL_KLASSIK);
        if (!c.equals(OFF) && !c.startsWith("alle:") && !owns(id, c)) return ALL_KLASSIK;
        return c;
    }

    /** Songs, die bei dieser Wahl laufen (Index in songs). */
    private synchronized List<Integer> playlist(UUID id) {
        String c = choiceOf(id);
        List<Integer> out = new ArrayList<>();
        if (c.equals(OFF)) return out;
        for (int i = 0; i < songs.size(); i++) {
            Track t = songs.get(i);
            if (!owns(id, t.id())) continue;
            if (c.startsWith("alle:") ? t.category().equals(c.substring(5)) : t.id().equals(c)) out.add(i);
        }
        if (out.isEmpty() && c.equals(ALL_SPECIAL)) {
            // noch kein Special-Song: Klassik spielen
            for (int i = 0; i < songs.size(); i++) if (owns(id, songs.get(i).id()) && songs.get(i).category().equals(KLASSIK)) out.add(i);
        }
        return out;
    }

    // =================================================================================================================
    // Freischalten: Drop, Quests, Chip-Shop
    // =================================================================================================================

    /**
     * Nach jeder Casino-Runde (CasinoPlay): Zähler für die Musik-Quests, dazu die Chance auf einen zufälligen Song
     * (casino.musik.drop-chance, Standard 0,5 % je Runde).
     */
    public void onRound(UUID id, boolean win, int streak) {
        Player p = plugin.getServer().getPlayer(id);
        List<String> fresh = new ArrayList<>();
        synchronized (this) {
            int[] s = stats.computeIfAbsent(id, k -> new int[3]);
            s[0]++;
            if (win) s[1]++;
            s[2] = Math.max(s[2], streak);
            dirty = true;
            for (Track t : songs) {
                String u = unlockOf(t);
                if (!u.startsWith("quest:") || owns(id, t.id())) continue;
                int[] q = questProgress(id, u);
                if (q != null && q[0] >= q[1]) fresh.add(t.id());
            }
            if (fresh.isEmpty() && Math.random() < plugin.getConfig().getDouble("casino.musik.drop-chance", 0.005)) {
                List<String> drops = new ArrayList<>();
                for (Track t : songs) if ("drop".equals(unlockOf(t)) && !owns(id, t.id())) drops.add(t.id());
                if (!drops.isEmpty()) fresh.add(drops.get((int) (Math.random() * drops.size())));
            }
        }
        for (String song : fresh) {
            if (p != null) unlock(p, song, fresh.size() == 1 && "drop".equals(unlockOf(track(song))) ? "Zufallsfund beim Spielen" : "Musik-Quest geschafft");
        }
    }

    /** {Stand, Ziel} einer Quest „quest:art:n“ oder null. */
    public synchronized int[] questProgress(UUID id, String unlock) {
        String[] p = unlock.split(":");
        if (p.length < 3) return null;
        int goal;
        try {
            goal = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        int[] s = stats.getOrDefault(id, new int[3]);
        int have = switch (p[1]) {
            case "runden" -> s[0];
            case "siege" -> s[1];
            case "serie" -> s[2];
            default -> 0;
        };
        return new int[]{Math.min(have, goal), goal};
    }

    /** Song freischalten (Drop, Quest, Chip-Shop). false, wenn er schon da war. */
    public boolean unlock(Player p, String song, String why) {
        Track t = track(song);
        if (t == null) return false;
        synchronized (this) {
            if (owns(p.getUniqueId(), song)) return false;
            owned.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>()).add(song);
            dirty = true;
        }
        save();
        p.showTitle(Title.title(Msg.parse("<#F2C94C>♪ Neuer Song!"), Msg.parse("<#F2DA90>" + t.name() + " <gray>· " + why),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        CasinoText.send(p, "<#F2C94C>♪ Neuer Song freigeschaltet:</#F2C94C> <white>" + t.name() + "</white> <gray>("
                + (SPECIAL.equals(t.category()) ? "Special" : "Klassik") + ", " + why + ") <click:run_command:'/music'><#F2DA90>[Musik öffnen]</#F2DA90></click>");
        return true;
    }

    // =================================================================================================================
    // Abspielen
    // =================================================================================================================

    private void tick() {
        if (listening.isEmpty() || songs.isEmpty()) return;
        float master = (float) plugin.getConfig().getDouble("casino.music.volume", 0.6);
        var it = listening.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, int[]> e = it.next();
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            int[] st = e.getValue();
            Track t = songs.get(Math.min(st[0], songs.size() - 1));
            if (st[1] == 0) p.playSound(p, t.audio(), SoundCategory.RECORDS, master, 1f);
            st[1]++;
            if (st[1] >= t.length()) {
                st[1] = 0;
                List<Integer> pl = playlist(e.getKey());
                if (pl.size() > 1) {
                    int at = pl.indexOf(st[0]);
                    st[0] = pl.get((at + 1) % pl.size());
                    nowPlaying(p, st[0]);
                } else if (pl.size() == 1) {
                    st[0] = pl.get(0);
                }
            }
        }
    }

    private void nowPlaying(Player p, int index) {
        p.sendActionBar(Msg.parse("<#F2C94C>♪</#F2C94C> <#F2DA90>" + songs.get(index).name() + "</#F2DA90> <dark_gray>·</dark_gray> <gray>/music"));
    }

    /** Startet die Musik, wenn der Spieler im Casino steht, sonst stoppt sie. */
    public synchronized void update(Player p, Location at) {
        // während einer Kino-Szene (z. B. Rundgang) folgt der Spieler der Kamera; fährt sie an den Rand des Casinos,
        // soll die Musik nicht aus- und wieder angehen (v2.02: im Rundgang fehlte die Musik an den Poker-Tischen)
        if (de.kuby.kubyplugins.cinema.Cinema.busy(p.getUniqueId())) return;
        boolean inside = at != null && inCasino(at);
        if (inside && present.add(p.getUniqueId())) {
            CasinoText.send(p, "Dein Stand <#F2C94C>" + CasinoText.chips(plugin.casino().chips(p.getUniqueId()))
                    + "</#F2C94C> <dark_gray>·</dark_gray> Kasse am Wechsel-NPC");
        } else if (!inside) {
            present.remove(p.getUniqueId());
        }
        boolean on = listening.containsKey(p.getUniqueId());
        if (inside && !on) start(p, -1);
        if (!inside && on) stop(p);
    }

    /** Läuft gerade Casino-Musik für diesen Spieler? */
    public synchronized boolean playing(UUID id) {
        return listening.containsKey(id);
    }

    /** Beim Ausloggen: nächstes Betreten zählt wieder als neu. */
    public synchronized void forget(Player p) {
        present.remove(p.getUniqueId());
    }

    public synchronized void stop(Player p) {
        silence(p, listening.remove(p.getUniqueId()));
    }

    private void silence(Player p, int[] st) {
        if (st == null || songs.isEmpty()) return;
        p.stopSound(songs.get(Math.min(st[0], songs.size() - 1)).audio(), SoundCategory.RECORDS);
    }

    /** Startet die Wahl des Spielers; index &gt;= 0 erzwingt diesen Song. */
    private void start(Player p, int index) {
        List<Integer> pl = playlist(p.getUniqueId());
        if (pl.isEmpty()) {
            silence(p, listening.remove(p.getUniqueId()));
            return;
        }
        int song = index >= 0 && pl.contains(index) ? index : pl.get((int) (Math.random() * pl.size()));
        silence(p, listening.put(p.getUniqueId(), new int[]{song, 0}));
        nowPlaying(p, song);
    }

    // =================================================================================================================
    // Fenster
    // =================================================================================================================

    /** /music und DJ Mia: Songauswahl (nur im Casino). */
    public void openMenu(Player p) {
        if (!inCasino(p.getLocation())) {
            CasinoText.error(p, "Musik aussuchen geht nur im Casino.");
            if (p.hasPermission("kuby.admin") && plugin.regions().find(CasinoService.REGION) == null) {
                CasinoText.send(p, "<gray>Es gibt noch keinen Casino-Bereich. Einrichten: <click:run_command:'/casino einrichten'><white>/casino einrichten</white></click>");
            }
            return;
        }
        UUID id = p.getUniqueId();
        String c = choiceOf(id);
        String cat = tab.getOrDefault(id, c.equals(ALL_SPECIAL) || (track(c) != null && SPECIAL.equals(track(c).category())) ? SPECIAL : KLASSIK);
        List<CasinoScreens.Song> list = new ArrayList<>();
        int ownK = 0, ownS = 0, allK = 0, allS = 0;
        for (Track t : songs) {
            boolean has = owns(id, t.id());
            if (KLASSIK.equals(t.category())) {
                allK++;
                if (has) ownK++;
            } else {
                allS++;
                if (has) ownS++;
            }
            if (!t.category().equals(cat)) continue;
            list.add(new CasinoScreens.Song(t.id(), t.name(), t.time(), has, how(id, t)));
        }
        int[] st = listening.get(id);
        String playing = st == null ? null : songs.get(st[0]).name();
        String time = st == null ? "" : songs.get(st[0]).time();
        CasinoScreens.MusicView view = new CasinoScreens.MusicView(list, c, cat, playing, time, ownK, allK, ownS, allS);
        UiDialog.show(p, CasinoScreens.music(view, new CasinoScreens.MusicActions() {
            @Override
            public Consumer<Player> choose(String c) {
                return pl -> {
                    CasinoMusic.this.choose(pl, c);
                    openMenu(pl);
                };
            }

            @Override
            public Consumer<Player> next() {
                return pl -> {
                    synchronized (CasinoMusic.this) {
                        int[] s = listening.get(pl.getUniqueId());
                        List<Integer> pls = playlist(pl.getUniqueId());
                        if (s != null && pls.size() > 1) {
                            silence(pl, s);
                            s[0] = pls.get((pls.indexOf(s[0]) + 1) % pls.size());
                            s[1] = 0;
                            nowPlaying(pl, s[0]);
                        }
                    }
                    openMenu(pl);
                };
            }

            @Override
            public Consumer<Player> tab(String category) {
                return pl -> {
                    tab.put(pl.getUniqueId(), category);
                    openMenu(pl);
                };
            }

            @Override
            public Consumer<Player> shop() {
                return pl -> plugin.chipShop().open(pl);
            }
        }));
    }

    /** Wie man einen gesperrten Song bekommt (Text für das Fenster). */
    private String how(UUID id, Track t) {
        String u = unlockOf(t);
        if (u.equals("frei")) return "";
        if (u.equals("drop")) return "Zufallsfund beim Spielen";
        if (u.startsWith("shop:")) {
            try {
                return "Chip-Shop · " + CasinoText.num(Long.parseLong(u.substring(5))) + " Chips";
            } catch (NumberFormatException e) {
                return "Chip-Shop";
            }
        }
        if (u.startsWith("quest:")) {
            int[] q = questProgress(id, u);
            if (q == null) return "Quest";
            String what = switch (u.split(":")[1]) {
                case "runden" -> "Runden spielen";
                case "siege" -> "Runden gewinnen";
                case "serie" -> "Siegesserie";
                default -> "Quest";
            };
            return "Quest: " + what + " " + q[0] + "/" + q[1];
        }
        return "";
    }

    private void choose(Player p, String c) {
        synchronized (this) {
            choice.put(p.getUniqueId(), c);
            dirty = true;
        }
        save();
        if (c.equals(OFF)) {
            silence(p, listening.remove(p.getUniqueId()));
            CasinoText.send(p, "Casino-Musik aus. Im Casino bleibt es ruhig.");
            return;
        }
        if (c.startsWith("alle:")) {
            CasinoText.send(p, "Casino-Musik: <white>alle " + (c.equals(ALL_SPECIAL) ? "Special" : "Klassik") + "-Songs</white> im Wechsel");
        } else {
            Track t = track(c);
            CasinoText.send(p, "Casino-Musik: <#F2DA90>" + (t == null ? c : t.name()) + "</#F2DA90> läuft in Dauerschleife");
        }
        if (inCasino(p.getLocation())) start(p, -1);
    }

    private synchronized void save() {
        dirty = false;
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : choice.entrySet()) out.set("wahl." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Set<String>> e : owned.entrySet()) out.set("songs." + e.getKey(), new ArrayList<>(e.getValue()));
        for (Map.Entry<UUID, int[]> e : stats.entrySet()) out.set("zaehler." + e.getKey(), List.of(e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-music.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (dirty) save();
    }
}
