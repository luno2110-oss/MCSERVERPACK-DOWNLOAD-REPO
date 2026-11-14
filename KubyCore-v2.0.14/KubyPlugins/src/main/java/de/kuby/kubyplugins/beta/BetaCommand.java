package de.kuby.kubyplugins.beta;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * /feedback (Formular), /bug &lt;Text&gt; (Bug sofort melden, mit Ort und Modus) für Beta-Tester und /kp beta
 * add|remove|liste|alle|export für Admins.
 */
public final class BetaCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public BetaCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        BetaService beta = plugin.beta();
        if (command.getName().equalsIgnoreCase("feedback")) {
            plugin.feedbackDialog().open(p);
            return true;
        }
        // /bug
        if (!beta.isTester(p) && !p.hasPermission("kuby.admin")) {
            Msg.send(p, "<gray>Bug-Meldungen sind für Beta-Tester. Sonst bitte <yellow>/report <gray>oder <yellow>/support<gray>.");
            return true;
        }
        if (args.length == 0) {
            Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <gray>Nutzung: <yellow>/bug <Was ist passiert?> <gray>(Ort und Modus werden mitgespeichert)");
            return true;
        }
        BetaService.Bug b = beta.reportBug(p, String.join(" ", args));
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <green>Bug #" + b.id() + " gespeichert. Danke!");
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasPermission("kuby.admin") && !o.equals(p)) {
                Msg.send(o, "<#7CF5FF><b>BUG #" + b.id() + "</b> <dark_gray>▶ <white>" + p.getName() + " <gray>(" + b.mode() + "): <white>"
                        + Msg.escape(b.text()) + " <click:run_command:'/kadmin bugs'><yellow>[Liste]</click>");
            }
        }
        return true;
    }

    /** /kp beta … */
    public void admin(Player p, String[] args) {
        BetaService beta = plugin.beta();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "add", "geben", "remove", "nehmen" -> {
                if (args.length < 3) {
                    Msg.send(p, "<red>/kp beta " + sub + " <Spieler>");
                    return;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (t == null) {
                    Msg.send(p, "<red>Diesen Spieler kennen wir nicht (muss einmal online gewesen sein).");
                    return;
                }
                boolean on = sub.equals("add") || sub.equals("geben");
                beta.setTester(t.getUniqueId(), on);
                Msg.send(p, on ? "<green>" + t.getName() + " ist jetzt Beta-Tester." : "<gray>" + t.getName() + " ist kein Beta-Tester mehr.");
                Player tp = t.getPlayer();
                if (tp != null && on) {
                    Msg.send(tp, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <white>Du bist jetzt Beta-Tester! <gray>Feedback: "
                            + "<click:run_command:'/feedback'><yellow>[/feedback]</click> <gray>· Bugs: <yellow>/bug <Text>");
                    Sfx.play(plugin, tp, Sfx.Kind.EPIC);
                }
            }
            case "liste", "list" -> {
                Msg.send(p, "<#7CF5FF><b>Beta-Tester</b> <gray>(" + beta.testers().size() + (beta.everyone() ? ", alle Spieler sind Tester" : "")
                        + ") · abgeschickt: " + beta.submittedCount());
                for (UUID u : beta.testers()) {
                    BetaService.Answers a = beta.answersOf(u);
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    Msg.send(p, "<white>" + (n == null ? u : n) + " <dark_gray>· " + (a.submitted > 0 ? "<green>abgeschickt" : "<gray>"
                            + a.values.size() + " Antworten im Entwurf"));
                }
            }
            case "alle" -> {
                boolean on = !beta.everyone();
                plugin.getConfig().set("beta.alle-tester", on);
                plugin.saveConfig();
                Msg.send(p, on ? "<green>Alle Spieler sind jetzt Beta-Tester." : "<gray>Nur noch eingetragene Spieler sind Beta-Tester.");
            }
            case "export" -> {
                try {
                    File f = beta.export();
                    Msg.send(p, "<green>Exportiert: <white>" + f.getPath() + " <gray>(" + beta.submittedCount() + " Formulare, "
                            + beta.bugs().size() + " Bugs)");
                } catch (IOException e) {
                    Msg.send(p, "<red>Export fehlgeschlagen: " + Msg.escape(e.getMessage()));
                }
            }
            default -> Msg.send(p, "<gold>/kp beta add|remove <Spieler> <gray>· <gold>liste <gray>· <gold>alle <gray>(alle Spieler als Tester an/aus) "
                    + "<gray>· <gold>export <gray>(CSV) · Auswertung: <yellow>/kadmin feedback");
        }
    }
}
