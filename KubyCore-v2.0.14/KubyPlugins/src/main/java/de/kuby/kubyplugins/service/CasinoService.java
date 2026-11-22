package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Casino-Grundlagen: Chips als eigene Währung, Umtausch am Kassentisch, Jackpot-Anzeige.
 * Nur im Survival. Der Casino-Bereich ist eine Region mit der ID "casino" (Schutz über das Regionssystem).
 */
public final class CasinoService {

    public static final String REGION = "casino";
    private static final String SURVIVAL = "survival";

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<UUID, Long> chips = new HashMap<>();
    private long jackpot;
    private String kasse;
    private String tisch;
    /** Spawnpunkt für /casino (Welt;x;y;z;yaw;pitch), gesetzt mit /casino einrichten. */
    private String spawn;

    public CasinoService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino.yml");
        this.tag = new NamespacedKey(plugin, "kp_casino_jackpot");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = yml.getConfigurationSection("chips");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    chips.put(UUID.fromString(key), s.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        jackpot = yml.getLong("jackpot", plugin.getConfig().getLong("casino.jackpot-start", 10000));
        kasse = yml.getString("kasse");
        tisch = yml.getString("tisch");
        spawn = yml.getString("spawn");
        migrateChipUnit(yml);
    }

    /**
     * Früher kostete ein Chip casino.chip-price Coins (Standard 100), jetzt wie im Referenz-Clip 1 Coin. Alte Stände,
     * Jackpot und Poker-Treuhand werden einmalig auf den neuen Wert umgerechnet (casino.yml merkt sich chip-unit: 1).
     */
    private void migrateChipUnit(YamlConfiguration yml) {
        long unit = yml.getLong("chip-unit", file.exists() ? plugin.getConfig().getLong("casino.chip-price", 100) : 1);
        if (unit <= 1) {
            if (!yml.contains("chip-unit")) save();
            return;
        }
        chips.replaceAll((id, v) -> v * unit);
        jackpot *= unit;
        File pokerFile = new File(plugin.getDataFolder(), "poker.yml");
        if (pokerFile.exists()) {
            YamlConfiguration poker = YamlConfiguration.loadConfiguration(pokerFile);
            ConfigurationSection escrow = poker.getConfigurationSection("escrow");
            if (escrow != null) {
                for (String key : escrow.getKeys(false)) escrow.set(key, escrow.getLong(key) * unit);
                try {
                    poker.save(pokerFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("poker.yml konnte nicht umgerechnet werden: " + e.getMessage());
                }
            }
        }
        // Einsätze der Spiele in der Config auf den neuen Chip-Wert umrechnen
        for (String path : new String[]{"casino.blackjack.bet", "casino.roulette.max-bet", "casino.slots.bet", "casino.crash.bet"}) {
            if (plugin.getConfig().contains(path)) plugin.getConfig().set(path, plugin.getConfig().getLong(path) * unit);
        }
        plugin.getConfig().set("casino.chip-price", null);
        plugin.saveConfig();
        plugin.getLogger().info("Casino: Chips auf 1 Coin je Chip umgerechnet (vorher " + unit + " Coins je Chip).");
        save();
    }

    /** Ein Chip ist einen Coin wert (wie im Referenz-Clip). */
    public static final long CHIP_VALUE = 1;

    public synchronized long chips(UUID id) {
        return chips.getOrDefault(id, 0L);
    }

    public synchronized long jackpot() {
        return jackpot;
    }

    public synchronized void addJackpot(long amount) {
        jackpot += amount;
        save();
        updateDisplay();
    }

    /** Zahlt den Jackpot aus: gibt den Betrag zurück und setzt ihn auf den Startwert. */
    public synchronized long claimJackpot() {
        long won = jackpot;
        jackpot = plugin.getConfig().getLong("casino.jackpot-start", 10000);
        save();
        updateDisplay();
        return won;
    }

    /** Prüft nur den Modus (Survival). Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String checkMode(Player p) {
        return SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId())) ? null : "Das Casino gibt es nur in Survival.";
    }

    /** Prüft Modus und Kassentisch. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String checkUse(Player p) {
        if (!SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))) {
            return "Das Casino gibt es nur in Survival.";
        }
        if (!nearKasse(p.getLocation())) {
            return "Geh zum Kassentisch, um Chips zu tauschen.";
        }
        return null;
    }

    /** Null, wenn das Casino offen ist, sonst die Meldung für den Spieler. Verkaufen geht auch bei geschlossenem Casino. */
    public String closedMessage() {
        return plugin.getConfig().getBoolean("casino.open", true) ? null : "Das Casino ist gerade geschlossen.";
    }

    public boolean hasKasse() {
        return kasseLocation() != null;
    }

    /** Kauft Chips (1 Chip = 1 Coin). Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String buy(Player p, long count) {
        String closed = closedMessage();
        if (closed != null) return closed;
        // Chips sind Survival-Geld: Tausch nur in Survival (sonst Coins zwischen den Modi verschieben)
        String mode = checkMode(p);
        if (mode != null) return mode;
        if (count <= 0) return "Die Anzahl muss größer als 0 sein.";
        long cost = count * CHIP_VALUE;
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
            return "So viele Coins hast du nicht.";
        }
        chips.merge(p.getUniqueId(), count, Long::sum);
        save();
        plugin.casinoLog().exchange(p.getUniqueId(), count, 0);
        return null;
    }

    /** Verkauft Chips. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String sell(Player p, long count) {
        String mode = checkMode(p);
        if (mode != null) return mode;
        if (count <= 0) return "Die Anzahl muss größer als 0 sein.";
        long have = chips(p.getUniqueId());
        if (have < count) return "So viele Chips hast du nicht.";
        chips.put(p.getUniqueId(), have - count);
        long tax = tax(p, count);
        plugin.modes().depositIn(p.getUniqueId(), SURVIVAL, count * CHIP_VALUE - tax);
        save();
        plugin.casinoLog().exchange(p.getUniqueId(), -count, tax);
        return null;
    }

    /** Steuer in Prozent beim Auszahlen (Farmpass: steuerfrei). */
    public int taxPercent(Player p) {
        return plugin.hasPerk("farmpass", p.getUniqueId()) ? 0 : plugin.getConfig().getInt("casino.cashout-tax-percent", 5);
    }

    /** Steuer in Coins für eine Auszahlung (aufgerundet, ganze Coins). */
    public long tax(Player p, long count) {
        long gross = count * CHIP_VALUE;
        return (gross * taxPercent(p) + 99) / 100;
    }

    /** Preis eines Chips in Coins (fest 1). */
    public long price() {
        return CHIP_VALUE;
    }

    /** Nimmt Chips weg, etwa für den Einsatz. False, wenn nicht genug da ist. */
    public synchronized boolean takeChips(UUID id, long count) {
        long have = chips(id);
        if (count <= 0 || have < count) return false;
        chips.put(id, have - count);
        save();
        if (plugin.jackpotDraw() != null) plugin.jackpotDraw().join(id);
        return true;
    }

    /** Gibt Chips zurück, etwa eine Auszahlung. */
    public synchronized void giveChips(UUID id, long count) {
        if (count <= 0) return;
        chips.merge(id, count, Long::sum);
        save();
    }

    /** Setzt den Blackjack-Tisch auf den angegebenen Block. */
    public synchronized void setTable(Location block) {
        tisch = format(block);
        save();
    }

    /** Position des Blackjack-Tischs (Block-Mitte am Boden), null wenn nicht gesetzt oder die Welt fehlt. */
    public Location tableLocation() {
        return parse(tisch);
    }

    /** Setzt den Kassentisch auf den angegebenen Block und zeigt dort den Jackpot an. */
    public synchronized void setKasse(Location block) {
        kasse = format(block);
        save();
        updateDisplay();
    }

    /** Teleportiert den Spieler mit Wartezeit zum Kassentisch. Nur in Survival. */
    /** Bedrock-Spieler sind vorerst ausgesperrt (Grafiken fehlen dort noch); abschaltbar mit casino.bedrock-gesperrt: false. */
    public boolean bedrockBlocked(Player p) {
        return plugin.getConfig().getBoolean("casino.bedrock-gesperrt", true) && de.kuby.kubyplugins.bedrock.Bedrock.is(p);
    }

    public static final String BEDROCK_MSG = "<red>Das Casino gibt es vorerst nur in der Java-Edition. <gray>Auf Bedrock fehlen dort noch Grafiken – wir arbeiten daran.";

    private final java.util.Map<java.util.UUID, Long> bedrockNoticeAt = new java.util.HashMap<>();

    /**
     * Bedrock-Spieler wollen ins Casino (v2.05, Wunsch des Besitzers: „Fehlermeldung, damit Bedrock-Spieler verstehen,
     * warum es nicht geht“): Titel auf dem Bildschirm, Erklärung im Chat und ein Hinweisfenster (Formular) mit dem
     * Grund. Höchstens alle 30 Sekunden, damit Laufen am Rand nicht ständig Fenster öffnet; dazwischen nur die Actionbar.
     */
    public void bedrockNotice(Player p) {
        long now = System.currentTimeMillis();
        Long last = bedrockNoticeAt.get(p.getUniqueId());
        p.sendActionBar(Msg.parse("<red>Casino: nur Java-Edition"));
        if (last != null && now - last < 30_000) return;
        bedrockNoticeAt.put(p.getUniqueId(), now);
        p.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<red><b>Casino gesperrt"),
                Msg.parse("<gray>Nur für die Java-Edition – Grund im Fenster und im Chat")));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        Msg.send(p, "<red><b>Das Casino ist für Bedrock vorerst gesperrt.</b>");
        Msg.send(p, "<gray>Tische, Karten, Chips, Automaten und Animationen sind eigene 3D-Modelle und Effekte. Auf Bedrock "
                + "werden noch nicht alle richtig angezeigt – du würdest Karten, Einsätze oder Gewinne nicht sicher sehen. "
                + "Damit niemand Chips verliert, ohne zu sehen was passiert, spielt das Casino nur in der Java-Edition.");
        Msg.send(p, "<gray>Alles andere (Survival, Shop, Jobs, Angeln …) geht normal. <yellow>Sobald das Casino auf Bedrock fertig ist, geben wir es frei.");
        // Fenster einen Moment später: kommt nicht während eines Teleports oder Chunk-Ladens
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            de.kuby.kubyplugins.bedrock.Bedrock.notice(p, "Casino – nur Java-Edition",
                    "§cDas Casino ist im Moment nur für die Java-Edition geöffnet.\n\n"
                            + "§fWarum? §7Die Tische, Karten, Chips, Automaten und Animationen im Casino sind eigene 3D-Modelle "
                            + "und Effekte. Auf Bedrock werden davon noch nicht alle richtig angezeigt – du würdest zum Beispiel "
                            + "Karten, Einsätze oder Gewinne nicht sicher sehen. Damit niemand Chips verliert, ohne zu sehen, was "
                            + "passiert, ist das Casino für Bedrock gesperrt.\n\n"
                            + "§fWas geht? §7Survival, Shop, Jobs, Angeln und alles andere kannst du ganz normal spielen.\n\n"
                            + "§eSobald das Casino auf Bedrock fertig ist, geben wir es frei.",
                    "Verstanden");
        }, 10L);
    }

    public void forgetBedrockNotice(java.util.UUID id) {
        bedrockNoticeAt.remove(id);
    }

    public void travel(Player p) {
        if (!SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Das Casino gibt es nur in Survival. Wechsle erst den Modus.");
            return;
        }
        if (bedrockBlocked(p)) {
            bedrockNotice(p);
            return;
        }
        Location target = spawn();
        if (target == null) {
            Location k = kasseLocation();
            if (k == null) {
                Msg.send(p, "<red>Das Casino ist noch nicht eingerichtet." + (p.hasPermission("kuby.admin") ? " <gray>Admins: <white>/casino einrichten" : ""));
                return;
            }
            target = k.clone().add(0, 1, 0);
            target.setDirection(p.getLocation().getDirection());
        }
        de.kuby.kubyplugins.util.Teleports.delayed(plugin, p, target, "Casino");
    }

    /** Spawnpunkt für /casino setzen (mit Blickrichtung). */
    public synchronized void setSpawn(Location l) {
        spawn = l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
        save();
    }

    /** Spawnpunkt für /casino, null wenn nicht gesetzt oder die Welt fehlt. */
    public Location spawn() {
        if (spawn == null) return null;
        String[] p = spawn.split(";");
        if (p.length < 6) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True, wenn der Ort genau der Block des Kassentischs ist. */
    public boolean isKasse(Location l) {
        Location k = kasseLocation();
        return k != null && l.getWorld() != null && l.getWorld().equals(k.getWorld())
                && l.getBlockX() == k.getBlockX() && l.getBlockY() == k.getBlockY() && l.getBlockZ() == k.getBlockZ();
    }

    public boolean nearKasse(Location l) {
        double r = plugin.getConfig().getDouble("casino.kasse-radius", 5);
        if (plugin.casinoNpc() != null && plugin.casinoNpc().near(l, de.kuby.kubyplugins.casino.CasinoNpc.Role.KASSE, r)) {
            return true;
        }
        Location k = kasseLocation();
        if (k == null || l.getWorld() == null || !l.getWorld().equals(k.getWorld())) return false;
        return l.distanceSquared(k) <= r * r;
    }

    /** Stellt die Jackpot-Anzeige nach einem Neustart wieder her. */
    public synchronized void respawn() {
        updateDisplay();
    }

    /** Position des Kassentischs, null wenn nicht gesetzt. */
    public Location kasse() {
        return kasseLocation();
    }

    private Location kasseLocation() {
        return parse(kasse);
    }

    private static String format(Location block) {
        return block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
    }

    private static Location parse(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Aktualisiert den schwebenden Jackpot-Text über dem Kassentisch oder legt ihn neu an. */
    /** Der alte Jackpot-Text über dem Kassentisch ist durch das Jackpot-Hologramm ersetzt: Reste entfernen. */
    private void updateDisplay() {
        Location k = kasseLocation();
        if (k == null || k.getWorld() == null || !k.getWorld().isChunkLoaded(k.getBlockX() >> 4, k.getBlockZ() >> 4)) return;
        for (TextDisplay d : k.getWorld().getNearbyEntitiesByType(TextDisplay.class, k.clone().add(0, 1.6, 0), 1)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : chips.entrySet()) {
            out.set("chips." + e.getKey(), e.getValue());
        }
        out.set("jackpot", jackpot);
        out.set("chip-unit", 1);
        if (kasse != null) out.set("kasse", kasse);
        if (tisch != null) out.set("tisch", tisch);
        if (spawn != null) out.set("spawn", spawn);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
