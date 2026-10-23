package de.kp.jumprun.holo;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.stats.StatsManager;
import de.kp.jumprun.stats.StatsManager.Record;
import de.kp.jumprun.util.Text;
import de.kp.jumprun.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * Verwaltet alle Bestenlisten-Hologramme: Speichern, Animation, Rangliste und persönliche Zeile.
 */
public final class HoloManager {

    private static final long TICK = 2L;
    /** Ticks zwischen zwei Zeilen beim Verschwinden und Erscheinen */
    private static final int ROW_STEP = 2;
    private static final int BAR_LENGTH = 20;
    private static final double RANGE = 24.0;

    private static final String COLOR_FIRST = "#ff4d4d";
    private static final String COLOR_SECOND = "#ffe14d";
    private static final String COLOR_THIRD = "#ffb300";
    private static final String COLOR_OTHER = "#9aa3ad";
    private static final String COLOR_OWN = "#ffe14d";

    private final KPJumpRun plugin;
    private final StatsManager stats;
    private final File file;
    private final Map<String, Holo> holos = new LinkedHashMap<>();

    public HoloManager(KPJumpRun plugin, StatsManager stats) {
        this.plugin = plugin;
        this.stats = stats;
        this.file = new File(plugin.getDataFolder(), "holos.yml");
    }

    // ---------- Speichern & Laden ----------

    public void load() {
        holos.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("holos");
        if (root == null) {
            return;
        }

        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Location location = s.getObject("location", Location.class);
            if (location == null) {
                continue;
            }

            Holo holo = new Holo(name, s.getString("arena", "default"), location);
            holo.scale(s.getDouble("scale", 1.0));
            holo.seconds(s.getInt("seconds", 10));
            String board = s.getString("board");
            if (board != null) {
                holo.boardId = UUID.fromString(board);
            }
            holos.put(name, holo);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Holo holo : holos.values()) {
            String base = "holos." + holo.name() + ".";
            yml.set(base + "location", holo.location());
            yml.set(base + "arena", holo.arena());
            yml.set(base + "scale", holo.scale());
            yml.set(base + "seconds", holo.seconds());
            yml.set(base + "board", holo.boardId == null ? null : holo.boardId.toString());
        }

        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("holos.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------- Verwaltung ----------

    public Holo get(String name) {
        return holos.get(name);
    }

    public Map<String, Holo> all() {
        return holos;
    }

    public Holo create(String name, String arena, Location location) {
        Holo holo = new Holo(name, arena, location);
        holos.put(name, holo);
        spawnBoard(holo);
        save();
        return holo;
    }

    public void delete(String name) {
        Holo holo = holos.remove(name);
        if (holo == null) {
            return;
        }
        removeEntity(holo.boardId);
        clearPersonal(holo);
        save();
    }

    public void move(Holo holo, Location location) {
        removeEntity(holo.boardId);
        clearPersonal(holo);
        holo.location(location);
        spawnBoard(holo);
        save();
    }

    public void rebuild(Holo holo) {
        removeEntity(holo.boardId);
        clearPersonal(holo);
        spawnBoard(holo);
        save();
    }

    public void startTicker() {
        // Ein Fehler im Tick würde Bukkit sonst stillschweigend die ganze Aufgabe beenden
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tick();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Fehler bei der Bestenliste (Hologramme)", t);
            }
        }, 0L, TICK);
    }

    // ---------- Tick ----------

    private void tick() {
        for (Holo holo : holos.values()) {
            if (holo.location().getWorld() == null) {
                continue;
            }
            // Board nach Neustart oder beim Laden der Chunk wieder herstellen
            if (holo.location().isChunkLoaded() && findBoard(holo) == null) {
                spawnBoard(holo);
            }
            advance(holo);
            renderBoard(holo);
            updatePersonal(holo);
        }
    }

    private void advance(Holo holo) {
        int limit = visibleLimit();
        int total = Math.min(rows(holo.category, holo.arena()).size(), limit);
        int showTicks = holo.seconds() * 20;

        switch (holo.phase) {
            case SHOW -> {
                holo.visibleRows = total;
                holo.phaseTicks += (int) TICK;
                holo.barFraction = 1.0 - Math.min(1.0, holo.phaseTicks / (double) showTicks);
                if (holo.phaseTicks >= showTicks) {
                    holo.phase = Holo.Phase.HIDE;
                    holo.phaseTicks = 0;
                    holo.stepTicks = 0;
                    holo.barFraction = 0.0;
                }
            }
            case HIDE -> {
                holo.stepTicks += (int) TICK;
                if (holo.stepTicks >= ROW_STEP) {
                    holo.stepTicks = 0;
                    if (holo.visibleRows > 0) {
                        holo.visibleRows--;
                        rowSound(holo, holo.visibleRows);
                    } else {
                        holo.category = holo.category.next();
                        holo.phase = Holo.Phase.REVEAL;
                        holo.phaseTicks = 0;
                        pageSound(holo);
                    }
                }
            }
            case REVEAL -> {
                int newTotal = Math.min(rows(holo.category, holo.arena()).size(), limit);
                if (newTotal == 0) {
                    holo.visibleRows = 0;
                    holo.phase = Holo.Phase.SHOW;
                    holo.phaseTicks = 0;
                    return;
                }
                holo.stepTicks += (int) TICK;
                if (holo.stepTicks >= ROW_STEP) {
                    holo.stepTicks = 0;
                    holo.visibleRows++;
                    rowSound(holo, holo.visibleRows - 1);
                    if (holo.visibleRows >= newTotal) {
                        holo.visibleRows = newTotal;
                        holo.phase = Holo.Phase.SHOW;
                        holo.phaseTicks = 0;
                    }
                }
            }
        }
    }

    // ---------- Rendering ----------

    private void renderBoard(Holo holo) {
        TextDisplay board = findBoard(holo);
        if (board == null) {
            return;
        }

        List<Row> rows = rows(holo.category, holo.arena());
        int limit = visibleLimit();
        String accent = holo.category.color();

        List<String> lines = new ArrayList<>();
        lines.add("<color:" + accent + ">" + Text.smallCaps(holo.category.title()) + "</color>");
        lines.add(bar(accent, holo.barFraction));
        lines.add("");

        if (rows.isEmpty()) {
            lines.add("<gray>Noch keine Einträge</gray>");
        } else {
            for (int i = 0; i < limit; i++) {
                if (i < holo.visibleRows && i < rows.size()) {
                    lines.add(rowLine(i + 1, rows.get(i), accent));
                } else {
                    lines.add("");
                }
            }
        }

        board.text(Text.mm(String.join("<newline>", lines)));
    }

    private String bar(String accent, double fraction) {
        int filled = (int) Math.round(fraction * BAR_LENGTH);
        return "<color:" + accent + ">" + "▰".repeat(filled) + "</color>"
                + "<dark_gray>" + "▱".repeat(BAR_LENGTH - filled) + "</dark_gray>";
    }

    private String rowLine(int rank, Row row, String accent) {
        String color = switch (rank) {
            case 1 -> COLOR_FIRST;
            case 2 -> COLOR_SECOND;
            case 3 -> COLOR_THIRD;
            default -> COLOR_OTHER;
        };
        return "<color:" + color + ">#" + rank + " " + row.name() + "</color>"
                + " <dark_gray>·</dark_gray> <color:" + accent + ">" + row.value() + "</color>";
    }

    /** Persönliche Zeile unter dem Board, nur für den Spieler selbst sichtbar. */
    private void updatePersonal(Holo holo) {
        World world = holo.location().getWorld();
        if (world == null) {
            return;
        }

        List<Row> rows = rows(holo.category, holo.arena());
        int limit = visibleLimit();
        Set<UUID> needed = new HashSet<>();

        for (Player player : world.getNearbyPlayers(holo.location(), RANGE)) {
            int index = indexOf(rows, player.getUniqueId());
            if (index < limit) {
                continue;
            }
            needed.add(player.getUniqueId());

            Holo.Personal personal = holo.personal.computeIfAbsent(player.getUniqueId(),
                    id -> spawnPersonal(holo));

            TextDisplay display = findPersonal(personal);
            if (display != null) {
                Row row = rows.get(index);
                display.text(Text.mm("<color:" + COLOR_OWN + ">#" + (index + 1) + " " + row.name()
                        + " <dark_gray>·</dark_gray> <color:" + holo.category.color() + ">" + row.value()
                        + "</color></color>"));
                hideFromOthers(player.getUniqueId(), personal, display);
            }
        }

        // Anzeigen für Spieler entfernen, die nicht mehr in Reichweite sind oder nicht mehr unten stehen
        Iterator<Map.Entry<UUID, Holo.Personal>> it = holo.personal.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Holo.Personal> entry = it.next();
            if (!needed.contains(entry.getKey())) {
                removeEntity(entry.getValue().displayId);
                it.remove();
            }
        }
    }

    /** Blendet die persönliche Zeile vor allen anderen Spielern aus. */
    private void hideFromOthers(UUID owner, Holo.Personal personal, TextDisplay display) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            UUID id = other.getUniqueId();
            if (id.equals(owner) || personal.hiddenFrom.contains(id)) {
                continue;
            }
            other.hideEntity(plugin, display);
            personal.hiddenFrom.add(id);
        }
    }

    // ---------- Sound ----------

    private void rowSound(Holo holo, int index) {
        playNearby(holo, Sound.UI_BUTTON_CLICK, 0.5f + index * 0.05f);
    }

    private void pageSound(Holo holo) {
        playNearby(holo, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f);
    }

    private void playNearby(Holo holo, Sound sound, float pitch) {
        World world = holo.location().getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getNearbyPlayers(holo.location(), RANGE)) {
            player.playSound(holo.location(), sound, SoundCategory.BLOCKS, 0.6f, pitch);
        }
    }

    // ---------- Daten ----------

    /** Alle Zeilen einer Kategorie, bestes Ergebnis zuerst. */
    public List<Row> rows(Category category, String arena) {
        List<Row> out = new ArrayList<>();
        switch (category) {
            case BEST_TIME -> {
                for (Map.Entry<UUID, Record> entry : stats.ranking(arena)) {
                    out.add(new Row(entry.getKey(), displayName(entry.getValue()),
                            TimeFormat.format(entry.getValue().bestMillis())));
                }
            }
            case COMPLETIONS -> out.addAll(countRows(arena, Record::completions));
            case FAILS -> out.addAll(countRows(arena, Record::fails));
        }
        return out;
    }

    private List<Row> countRows(String arena, ToIntFunction<Record> value) {
        List<Map.Entry<UUID, Record>> list = new ArrayList<>();
        for (Map.Entry<UUID, Record> entry : stats.records(arena).entrySet()) {
            if (value.applyAsInt(entry.getValue()) > 0) {
                list.add(entry);
            }
        }
        list.sort((a, b) -> Integer.compare(value.applyAsInt(b.getValue()), value.applyAsInt(a.getValue())));

        List<Row> out = new ArrayList<>();
        for (Map.Entry<UUID, Record> entry : list) {
            out.add(new Row(entry.getKey(), displayName(entry.getValue()),
                    String.valueOf(value.applyAsInt(entry.getValue()))));
        }
        return out;
    }

    private String displayName(Record record) {
        return record.name().isEmpty() ? "Unbekannt" : record.name();
    }

    private int indexOf(List<Row> rows, UUID uuid) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).uuid().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    private int visibleLimit() {
        return plugin.getConfig().getInt("leaderboard.visible-rows", 10);
    }

    // ---------- Entities ----------

    private void spawnBoard(Holo holo) {
        World world = holo.location().getWorld();
        if (world == null) {
            return;
        }
        TextDisplay board = world.spawn(holo.location(), TextDisplay.class, d -> configure(d, holo.scale()));
        holo.boardId = board.getUniqueId();
    }

    private Holo.Personal spawnPersonal(Holo holo) {
        World world = holo.location().getWorld();
        Location at = holo.location().clone().add(0, -0.5 * holo.scale(), 0);
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> configure(d, holo.scale()));
        Holo.Personal personal = new Holo.Personal(display.getUniqueId());
        return personal;
    }

    private void configure(TextDisplay display, double scale) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(300);
        display.setBackgroundColor(Color.fromARGB(200, 8, 12, 18));
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setPersistent(true);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f((float) scale),
                new Quaternionf()));
    }

    private TextDisplay findBoard(Holo holo) {
        return find(holo.boardId);
    }

    private TextDisplay findPersonal(Holo.Personal personal) {
        return find(personal.displayId);
    }

    private TextDisplay find(UUID id) {
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof TextDisplay display ? display : null;
    }

    private void removeEntity(UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private void clearPersonal(Holo holo) {
        for (Holo.Personal personal : holo.personal.values()) {
            removeEntity(personal.displayId);
        }
        holo.personal.clear();
    }
}
