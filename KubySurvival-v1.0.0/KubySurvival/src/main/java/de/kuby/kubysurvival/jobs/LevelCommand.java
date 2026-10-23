package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /level [Spieler|top]. Das Level kommt aus den Spieler-XP: Online-Zeit (level.xp-per-minute, nur aktiv),
 * alle Job-XP und Quest-Belohnungen. Level L braucht insgesamt 150 * L * (L - 1) XP.
 */
public final class LevelCommand implements CommandExecutor, TabCompleter {

    private final KubySurvival addon;

    public LevelCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        JobService jobs = addon.jobs();
        if (args.length > 0 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("rangliste"))) {
            Msg.send(sender, "<gold><b>Level-Rangliste</b>");
            int i = 1;
            for (Map.Entry<UUID, Long> e : jobs.topXp(10)) {
                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                int level = JobService.playerLevel(jobs.worker(e.getKey()));
                String color = i == 1 ? "<gold>" : i == 2 ? "<gray>" : i == 3 ? "<#C8884A>" : "<white>";
                Msg.send(sender, color + i++ + ". <white>" + (name == null ? "?" : name) + " <gray>– Level <green>" + level
                        + " <dark_gray>(" + Msg.coins(e.getValue()) + " XP)");
            }
            if (i == 1) Msg.send(sender, "<gray>Noch hat niemand XP gesammelt.");
            return true;
        }
        UUID id;
        String name;
        if (args.length > 0) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (op == null) {
                Msg.send(sender, "<red>Diesen Spieler kennen wir nicht.");
                return true;
            }
            id = op.getUniqueId();
            name = op.getName();
        } else if (sender instanceof Player p) {
            id = p.getUniqueId();
            name = null;
        } else {
            return true;
        }
        JobService.Worker w = jobs.worker(id);
        int level = JobService.playerLevel(w);
        long from = JobService.playerXpFor(level);
        long to = JobService.playerXpFor(level + 1);
        int pct = (int) Math.min(99, (w.totalXp - from) * 100 / Math.max(1, to - from));
        Msg.send(sender, "<gold><b>" + (name == null ? "Dein Level" : "Level von " + Msg.escape(name)) + ": <green>" + level + "</b>");
        Msg.send(sender, JobMenu.bar(pct) + " <dark_gray>· <gray>" + Msg.coins(w.totalXp - from) + "/" + Msg.coins(to - from)
                + " XP bis Level " + (level + 1));
        if (name == null) {
            Msg.send(sender, "<gray>XP gibt es fürs Online-Sein (" + addon.core().getConfig().getLong("level.xp-per-minute", 5)
                    + " pro aktiver Minute), für Jobs und für Quests. Jeder Aufstieg bringt <light_purple>Tokens<gray>.");
            Msg.send(sender, "<click:run_command:'/level top'><yellow>[Rangliste]</click>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        if ("top".startsWith(args[0].toLowerCase())) out.add("top");
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
        return out;
    }
}
