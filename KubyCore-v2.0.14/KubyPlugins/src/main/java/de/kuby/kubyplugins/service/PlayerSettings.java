package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persönliche Einstellungen (für alle Modi gleich). Schlüssel, Reiter, Texte (Deutsch/Englisch) und Standardwerte
 * stehen in OPTIONS. Schalter sind an/aus, Mehrfach-Schalter haben feste Stellungen (z. B. die Actionbar).
 * Nur Abweichungen vom Standard werden gespeichert (settings.yml).
 */
public final class PlayerSettings {

    /** Reiter im Einstellungen-Menü (Reihenfolge wie dort); null = nur über eigene Menüs (z. B. /bossbar). */
    public enum Tab { CHAT, REQUESTS, NOTIFY, DISPLAY, OTHER }

    /** Stellung eines Mehrfach-Schalters. "off" zählt als aus. */
    public record Choice(String id, String de, String en) {}

    /**
     * modes: in welchen Modi der Schalter im Menü steht (leer = überall, "hub" zählt wie SMP).
     * choices: null = an/aus, sonst die Stellungen (die erste ist der Standard).
     */
    public record Option(String key, Tab tab, String de, String en, String descDe, String descEn, boolean def,
                         List<Choice> choices, Set<String> modes) {
        public boolean multi() {
            return choices != null;
        }

        public String name(boolean english) {
            return english ? en : de;
        }

        public String desc(boolean english) {
            return english ? descEn : descDe;
        }

        public boolean shownIn(String mode) {
            if (tab == null) return false;
            if (modes.isEmpty()) return true;
            return modes.contains(ModeService.HUB.equals(mode) || mode == null ? ModeService.SMP : mode);
        }
    }

    public static final Map<String, Option> OPTIONS = new LinkedHashMap<>();

    private static final Set<String> ALL = Set.of(), SMP = Set.of(ModeService.SMP), SURVIVAL = Set.of(ModeService.SURVIVAL),
            NOT_SURVIVAL = Set.of(ModeService.SMP, ModeService.SKYBLOCK);

    public static final List<Choice> ACTIONBAR = List.of(
            new Choice("money_xp", "Geld + XP", "Money + XP"),
            new Choice("money", "Nur Geld", "Money only"),
            new Choice("xp", "Nur XP", "XP only"),
            new Choice("all", "Alle Währungen", "All currencies"),
            new Choice("coords", "Koordinaten", "Coordinates"),
            new Choice("time", "Uhrzeit", "Time"),
            new Choice("off", "Deaktiviert", "Disabled"));

    public static final List<Choice> AUTO_TPA = List.of(
            new Choice("off", "Aus", "Off"),
            new Choice("friends", "Freunde", "Friends"),
            new Choice("team", "Freunde + Team", "Friends + team"));

    public static final List<Choice> LANGUAGES = List.of(
            new Choice("de", "Deutsch", "Deutsch"),
            new Choice("en", "English", "English"));

    static {
        // Chat
        add("chat.public", Tab.CHAT, "Öffentlicher Chat", "Public chat",
                "Chat-Nachrichten anderer Spieler sehen.", "See chat messages from other players.", true);
        add("msg", Tab.CHAT, "Private Nachrichten", "Private messages",
                "Andere dürfen dir mit /msg schreiben. Freunde dürfen es immer.",
                "Others may message you with /msg. Friends always can.", true);
        add("chat.joinleave", Tab.CHAT, "Join & Leave", "Join & leave",
                "Meldung, wenn jemand den Server betritt oder verlässt.", "Message when someone joins or leaves.", true);
        add("chat.friends", Tab.CHAT, "Freunde online/offline", "Friends online/offline",
                "Meldung, wenn Freunde kommen oder gehen.", "Message when friends come or go.", true);
        add("chat.deaths", Tab.CHAT, "Todesmeldungen", "Death messages",
                "Meldung, wenn jemand stirbt.", "Message when someone dies.", true);
        add("chat.quiz", Tab.CHAT, "Chat-Quiz", "Chat quiz",
                "Quizfragen im Chat. Wer zuerst richtig antwortet, gewinnt.",
                "Quiz questions in chat. The first right answer wins.", true);
        add("chat.help", Tab.CHAT, "Chat-Hilfe", "Chat help",
                "Bei vertippten Befehlen den passenden vorschlagen.", "Suggest the right command after a typo.", true);
        add("chat.changelog", Tab.CHAT, "Changelog beim Join", "Changelog on join",
                "Neuerungen beim Betreten des Servers zeigen.", "Show what's new when you join.", true);
        // Anfragen
        add("friends", Tab.REQUESTS, "Freundesanfragen", "Friend requests",
                "Andere dürfen dir Freundesanfragen schicken.", "Others may send you friend requests.", true);
        add("team.invites", Tab.REQUESTS, "Team-Anfragen", "Team invites",
                "Andere dürfen dich in ihr Team einladen.", "Others may invite you to their team.", true, NOT_SURVIVAL);
        add("trade", Tab.REQUESTS, "Handelsanfragen", "Trade requests",
                "Andere dürfen dir mit /trade einen Handel anbieten.", "Others may offer you a trade with /trade.", true);
        add("tpa", Tab.REQUESTS, "TPA-Anfragen", "TPA requests",
                "Andere dürfen mit /tpa zu dir. Freunde dürfen es immer.",
                "Others may /tpa to you. Friends always can.", true);
        add("tpahere", Tab.REQUESTS, "TPAhere-Anfragen", "TPAhere requests",
                "Andere dürfen dich mit /tpahere zu sich holen.", "Others may pull you to them with /tpahere.", true);
        addChoice("autotpa", Tab.REQUESTS, "Auto-TPA", "Auto TPA",
                "Teleport-Anfragen von diesen Spielern sofort annehmen.",
                "Accept teleport requests from these players at once.", AUTO_TPA, ALL);
        add("duel", Tab.REQUESTS, "Duell-Anfragen", "Duel requests",
                "Andere dürfen dich zum Duell herausfordern.", "Others may challenge you to a duel.", true, SMP);
        add("marry", Tab.REQUESTS, "Heiratsanträge", "Marriage proposals",
                "Andere dürfen dir einen Antrag machen.", "Others may propose to you.", true, SURVIVAL);
        // Mitteilungen
        add("notify.broadcast", Tab.NOTIFY, "Broadcasts & Tipps", "Broadcasts & tips",
                "Server-Meldungen und Tipps im Chat. Wichtige Ansagen kommen immer durch.",
                "Server messages and tips in chat. Important news always gets through.", true);
        add("notify.coinflip", Tab.NOTIFY, "Coinflip-Mitteilungen", "Coinflip messages",
                "Große Gewinne beim Coinflip.", "Big coinflip wins.", true);
        add("notify.jackpot", Tab.NOTIFY, "Jackpot-Mitteilungen", "Jackpot messages",
                "Jackpot-Ziehungen und Slots-Jackpots im Casino.", "Jackpot draws and slot jackpots in the casino.", true);
        add("notify.dailypot", Tab.NOTIFY, "DailyPot-Mitteilungen", "DailyPot messages",
                "Gewinner des Tagespots.", "Winners of the daily pot.", true);
        add("notify.crate", Tab.NOTIFY, "Crate-Mitteilungen", "Crate messages",
                "Seltene Gewinne aus Kisten.", "Rare wins from crates.", true);
        add("notify.rtpqueue", Tab.NOTIFY, "RTP-Queue-Mitteilungen", "RTP queue messages",
                "Meldung, wenn jemand in der RTP-Warteschlange auf einen Gegner wartet.",
                "Message when someone waits in the RTP queue for an opponent.", true, SMP);
        add("notify.duel", Tab.NOTIFY, "Duell-Mitteilungen", "Duel messages",
                "Ergebnisse von Duellen und Turnieren, Kopfgelder.", "Results of duels and tournaments, bounties.", true, SMP);
        add("notify.vote", Tab.NOTIFY, "Vote-Mitteilungen", "Vote messages",
                "Meldung, wenn jemand für den Server abstimmt.", "Message when someone votes for the server.", true);
        add("notify.angel", Tab.NOTIFY, "Angel-Turnier", "Fishing tournament",
                "Start und Ergebnis der Angel-Turniere.", "Start and results of fishing tournaments.", true, SURVIVAL);
        add("notify.wedding", Tab.NOTIFY, "Hochzeiten", "Weddings",
                "Meldung, wenn zwei Spieler heiraten.", "Message when two players get married.", true, SURVIVAL);
        // Anzeige
        add("scoreboard", Tab.DISPLAY, "Scoreboard", "Scoreboard",
                "Die Anzeige rechts am Bildschirm.", "The panel on the right side of the screen.", true);
        addChoice("actionbar", Tab.DISPLAY, "Actionbar", "Action bar",
                "Dauer-Anzeige in der Actionbar. Wichtige Meldungen kommen immer durch.",
                "Permanent display in the action bar. Important messages always get through.", ACTIONBAR, ALL);
        add("sounds", Tab.DISPLAY, "Sounds", "Sounds",
                "Klick-, Erfolgs- und Hinweis-Sounds.", "Click, success and notice sounds.", true);
        add("particles", Tab.DISPLAY, "Partikel & Effekte", "Particles & effects",
                "Partikel von Hüten, Teleports, Casino und Kisten.", "Particles from hats, teleports, casino and crates.", true);
        add("nightvision", Tab.DISPLAY, "Nachtsicht", "Night vision",
                "Dauerhafte Nachtsicht ohne Partikel.", "Permanent night vision without particles.", false);
        add("team.glow", Tab.DISPLAY, "Team-Leuchten", "Team glow",
                "Teammitglieder durch Wände markiert sehen.", "See team members marked through walls.", true, NOT_SURVIVAL);
        add("infobar", Tab.DISPLAY, "Info-Leiste", "Info bar",
                "Die Leiste oben am Bildschirm. Inhalt: /bossbar", "The bar at the top of the screen. Content: /bossbar",
                true, SURVIVAL);
        add("prices", Tab.DISPLAY, "Preise im Inventar", "Prices in inventory",
                "Verkaufswert des Gegenstands in der Hand anzeigen.", "Show the sell value of the item in your hand.", true);
        add("npcs", Tab.DISPLAY, "Deko-NPCs", "Decoration NPCs",
                "Figuren und Statuen zur Deko sehen (Händler und Croupiers bleiben).",
                "See decorative figures and statues (traders and croupiers stay).", true);
        // Sonstiges
        add("reconnect", Tab.OTHER, "Auto-Neuverbindung", "Auto reconnect",
                "Beim Einloggen zurück in den letzten Modus. Aus: in die Lobby.",
                "Return to your last mode when you join. Off: lobby.", true);
        add("pay.confirm", Tab.OTHER, "Pay-Bestätigung", "Pay confirmation",
                "Vor jeder Überweisung mit /pay nachfragen.", "Ask before every transfer with /pay.", true);
        add("monster", Tab.OTHER, "Monster in der Nähe", "Monsters nearby",
                "Warnung in der Actionbar, wenn Monster in der Nähe sind.",
                "Warning in the action bar when monsters are close.", true);
        add("jump", Tab.OTHER, "Nachspringen", "Follow jump",
                "Freunde dürfen mit /friend jump zu dir springen.", "Friends may jump to you with /friend jump.", true);
        add("hat.self", Tab.OTHER, "Eigener Hut", "Own hat",
                "Deinen Hut selbst sehen (in der Ansicht von außen).", "See your own hat (in third person).", true);
        // ohne Reiter
        add("bar.money", null, "Leiste: Geld", "Bar: money", "Coins und Juwelen", "Coins and jewels", true);
        add("bar.job", null, "Leiste: Job", "Bar: job", "Job, Level, Fortschritt, Limit", "Job, level, progress, limit", true);
        add("bar.place", null, "Leiste: Ort", "Bar: place", "Spawn, Grundstück, Wildnis, Farmwelt",
                "Spawn, claim, wilderness, farm world", true);
        add("bar.quest", null, "Leiste: Quest", "Bar: quest", "Verfolgte Quest mit Fortschritt", "Tracked quest with progress", true);
        add("bar.time", null, "Leiste: Uhrzeit", "Bar: time", "Echte Uhrzeit (Berlin)", "Real time (Berlin)", false);
        add("translate", null, "Auto-Übersetzen", "Auto-translate",
                "Übersetzt Nachrichten anderer Spieler.", "Translates messages from other players.", false);
        addChoice("lang", null, "Sprache", "Language", "Sprache der Menüs und Meldungen.",
                "Language of menus and messages.", LANGUAGES, ALL);
    }

    private static void add(String key, Tab tab, String de, String en, String descDe, String descEn, boolean def) {
        add(key, tab, de, en, descDe, descEn, def, ALL);
    }

    private static void add(String key, Tab tab, String de, String en, String descDe, String descEn, boolean def,
                            Set<String> modes) {
        OPTIONS.put(key, new Option(key, tab, de, en, descDe, descEn, def, null, modes));
    }

    private static void addChoice(String key, Tab tab, String de, String en, String descDe, String descEn,
                                  List<Choice> choices, Set<String> modes) {
        OPTIONS.put(key, new Option(key, tab, de, en, descDe, descEn, true, choices, modes));
    }

    /** Schalter eines Reiters, die im Modus angezeigt werden (Reihenfolge wie in OPTIONS). */
    public static List<Option> shown(Tab tab, String mode) {
        List<Option> out = new ArrayList<>();
        for (Option o : OPTIONS.values()) if (o.tab() == tab && o.shownIn(mode)) out.add(o);
        return out;
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Map<String, Object>> values = new HashMap<>();
    private final List<java.util.function.BiConsumer<org.bukkit.entity.Player, String>> listeners = new ArrayList<>();

    public PlayerSettings(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Map<String, Object> m = new HashMap<>();
                for (String opt : s.getKeys(true)) {
                    if (s.isBoolean(opt)) m.put(opt, s.getBoolean(opt));
                    else if (s.isString(opt)) m.put(opt, s.getString(opt));
                }
                values.put(UUID.fromString(k), m);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** Wird nach jeder Änderung durch den Spieler aufgerufen (Spieler, Schlüssel), z. B. für Nachtsicht. */
    public void onChange(java.util.function.BiConsumer<org.bukkit.entity.Player, String> listener) {
        listeners.add(listener);
    }

    private void changed(UUID id, String key) {
        org.bukkit.entity.Player p = plugin.getServer().getPlayer(id);
        if (p == null) return;
        for (java.util.function.BiConsumer<org.bukkit.entity.Player, String> l : listeners) {
            try {
                l.accept(p, key);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Einstellung " + key + ": " + ex);
            }
        }
    }

    public synchronized boolean get(UUID id, String key) {
        Option o = OPTIONS.get(key);
        if (o != null && o.multi()) return !"off".equals(choice(id, key));
        Map<String, Object> m = values.get(id);
        if (m != null && m.get(key) instanceof Boolean b) return b;
        return o == null || o.def();
    }

    public synchronized boolean toggle(UUID id, String key) {
        boolean now = !get(id, key);
        set(id, key, now);
        return now;
    }

    public synchronized void set(UUID id, String key, boolean value) {
        Option o = OPTIONS.get(key);
        Map<String, Object> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (o != null && value == o.def()) m.remove(key);
        else m.put(key, value);
        save();
        changed(id, key);
    }

    /** Stellung eines Mehrfach-Schalters (id der Stellung). */
    public synchronized String choice(UUID id, String key) {
        Option o = OPTIONS.get(key);
        Map<String, Object> m = values.get(id);
        if (m != null && m.get(key) instanceof String s && o != null && o.multi()) {
            for (Choice c : o.choices()) if (c.id().equals(s)) return s;
        }
        return o == null || !o.multi() ? "" : o.choices().get(0).id();
    }

    public synchronized void setChoice(UUID id, String key, String value) {
        Option o = OPTIONS.get(key);
        Map<String, Object> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (o != null && o.multi() && o.choices().get(0).id().equals(value)) m.remove(key);
        else m.put(key, value);
        if ("lang".equals(key)) m.put("langchosen", Boolean.TRUE);
        save();
        changed(id, key);
    }

    /** Nächste Stellung eines Mehrfach-Schalters; gibt die neue Stellung zurück. */
    public synchronized String cycle(UUID id, String key) {
        Option o = OPTIONS.get(key);
        if (o == null || !o.multi()) return "";
        String cur = choice(id, key);
        int i = 0;
        for (int k = 0; k < o.choices().size(); k++) if (o.choices().get(k).id().equals(cur)) i = k;
        String next = o.choices().get((i + 1) % o.choices().size()).id();
        setChoice(id, key, next);
        return next;
    }

    /** Freier Text (z. B. zuletzt gesehener Changelog). */
    public synchronized String text(UUID id, String key) {
        Map<String, Object> m = values.get(id);
        return m != null && m.get(key) instanceof String s ? s : "";
    }

    public synchronized void setText(UUID id, String key, String value) {
        Map<String, Object> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (value == null || value.isEmpty()) m.remove(key);
        else m.put(key, value);
        save();
    }

    /**
     * Sprache nach der Client-Sprache (beim ersten Einloggen und wenn sie sich ändert), solange der Spieler sie nicht
     * selbst in den Einstellungen gewählt hat.
     */
    public synchronized void autoLanguage(UUID id, boolean en) {
        Map<String, Object> m = values.computeIfAbsent(id, k -> new HashMap<>());
        if (Boolean.TRUE.equals(m.get("langchosen"))) return;
        boolean now = "en".equals(m.get("lang"));
        if (now == en) return;
        if (en) m.put("lang", "en");
        else m.remove("lang");
        save();
        changed(id, "lang");
    }

    public boolean english(UUID id) {
        return "en".equals(choice(id, "lang"));
    }

    /** Setzt alle Schlüssel mit diesem Anfang auf den Standard zurück. */
    public synchronized void reset(UUID id, String prefix) {
        Map<String, Object> m = values.get(id);
        if (m == null) return;
        m.keySet().removeIf(k -> k.startsWith(prefix));
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Object>> e : values.entrySet()) {
            for (Map.Entry<String, Object> v : e.getValue().entrySet()) out.set(e.getKey() + "." + v.getKey(), v.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("settings.yml: " + ex.getMessage());
        }
    }
}
