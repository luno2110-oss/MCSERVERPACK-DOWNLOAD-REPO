package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Slots am Automaten. Rechtsklick auf den Automaten (z. B. einen Hebel) startet eine Drehung, der Einsatz kommt aus den Chips.
 * Drei Walzen laufen als Anzeigen über dem Automaten und halten nacheinander an. Das Ergebnis steht schon beim Start fest,
 * damit Ausloggen oder ein Neustart während der Drehung nichts am Ausgang ändert. Drei Siebener gewinnen den Jackpot.
 */
public final class SlotsService {

    private static final String SURVIVAL = "survival";
    private static final int FRAMES = 10;
    private static final long FRAME_TICKS = 3L;
    private static final long RESULT_SHOWN_TICKS = 100L;

    /** Symbol mit Gewicht (Häufigkeit) und Auszahlung bei drei gleichen (Vielfaches des Einsatzes, in Chips). */
    private enum Symbol {
        CLUB("<green>♣", 35, 8),
        DIAMOND("<aqua>♦", 28, 12),
        HEART("<red>♥", 20, 25),
        STAR("<gold>★", 12, 60),
        SEVEN("<dark_red><b>7</b>", 5, 100);

        final String text;
        final int weight;
        final int multiplier;

        Symbol(String text, int weight, int multiplier) {
            this.text = text;
            this.weight = weight;
            this.multiplier = multiplier;
        }

        static Symbol roll() {
            int total = 0;
            for (Symbol s : values()) total += s.weight;
            int r = ThreadLocalRandom.current().nextInt(total);
            for (Symbol s : values()) {
                r -= s.weight;
                if (r < 0) return s;
            }
            return CLUB;
        }
    }

    private static final class Spin {
        final UUID player;
        final long bet;
        final Symbol[] result;

        Spin(UUID player, long bet, Symbol[] result) {
            this.player = player;
            this.bet = bet;
            this.result = result;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final List<TextDisplay> displays = new ArrayList<>();
    private String automat;
    private Spin spin;

    public SlotsService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "slots.yml");
        this.tag = new NamespacedKey(plugin, "kp_slots");
        automat = YamlConfiguration.loadConfiguration(file).getString("automat");
    }

    /** Entfernt Reste der letzten Sitzung und zeigt den Automaten im Ruhezustand. */
    public synchronized void respawn() {
        Location m = machine();
        if (m == null || m.getWorld() == null) return;
        for (TextDisplay d : m.getWorld().getNearbyEntitiesByType(TextDisplay.class, m, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
        showIdle();
    }

    public synchronized void setMachine(Location block) {
        automat = block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
        save();
        showIdle();
    }

    public boolean isMachine(Location l) {
        Location m = machine();
        return m != null && l.getWorld() != null && l.getWorld().equals(m.getWorld())
                && l.getBlockX() == m.getBlockX() && l.getBlockY() == m.getBlockY() && l.getBlockZ() == m.getBlockZ();
    }

    /** Startet eine Drehung. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public synchronized String pull(Player p) {
        UUID id = p.getUniqueId();
        if (spin != null) return spin.player.equals(id) ? null : "Der Automat dreht gerade.";
        if (!SURVIVAL.equals(plugin.modes().modeOf(id))) return "Slots gibt es nur in Survival.";
        long bet = plugin.getConfig().getLong("casino.slots.bet", 1);
        if (!plugin.casino().takeChips(id, bet)) {
            return "Du brauchst " + bet + " Chip(s) für eine Drehung. Tausch sie am Kassentisch.";
        }
        long feed = Math.round(bet * plugin.casino().price()
                * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        Spin s = new Spin(id, bet, new Symbol[]{Symbol.roll(), Symbol.roll(), Symbol.roll()});
        spin = s;
        for (int frame = 1; frame <= FRAMES; frame++) {
            int f = frame;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> frame(s, f), f * FRAME_TICKS);
        }
        return null;
    }

    /** Beendet eine laufende Drehung beim Herunterfahren, damit Einsatz und Gewinn nicht verloren gehen. */
    public synchronized void shutdown() {
        if (spin != null) settle(spin);
    }

    /** Ein Animationsschritt. Walze 1 hält bei Bild 4, Walze 2 bei Bild 7, Walze 3 beim letzten Bild. */
    private synchronized void frame(Spin s, int frame) {
        if (spin != s) return;
        Symbol[] shown = new Symbol[3];
        int[] stopAt = {4, 7, FRAMES};
        for (int i = 0; i < 3; i++) {
            shown[i] = frame >= stopAt[i] ? s.result[i] : Symbol.roll();
        }
        if (frame >= FRAMES) {
            settle(s);
        } else {
            render(shown, "<gray>Dreht ...");
        }
    }

    private void settle(Spin s) {
        spin = null;
        Symbol[] r = s.result;
        boolean three = r[0] == r[1] && r[1] == r[2];
        long payout = three ? s.bet * r[0].multiplier : 0;
        String result;
        if (three && r[0] == Symbol.SEVEN) {
            long money = plugin.casino().claimJackpot();
            plugin.economy().deposit(s.player, money);
            result = "<gold><b>JACKPOT!</b> <yellow>+" + Msg.coins(money) + "$ <green>+" + (payout - s.bet) + " Chips";
            Bukkit.broadcast(Msg.parse("<gold>Casino: <white>" + nameOf(s.player) + " <gold>hat den Jackpot geknackt: <yellow>"
                    + Msg.coins(money) + "$"));
        } else if (three) {
            result = "<green>Drei gleiche! +" + (payout - s.bet) + " Chips";
        } else {
            result = "<red>Leider nichts. -" + s.bet + " Chip(s)";
        }
        plugin.casino().giveChips(s.player, payout);
        render(r, result);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::resetIdle, RESULT_SHOWN_TICKS);
        Player online = Bukkit.getPlayer(s.player);
        if (online != null) {
            Msg.send(online, result + "  <gray>Chips: " + plugin.casino().chips(s.player));
        }
    }

    private String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "Jemand" : name;
    }

    private synchronized void resetIdle() {
        if (spin == null) showIdle();
    }

    private void showIdle() {
        render(new Symbol[]{Symbol.SEVEN, Symbol.SEVEN, Symbol.SEVEN},
                "<gold>SLOTS <gray>· <white>Rechtsklick zum Spielen");
    }

    /** Baut die Anzeige neu auf: drei Walzen nebeneinander und ein Statustext darüber. */
    private void render(Symbol[] reels, String status) {
        Location m = machine();
        if (m == null || m.getWorld() == null) return;
        if (!m.getWorld().isChunkLoaded(m.getBlockX() >> 4, m.getBlockZ() >> 4)) return;
        for (TextDisplay d : displays) d.remove();
        displays.clear();
        Location base = m.clone().add(0, 1.0, 0);
        spawn(base.clone().add(0, 1.0, 0), status);
        for (int i = 0; i < 3; i++) {
            spawn(base.clone().add((i - 1) * 0.5, 0.4, 0), "<b>[</b>" + reels[i].text + "<reset><b>]</b>");
        }
    }

    private void spawn(Location at, String mini) {
        World w = at.getWorld();
        TextDisplay d = w.spawn(at, TextDisplay.class, e -> {
            e.text(Msg.parse(mini));
            e.setBillboard(Display.Billboard.VERTICAL);
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        displays.add(d);
    }

    private Location machine() {
        if (automat == null) return null;
        String[] p = automat.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        if (automat != null) out.set("automat", automat);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("slots.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
