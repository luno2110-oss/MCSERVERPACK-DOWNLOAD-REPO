package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /sidequests [nav|cancel]. annehmen <Figur> kommt vom Klick im Chat. */
public final class SideQuestCommand implements CommandExecutor, TabCompleter {

    private final KubySurvival addon;

    public SideQuestCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        SideQuestService side = addon.sideQuests();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "" -> new SideQuestMenu(addon).open(p);
            case "nav" -> Msg.send(p, side.toggleNav(p.getUniqueId()) ? "<green>Wegweisung an." : "<gray>Wegweisung aus.");
            case "cancel", "abbrechen" -> Msg.send(p, side.cancel(p.getUniqueId())
                    ? "<gray>Nebenquest abgebrochen. Du kannst sie bei der Figur neu annehmen." : "<gray>Du hast keine laufende Nebenquest.");
            case "annehmen", "accept" -> {
                if (args.length < 2) return true;
                String err = side.accept(p, args[1].toLowerCase());
                if (err != null) Msg.send(p, "<red>" + err);
            }
            default -> {
                Msg.send(p, "<gold><b>Nebenquests</b>");
                Msg.send(p, "<white>/sidequests <gray>Alle Nebenquests");
                Msg.send(p, "<white>/sidequests nav <gray>Wegweisung an/aus");
                Msg.send(p, "<white>/sidequests cancel <gray>Laufende abbrechen");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) for (String s : List.of("nav", "cancel")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        return out;
    }
}
