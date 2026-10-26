package de.kuby.kubysurvival.farmpass;

import de.kuby.kubyplugins.service.ModeService;
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

/**
 * /farmpass [pet|fly|kiste], für Store/Admins: /farmpass give <Spieler> [Tage] und /farmpass take <Spieler>.
 * /werkbank öffnet mit Pass überall eine Werkbank, /endertruhe die eigene Endertruhe.
 */
public final class FarmpassCommand implements CommandExecutor, TabCompleter {

    private final KubySurvival addon;

    public FarmpassCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FarmpassService fp = addon.farmpass();
        boolean bench = command.getName().equalsIgnoreCase("werkbank");
        if (bench || command.getName().equalsIgnoreCase("endertruhe")) {
            if (!(sender instanceof Player p)) return true;
            if (!fp.active(p.getUniqueId())) {
                Msg.send(p, "<gray>/" + (bench ? "werkbank" : "endertruhe") + " gibt es mit dem <green>Farmpass<gray>: <white>/farmpass");
            } else if (!ModeService.SURVIVAL.equals(addon.core().modes().modeOf(p.getUniqueId()))) {
                Msg.send(p, "<red>Nur in Survival.");
            } else if (bench) {
                p.openWorkbench(null, true);
            } else {
                p.openInventory(p.getEnderChest());
            }
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if ((sub.equals("give") || sub.equals("take")) && (!(sender instanceof Player) || sender.hasPermission("kuby.admin"))) {
            if (args.length < 2) {
                Msg.send(sender, "<red>Nutzung: /farmpass give <Spieler> [Tage] · /farmpass take <Spieler>");
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) op = Bukkit.getOfflinePlayer(args[1]);
            if (sub.equals("take")) {
                fp.take(op.getUniqueId());
                Msg.send(sender, "<green>Farmpass von " + args[1] + " beendet.");
                return true;
            }
            int days = 30;
            if (args.length > 2) {
                try {
                    days = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    Msg.send(sender, "<red>Tage müssen eine Zahl sein.");
                    return true;
                }
            }
            fp.give(op.getUniqueId(), days);
            Msg.send(sender, "<green>Farmpass für " + args[1] + " um " + days + " Tage verlängert.");
            return true;
        }
        if (!(sender instanceof Player p)) return true;
        switch (sub) {
            case "pet", "haustier" -> new FarmpassPetMenu(addon).open(p);
            case "fly", "fliegen" -> {
                if (!fp.active(p.getUniqueId())) {
                    Msg.send(p, "<gray>Fliegen gibt es mit dem <green>Farmpass<gray>.");
                } else {
                    Msg.send(p, fp.toggleFly(p.getUniqueId()) ? "<green>Fliegen an. <gray>(Spawn, Casino, deine und Freundes-Grundstücke)"
                            : "<gray>Fliegen aus.");
                }
            }
            case "kiste", "weekly" -> {
                String err = fp.claimWeekly(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            default -> new FarmpassMenu(addon).open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("farmpass") && args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("pet", "fly", "kiste"));
            if (sender.hasPermission("kuby.admin")) subs.addAll(List.of("give", "take"));
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        }
        return out;
    }
}
