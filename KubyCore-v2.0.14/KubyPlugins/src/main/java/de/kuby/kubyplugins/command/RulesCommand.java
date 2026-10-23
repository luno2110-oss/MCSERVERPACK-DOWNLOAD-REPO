package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /regeln [Nummer]: Paragraphen aus der Config (rules.list, Format "Titel;Text").
 * /report <Spieler> [Grund]: Meldung an alle mit kuby.report.see, dazu ein Eintrag in reports.log. Sperre zwischen Meldungen.
 */
public final class RulesCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins plugin;
    private final Map<UUID, Long> lastReport = new HashMap<>();

    public RulesCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private List<String[]> rules() {
        List<String[]> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("rules.list")) {
            int i = line.indexOf(';');
            out.add(i < 0 ? new String[]{line, ""} : new String[]{line.substring(0, i).trim(), line.substring(i + 1).trim()});
        }
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("report")) return report(sender, args);
        List<String[]> rules = rules();
        if (rules.isEmpty()) {
            Msg.send(sender, "<gray>Es sind noch keine Regeln eingetragen.");
            return true;
        }
        if (args.length > 0) {
            int n;
            try {
                n = Integer.parseInt(args[0].replace("§", ""));
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Nutzung: /regeln <1-" + rules.size() + ">");
                return true;
            }
            if (n < 1 || n > rules.size()) {
                Msg.send(sender, "<red>Es gibt die Paragraphen 1 bis " + rules.size() + ".");
                return true;
            }
            String[] r = rules.get(n - 1);
            sender.sendMessage(Msg.parse("<green><b>§" + n + " " + Msg.escape(r[0]) + "</b>"));
            sender.sendMessage(Msg.parse("<white>" + Msg.escape(r[1])));
            if (n < rules.size()) {
                sender.sendMessage(Msg.parse("<gray><click:run_command:'/regeln " + (n + 1) + "'><hover:show_text:'Nächster Paragraph'>"
                        + "[Weiter zu §" + (n + 1) + "]</hover></click>"));
            }
            return true;
        }
        sender.sendMessage(Msg.parse("<green><b>Regeln</b> <gray>(Klick auf einen Paragraphen für den ganzen Text)"));
        for (int i = 0; i < rules.size(); i++) {
            sender.sendMessage(Msg.parse("<click:run_command:'/regeln " + (i + 1) + "'><hover:show_text:'"
                    + Msg.escape(rules.get(i)[1]).replace("'", "’") + "'><green>§" + (i + 1) + " <white>"
                    + Msg.escape(rules.get(i)[0]) + "</hover></click>"));
        }
        sender.sendMessage(Msg.parse("<gray>Verstoß gesehen? <aqua>/report <Spieler> [Grund]"));
        return true;
    }

    private boolean report(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) {
            Msg.send(p, "<red>Nutzung: /report <Spieler> [Grund]");
            return true;
        }
        long wait = plugin.getConfig().getLong("rules.report-cooldown-seconds", 60) * 1000L;
        Long last = lastReport.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) {
            Msg.send(p, "<red>Bitte warte kurz, bevor du wieder jemanden meldest.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null || target.getUniqueId().equals(p.getUniqueId())) {
            Msg.send(p, "<red>Diesen Spieler kennen wir nicht.");
            return true;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "kein Grund angegeben";
        lastReport.put(p.getUniqueId(), System.currentTimeMillis());
        String where = p.getWorld().getName() + " " + p.getLocation().getBlockX() + " " + p.getLocation().getBlockY() + " "
                + p.getLocation().getBlockZ();
        int seen = 0;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("kuby.report.see")) continue;
            staff.sendMessage(Msg.parse("<red><b>Meldung</b> <white>" + Msg.escape(p.getName()) + " <gray>meldet <white>"
                    + Msg.escape(String.valueOf(target.getName())) + "<gray>: " + Msg.escape(reason)
                    + " <dark_gray>(" + where + ") <click:run_command:'/tp " + p.getName() + "'><aqua>[Hin]</click>"));
            seen++;
        }
        File log = new File(plugin.getDataFolder(), "reports.log");
        try (FileWriter w = new FileWriter(log, true)) {
            w.write(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " | " + p.getName()
                    + " -> " + target.getName() + " | " + reason + " | " + where + System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("reports.log: " + e.getMessage());
        }
        Msg.send(p, "<green>Danke, deine Meldung ist angekommen." + (seen == 0 ? " <gray>Gerade ist niemand vom Team online, "
                + "sie wird gespeichert. Dringendes bitte im Discord melden (/discord)." : ""));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        if (command.getName().equalsIgnoreCase("report")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
        } else {
            for (int i = 1; i <= rules().size(); i++) out.add(String.valueOf(i));
        }
        return out;
    }
}
