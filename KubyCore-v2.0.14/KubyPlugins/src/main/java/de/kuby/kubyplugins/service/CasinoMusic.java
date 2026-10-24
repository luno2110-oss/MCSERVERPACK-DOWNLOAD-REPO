package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoScreens;
import de.kuby.kubyplugins.casino.CasinoText;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Casino-Musik wie im Clip: drei Notenblock-Songs (Brisk Casino Swing, Mid-Night Lounge, Late-night Lounge), die im
 * Casino für jeden Spieler einzeln laufen (Kategorie Jukebox/Notenblöcke). Wahl: alle Songs im Wechsel, ein Song in
 * Dauerschleife oder aus. /music oder DJ Mia öffnen die Auswahl.
 */
public final class CasinoMusic {

    private record Note(Sound sound, float pitch, float volume) {}

    private record Track(String name, int length, Note[][] notes) {
        String time() {
            int s = length / 20;
            return (s / 60) + ":" + String.format("%02d", s % 60);
        }
    }

    /** Wahl: alle Songs im Wechsel. */
    public static final int ALL = -1;
    /** Wahl: Musik aus. */
    public static final int OFF = -2;
    private static final String[] IDS = {"brisk", "midnight", "latenight"};

    private final KubyPlugins plugin;
    private final File file;
    private final List<Track> songs = new ArrayList<>();
    private final Map<UUID, Integer> choice = new HashMap<>();
    /** Wer gerade hört: Song-Index und Position (Tick). */
    private final Map<UUID, int[]> listening = new LinkedHashMap<>();
    /** Wer gerade im Casino steht (für die Begrüßung beim Betreten). */
    private final java.util.Set<UUID> present = new java.util.HashSet<>();

    public CasinoMusic(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-music.yml");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("wahl");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    choice.put(UUID.fromString(key), s.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (String id : IDS) {
            Track t = load(id);
            if (t != null) songs.add(t);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private Track load(String id) {
        try (InputStream in = plugin.getResource("casino/songs/" + id + ".txt")) {
            if (in == null) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String[] head = r.readLine().split("\\|");
            int length = Integer.parseInt(head[1].trim());
            List<List<Note>> byTick = new ArrayList<>(length);
            for (int i = 0; i < length; i++) byTick.add(new ArrayList<>(2));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split(" ");
                if (p.length < 4) continue;
                int tick = Integer.parseInt(p[0]);
                if (tick < 0 || tick >= length) continue;
                Sound sound = soundOf(p[1]);
                if (sound == null) continue;
                float pitch = (float) Math.pow(2, (Integer.parseInt(p[2]) - 12) / 12.0);
                byTick.get(tick).add(new Note(sound, pitch, Integer.parseInt(p[3]) / 100f));
            }
            Note[][] notes = new Note[length][];
            for (int i = 0; i < length; i++) notes[i] = byTick.get(i).toArray(new Note[0]);
            return new Track(head[0].trim(), length, notes);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Casino-Song " + id + " konnte nicht geladen werden: " + e.getMessage());
            return null;
        }
    }

    private static Sound soundOf(String instrument) {
        return switch (instrument) {
            case "harp" -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case "bass" -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case "basedrum" -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case "snare" -> Sound.BLOCK_NOTE_BLOCK_SNARE;
            case "hat" -> Sound.BLOCK_NOTE_BLOCK_HAT;
            case "guitar" -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case "flute" -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case "bell" -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case "chime" -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case "xylophone" -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case "iron_xylophone" -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case "cow_bell" -> Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case "didgeridoo" -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case "bit" -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case "banjo" -> Sound.BLOCK_NOTE_BLOCK_BANJO;
            case "pling" -> Sound.BLOCK_NOTE_BLOCK_PLING;
            default -> null;
        };
    }

    public boolean inCasino(Location l) {
        RegionService.Region r = plugin.regions().find(CasinoService.REGION);
        return r != null && r.contains(l);
    }

    public int choiceOf(UUID id) {
        int c = choice.getOrDefault(id, ALL);
        return c >= songs.size() ? ALL : c;
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
            for (Note n : t.notes()[st[1]]) p.playSound(p, n.sound(), SoundCategory.RECORDS, n.volume() * master, n.pitch());
            st[1]++;
            if (st[1] >= t.length()) {
                st[1] = 0;
                if (choiceOf(e.getKey()) == ALL) {
                    st[0] = (st[0] + 1) % songs.size();
                    nowPlaying(p, st[0]);
                }
            }
        }
    }

    private void nowPlaying(Player p, int index) {
        p.sendActionBar(Msg.parse("<#F2C94C>♪</#F2C94C> <#F2DA90>" + songs.get(index).name() + "</#F2DA90> <dark_gray>·</dark_gray> <gray>/music"));
    }

    /** Startet die Musik, wenn der Spieler im Casino steht, sonst stoppt sie. */
    public synchronized void update(Player p, Location at) {
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

    /** Beim Ausloggen: nächstes Betreten zählt wieder als neu. */
    public synchronized void forget(Player p) {
        present.remove(p.getUniqueId());
    }

    public synchronized void stop(Player p) {
        listening.remove(p.getUniqueId());
    }

    /** Startet die Wahl des Spielers; index &gt;= 0 erzwingt diesen Song (bei "Alle Songs"). */
    private void start(Player p, int index) {
        if (songs.isEmpty()) return;
        int c = choiceOf(p.getUniqueId());
        if (c == OFF) {
            listening.remove(p.getUniqueId());
            return;
        }
        int song = c >= 0 ? c : index >= 0 ? index : (int) (Math.random() * songs.size());
        listening.put(p.getUniqueId(), new int[]{song, 0});
        nowPlaying(p, song);
    }

    // =================================================================================================================
    // Menü
    // =================================================================================================================

    /** /music und DJ Mia: Songauswahl (nur im Casino). */
    public void openMenu(Player p) {
        if (!inCasino(p.getLocation())) {
            CasinoText.error(p, "Musik aussuchen geht nur im Casino.");
            return;
        }
        List<CasinoScreens.Song> list = new ArrayList<>();
        for (Track t : songs) list.add(new CasinoScreens.Song(t.name(), t.time()));
        int[] st = listening.get(p.getUniqueId());
        int playing = st == null ? -1 : st[0];
        String time = playing < 0 ? "" : songs.get(playing).time();
        CasinoScreens.MusicView view = new CasinoScreens.MusicView(list, choiceOf(p.getUniqueId()), playing, time);
        UiDialog.show(p, CasinoScreens.music(view, new CasinoScreens.MusicActions() {
            @Override
            public Consumer<Player> choose(int c) {
                return pl -> {
                    CasinoMusic.this.choose(pl, c);
                    openMenu(pl);
                };
            }

            @Override
            public Consumer<Player> next() {
                return pl -> {
                    int[] s = listening.get(pl.getUniqueId());
                    if (s != null && !songs.isEmpty()) {
                        s[0] = (s[0] + 1) % songs.size();
                        s[1] = 0;
                        nowPlaying(pl, s[0]);
                    }
                    openMenu(pl);
                };
            }
        }));
    }

    private synchronized void choose(Player p, int c) {
        choice.put(p.getUniqueId(), c);
        save();
        if (c == OFF) {
            listening.remove(p.getUniqueId());
            CasinoText.send(p, "Casino-Musik aus. Im Casino bleibt es ruhig.");
            return;
        }
        if (c == ALL) {
            CasinoText.send(p, "Casino-Musik: <white>alle Songs</white> im Wechsel");
        } else {
            CasinoText.send(p, "Casino-Musik: <#F2DA90>" + songs.get(c).name() + "</#F2DA90> läuft in Dauerschleife");
        }
        if (inCasino(p.getLocation())) {
            int[] st = listening.get(p.getUniqueId());
            // bei "Alle Songs" den laufenden Song weiterspielen
            if (c == ALL && st != null) return;
            start(p, -1);
        }
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : choice.entrySet()) out.set("wahl." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-music.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
