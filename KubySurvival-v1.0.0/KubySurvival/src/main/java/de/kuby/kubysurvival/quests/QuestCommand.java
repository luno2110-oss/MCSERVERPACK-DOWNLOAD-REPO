package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /quest [daily|weekly|abholen|streak|top|verfolgen <Quest>|aus|hilfe]. Ohne Argument das Quest-Menü. */
public final class QuestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("daily", "weekly", "abholen", "streak", "top", "aus", "hilfe");

    private final KubySurvival addon;

    public QuestCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("reload") && sender.hasPermission("kuby.admin")) {
            addon.quests().loadDefs();
            Msg.send(sender, "<green>quests.yml neu geladen.");
            return true;
        }
        if (!(sender instanceof Player p)) return true;
        QuestService q = addon.quests();
        UUID id = p.getUniqueId();
        switch (sub) {
            case "" -> new QuestMenu(addon).open(p);
            case "daily", "tag", "täglich" -> {
                Msg.send(p, "<yellow><b>Tages-Quests</b> <gray>(neue in " + QuestMenu.untilMidnight() + ")");
                for (QuestService.Def d : q.dailies(id)) line(p, "d:" + d.id(), d);
            }
            case "weekly", "woche" -> {
                Msg.send(p, "<aqua><b>Wochen-Quests</b> <gray>(neue jeden Montag)");
                for (QuestService.Def d : q.weeklies(id)) line(p, "w:" + d.id(), d);
            }
            case "abholen", "claim" -> {
                int n = q.claimAll(p);
                if (n == 0) Msg.send(p, "<gray>Gerade gibt es nichts abzuholen. Schau mit <white>/quest</white> nach deinen Quests.");
            }
            case "streak", "serie" -> {
                int s = q.streak(id);
                Msg.send(p, "<gold>Deine Serie: <white>" + s + (s == 1 ? " Tag" : " Tage") + " <gray>(beste: " + q.bestStreak(id) + ")");
                Msg.send(p, "<gray>Hol jeden Tag mindestens eine Tages-Quest ab. Die erste am Tag bringt <gold>"
                        + Msg.coins(q.streakBonus()) + " Coins <gray>je Serientag extra (bis 30 Tage).");
                StringBuilder sb = new StringBuilder();
                q.streakRewards().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                    if (sb.length() > 0) sb.append("<dark_gray>, ");
                    sb.append(e.getKey() <= s ? "<green>" : "<white>").append(e.getKey()).append(" Tage <light_purple>+").append(e.getValue());
                });
                if (sb.length() > 0) Msg.send(p, "<gray>Tokens bei: " + sb);
            }
            case "top", "rangliste" -> {
                Msg.send(p, "<gold><b>Quest-Rangliste</b>");
                int i = 1;
                for (Map.Entry<UUID, Long> e : q.top(10)) {
                    String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    Msg.send(p, "<gold>" + i++ + ". <white>" + (name == null ? "?" : name) + " <gray>– " + Msg.coins(e.getValue()) + " Quests");
                }
                if (i == 1) Msg.send(p, "<gray>Noch hat niemand eine Quest geschafft.");
                Msg.send(p, "<gray>Du: <white>" + q.total(id) + " Quests");
            }
            case "verfolgen", "track" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>Nutzung: /quest verfolgen <Quest> <gray>(oder im Menü anklicken)");
                    return true;
                }
                q.setTracked(id, args[1]);
                QuestService.Track t = q.track(id);
                Msg.send(p, t != null && t.key().equals(args[1]) ? "<green>Wird jetzt oben angezeigt: <white>" + t.def().name()
                        : "<red>Diese Quest kannst du gerade nicht verfolgen.");
            }
            case "aus", "off" -> {
                q.setTracked(id, "-");
                Msg.send(p, "<gray>Die Quest-Anzeige oben ist aus. Mit <white>/quest</white> schaltest du sie im Menü wieder an.");
            }
            default -> help(p);
        }
        return true;
    }

    private void line(Player p, String key, QuestService.Def d) {
        QuestService q = addon.quests();
        UUID id = p.getUniqueId();
        String state = q.isClaimed(id, key) ? "<green>✔"
                : q.isDone(id, key) ? "<click:run_command:'/quest abholen'><gold>[Abholen]</click>"
                : "<click:run_command:'/quest verfolgen " + key + "'><hover:show_text:'Oben anzeigen'><gray>"
                + Msg.coins(q.progressOf(id, key)) + "/" + Msg.coins(d.amount()) + "</hover></click>";
        Msg.send(p, "<white>" + Msg.escape(d.name()) + " <dark_gray>– <gray>" + Msg.escape(d.task()) + " " + state);
    }

    private void help(Player p) {
        Msg.send(p, "<gold><b>Quests</b>");
        String[][] lines = {
                {"/quest", "Quest-Menü"},
                {"/quest daily", "Tages-Quests"},
                {"/quest weekly", "Wochen-Quests"},
                {"/quest abholen", "Belohnungen abholen"},
                {"/quest streak", "Deine Serie"},
                {"/quest top", "Rangliste"},
                {"/quest aus", "Anzeige oben ausschalten"}};
        for (String[] l : lines) Msg.send(p, "<click:suggest_command:'" + l[0] + "'><white>" + l[0] + "</click> <gray>" + l[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        return out;
    }
}
