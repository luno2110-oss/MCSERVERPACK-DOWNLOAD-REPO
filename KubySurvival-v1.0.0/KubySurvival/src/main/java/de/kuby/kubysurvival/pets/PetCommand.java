package de.kuby.kubysurvival.pets;

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
import java.util.Arrays;
import java.util.List;

/** /pet [summon <Name>|dismiss|ride], für Kisten/Ränge/Admins: /pet give <Spieler> <id>. */
public final class PetCommand implements CommandExecutor, TabCompleter {

    private final KubySurvival addon;

    public PetCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PetService ps = addon.pets();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("give") && (!(sender instanceof Player) || sender.hasPermission("kuby.admin"))) {
            if (args.length < 3 || ps.kind(args[2]) == null) {
                Msg.send(sender, "<red>Nutzung: /pet give <Spieler> <id>");
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) op = Bukkit.getOfflinePlayer(args[1]);
            ps.grant(op.getUniqueId(), args[2].toLowerCase());
            Msg.send(sender, "<green>" + args[1] + " hat jetzt das Haustier " + ps.kind(args[2]).name() + ".");
            Player target = op.getPlayer();
            if (target != null) Msg.send(target, "<gold>Neues Haustier: <white>" + ps.kind(args[2]).name() + " <gray>· <white>/pet");
            return true;
        }
        if (!(sender instanceof Player p)) return true;
        switch (sub) {
            case "" -> new PetMenu(addon).open(p);
            case "summon", "rufen" -> {
                if (args.length < 2) {
                    new PetMenu(addon).open(p);
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                PetService.Kind k = ps.find(p.getUniqueId(), name);
                if (k == null) {
                    Msg.send(p, "<red>Dieses Haustier kennen wir nicht. <gray>Alle: <white>/pet");
                    return true;
                }
                String err = ps.summon(p, k);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "dismiss", "weg" -> Msg.send(p, ps.dismiss(p.getUniqueId()) ? "<gray>Dein Haustier ist nach Hause gelaufen." : "<gray>Du hast gerade kein Haustier dabei.");
            case "ride", "reiten" -> {
                String err = ps.ride(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            default -> {
                Msg.send(p, "<gold><b>Haustiere</b>");
                Msg.send(p, "<white>/pet <gray>Menü · <white>/pet summon <Name> <gray>rufen · <white>/pet dismiss <gray>wegschicken · <white>/pet ride <gray>aufsteigen");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("summon", "dismiss", "ride")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("summon") && sender instanceof Player p) {
            for (PetService.Kind k : addon.pets().kinds()) {
                if (addon.pets().owns(p, k) && k.id().startsWith(args[1].toLowerCase())) out.add(k.id());
            }
        }
        return out;
    }
}
