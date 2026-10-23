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
        boards.remove(id);
        counters.remove(id);
        lastLines.remove(id);
        lastTitle.remove(id);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!boards.containsKey(p.getUniqueId())) join(p);
            update(p);
        }
    }

    private void update(Player p) {
        UUID id = p.getUniqueId();
        Scoreboard sb = boards.get(id);
        if (sb == null) return;
        // Spieler-Einstellung "scoreboard": Sidebar aus- und wieder einblenden.
        Objective side = sb.getObjective(OBJ);
        boolean wanted = plugin.playerSettings() == null || plugin.playerSettings().get(id, "scoreboard");
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

        // Mit geladenem Grafik-Pack: Rahmen, Logo und Icons. Ohne: reine Text-Version.
        boolean textured = addon.getConfig().getBoolean("scoreboard-logo-title", true) && plugin.uiPack().active(p);
        List<String> lines = lines(p, mode, shown, textured);
        String title = textured
                ? SbUi.title(mode, lines.size())
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
                        : ScoreboardText.fit(ScoreboardText.stripIcons(lines.get(i)), maxPx);
            }
            if (!text.equals(prev[i])) {
                Team t = sb.getTeam("kp_l" + i);
                if (t != null) t.prefix(Msg.parse(text));
                prev[i] = text;
            }
            if (i < lines.size()) {
                Score sc = obj.getScore(ENTRIES[i]);
                sc.setScore(lines.size() - i);
                sc.numberFormat(NumberFormat.blank());
            } else {
                sb.resetScores(ENTRIES[i]);
            }
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
            default -> {
                out.add(ind.apply(row(ScoreboardText.I_RANK, "RANG", "<gray>" + rank)));
                out.add("");
                out.add(ind.apply(row(ScoreboardText.I_GEM, "JUWELEN", "<light_purple>" + ScoreboardText.number(jewels))));
                out.add("");
                out.add(ind.apply("<green>" + SmallCaps.of("MODUS WÄHLEN:")));
                out.add(ind.apply("<gray>" + SmallCaps.of("LINKS:") + " <white>" + SmallCaps.of("SMP")));
                out.add(ind.apply("<gray>" + SmallCaps.of("RECHTS:") + " <white>" + SmallCaps.of("SURVIVAL")));
                out.add("");
                out.add(mid.apply(people));
                out.add(mid.apply(online));
            }
        }
        if (textured) out.add("");   // Luft über dem unteren Rand
        return out;
    }

    private String rank(Player p) {
        for (String raw : addon.getConfig().getStringList("scoreboard.ranks")) {
            String[] parts = raw.split(";", 2);
            if (parts.length == 2 && p.hasPermission(parts[0].trim())) return parts[1].trim();
        }
        return "<white>Mitglied";
    }
}
