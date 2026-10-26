package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spielzeit: die gesamte Online-Zeit (für Statistik und Support) und die aktive Zeit je Modus. Wer sich
 * playtime.idle-minutes lang nicht bewegt, zählt nicht. Mit der aktiven Zeit schaltet man Stufen frei
 * (playtime.stages.&lt;modus&gt;: "Stunden;Name;Farbe;coins:1000,tokens:1,key:vote:1,jewels:2,xp:500,tag:id,cmd:Befehl",
 * Name und Farbe dürfen fehlen: "Stunden;Belohnungen" oder "Stunden;Name;Belohnungen").
 * Abholen nur mit verlinktem Discord (playtime.require-discord, gilt nur, wenn DiscordSRV läuft).
 */
public final class PlaytimeService implements Listener {

    /** Eine Belohnung: coins, tokens, jewels, xp, key (value = Schlüsselart), tag (value = Titel), cmd (value = Befehl). */
    public record Prize(String kind, String value, long amount) {}

    /** Farben der Stufen im Dialog (Config-Namen); ohne Angabe je 8 Stufen eine Farbe. */
    public static final List<String> STYLES = List.of("grau", "tuerkis", "gold", "orange", "blau", "pink", "lila", "rot",
            "bernstein");

    /** Eine Stufe: Nummer (ab 1), nötige aktive Minuten, Name (leer: "Stufe N"), Farbe (STYLES), Belohnungen. */
    public record Stage(int number, long minutes, String name, String style, List<Prize> prizes) {
        public long hours() {
            return minutes / 60;
        }

        /** Name zum Anzeigen. */
        public String title() {
            return name.isEmpty() ? "Stufe " + number : name;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Long> minutes = new HashMap<>();
    /** Aktive Minuten je modus|uuid. */
    private final Map<String, Long> active = new HashMap<>();
    /** Abgeholte Stufen (Minuten der Stufe) je modus|uuid. */
    private final Map<String, Set<Long>> claimed = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Integer> idle = new HashMap<>();

    /** Die Survival-Stufen bis Version 12.8 (15 Stufen ohne Namen): werden beim Start durch die neuen ersetzt. */
    private static final List<String> OLD_SURVIVAL = List.of("1;coins:500,tokens:1", "3;coins:1000,tokens:1,key:vote:1",
            "6;coins:1500,tokens:2", "10;coins:2000,tokens:2,jewels:2", "15;coins:2500,tokens:2,key:selten:1",
            "24;coins:3000,tokens:3", "36;coins:4000,tokens:3,jewels:3", "50;coins:5000,tokens:4,key:selten:1",
            "75;coins:6000,tokens:4,jewels:5", "100;coins:8000,tokens:5,key:episch:1", "150;coins:10000,tokens:5,jewels:5",
            "200;coins:12500,tokens:6,key:episch:1", "300;coins:15000,tokens:8,jewels:10",
            "400;coins:20000,tokens:10,key:legendaer:1", "500;coins:25000,tokens:12,key:legendaer:1,jewels:15");

    public PlaytimeService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playtime.yml");
        upgradeConfig();
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    /**
     * Ältere config.yml: die unveränderten alten Survival-Stufen durch die 57 benannten ersetzen, den Kosmetik-Schlüssel
     * (Art, Kiste) und den Kurznamen „Epic-Key“ ergänzen. Eigene Stufen und Kisten bleiben, wie sie sind.
     */
    private void upgradeConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        java.io.InputStream in = plugin.getResource("config.yml");
        if (in == null) return;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        boolean changed = false;
        if (cfg.getStringList("playtime.stages.survival").equals(OLD_SURVIVAL)) {
            cfg.set("playtime.stages.survival", def.getStringList("playtime.stages.survival"));
            changed = true;
        }
        if (cfg.isList("keys.types")) {
            List<String> types = new ArrayList<>(cfg.getStringList("keys.types"));
            int epic = types.indexOf("episch;Episch;#C77DFF");
            boolean typesChanged = epic >= 0;
            if (epic >= 0) types.set(epic, "episch;Episch;#C77DFF;Epic-Key");
            if (types.stream().noneMatch(t -> t.toLowerCase(Locale.ROOT).startsWith("kosmetik;"))) {
                types.add("kosmetik;Kosmetik;#FC99DA");
                typesChanged = true;
            }
            if (typesChanged) {
                cfg.set("keys.types", types);
                changed = true;
            }
        }
        if (cfg.isConfigurationSection("crates.survival") && !cfg.isConfigurationSection("crates.survival.kosmetik")
                && def.isConfigurationSection("crates.survival.kosmetik")) {
            ConfigurationSection src = def.getConfigurationSection("crates.survival.kosmetik");
            for (String key : src.getKeys(true)) {
                if (!src.isConfigurationSection(key)) cfg.set("crates.survival.kosmetik." + key, src.get(key));
            }
            changed = true;
        }
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("Spielzeit: config.yml auf die neuen Stufen und den Kosmetik-Schlüssel gebracht.");
        }
    }

    private static String k(String mode, UUID id) {
        return mode + "|" + id;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Location now = p.getLocation();
            Location before = lastPos.put(id, now);
            boolean moved = before == null || before.getWorld() != now.getWorld() || before.distanceSquared(now) > 4
                    || Math.abs(before.getYaw() - now.getYaw()) > 10;
            int idleMin = moved ? 0 : idle.getOrDefault(id, 0) + 1;
            idle.put(id, idleMin);
            String mode = plugin.modes().modeOf(id);
            long reached;
            synchronized (this) {
                minutes.merge(id, 1L, Long::sum);
                if (idleMin >= plugin.getConfig().getInt("playtime.idle-minutes", 5)) continue;
                reached = active.merge(k(mode, id), 1L, Long::sum);
            }
            for (Stage s : stages(mode)) {
                if (s.minutes() == reached) {
                    Msg.send(p, "<#F2C84A><b>SPIELZEIT</b> <dark_gray>▶ <gray>Stufe <white>" + s.number()
                            + (s.name().isEmpty() ? "" : " „" + s.name() + "“") + "</white> erreicht ("
                            + s.hours() + (s.hours() == 1 ? " Stunde" : " Stunden") + "). "
                            + "<click:run_command:'/playtime'><#F2C84A>[Abholen]</click>");
                    Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                }
            }
        }
        save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPos.remove(e.getPlayer().getUniqueId());
        idle.remove(e.getPlayer().getUniqueId());
    }

    /** Gesamte Online-Zeit in Minuten (alle Modi, auch untätig). */
    public synchronized long minutesOf(UUID id) {
        return minutes.getOrDefault(id, 0L);
    }

    /** Aktive Minuten in einem Modus (zählt für die Stufen). */
    public synchronized long activeMinutes(UUID id, String mode) {
        return active.getOrDefault(k(mode, id), 0L);
    }

    /** Stufen eines Modus aus der Config, nach Stunden sortiert. */
    public List<Stage> stages(String mode) {
        List<Stage> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("playtime.stages." + mode)) {
            String[] p = raw.split(";", 4);
            if (p.length < 2) continue;
            try {
                double hours = Double.parseDouble(p[0].trim().replace(',', '.'));
                String name = p.length > 2 ? p[1].trim() : "";
                String style = p.length > 3 ? p[2].trim().toLowerCase(Locale.ROOT) : "";
                List<Prize> prizes = new ArrayList<>();
                for (String part : p[p.length - 1].split(",")) {
                    Prize pr = parse(part.trim());
                    if (pr != null) prizes.add(pr);
                }
                list.add(new Stage(0, Math.round(hours * 60), name, style, prizes));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ungültige Spielzeit-Stufe: " + raw);
            }
        }
        list.sort((a, b) -> Long.compare(a.minutes(), b.minutes()));
        List<Stage> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Stage s = list.get(i);
            String style = STYLES.contains(s.style()) ? s.style() : STYLES.get(Math.min(i / 8, 7));
            out.add(new Stage(i + 1, s.minutes(), s.name(), style, s.prizes()));
        }
        return out;
    }

    private Prize parse(String s) {
        int c = s.indexOf(':');
        if (c <= 0) return null;
        String kind = s.substring(0, c).toLowerCase(Locale.ROOT);
        String rest = s.substring(c + 1);
        try {
            return switch (kind) {
                case "coins", "tokens", "jewels", "xp" -> new Prize(kind, "", Long.parseLong(rest.trim()));
                case "key" -> {
                    String[] kp = rest.split(":");
                    yield new Prize(kind, kp[0].trim().toLowerCase(Locale.ROOT), kp.length > 1 ? Long.parseLong(kp[1].trim()) : 1);
                }
                case "tag", "cmd" -> new Prize(kind, rest.trim(), 1);
                default -> null;
            };
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ungültige Spielzeit-Belohnung: " + s);
            return null;
        }
    }

    public synchronized boolean isClaimed(UUID id, String mode, Stage s) {
        return claimed.getOrDefault(k(mode, id), Set.of()).contains(s.minutes());
    }

    /** Fertige, noch nicht abgeholte Stufen im aktuellen Modus. */
    public List<Stage> ready(UUID id, String mode) {
        long act = activeMinutes(id, mode);
        List<Stage> out = new ArrayList<>();
        for (Stage s : stages(mode)) if (act >= s.minutes() && !isClaimed(id, mode, s)) out.add(s);
        return out;
    }

    /** Warum dieser Spieler gerade nichts abholen darf, oder null. */
    public String blocked(Player p) {
        if (plugin.getConfig().getBoolean("playtime.require-discord", true) && plugin.discord().available()
                && !plugin.homes().verified(p.getUniqueId())) {
            return "Zum Abholen muss dein Discord verlinkt sein. /verify zeigt dir, wie es geht.";
        }
        return null;
    }

    /** Eine Stufe abholen (im aktuellen Modus). Null bei Erfolg. */
    public String claim(Player p, Stage s) {
        String why = blocked(p);
        if (why != null) return why;
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        synchronized (this) {
            if (activeMinutes(id, mode) < s.minutes()) return "Diese Stufe hast du noch nicht erreicht.";
            if (isClaimed(id, mode, s)) return "Diese Stufe hast du schon abgeholt.";
            claimed.computeIfAbsent(k(mode, id), x -> new HashSet<>()).add(s.minutes());
        }
        for (Prize pr : s.prizes()) give(p, mode, pr);
        plugin.stats().save();
        save();
        return null;
    }

    /** Alle fertigen Stufen abholen. Gibt die Anzahl zurück (0 = nichts da) oder -1, wenn das Abholen gesperrt ist. */
    public int claimAll(Player p) {
        if (blocked(p) != null) return -1;
        int n = 0;
        for (Stage s : ready(p.getUniqueId(), plugin.modes().modeOf(p.getUniqueId()))) {
            if (claim(p, s) == null) n++;
        }
        return n;
    }

    private void give(Player p, String mode, Prize pr) {
        UUID id = p.getUniqueId();
        switch (pr.kind()) {
            case "coins" -> plugin.modes().depositIn(id, mode, pr.amount());
            case "jewels" -> plugin.jewels().add(id, pr.amount());
            case "xp" -> plugin.stats().get(id).xp += pr.amount();
            case "tokens" -> {
                if (!plugin.give("tokens", id, pr.amount())) plugin.getLogger().warning("Spielzeit: Tokens ohne Survival-Addon übersprungen");
            }
            case "key" -> plugin.keys().give(id, mode, pr.value(), (int) pr.amount());
            case "tag" -> plugin.tags().grant(id, pr.value());
            case "cmd" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    pr.value().replace("%player%", p.getName()));
            default -> {
            }
        }
    }

    /** Kurzer Text einer Belohnung, z. B. "2.500 Coins" oder "1 Seltener Schlüssel". */
    public String describe(Prize pr) {
        return switch (pr.kind()) {
            case "coins" -> Msg.coins(pr.amount()) + " Coins";
            case "tokens" -> pr.amount() + (pr.amount() == 1 ? " Token" : " Tokens");
            case "jewels" -> pr.amount() + (pr.amount() == 1 ? " Juwel" : " Juwelen");
            case "xp" -> pr.amount() + " XP";
            case "key" -> {
                KeyService.KeyType t = plugin.keys().type(pr.value());
                yield pr.amount() + " " + (t == null ? pr.value() : t.full());
            }
            case "tag" -> {
                String d = plugin.tags().displayOf(pr.value());
                yield "Titel " + (d == null ? pr.value()
                        : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.parse(d)));
            }
            default -> "Überraschung";
        };
    }

    private synchronized void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection total = yml.getConfigurationSection("gesamt");
        if (total == null) {
            // altes Format: <uuid>.minutes
            for (String key : yml.getKeys(false)) {
                try {
                    minutes.put(UUID.fromString(key), yml.getLong(key + ".minutes"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } else {
            for (String key : total.getKeys(false)) {
                try {
                    minutes.put(UUID.fromString(key), total.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection act = yml.getConfigurationSection("aktiv");
        if (act != null) for (String key : act.getKeys(false)) active.put(key.replace('_', '|'), act.getLong(key));
        ConfigurationSection cl = yml.getConfigurationSection("abgeholt");
        if (cl != null) {
            for (String key : cl.getKeys(false)) {
                Set<Long> set = new HashSet<>();
                for (Object o : cl.getList(key, List.of())) if (o instanceof Number n) set.add(n.longValue());
                claimed.put(key.replace('_', '|'), set);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        minutes.forEach((id, m) -> out.set("gesamt." + id, m));
        active.forEach((key, m) -> out.set("aktiv." + key.replace('|', '_'), m));
        claimed.forEach((key, set) -> {
            if (!set.isEmpty()) out.set("abgeholt." + key.replace('|', '_'), new ArrayList<>(set));
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("playtime.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
