package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /job [info <Beruf>|waehlen <Beruf>|limit|auftrag|hilfe]. Ohne Argument öffnet sich das Job-Menü. */
public final class JobCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("info", "waehlen", "nebenjob", "limit", "auftrag", "hilfe");

    private final KubySurvival addon;

    public JobCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        JobService jobs = addon.jobs();
        JobService.Worker w = jobs.worker(p.getUniqueId());
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "" -> new JobMenu(addon, addon.core()).open(p);
            case "info" -> {
                Job j = args.length > 1 ? Job.find(args[1]) : w.job;
                if (j == null) {
                    Msg.send(p, "<red>Nutzung: /job info <Beruf> <gray>(" + names() + ")");
                    return true;
                }
                Msg.send(p, j.color + "<b>" + j.display + "</b> <gray>Level <white>" + jobs.jobLevel(w, j) + " <dark_gray>/ "
                        + jobs.maxLevel() + " <gray>(" + jobs.progress(w, j) + "%)");
                for (String t : j.tasks) Msg.send(p, "<gray>▪ <white>" + t);
                if (w.job != j) {
                    Msg.send(p, "<click:run_command:'/job waehlen " + j.ascii + "'><yellow>[" + j.display + " werden]</click>");
                }
            }
            case "waehlen", "wählen", "join" -> {
                Job j = args.length > 1 ? Job.find(args[1]) : null;
                if (j == null) {
                    Msg.send(p, "<red>Nutzung: /job waehlen <Beruf> <gray>(" + names() + ")");
                    return true;
                }
                String err = jobs.choose(p, j);
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                } else {
                    Msg.send(p, "<green>Du bist jetzt " + j.color + j.display + "<green>. <gray>Was zählt: <white>/job info");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
                }
            }
            case "nebenjob", "side" -> {
                if (addon.farmpass() == null || !addon.farmpass().active(p.getUniqueId())) {
                    Msg.send(p, "<gray>Einen Nebenjob gibt es mit dem <green>Farmpass<gray>: <white>/farmpass");
                    return true;
                }
                if (args.length < 2) {
                    Msg.send(p, "<gray>Nebenjob: <white>" + (w.sideJob == null ? "keiner" : w.sideJob.display)
                            + " <gray>· Setzen: <white>/job nebenjob <Beruf> <gray>· Aus: <white>/job nebenjob aus");
                    return true;
                }
                Job j = args[1].equalsIgnoreCase("aus") ? null : Job.find(args[1]);
                if (j == null && !args[1].equalsIgnoreCase("aus")) {
                    Msg.send(p, "<red>Diesen Beruf gibt es nicht. <gray>(" + names() + ")");
                    return true;
                }
                String err = jobs.chooseSide(p, j);
                Msg.send(p, err != null ? "<red>" + err : j == null ? "<gray>Nebenjob aus."
                        : "<green>Dein Nebenjob: " + j.color + j.display + "<green>. Er zahlt wie der Hauptjob (gleiches Stundenlimit).");
            }
            case "limit" -> {
                long cap = jobs.hourlyCap(w);
                Msg.send(p, "<yellow>Stundenlimit: <gold>" + Msg.coins(w.earnedHour) + " <dark_gray>/ " + Msg.coins(cap)
                        + " Coins <gray>· noch <white>" + Msg.coins(Math.max(0, cap - w.earnedHour))
                        + " <gray>· neu in <white>" + JobService.minutesToNextHour() + " Min.");
                long wait = jobs.cooldownMinutes(p.getUniqueId());
                if (wait > 0) Msg.send(p, "<gray>Jobwechsel wieder in <white>" + wait + " Min.");
            }
            case "auftrag", "order" -> {
                Msg.send(p, "<gold><b>Wochenauftrag</b>");
                for (String line : JobMenu.orderLore(jobs, w)) if (!line.isEmpty()) Msg.send(p, line);
            }
            case "hilfe", "help" -> help(p);
            default -> {
                Job j = Job.find(sub);
                if (j != null) {
                    p.performCommand("job info " + j.ascii);
                } else {
                    help(p);
                }
            }
        }
        return true;
    }

    private static String names() {
        StringBuilder sb = new StringBuilder();
        for (Job j : Job.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(j.ascii);
        }
        return sb.toString();
    }

    private void help(Player p) {
        Msg.send(p, "<dark_green><b>Jobs</b>");
        String[][] lines = {
                {"/job", "Job-Menü"},
                {"/job info <Beruf>", "Aufgaben eines Berufs"},
                {"/job waehlen <Beruf>", "Beruf wählen"},
                {"/job limit", "Was diese Stunde noch geht"},
                {"/job auftrag", "Dein Wochenauftrag"},
                {"/job nebenjob <Beruf>", "Zweiter Job (Farmpass)"},
                {"/tokenshop", "Token-Shop"}};
        for (String[] l : lines) {
            Msg.send(p, "<click:suggest_command:'" + l[0].replaceAll(" <.*", " ") + "'><white>" + Msg.escape(l[0]) + "</click> <gray>" + l[1]);
        }
        Msg.send(p, "<gray>Berufe: <white>" + names());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("waehlen")
                || args[0].equalsIgnoreCase("nebenjob"))) {
            for (Job j : Job.values()) if (j.ascii.toLowerCase().startsWith(args[1].toLowerCase())) out.add(j.ascii.toLowerCase());
        }
        return out;
    }
}
