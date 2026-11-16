package de.kuby.kubyscoreboard;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyAllTimer;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.TeamService;
import org.bukkit.plugin.java.JavaPlugin;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Sidebar für Hub, SMP und Survival. Der Kontostand zählt bei Änderungen Ziffer für Ziffer.
 * Ein Moduswechsel setzt den Stand direkt, ohne Zählen und ohne Ton.
 * Am Ende der Zählung gibt es einen Abschlusston: tief bei Verlust, hell bei Gewinn.
 */
public final class ScoreboardService {

    private static final String OBJ = "kp_side";
    private static final String[] ENTRIES = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f",
            "§k", "§l", "§m", "§n", "§o", "§r"};
    private static final int MAX_LINES = ENTRIES.length;

    /** Anzeigewert für das Geld eines Spielers. */
    private static final class Counter {
        long shown;
        long target;
        boolean ready;
        String mode = "";
        boolean animating;
        int dir;
    }

    private final JavaPlugin addon;
    private final KubyPlugins plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Counter> counters = new HashMap<>();
    private final Map<UUID, String[]> lastLines = new HashMap<>();
    private final Map<UUID, String> lastTitle = new HashMap<>();
    /** Zeilenzahl beim letzten Setzen der Punkte (Punkte nur bei Änderung neu setzen, sonst Pakete je Zeile). */
    private final Map<UUID, Integer> lastSize = new HashMap<>();
    /** Zähler der Ticks (alle 2 Server-Ticks), für die verteilte Aktualisierung einmal pro Sekunde. */
    private long tickNo;
    /** Spieler im Casino (für die Casino-Ansicht). */
    private int inCasino;

    public ScoreboardService(JavaPlugin addon, KubyPlugins plugin) {
        this.addon = addon;
        this.plugin = plugin;
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 40L, 2L);
    }

    public void join(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective(OBJ, Criteria.DUMMY, Msg.parse(" "));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < MAX_LINES; i++) {
            Team t = sb.registerNewTeam("kp_l" + i);
            t.addEntry(ENTRIES[i]);
        }
        boards.put(p.getUniqueId(), sb);
        counters.remove(p.getUniqueId());
        lastLines.remove(p.getUniqueId());
        lastTitle.remove(p.getUniqueId());
        lastSize.remove(p.getUniqueId());
        // Die Sidebar ist der Normalfall. hud.enabled: true blendet sie aus (HUD-Zeilen über hudLines).
        if (!addon.getConfig().getBoolean("hud.enabled", false)) p.setScoreboard(sb);
    }

    /** Zeilen für das HUD, mit dem aktuell angezeigten Kontostand. */
    public List<String> hudLines(Player p) {
        Counter c = counters.get(p.getUniqueId());
        long shown = (c != null && c.ready) ? c.shown : plugin.economy().balance(p.getUniqueId());
        return lines(p, plugin.modes().modeOf(p.getUniqueId()), shown, false);
    }

    public void quit(UUID id) {
        belowSent.remove(id);
        boards.remove(id);
        counters.remove(id);
        lastLines.remove(id);
        lastTitle.remove(id);
        lastSize.remove(id);
    }

    /**
     * Alle 2 Ticks: Wer gerade zählt (Kontostand läuft) oder bei wem sich Geld oder Modus geändert hat, bekommt sofort
     * neue Zeilen. Alle anderen einmal pro Sekunde, über die Spieler verteilt (vorher alle 10-mal pro Sekunde).
     */
    private void tick() {
        tickNo++;
        if (tickNo % 10 == 5) belowTick();
        if (tickNo % 10 == 1) {
            int n = 0;
            if (plugin.casinoMusic() != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (ModeService.SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId())) && plugin.casinoMusic().inCasino(p.getLocation())) n++;
                }
            }
            inCasino = n;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!boards.containsKey(id)) {
                join(p);
                update(p);
                continue;
            }
            Counter c = counters.get(id);
            boolean urgent = c == null || !c.ready || c.animating || c.target != plugin.economy().balance(id)
                    || !plugin.modes().modeOf(id).equals(c.mode);
            if (urgent || (tickNo + (id.hashCode() & 0x7fffffff)) % 10 == 0) update(p);
        }
    }

    private void update(Player p) {
        UUID id = p.getUniqueId();
        Scoreboard sb = boards.get(id);
        if (sb == null) return;
        // Spieler-Einstellung "scoreboard": Sidebar aus- und wieder einblenden.
        Objective side = sb.getObjective(OBJ);
        boolean wanted = (plugin.playerSettings() == null || plugin.playerSettings().get(id, "scoreboard"))
                && !de.kuby.kubyplugins.cinema.Cinema.busy(id);
        if (side != null) {
            if (!wanted) {
                if (side.getDisplaySlot() != null) side.setDisplaySlot(null);
                return;
            }
            if (side.getDisplaySlot() != DisplaySlot.SIDEBAR) side.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        String mode = plugin.modes().modeOf(id);
        long balance = plugin.economy().balance(id);
        long shown = counterTick(p, balance, mode);
        // Im Casino (Survival, Region "casino") eigene Ansicht wie im Clip
        if (ModeService.SURVIVAL.equals(mode) && plugin.casinoMusic() != null && plugin.casinoMusic().inCasino(p.getLocation())) {
            mode = "casino";
        }

        // Mit geladenem Grafik-Pack: Rahmen, Logo und Icons. Ohne: reine Text-Version.
        boolean textured = addon.getConfig().getBoolean("scoreboard-logo-title", true) && plugin.uiPack().javaPack(p);
        // Bedrock (Geyser): Text-Fassung, Icons und Modus-Logo als Glyphen aus dem Bedrock-Pack, normale Buchstaben
        boolean bedrock = !textured && plugin.getConfig().getBoolean("bedrock.scoreboard", true)
                && de.kuby.kubyplugins.bedrock.Bedrock.is(p);
        List<String> lines = lines(p, mode, shown, textured);
        String title = textured
                ? SbUi.title(mode, lines.size())
                : bedrock ? bedrockTitle(mode)
                : ScoreboardText.smallText(addon.getConfig().getString("scoreboard.title." + mode, "<gold><bold>KUBY"));
        int maxPx = textured ? SbUi.W : addon.getConfig().getInt("scoreboard.max-width", 140);
        if (!title.equals(lastTitle.get(id))) {
            sb.getObjective(OBJ).displayName(Msg.parse(title));
            lastTitle.put(id, title);
        }

        String[] prev = lastLines.computeIfAbsent(id, k -> new String[MAX_LINES]);
        Objective obj = sb.getObjective(OBJ);
        for (int i = 0; i < MAX_LINES; i++) {
            String text = "";
            if (i < lines.size()) {
                text = textured
                        ? ScoreboardText.fit(lines.get(i), maxPx)
                        : bedrock ? bedrockLine(lines.get(i))
                        : ScoreboardText.fit(ScoreboardText.stripIcons(lines.get(i)), maxPx);
            }
            if (!text.equals(prev[i])) {
                Team t = sb.getTeam("kp_l" + i);
                if (t != null) t.prefix(Msg.parse(text));
                prev[i] = text;
            }
        }
        // Punkte (Reihenfolge) hängen nur an der Zahl der Zeilen: nur bei Änderung neu setzen
        Integer size = lastSize.get(id);
        if (size == null || size != lines.size()) {
            for (int i = 0; i < MAX_LINES; i++) {
                if (i < lines.size()) {
                    Score sc = obj.getScore(ENTRIES[i]);
                    sc.setScore(lines.size() - i);
                    sc.numberFormat(NumberFormat.blank());
                } else {
                    sb.resetScores(ENTRIES[i]);
                }
            }
            lastSize.put(id, lines.size());
        }
    }

    /**
     * Zählt den angezeigten Kontostand Schritt für Schritt zum echten Stand.
     * Moduswechsel: sofort, ohne Ton. Zählung fertig: Abschlusston.
     */
    private long counterTick(Player p, long balance, String mode) {
        Counter c = counters.computeIfAbsent(p.getUniqueId(), k -> new Counter());
        if (!c.ready || !mode.equals(c.mode)) {
            c.shown = balance;
            c.target = balance;
            c.ready = true;
            c.mode = mode;
            c.animating = false;
            return c.shown;
        }
        c.target = balance;
        if (c.shown == c.target) {
            if (c.animating) {
                c.animating = false;
                endSound(p, c.dir);
            }
            return c.shown;
        }
        long diff = c.target - c.shown;
        c.dir = diff > 0 ? 1 : -1;
        c.animating = true;
        long step = Math.max(1, Math.abs(diff) / 6);
        if (Math.abs(diff) <= step) c.shown = c.target;
        else c.shown += c.dir * step;
        if (c.shown == c.target) {
            c.animating = false;
            endSound(p, c.dir);
        } else {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, c.dir > 0 ? 1.5f : 0.7f);
        }
        return c.shown;
    }

    private void endSound(Player p, int dir) {
        if (dir > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
        }
    }

    /** Angezeigter Kontostand mit Farbe, für Hotbar und ActionBar. */
    public String moneyText(Player p) {
        Counter c = counters.get(p.getUniqueId());
        long balance = plugin.economy().balance(p.getUniqueId());
        if (c == null || !c.ready) return "<gold>" + ScoreboardText.number(balance) + "$";
        return moneyColor(p) + ScoreboardText.number(c.shown) + "$";
    }

    private String moneyColor(Player p) {
        Counter c = counters.get(p.getUniqueId());
        if (c == null || c.shown == c.target) return "<gold>";   // in Ruhe gold, beim Zählen grün/rot
        return c.target > c.shown ? "<green>" : "<red>";
    }

    private static final java.util.regex.Pattern SB_ICON = java.util.regex.Pattern.compile(
            "(?:<white>)?<font:" + java.util.regex.Pattern.quote(ScoreboardText.FONT) + ">([^<]*)</font>(?:</white>)? ?");

    /** Bedrock-Zeile: Pack-Icons als Glyphen (fehlt eins, entfällt es), Small Caps als normale Buchstaben. */
    static String bedrockLine(String mini) {
        java.util.regex.Matcher m = SB_ICON.matcher(mini);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            StringBuilder g = new StringBuilder();
            for (char c : m.group(1).toCharArray()) g.append(de.kuby.kubyplugins.bedrock.BedrockAssets.scoreboard(c));
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(g.isEmpty() ? "" : "<white>" + g + "</white> "));
        }
        m.appendTail(out);
        return de.kuby.kubyplugins.bedrock.FormModel.unsmall(out.toString());
    }

    /** Bedrock-Titel: Modus-Logo als Glyph und der Titel aus der Config in normalen Buchstaben. */
    private String bedrockTitle(String mode) {
        char logo = switch (mode) {
            case "smp" -> '\ue050';
            case "survival" -> '\ue051';
            case "skyblock" -> '\ue054';
            case "casino" -> '\ue053';
            default -> '\ue052';
        };
        String g = de.kuby.kubyplugins.bedrock.BedrockAssets.scoreboard(logo);
        String t = de.kuby.kubyplugins.bedrock.FormModel.unsmall(
                addon.getConfig().getString("scoreboard.title." + mode, "<gold><bold>KUBY"));
        return g.isEmpty() ? t : "<white>" + g + "</white> " + t;
    }

    /** Beschriftete Zeile: Icon, Name in Small Caps (weiß), dann der farbige Wert. */
    private static String row(String icon, String label, String value) {
        return ScoreboardText.icon(icon) + "<white>" + SmallCaps.of(label) + ": " + value;
    }

    private List<String> lines(Player p, String mode, long shown, boolean textured) {
        UUID id = p.getUniqueId();
        String rank = ScoreboardText.smallText(rank(p));
        TeamService.Team team = plugin.teams().of(id);
        String teamName = team == null ? "—" : SmallCaps.of(team.name());
        String money = moneyColor(p) + ScoreboardText.money(shown);
        long jewels = plugin.jewels().balance(id);
        long xp = plugin.stats().get(id).xp;
        String online = "<green>" + Bukkit.getOnlinePlayers().size() + "<white>/" + Bukkit.getMaxPlayers();
        String keyall = plugin.getConfig().getString("keyall.command", "").isBlank()
                ? "<red>" + SmallCaps.of("AUS")
                : "<yellow>" + ScoreboardText.clock(KeyAllTimer.secondsUntilNext());
        String people = ScoreboardText.icon(ScoreboardText.I_PEOPLE) + "<green>" + SmallCaps.of("SPIELER ONLINE");

        // Mit Grafik: Zeilen 1 px vom Rahmen, mittige Zeilen über SbUi, oben eine Leerzeile unter dem Logo.
        UnaryOperator<String> ind = textured ? SbUi::indent : s -> s;
        UnaryOperator<String> mid = textured ? SbUi::center : s -> s;
        List<String> out = new ArrayList<>();
        if (textured) out.add("");
        switch (mode) {
            case ModeService.SMP -> {
                var st = plugin.stats().get(id);
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add(ind.apply(row(ScoreboardText.I_TEAM, "TEAM", "<white>" + teamName)));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_COIN, "COINS", money)));
                out.add(ind.apply(row(ScoreboardText.I_GEM, "JUWELEN", "<light_purple>" + ScoreboardText.number(jewels))));
                out.add(ind.apply(row(ScoreboardText.I_XP, "XP", "<green>" + ScoreboardText.number(xp))));
                out.add(ind.apply(row(ScoreboardText.I_TROPHY, "TROPHÄEN", "<#8C78FF>" + ScoreboardText.number(st.trophies))));
                out.add(ind.apply(row(ScoreboardText.I_SWORD, "KILLS", "<red>" + ScoreboardText.number(st.kills))));
                out.add(ind.apply(row(ScoreboardText.I_SKULL, "TODE", "<gold>" + ScoreboardText.number(st.deaths))));
                out.add(ind.apply(row(ScoreboardText.I_CLOCK, "KEYALL IN", keyall)));
                out.add("");
                int w = addon.getConfig().getInt("scoreboard.max-width", 140);
                String head = "<green>" + SmallCaps.of("SPIELER ONLINE");
                String count = online;
                out.add(textured ? SbUi.center(head) : ScoreboardText.center(head, w));
                out.add(textured ? SbUi.center(count) : ScoreboardText.center(count, w));
            }
            case ModeService.SURVIVAL -> {
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add(ind.apply(row(ScoreboardText.I_LEVEL, "LEVEL", plugin.hasStat("player_level")
                        ? "<gray>" + plugin.statValue("player_level", id) : "<white>—")));
                String job = plugin.textValue("job", id);
                out.add(ind.apply(row(ScoreboardText.I_JOB, "JOB", job.isEmpty() ? "<gray>" + SmallCaps.of("KEINER") : "<aqua>" + SmallCaps.of(job))));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_COIN, "COINS", money)));
                out.add(ind.apply(row(ScoreboardText.I_GEM, "JUWELEN", "<light_purple>" + ScoreboardText.number(jewels))));
                out.add(ind.apply(row(ScoreboardText.I_TOKEN, "TOKENS", plugin.hasStat("tokens")
                        ? "<yellow>" + ScoreboardText.number(plugin.statValue("tokens", id)) : "<white>—")));
                out.add(ind.apply(row(ScoreboardText.I_CLAIM, "CLAIMBLÖCKE", "<green>" + ScoreboardText.number(plugin.statValue("claimblocks", id)))));
                out.add(ind.apply(row(ScoreboardText.I_CLOCK, "KEYALL IN", keyall)));
                out.add("");
                String hint1 = ScoreboardText.smallText(addon.getConfig().getString("scoreboard.survival-hint-1", "<white>SPAWN VERLASSEN?"));
                String hint2 = addon.getConfig().getString("scoreboard.survival-hint-2", "<#D4C850>/bauwelt");
                out.add(ind.apply(ScoreboardText.icon(ScoreboardText.I_SPAWN) + hint1));
                // Zweite Zeile eingerückt mit orangem Pfeil, Befehl in normaler Schrift
                out.add(textured ? SbUi.indent(ScoreboardText.icon(ScoreboardText.I_ARROW) + hint2, 16) : "<gold>→ " + hint2);
            }
            case ModeService.SKYBLOCK -> {
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add(ind.apply(row(ScoreboardText.I_TEAM, "TEAM", "<white>" + teamName)));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_COIN, "COINS", money)));
                out.add(ind.apply(row(ScoreboardText.I_GEM, "JUWELEN", "<light_purple>" + ScoreboardText.number(jewels))));
                out.add(ind.apply(row(ScoreboardText.I_XP, "XP", "<green>" + ScoreboardText.number(xp))));
                long lvl = plugin.hasStat("island_level") ? plugin.statValue("island_level", id) : 0;
                long place = plugin.hasStat("island_rank") ? plugin.statValue("island_rank", id) : 0;
                boolean hasIsland = plugin.hasStat("island_points") && (lvl > 0 || plugin.statValue("island_points", id) > 0 || place > 0);
                out.add(ind.apply(row(ScoreboardText.I_ISLAND, "INSEL", hasIsland ? "<aqua>" + SmallCaps.of("LV.") + " " + ScoreboardText.number(lvl)
                        + (place > 0 ? " <gray>#" + place : "") : "<gray>" + SmallCaps.of("KEINE"))));
                long gens = plugin.hasStat("generators") ? plugin.statValue("generators", id) : 0;
                long slots = plugin.hasStat("generator_slots") ? plugin.statValue("generator_slots", id) : 0;
                String boost = plugin.textValue("sb_boost", id);
                out.add(ind.apply(row(ScoreboardText.I_GENERATOR, "GENERATOREN", "<white>" + gens + "<gray>/" + slots
                        + (boost.isEmpty() ? "" : " <gold>⚡" + SmallCaps.of(boost)))));
                String ob = plugin.textValue("sb_oneblock", id);
                out.add(ind.apply(row(ScoreboardText.I_ONEBLOCK, "ONE-BLOCK", ob.isEmpty() ? "<gray>—" : "<green>" + SmallCaps.of(ob))));
                out.add(ind.apply(row(ScoreboardText.I_CLOCK, "KEYALL IN", keyall)));
                out.add("");
                int w = addon.getConfig().getInt("scoreboard.max-width", 140);
                String head = "<green>" + SmallCaps.of("SPIELER ONLINE");
                out.add(textured ? SbUi.center(head) : ScoreboardText.center(head, w));
                out.add(textured ? SbUi.center(online) : ScoreboardText.center(online, w));
            }
            case "casino" -> {
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add(ind.apply(ScoreboardText.icon(ScoreboardText.I_NAME) + "<white>" + p.getName()));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_COIN, "COINS", money)));
                out.add(ind.apply(row(ScoreboardText.I_CHIP, "CHIPS", "<#F2A02A>" + ScoreboardText.number(plugin.casino().chips(id)))));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_SEVEN, "LETZTER GEWINN", "")));
                Long last = plugin.casinoLog().lastNet(id);
                String lastText = last == null ? "<gray>—" : last > 0 ? "<green>+" + ScoreboardText.number(last)
                        : last < 0 ? "<red>-" + ScoreboardText.number(-last) : "<gray>±0";
                out.add(textured ? SbUi.indent(lastText, 10) : "  " + lastText);
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_DICE, "IM CASINO", "<aqua>" + inCasino)));
                // Platz unten für Slot-Automat und Roulette-Rad
                if (textured) out.add("");
            }
            default -> {
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_GEM, "JUWELEN", "<light_purple>" + ScoreboardText.number(jewels))));
                out.add("");
                // Spieler je Modus (Kompass öffnet die Modus-Wahl)
                Map<String, Integer> counts = modeCounts();
                out.add(ind.apply("<green>" + SmallCaps.of("SPIELER JE MODUS")));
                out.add(ind.apply(row(ScoreboardText.I_SWORD, "SMP", "<white>" + counts.getOrDefault(ModeService.SMP, 0))));
                out.add(ind.apply(row(ScoreboardText.I_CLAIM, "SURVIVAL", "<white>" + counts.getOrDefault(ModeService.SURVIVAL, 0))));
                out.add(ind.apply(row(ScoreboardText.I_ISLAND, "SKYBLOCK", "<white>" + counts.getOrDefault(ModeService.SKYBLOCK, 0))));
                out.add(ind.apply(row(ScoreboardText.I_SPAWN, "LOBBY", "<white>" + counts.getOrDefault(ModeService.HUB, 0))));
                out.add("");
                out.add(mid.apply(people));
                out.add(mid.apply(online));
            }
        }
        if (textured) out.add("");   // Luft über dem unteren Rand
        return out;
    }

    private Map<String, Integer> counts = Map.of();
    private long countsAt;

    /** Spieler je Modus, höchstens einmal pro Sekunde gezählt (das Scoreboard fragt für jeden Spieler). */
    private Map<String, Integer> modeCounts() {
        long now = System.currentTimeMillis();
        if (now - countsAt < 1000) return counts;
        Map<String, Integer> m = new java.util.HashMap<>();
        for (Player o : Bukkit.getOnlinePlayers()) m.merge(plugin.modes().modeOf(o.getUniqueId()), 1, Integer::sum);
        counts = m;
        countsAt = now;
        return m;
    }

    // =================================================================================================================
    // Zeile unter dem Namen (v2.01, Wunsch des Besitzers)
    // Casino: Chips · Survival: Geld und Level · SMP: Geld und Trophäen · Skyblock: Team und Insel-Level ·
    // Hub: nichts, nur Team (Admin, Moderator, Team) und Media/Streamer (kuby.media).
    // Steht in jedem Scoreboard als BELOW_NAME: Minecraft zeigt sie unter dem Namensschild und blendet sie beim
    // Schleichen, Verstecken und Unsichtbarsein selbst mit aus. Wer keine Zeile hat, bekommt ein leeres Format.
    // =================================================================================================================

    private static final String BELOW = "kp_below";
    /** Je Betrachter: zuletzt gesendete Zeile je Spielername (nur Änderungen schicken). */
    private final Map<UUID, Map<String, String>> belowSent = new HashMap<>();

    /** Zeile für Spieler t; bedrock: ohne Kapitälchen (die Bedrock-Schrift kennt sie nicht). null = keine Zeile. */
    private String belowLine(Player t, boolean bedrock) {
        UUID id = t.getUniqueId();
        java.util.function.UnaryOperator<String> w = x -> bedrock ? x : SmallCaps.of(x);
        String mode = plugin.modes().modeOf(id);
        if (ModeService.SURVIVAL.equals(mode) && plugin.casinoMusic() != null && plugin.casinoMusic().inCasino(t.getLocation())) {
            return "<#F2C94C>" + ScoreboardText.number(plugin.casino().chips(id)) + " <white>" + w.apply("Chips");
        }
        return switch (mode) {
            case ModeService.SURVIVAL -> "<#7CE07C>" + ScoreboardText.money(plugin.economy().balance(id)) + " <dark_gray>· <aqua>"
                    + w.apply("Level") + " " + (plugin.hasStat("player_level") ? plugin.statValue("player_level", id) : 1);
            case ModeService.SMP -> "<#7CE07C>" + ScoreboardText.money(plugin.economy().balance(id)) + " <dark_gray>· <#8C78FF>"
                    + ScoreboardText.number(plugin.stats().get(id).trophies) + " " + w.apply("Trophäen");
            case ModeService.SKYBLOCK -> {
                TeamService.Team team = plugin.teams().of(id);
                long lvl = plugin.hasStat("island_level") ? plugin.statValue("island_level", id) : 0;
                yield "<white>" + (team == null ? "<gray>" + w.apply("Kein Team") : team.name()) + " <dark_gray>· <gold>"
                        + w.apply("Insel-Level") + " " + lvl;
            }
            case ModeService.HUB -> {
                var role = plugin.staff() == null ? null : plugin.staff().role(t);
                if (role != null) yield "<" + role.color + ">" + w.apply(role.label);
                if (t.hasPermission("kuby.media")) yield "<#B57BFF>" + w.apply("Media");
                yield null;
            }
            default -> null;
        };
    }

    /** Wert für Clients ohne Zahlenformat (z. B. ältere Bedrock-Brücken): die wichtigste Zahl der Zeile. */
    private int belowValue(Player t) {
        UUID id = t.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        long v;
        if (ModeService.SURVIVAL.equals(mode) && plugin.casinoMusic() != null && plugin.casinoMusic().inCasino(t.getLocation())) {
            v = plugin.casino().chips(id);
        } else if (ModeService.SURVIVAL.equals(mode) || ModeService.SMP.equals(mode)) {
            v = plugin.economy().balance(id);
        } else if (ModeService.SKYBLOCK.equals(mode)) {
            v = plugin.hasStat("island_level") ? plugin.statValue("island_level", id) : 0;
        } else {
            v = 0;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }

    private void belowTick() {
        if (!addon.getConfig().getBoolean("unter-dem-namen", true)) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        Map<UUID, String> javaLine = new HashMap<>(), bedLine = new HashMap<>();
        Map<UUID, Integer> value = new HashMap<>();
        for (Player t : online) {
            javaLine.put(t.getUniqueId(), belowLine(t, false));
            bedLine.put(t.getUniqueId(), belowLine(t, true));
            value.put(t.getUniqueId(), belowValue(t));
        }
        for (Player v : online) {
            Scoreboard sb = v.getScoreboard();
            Scoreboard own = boards.get(v.getUniqueId());
            boolean personal = own != null && own == sb;
            if (!personal && sb == Bukkit.getScoreboardManager().getMainScoreboard() && v != online.get(0)) continue;  // Hauptboard nur einmal
            Objective o = sb.getObjective(BELOW);
            if (o == null) {
                o = sb.registerNewObjective(BELOW, Criteria.DUMMY, net.kyori.adventure.text.Component.empty());
                o.numberFormat(NumberFormat.blank());
                o.setDisplaySlot(DisplaySlot.BELOW_NAME);
            } else if (o.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
                o.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
            boolean bedrock = personal && de.kuby.kubyplugins.bedrock.Bedrock.is(v);
            Map<String, String> sent = belowSent.computeIfAbsent(personal ? v.getUniqueId() : new UUID(0, 0), k -> new HashMap<>());
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Player t : online) {
                String line = (bedrock ? bedLine : javaLine).get(t.getUniqueId());
                String name = t.getName();
                names.add(name);
                String key = line == null ? null : line + "|" + value.get(t.getUniqueId());
                if (java.util.Objects.equals(sent.get(name), key)) continue;
                if (line == null) {
                    o.getScore(name).resetScore();
                    sent.remove(name);
                } else {
                    Score sc = o.getScore(name);
                    sc.setScore(value.get(t.getUniqueId()));
                    sc.numberFormat(NumberFormat.fixed(Msg.parse(line)));
                    sent.put(name, key);
                }
            }
            for (String gone : new ArrayList<>(sent.keySet())) {
                if (names.contains(gone)) continue;
                o.getScore(gone).resetScore();
                sent.remove(gone);
            }
        }
    }

    private String rank(Player p) {
        for (String raw : addon.getConfig().getStringList("scoreboard.ranks")) {
            String[] parts = raw.split(";", 2);
            if (parts.length == 2 && p.hasPermission(parts[0].trim())) return parts[1].trim();
        }
        return "<white>Mitglied";
    }
}
